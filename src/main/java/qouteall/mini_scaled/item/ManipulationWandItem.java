package qouteall.mini_scaled.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.mini_scaled.GlassFrameMatching;
import qouteall.mini_scaled.ScaleBoxManipulation;
import qouteall.mini_scaled.ScaleBoxRecord;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlockEntity;
import qouteall.mini_scaled.gui.ScaleBoxGuiManager;
import qouteall.q_misc_util.my_util.IntBox;

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
//
//    public static enum Mode {
//        none, expand, shrink, toggleScaleChange, toggleGravityChange, toggleAccessControl;
//
//        public Mode next() {
//            Mode[] values = values();
//            return values[(ordinal() + 1) % values.length];
//        }
//
//        public MutableComponent getText() {
//            return Component.translatable("mini_scaled.manipulation_wand.mode." + name());
//        }
//
//        public String toStr() {
//            return switch (this) {
//                case expand -> "expand";
//                case shrink -> "shrink";
//                case toggleScaleChange -> "toggleScaleChange";
//                case toggleGravityChange -> "toggleGravityChange";
//                case toggleAccessControl -> "toggleAccessControl";
//                default -> "none";
//            };
//        }
//
//        public static Mode fromStr(String str) {
//            return switch (str) {
//                case "expand" -> expand;
//                case "shrink" -> shrink;
//                case "toggleScaleChange" -> toggleScaleChange;
//                case "toggleGravityChange" -> toggleGravityChange;
//                case "toggleAccessControl" -> toggleAccessControl;
//                default -> none;
//            };
//        }
//    }
    
    public ManipulationWandItem(Properties properties) {
        super(properties);
    }
    
    public static void registerCreativeInventory(Consumer<ItemStack> func) {
        ItemStack itemStack = new ItemStack(instance);
        func.accept(itemStack);
    }

//    public static Mode getModeFromNbt(@Nullable CompoundTag tag) {
//        if (tag == null) {
//            return Mode.none;
//        }
//        else {
//            return Mode.fromStr(tag.getString("mode"));
//        }
//    }
//
//    public static CompoundTag modeToNbt(Mode mode) {
//        CompoundTag tag = new CompoundTag();
//        tag.putString("mode", mode.toStr());
//        return tag;
//    }
    
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);
        
        if (level.isClientSide()) {
            return new InteractionResultHolder<>(InteractionResult.SUCCESS, itemStack);
        }
        
        // directly open the gui
        ScaleBoxGuiManager.get(player.getServer()).openManagementGui(((ServerPlayer) player), null);
        
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
        Block block = blockState.getBlock();
        
        ScaleBoxGuiManager scaleBoxGuiManager = ScaleBoxGuiManager.get(player.getServer());
        
        if (block == ScaleBoxPlaceholderBlock.instance) {
            BlockEntity blockEntity = world.getBlockEntity(clickedPos);
            
            if (blockEntity instanceof ScaleBoxPlaceholderBlockEntity placeholderBlockEntity) {
                int boxId = placeholderBlockEntity.boxId;
                ScaleBoxRecord scaleBoxRecord = ScaleBoxRecord.get(world.getServer());
                ScaleBoxRecord.Entry theEntry = scaleBoxRecord.getEntryById(boxId);
                if (theEntry != null && Objects.equals(theEntry.ownerId, player.getUUID())) {
                    entry = theEntry;
                }
            }
        }
        else if (block instanceof StainedGlassBlock stainedGlassBlock) {
            // TODO wrap scale box
            
            DyeColor color = stainedGlassBlock.getColor();
            
            IntBox glassFrame = GlassFrameMatching.matchGlassFrame(
                world, clickedPos,
                s -> world.getBlockState(s) == blockState,
                ScaleBoxManipulation.MAX_INNER_LEN
            );
            
            if (glassFrame != null) {
                scaleBoxGuiManager.tryStartingPendingWrapping(
                    ((ServerPlayer) player), world.dimension(),
                    glassFrame, color, clickedPos
                );
            }
            
            return InteractionResult.SUCCESS;
        }
        
        scaleBoxGuiManager.openManagementGui(
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
