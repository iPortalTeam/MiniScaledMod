package qouteall.mini_scaled;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;
import qouteall.mini_scaled.block.BoxBarrierBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlockEntity;
import qouteall.q_misc_util.api.DimensionAPI;

import java.util.Optional;

public class MiniScaledModInitializer implements ModInitializer {
    
    @Override
    public void onInitialize() {
        
        DimensionAPI.serverDimensionsLoadEvent.register(VoidDimension::initializeVoidDimension);
        
        ScaleBoxPlaceholderBlock.init();
        
        BoxBarrierBlock.init();
        
        ScaleBoxPlaceholderBlockEntity.init();
        
        MiniScaledPortal.init();
        
        ScaleBoxItem.init();
        
        ScaleBoxCraftingRecipe.init();
        
        IPGlobal.enableDepthClampForPortalRendering = true;
        
        ServerTickEvents.END_SERVER_TICK.register(MiniScaledModInitializer::teleportFallenEntities);
        
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
                        new LiteralText("You fell off the scale box. Returned to the spawn point"), false);
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
