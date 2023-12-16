package qouteall.mini_scaled.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "mini_scaled")
public class MiniScaledConfig implements ConfigData {
    
    public String creationItem = "minecraft:netherite_ingot";
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    public ScaleBoxInteractionMode interactionMode = ScaleBoxInteractionMode.normal;
    
    public int maxScaleBoxPerPlayer = 40;
    
    public int wrappingAnimationTicks = 60;
    public int unwrappingAnimationTicks = 60;
    
    public boolean clientBetterAnimation = true;
    public boolean serverBetterAnimation = true;
}
