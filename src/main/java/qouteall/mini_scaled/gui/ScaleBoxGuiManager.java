package qouteall.mini_scaled.gui;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.mini_scaled.ScaleBoxGeneration;
import qouteall.mini_scaled.ScaleBoxOperations;
import qouteall.mini_scaled.ScaleBoxRecord;
import qouteall.mini_scaled.ducks.MiniScaled_MinecraftServerAccessor;
import qouteall.q_misc_util.api.McRemoteProcedureCall;
import qouteall.q_misc_util.my_util.IntBox;

import java.util.List;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Consumer;

public class ScaleBoxGuiManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private final MinecraftServer server;
    
    public static ScaleBoxGuiManager get(MinecraftServer server) {
        return ((MiniScaled_MinecraftServerAccessor) server).miniScaled_getScaleBoxGuiManager();
    }
    
    public static class StateForPlayer {
        public @Nullable ChunkLoader chunkLoader;
        
        public @Nullable PendingScaleBoxWrapping pendingScaleBoxWrapping;
        
        // TODO record player position and remove chunk loader when player moves
        
        public void clearChunkLoader(ServerPlayer player) {
            if (chunkLoader != null) {
                PortalAPI.removeChunkLoaderForPlayer(player, chunkLoader);
                chunkLoader = null;
            }
        }
        
        public void updateChunkLoader(ServerPlayer player, ChunkLoader chunkLoader) {
            clearChunkLoader(player);
            this.chunkLoader = chunkLoader;
            PortalAPI.addChunkLoaderForPlayer(player, chunkLoader);
        }
    }
    
    private final WeakHashMap<ServerPlayer, StateForPlayer> stateMap = new WeakHashMap<>();
    
    public ScaleBoxGuiManager(MinecraftServer server) {this.server = server;}
    
    private StateForPlayer getPlayerState(ServerPlayer player) {
        return this.stateMap.computeIfAbsent(player, k -> new StateForPlayer());
    }
    
    public void openManagementGui(ServerPlayer player, @Nullable Integer targetBoxId) {
        ScaleBoxRecord scaleBoxRecord = ScaleBoxRecord.get(player.server);
        
        List<ScaleBoxRecord.Entry> entries = scaleBoxRecord.getEntriesByOwner(player.getUUID());
        
        ManagementGuiData managementGuiData = new ManagementGuiData(entries, targetBoxId);
        
        /**{@link RemoteCallables#tellClientToOpenManagementGui(CompoundTag)}*/
        McRemoteProcedureCall.tellClientToInvoke(
            player,
            "qouteall.mini_scaled.gui.ScaleBoxGuiManager.RemoteCallables.tellClientToOpenManagementGui",
            managementGuiData.toTag()
        );
    }
    
    public void onRequestChunkLoading(ServerPlayer player, int boxId) {
        ScaleBoxRecord scaleBoxRecord = ScaleBoxRecord.get(player.server);
        
        ScaleBoxRecord.Entry entry = scaleBoxRecord.getEntryById(boxId);
        
        if (entry == null) {
            LOGGER.warn("Invalid scale box id for chunk loading {} {}", boxId, player);
            return;
        }
        
        if (!Objects.equals(entry.ownerId, player.getUUID())) {
            LOGGER.warn("Player {} is not the owner of scale box {}", player, boxId);
            return;
        }
        
        StateForPlayer state = getPlayerState(player);
        
        int renderDistance = McHelper.getLoadDistanceOnServer(player.server);
        state.updateChunkLoader(player, entry.createChunkLoader(renderDistance));
    }
    
    public void onCloseGui(ServerPlayer player) {
        StateForPlayer state = getPlayerState(player);
        
        state.clearChunkLoader(player);
        
        stateMap.remove(player);
    }
    
    public static void handleScaleBoxPropertyChange(
        ServerPlayer player, int boxId, Consumer<ScaleBoxRecord.Entry> func
    ) {
        ScaleBoxRecord rec = ScaleBoxRecord.get(player.server);
        
        ScaleBoxRecord.Entry entry = rec.getEntryById(boxId);
        
        if (entry == null) {
            LOGGER.warn("Invalid scale box id for property change {} {}", boxId, player);
            return;
        }
        
        if (!Objects.equals(entry.ownerId, player.getUUID())) {
            LOGGER.warn("Player {} is not the owner of scale box {}", player, boxId);
            return;
        }
        
        func.accept(entry);
        ScaleBoxGeneration.updateScaleBoxPortals(entry, ((ServerPlayer) player));
    }
    
    /**
     * @return whether successfully started
     */
    public boolean tryStartingPendingWrapping(
        ServerPlayer player, ResourceKey<Level> dimension,
        IntBox glassFrame, DyeColor color
    ) {
        StateForPlayer playerState = getPlayerState(player);
        
        BlockPos areaSize = glassFrame.getSize();
        
        if (areaSize.getX() <= 1 || areaSize.getY() <= 1 || areaSize.getZ() <= 1) {
            player.sendSystemMessage(Component.translatable(
                "mini_scaled.cannot_wrap_size_too_small"
            ));
            return false;
        }
        
        IntArrayList commonFactors = ScaleBoxOperations.getCommonFactors(
            areaSize.getX(), areaSize.getY(), areaSize.getZ()
        );
        
        if (commonFactors.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                "mini_scaled.no_integer_scale",
                areaSize.getX(), areaSize.getY(), areaSize.getZ()
            ));
            return false;
        }
        
        List<ScaleBoxWrappingScreen.Option> options = commonFactors.intStream()
            .mapToObj(
                scale -> new ScaleBoxWrappingScreen.Option(
                    scale, ScaleBoxOperations.getCost(areaSize, scale)
                )
            ).toList();
        
        playerState.pendingScaleBoxWrapping = new PendingScaleBoxWrapping(
            dimension, glassFrame, color, options
        );
        
        McRemoteProcedureCall.tellClientToInvoke(
            player,
            "qouteall.mini_scaled.gui.ScaleBoxGuiManager.RemoteCallables.tellClientToOpenPendingWrappingGui",
            options
        );
        
        return true;
    }
    
    public static record ManagementGuiData(
        List<ScaleBoxRecord.Entry> entriesForPlayer,
        @Nullable Integer boxId
    ) {
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            
            ListTag listTag = new ListTag();
            for (ScaleBoxRecord.Entry entry : entriesForPlayer) {
                listTag.add(entry.toTag());
            }
            
            tag.put("entries", listTag);
            
            if (boxId != null) {
                tag.putInt("targetBoxId", boxId);
            }
            
            return tag;
        }
        
        public static ManagementGuiData fromTag(CompoundTag tag) {
            ListTag listTag = tag.getList("entries", Tag.TAG_COMPOUND);
            
            List<ScaleBoxRecord.Entry> entries = listTag.stream()
                .map(t -> ScaleBoxRecord.Entry.fromTag((CompoundTag) t))
                .toList();
            
            Integer boxId = tag.contains("targetBoxId") ? tag.getInt("targetBoxId") : null;
            
            return new ManagementGuiData(entries, boxId);
        }
    }
    
    public static class RemoteCallables {
        @Environment(EnvType.CLIENT)
        public static void tellClientToOpenManagementGui(CompoundTag tag) {
            ScaleBoxManagementScreen.openGui(ManagementGuiData.fromTag(tag));
        }
        
        public static void requestChunkLoading(ServerPlayer player, int boxId) {
            ScaleBoxGuiManager scaleBoxGuiManager = ScaleBoxGuiManager.get(player.server);
            
            scaleBoxGuiManager.onRequestChunkLoading(player, boxId);
        }
        
        public static void onGuiClose(ServerPlayer player) {
            ScaleBoxGuiManager.get(player.server).onCloseGui(player);
        }
        
        public static void updateScaleBoxOption(
            ServerPlayer player, int boxId,
            boolean scaleTransform, boolean gravityTransform, boolean accessControl
        ) {
            ScaleBoxGuiManager.handleScaleBoxPropertyChange(
                player, boxId,
                entry -> {
                    entry.teleportChangesScale = scaleTransform;
                    entry.teleportChangesGravity = gravityTransform;
                    entry.accessControl = accessControl;
                }
            );
        }
        
        @Environment(EnvType.CLIENT)
        public static void tellClientToOpenPendingWrappingGui(
            ResourceKey<Level> dimension,
            List<ScaleBoxWrappingScreen.Option> options
        ) {
            ScaleBoxWrappingScreen screen = new ScaleBoxWrappingScreen(
                Component.literal(""),
                options
            );
            Minecraft.getInstance().setScreen(screen);
        }
    }
    
}
