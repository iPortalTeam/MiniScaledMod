package qouteall.mini_scaled;

import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.DyeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import org.apache.commons.lang3.Validate;

public class ScaleBoxCraftingRecipe extends SpecialCraftingRecipe {
    private static final RecipeSerializer<ScaleBoxCraftingRecipe> recipeSerializer =
        new SpecialRecipeSerializer<>(ScaleBoxCraftingRecipe::new);
    
    public ScaleBoxCraftingRecipe(Identifier id) {
        super(id);
    }
    
    public static void init() {
        Registry.register(Registry.RECIPE_SERIALIZER,
            "mini_scaled:scale_box_recipe_serializer",
            recipeSerializer
        );
    }
    
    @Override
    public boolean matches(CraftingInventory inv, World world) {
        int netherStarNum = 0;
        int glassNum = 0;
        int dyeNum = 0;
        for (int slot = 0; slot < inv.size(); slot++) {
            ItemStack stack = inv.getStack(slot);
            Item item = stack.getItem();
            if (item == Items.NETHER_STAR) {
                netherStarNum++;
                if (netherStarNum >= 4) {
                    return false;
                }
            }
            else if (item instanceof DyeItem) {
                dyeNum++;
                if (dyeNum >= 2) {
                    return false;
                }
            }
            else if (item == Items.GLASS) {
                glassNum++;
                if (glassNum >= 2) {
                    return false;
                }
            }
        }
        return netherStarNum != 0 && glassNum != 0 && dyeNum != 0;
    }
    
    @Override
    public ItemStack craft(CraftingInventory inv) {
        DyeItem dyeItem = null;
        int netherStarNum = 0;
        
        for (int slot = 0; slot < inv.size(); slot++) {
            ItemStack stack = inv.getStack(slot);
            Item item = stack.getItem();
            if (item == Items.NETHER_STAR) {
                netherStarNum++;
            }
            else if (item instanceof DyeItem) {
                dyeItem = ((DyeItem) item);
            }
        }
        
        Validate.notNull(dyeItem);
        
        int size = 0;
        if (netherStarNum == 1) {
            size = 8;
        }
        else if (netherStarNum == 2) {
            size = 16;
        }
        else if (netherStarNum == 3) {
            size = 32;
        }
        
        ItemStack itemStack = new ItemStack(ScaleBoxItem.instance);
        ScaleBoxItem.ItemInfo itemInfo = new ScaleBoxItem.ItemInfo(size, dyeItem.getColor());
        itemInfo.writeToTag(itemStack.getOrCreateTag());
        return itemStack;
    }
    
    @Override
    public boolean fits(int width, int height) {
        return width * height >= 2;
    }
    
    @Override
    public RecipeSerializer<?> getSerializer() {
        return recipeSerializer;
    }
}
