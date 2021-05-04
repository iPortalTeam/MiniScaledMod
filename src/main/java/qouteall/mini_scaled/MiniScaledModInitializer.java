package qouteall.mini_scaled;

import com.qouteall.immersive_portals.api.IPDimensionAPI;
import net.fabricmc.api.ModInitializer;

public class MiniScaledModInitializer implements ModInitializer {
    
    @Override
    public void onInitialize() {
        
        IPDimensionAPI.onServerWorldInit.connect(VoidDimension::initializeVoidDimension);
        
        ScaleBoxPlaceholderBlock.init();
        
        ScaleBoxPlaceholderBlockEntity.init();
        
        MiniScaledPortal.init();
        
        ScaleBoxItem.init();
        
        System.out.println("MiniScaled Mod Initializing");
    }
}
