package qouteall.mini_scaled;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.mini_scaled.block.BoxBarrierBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlockEntity;
import qouteall.mini_scaled.config.MiniScaledConfig;
import qouteall.mini_scaled.gui.ScaleBoxInteractionManager;
import qouteall.mini_scaled.item.ManipulationWandItem;
import qouteall.mini_scaled.item.ScaleBoxEntranceItem;

public class MiniScaledModInitializer implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MiniScaledModInitializer.class);
    
    public static final CreativeModeTab TAB =
        FabricItemGroup.builder()
            .icon(() -> new ItemStack(ManipulationWandItem.INSTANCE))
            .title(Component.translatable("mini_scaled.item_group"))
            .displayItems((enabledFeatures, entries) -> {
                ManipulationWandItem.registerCreativeInventory(entries::accept);
            })
            .build();
    
    @Override
    public void onInitialize() {
        LOGGER.info("MiniScaled Mod Initializing");
        
        VoidDimension.init();
        
        DimensionAPI.suppressExperimentalWarningForNamespace("mini_scaled");
        
        ScaleBoxPlaceholderBlock.init();
        
        BoxBarrierBlock.init();
        
        ScaleBoxPlaceholderBlockEntity.init();
        
        MiniScaledPortal.init();
        
        ScaleBoxEntranceItem.init();
        
        ManipulationWandItem.init();
        
        ScaleBoxEntranceCreation.init();
        
        ScaleBoxInteractionManager.init();
        
        IPGlobal.enableDepthClampForPortalRendering = true;
        
        ServerTickEvents.END_SERVER_TICK.register(FallenEntityTeleportaion::teleportFallenEntities);
        
        // config
        MSGlobal.config = AutoConfig.register(MiniScaledConfig.class, GsonConfigSerializer::new);
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            MiniScaledConfig config = AutoConfig.getConfigHolder(MiniScaledConfig.class).getConfig();
            applyConfigServerSide(config);
        });
        AutoConfig.getConfigHolder(MiniScaledConfig.class).registerSaveListener((configHolder, config) -> {
            applyConfigServerSide(config);
            applyConfigClientSide(config);
            return InteractionResult.PASS;
        });
        
        Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            new ResourceLocation("mini_scaled", "mini_scaled"),
            TAB
        );
        
        CommandRegistrationCallback.EVENT.register(
            (dispatcher, ctx, environment) -> MiniScaledCommand.register(dispatcher, ctx)
        );
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
            LOGGER.error("", e);
        }
    }
    
    public static void applyConfigClientSide(MiniScaledConfig miniScaledConfig) {
    
    }
}
