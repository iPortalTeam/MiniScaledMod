package qouteall.mini_scaled.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import qouteall.mini_scaled.CreationItem;

@Config(name = "mini_scaled")
public class MiniScaledConfig implements ConfigData {
    
    public String wrappingIngredient = CreationItem.DEFAULT_CREATION_ITEM_ID;
    public double wrappingIngredientAmountMultiplier = 1.0;
    
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    public ScaleBoxInteractionMode interactionMode = ScaleBoxInteractionMode.normal;
    
    public int maxScaleBoxPerPlayer = 40;
    
    public int wrappingAnimationTicks = 60;
    public int unwrappingAnimationTicks = 60;
    
    public boolean clientBetterAnimation = true;
    public boolean serverBetterAnimation = true;
}
