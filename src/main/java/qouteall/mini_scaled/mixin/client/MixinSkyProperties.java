package qouteall.mini_scaled.mixin.client;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.mini_scaled.VoidDimension;

@Mixin(DimensionEffects.class)
public class MixinSkyProperties {
    @Shadow
    @Final
    private static Object2ObjectMap<Identifier, DimensionEffects> BY_IDENTIFIER;
    
    static {
        BY_IDENTIFIER.put(
            new Identifier("mini_scaled:cloudless"),
            new VoidDimension.VoidSkyProperties()
        );
    }
}
