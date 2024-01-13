package qouteall.mini_scaled.mixin.common;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.mini_scaled.VoidDimension;

@Mixin(Level.class)
public class MixinLevel {
    // the time in MiniScaled void dimension is always day
    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    private void onGetDayTime(CallbackInfoReturnable<Long> cir) {
        Level this_ = (Level) (Object) this;
        if (this_.dimension() == VoidDimension.KEY) {
            cir.setReturnValue(1000L);
        }
    }
}
