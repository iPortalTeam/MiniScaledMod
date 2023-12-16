package qouteall.mini_scaled.mixin.client;

import net.minecraft.client.color.item.ItemColors;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemColors.class)
public class MixinItemColors {
//    @Inject(method = "createDefault", at = @At("RETURN"))
//    private static void onCreate(BlockColors blockColors, CallbackInfoReturnable<ItemColors> cir) {
//        ItemColors itemColors = cir.getReturnValue();
//        itemColors.register(
//            (stack, tintIndex) -> ScaleBoxEntranceItem.getRenderingColor(stack),
//            ScaleBoxEntranceItem.INSTANCE
//        );
//    }
}
