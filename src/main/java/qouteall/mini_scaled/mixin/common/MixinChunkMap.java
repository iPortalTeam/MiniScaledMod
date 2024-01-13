package qouteall.mini_scaled.mixin.common;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.mini_scaled.VoidDimension;

@Mixin(ChunkMap.class)
public class MixinChunkMap {
    @Shadow @Final private ServerLevel level;
    
    // that method also controls whether to do chunk tick
    // chunk tick includes block random tick
    // make it to always do chunk ticking in MiniScaled void dimension
    // (so that crops can grow without the player being inside)
    @Inject(method = "anyPlayerCloseEnoughForSpawning", at = @At("HEAD"), cancellable = true)
    private void onAnyPlayerCloseEnoughForSpawning(ChunkPos chunkPos, CallbackInfoReturnable<Boolean> cir) {
        if (level.dimension() == VoidDimension.KEY) {
            cir.setReturnValue(true);
        }
    }
}
