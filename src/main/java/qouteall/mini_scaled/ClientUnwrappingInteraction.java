package qouteall.mini_scaled;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.impl.event.interaction.InteractionEventsRouterClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.mini_scaled.gui.ScaleBoxInteractionManager;
import qouteall.mini_scaled.item.ManipulationWandItem;
import qouteall.q_misc_util.CustomTextOverlay;
import qouteall.q_misc_util.api.McRemoteProcedureCall;
import qouteall.q_misc_util.my_util.AARotation;
import qouteall.q_misc_util.my_util.IntBox;

import java.util.Objects;

import static qouteall.mini_scaled.gui.ScaleBoxInteractionManager.*;

public class ClientUnwrappingInteraction {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static @Nullable Session session = null;
    
    public static final class Session {
        public final ResourceKey<Level> dimension;
        public final int boxId;
        public final IntBox entranceArea;
        public final int scale;
        
        public @Nullable IntBox unwrappingArea;
        
        public Session(
            ResourceKey<Level> dimension,
            int boxId,
            IntBox entranceArea,
            int scale
        ) {
            this.dimension = dimension;
            this.boxId = boxId;
            this.entranceArea = entranceArea;
            this.scale = scale;
        }
        
    }
    
    public static void init() {
        IPGlobal.clientCleanupSignal.connect(ClientUnwrappingInteraction::reset);
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> updateDisplay());
        
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() == ManipulationWandItem.INSTANCE) {
                if (session != null) {
                    onLeftClick();
                    return true;
                }
            }
            return false;
        });
        
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() == ManipulationWandItem.INSTANCE) {
                if (session != null) {
                    onRightClick();
                    
                    // don't send interaction packet to server
                    return InteractionResultHolder.fail(mainHandItem);
                }
            }
            
            return InteractionResultHolder.pass(mainHandItem);
        });
        
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() == ManipulationWandItem.INSTANCE) {
                if (session != null) {
                    onRightClick();
                    
                    // don't send interaction packet to server
                    return InteractionResult.FAIL;
                }
            }
            
            return InteractionResult.PASS;
        });
    }
    
    private static void reset() {
        session = null;
    }
    
    public static void startPreUnwrapping(
        ScaleBoxRecord.Entry entry
    ) {
        if (entry.currentEntranceDim == null || entry.currentEntrancePos == null) {
            LOGGER.warn("Invalid box info {}", entry.toTag());
            reset();
            return;
        }
        
        session = new Session(
            entry.currentEntranceDim, entry.id, entry.getOuterAreaBox(), entry.scale
        );
    }
    
    public static void onLeftClick() {
        session = null;
    }
    
    public static void onRightClick() {
        if (session != null) {
            if (session.unwrappingArea != null) {
                /**{@link ScaleBoxInteractionManager.RemoteCallables#confirmUnwrapping}*/
                McRemoteProcedureCall.tellServerToInvoke(
                    "qouteall.mini_scaled.gui.ScaleBoxInteractionManager.RemoteCallables.confirmUnwrapping",
                    session.boxId,
                    session.unwrappingArea
                );
                reset();
            }
        }
    }
    
    private static void updateDisplay() {
        if (session == null) {
            return;
        }
        
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.dimension() != session.dimension) {
            return;
        }
        
        LocalPlayer player = minecraft.player;
        
        if (player == null) {
            return;
        }
        
        if (player.getMainHandItem().getItem() != ManipulationWandItem.INSTANCE) {
            return;
        }
        
        BlockPos entranceSize = session.entranceArea.getSize();
        BlockPos outerSize = entranceSize.multiply(session.scale);
        
        Vec3 viewVector = player.getViewVector(1);
        boolean xPosi = viewVector.x > 0;
        boolean yPosi = viewVector.y > 0;
        boolean zPosi = viewVector.z > 0;
        
        BlockPos basePos = session.entranceArea.getVertex(!xPosi, !yPosi, !zPosi);
        BlockPos signedSize = new BlockPos(
            xPosi ? outerSize.getX() : -outerSize.getX(),
            yPosi ? outerSize.getY() : -outerSize.getY(),
            zPosi ? outerSize.getZ() : -outerSize.getZ()
        );
        
        session.unwrappingArea = IntBox.getBoxByPosAndSignedSize(basePos, signedSize);
        
        CustomTextOverlay.putText(
            Component.translatable(
                "mini_scaled.select_direction_for_unwrapping",
                minecraft.options.keyUse.getTranslatedKeyMessage(),
                minecraft.options.keyAttack.getTranslatedKeyMessage()
            ),
            0.1,
            "mini_scaled"
        );
    }
    
    public static void clientRender(
        LocalPlayer player, ItemStack itemStack, PoseStack poseStack,
        MultiBufferSource.BufferSource bufferSource, double camX, double camY, double camZ
    ) {
        if (session == null) {
            return;
        }
        
        if (PortalRendering.isRendering()) {
            return;
        }
        
        if (player.getMainHandItem().getItem() != ManipulationWandItem.INSTANCE) {
            return;
        }
        
        IntBox unwrappingArea = session.unwrappingArea;
        
        if (unwrappingArea == null) {
            return;
        }
        
        LevelRenderer.renderLineBox(
            poseStack,
            bufferSource.getBuffer(RenderType.lines()),
            unwrappingArea.toRealNumberBox().move(-camX, -camY, -camZ),
            1, 1, 1, 1
        );
    }
}
