package qouteall.mini_scaled;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.mc_utils.ServerTaskList;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;
import qouteall.mini_scaled.util.MSUtil;

@SuppressWarnings("SpellCheckingInspection")
public class FallenEntityTeleportaion {
    private static final Logger LOGGER = LoggerFactory.getLogger(FallenEntityTeleportaion.class);
    
    static void teleportFallenEntities(MinecraftServer server) {
        server.getProfiler().push("mini_scaled_tick");
        
        ServerLevel voidWorld = server.getLevel(VoidDimension.KEY);
        if (voidWorld != null) {
            for (Entity entity : voidWorld.getAllEntities()) {
                teleportFallenEntity(entity);
            }
        }
        
        server.getProfiler().pop();
    }
    
    private static void teleportFallenEntity(Entity entity) {
        if (entity == null) {
            // cannot reproduce the crash stably
            return;
        }
        
        if (entity instanceof ServerPlayer player) {
            BlockPos blockPos = player.blockPosition();
            BlockPos scaleBoxPos = ScaleBoxGeneration.getNearestPosInScaleBoxToTeleportTo(blockPos);
            
            int horizontalDistance = Math.max(
                Math.abs(blockPos.getX() - scaleBoxPos.getX()),
                Math.abs(blockPos.getZ() - scaleBoxPos.getZ())
            );
            // too far from the nearest scale box position
            if (horizontalDistance > ScaleBoxManipulation.MAX_INNER_LEN + 10) {
                ServerTeleportationManager.teleportEntityGeneral(
                    entity,
                    Vec3.atCenterOf(scaleBoxPos),
                    ((ServerLevel) entity.level())
                );
                
                LOGGER.info("Player {} tries to go to another scale box's region directly", player.getName().getString());
                
                ServerTaskList.of(player.server).addTask(() -> {
                    player.displayClientMessage(
                        Component.literal("Going to another scale box's region directly is not allowed."),
                        false
                    );
                    return true;
                });
            }
            else if (player.getY() < 32) {
                ServerLevel overworld = McHelper.getOverWorldOnServer();
                
                ServerTeleportationManager.teleportEntityGeneral(
                    player, Vec3.atCenterOf(MSUtil.getSpawnPos(overworld)), overworld
                );
                
                ServerTaskList.of(player.server).addTask(() -> {
                    player.displayClientMessage(
                        Component.translatable("mini_scaled.return_to_spawn"),
                        false
                    );
                    return true;
                });
            }
        }
        
        if (entity.getY() < 32) {
            BlockPos blockPos = entity.blockPosition();
            
            BlockPos newPos = ScaleBoxGeneration.getNearestPosInScaleBoxToTeleportTo(blockPos);
            
            entity.setDeltaMovement(Vec3.ZERO);
            
            ServerTeleportationManager.teleportEntityGeneral(
                entity,
                Vec3.atCenterOf(newPos),
                ((ServerLevel) entity.level())
            );
        }
    }
}

