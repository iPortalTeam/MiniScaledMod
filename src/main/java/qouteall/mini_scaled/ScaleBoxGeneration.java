package qouteall.mini_scaled;

import com.qouteall.immersive_portals.Helper;
import com.qouteall.immersive_portals.McHelper;
import com.qouteall.immersive_portals.my_util.IntBox;
import com.qouteall.immersive_portals.portal.PortalExtension;
import com.qouteall.immersive_portals.portal.PortalManipulation;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.Validate;

public class ScaleBoxGeneration {
    public static void putScaleBox(
        ServerWorld world,
        ServerPlayerEntity player,
        int size,
        BlockPos outerBoxPos,
        DyeColor color
    ) {
        Validate.isTrue(world.getRegistryKey() != VoidDimension.dimensionId);
        Validate.isTrue(size == 16);//temporary
        
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
            portal.hasCrossPortalCollision = false;
            portal.portalTag = "imm_ptl:scale_box";
            PortalExtension.get(portal).adjustPositionAfterTeleport = false;
            portal.boxId = boxId;
            portal.generation = generation;
            
            McHelper.spawnServerEntity(portal);
            
            MiniScaledPortal reversePortal =
                PortalManipulation.createReversePortal(portal, MiniScaledPortal.entityType);
            
            reversePortal.fuseView = false;
            reversePortal.renderingMergable = true;
            reversePortal.hasCrossPortalCollision = true;
            PortalExtension.get(reversePortal).adjustPositionAfterTeleport = true;
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
    
    private static void initializeInnerBoxBlocks(ScaleBoxRecord.Entry entry) {
        ServerWorld voidWorld = VoidDimension.getVoidWorld();
        
        IntBox box = entry.getAreaBox();
        IntBox expanded = box.getAdjusted(-1, -1, -1, 1, 1, 1);
        
        for (Direction direction : Direction.values()) {
            expanded.getSurfaceLayer(direction).fastStream().forEach(blockPos -> {
                voidWorld.setBlockState(blockPos, Blocks.BARRIER.getDefaultState());
            });
        }
    }
    
}
