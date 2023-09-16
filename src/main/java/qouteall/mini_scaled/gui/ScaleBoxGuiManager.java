package qouteall.mini_scaled.gui;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.mini_scaled.ScaleBoxRecord;
import qouteall.mini_scaled.ducks.MiniScaled_MinecraftServerAccessor;
import qouteall.q_misc_util.api.McRemoteProcedureCall;

import java.util.Objects;
import java.util.WeakHashMap;

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
    
    public void onUpdateGui(ServerPlayer player, int boxId) {
        ScaleBoxRecord scaleBoxRecord = ScaleBoxRecord.get();
        
        ScaleBoxRecord.Entry entry = scaleBoxRecord.getEntryById(boxId);
        
        if (entry == null) {
            LOGGER.warn("Scale box not found {}", boxId);
            return;
        }
        
        if (!Objects.equals(player.getUUID(), entry.ownerId)) {
            LOGGER.warn("Scale box not owned by {}", player);
            return;
        }
        
        ChunkLoader chunkLoader = entry.createChunkLoader();
        
        StateForPlayer state = this.stateMap.computeIfAbsent(player, k -> new StateForPlayer());
        
        state.updateChunkLoader(player, chunkLoader);
        
        McRemoteProcedureCall.tellClientToInvoke(
            player,
            "qouteall.mini_scaled.gui.ScaleBoxGuiManager.RemoteCallables.tellClientToOpenGui",
            boxId,
            entry.getInnerAreaBox().getCenterVec()
        );
    }
    
    public void onCloseGui(ServerPlayer player) {
        StateForPlayer state = this.stateMap.computeIfAbsent(player, k -> new StateForPlayer());
        
        state.clearChunkLoader(player);
        
        stateMap.remove(player);
    }
    
    public static class RemoteCallables {
        @Environment(EnvType.CLIENT)
        public static void tellClientToOpenGui(int boxId, Vec3 pos) {
            ScaleBoxManagementScreen.openGui(boxId, pos);
        }
        
        public static void onGuiClose(ServerPlayer player) {
            ScaleBoxGuiManager.get(player.server).onCloseGui(player);
        }
    }
}
