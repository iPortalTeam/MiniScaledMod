package qouteall.mini_scaled;

import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Pair;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.imm_ptl.core.chunk_loading.DimensionalChunkPos;
import qouteall.imm_ptl.core.portal.PortalExtension;
import qouteall.imm_ptl.core.portal.PortalManipulation;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.apache.commons.lang3.Validate;
import qouteall.mini_scaled.block.BoxBarrierBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlockEntity;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.AARotation;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.IntBox;

import java.util.Arrays;
import java.util.UUID;

public class ScaleBoxGeneration {
    static final int[] supportedScales = {4, 8, 16, 32};
    
    public static void putScaleBoxIntoWorld(
        ScaleBoxRecord.Entry entry,
        ServerWorld world, BlockPos outerBoxBasePos,
        AARotation rotation
    ) {
        entry.currentEntranceDim = world.getRegistryKey();
        entry.currentEntrancePos = outerBoxBasePos;
        entry.entranceRotation = rotation;
        entry.generation++;
        
        ScaleBoxRecord.get().setDirty(true);
        
        ServerWorld voidWorld = VoidDimension.getVoidWorld();
        createScaleBoxPortals(voidWorld, world, entry);
        
        entry.getOuterAreaBox().stream().forEach(outerPos -> {
            world.setBlockState(outerPos, ScaleBoxPlaceholderBlock.instance.getDefaultState());
            
            BlockEntity blockEntity = world.getBlockEntity(outerPos);
            if (blockEntity == null) {
                System.err.println("cannot find block entity for scale box");
            }
            else {
                ScaleBoxPlaceholderBlockEntity be = (ScaleBoxPlaceholderBlockEntity) blockEntity;
                be.boxId = entry.id;
                be.isBasePos = outerPos.equals(entry.currentEntrancePos);
            }
        });
    }
    
    private static void createScaleBoxPortals(
        ServerWorld innerWorld,
        ServerWorld outerWorld,
        ScaleBoxRecord.Entry entry
    ) {
        AARotation entranceRotation = entry.getEntranceRotation();
        AARotation toInnerRotation = entranceRotation.getInverse();
        Box outerAreaBox = entry.getOuterAreaBox().toRealNumberBox();
        Box innerAreaBox = entry.getInnerAreaBox().toRealNumberBox();
        BlockPos outerAreaBoxSize = entry.getOuterAreaBox().getSize();
        int scale = entry.scale;
        DQuaternion quaternion = toInnerRotation.matrix.toQuaternion();
        int boxId = entry.id;
        int generation = entry.generation;
        
        for (Direction outerDirection : Direction.values()) {
            MiniScaledPortal portal = MiniScaledPortal.entityType.create(outerWorld);
            Validate.notNull(portal);
            
            portal.setDestinationDimension(innerWorld.getRegistryKey());
            
            Direction innerDirection = toInnerRotation.transformDirection(outerDirection);
            
            portal.setOriginPos(Helper.getBoxSurface(outerAreaBox, outerDirection).getCenter());
            portal.setDestination(Helper.getBoxSurface(innerAreaBox, innerDirection).getCenter());
            
            Pair<Direction, Direction> perpendicularDirections = Helper.getPerpendicularDirections(outerDirection);
            Direction pd1 = perpendicularDirections.getLeft();
            Direction pd2 = perpendicularDirections.getRight();
            
            portal.setOrientation(Vec3d.of(pd1.getVector()), Vec3d.of(pd2.getVector()));
            portal.setWidth(Helper.getCoordinate(outerAreaBoxSize, pd1.getAxis()));
            portal.setHeight(Helper.getCoordinate(outerAreaBoxSize, pd2.getAxis()));
            
            portal.setRotation(quaternion);
            
            portal.scaling = scale;
            portal.teleportChangesScale = false;
            portal.fuseView = true;
            portal.renderingMergable = true;
            portal.hasCrossPortalCollision = true;
            portal.portalTag = "mini_scaled:scaled_box";
            PortalExtension.get(portal).adjustPositionAfterTeleport = false;
            portal.setInteractable(false);
            portal.boxId = boxId;
            portal.generation = generation;
            
            McHelper.spawnServerEntity(portal);
            
            MiniScaledPortal reversePortal =
                PortalManipulation.createReversePortal(portal, MiniScaledPortal.entityType);
            
            reversePortal.fuseView = false;
            reversePortal.renderingMergable = true;
            reversePortal.hasCrossPortalCollision = true;
            if (outerDirection != Direction.DOWN && outerDirection != Direction.UP) {
                PortalExtension.get(reversePortal).adjustPositionAfterTeleport = true;
            }
            reversePortal.setInteractable(false);
            reversePortal.boxId = boxId;
            reversePortal.generation = generation;
            
            McHelper.spawnServerEntity(reversePortal);
        }
    }
    
    
    public static ScaleBoxRecord.Entry getOrCreateEntry(
        UUID playerId, String playerName, int scale, DyeColor color, ScaleBoxRecord record
    ) {
        Validate.notNull(playerId);
        
        ScaleBoxRecord.Entry entry = record.getEntryByPredicate(e -> {
            return e.ownerId.equals(playerId) && e.color == color &&
                e.scale == scale;
        });
        
        if (entry == null) {
            int newId = allocateId(record);
            ScaleBoxRecord.Entry newEntry = new ScaleBoxRecord.Entry();
            newEntry.id = newId;
            newEntry.color = color;
            newEntry.ownerId = playerId;
            newEntry.ownerNameCache = playerName;
            newEntry.scale = scale;
            newEntry.generation = 0;
            newEntry.innerBoxPos = allocateInnerBoxPos(newId);
            newEntry.currentEntranceSize = new BlockPos(1, 1, 1);
            record.addEntry(newEntry);
            record.setDirty(true);
            
            initializeInnerBoxBlocks(null, newEntry);
            
            entry = newEntry;
        }
        return entry;
    }
    
    private static int allocateId(ScaleBoxRecord record) {
        return record.allocateId();
    }
    
    private static BlockPos allocateInnerBoxPos(int boxId) {
        int xIndex = boxId % 265;
        int zIndex = MathHelper.floorDiv(boxId, 256);
        
        return new BlockPos(xIndex * 16 * 32, 64, zIndex * 16 * 32);
    }
    
    public static BlockPos getNearestPosInScaleBoxToTeleportTo(BlockPos pos) {
        double gridLen = 16.0 * 32;
        return new BlockPos(
            Math.round(pos.getX() / gridLen) * gridLen + 5,
            64 + 5,
            Math.round(pos.getZ() / gridLen) * gridLen + 5
        );
    }
    
    public static void initializeInnerBoxBlocks(
        @Nullable BlockPos oldEntranceSize,
        ScaleBoxRecord.Entry entry
    ) {
        IntBox innerAreaBox = entry.getInnerAreaBox();
        
        ServerWorld voidWorld = VoidDimension.getVoidWorld();
        
        ChunkLoader chunkLoader = new ChunkLoader(
            new DimensionalChunkPos(
                voidWorld.getRegistryKey(),
                new ChunkPos(innerAreaBox.getCenter())
            ),
            Math.max(innerAreaBox.getSize().getX(), innerAreaBox.getSize().getZ()) / 16 + 2
        );
        
        Block glassBlock = getGlassBlock(entry.color);
        BlockState frameBlock = glassBlock.getDefaultState();
        
        // set block after fulling loading the chunk
        // to avoid lighting problems
        chunkLoader.loadChunksAndDo(() -> {
            IntBox newEntranceOffsets = IntBox.fromBasePointAndSize(BlockPos.ORIGIN, entry.currentEntranceSize);
            IntBox oldEntranceOffsets = oldEntranceSize != null ?
                IntBox.fromBasePointAndSize(BlockPos.ORIGIN, oldEntranceSize) : null;
            
            
            newEntranceOffsets.stream().forEach(offset -> {
                if (oldEntranceOffsets != null) {
                    if (oldEntranceOffsets.contains(offset)) {
                        // if expanded, don't clear the existing regions
                        return;
                    }
                }
                
                IntBox innerUnitBox = entry.getInnerUnitBox(offset);
                
                // clear the barrier blocks if the scale box expanded
                innerUnitBox.fastStream().forEach(blockPos -> {
                    voidWorld.setBlockState(blockPos, Blocks.AIR.getDefaultState());
                });
                
                // setup the frame blocks
                for (IntBox edge : innerUnitBox.get12Edges()) {
                    edge.fastStream().forEach(blockPos -> {
                        voidWorld.setBlockState(blockPos, frameBlock);
                    });
                }
            });
            
            if (oldEntranceOffsets != null) {
                // clear the shrunk area's blocks
                oldEntranceOffsets.stream().forEach(offset -> {
                    if (newEntranceOffsets.contains(offset)) {
                        return;
                    }
                    
                    IntBox innerUnitBox = entry.getInnerUnitBox(offset);
                    
                    innerUnitBox.fastStream().forEach(blockPos -> {
                        voidWorld.setBlockState(blockPos, Blocks.AIR.getDefaultState());
                    });
                });
            }
            
            IntBox expanded = innerAreaBox.getAdjusted(-1, -1, -1, 1, 1, 1);
            
            for (Direction direction : Direction.values()) {
                expanded.getSurfaceLayer(direction).fastStream().forEach(blockPos -> {
                    voidWorld.setBlockState(blockPos, BoxBarrierBlock.instance.getDefaultState());
                });
            }
            
        });
        
    }
    
    private static Block getGlassBlock(DyeColor color) {
        return Registries.BLOCK.get(new Identifier("minecraft:" + color.getName() + "_stained_glass"));
    }
    
    public static boolean isValidScale(int size) {
        return Arrays.stream(supportedScales).anyMatch(s -> s == size);
    }
    
}
