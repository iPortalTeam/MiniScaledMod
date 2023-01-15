package qouteall.mini_scaled;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class ScaleBoxEntranceItem extends Item {
    
    public static final ScaleBoxEntranceItem instance = new ScaleBoxEntranceItem(new Item.Settings());
    
    public static void init() {
        Registry.register(
            Registries.ITEM,
            new Identifier("mini_scaled:scale_box_item"),
            instance
        );
    }
    
    public static class ItemInfo {
        public int scale;
        public DyeColor color;
        @Nullable
        public UUID ownerId;
        @Nullable
        public String ownerNameCache;
        
        public ItemInfo(int scale, DyeColor color) {
            this.scale = scale;
            this.color = color;
        }
        
        public ItemInfo(
            int size, DyeColor color, @NotNull UUID ownerId, @NotNull String ownerNameCache
        ) {
            this.scale = size;
            this.color = color;
            this.ownerId = ownerId;
            this.ownerNameCache = ownerNameCache;
        }
        
        public ItemInfo(NbtCompound tag) {
            scale = tag.getInt("size");
            color = DyeColor.byName(tag.getString("color"), DyeColor.BLACK);
            if (tag.contains("ownerId")) {
                ownerId = tag.getUuid("ownerId");
                ownerNameCache = tag.getString("ownerNameCache");
            }
        }
        
        public void writeToTag(NbtCompound compoundTag) {
            compoundTag.putInt("size", scale);
            compoundTag.putString("color", color.getName());
            if (ownerId != null) {
                compoundTag.putUuid("ownerId", ownerId);
                compoundTag.putString("ownerNameCache", ownerNameCache);
            }
        }
    }
    
    public ScaleBoxEntranceItem(Settings settings) {
        super(settings);
    }
    
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
    
        return ScaleBoxManipulation.onRightClickUsingEntrance(context);
    }
    
    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        ItemInfo itemInfo = new ItemInfo(stack.getOrCreateNbt());
        tooltip.add(Text.translatable("mini_scaled.color")
            .append(getColorText(itemInfo.color).formatted(Formatting.GOLD))
        );
        tooltip.add(Text.translatable("mini_scaled.scale")
            .append(Text.literal(Integer.toString(itemInfo.scale)).formatted(Formatting.AQUA))
        );
        if (itemInfo.ownerNameCache != null) {
            tooltip.add(Text.translatable("mini_scaled.owner")
                .append(Text.literal(itemInfo.ownerNameCache).formatted(Formatting.YELLOW))
            );
        }

        // the entrance size is not shown because the size may change, and it's hard to update
//        if (itemInfo.entranceSizeCache != null) {
//            String sizeStr = String.format("%d x %d x %d",
//                itemInfo.entranceSizeCache.getX(), itemInfo.entranceSizeCache.getY(), itemInfo.entranceSizeCache.getZ()
//            );
//
//            tooltip.add(new TranslatableText("mini_scaled.entrance_size")
//                .append(new LiteralText(sizeStr))
//            );
//        }
    }
    
    public static void registerCreativeInventory(Consumer<ItemStack> func) {
        for (int scale : ScaleBoxGeneration.supportedScales) {
            for (DyeColor dyeColor : DyeColor.values()) {
                ItemStack itemStack = new ItemStack(instance);
                
                ItemInfo itemInfo = new ItemInfo(scale, dyeColor);
                itemInfo.writeToTag(itemStack.getOrCreateNbt());
                
                func.accept(itemStack);
            }
        }
    }
    
    private static final Text spaceText = Text.literal(" ");
    
    @Override
    public Text getName(ItemStack stack) {
        ItemInfo itemInfo = new ItemInfo(stack.getOrCreateNbt());
        DyeColor color = itemInfo.color;
        MutableText result = Text.translatable("item.mini_scaled.scale_box_item")
            .append(spaceText)
            .append(Text.literal(Integer.toString(itemInfo.scale)));
        if (itemInfo.ownerNameCache != null) {
            result = result.append(spaceText)
                .append(Text.translatable("mini_scaled.owner"))
                .append(Text.literal(itemInfo.ownerNameCache));
        }
        return result;
    }
    
    public static MutableText getColorText(DyeColor color) {
        return Text.translatable("color.minecraft." + color.getName());
    }
    
    @Nullable
    public static ItemStack boxIdToItem(int boxId) {
        ScaleBoxRecord.Entry entry = ScaleBoxRecord.get().getEntryById(boxId);
        if (entry == null) {
            System.err.println("invalid boxId for item " + boxId);
            return null;
        }
        
        ItemStack itemStack = new ItemStack(ScaleBoxEntranceItem.instance);
        new ScaleBoxEntranceItem.ItemInfo(
            entry.scale, entry.color, entry.ownerId, entry.ownerNameCache
        ).writeToTag(itemStack.getOrCreateNbt());
        
        return itemStack;
    }
    
    public static int getRenderingColor(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null) {
            return 0;
        }
        // not using ItemInfo to improve performance
        String colorText = nbt.getString("color");
        DyeColor dyeColor = DyeColor.byName(colorText, DyeColor.BLACK);
        return dyeColor.getMapColor().color;
    }
}
