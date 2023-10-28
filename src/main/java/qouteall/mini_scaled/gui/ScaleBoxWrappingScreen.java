package qouteall.mini_scaled.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ScaleBoxWrappingScreen extends Screen {
    
    public static record Option(
        int scale,
        int ingredientCost
    ) {}
    
    public final List<Option> options;
    
    private final OptionListWidget optionListWidget;
    
    public ScaleBoxWrappingScreen(
        Component title, List<Option> options
    ) {
        super(title);
        this.options = options;
        this.optionListWidget = new OptionListWidget(
            minecraft, width, height,
            100, 200,
            30
        );
        
        for (Option option : options) {
            optionListWidget.children().add(new OptionEntryWidget(option));
        }
    }
    
    @Override
    protected void init() {
        super.init();
        
        optionListWidget.updateSize(
            width, // width
            height, // height
            0, // start y
            height // end y
        );
        
        addRenderableWidget(optionListWidget);
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    
    public static class OptionListWidget extends AbstractSelectionList<OptionEntryWidget> {
        
        public OptionListWidget(
            Minecraft minecraft, int width, int height, int y0, int y1, int itemHeight
        ) {
            super(minecraft, width, height, y0, y1, itemHeight);
            
            setRenderBackground(false);
        }
        
        @Override
        public void updateNarration(NarrationElementOutput narrationElementOutput) {
        
        }
    }
    
    public static class OptionEntryWidget extends ContainerObjectSelectionList.Entry<OptionEntryWidget> {
        
        public final Option option;
        
        private @Nullable MultiLineLabel multiLineLabel;
        
        public OptionEntryWidget(Option option) {
            this.option = option;
        }
        
        @Override
        public @NotNull List<? extends NarratableEntry> narratables() {
            return List.of();
        }
        
        @Override
        public void render(
            GuiGraphics guiGraphics,
            int index, int top, int left,
            int width, int height,
            int mouseX, int mouseY,
            boolean hovering, float partialTick
        ) {
            if (multiLineLabel == null) {
                multiLineLabel = MultiLineLabel.create(
                    Minecraft.getInstance().font,
                    Component.literal(String.valueOf(option.scale())),
                    width
                );
            }
        }
        
        @Override
        public @NotNull List<? extends GuiEventListener> children() {
            return List.of();
        }
    }
}
