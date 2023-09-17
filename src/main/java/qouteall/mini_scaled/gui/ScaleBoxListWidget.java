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
        super(Minecraft.getInstance(), width, height, top, bottom, itemHeight);
        
        this.parent = parent;
        
        setRenderBackground(false);
        setRenderTopAndBottom(false);
    }
    
    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {
    
    }
    
    @Override
    public int getRowWidth() {
        return rowWidth;
    }
    
    @Override
    protected int getScrollbarPosition() {
        return (width - rowWidth) / 2 + rowWidth;
    }
}
