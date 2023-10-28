package qouteall.mini_scaled.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ScaleBoxWrappingScreen extends Screen {
    
    public static record Option(
        int scale,
        int ingredientCost
    ) {}
    
    public final List<Option> options;
    
    public ScaleBoxWrappingScreen(
        Component title, List<Option> options
    ) {
        super(title);
        this.options = options;
    }
    
    
    
}
