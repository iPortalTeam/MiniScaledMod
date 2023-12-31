package qouteall.mini_scaled.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.mini_scaled.block.BoxBarrierBlock;

@Mixin(FlowingFluid.class)
public class MixinFlowingFluid {
    /**
     * Vanilla hardcoded the block types that has non-full collision and blocks fluid.
     * So I cannot make the barrier fluid-proof by overriding a method.
     */
    @Inject(method = "canHoldFluid", at = @At("HEAD"), cancellable = true)
    private void onCanHoldFluid(
        BlockGetter level, BlockPos pos, BlockState state, Fluid fluid,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (state.getBlock() == BoxBarrierBlock.INSTANCE) {
            cir.setReturnValue(false);
        }
    }
}
