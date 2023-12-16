package qouteall.mini_scaled;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.resources.ResourceLocation;
import qouteall.imm_ptl.core.render.PortalEntityRenderer;
import qouteall.mini_scaled.gui.ScaleBoxManagementScreen;
import qouteall.mini_scaled.item.ScaleBoxEntranceItem;

public class MiniScaledModInitializerClient implements ClientModInitializer {
    private static void initClient() {
        EntityRendererRegistry.register(
            MiniScaledPortal.ENTITY_TYPE,
            (context) -> new PortalEntityRenderer(context)
        );
        
        ClientScaleBoxInteractionControl.init();
        
        ScaleBoxManagementScreen.init_();
        
        ClientUnwrappingInteraction.init();
        
        ColorProviderRegistry.ITEM.register(
            (stack, tintIndex) -> ScaleBoxEntranceItem.getRenderingColor(stack),
            ScaleBoxEntranceItem.INSTANCE
        );
        
        DimensionRenderingRegistry.registerDimensionEffects(
            new ResourceLocation("mini_scaled:cloudless"),
            new VoidDimension.VoidSkyProperties()
        );
    }
    
    @Override
    public void onInitializeClient() {
        initClient();
    }
}
