package qouteall.mini_scaled.gui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.animation.TimingFunction;
import qouteall.imm_ptl.core.render.GuiPortalRendering;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.PortalRenderer;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.mini_scaled.MiniScaledPortal;
import qouteall.mini_scaled.ScaleBoxRecord;
import qouteall.mini_scaled.VoidDimension;
import qouteall.mini_scaled.util.MSUtil;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.api.McRemoteProcedureCall;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.animation.Animated;

import java.util.List;
import java.util.Objects;
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
        Animated.DOUBLE_DEFAULT_ZERO_TYPE_INFO,
        () -> RenderStates.renderStartNanoTime,
        TimingFunction.sine::mapProgress,
        0.0
    );
    private Animated<Double> yawAnim = new Animated<>(
        Animated.DOUBLE_DEFAULT_ZERO_TYPE_INFO,
        () -> RenderStates.renderStartNanoTime,
        TimingFunction.sine::mapProgress,
        0.0
    );
    private Animated<Double> distanceAnim = new Animated<>(
        Animated.DOUBLE_DEFAULT_ZERO_TYPE_INFO,
        () -> RenderStates.renderStartNanoTime,
        TimingFunction.sine::mapProgress,
        20.0
    );
    
    private @Nullable MultiLineLabel labelCache;
    
    private final Button optionsButton;
    
    public static void init_() {
        // don't render MiniScaled portal when rendering the view in scale box gui
        PortalRenderer.PORTAL_RENDERING_PREDICATE.register(portal -> {
            if (portal instanceof MiniScaledPortal) {
                List<UUID> renderingDescription = WorldRenderInfo.getRenderingDescription();
                if (!renderingDescription.isEmpty() &&
                    Objects.equals(renderingDescription.get(0), RENDERING_DESC)
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
        
        optionsButton = Button.builder(
            Component.translatable("mini_scaled.options"),
            button -> {
                if (selected != null) {
                    ScaleBoxOptionsScreen optionsScreen = new ScaleBoxOptionsScreen(this, selected);
                    assert minecraft != null;
                    minecraft.setScreen(optionsScreen);
                }
            }
        ).build();
        optionsButton.visible = false;
        
        if (guiData.boxId() != null) {
            ScaleBoxEntryWidget widget = listWidget.children().stream()
                .filter(e -> e.entry.id == guiData.boxId())
                .findFirst().orElse(null);
            
            if (widget != null) {
                onEntrySelected(widget);
            }
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
        labelCache = null;
        optionsButton.visible = true;
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
        
        addRenderableWidget(listWidget);
        
        optionsButton.setWidth(100);
        optionsButton.setX(width - 10 - optionsButton.getWidth());
        optionsButton.setY(height - 10 - optionsButton.getHeight());
        
        addRenderableWidget(optionsButton);
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        if (selected == null) {
            guiGraphics.drawCenteredString(
                font,
                Component.translatable("mini_scaled.select_a_scale_box"),
                (int) (width * (1 - VIEW_RATIO)) + (int) (width * VIEW_RATIO) / 2,
                (int) (height * VIEW_RATIO) / 2,
                0xFFFFFFFF
            );
            return;
        }
        
        renderView(selected);
        
        if (labelCache == null) {
            labelCache = MultiLineLabel.create(
                font,
                getLabel(selected),
                (int) (width * VIEW_RATIO) - 20
            );
        }
        
        labelCache.renderLeftAligned(
            guiGraphics,
            ((int) (width * (1 - VIEW_RATIO))) + 10,
            ((int) (height * VIEW_RATIO)) + 10,
            10, 0xFFFFFFFF
        );
    }
    
    private static Component getLabel(ScaleBoxRecord.Entry entry) {
        MutableComponent component = Component.literal("");
        
        component.append(Component.translatable("mini_scaled.color"));
        component.append(MSUtil.getColorText(entry.color)
            .withStyle(Style.EMPTY.withColor(entry.color.getTextColor()))
        );
        component.append("     ");
        
        component.append(Component.translatable("mini_scaled.scale"));
        component.append(Component.literal(Integer.toString(entry.scale)).withStyle(ChatFormatting.AQUA));
        component.append("\n");
        
        if (entry.currentEntranceDim != null && entry.currentEntrancePos != null) {
            component.append(Component.translatable("mini_scaled.in"));
            component.append(McHelper.getDimensionName(entry.currentEntranceDim));
            component.append(Component.literal("   %d  %d  %d".formatted(
                entry.currentEntrancePos.getX(),
                entry.currentEntrancePos.getY(),
                entry.currentEntrancePos.getZ()
            )).withStyle(ChatFormatting.AQUA));
        }
        
        return component;
    }
    
    private void renderView(ScaleBoxRecord.Entry e) {
        Vec3 viewingCenter = e.getInnerAreaBox().getCenterVec();
        
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
        WorldRenderInfo worldRenderInfo =
            new WorldRenderInfo.Builder()
                .setWorld(ClientWorldLoader.getWorld(VoidDimension.KEY))
                .setCameraPos(cameraPosition)
                .setCameraTransformation(cameraTransformation)
                .setOverwriteCameraTransformation(true)
                .setDescription(RENDERING_DESC)
                .setDoRenderHand(false)
                .setEnableViewBobbing(false)
                .setDoRenderSky(false)
                .build();
        
        GuiPortalRendering.submitNextFrameRendering(worldRenderInfo, frameBuffer);
        
        int h = minecraft.getWindow().getHeight();
        int w = minecraft.getWindow().getWidth();
        MyRenderHelper.drawFramebuffer(
            frameBuffer,
            true, false,
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
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (mouseX < listWidget.rowWidth) {
            return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
        }
        
        Double target = distanceAnim.getTarget();
        assert target != null;
        
        double newTarget = target + deltaY * -1.0;
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
