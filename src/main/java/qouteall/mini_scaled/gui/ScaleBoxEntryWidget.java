package qouteall.mini_scaled.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import org.jetbrains.annotations.NotNull;
import qouteall.mini_scaled.ScaleBoxRecord;

import java.util.ArrayList;
import java.util.List;

public class ScaleBoxEntryWidget extends ContainerObjectSelectionList.Entry<ScaleBoxEntryWidget> {
    public final static int WIDGET_HEIGHT = 50;
    
    private final ScaleBoxListWidget parent;
    private final int index;
    private final ScaleBoxRecord.Entry entry;
    
    private final List<GuiEventListener> children = new ArrayList<>();
    
    public ScaleBoxEntryWidget(ScaleBoxListWidget parent, int index, ScaleBoxRecord.Entry entry) {
        this.parent = parent;
        this.index = index;
        this.entry = entry;
    }
    
    @Override
    public @NotNull List<? extends NarratableEntry> narratables() {
        return List.of();
    }
    
    @Override
    public void render(
        @NotNull GuiGraphics guiGraphics,
        int index,
        int y,
        int x,
        int rowWidth,
        int itemHeight,
        int mouseX,
        int mouseY,
        boolean hovered,
        float partialTick
    ) {
        Minecraft client = Minecraft.getInstance();
        
        guiGraphics.drawString(
            client.font, "aaa",
            x + WIDGET_HEIGHT + 3, (int) (y),
            0xFFFFFFFF
        );
    }
    
    @Override
    public List<? extends GuiEventListener> children() {
        return children;
    }
}
