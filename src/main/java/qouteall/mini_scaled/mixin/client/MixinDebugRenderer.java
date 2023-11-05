package qouteall.mini_scaled.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.mini_scaled.ClientUnwrappingInteraction;
import qouteall.mini_scaled.item.ManipulationWandItem;

@Mixin(DebugRenderer.class)
public class MixinDebugRenderer {
    @Inject(
        method = "render",
        at = @At("RETURN")
    )
    private void onRender(
        PoseStack poseStack,
        MultiBufferSource.BufferSource bufferSource,
        double camX, double camY, double camZ,
        CallbackInfo ci
    ) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        
        ItemStack itemStack = player.getMainHandItem();
        
        if (itemStack.getItem() == ManipulationWandItem.INSTANCE) {
            ClientUnwrappingInteraction.clientRender(
                player, itemStack, poseStack, bufferSource, camX, camY, camZ
            );
        }
    }
}
