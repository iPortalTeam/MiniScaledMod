package qouteall.mini_scaled.mixin.client;

import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.item.ItemColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.mini_scaled.ScaleBoxEntranceItem;

@Mixin(ItemColors.class)
public class MixinItemColors {
    @Inject(method = "create", at = @At("RETURN"), cancellable = true)
    private static void onCreate(BlockColors blockColors, CallbackInfoReturnable<ItemColors> cir) {
        ItemColors itemColors = cir.getReturnValue();
        itemColors.register(
            (stack, tintIndex) -> ScaleBoxEntranceItem.getRenderingColor(stack),
            ScaleBoxEntranceItem.instance
        );
    }

//    @Inject(method = "getColor", at = @At("HEAD"), cancellable = true)
//    private void onGetColor(ItemStack item, int tintIndex, CallbackInfoReturnable<Integer> cir) {
//        if (item.getItem() == ScaleBoxItem.instance) {
//            int renderingColor = ScaleBoxItem.getRenderingColor(item);
//            cir.setReturnValue(renderingColor);
//        }
//    }
}
