package qouteall.mini_scaled.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;
import qouteall.mini_scaled.ScaleBoxRecord;
import qouteall.q_misc_util.api.McRemoteProcedureCall;

public class ScaleBoxOptionsScreen extends Screen {
    private final @Nullable Screen parent;
    
    private final ScaleBoxRecord.Entry entry;
    
    private final MultiLineTextWidget scaleTransformText;
    private final Button scaleTransformButton;
    private final MultiLineTextWidget gravityTransformText;
    private final Button gravityTransformButton;
    private final MultiLineTextWidget accessControlText;
    private final Button accessControlButton;
    
    private final Button finishButton;
    
    protected ScaleBoxOptionsScreen(@Nullable Screen parent, ScaleBoxRecord.Entry entry) {
        super(Component.translatable("mini_scaled.options_title"));
        
        this.parent = parent;
        this.entry = entry;
        
        this.scaleTransformText = new MultiLineTextWidget(
            Component.translatable("mini_scaled.enable_scale_transform"), font
        );
        
        this.scaleTransformButton = Button.builder(
            getEnablementText(entry.teleportChangesScale),
            button -> {
                entry.teleportChangesScale = !entry.teleportChangesScale;
                button.setMessage(getEnablementText(entry.teleportChangesScale));
            }
        ).build();
        
        this.gravityTransformText = new MultiLineTextWidget(
            Component.translatable("mini_scaled.enable_gravity_transform"), font
        );
        
        this.gravityTransformButton = Button.builder(
            getEnablementText(entry.teleportChangesGravity),
            button -> {
                entry.teleportChangesGravity = !entry.teleportChangesGravity;
                button.setMessage(getEnablementText(entry.teleportChangesGravity));
            }
        ).build();
        
        this.accessControlText = new MultiLineTextWidget(
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
            entry.teleportChangesScale,
            entry.teleportChangesGravity,
            entry.accessControl
        );
        
        assert minecraft != null;
        minecraft.setScreen(parent);
    }
    
    private static MutableComponent getEnablementText(boolean cond) {
        return Component.translatable(cond ? "imm_ptl.enabled" : "imm_ptl.disabled");
    }
}
