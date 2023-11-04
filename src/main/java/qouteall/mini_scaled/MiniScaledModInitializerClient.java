package qouteall.mini_scaled;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import qouteall.imm_ptl.core.render.PortalEntityRenderer;
import qouteall.mini_scaled.gui.ScaleBoxManagementScreen;

public class MiniScaledModInitializerClient implements ClientModInitializer {
    private static void initClient() {
        EntityRendererRegistry.register(
            MiniScaledPortal.entityType,
            (context) -> new PortalEntityRenderer(context)
        );
        
        ClientScaleBoxInteractionControl.init();
        
        ScaleBoxManagementScreen.init_();
        
        ClientUnwrappingInteraction.init();
    }
    
    @Override
    public void onInitializeClient() {
        initClient();
    }
}
