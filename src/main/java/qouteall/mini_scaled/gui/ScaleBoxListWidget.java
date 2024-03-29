package qouteall.mini_scaled.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;

public class ScaleBoxListWidget extends AbstractSelectionList<ScaleBoxEntryWidget> {
    private final ScaleBoxManagementScreen parent;
    
    public int rowWidth = 300;
    
    public ScaleBoxListWidget(
        ScaleBoxManagementScreen parent, int width, int height, int top, int bottom, int itemHeight
    ) {
        super(Minecraft.getInstance(), width, height, top, itemHeight);
        
        this.parent = parent;
        
        setRenderBackground(false);
    }
    
    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    
    }
    
    @Override
    public int getRowWidth() {
        return rowWidth;
    }
    
    @Override
    protected int getScrollbarPosition() {
        return rowWidth;
    }
}
