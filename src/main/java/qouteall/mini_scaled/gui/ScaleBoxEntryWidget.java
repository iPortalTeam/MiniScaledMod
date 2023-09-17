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
import java.util.function.Consumer;

public class ScaleBoxEntryWidget extends ContainerObjectSelectionList.Entry<ScaleBoxEntryWidget> {
    public final static int WIDGET_HEIGHT = 50;
    
    public final ScaleBoxListWidget parent;
    public final int index;
    public final ScaleBoxRecord.Entry entry;
    public final Consumer<ScaleBoxEntryWidget> selectCallback;
    
    private final List<GuiEventListener> children = new ArrayList<>();
    
    public ScaleBoxEntryWidget(
        ScaleBoxListWidget parent, int index, ScaleBoxRecord.Entry entry,
        Consumer<ScaleBoxEntryWidget> selectCallback
    ) {
        this.parent = parent;
        this.index = index;
        this.entry = entry;
        this.selectCallback = selectCallback;
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
            x + 3, (int) (y),
            0xFFFFFFFF
        );
    }
    
    @Override
    public List<? extends GuiEventListener> children() {
        return children;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        selectCallback.accept(this);
        super.mouseClicked(mouseX, mouseY, button);
        return true;//allow outer dragging
    }
}
