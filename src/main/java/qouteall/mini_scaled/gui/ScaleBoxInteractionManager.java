package qouteall.mini_scaled.gui;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.mini_scaled.MSGlobal;
import qouteall.mini_scaled.ScaleBoxEntranceCreation;
import qouteall.mini_scaled.ScaleBoxGeneration;
import qouteall.mini_scaled.ScaleBoxOperations;
import qouteall.mini_scaled.ScaleBoxRecord;
import qouteall.mini_scaled.ducks.MiniScaled_MinecraftServerAccessor;
import qouteall.mini_scaled.item.ScaleBoxEntranceItem;
import qouteall.mini_scaled.util.MSUtil;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.api.McRemoteProcedureCall;
import qouteall.q_misc_util.my_util.IntBox;
import qouteall.q_misc_util.my_util.MyTaskList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Consumer;

public class ScaleBoxInteractionManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static final int ACQUIRE_ENTRANCE_COOLDOWN_SECONDS = 10;
    
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> get(server).tick());
    }
    
    private final MinecraftServer server;
    
    public static ScaleBoxInteractionManager get(MinecraftServer server) {
        return ((MiniScaled_MinecraftServerAccessor) server).miniScaled_getScaleBoxGuiManager();
    }
    
    public static class StateForPlayer {
        public @Nullable Integer clickedBoxId;
        
        public @Nullable ChunkLoader chunkLoader;
        
        public @Nullable PendingScaleBoxWrapping pendingScaleBoxWrapping;
        
        public long lastGetEntranceGameTime = 0;
        
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
    
    public ScaleBoxInteractionManager(MinecraftServer server) {this.server = server;}
    
    private StateForPlayer getPlayerState(ServerPlayer player) {
        return this.stateMap.computeIfAbsent(player, k -> new StateForPlayer());
    }
    
    private void tick() {
        stateMap.entrySet().removeIf(e -> e.getKey().isRemoved());
    }
    
    public void openManagementGui(ServerPlayer player, @Nullable Integer targetBoxId) {
        ScaleBoxRecord scaleBoxRecord = ScaleBoxRecord.get(player.server);
        
        List<ScaleBoxRecord.Entry> entries = scaleBoxRecord.getEntriesByOwner(player.getUUID());
        
        ManagementGuiData managementGuiData = new ManagementGuiData(entries, targetBoxId);
        
        /**{@link RemoteCallables#tellClientToOpenManagementGui(CompoundTag)}*/
        McRemoteProcedureCall.tellClientToInvoke(
            player,
            "qouteall.mini_scaled.gui.ScaleBoxInteractionManager.RemoteCallables.tellClientToOpenManagementGui",
            managementGuiData.toTag()
        );
        
        StateForPlayer playerState = getPlayerState(player);
        playerState.clickedBoxId = targetBoxId;
    }
    
    public void openManagementGuiForAllScaleBoxes(
        ServerPlayer player, @Nullable Integer targetBoxId
    ) {
        if (!player.hasPermissions(2)) {
            LOGGER.error("The player does not have level 2 permission to view all scale boxes {}", player);
            return;
        }
        
        ScaleBoxRecord scaleBoxRecord = ScaleBoxRecord.get(player.server);
        
        List<ScaleBoxRecord.Entry> entries = new ArrayList<>(scaleBoxRecord.getAllEntries());
        
        ManagementGuiData managementGuiData = new ManagementGuiData(entries, targetBoxId);
        
        /**{@link RemoteCallables#tellClientToOpenManagementGui(CompoundTag)}*/
        McRemoteProcedureCall.tellClientToInvoke(
            player,
            "qouteall.mini_scaled.gui.ScaleBoxInteractionManager.RemoteCallables.tellClientToOpenManagementGui",
            managementGuiData.toTag()
        );
        
        StateForPlayer playerState = getPlayerState(player);
        playerState.clickedBoxId = targetBoxId;
    }
    
    public void onRequestChunkLoading(ServerPlayer player, int boxId) {
        ScaleBoxRecord scaleBoxRecord = ScaleBoxRecord.get(player.server);
        
        ScaleBoxRecord.Entry entry = scaleBoxRecord.getEntryById(boxId);
        
        if (entry == null) {
            LOGGER.warn("Invalid scale box id for chunk loading {} {}", boxId, player);
            return;
        }
        
        if (!Objects.equals(entry.ownerId, player.getUUID()) && !player.hasPermissions(2)) {
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
        
        state.clickedBoxId = null;
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
        ScaleBoxRecord record = ScaleBoxRecord.get(player.server);
        
        int maxScaleBoxPerPlayer = MSGlobal.config.getConfig().maxScaleBoxPerPlayer;
        if (record.getEntriesByOwner(player.getUUID()).size() + 1 >= maxScaleBoxPerPlayer) {
            player.sendSystemMessage(Component.translatable(
                "mini_scaled.count_limit",
                maxScaleBoxPerPlayer
            ));
            return false;
        }
        
        StateForPlayer playerState = getPlayerState(player);
        
        BlockPos areaSize = glassFrame.getSize();
        
        if (areaSize.getX() <= 2 || areaSize.getY() <= 2 || areaSize.getZ() <= 2) {
            player.sendSystemMessage(Component.translatable(
                "mini_scaled.cannot_wrap_invalid_frame"
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
        
        /**{@link ScaleBoxInteractionManager.RemoteCallables#tellClientToOpenPendingWrappingGui}*/
        McRemoteProcedureCall.tellClientToInvoke(
            player,
            "qouteall.mini_scaled.gui.ScaleBoxInteractionManager.RemoteCallables.tellClientToOpenPendingWrappingGui",
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
        
        if (!checkGlassFrameIntegrityAndInform(player, glassFrame, world, glassBlockState)) {
            return;
        }
        
        if (!checkUnbreakableBlock(player, world, glassFrame)) {
            return;
        }
        
        if (!checkEntityInRegion(player, world, glassFrame)) {
            return;
        }
        
        int ingredientCost = option.ingredientCost();
        Item costItem = ScaleBoxEntranceCreation.getCreationItem();
        ItemStack costItemStack = new ItemStack(costItem, ingredientCost);
        
        // check item
        if (!player.isCreative()) {
            if (!checkInventory(player, costItemStack, ingredientCost)) {
                return;
            }
            
            // don't remove the item for now. remove when the chunk is loaded
        }
        
        IntBox wrappedBox = pendingScaleBoxWrapping.glassFrame();
        BlockPos boxSize = wrappedBox.getSize();
        BlockPos entranceSize = Helper.divide(boxSize, scale);
        IntBox entranceBox = wrappedBox.confineInnerBox(
            IntBox.fromBasePointAndSize(pendingScaleBoxWrapping.clickingPos(), entranceSize)
        );
        
        // pre-reserve the region id
        int regionId = scaleBoxRecord.reserveRegionId();
        
        ScaleBoxRecord.Entry newEntry = new ScaleBoxRecord.Entry();
        newEntry.id = -1; // allocate id later
        newEntry.regionId = regionId;
        newEntry.color = pendingScaleBoxWrapping.color();
        newEntry.ownerId = player.getUUID();
        newEntry.ownerNameCache = player.getName().getString();
        newEntry.scale = scale;
        newEntry.generation = 0;
        newEntry.innerBoxPos = ScaleBoxGeneration.getInnerBoxPosFromRegionId(regionId);
        newEntry.currentEntranceSize = entranceSize;
        
        int renderDistance = McHelper.getLoadDistanceOnServer(player.server);
        ChunkLoader chunkLoader = newEntry.createChunkLoader(renderDistance);
        
        // don't add the entry to record now
        // add when the process finishes
        
        PortalAPI.addChunkLoaderForPlayer(player, chunkLoader);
        
        Runnable onFail = () -> {
            LOGGER.info("On wrapping failed {} {}", player, newEntry);
            scaleBoxRecord.clearRegionId(regionId);
        };
        
        IPGlobal.serverTaskList.addTask(MyTaskList.withMacroLifecycle(
            // begin action
            () -> {},
            
            // end action
            () -> {
                // don't immediately remove the chunk loading
                // because portal chunk loading has delay
                IPGlobal.serverTaskList.addTask(MyTaskList.withDelay(
                    20, MyTaskList.oneShotTask(() -> {
                        LOGGER.info("Cleaning up wrapping {} {}", player, newEntry);
                        
                        PortalAPI.removeChunkLoaderForPlayer(player, chunkLoader);
                    })
                ));
            },
            
            // task
            () -> {
                if (player.isRemoved()) {
                    onFail.run();
                    return true;
                }
                
                if (server.getLevel(chunkLoader.center.dimension) == null ||
                    server.getLevel(pendingScaleBoxWrapping.dimension()) == null
                ) {
                    // dimension dynamically removed
                    onFail.run();
                    return true;
                }
                
                if (chunkLoader.getLoadedChunkNum(server) < chunkLoader.getChunkNum()) {
                    // wait for loading
                    
                    player.displayClientMessage(
                        Component.translatable("mini_scaled.wait_for_chunk_loading"),
                        true
                    );
                    
                    return false;
                }
                
                // clear loading chunk message
                player.displayClientMessage(Component.empty(), true);
                
                // re-check because chunk loading takes time, and it may change during loading
                
                if (!checkGlassFrameIntegrityAndInform(player, glassFrame, world, glassBlockState)) {
                    onFail.run();
                    return true;
                }
                if (!checkUnbreakableBlock(player, world, glassFrame)) {
                    onFail.run();
                    return true;
                }
                if (!checkEntityInRegion(player, world, glassFrame)) {
                    onFail.run();
                    return true;
                }
                
                if (!player.isCreative()) {
                    if (!checkInventory(player, costItemStack, ingredientCost)) {
                        onFail.run();
                        return true;
                    }
                    
                    // finally remove the item
                    MSUtil.removeItem(player.getInventory(), s -> s.getItem() == costItem, ingredientCost);
                }
                
                // finally do wrapping
                newEntry.id = scaleBoxRecord.allocateId();
                scaleBoxRecord.addEntry(newEntry);
                scaleBoxRecord.setDirty();
                
                ScaleBoxOperations.doWrap(world, player, newEntry, wrappedBox, entranceBox);
                
                return true;
            }
        ));
    }
    
    private static boolean checkEntityInRegion(ServerPlayer player, ServerLevel world, IntBox glassFrame) {
        Entity nonWrappableEntity = ScaleBoxOperations.checkNonWrappableEntity(world, glassFrame);
        if (nonWrappableEntity != null) {
            player.sendSystemMessage(Component.translatable(
                "mini_scaled.non_wrappable_entity",
                nonWrappableEntity.getDisplayName()
            ));
            return false;
        }
        return true;
    }
    
    private static boolean checkInventory(ServerPlayer player, ItemStack costItemStack, int ingredientCost) {
        int playerItemCount = player.getInventory().countItem(costItemStack.getItem());
        if (playerItemCount < costItemStack.getCount()) {
            player.sendSystemMessage(Component.translatable(
                "mini_scaled.not_enough_ingredient",
                ingredientCost, costItemStack.getDisplayName()
            ));
            return false;
        }
        return true;
    }
    
    private static boolean checkUnbreakableBlock(ServerPlayer player, ServerLevel world, IntBox glassFrame) {
        BlockPos nonWrappableBlockPos = ScaleBoxOperations.checkNonWrappableBlock(world, glassFrame);
        if (nonWrappableBlockPos != null) {
            player.sendSystemMessage(Component.translatable(
                "mini_scaled.non_wrappable_block",
                nonWrappableBlockPos.getX(), nonWrappableBlockPos.getY(), nonWrappableBlockPos.getZ()
            ));
            return false;
        }
        return true;
    }
    
    private static boolean checkGlassFrameIntegrityAndInform(
        ServerPlayer player, IntBox glassFrame, ServerLevel world, BlockState glassBlockState
    ) {
        for (IntBox edge : glassFrame.get12Edges()) {
            if (edge.fastStream().anyMatch(p -> world.getBlockState(p) != glassBlockState)) {
                player.sendSystemMessage(Component.translatable(
                    "mini_scaled.glass_frame_broken"
                ));
                return false;
            }
        }
        
        return true;
    }
    
    public void cancelWrapping(ServerPlayer player) {
        StateForPlayer playerState = getPlayerState(player);
        
        playerState.pendingScaleBoxWrapping = null;
    }
    
    private void acquireEntrance(ServerPlayer player, int boxId) {
        StateForPlayer playerState = getPlayerState(player);
        
        long gameTime = player.level().getGameTime();
        
        if (gameTime - playerState.lastGetEntranceGameTime > 20 * ACQUIRE_ENTRANCE_COOLDOWN_SECONDS) {
            playerState.lastGetEntranceGameTime = gameTime;
            
            ScaleBoxRecord record = ScaleBoxRecord.get(player.server);
            ScaleBoxRecord.Entry entry = record.getEntryById(boxId);
            
            if (entry == null) {
                LOGGER.warn("Invalid scale box id {} {} when acquiring entrance", boxId, player);
                player.sendSystemMessage(Component.literal("Invalid box id"));
                return;
            }
            
            ItemStack itemStack = new ItemStack(
                ScaleBoxEntranceItem.instance, 1
            );
            itemStack.setTag(
                new ScaleBoxEntranceItem.ItemInfo(
                    entry.scale, entry.color, entry.ownerId, entry.ownerNameCache, boxId
                ).toTag()
            );
            
            player.getInventory().add(itemStack);
            // no need to care about the case when inventory is full, because entrance item is free now
        }
        else {
            player.sendSystemMessage(Component.translatable(
                "mini_scaled.acquire_entrance_cooldown",
                ACQUIRE_ENTRANCE_COOLDOWN_SECONDS
            ));
        }
    }
    
    private void confirmUnwrapping(ServerPlayer player, int boxId, IntBox unwrappingArea) {
        ScaleBoxRecord record = ScaleBoxRecord.get(player.server);
        ScaleBoxRecord.Entry entry = record.getEntryById(boxId);
        
        if (entry == null) {
            player.sendSystemMessage(Component.literal("Invalid box id"));
            return;
        }
        
        if (!Objects.equals(player.getUUID(), entry.ownerId)) {
            player.sendSystemMessage(Component.literal("You are not the owner of this box"));
            return;
        }
        
        ServerLevel world = ((ServerLevel) player.level());
        if (world.dimension() != entry.currentEntranceDim) {
            player.sendSystemMessage(Component.literal("You are not in the dimension of the entrance"));
            return;
        }
        
        if (player.position().subtract(entry.getOuterAreaBox().getCenterVec()).lengthSqr() > 32 * 32) {
            player.sendSystemMessage(Component.literal("You are too far away from the entrance"));
            return;
        }
        
        // does not check whether the scale box contains non-wrappable blocks and entities
        // because having them is not feasible in non-cheating gameplay
        // and the player should always be able to unwrap any box to free slot
        
        ScaleBoxOperations.preUnwrap(
            player, entry, unwrappingArea,
            entry.getEntranceRotation()
        );
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
            ScaleBoxInteractionManager scaleBoxInteractionManager = ScaleBoxInteractionManager.get(player.server);
            
            scaleBoxInteractionManager.onRequestChunkLoading(player, boxId);
        }
        
        public static void onGuiClose(ServerPlayer player) {
            ScaleBoxInteractionManager.get(player.server).onCloseGui(player);
        }
        
        public static void updateScaleBoxOption(
            ServerPlayer player, int boxId,
            boolean scaleTransform, boolean gravityTransform, boolean accessControl
        ) {
            ScaleBoxInteractionManager.handleScaleBoxPropertyChange(
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
            ScaleBoxInteractionManager.get(player.server).confirmWrapping(player, scale);
        }
        
        public static void cancelWrapping(ServerPlayer player) {
            ScaleBoxInteractionManager.get(player.server).cancelWrapping(player);
        }
        
        public static void acquireEntrance(ServerPlayer player, int boxId) {
            ScaleBoxInteractionManager.get(player.server).acquireEntrance(player, boxId);
        }
        
        public static void confirmUnwrapping(
            ServerPlayer player,
            int boxId,
            IntBox area
        ) {
            ScaleBoxInteractionManager.get(player.server).confirmUnwrapping(player, boxId, area);
        }
    }
    
}
