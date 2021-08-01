package qouteall.mini_scaled;

import net.fabricmc.api.ModInitializer;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.mini_scaled.block.BoxBarrierBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlockEntity;
import qouteall.q_misc_util.api.DimensionAPI;

public class MiniScaledModInitializer implements ModInitializer {
    
    @Override
    public void onInitialize() {
        
        DimensionAPI.serverDimensionsLoadEvent.register(VoidDimension::initializeVoidDimension);
        
        ScaleBoxPlaceholderBlock.init();
        
        BoxBarrierBlock.init();
        
        ScaleBoxPlaceholderBlockEntity.init();
        
        MiniScaledPortal.init();
        
        ScaleBoxItem.init();
        
        ScaleBoxCraftingRecipe.init();
        
        IPGlobal.enableDepthClampForPortalRendering = true;
        
        System.out.println("MiniScaled Mod Initializing");
    }
}
