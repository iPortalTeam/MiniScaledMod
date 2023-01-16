package qouteall.mini_scaled;

import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.imm_ptl.core.chunk_loading.DimensionalChunkPos;
import qouteall.imm_ptl.core.portal.PortalExtension;
import qouteall.imm_ptl.core.portal.PortalManipulation;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ScaleBoxGeneration {
    public static final int[] supportedScales = {4, 8, 16, 32};
    
    public static void putScaleBoxIntoWorld(
        ScaleBoxRecord.Entry entry,
        ServerLevel world, BlockPos outerBoxBasePos,
        AARotation rotation
    ) {
        entry.currentEntranceDim = world.dimension();
        entry.currentEntrancePos = outerBoxBasePos;
        entry.entranceRotation = rotation;
        entry.generation++;
        
        ScaleBoxRecord.get().setDirty(true);
        
        ServerLevel voidWorld = VoidDimension.getVoidWorld();
        createScaleBoxPortals(voidWorld, world, entry);
        
        entry.getOuterAreaBox().stream().forEach(outerPos -> {
            world.setBlockAndUpdate(outerPos, ScaleBoxPlaceholderBlock.instance.defaultBlockState());
            
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
        
        for (Direction outerDirection : Direction.values()) {
            MiniScaledPortal portal = MiniScaledPortal.entityType.create(outerWorld);
            Validate.notNull(portal);
            
            portal.setDestinationDimension(innerWorld.dimension());
            
            Direction innerDirection = toInnerRotation.transformDirection(outerDirection);
            
            portal.setOriginPos(Helper.getBoxSurface(outerAreaBox, outerDirection).getCenter());
            portal.setDestination(Helper.getBoxSurface(innerAreaBox, innerDirection).getCenter());
            
            Tuple<Direction, Direction> perpendicularDirections = Helper.getPerpendicularDirections(outerDirection);
            Direction pd1 = perpendicularDirections.getA();
            Direction pd2 = perpendicularDirections.getB();
            
            portal.setOrientation(Vec3.atLowerCornerOf(pd1.getNormal()), Vec3.atLowerCornerOf(pd2.getNormal()));
            portal.setWidth(Helper.getCoordinate(outerAreaBoxSize, pd1.getAxis()));
            portal.setHeight(Helper.getCoordinate(outerAreaBoxSize, pd2.getAxis()));
            
            portal.setRotation(quaternion);
            
            portal.scaling = scale;
            portal.teleportChangesScale = entry.teleportChangesScale;
            portal.setTeleportChangesGravity(entry.teleportChangesGravity);
            portal.fuseView = true;
            portal.renderingMergable = true;
            portal.hasCrossPortalCollision = true;
            portal.portalTag = "mini_scaled:scaled_box";
            PortalExtension.get(portal).adjustPositionAfterTeleport = true;
            portal.setInteractable(false);
            portal.boxId = boxId;
            portal.generation = generation;
            
            McHelper.spawnServerEntity(portal);
            
            MiniScaledPortal reversePortal =
                PortalManipulation.createReversePortal(portal, MiniScaledPortal.entityType);
            
            reversePortal.fuseView = false;
            reversePortal.renderingMergable = true;
            reversePortal.hasCrossPortalCollision = true;
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
        int zIndex = Mth.intFloorDiv(boxId, 256);
        
        return new BlockPos(xIndex * 16 * 32, 64, zIndex * 16 * 32);
    }
    
    public static BlockPos getNearestPosInScaleBoxToTeleportTo(BlockPos pos) {
        double gridLen = 16.0 * 32;
        return new BlockPos(
            Math.round(pos.getX() / gridLen) * gridLen + 2,
            64 + 2,
            Math.round(pos.getZ() / gridLen) * gridLen + 2
        );
    }
    
    public static void initializeInnerBoxBlocks(
        @Nullable BlockPos oldEntranceSize,
        ScaleBoxRecord.Entry entry
    ) {
        IntBox innerAreaBox = entry.getInnerAreaBox();
        
        ServerLevel voidWorld = VoidDimension.getVoidWorld();
        
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
                    voidWorld.setBlockAndUpdate(blockPos, Blocks.AIR.defaultBlockState());
                });
                
                // setup the frame blocks
                for (IntBox edge : innerUnitBox.get12Edges()) {
                    edge.fastStream().forEach(blockPos -> {
                        voidWorld.setBlockAndUpdate(blockPos, frameBlock);
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
                        voidWorld.setBlockAndUpdate(blockPos, Blocks.AIR.defaultBlockState());
                    });
                });
            }
            
            IntBox expanded = innerAreaBox.getAdjusted(-1, -1, -1, 1, 1, 1);
            
            for (Direction direction : Direction.values()) {
                expanded.getSurfaceLayer(direction).fastStream().forEach(blockPos -> {
                    voidWorld.setBlockAndUpdate(blockPos, BoxBarrierBlock.instance.defaultBlockState());
                });
            }
            
        });
        
    }
    
    private static Block getGlassBlock(DyeColor color) {
        return BuiltInRegistries.BLOCK.get(new ResourceLocation("minecraft:" + color.getName() + "_stained_glass"));
    }
    
    public static boolean isValidScale(int size) {
        return Arrays.stream(supportedScales).anyMatch(s -> s == size);
    }
    
}
