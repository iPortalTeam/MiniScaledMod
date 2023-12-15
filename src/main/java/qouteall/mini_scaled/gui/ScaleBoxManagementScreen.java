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
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
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
import qouteall.mini_scaled.ClientUnwrappingInteraction;
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
    
    private final ScaleBoxInteractionManager.ManagementGuiData data;
    
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
    
    private final Button getEntranceButton;
    
    private final Button unwrapButton;
    
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
    
    public static void openGui(ScaleBoxInteractionManager.ManagementGuiData managementGuiData) {
        ScaleBoxManagementScreen screen = new ScaleBoxManagementScreen(managementGuiData);
        
        Minecraft.getInstance().setScreen(screen);
    }
    
    public ScaleBoxManagementScreen(ScaleBoxInteractionManager.ManagementGuiData managementGuiData) {
        super(Component.literal("Scale box management"));
        
        // in vanilla it's set in init(), but I want to initialize early
        this.minecraft = Minecraft.getInstance();
        this.font = minecraft.font;
        
        this.data = managementGuiData;
        
        this.listWidget = new ScaleBoxListWidget(
            this, width, height,
            100, 200,
            ScaleBoxEntryWidget.WIDGET_HEIGHT
        );
        
        List<ScaleBoxRecord.Entry> entriesForPlayer = managementGuiData.entriesForPlayer();
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
        
        getEntranceButton = Button.builder(
            Component.translatable("mini_scaled.get_entrance"),
            button -> {
                if (selected != null) {
                    /**{@link ScaleBoxInteractionManager.RemoteCallables#acquireEntrance}*/
                    McRemoteProcedureCall.tellServerToInvoke(
                        "qouteall.mini_scaled.gui.ScaleBoxInteractionManager.RemoteCallables.acquireEntrance",
                        selected.id
                    );
                    minecraft.setScreen(null);
                }
            }
        ).build();
        getEntranceButton.visible = false;
        
        unwrapButton = Button.builder(
            Component.translatable("mini_scaled.unwrap"),
            button -> {
                if (selected != null) {
                    Minecraft.getInstance().setScreen(null);
                    ClientUnwrappingInteraction.startPreUnwrapping(selected);
                }
            }
        ).build();
        unwrapButton.visible = false;
        
        if (managementGuiData.boxId() != null) {
            ScaleBoxEntryWidget widget = listWidget.children().stream()
                .filter(e -> e.entry.id == managementGuiData.boxId())
                .findFirst().orElse(null);
            
            if (widget != null) {
                onEntrySelected(widget);
            }
        }
        
        // initialize the global singleton framebuffer
        if (frameBuffer == null) {
            // the initial framebuffer size doesn't matter here
            // because it will be automatically resized when rendering
            frameBuffer = new TextureTarget(2, 2, true, true);
        }
    }
    
    private void onEntrySelected(ScaleBoxEntryWidget w) {
        selected = w.entry;
        listWidget.setSelected(w);
        labelCache = null;
        optionsButton.visible = true;
        getEntranceButton.visible = true;
        
        // unwrap button is only visible for the interacted box
        unwrapButton.visible = Objects.equals(data.boxId(), w.entry.id);
        
        /**{@link ScaleBoxInteractionManager.RemoteCallables#requestChunkLoading(ServerPlayer, int)}*/
        McRemoteProcedureCall.tellServerToInvoke(
            "qouteall.mini_scaled.gui.ScaleBoxInteractionManager.RemoteCallables.requestChunkLoading",
            w.entry.id
        );
    }
    
    @Override
    protected void init() {
        super.init();
        
        int scrollBarWidth = 6;
        int listWidth = width - (int) (width * VIEW_RATIO);
        listWidget.setSize(listWidth, height);
        listWidget.setPosition(0, 0);
        listWidget.rowWidth = listWidth - scrollBarWidth;
        
        addRenderableWidget(listWidget);
        addRenderableWidget(optionsButton);
        addRenderableWidget(getEntranceButton);
        addRenderableWidget(unwrapButton);
        
        optionsButton.setWidth(80);
        getEntranceButton.setWidth(120);
        unwrapButton.setWidth(100);
        
        GridLayout gridLayout = new GridLayout(0, 0).spacing(10);
        LayoutSettings layoutSettings = gridLayout.defaultCellSetting()
            .alignVerticallyMiddle().alignHorizontallyLeft();
        
        gridLayout.addChild(optionsButton, 0, 0);
        gridLayout.addChild(getEntranceButton, 0, 1);
        gridLayout.addChild(unwrapButton, 0, 2);
        
        gridLayout.arrangeElements();
        gridLayout.setPosition(listWidth + 10, height - 5 - gridLayout.getHeight());
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (selected == null) {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            
            MutableComponent text = data.entriesForPlayer().isEmpty() ?
                Component.translatable("mini_scaled.no_scale_box") :
                Component.translatable("mini_scaled.select_a_scale_box");
            guiGraphics.drawCenteredString(
                font,
                text,
                (int) (width * (1 - VIEW_RATIO)) + (int) (width * VIEW_RATIO) / 2,
                (int) (height * VIEW_RATIO) / 2,
                0xFFFFFFFF
            );
            return;
        }
        
        // make the non-view area darker
        guiGraphics.fillGradient(
            ((int) (width * (1 - VIEW_RATIO))), ((int) (height * VIEW_RATIO)), this.width, this.height,
            0x88000000, 0x88000000
        );
        guiGraphics.fillGradient(
            0, 0, ((int) (width * (1 - VIEW_RATIO))), this.height,
            0x88000000, 0x88000000
        );
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
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
            ((int) (height * VIEW_RATIO)) + 3,
            10, 0xFFFFFFFF
        );
    }
    
    private Component getLabel(ScaleBoxRecord.Entry entry) {
        MutableComponent component = Component.literal("");
        
        component.append(Component.translatable("mini_scaled.color"));
        component.append(MSUtil.getColorText(entry.color)
            .withStyle(Style.EMPTY.withColor(entry.color.getTextColor()))
        );
        component.append("     ");
        
        component.append(Component.translatable("mini_scaled.scale"));
        component.append(Component.literal(Integer.toString(entry.scale)).withStyle(ChatFormatting.AQUA));
        
        if (data.isOP()) {
            component.append("     ");
            component.append(Component.translatable("mini_scaled.owner"));
            component.append(Component.literal(entry.ownerNameCache).withStyle(ChatFormatting.AQUA));
        }
        
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
        /**{@link ScaleBoxInteractionManager.RemoteCallables#onGuiClose(ServerPlayer)}*/
        McRemoteProcedureCall.tellServerToInvoke(
            "qouteall.mini_scaled.gui.ScaleBoxInteractionManager.RemoteCallables.onGuiClose"
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
    
    // change background color
    @Override
    public void renderTransparentBackground(GuiGraphics guiGraphics) {
        guiGraphics.fillGradient(
            0, 0, this.width, this.height, 0x21000000, 0x21000000
        );
    }
}
