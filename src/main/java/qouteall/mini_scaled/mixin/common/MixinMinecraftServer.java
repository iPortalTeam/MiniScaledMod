package qouteall.mini_scaled.mixin.common;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import qouteall.mini_scaled.ducks.MiniScaled_MinecraftServerAccessor;
import qouteall.mini_scaled.gui.ScaleBoxGuiManager;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServer implements MiniScaled_MinecraftServerAccessor {
    @Unique
    private ScaleBoxGuiManager miniScaled_scaleBoxGuiManager;
    
    @Override
    public ScaleBoxGuiManager miniScaled_getScaleBoxGuiManager() {
        if (miniScaled_scaleBoxGuiManager == null) {
            miniScaled_scaleBoxGuiManager = new ScaleBoxGuiManager((MinecraftServer) (Object) this);
        }
        
        return miniScaled_scaleBoxGuiManager;
    }
}
