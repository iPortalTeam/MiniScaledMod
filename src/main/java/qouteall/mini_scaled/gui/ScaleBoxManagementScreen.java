package qouteall.mini_scaled.gui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.portal.animation.TimingFunction;
import qouteall.imm_ptl.core.render.GuiPortalRendering;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.PortalRenderer;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.mini_scaled.MiniScaledPortal;
import qouteall.mini_scaled.ScaleBoxRecord;
import qouteall.mini_scaled.VoidDimension;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.api.McRemoteProcedureCall;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.animation.Animated;

import java.util.List;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class ScaleBoxManagementScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static final UUID RENDERING_DESC = UUID.randomUUID();
    
    public static final float VIEW_RATIO = 0.8f;
    
    private static RenderTarget frameBuffer;
    
    private ScaleBoxListWidget listWidget;
    
    private final ScaleBoxGuiManager.GuiData data;
    
    private @Nullable ScaleBoxRecord.Entry selected;
    
    private double mouseX;
    private double mouseY;
    
    private Animated<Double> pitchAnim = new Animated<>(
        Animated.DOUBLE_TYPE_INFO,
        () -> RenderStates.renderStartNanoTime,
        TimingFunction.sine::mapProgress,
        0.0
    );
    private Animated<Double> yawAnim = new Animated<>(
        Animated.DOUBLE_TYPE_INFO,
        () -> RenderStates.renderStartNanoTime,
        TimingFunction.sine::mapProgress,
        0.0
    );
    private Animated<Double> distanceAnim = new Animated<>(
        Animated.DOUBLE_TYPE_INFO,
        () -> RenderStates.renderStartNanoTime,
        TimingFunction.sine::mapProgress,
        20.0
    );
    
    public static void init_() {
        // don't render MiniScaled portal when rendering the view in scale box gui
        PortalRenderer.PORTAL_RENDERING_PREDICATE.register(portal -> {
            if (portal instanceof MiniScaledPortal) {
                List<UUID> renderingDescription = WorldRenderInfo.getRenderingDescription();
                if (!renderingDescription.isEmpty() &&
                    renderingDescription.get(0).equals(RENDERING_DESC)
                ) {
                    return false;
                }
            }
            
            return true;
        });
    }
    
    public static void openGui(ScaleBoxGuiManager.GuiData guiData) {
        ScaleBoxManagementScreen screen = new ScaleBoxManagementScreen(guiData);
        
        Minecraft.getInstance().setScreen(screen);
    }
    
    public ScaleBoxManagementScreen(ScaleBoxGuiManager.GuiData guiData) {
        super(Component.literal("Scale box management"));
        
        this.data = guiData;
        
        this.listWidget = new ScaleBoxListWidget(
            this, width, height,
            100, 200,
            ScaleBoxEntryWidget.WIDGET_HEIGHT
        );
        
        List<ScaleBoxRecord.Entry> entriesForPlayer = guiData.entriesForPlayer();
        for (int i = 0; i < entriesForPlayer.size(); i++) {
            ScaleBoxRecord.Entry entry = entriesForPlayer.get(i);
            ScaleBoxEntryWidget widget = new ScaleBoxEntryWidget(
                listWidget, i, entry,
                this::onEntrySelected
            );
            listWidget.children().add(widget);
        }
        
        // initialize the global singleton framebuffer
        if (frameBuffer == null) {
            // the framebuffer size doesn't matter here
            // because it will be automatically resized when rendering
            frameBuffer = new TextureTarget(2, 2, true, true);
        }
    }
    
    private void onEntrySelected(ScaleBoxEntryWidget w) {
        selected = w.entry;
        listWidget.setSelected(w);
        McRemoteProcedureCall.tellServerToInvoke(
            "qouteall.mini_scaled.gui.ScaleBoxGuiManager.RemoteCallables.requestChunkLoading",
            w.entry.id
        );
    }
    
    @Override
    protected void init() {
        super.init();
        
        int scrollBarWidth = 6;
        int listWidth = width - (int) (width * VIEW_RATIO) - scrollBarWidth;
        listWidget.updateSize(
            listWidth, // width
            height, // height
            0, // start y
            height // end y
        );
        listWidget.setLeftPos(0); // left x
        listWidget.rowWidth = listWidth;
        
        addWidget(listWidget);
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        renderBackground(guiGraphics);
        
        listWidget.render(guiGraphics, mouseX, mouseY, partialTick);
        
        if (selected == null) {
            return;
        }
        
        Vec3 viewingCenter = selected.getInnerAreaBox().getCenterVec();
        
        Double pitch = pitchAnim.getCurrent();
        Double yaw = yawAnim.getCurrent();
        Double distance = distanceAnim.getCurrent();
        
        assert pitch != null;
        assert yaw != null;
        assert distance != null;
        assert minecraft != null;
        
        DQuaternion rot = DQuaternion.getCameraRotation(pitch, yaw);
        
        Matrix4f cameraTransformation = new Matrix4f();
        cameraTransformation.identity();
        cameraTransformation.rotate(rot.toMcQuaternion());
        
        Vec3 offset = rot.getConjugated().rotate(new Vec3(0, 0, distance));
        Vec3 cameraPosition = viewingCenter.add(offset);
        
        // Create the world render info
        WorldRenderInfo worldRenderInfo = new WorldRenderInfo(
            ClientWorldLoader.getWorld(VoidDimension.dimensionId),
            cameraPosition,
            cameraTransformation,
            true,
            RENDERING_DESC,
            minecraft.options.getEffectiveRenderDistance(),
            false,
            false
        );
        
        GuiPortalRendering.submitNextFrameRendering(worldRenderInfo, frameBuffer);
        
        int h = minecraft.getWindow().getHeight();
        int w = minecraft.getWindow().getWidth();
        MyRenderHelper.drawFramebuffer(
            frameBuffer,
            false, false,
            w * (1 - VIEW_RATIO), w,
            0, h * VIEW_RATIO
        );
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        
        this.mouseX = mouseX;
        this.mouseY = mouseY;

//        LOGGER.info("mouse moved {} {}", mouseX, mouseY);
        
        double pitch = (mouseY / ((double) height) - 0.5) * 180;
        double yaw = (mouseX / (double) width) * 360;
        long duration = Helper.secondToNano(0.1);
        pitchAnim.setTarget(pitch, duration);
        yawAnim.setTarget(yaw, duration);
        
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
//        LOGGER.info("mouse scrolled {} {} {}", mouseX, mouseY, delta);
        
        if (mouseX < listWidget.rowWidth) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        
        Double target = distanceAnim.getTarget();
        assert target != null;
        
        double newTarget = target + delta * -1.0;
        newTarget = Mth.clamp(newTarget, 5, 100);
        long duration = Helper.secondToNano(0.2);
        distanceAnim.setTarget(newTarget, duration);
        
        return false;
    }
    
    @Override
    public void onClose() {
        super.onClose();
        
        // tell server to remove chunk loader
        McRemoteProcedureCall.tellServerToInvoke(
            "qouteall.mini_scaled.gui.ScaleBoxGuiManager.RemoteCallables.onGuiClose"
        );
    }
    
    // close when E is pressed
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        
        if (minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        
        return false;
    }
}
