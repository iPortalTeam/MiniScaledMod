package qouteall.mini_scaled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.imm_ptl.core.chunk_loading.DimensionalChunkPos;
import qouteall.imm_ptl.core.portal.PortalExtension;
import qouteall.imm_ptl.core.portal.PortalManipulation;
import qouteall.imm_ptl.core.portal.shape.BoxPortalShape;
import qouteall.mini_scaled.block.BoxBarrierBlock;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.AARotation;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.IntBox;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ScaleBoxGeneration {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleBoxGeneration.class);
    
    public static final int[] supportedScales = {4, 8, 16, 32};
    
    public static void createScaleBoxPortals(
        ServerLevel innerWorld,
        ServerLevel outerWorld,
        ScaleBoxRecord.Entry entry
    ) {
        AARotation entranceRotation = entry.getEntranceRotation();
        AARotation toInnerRotation = entranceRotation.getInverse();
        AABB outerAreaBox = entry.getOuterAreaBox().toRealNumberBox();
        AABB innerAreaBox = entry.getInnerAreaBox().toRealNumberBox();
        BlockPos outerAreaBoxSize = entry.getOuterAreaBox().getSize();
        int scale = entry.scale;
        DQuaternion quaternion = toInnerRotation.matrix.toQuaternion();
        int boxId = entry.id;
        int generation = entry.generation;
        
        MiniScaledPortal portal = MiniScaledPortal.entityType.create(outerWorld);
        assert portal != null;
        
        portal.setOriginPos(outerAreaBox.getCenter());
        
        portal.setPortalShape(BoxPortalShape.FACING_OUTWARDS);
        
        portal.axisW = new Vec3(1, 0, 0);
        portal.axisH = new Vec3(0, 1, 0);
        portal.width = outerAreaBoxSize.getX();
        portal.height = outerAreaBoxSize.getY();
        portal.thickness = outerAreaBoxSize.getZ();
        
        portal.setDestinationDimension(innerWorld.dimension());
        portal.setDestination(innerAreaBox.getCenter());
        
        portal.setRotation(quaternion);
        portal.scaling = scale;
        
        portal.teleportChangesScale = entry.teleportChangesScale;
        portal.setTeleportChangesGravity(entry.teleportChangesGravity);
        portal.fuseView = true;
        portal.hasCrossPortalCollision = true;
        portal.portalTag = "mini_scaled:scaled_box";
        PortalExtension.get(portal).adjustPositionAfterTeleport = true;
        portal.setInteractable(true);
        portal.boxId = boxId;
        portal.generation = generation;
        portal.recordEntry = entry;
        
        McHelper.spawnServerEntity(portal);
        
        MiniScaledPortal reversePortal =
            PortalManipulation.createReversePortal(portal, MiniScaledPortal.entityType);
        
        reversePortal.fuseView = false;
        reversePortal.hasCrossPortalCollision = true;
        reversePortal.setInteractable(true);
        reversePortal.boxId = boxId;
        reversePortal.generation = generation;
        reversePortal.recordEntry = entry;
        
        // When used with Iris, it renders normal portal instead of fuse view,
        // then when the player touches the portal, it wrongly renders the player.
        // It's a workaround to avoid this.
        reversePortal.doRenderPlayer = false;
        
        McHelper.spawnServerEntity(reversePortal);
    }
    
    
    public static ScaleBoxRecord.Entry getOrCreateEntry(
        MinecraftServer server,
        UUID playerId, String playerName, int scale, DyeColor color, ScaleBoxRecord record
    ) {
        Validate.notNull(playerId);
        
        ScaleBoxRecord.Entry entry = record.getEntriesByOwner(playerId).stream().filter(
            e -> e.color == color && e.scale == scale
        ).findFirst().orElse(null);
        
        if (entry == null) {
            int newId = record.allocateId();
            ScaleBoxRecord.Entry newEntry = new ScaleBoxRecord.Entry();
            newEntry.id = newId;
            newEntry.color = color;
            newEntry.ownerId = playerId;
            newEntry.ownerNameCache = playerName;
            newEntry.scale = scale;
            newEntry.generation = 0;
            newEntry.innerBoxPos = getInnerBoxPosFromId(newId);
            newEntry.currentEntranceSize = new BlockPos(1, 1, 1);
            record.addEntry(newEntry);
            record.setDirty(true);
            
            initializeInnerBoxBlocks(server, null, newEntry);
            
            entry = newEntry;
        }
        return entry;
    }
    
    public static BlockPos getInnerBoxPosFromId(int boxId) {
        int xIndex = boxId % 256;
        int zIndex = Mth.floorDiv(boxId, 256);
        
        return new BlockPos(xIndex * 16 * 32, 64, zIndex * 16 * 32);
    }
    
    public static BlockPos getNearestPosInScaleBoxToTeleportTo(BlockPos pos) {
        double gridLen = 16.0 * 32;
        return BlockPos.containing(
            Math.round(pos.getX() / gridLen) * gridLen + 2,
            ScaleBoxManipulation.MAX_INNER_LEN + 2,
            Math.round(pos.getZ() / gridLen) * gridLen + 2
        );
    }
    
    public static void initializeInnerBoxBlocks(
        MinecraftServer server,
        @Nullable BlockPos oldEntranceSize,
        ScaleBoxRecord.Entry entry
    ) {
        IntBox innerAreaBox = entry.getInnerAreaBox();
        
        ServerLevel voidWorld = VoidDimension.getVoidServerWorld(server);
        
        ChunkLoader chunkLoader = new ChunkLoader(
            new DimensionalChunkPos(
                voidWorld.dimension(),
                new ChunkPos(innerAreaBox.getCenter())
            ),
            Math.max(innerAreaBox.getSize().getX(), innerAreaBox.getSize().getZ()) / 16 + 2
        );
        
        Block glassBlock = getGlassBlock(entry.color);
        BlockState frameBlock = glassBlock.defaultBlockState();
        
        // set block after fulling loading the chunk
        // to avoid lighting problems
        chunkLoader.loadChunksAndDo(() -> {
            IntBox newEntranceOffsets = IntBox.fromBasePointAndSize(BlockPos.ZERO, entry.currentEntranceSize);
            IntBox oldEntranceOffsets = oldEntranceSize != null ?
                IntBox.fromBasePointAndSize(BlockPos.ZERO, oldEntranceSize) : null;
            
            // clear the barrier blocks if the scale box expanded
            newEntranceOffsets.stream().forEach(offset -> {
                if (oldEntranceOffsets != null) {
                    if (oldEntranceOffsets.contains(offset)) {
                        // if expanded, don't clear the existing regions
                        return;
                    }
                }
                
                entry.getInnerUnitBox(offset).fastStream().forEach(blockPos -> {
                    voidWorld.setBlockAndUpdate(blockPos, Blocks.AIR.defaultBlockState());
                });
            });
            
            if (oldEntranceOffsets != null) {
                // clear the shrunk area's blocks
                oldEntranceOffsets.stream().forEach(offset -> {
                    if (newEntranceOffsets.contains(offset)) {
                        return;
                    }
                    
                    IntBox innerUnitBox = entry.getInnerUnitBox(offset);
                    
                    innerUnitBox.fastStream().forEach(blockPos -> {
                        voidWorld.setBlockAndUpdate(blockPos, Blocks.AIR.defaultBlockState());
                    });
                });
            }
            
            // put the new barrier blocks
            IntBox expanded = innerAreaBox.getAdjusted(-1, -1, -1, 1, 1, 1);
            for (Direction direction : Direction.values()) {
                expanded.getSurfaceLayer(direction).fastStream().forEach(blockPos -> {
                    voidWorld.setBlockAndUpdate(blockPos, BoxBarrierBlock.instance.defaultBlockState());
                });
            }
            
            // find the untouched unit regions
            Set<BlockPos> untouchedRegionOffsets = newEntranceOffsets.stream().filter(
                offset -> isUnitRegionUntouched(entry, offset, voidWorld, frameBlock)
            ).map(BlockPos::immutable).collect(Collectors.toSet());
            
            // clear the untouched unit regions
            untouchedRegionOffsets.forEach(offset -> {
                IntBox innerUnitBox = entry.getInnerUnitBox(offset);
                
                innerUnitBox.fastStream().forEach(blockPos -> {
                    voidWorld.setBlockAndUpdate(blockPos, Blocks.AIR.defaultBlockState());
                });
            });
            
            // regenerate the outer frame in the untouched unit regions
            for (IntBox edge : entry.getInnerAreaLocalBox().get12Edges()) {
                edge.fastStream().forEach(blockOffset -> {
                    BlockPos unitRegionOffset = entry.blockOffsetToUnitRegionOffset(blockOffset);
                    if (untouchedRegionOffsets.contains(unitRegionOffset)) {
                        BlockPos blockPos = entry.innerBoxPos.offset(blockOffset);
                        voidWorld.setBlockAndUpdate(blockPos, frameBlock);
                    }
                });
            }
            
        });
        
    }
    
    // untouched means it's all air, except that on the edge it can have frame blocks
    private static boolean isUnitRegionUntouched(
        ScaleBoxRecord.Entry entry,
        BlockPos regionOffset,
        ServerLevel voidWorld,
        BlockState frameBlock
    ) {
        IntBox innerUnitBox = entry.getInnerUnitBox(regionOffset);
        return innerUnitBox.fastStream().allMatch(blockPos -> {
            BlockState blockState = voidWorld.getBlockState(blockPos);
            if (innerUnitBox.isOnEdge(blockPos)) {
                return blockState.isAir() || blockState == frameBlock;
            }
            else {
                return blockState.isAir();
            }
        });
    }
    
    public static Block getGlassBlock(DyeColor color) {
        return BuiltInRegistries.BLOCK.get(new ResourceLocation("minecraft:" + color.getName() + "_stained_glass"));
    }
    
    public static boolean isValidScale(int size) {
        return Arrays.stream(supportedScales).anyMatch(s -> s == size);
    }
    
    // will set dirty
    public static void updateScaleBoxPortals(
        ScaleBoxRecord.Entry entry,
        ServerPlayer player
    ) {
        ResourceKey<Level> currentEntranceDim = entry.currentEntranceDim;
        if (currentEntranceDim == null) {
            LOGGER.error("Updating a scale box that has no entrance");
            return;
        }
        ScaleBoxOperations.putScaleBoxEntranceIntoWorld(
            entry,
            McHelper.getServerWorld(currentEntranceDim),
            entry.currentEntrancePos,
            entry.getEntranceRotation(),
            player
        );
    }
    
    public static void createInnerPortalsPointingToVoidUnderneath(
        MinecraftServer server,
        ScaleBoxRecord.Entry entry
    ) {
        ServerLevel voidWorld = VoidDimension.getVoidServerWorld(server);
        AABB innerAreaBox = entry.getInnerAreaBox().toRealNumberBox();
        Vec3 innerAreaBoxSize = Helper.getBoxSize(innerAreaBox);
        int boxId = entry.id;
        int generation = entry.generation;
        
        MiniScaledPortal portal = MiniScaledPortal.entityType.create(voidWorld);
        assert portal != null;
        
        portal.setPortalShape(BoxPortalShape.FACING_INWARDS);
        
        portal.axisW = new Vec3(1, 0, 0);
        portal.axisH = new Vec3(0, 1, 0);
        portal.width = innerAreaBoxSize.x();
        portal.height = innerAreaBoxSize.y();
        portal.thickness = innerAreaBoxSize.z();
        
        portal.setOriginPos(innerAreaBox.getCenter());
        portal.setDestination(portal.getOriginPos().add(0, -1000, 0));
        portal.setDestinationDimension(voidWorld.dimension());
        
        portal.portalTag = "mini_scaled:scaled_box_inner_wrapping";
        portal.boxId = boxId;
        portal.generation = generation;
        
        McHelper.spawnServerEntity(portal);
    }
}
