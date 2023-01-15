package qouteall.mini_scaled.mixin.client;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.mini_scaled.VoidDimension;

@Mixin(DimensionSpecialEffects.class)
public class MixinSkyProperties {
    @Shadow
    @Final
    private static Object2ObjectMap<ResourceLocation, DimensionSpecialEffects> EFFECTS;
    
    static {
        EFFECTS.put(
            new ResourceLocation("mini_scaled:cloudless"),
            new VoidDimension.VoidSkyProperties()
        );
    }
}
