package qouteall.mini_scaled.mixin.common;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.mini_scaled.VoidDimension;

@Mixin(ServerLevel.class)
public abstract class MixinServerLevel {
    // make MiniScaled dimension always not-raining
    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void onAdvanceWeatherCycle(CallbackInfo ci) {
        ServerLevel this_ = (ServerLevel) (Object) this;
        if (this_.dimension() == VoidDimension.KEY) {
            this_.setWeatherParameters(
                180000, 0, false, false
            );
            ci.cancel();
        }
    }
}
