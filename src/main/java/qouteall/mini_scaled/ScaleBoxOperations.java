package qouteall.mini_scaled;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.CloneCommands;
import net.minecraft.server.commands.FillCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Tuple;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.redstone.NeighborUpdater;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlockEntity;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.AARotation;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.IntBox;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class ScaleBoxOperations {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // the result does not include 1
    public static IntArrayList getCommonFactors(
        int wx, int wy, int wz
    ) {
        IntArrayList result = new IntArrayList();
        
        for (int i = 2; i <= wx; i++) {
            if (wx % i == 0 && wy % i == 0 && wz % i == 0) {
                result.add(i);
            }
        }
        
        return result;
    }
    
    public static int getCost(
        BlockPos boxSize, int scale
    ) {
        int product = boxSize.getX() * boxSize.getY() * boxSize.getZ();
        
        double r = Math.pow((double) product, 1.0 / 3) / 8;
        
        double r2 = scale / 8.0;
        
        return (int) Math.ceil(r + r2);
    }
    
    // Note should check for player action validity before calling this
    public static void wrap(
        ServerLevel world,
        IntBox box,
        ServerPlayer player,
        DyeColor color,
        int scale,
        BlockPos clickingPos
    ) {
        MinecraftServer server = world.getServer();
        ServerLevel voidDim = VoidDimension.getVoidServerWorld(server);
        
        ScaleBoxRecord scaleBoxRecord = ScaleBoxRecord.get(server);
        
        BlockPos boxSize = box.getSize();
        BlockPos entranceSize = Helper.divide(boxSize, scale);
        IntBox entranceBox = box.confineInnerBox(IntBox.fromBasePointAndSize(clickingPos, entranceSize));
        
        int newBoxId = scaleBoxRecord.allocateId();
        
        ScaleBoxRecord.Entry newEntry = new ScaleBoxRecord.Entry();
        newEntry.id = newBoxId;
        newEntry.color = color;
        newEntry.ownerId = player.getUUID();
        newEntry.ownerNameCache = player.getName().getString();
        newEntry.scale = scale;
        newEntry.generation = 0;
        newEntry.innerBoxPos = ScaleBoxGeneration.allocateInnerBoxPos(newBoxId);
        newEntry.currentEntranceSize = entranceSize;
        
        scaleBoxRecord.addEntry(newEntry);
        scaleBoxRecord.setDirty();
        
        transferRegion(
            world,
            box.l,
            voidDim,
            newEntry.innerBoxPos,
            boxSize,
            AARotation.IDENTITY
        );
        
        putScaleBoxEntranceIntoWorld(
            newEntry,
            world,
            entranceBox.l,
            AARotation.IDENTITY,
            player
        );
        
        // TODO setup barrier block
    }
    
    public static void unwrap(
        ServerLevel world,
        ServerPlayer player,
        ScaleBoxRecord.Entry entry,
        IntBox expandedBox,
        AARotation rotationFromInnerToOuter
    ) {
        MinecraftServer server = world.getServer();
        ServerLevel voidDim = VoidDimension.getVoidServerWorld(server);
        
        ScaleBoxRecord scaleBoxRecord = ScaleBoxRecord.get(server);
        
        IntBox innerAreaBox = entry.getInnerAreaBox();
        
        scaleBoxRecord.removeEntry(entry.id);
        scaleBoxRecord.setDirty();
        
        transferRegion(
            voidDim,
            innerAreaBox.l,
            world,
            expandedBox.l,
            innerAreaBox.getSize(),
            rotationFromInnerToOuter
        );
    }
    
    public static void transferRegion(
        ServerLevel fromWorld,
        BlockPos fromOrigin,
        ServerLevel toWorld,
        BlockPos toOrigin,
        BlockPos regionSize,
        AARotation rotation
    ) {
        if (fromWorld == toWorld) {
            IntBox fromBox = IntBox.fromBasePointAndSize(fromOrigin, regionSize);
            BlockPos diagOffset = regionSize.offset(-1, -1, -1);
            IntBox toBox = IntBox.fromPosAndOffset(toOrigin, rotation.transform(diagOffset));
            Validate.isTrue(
                IntBox.getIntersect(fromBox, toBox) == null,
                "the source region and destination region should not intersect"
            );
        }
        
        @Nullable Rotation vanillaRotation = rotation.toVanillaRotation();
        
        transferBlockAndBlockEntities(
            fromWorld, fromOrigin, toWorld, toOrigin, regionSize, rotation, vanillaRotation
        );
        
        transferEntities(fromWorld, fromOrigin, toWorld, toOrigin, regionSize, rotation);
    }
    
    /**
     * Simply using setBlockAndUpdate() will cause wrong block update in the process
     * (for example, the torch block drops when its wall block is missing. Placing torch before wall make it break. Clearing wall before torch also make it break.)
     * In {@link Level#setBlock(BlockPos, BlockState, int, int)}, the meaning of flag on server side:
     * 1 : Cause {@link NeighborUpdater#updateNeighborsAtExceptFromFacing(BlockPos, Block, Direction)}
     * Including redstone updates.
     * 2 : Cause mob AI pathfinding update and notify ChunkHolder block change for network sync.
     * This is always required if we don't want client block de-sync.
     * 4 : not relevant on server side
     * 8 : ?
     * 16: cause shape update (this is the update that cause torch to drop without wall)
     * 32: drop item when block destroys {@link Block#updateOrDestroy(BlockState, BlockState, LevelAccessor, BlockPos, int, int)}
     * <p>
     * -2 erases 1,  -33 erases 32,  -34 erases 1 and 32
     * <p>
     * Ref:
     * {@link StructureTemplate#placeInWorld(ServerLevelAccessor, BlockPos, BlockPos, StructurePlaceSettings, RandomSource, int)}
     * uses flag 2 for structure block (use flag 16 + 4 to remove block entity with barrier before)
     * <p>
     * {@link FillCommand#fillBlocks(CommandSourceStack, BoundingBox, BlockInput, FillCommand.Mode, Predicate)}
     * uses flag 2
     */
    private static void transferBlockAndBlockEntities(
        ServerLevel fromWorld, BlockPos fromOrigin,
        ServerLevel toWorld, BlockPos toOrigin,
        BlockPos regionSize,
        AARotation rotation,
        @Nullable Rotation vanillaRotation
    ) {
        for (int dx = 0; dx < regionSize.getX(); dx++) {
            for (int dy = 0; dy < regionSize.getY(); dy++) {
                for (int dz = 0; dz < regionSize.getZ(); dz++) {
                    BlockPos fromPos = fromOrigin.offset(dx, dy, dz);
                    
                    BlockPos transformedDelta = rotation.transform(new BlockPos(dx, dy, dz));
                    BlockPos toPos = toOrigin.offset(transformedDelta);
                    
                    BlockState blockState = fromWorld.getBlockState(fromPos);
                    BlockState rotatedBlockState = vanillaRotation == null ?
                        blockState : blockState.rotate(vanillaRotation);
                    
                    @Nullable CompoundTag blockEntityTag = null;
                    if (blockState.hasBlockEntity()) {
                        BlockEntity blockEntity = fromWorld.getBlockEntity(fromPos);
                        if (blockEntity != null) {
                            blockEntityTag = blockEntity.saveWithoutMetadata();
                            Clearable.tryClear(blockEntity);
                        }
                    }
                    
                    // clear the block without triggering shape update
                    fromWorld.setBlock(
                        fromPos, Blocks.AIR.defaultBlockState(), 2
                    );
                    
                    // set the block without triggering shape update
                    toWorld.setBlock(
                        toPos, rotatedBlockState, 2
                    );
                    
                    if (blockEntityTag != null) {
                        BlockEntity newBlockEntity = toWorld.getBlockEntity(toPos);
                        if (newBlockEntity != null) {
                            newBlockEntity.load(blockEntityTag);
                            newBlockEntity.setChanged();
                        }
                        else {
                            LOGGER.warn(
                                "cannot find block entity after transfer. from {} {} to {} {} block {} tag {}",
                                fromWorld, fromPos, toWorld, toPos, blockState, blockEntityTag
                            );
                        }
                    }
                }
            }
        }
        
        for (int dx = 0; dx < regionSize.getX(); dx++) {
            for (int dy = 0; dy < regionSize.getY(); dy++) {
                for (int dz = 0; dz < regionSize.getZ(); dz++) {
                    BlockPos fromPos = fromOrigin.offset(dx, dy, dz);
                    BlockState fromBlockState = fromWorld.getBlockState(fromPos);
                    
                    fromWorld.blockUpdated(fromPos, fromBlockState.getBlock());
                    
                    BlockPos transformedDelta = rotation.transform(new BlockPos(dx, dy, dz));
                    BlockPos toPos = toOrigin.offset(transformedDelta);
                    
                    BlockState toBlockState = toWorld.getBlockState(toPos);
                    BlockState toBlockStateUpdated = Block.updateFromNeighbourShapes(
                        toBlockState, toWorld, toPos
                    );
                    if (toBlockState != toBlockStateUpdated) {
                        toWorld.setBlock(toPos, toBlockStateUpdated, 2 | 16);
                    }
                    
                    toWorld.blockUpdated(toPos, toBlockStateUpdated.getBlock());
                }
            }
        }
    }
    
    private static void transferEntities(
        ServerLevel fromWorld, BlockPos fromOrigin,
        ServerLevel toWorld, BlockPos toOrigin,
        BlockPos regionSize, AARotation rotation
    ) {
        AABB box = IntBox.fromBasePointAndSize(fromOrigin, regionSize).toRealNumberBox();
        List<Entity> entities = McHelper.findEntitiesByBox(
            Entity.class,
            fromWorld,
            box,
            16,
            ScaleBoxOperations::canMoveEntity
        );
        
        Vec3 fromOriginPos = Vec3.atLowerCornerOf(fromOrigin);
        Vec3 toOriginPos = Vec3.atLowerCornerOf(toOrigin);
        for (Entity entity : entities) {
            Vec3 position = entity.position();
            Vec3 newPosition = rotation.quaternion.rotate(position.subtract(fromOriginPos)).add(toOriginPos);
            
            DQuaternion oldCameraRot = DQuaternion.getCameraRotation(
                entity.getXRot(), entity.getYRot()
            ).getConjugated();
            DQuaternion newCameraRot = oldCameraRot.hamiltonProduct(rotation.quaternion).getConjugated();
            Tuple<Double, Double> newPitchYaw = DQuaternion.getPitchYawFromRotation(newCameraRot);
            double newXRot = newPitchYaw.getA();
            double newYRot = newPitchYaw.getB();
            
            // TODO transform entity gravity
            
            PortalAPI.teleportEntity(
                entity, toWorld, newPosition
            );
            entity.setXRot((float) newXRot);
            entity.setYRot((float) newYRot);
        }
    }
    
    // TODO pre-check
    public static boolean canMoveBlock(Level world, BlockPos pos, BlockState blockState) {
        float destroySpeed = blockState.getDestroySpeed(world, pos);
        // bedrock's is -1. obsidian's is 50
        return destroySpeed >= 0 && destroySpeed < 50;
    }
    
    public static boolean canMoveEntity(Entity entity) {
        return entity.canChangeDimensions();
    }
    
    public static void putScaleBoxEntranceIntoWorld(
        ScaleBoxRecord.Entry entry,
        ServerLevel world, BlockPos outerBoxBasePos,
        AARotation rotation,
        ServerPlayer player
    ) {
        if (entry.accessControl) {
            if (!Objects.equals(entry.ownerId, player.getUUID())) {
                ScaleBoxManipulation.showScaleBoxAccessDeniedMessage(player);
                return;
            }
        }
        
        entry.currentEntranceDim = world.dimension();
        entry.currentEntrancePos = outerBoxBasePos;
        entry.entranceRotation = rotation;
        entry.generation++;
        
        MinecraftServer server = world.getServer();
        ScaleBoxRecord.get(server).setDirty(true);
        
        ServerLevel voidWorld = VoidDimension.getVoidServerWorld(server);
        ScaleBoxGeneration.createScaleBoxPortals(voidWorld, world, entry);
        
        entry.getOuterAreaBox().stream().forEach(outerPos -> {
            world.setBlockAndUpdate(outerPos, ScaleBoxPlaceholderBlock.instance.defaultBlockState());
            
            BlockEntity blockEntity = world.getBlockEntity(outerPos);
            if (blockEntity == null) {
                LOGGER.info("cannot find block entity for scale box");
            }
            else {
                ScaleBoxPlaceholderBlockEntity be = (ScaleBoxPlaceholderBlockEntity) blockEntity;
                be.boxId = entry.id;
                be.isBasePos = outerPos.equals(entry.currentEntrancePos);
            }
        });
    }
}
