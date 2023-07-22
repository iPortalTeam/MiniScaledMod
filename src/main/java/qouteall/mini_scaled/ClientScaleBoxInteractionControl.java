package qouteall.mini_scaled;

import com.mojang.datafixers.util.Pair;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.commands.PortalCommand;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.mini_scaled.config.ScaleBoxInteractionMode;
import qouteall.mini_scaled.item.ManipulationWandItem;

@Environment(EnvType.CLIENT)
public class ClientScaleBoxInteractionControl {
    public static final double innerInteractionDistance = 2.5;
    
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            update();
        });
    }
    
    private static boolean canInteractInsideScaleBox = false;
    
    public static boolean canInteractInsideScaleBox() {
        return canInteractInsideScaleBox;
    }
    
    private static void update() {
        canInteractInsideScaleBox = false;
        
        Minecraft client = Minecraft.getInstance();
        
        if (client.player == null) {
            return;
        }
        
        if (client.player.getMainHandItem().getItem() == ManipulationWandItem.instance) {
            return;
        }
        
        ScaleBoxInteractionMode interactionMode = MSGlobal.config.getConfig().interactionMode;
        
        switch (interactionMode) {
            case normal -> {
                updateOnNormalMode(client);
            }
            case crouchForInside -> {
                canInteractInsideScaleBox = client.player.getPose() == Pose.CROUCHING;
            }
            case crouchForOutside -> {
                canInteractInsideScaleBox = client.player.getPose() != Pose.CROUCHING;
            }
        }
    }
    
    private static void updateOnNormalMode(Minecraft client) {
        assert client.player != null;
        Vec3 cameraPos = client.player.getEyePosition();
        Pair<Portal, Vec3> raytraceResult = PortalCommand.raytracePortals(
            client.player.level(),
            cameraPos,
            cameraPos.add(client.player.getViewVector(1).scale(10)),
            false
        ).orElse(null);
        
        if (raytraceResult != null) {
            Portal portal = raytraceResult.getFirst();
            if (portal instanceof MiniScaledPortal miniScaledPortal) {
                if (miniScaledPortal.isOuterPortal()) {
                    double distance = miniScaledPortal.getDistanceToNearestPointInPortal(cameraPos);
                    if (distance < innerInteractionDistance) {
                        canInteractInsideScaleBox = true;
                    }
                }
            }
        }
    }
    
    
}
