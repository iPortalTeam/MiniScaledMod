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
import org.joml.Matrix4f;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.portal.animation.TimingFunction;
import qouteall.imm_ptl.core.render.GuiPortalRendering;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.mini_scaled.VoidDimension;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.api.McRemoteProcedureCall;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.animation.Animated;
import qouteall.q_misc_util.my_util.animation.RenderedPoint;

@Environment(EnvType.CLIENT)
public class ScaleBoxManagementScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static RenderTarget frameBuffer;
    
    public int boxId = -1;
    public Vec3 viewingCenter;
    
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
    
    public ScaleBoxManagementScreen() {
        super(Component.literal("Scale box management"));
        
        if (frameBuffer == null) {
            // the framebuffer size doesn't matter here
            // because it will be automatically resized when rendering
            frameBuffer = new TextureTarget(2, 2, true, true);
        }
    }
    
    public static void openGui(int boxId, Vec3 pos) {
        ScaleBoxManagementScreen screen = new ScaleBoxManagementScreen();
        screen.boxId = boxId;
        screen.viewingCenter = pos;
        
        Minecraft.getInstance().setScreen(screen);
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);
        
        Double pitch = pitchAnim.getCurrent();
        Double yaw = yawAnim.getCurrent();
        Double distance = distanceAnim.getCurrent();
        
        assert pitch != null;
        assert yaw != null;
        assert distance != null;
        
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
            null,
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
            w * 0.2f, w * 0.8f,
            h * 0.2f, h * 0.8f
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
        LOGGER.info("mouse scrolled {} {} {}", mouseX, mouseY, delta);
        
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
