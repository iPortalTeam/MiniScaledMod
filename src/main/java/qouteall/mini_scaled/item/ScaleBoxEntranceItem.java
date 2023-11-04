package qouteall.mini_scaled.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.mini_scaled.ScaleBoxGeneration;
import qouteall.mini_scaled.ScaleBoxManipulation;
import qouteall.mini_scaled.ScaleBoxRecord;
import qouteall.mini_scaled.util.MSUtil;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class ScaleBoxEntranceItem extends Item {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleBoxEntranceItem.class);
    
    public static final ScaleBoxEntranceItem instance = new ScaleBoxEntranceItem(new Item.Properties());
    
    public static void init() {
        Registry.register(
            BuiltInRegistries.ITEM,
            new ResourceLocation("mini_scaled:scale_box_item"),
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
        
        // boxId only exists in newer versions of the mod
        @Nullable
        public Integer boxId;
        
        public ItemInfo(int scale, DyeColor color) {
            this.scale = scale;
            this.color = color;
        }
        
        public ItemInfo(
            int scale, DyeColor color, @NotNull UUID ownerId, @NotNull String ownerNameCache,
            int boxId
        ) {
            this.scale = scale;
            this.color = color;
            this.ownerId = ownerId;
            this.ownerNameCache = ownerNameCache;
            this.boxId = boxId;
        }
        
        public ItemInfo(CompoundTag tag) {
            scale = tag.getInt("size");
            color = DyeColor.byName(tag.getString("color"), DyeColor.BLACK);
            if (tag.contains("ownerId")) {
                ownerId = tag.getUUID("ownerId");
                ownerNameCache = tag.getString("ownerNameCache");
            }
            if (tag.contains("boxId")) {
                boxId = tag.getInt("boxId");
            }
        }
        
        public void writeToTag(CompoundTag compoundTag) {
            compoundTag.putInt("size", scale);
            compoundTag.putString("color", color.getName());
            if (ownerId != null) {
                compoundTag.putUUID("ownerId", ownerId);
                compoundTag.putString("ownerNameCache", ownerNameCache);
            }
            if (boxId != null) {
                compoundTag.putInt("boxId", boxId);
            }
        }
        
        public CompoundTag toTag() {
            CompoundTag compoundTag = new CompoundTag();
            writeToTag(compoundTag);
            return compoundTag;
        }
    }
    
    public ScaleBoxEntranceItem(Properties settings) {
        super(settings);
    }
    
    @Override
    public InteractionResult useOn(UseOnContext context) {
    
        return ScaleBoxManipulation.onRightClickUsingEntrance(context);
    }
    
    @Override
    public void appendHoverText(ItemStack stack, Level world, List<Component> tooltip, TooltipFlag context) {
        super.appendHoverText(stack, world, tooltip, context);
        ItemInfo itemInfo = new ItemInfo(stack.getOrCreateTag());
        tooltip.add(Component.translatable("mini_scaled.color")
            .append(MSUtil.getColorText(itemInfo.color).withStyle(ChatFormatting.GOLD))
        );
        tooltip.add(Component.translatable("mini_scaled.scale")
            .append(Component.literal(Integer.toString(itemInfo.scale)).withStyle(ChatFormatting.AQUA))
        );
        if (itemInfo.ownerNameCache != null) {
            tooltip.add(Component.translatable("mini_scaled.owner")
                .append(Component.literal(itemInfo.ownerNameCache).withStyle(ChatFormatting.YELLOW))
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
                itemInfo.writeToTag(itemStack.getOrCreateTag());
                
                func.accept(itemStack);
            }
        }
    }
    
    private static final Component spaceText = Component.literal(" ");
    
    @Override
    public Component getName(ItemStack stack) {
        ItemInfo itemInfo = new ItemInfo(stack.getOrCreateTag());
        DyeColor color = itemInfo.color;
        MutableComponent result = Component.translatable("item.mini_scaled.scale_box_item")
            .append(spaceText)
            .append(Component.literal(Integer.toString(itemInfo.scale)));
        if (itemInfo.ownerNameCache != null) {
            result = result.append(spaceText)
                .append(Component.translatable("mini_scaled.owner"))
                .append(Component.literal(itemInfo.ownerNameCache));
        }
        return result;
    }
    
    @Nullable
    public static ItemStack boxIdToItem(MinecraftServer server, int boxId) {
        ScaleBoxRecord.Entry entry = ScaleBoxRecord.get(server).getEntryById(boxId);
        if (entry == null) {
            LOGGER.info("invalid boxId for item {}", boxId);
            return null;
        }
        
        ItemStack itemStack = new ItemStack(ScaleBoxEntranceItem.instance);
        new ScaleBoxEntranceItem.ItemInfo(
            entry.scale, entry.color, entry.ownerId, entry.ownerNameCache, boxId
        ).writeToTag(itemStack.getOrCreateTag());
        
        return itemStack;
    }
    
    public static int getRenderingColor(ItemStack stack) {
        CompoundTag nbt = stack.getTag();
        if (nbt == null) {
            return 0;
        }
        // not using ItemInfo to improve performance
        String colorText = nbt.getString("color");
        DyeColor dyeColor = DyeColor.byName(colorText, DyeColor.BLACK);
        return dyeColor.getFireworkColor();
    }
}
