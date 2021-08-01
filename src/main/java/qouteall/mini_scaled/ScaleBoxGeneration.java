package qouteall.mini_scaled;

import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.imm_ptl.core.chunk_loading.DimensionalChunkPos;
import qouteall.imm_ptl.core.chunk_loading.NewChunkTrackingGraph;
import qouteall.imm_ptl.core.portal.PortalExtension;
import qouteall.imm_ptl.core.portal.PortalManipulation;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import org.apache.commons.lang3.Validate;
import qouteall.mini_scaled.block.BoxBarrierBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlockEntity;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.IntBox;
import qouteall.q_misc_util.my_util.MyTaskList;

import java.util.Arrays;

public class ScaleBoxGeneration {
    static final int[] supportedSizes = {8, 16, 32};
    
    public static void putScaleBox(
        ServerWorld world,
        ServerPlayerEntity player,
        int size,
        BlockPos outerBoxPos,
        DyeColor color
    ) {
        ScaleBoxRecord record = ScaleBoxRecord.get();
        
        ScaleBoxRecord.Entry entry = getOrCreateEntry(player, size, color, record);
        
        entry.currentEntranceDim = world.getRegistryKey();
        entry.currentEntrancePos = outerBoxPos;
        entry.generation++;
        record.setDirty(true);
        
        
        ServerWorld voidWorld = VoidDimension.getVoidWorld();
        createScaleBoxPortals(
            voidWorld,
            entry.getAreaBox().toRealNumberBox(),
            world,
            Vec3d.ofBottomCenter(outerBoxPos),
            entry.size,
            entry.id,
            entry.generation
        );
        
        world.setBlockState(outerBoxPos, ScaleBoxPlaceholderBlock.instance.getDefaultState());
        BlockEntity blockEntity = world.getBlockEntity(outerBoxPos);
        if (blockEntity == null) {
            System.err.println("cannot find block entity for scale box");
        }
        else {
            ScaleBoxPlaceholderBlockEntity be = (ScaleBoxPlaceholderBlockEntity) blockEntity;
            be.boxId = entry.id;
        }
    }
    
    private static void removeScaleBox(int boxId) {
        ScaleBoxRecord.Entry entry = ScaleBoxRecord.getEntryById(boxId);
        if (entry == null) {
            System.err.println("removing nonexistent box " + boxId);
            return;
        }
    }
    
    private static void createScaleBoxPortals(
        ServerWorld areaWorld, Box area,
        ServerWorld boxWorld, Vec3d boxBottomCenter,
        double scale,
        int boxId, int generation
    ) {
        Vec3d viewBoxSize = Helper.getBoxSize(area).multiply(1.0 / scale);
        Box viewBox = Helper.getBoxByBottomPosAndSize(boxBottomCenter, viewBoxSize);
        for (Direction direction : Direction.values()) {
            MiniScaledPortal portal = PortalManipulation.createOrthodoxPortal(
                MiniScaledPortal.entityType,
                boxWorld, areaWorld,
                direction, Helper.getBoxSurface(viewBox, direction),
                Helper.getBoxSurface(area, direction).getCenter()
            );
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
            if (direction != Direction.DOWN && direction != Direction.UP) {
                PortalExtension.get(reversePortal).adjustPositionAfterTeleport = true;
            }
            reversePortal.setInteractable(false);
            reversePortal.boxId = boxId;
            reversePortal.generation = generation;
            
            McHelper.spawnServerEntity(reversePortal);
        }
    }
    
    
    private static ScaleBoxRecord.Entry getOrCreateEntry(
        ServerPlayerEntity player, int size, DyeColor color, ScaleBoxRecord record
    ) {
        ScaleBoxRecord.Entry entry = record.entries.stream().filter(e -> {
            return e.ownerId.equals(player.getUuid()) && e.color == color &&
                e.size == size;
        }).findFirst().orElse(null);
        
        if (entry == null) {
            int newId = allocateId(record);
            ScaleBoxRecord.Entry newEntry = new ScaleBoxRecord.Entry();
            newEntry.id = newId;
            newEntry.color = color;
            newEntry.ownerId = player.getUuid();
            newEntry.ownerNameCache = player.getName().asString();
            newEntry.size = size;
            newEntry.generation = 0;
            newEntry.innerBoxPos = allocateInnerBoxPos(newId);
            record.entries.add(newEntry);
            record.setDirty(true);
            
            initializeInnerBoxBlocks(newEntry);
            
            entry = newEntry;
        }
        return entry;
    }
    
    private static int allocateId(ScaleBoxRecord record) {
        return record.entries.stream().mapToInt(e -> e.id).max().orElse(0) + 1;
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
    
    private static BlockPos selectCoordinateFromBox(IntBox box, boolean high) {
        return high ? box.h : box.l;
    }
    
    private static BlockPos selectCoordinateFromBox(IntBox box, boolean xUp, boolean yUp, boolean zUp) {
        return new BlockPos(
            selectCoordinateFromBox(box, xUp).getX(),
            selectCoordinateFromBox(box, yUp).getY(),
            selectCoordinateFromBox(box, zUp).getZ()
        );
    }
    
    // TODO change to use IP's version
    private static IntBox[] get12Edges(IntBox box) {
        return new IntBox[]{
            new IntBox(
                selectCoordinateFromBox(box, false, false, false),
                selectCoordinateFromBox(box, false, false, true)
            ),
            new IntBox(
                selectCoordinateFromBox(box, false, true, false),
                selectCoordinateFromBox(box, false, true, true)
            ),
            new IntBox(
                selectCoordinateFromBox(box, true, false, false),
                selectCoordinateFromBox(box, true, false, true)
            ),
            new IntBox(
                selectCoordinateFromBox(box, true, true, false),
                selectCoordinateFromBox(box, true, true, true)
            ),
            
            new IntBox(
                selectCoordinateFromBox(box, false, false, false),
                selectCoordinateFromBox(box, false, true, false)
            ),
            new IntBox(
                selectCoordinateFromBox(box, false, false, true),
                selectCoordinateFromBox(box, false, true, true)
            ),
            new IntBox(
                selectCoordinateFromBox(box, true, false, false),
                selectCoordinateFromBox(box, true, true, false)
            ),
            new IntBox(
                selectCoordinateFromBox(box, true, false, true),
                selectCoordinateFromBox(box, true, true, true)
            ),
            
            new IntBox(
                selectCoordinateFromBox(box, false, false, false),
                selectCoordinateFromBox(box, true, false, false)
            ),
            new IntBox(
                selectCoordinateFromBox(box, false, false, true),
                selectCoordinateFromBox(box, true, false, true)
            ),
            new IntBox(
                selectCoordinateFromBox(box, false, true, false),
                selectCoordinateFromBox(box, true, true, false)
            ),
            new IntBox(
                selectCoordinateFromBox(box, false, true, true),
                selectCoordinateFromBox(box, true, true, true)
            )
        };
    }
    
    private static void initializeInnerBoxBlocks(ScaleBoxRecord.Entry entry) {
        ServerWorld voidWorld = VoidDimension.getVoidWorld();
        
        IntBox box = entry.getAreaBox();
        
        ChunkLoader chunkLoader = new ChunkLoader(
            new DimensionalChunkPos(
                voidWorld.getRegistryKey(),
                new ChunkPos(box.l)
            ),
            0
        );
        
        // set block after fulling loading the chunk
        // to avoid lighting problems
        chunkLoader.loadChunksAndDo(() -> {
            IntBox expanded = box.getAdjusted(-1, -1, -1, 1, 1, 1);
            
            for (Direction direction : Direction.values()) {
                expanded.getSurfaceLayer(direction).fastStream().forEach(blockPos -> {
                    voidWorld.setBlockState(blockPos, BoxBarrierBlock.instance.getDefaultState());
                });
            }
            
            Block woolBlock = Registry.BLOCK.get(new Identifier("minecraft:" + entry.color.getName() + "_wool"));
            BlockState frameBlock = woolBlock.getDefaultState();
            
            for (IntBox edge : get12Edges(box)) {
                edge.fastStream().forEach(blockPos -> {
                    voidWorld.setBlockState(blockPos, frameBlock);
                });
            }
        });
        
    }
    
    public static boolean isValidSize(int size) {
        return Arrays.stream(supportedSizes).anyMatch(s -> s == size);
    }
    
}
