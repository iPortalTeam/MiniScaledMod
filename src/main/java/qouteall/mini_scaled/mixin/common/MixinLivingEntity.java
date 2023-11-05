package qouteall.mini_scaled.mixin.common;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.mini_scaled.VoidDimension;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {
    // do not apply fall damage in the scale box dimension
    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void onHandleFallDamage(
        float fallDistance, float damageMultiplier, DamageSource damageSource,
        CallbackInfoReturnable<Boolean> cir
    ) {
        LivingEntity this_ = (LivingEntity) ((Object) this);
        if (this_.level().dimension() == VoidDimension.KEY) {
            cir.cancel();
        }
    }
}
