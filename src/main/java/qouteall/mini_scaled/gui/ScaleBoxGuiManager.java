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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.mini_scaled.ScaleBoxEntranceCreation;
import qouteall.mini_scaled.ScaleBoxGeneration;
import qouteall.mini_scaled.ScaleBoxOperations;
import qouteall.mini_scaled.ScaleBoxRecord;
import qouteall.mini_scaled.ducks.MiniScaled_MinecraftServerAccessor;
import qouteall.mini_scaled.util.MSUtil;
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
        IntBox glassFrame, DyeColor color,
        BlockPos clickedPos
    ) {
        StateForPlayer playerState = getPlayerState(player);
        
        BlockPos areaSize = glassFrame.getSize();
        
        if (areaSize.getX() <= 2 || areaSize.getY() <= 2 || areaSize.getZ() <= 2) {
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
            dimension, glassFrame, color, options, clickedPos
        );
        
        /**{@link qouteall.mini_scaled.gui.ScaleBoxGuiManager.RemoteCallables#tellClientToOpenPendingWrappingGui}*/
        McRemoteProcedureCall.tellClientToInvoke(
            player,
            "qouteall.mini_scaled.gui.ScaleBoxGuiManager.RemoteCallables.tellClientToOpenPendingWrappingGui",
            dimension,
            options,
            glassFrame.getSize(),
            ScaleBoxEntranceCreation.getCreationItem()
        );
        
        return true;
    }
    
    public void confirmWrapping(ServerPlayer player, int scale) {
        StateForPlayer playerState = getPlayerState(player);
        
        PendingScaleBoxWrapping pendingScaleBoxWrapping = playerState.pendingScaleBoxWrapping;
        
        if (pendingScaleBoxWrapping == null) {
            player.sendSystemMessage(Component.literal("Failed to confirm wrapping scale box."));
            return;
        }
        
        ScaleBoxWrappingScreen.Option option = pendingScaleBoxWrapping.options().stream()
            .filter(o -> o.scale() == scale)
            .findFirst().orElse(null);
        
        if (option == null) {
            player.sendSystemMessage(Component.literal("Invalid option"));
            return;
        }
        
        // check color+scale conflict
        DyeColor color = pendingScaleBoxWrapping.color();
        ScaleBoxRecord scaleBoxRecord = ScaleBoxRecord.get(server);
        boolean hasExisting = scaleBoxRecord.getEntriesByOwner(player.getUUID())
            .stream().anyMatch(e -> e.color == color && e.scale == scale);
        if (hasExisting) {
            player.sendSystemMessage(Component.translatable(
                "mini_scaled.already_has_box",
                MSUtil.getColorText(color),
                scale
            ));
            return;
        }
        
        // check glass frame integrity
        IntBox glassFrame = pendingScaleBoxWrapping.glassFrame();
        ServerLevel world = server.getLevel(pendingScaleBoxWrapping.dimension());
        if (world == null) {
            LOGGER.error("Cannot find world {}", pendingScaleBoxWrapping.dimension().location());
            return;
        }
        BlockState glassBlockState =
            ScaleBoxGeneration.getGlassBlock(pendingScaleBoxWrapping.color()).defaultBlockState();
        for (IntBox edge : glassFrame.get12Edges()) {
            if (edge.fastStream().anyMatch(p -> world.getBlockState(p) != glassBlockState)) {
                player.sendSystemMessage(Component.translatable(
                    "mini_scaled.glass_frame_broken"
                ));
                return;
            }
        }
        
        // check item
        if (!player.isCreative()) {
            int ingredientCost = option.ingredientCost();
            Item costItem = ScaleBoxEntranceCreation.getCreationItem();
            ItemStack costItemStack = new ItemStack(costItem, ingredientCost);
            Inventory playerInventory = player.getInventory();
            int playerItemCount = playerInventory.countItem(costItem);
            if (playerItemCount < ingredientCost) {
                player.sendSystemMessage(Component.translatable(
                    "mini_scaled.not_enough_ingredient",
                    ingredientCost, costItemStack.getDisplayName()
                ));
                return;
            }
            
            MSUtil.removeItem(playerInventory, s -> s.getItem() == costItem, ingredientCost);
        }
        
        ScaleBoxOperations.wrap(
            world, pendingScaleBoxWrapping.glassFrame(),
            player, pendingScaleBoxWrapping.color(),
            scale, pendingScaleBoxWrapping.clickingPos()
        );
    }
    
    public void cancelWrapping(ServerPlayer player) {
        StateForPlayer playerState = getPlayerState(player);
        
        playerState.pendingScaleBoxWrapping = null;
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
            List<ScaleBoxWrappingScreen.Option> options,
            BlockPos boxSize,
            Item creationItem
        ) {
            ScaleBoxWrappingScreen screen = new ScaleBoxWrappingScreen(
                Component.literal(""),
                options, boxSize, creationItem
            );
            Minecraft.getInstance().setScreen(screen);
        }
        
        public static void confirmWrapping(ServerPlayer player, int scale) {
            ScaleBoxGuiManager.get(player.server).confirmWrapping(player, scale);
        }
        
        public static void cancelWrapping(ServerPlayer player) {
            ScaleBoxGuiManager.get(player.server).cancelWrapping(player);
        }
    }
    
}
