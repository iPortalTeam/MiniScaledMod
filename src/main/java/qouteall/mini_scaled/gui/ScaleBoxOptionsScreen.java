package qouteall.mini_scaled.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;
import qouteall.mini_scaled.ScaleBoxRecord;
import qouteall.q_misc_util.api.McRemoteProcedureCall;

public class ScaleBoxOptionsScreen extends Screen {
    private final @Nullable Screen parent;
    
    private final ScaleBoxRecord.Entry entry;
    
    private final StringWidget scaleTransformText;
    private final Button scaleTransformButton;
    private final StringWidget gravityTransformText;
    private final Button gravityTransformButton;
    private final StringWidget accessControlText;
    private final Button accessControlButton;
    
    private final Button finishButton;
    
    protected ScaleBoxOptionsScreen(@Nullable Screen parent, ScaleBoxRecord.Entry entry) {
        super(Component.translatable("mini_scaled.options_title"));
        
        this.parent = parent;
        this.entry = entry;
        
        this.font = Minecraft.getInstance().font; // it's normally initialized on init()
        
        this.scaleTransformText = new StringWidget(
            Component.translatable("mini_scaled.enable_scale_transform"), font
        );
        
        this.scaleTransformButton = Button.builder(
            getEnablementText(entry.teleportChangesScale),
            button -> {
                entry.teleportChangesScale = !entry.teleportChangesScale;
                button.setMessage(getEnablementText(entry.teleportChangesScale));
            }
        ).build();
        
        this.gravityTransformText = new StringWidget(
            Component.translatable("mini_scaled.enable_gravity_transform"), font
        );
        
        this.gravityTransformButton = Button.builder(
            getEnablementText(entry.teleportChangesGravity),
            button -> {
                entry.teleportChangesGravity = !entry.teleportChangesGravity;
                button.setMessage(getEnablementText(entry.teleportChangesGravity));
            }
        ).build();
        
        this.accessControlText = new StringWidget(
            Component.translatable("mini_scaled.enable_access_control"), font
        );
        
        this.accessControlButton = Button.builder(
            getEnablementText(entry.accessControl),
            button -> {
                entry.accessControl = !entry.accessControl;
                button.setMessage(getEnablementText(entry.accessControl));
            }
        ).build();
        
        this.finishButton = Button.builder(
            Component.translatable("imm_ptl.finish"),
            button -> {
                this.onClose();
            }
        ).build();
    }
    
    @Override
    public void onClose() {
        McRemoteProcedureCall.tellServerToInvoke(
            "qouteall.mini_scaled.gui.ScaleBoxGuiManager.RemoteCallables.updateScaleBoxOption",
            entry.id,
            entry.teleportChangesScale,
            entry.teleportChangesGravity,
            entry.accessControl
        );
        
        assert minecraft != null;
        minecraft.setScreen(parent);
    }
    
    @Override
    protected void init() {
        super.init();
        
        addRenderableWidget(scaleTransformText);
        addRenderableWidget(scaleTransformButton);
        addRenderableWidget(gravityTransformText);
        addRenderableWidget(gravityTransformButton);
        addRenderableWidget(accessControlText);
        addRenderableWidget(accessControlButton);
        
        addRenderableWidget(finishButton);
        
        GridLayout gridLayout = new GridLayout(0, 0).spacing(20);
        GridLayout.RowHelper rowHelper = gridLayout.createRowHelper(2);
        LayoutSettings layoutSettings = gridLayout.defaultCellSetting().alignVerticallyMiddle();
        rowHelper.addChild(scaleTransformText, layoutSettings);
        rowHelper.addChild(scaleTransformButton, layoutSettings);
        rowHelper.addChild(gravityTransformText, layoutSettings);
        rowHelper.addChild(gravityTransformButton, layoutSettings);
        rowHelper.addChild(accessControlText, layoutSettings);
        rowHelper.addChild(accessControlButton, layoutSettings);
        
        gridLayout.arrangeElements();
        
        gridLayout.setPosition(
            (width - gridLayout.getWidth()) / 2,
            (height - gridLayout.getHeight()) / 2
        );
        
        finishButton.setX(20);
        finishButton.setY(height - 20 - finishButton.getHeight());
    }
    
    private static MutableComponent getEnablementText(boolean cond) {
        return Component.translatable(cond ? "imm_ptl.enabled" : "imm_ptl.disabled");
    }
}
