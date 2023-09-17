package qouteall.mini_scaled.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;

public class ScaleBoxListWidget extends AbstractSelectionList<ScaleBoxEntryWidget> {
    public static final int ROW_WIDTH = 300;
    
    private final ScaleBoxManagementScreen parent;
    
    public ScaleBoxListWidget(
        ScaleBoxManagementScreen parent, int width, int height, int top, int bottom, int itemHeight
    ) {
        super(Minecraft.getInstance(), width, height, top, bottom, itemHeight);
        
        this.parent = parent;
        
        setRenderBackground(false);
        setRenderTopAndBottom(false);
    }
    
    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {
    
    }
}
