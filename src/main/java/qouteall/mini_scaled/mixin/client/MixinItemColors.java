package qouteall.mini_scaled.mixin.client;

import net.minecraft.client.color.item.ItemColors;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.mini_scaled.ScaleBoxItem;

@Mixin(ItemColors.class)
public class MixinItemColors {
    @Inject(method = "getColor", at = @At("HEAD"), cancellable = true)
    private void onGetColor(ItemStack item, int tintIndex, CallbackInfoReturnable<Integer> cir) {
        if (item.getItem() == ScaleBoxItem.instance) {
            int renderingColor = ScaleBoxItem.getRenderingColor(item);
            cir.setReturnValue(renderingColor);
        }
    }
}
