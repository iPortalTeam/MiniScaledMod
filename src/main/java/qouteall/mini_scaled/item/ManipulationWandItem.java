package qouteall.mini_scaled.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.mini_scaled.ScaleBoxGeneration;
import qouteall.mini_scaled.ScaleBoxManipulation;
import qouteall.mini_scaled.ScaleBoxRecord;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlockEntity;
import qouteall.mini_scaled.gui.ScaleBoxGuiManager;
import qouteall.mini_scaled.util.MSUtil;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class ManipulationWandItem extends Item {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManipulationWandItem.class);
    
    public static final ManipulationWandItem instance = new ManipulationWandItem(new Item.Properties());
    
    public static void init() {
        Registry.register(
            BuiltInRegistries.ITEM,
            new ResourceLocation("mini_scaled:manipulation_wand"),
            instance
        );
    }
    
    public ManipulationWandItem(Properties properties) {
        super(properties);
    }
    
    public static void registerCreativeInventory(Consumer<ItemStack> func) {
        ItemStack itemStack = new ItemStack(instance);
        func.accept(itemStack);
    }
    
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);
        
        if (level.isClientSide()) {
            return new InteractionResultHolder<>(InteractionResult.SUCCESS, itemStack);
        }
        
        ScaleBoxGuiManager.get(player.getServer()).openGui(((ServerPlayer) player), null);
        
        return new InteractionResultHolder<>(InteractionResult.SUCCESS, itemStack);
    }
    
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        if (world.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        
        Player player = context.getPlayer();
        
        if (player == null) {
            return InteractionResult.FAIL;
        }
        
        @Nullable ScaleBoxRecord.Entry entry = null;
        
        BlockPos clickedPos = context.getClickedPos();
        BlockState blockState = world.getBlockState(clickedPos);
        if (blockState.getBlock() == ScaleBoxPlaceholderBlock.instance) {
            BlockEntity blockEntity = world.getBlockEntity(clickedPos);
            
            if (blockEntity instanceof ScaleBoxPlaceholderBlockEntity placeholderBlockEntity) {
                int boxId = placeholderBlockEntity.boxId;
                ScaleBoxRecord scaleBoxRecord = ScaleBoxRecord.get();
                ScaleBoxRecord.Entry theEntry = scaleBoxRecord.getEntryById(boxId);
                if (theEntry != null && Objects.equals(theEntry.ownerId, player.getUUID())) {
                    entry = theEntry;
                }
            }
        }
        
        ScaleBoxGuiManager.get(player.getServer()).openGui(
            ((ServerPlayer) player),
            entry == null ? null : entry.id
        );
        
        return InteractionResult.FAIL;
    }
    
    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.mini_scaled.manipulation_wand");
    }
}
