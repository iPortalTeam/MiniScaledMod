package qouteall.mini_scaled;

import com.qouteall.immersive_portals.api.IPDimensionAPI;
import net.fabricmc.api.ModInitializer;
import qouteall.mini_scaled.block.BoxBarrierBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlockEntity;

public class MiniScaledModInitializer implements ModInitializer {
    
    @Override
    public void onInitialize() {
        
        IPDimensionAPI.onServerWorldInit.connect(VoidDimension::initializeVoidDimension);
        
        ScaleBoxPlaceholderBlock.init();
        
        BoxBarrierBlock.init();
        
        ScaleBoxPlaceholderBlockEntity.init();
        
        MiniScaledPortal.init();
        
        ScaleBoxItem.init();
        
        ScaleBoxCraftingRecipe.init();
        
        System.out.println("MiniScaled Mod Initializing");
    }
}
