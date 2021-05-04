package qouteall.mini_scaled.mixin.client;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.client.render.SkyProperties;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.mini_scaled.VoidDimension;

@Mixin(SkyProperties.class)
public class MixinSkyProperties {
    @Shadow
    @Final
    private static Object2ObjectMap<Identifier, SkyProperties> BY_IDENTIFIER;
    
    static {
        BY_IDENTIFIER.put(
            new Identifier("mini_scaled:cloudless"),
            new VoidDimension.VoidSkyProperties()
        );
    }
}
