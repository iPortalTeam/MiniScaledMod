package qouteall.mini_scaled;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import qouteall.imm_ptl.core.render.PortalEntityRenderer;

public class MiniScaledModInitializerClient implements ClientModInitializer {
    private static void initClient() {
        EntityRendererRegistry.register(
            MiniScaledPortal.entityType,
            (context) -> new PortalEntityRenderer(context)
        );
    
        ClientScaleBoxInteractionControl.init();
    }
    
    @Override
    public void onInitializeClient() {
        initClient();
    }
}
