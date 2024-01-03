package qouteall.mini_scaled.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import qouteall.q_misc_util.api.McRemoteProcedureCall;

import java.util.List;
import java.util.function.Consumer;

public class ScaleBoxWrappingScreen extends Screen {
    
    public static final int ITEM_HEIGHT = 30;
    
    public final ScaleBoxInteractionManager.PendingWrappingGuiData data;
    
    private final OptionListWidget optionListWidget;
    
    private final StringWidget titleText;
    private final StringWidget titleText2;
    
    private final Button confirmButton;
    private final Button cancelButton;
    
    public ScaleBoxWrappingScreen(
        ScaleBoxInteractionManager.PendingWrappingGuiData pendingWrappingGuiData
    ) {
        super(Component.literal("pending wrapping screen"));
        
        // in vanilla it's set in init(), but I want to initialize early
        this.minecraft = Minecraft.getInstance();
        this.font = Minecraft.getInstance().font;
        
        this.data = pendingWrappingGuiData;
        
        this.optionListWidget = new OptionListWidget(
            minecraft, width, height, 0,
            ITEM_HEIGHT
        );
        
        BlockPos boxSize = data.boxSize();
        
        for (ScaleBoxInteractionManager.WrappingOption option : data.options()) {
            optionListWidget.children().add(new OptionEntryWidget(
                option, this::onSelect,
                boxSize
            ));
        }
        
        this.titleText = new StringWidget(
            Component.translatable("mini_scaled.wrap_select_scale"), font
        ).alignCenter();
        
        this.titleText2 = new StringWidget(
            Component.translatable(
                "mini_scaled.wrap_box_size",
                boxSize.getX(), boxSize.getY(), boxSize.getZ()
            ), font
        ).alignCenter();
        
        
        this.confirmButton = Button.builder(
            Component.translatable("gui.ok"),
            button -> {
                onConfirm();
            }
        ).build();
        
        this.cancelButton = Button.builder(
            Component.translatable("gui.cancel"),
            button -> {
                onCancel();
            }
        ).build();
        
        this.confirmButton.active = false;
    }
    
    private void onSelect(OptionEntryWidget selected) {
        optionListWidget.setSelected(selected);
        this.confirmButton.active = true;
    }
    
    private void onCancel() {
        /**{@link ScaleBoxInteractionManager.RemoteCallables#cancelWrapping(ServerPlayer)}*/
        McRemoteProcedureCall.tellServerToInvoke(
            "qouteall.mini_scaled.gui.ScaleBoxInteractionManager.RemoteCallables.cancelWrapping"
        );
        
        assert minecraft != null;
        minecraft.setScreen(null);
    }
    
    private void onConfirm() {
        OptionEntryWidget selected = optionListWidget.getSelected();
        
        if (selected != null) {
            int scale = selected.option.scale();
            /**{@link ScaleBoxInteractionManager.RemoteCallables#confirmWrapping(ServerPlayer, int)}*/
            McRemoteProcedureCall.tellServerToInvoke(
                "qouteall.mini_scaled.gui.ScaleBoxInteractionManager.RemoteCallables.confirmWrapping",
                scale
            );
            
            assert minecraft != null;
            minecraft.setScreen(null);
        }
    }
    
    @Override
    protected void init() {
        super.init();
        
        addRenderableWidget(optionListWidget);
        addRenderableWidget(confirmButton);
        addRenderableWidget(cancelButton);
        
        addRenderableWidget(titleText);
        addRenderableWidget(titleText2);
        
        titleText.setWidth(width);
        titleText2.setWidth(width);
        
        optionListWidget.setHeight(height - 80);
        optionListWidget.setWidth(width - 80);
        
        GridLayout gridLayout = new GridLayout(0, 0).spacing(10);
        LayoutSettings layoutSettings = gridLayout.defaultCellSetting()
            .alignVerticallyMiddle().alignHorizontallyCenter();
        gridLayout.addChild(titleText, 0, 0, 1, 2, layoutSettings);
        gridLayout.addChild(titleText2, 1, 0, 1, 2, layoutSettings);
        gridLayout.addChild(optionListWidget, 2, 0, 1, 2, layoutSettings);
        gridLayout.addChild(confirmButton, 3, 0, layoutSettings);
        gridLayout.addChild(cancelButton, 3, 1, layoutSettings);
        
        gridLayout.arrangeElements();
        
        gridLayout.setPosition(
            (width - gridLayout.getWidth()) / 2,
            (height - gridLayout.getHeight()) / 2
        );
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        guiGraphics.pose().pushPose();
        
        float scale = 0.7f;
        
//        guiGraphics.pose().translate(width * 0.1, height * 0.87, 0);
        guiGraphics.pose().scale(scale, scale, 1);
        
        guiGraphics.drawString(
            minecraft.font,
            Component.translatable("mini_scaled.item_may_change_in_future"),
            0, 0, 0xffffffff
        );
        
        guiGraphics.pose().popPose();
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    
    public static class OptionListWidget extends AbstractSelectionList<OptionEntryWidget> {
        
        public OptionListWidget(
            Minecraft minecraft, int width, int height, int y0, int itemHeight
        ) {
            super(minecraft, width, height, y0, itemHeight);
            Validate.notNull(minecraft, "minecraft is null");
            
            setRenderBackground(false);
        }
        
        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        
        }
    }
    
    public static class OptionEntryWidget extends ContainerObjectSelectionList.Entry<OptionEntryWidget> {
        
        public final ScaleBoxInteractionManager.WrappingOption option;
        public final Consumer<OptionEntryWidget> selectCallback;
        private final BlockPos outerBoxSize;
        
        private MultiLineLabel line1;
        private MultiLineLabel line2;
        
        public OptionEntryWidget(
            ScaleBoxInteractionManager.WrappingOption option, Consumer<OptionEntryWidget> selectCallback,
            BlockPos outerBoxSize
        ) {
            this.option = option;
            this.selectCallback = selectCallback;
            this.outerBoxSize = outerBoxSize;
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
            Font font = Minecraft.getInstance().font;
            if (line1 == null) {
                line1 = MultiLineLabel.create(
                    font,
                    Component.translatable("mini_scaled.scale")
                        .append(String.valueOf(option.scale()))
                        .append("   (%d × %d × %d)".formatted(
                            outerBoxSize.getX() / option.scale(),
                            outerBoxSize.getY() / option.scale(),
                            outerBoxSize.getZ() / option.scale()
                        )),
                    width
                );
            }
            
            if (line2 == null) {
                line2 = MultiLineLabel.create(
                    font,
                    Component.translatable("mini_scaled.ingredient_cost"),
                    width
                );
            }
            
            line1.renderLeftAligned(
                guiGraphics,
                left, top, 15, 0x00ffffff
            );
            
            line2.renderLeftAligned(
                guiGraphics,
                left, top + 15, 15, 0x00ffffff
            );
            
            // render cost item icon
            int costItemIconOffset = line2.getWidth();
            
            int itemDisplayWidth = 18;
            for (int i = 0; i < option.ingredients().size(); i++) {
                ItemStack ingredient = option.ingredients().get(i);
                guiGraphics.renderItem(
                    ingredient,
                    left + costItemIconOffset + itemDisplayWidth * i, top + 15 - 5
                );
                guiGraphics.renderItemDecorations(
                    font, ingredient,
                    left + costItemIconOffset + itemDisplayWidth * i, top + 15 - 5
                );
            }
        }
        
        @Override
        public @NotNull List<? extends GuiEventListener> children() {
            return List.of();
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            selectCallback.accept(this);
            super.mouseClicked(mouseX, mouseY, button);
            return true;
        }
    }
}
