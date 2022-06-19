package qouteall.mini_scaled;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralTextContent;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;
import qouteall.mini_scaled.block.BoxBarrierBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlockEntity;
import qouteall.q_misc_util.LifecycleHack;
import qouteall.q_misc_util.MiscHelper;
import qouteall.q_misc_util.api.DimensionAPI;

public class MiniScaledModInitializer implements ModInitializer {
    
    @Override
    public void onInitialize() {
        
        DimensionAPI.serverDimensionsLoadEvent.register(VoidDimension::initializeVoidDimension);
        LifecycleHack.markNamespaceStable("mini_scaled");
        
        ScaleBoxPlaceholderBlock.init();
        
        BoxBarrierBlock.init();
        
        ScaleBoxPlaceholderBlockEntity.init();
        
        MiniScaledPortal.init();
        
        ScaleBoxEntranceItem.init();
        
        ScaleBoxCraftingRecipe.init();
        
        ScaleBoxEntranceCreation.init();
        
        IPGlobal.enableDepthClampForPortalRendering = true;
        
        ServerTickEvents.END_SERVER_TICK.register(MiniScaledModInitializer::teleportFallenEntities);
        
        UseBlockCallback.EVENT.register((PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) -> {
            Block block = world.getBlockState(hitResult.getBlockPos()).getBlock();
            if (block == ScaleBoxPlaceholderBlock.instance) {
                return ScaleBoxEntranceItem.onRightClickScaleBox(player, world, hand, hitResult);
            }
            
            return ActionResult.PASS;
        });
        
        System.out.println("MiniScaled Mod Initializing");
    }
    
    private static void teleportFallenEntities(MinecraftServer server) {
        server.getProfiler().push("mini_scaled_tick");
        
        ServerWorld voidWorld = server.getWorld(VoidDimension.dimensionId);
        if (voidWorld != null) {
            for (Entity entity : voidWorld.iterateEntities()) {
                teleportFallenEntity(entity);
            }
        }
        
        server.getProfiler().pop();
    }
    
    private static void teleportFallenEntity(Entity entity) {
        if (entity == null) {
            // cannot reproduce the crash stably
            // TODO debug it
            return;
        }
        
        if (entity.getY() < 32) {
            System.out.println("Entity fallen from scale box " + entity);
            
            if (entity instanceof ServerPlayerEntity) {
                ServerPlayerEntity player = (ServerPlayerEntity) entity;
                
                ServerWorld overworld = player.server.getWorld(World.OVERWORLD);
                
                ServerTeleportationManager.teleportEntityGeneral(
                    player, Vec3d.ofCenter(overworld.getSpawnPos()), overworld
                );
                
                IPGlobal.serverTaskList.addTask(() -> {
                    player.sendMessage(
                        Text.literal("You fell off the scale box. Returned to the spawn point"),
                        false
                    );
                    return true;
                });
            }
            else {
                BlockPos blockPos = entity.getBlockPos();
                
                BlockPos newPos = ScaleBoxGeneration.getNearestPosInScaleBoxToTeleportTo(blockPos);
                
                entity.setVelocity(Vec3d.ZERO);
                
                ServerTeleportationManager.teleportEntityGeneral(
                    entity,
                    Vec3d.ofCenter(newPos),
                    ((ServerWorld) entity.world)
                );
            }
        }
    }
}
