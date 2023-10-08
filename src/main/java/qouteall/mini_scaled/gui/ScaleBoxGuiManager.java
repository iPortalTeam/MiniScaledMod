package qouteall.mini_scaled.gui;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.mini_scaled.ScaleBoxGeneration;
import qouteall.mini_scaled.ScaleBoxRecord;
import qouteall.mini_scaled.ducks.MiniScaled_MinecraftServerAccessor;
import qouteall.q_misc_util.api.McRemoteProcedureCall;

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
    
    public void openGui(ServerPlayer player, @Nullable Integer targetBoxId) {
        ScaleBoxRecord scaleBoxRecord = ScaleBoxRecord.get();
        
        List<ScaleBoxRecord.Entry> entries = scaleBoxRecord.getEntriesByOwner(player.getUUID());
        
        GuiData guiData = new GuiData(entries, targetBoxId);
        
        McRemoteProcedureCall.tellClientToInvoke(
            player,
            "qouteall.mini_scaled.gui.ScaleBoxGuiManager.RemoteCallables.tellClientToOpenGui",
            guiData.toTag()
        );
    }
    
    public void onRequestChunkLoading(ServerPlayer player, int boxId) {
        ScaleBoxRecord scaleBoxRecord = ScaleBoxRecord.get();
        
        ScaleBoxRecord.Entry entry = scaleBoxRecord.getEntryById(boxId);
        
        if (entry == null) {
            LOGGER.warn("Invalid scale box id for chunk loading {} {}", boxId, player);
            return;
        }
        
        if (!Objects.equals(entry.ownerId, player.getUUID())) {
            LOGGER.warn("Player {} is not the owner of scale box {}", player, boxId);
            return;
        }
        
        StateForPlayer state = this.stateMap.computeIfAbsent(player, k -> new StateForPlayer());
        
        int renderDistance = McHelper.getLoadDistanceOnServer(player.server);
        state.updateChunkLoader(player, entry.createChunkLoader(renderDistance));
    }
    
    public void onCloseGui(ServerPlayer player) {
        StateForPlayer state = this.stateMap.computeIfAbsent(player, k -> new StateForPlayer());
        
        state.clearChunkLoader(player);
        
        stateMap.remove(player);
    }
    
    public static void handleScaleBoxPropertyChange(
        ServerPlayer player, int boxId, Consumer<ScaleBoxRecord.Entry> func
    ) {
        ScaleBoxRecord rec = ScaleBoxRecord.get();
        
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
    
    public static record GuiData(
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
        
        public static GuiData fromTag(CompoundTag tag) {
            ListTag listTag = tag.getList("entries", Tag.TAG_COMPOUND);
            
            List<ScaleBoxRecord.Entry> entries = listTag.stream()
                .map(t -> ScaleBoxRecord.Entry.fromTag((CompoundTag) t))
                .toList();
            
            Integer boxId = tag.contains("targetBoxId") ? tag.getInt("targetBoxId") : null;
            
            return new GuiData(entries, boxId);
        }
    }
    
    public static class RemoteCallables {
        @Environment(EnvType.CLIENT)
        public static void tellClientToOpenGui(CompoundTag tag) {
            ScaleBoxManagementScreen.openGui(GuiData.fromTag(tag));
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
    }
}
