package qouteall.mini_scaled;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.imm_ptl.core.IPGlobal;
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
        
        ServerTickEvents.END_SERVER_TICK.register(FallenEntityTeleportaion::teleportFallenEntities);
        
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
        
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
            .register(entries -> {
                ManipulationWandItem.registerCreativeInventory(entries::accept);
                ScaleBoxEntranceItem.registerCreativeInventory(entries::accept);
            });
        
        LOGGER.info("MiniScaled Mod Initializing");
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
                ScaleBoxEntranceCreation.creationItem = Items.NETHERITE_INGOT;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void applyConfigClientSide(MiniScaledConfig miniScaledConfig) {
    
    }
}
