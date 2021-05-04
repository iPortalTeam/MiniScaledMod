package qouteall.mini_scaled;

import com.qouteall.immersive_portals.render.PortalEntityRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendereregistry.v1.EntityRendererRegistry;

public class MiniScaledModInitializerClient implements ClientModInitializer {
    private static void initClient() {
        EntityRendererRegistry.INSTANCE.register(
            MiniScaledPortal.entityType,
            (entityRenderDispatcher, context) -> new PortalEntityRenderer(entityRenderDispatcher)
        );
    }
    
    @Override
    public void onInitializeClient() {
        initClient();
    }
}
