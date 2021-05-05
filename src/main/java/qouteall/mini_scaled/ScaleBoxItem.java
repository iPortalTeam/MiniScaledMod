package qouteall.mini_scaled;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ScaleBoxItem extends Item {
    
    public static final ScaleBoxItem instance = new ScaleBoxItem(new Item.Settings().group(ItemGroup.MISC));
    
    public static void init() {
        Registry.register(
            Registry.ITEM,
            new Identifier("mini_scaled:scale_box_item"),
            instance
        );
    }
    
    public static class ItemInfo {
        public int size;
        public DyeColor color;
        
        public ItemInfo(int size, DyeColor color) {
            this.size = size;
            this.color = color;
        }
        
        public ItemInfo(CompoundTag tag) {
            size = tag.getInt("size");
            color = DyeColor.byName(tag.getString("color"), DyeColor.BLACK);
        }
        
        public void writeToTag(CompoundTag compoundTag) {
            compoundTag.putInt("size", size);
            compoundTag.putString("color", color.getName());
        }
    }
    
    public ScaleBoxItem(Settings settings) {
        super(settings);
    }
    
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        
        World world = context.getWorld();
        
        if (world.isClient()) {
            return ActionResult.FAIL;
        }
        
        if (context.getPlayer() == null) {
            return ActionResult.FAIL;
        }
        
        BlockPos pos = context.getBlockPos().offset(context.getSide());
        
        if (!world.isAir(pos)) {
            return ActionResult.FAIL;
        }
        
        ItemStack stack = context.getStack();
        ItemInfo itemInfo = new ItemInfo(stack.getOrCreateTag());
        
        ServerPlayerEntity player = (ServerPlayerEntity) context.getPlayer();
    
        int size = itemInfo.size;
        if (!ScaleBoxGeneration.isValidSize(size)) {
            player.sendMessage(new LiteralText("bad item data"), false);
            return ActionResult.FAIL;
        }
        
        ScaleBoxGeneration.putScaleBox(
            ((ServerWorld) world),
            player,
            size,
            pos,
            itemInfo.color
        );
        
        return ActionResult.SUCCESS;
    }
    
    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        ItemInfo itemInfo = new ItemInfo(stack.getOrCreateTag());
        tooltip.add(new LiteralText(itemInfo.color.getName()));
        tooltip.add(new LiteralText(Integer.toString(itemInfo.size)));
    }
    
    private static final int[] supportedSizes = {8, 16, 32};
    
    @Override
    public void appendStacks(ItemGroup group, DefaultedList<ItemStack> stacks) {
        if (this.isIn(group)) {
            for (int size : supportedSizes) {
                for (DyeColor dyeColor : DyeColor.values()) {
                    ItemStack itemStack = new ItemStack(instance);
        
                    ItemInfo itemInfo = new ItemInfo(size, dyeColor);
                    itemInfo.writeToTag(itemStack.getOrCreateTag());
        
                    stacks.add(itemStack);
                }
            }
        }
    }
}
