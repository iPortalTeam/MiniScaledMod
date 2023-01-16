package qouteall.mini_scaled;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.impl.itemgroup.MinecraftItemGroups;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;
import qouteall.mini_scaled.block.BoxBarrierBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlockEntity;
import qouteall.mini_scaled.config.MiniScaledConfig;
import qouteall.mini_scaled.item.ManipulationWandItem;
import qouteall.mini_scaled.item.ScaleBoxEntranceItem;
import qouteall.q_misc_util.LifecycleHack;
import qouteall.q_misc_util.MiscHelper;
import qouteall.q_misc_util.api.DimensionAPI;
import qouteall.q_misc_util.my_util.LimitedLogger;

public class MiniScaledModInitializer implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MiniScaledModInitializer.class);
    private static final LimitedLogger LIMITED_LOGGER = new LimitedLogger(50);
    
    @Override
    public void onInitialize() {
        
        DimensionAPI.serverDimensionsLoadEvent.register(VoidDimension::initializeVoidDimension);
        LifecycleHack.markNamespaceStable("mini_scaled");
        
        ScaleBoxPlaceholderBlock.init();
        
        BoxBarrierBlock.init();
        
        ScaleBoxPlaceholderBlockEntity.init();
        
        MiniScaledPortal.init();
        
        ScaleBoxEntranceItem.init();
        
        ManipulationWandItem.init();
        
        ScaleBoxEntranceCreation.init();
        
        IPGlobal.enableDepthClampForPortalRendering = true;
        
        ServerTickEvents.END_SERVER_TICK.register(MiniScaledModInitializer::teleportFallenEntities);
        
        UseBlockCallback.EVENT.register((Player player, Level world, InteractionHand hand, BlockHitResult hitResult) -> {
            Block block = world.getBlockState(hitResult.getBlockPos()).getBlock();
            if (block == ScaleBoxPlaceholderBlock.instance) {
                return ScaleBoxManipulation.onHandRightClickEntrance(player, world, hand, hitResult);
            }
            
            return InteractionResult.PASS;
        });
        
        // config
        MSGlobal.config = AutoConfig.register(MiniScaledConfig.class, GsonConfigSerializer::new);
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            MiniScaledConfig config = AutoConfig.getConfigHolder(MiniScaledConfig.class).getConfig();
            applyConfigServerSide(config);
        });
        AutoConfig.getConfigHolder(MiniScaledConfig.class).registerSaveListener((configHolder, config) -> {
            if (MiscHelper.getServer() != null) {
                applyConfigServerSide(config);
            }
            applyConfigClientSide(config);
            return InteractionResult.PASS;
        });
        
        ItemGroupEvents.modifyEntriesEvent(MinecraftItemGroups.TOOLS_ID)
            .register(entries -> {
                ManipulationWandItem.registerCreativeInventory(entries::accept);
                ScaleBoxEntranceItem.registerCreativeInventory(entries::accept);
            });
        
        ClientScaleBoxInteractionControl.init();
        
        LOGGER.info("MiniScaled Mod Initializing");
    }
    
    private static void teleportFallenEntities(MinecraftServer server) {
        server.getProfiler().push("mini_scaled_tick");
        
        ServerLevel voidWorld = server.getLevel(VoidDimension.dimensionId);
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
        
        if (entity.getY() < 32) {
            LIMITED_LOGGER.invoke(() -> {
                LOGGER.info("Entity fallen from scale box " + entity);
            });
            
            if (entity instanceof ServerPlayer) {
                ServerPlayer player = (ServerPlayer) entity;
                
                ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
                
                ServerTeleportationManager.teleportEntityGeneral(
                    player, Vec3.atCenterOf(overworld.getSharedSpawnPos()), overworld
                );
                
                IPGlobal.serverTaskList.addTask(() -> {
                    player.displayClientMessage(
                        Component.literal("You fell off the scale box. Returned to the spawn point"),
                        false
                    );
                    return true;
                });
            }
            else {
                BlockPos blockPos = entity.blockPosition();
                
                BlockPos newPos = ScaleBoxGeneration.getNearestPosInScaleBoxToTeleportTo(blockPos);
                
                entity.setDeltaMovement(Vec3.ZERO);
                
                ServerTeleportationManager.teleportEntityGeneral(
                    entity,
                    Vec3.atCenterOf(newPos),
                    ((ServerLevel) entity.level)
                );
            }
        }
    }
    
    public static void applyConfigServerSide(MiniScaledConfig miniScaledConfig) {
        try {
            ResourceLocation identifier = new ResourceLocation(miniScaledConfig.creationItem);
            
            Item creationItem = BuiltInRegistries.ITEM.get(identifier);
            
            if (creationItem != Items.AIR) {
                ScaleBoxEntranceCreation.creationItem = creationItem;
            }
            else {
                LOGGER.error("Invalid scale box creation item {}", identifier);
                ScaleBoxEntranceCreation.creationItem = null;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void applyConfigClientSide(MiniScaledConfig miniScaledConfig) {
    
    }
}
