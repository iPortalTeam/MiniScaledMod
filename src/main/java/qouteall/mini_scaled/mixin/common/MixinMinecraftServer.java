package qouteall.mini_scaled.mixin.common;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import qouteall.mini_scaled.ducks.MiniScaled_MinecraftServerAccessor;
import qouteall.mini_scaled.gui.ScaleBoxInteractionManager;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServer implements MiniScaled_MinecraftServerAccessor {
    @Unique
    private ScaleBoxInteractionManager miniScaled_scaleBoxInteractionManager;
    
    @Override
    public ScaleBoxInteractionManager miniScaled_getScaleBoxGuiManager() {
        if (miniScaled_scaleBoxInteractionManager == null) {
            miniScaled_scaleBoxInteractionManager = new ScaleBoxInteractionManager((MinecraftServer) (Object) this);
        }
        
        return miniScaled_scaleBoxInteractionManager;
    }
}
