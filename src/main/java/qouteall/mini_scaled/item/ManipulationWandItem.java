package qouteall.mini_scaled.item;

import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.mini_scaled.ScaleBoxGeneration;
import qouteall.mini_scaled.ScaleBoxManipulation;
import qouteall.mini_scaled.ScaleBoxRecord;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlockEntity;
import qouteall.mini_scaled.util.MSUtil;

import javax.annotation.Nullable;
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
    
    public static enum Mode {
        none, expand, shrink, toggleScaleChange, toggleGravityChange;
        
        public Mode next() {
            Mode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
        
        public MutableComponent getText() {
            return Component.translatable("mini_scaled.manipulation_wand.mode." + name());
        }
    }
    
    public ManipulationWandItem(Properties properties) {
        super(properties);
    }
    
    public static Mode getModeFromNbt(@Nullable CompoundTag tag) {
        if (tag == null) {
            return Mode.none;
        }
        else {
            return Mode.valueOf(tag.getString("mode"));
        }
    }
    
    public static CompoundTag modeToNbt(Mode mode) {
        CompoundTag tag = new CompoundTag();
        tag.putString("mode", mode.name());
        return tag;
    }
    
    public static void registerCreativeInventory(Consumer<ItemStack> func) {
        ItemStack itemStack = new ItemStack(instance);
        itemStack.setTag(modeToNbt(Mode.none));
        func.accept(itemStack);
    }
    
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);
        
        if (level.isClientSide()) {
            return new InteractionResultHolder<>(InteractionResult.SUCCESS, itemStack);
        }
        
        Mode mode = getModeFromNbt(itemStack.getTag());
        Mode nextMode = mode.next();
        itemStack.setTag(modeToNbt(nextMode));
        
        return new InteractionResultHolder<>(InteractionResult.SUCCESS, itemStack);
    }
    
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        
        Player player = context.getPlayer();
        
        if (player == null) {
            return InteractionResult.FAIL;
        }
        
        ServerLevel world = (ServerLevel) context.getLevel();
        
        ItemStack itemInHand = context.getItemInHand();
        Mode mode = getModeFromNbt(itemInHand.getTag());
        
        if (mode == Mode.none) {
            player.displayClientMessage(
                Component.translatable("mini_scaled.manipulation_wand.tip"),
                true
            );
            return InteractionResult.SUCCESS;
        }
        
        BlockPos clickedPos = context.getClickedPos();
        BlockState blockState = world.getBlockState(clickedPos);
        if (blockState.getBlock() != ScaleBoxPlaceholderBlock.instance) {
            player.displayClientMessage(Component.translatable("mini_scaled.manipulation_wand.use_on_scale_box"), true);
            return InteractionResult.FAIL;
        }
        BlockEntity blockEntity = world.getBlockEntity(clickedPos);
        if (!(blockEntity instanceof ScaleBoxPlaceholderBlockEntity placeholderBlockEntity)) {
            LOGGER.error("Scale box block entity not found");
            return InteractionResult.FAIL;
        }
        
        int boxId = placeholderBlockEntity.boxId;
        ScaleBoxRecord scaleBoxRecord = ScaleBoxRecord.get();
        ScaleBoxRecord.Entry entry = scaleBoxRecord.getEntryById(boxId);
        
        if (entry == null) {
            LOGGER.error("Cannot find scale box record entry {}", boxId);
            return InteractionResult.FAIL;
        }
        
        Direction outerSide = context.getClickedFace();
        
        switch (mode) {
            case expand -> {
                return ScaleBoxManipulation.tryToExpandScaleBox(
                    player,
                    world,
                    entry,
                    outerSide,
                    (requiredEntranceItemNum) -> {
                        if (player.isCreative()) {
                            return true;
                        }
                        
                        Inventory inventory = player.getInventory();
                        boolean has = MSUtil.removeIfHas(inventory,
                            s -> {
                                Item item = s.getItem();
                                if (item != ScaleBoxEntranceItem.instance) {
                                    return false;
                                }
                                ScaleBoxEntranceItem.ItemInfo itemInfo =
                                    new ScaleBoxEntranceItem.ItemInfo(s.getOrCreateTag());
                                return itemInfo.color == entry.color &&
                                    itemInfo.scale == entry.scale &&
                                    Objects.equals(itemInfo.ownerId, entry.ownerId);
                            },
                            requiredEntranceItemNum
                        );
                        if (!has) {
                            player.displayClientMessage(
                                Component.translatable("mini_scaled.cannot_expand_not_enough", requiredEntranceItemNum),
                                false
                            );
                        }
                        return has;
                    }
                );
            }
            case shrink -> {
                return ScaleBoxManipulation.tryToShrinkScaleBox(
                    player,
                    world,
                    entry,
                    outerSide
                );
            }
            case toggleScaleChange -> {
                entry.teleportChangesScale = !entry.teleportChangesScale;
                ScaleBoxGeneration.updateScaleBoxPortals(entry);
                player.displayClientMessage(
                    Component.translatable(
                        entry.teleportChangesScale ?
                            "mini_scaled.manipulation_wand.scale_change.enabled" :
                            "mini_scaled.manipulation_wand.scale_change.disabled"
                    ),
                    true
                );
                return InteractionResult.SUCCESS;
            }
            case toggleGravityChange -> {
                entry.teleportChangesGravity = !entry.teleportChangesGravity;
                ScaleBoxGeneration.updateScaleBoxPortals(entry);
                player.displayClientMessage(
                    Component.translatable(
                        entry.teleportChangesGravity ?
                            "mini_scaled.manipulation_wand.gravity_change.enabled" :
                            "mini_scaled.manipulation_wand.gravity_change.disabled"
                    ),
                    true
                );
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.FAIL;
    }
    
    @Override
    public void appendHoverText(ItemStack stack, Level world, List<Component> tooltip, TooltipFlag context) {
        Mode mode = getModeFromNbt(stack.getTag());
        tooltip.add(Component.translatable("mini_scaled.manipulation_wand.tip"));
    }
    
    @Override
    public Component getName(ItemStack stack) {
        Mode mode = getModeFromNbt(stack.getTag());
        
        MutableComponent baseText = Component.translatable("item.mini_scaled.manipulation_wand");
        
        if (mode == Mode.none) {
            return baseText;
        }
        
        return baseText
            .append(Component.literal(" : "))
            .append(mode.getText().withStyle(ChatFormatting.GOLD));
    }
}
