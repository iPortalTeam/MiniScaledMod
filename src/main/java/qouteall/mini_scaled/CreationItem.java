package qouteall.mini_scaled;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import qouteall.mini_scaled.util.MSUtil;

import java.util.List;

public class CreationItem {
    
    private static final Item DEFAULT_CREATION_ITEM = Items.ENDER_EYE;
    public static final String DEFAULT_CREATION_ITEM_ID = "minecraft:ender_eye";
    
    private static @Nullable Item creationItem;
    
    public static void setCreationItem(@Nullable Item item) {
        creationItem = item;
    }
    
    public static Item getCreationItem() {
        if (creationItem == null) {
            return DEFAULT_CREATION_ITEM;
        }
        return creationItem;
    }
    
    public static void init() {
    
    }
    
    public static List<ItemStack> getCost(
        BlockPos boxSize, int scale
    ) {
        double multiplier = MSGlobal.config.getConfig().wrappingIngredientAmountMultiplier;
        
        int volume = boxSize.getX() * boxSize.getY() * boxSize.getZ();
        
        double powerTrans = 1.0 / 2;
        double n1 = Math.pow((double) volume, powerTrans) * 0.3;
        
        double n2 = scale * 0.3;
        
        int itemNum = (int) Math.ceil((n1 + n2) * multiplier);
        
        return MSUtil.createItemStacks(
            getCreationItem(),
            itemNum
        );
    }
    
    public static boolean checkInventory(
        ServerPlayer player, List<ItemStack> costItems
    ) {
        // there may be same-typed items in different stacks
        // so firstly count the items
        Object2IntOpenHashMap<Item> itemToCount = new Object2IntOpenHashMap<>();
        itemToCount.defaultReturnValue(0);
        
        for (ItemStack costItem : costItems) {
            itemToCount.addTo(costItem.getItem(), costItem.getCount());
        }
        
        for (Object2IntMap.Entry<Item> e : itemToCount.object2IntEntrySet()) {
            int requiredCount = e.getIntValue();
            Item item = e.getKey();
            
            int takenCount = ContainerHelper.clearOrCountMatchingItems(
                player.getInventory(),
                i -> i.getItem() == item,
                requiredCount,
                true
            );
            
            if (takenCount < requiredCount) {
                player.sendSystemMessage(Component.translatable(
                    "mini_scaled.not_enough_ingredient",
                    requiredCount, new ItemStack(item, requiredCount).getDisplayName()
                ));
                return false;
            }
        }
        
        return true;
    }
    
    public static void removeItems(
        ServerPlayer player, List<ItemStack> costItems
    ) {
        for (ItemStack itemStack : costItems) {
            ContainerHelper.clearOrCountMatchingItems(
                player.getInventory(),
                i -> i.getItem() == itemStack.getItem(),
                itemStack.getCount(),
                false
            );
        }
    }
}
