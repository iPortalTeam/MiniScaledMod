package qouteall.mini_scaled;

import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.commands.FillCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Tuple;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
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
import qouteall.imm_ptl.core.compat.GravityChangerInterface;
import qouteall.q_misc_util.my_util.AARotation;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.IntBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class RegionTransfer {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static void transferRegion(
        ServerLevel srcWorld,
        BlockPos srcOrigin,
        ServerLevel dstWorld,
        BlockPos dstOrigin,
        BlockPos regionSize,
        AARotation rotation,
        boolean syncSrcSideBlockUpdateToClientImmediately,
        boolean syncDstSideBlockUpdateToClientImmediately,
        boolean transformGravity,
        Predicate<Entity> canTransferEntity
    ) {
        IntBox srcBox = IntBox.fromBasePointAndSize(srcOrigin, regionSize);
        BlockPos diagOffset = regionSize.offset(-1, -1, -1);
        IntBox dstBox = IntBox.fromPosAndOffset(dstOrigin, rotation.transform(diagOffset));
        
        Validate.isTrue(
            dstBox.h.getY() < dstWorld.getMaxBuildHeight(),
            "the destination region should not exceed world height limit"
        );
        Validate.isTrue(
            dstBox.l.getY() >= dstWorld.getMinBuildHeight(),
            "the destination region should not be below world height limit"
        );
        
        if (srcWorld == dstWorld) {
            Validate.isTrue(
                IntBox.getIntersect(srcBox, dstBox) == null,
                "the source region and destination region should not intersect"
            );
        }
        
        @Nullable Rotation vanillaRotation = rotation.toVanillaRotation();
        
        transferBlockAndBlockEntities(
            srcWorld, srcOrigin, dstWorld, dstOrigin, regionSize, rotation, vanillaRotation
        );
        
        transferEntities(
            srcWorld, srcOrigin, dstWorld, dstOrigin, regionSize, rotation, transformGravity,
            canTransferEntity
        );
        
        if (syncSrcSideBlockUpdateToClientImmediately) {
            PortalAPI.syncBlockUpdateToClientImmediately(srcWorld, srcBox);
        }
        
        if (syncDstSideBlockUpdateToClientImmediately) {
            PortalAPI.syncBlockUpdateToClientImmediately(dstWorld, dstBox);
        }
    }
    
    /**
     * Simply using setBlockAndUpdate() will cause wrong block update in the process
     * (for example, the torch block drops when its wall block is missing. Placing torch before wall make it break. Clearing wall before torch also make it break.)
     * In {@link Level#setBlock(BlockPos, BlockState, int, int)}, the meaning of flag on server side:
     * 1 : Cause {@link NeighborUpdater#updateNeighborsAtExceptFromFacing(BlockPos, Block, Direction)}
     * Including redstone updates.
     * 2 : Cause mob AI pathfinding update and notify ChunkHolder block change for network sync.
     * This is always required if we don't want client block de-sync.
     * 4 : Make client to rebuild chunk.
     * 8 : Make client to rebuild chunk as player-changed {@link LevelRenderer#blockChanged}
     * 16: disable shape update (this is the update that cause torch to drop without wall)
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
     * <p>
     * Note: changing the flag cannot fully disable block update.
     * In {@link LevelChunk#setBlockState}, it calls {@link BlockState#onRemove}, regardless of the flag.
     * In vanilla, using /fill command to break extended piston in some direction will cause it to drop.
     */
    private static void transferBlockAndBlockEntities(
        ServerLevel srcWorld, BlockPos srcOrigin,
        ServerLevel dstWorld, BlockPos dstOrigin,
        BlockPos regionSize,
        AARotation rotation,
        @Nullable Rotation vanillaRotation
    ) {
        BlockPos diagOffset = regionSize.offset(-1, -1, -1);
        IntBox dstBox = IntBox.fromPosAndOffset(dstOrigin, rotation.transform(diagOffset));
        
        List<BlockPos> srcDelayClearPos = new ArrayList<>();
        
        for (int dx = 0; dx < regionSize.getX(); dx++) {
            for (int dy = 0; dy < regionSize.getY(); dy++) {
                for (int dz = 0; dz < regionSize.getZ(); dz++) {
                    BlockPos srcPos = srcOrigin.offset(dx, dy, dz);
                    
                    BlockPos transformedDelta = rotation.transform(new BlockPos(dx, dy, dz));
                    BlockPos dstPos = dstOrigin.offset(transformedDelta);
                    
                    BlockState srcState = srcWorld.getBlockState(srcPos);
                    BlockState rotatedSrcState = vanillaRotation == null ?
                        srcState : srcState.rotate(vanillaRotation);
                    
                    @Nullable CompoundTag blockEntityTag = null;
                    if (srcState.hasBlockEntity()) {
                        BlockEntity blockEntity = srcWorld.getBlockEntity(srcPos);
                        if (blockEntity != null) {
                            blockEntityTag = blockEntity.saveWithoutMetadata();
                            Clearable.tryClear(blockEntity);
                        }
                    }
                    
                    // clear the block without triggering shape update
                    if (shouldDelayClear(srcState)) {
                        srcDelayClearPos.add(srcPos);
                    }
                    else {
                        srcWorld.setBlock(
                            srcPos, Blocks.AIR.defaultBlockState(), 2 | 16
                        );
                    }
                    
                    // set the block without triggering shape update
                    dstWorld.setBlock(
                        dstPos, rotatedSrcState, 2 | 16
                    );
                    
                    if (blockEntityTag != null) {
                        BlockEntity newBlockEntity = dstWorld.getBlockEntity(dstPos);
                        if (newBlockEntity != null) {
                            newBlockEntity.load(blockEntityTag);
                            newBlockEntity.setChanged();
                        }
                        else {
                            LOGGER.error(
                                "cannot find block entity after transfer. from {} {} to {} {} block {} tag {}",
                                srcWorld, srcPos, dstWorld, dstPos, srcState, blockEntityTag
                            );
                        }
                    }
                }
            }
        }
        
        for (BlockPos pos : srcDelayClearPos) {
            srcWorld.setBlock(
                pos, Blocks.AIR.defaultBlockState(), 2 | 16
            );
        }
        
        // also update the outer layer of region
        for (int dx = -1; dx <= regionSize.getX(); dx++) {
            for (int dy = -1; dy <= regionSize.getY(); dy++) {
                for (int dz = -1; dz <= regionSize.getZ(); dz++) {
                    BlockPos fromPos = srcOrigin.offset(dx, dy, dz);
                    updateBlockStatus(srcWorld, fromPos);
                    
                    BlockPos transformedDelta = rotation.transform(new BlockPos(dx, dy, dz));
                    BlockPos toPos = dstOrigin.offset(transformedDelta);
                    
                    updateBlockStatus(dstWorld, toPos);
                }
            }
        }
        
        cleanupInvalidStructures(dstWorld, dstBox);
    }
    
    // Piston head block is special. If we break piston head before breaking base,
    // piston base will break and drop item.
    // Vanilla code does not allow suppressing this by the flag.
    private static boolean shouldDelayClear(BlockState blockState) {
        return blockState.getBlock() instanceof PistonHeadBlock;
    }
    
    public static void updateBlockStatus(ServerLevel world, BlockPos pos) {
        BlockState blockState = world.getBlockState(pos);
        BlockState blockStateUpdated = Block.updateFromNeighbourShapes(
            blockState, world, pos
        );
        if (blockState != blockStateUpdated) {
            if (blockStateUpdated.isAir()) {
                // need to destroy block instead of setting block here
                // setting block will automatically remove the flag 32
                // this will cause item loss
                world.destroyBlock(pos, true);
            }
            else {
                world.setBlockAndUpdate(pos, blockStateUpdated);
            }
        }
        
        world.blockUpdated(pos, blockState.getBlock());
    }
    
    private static void transferEntities(
        ServerLevel fromWorld, BlockPos fromOrigin,
        ServerLevel toWorld, BlockPos toOrigin,
        BlockPos regionSize, AARotation rotation,
        boolean transformGravity,
        Predicate<Entity> entityPredicate
    ) {
        AABB box = IntBox.fromBasePointAndSize(fromOrigin, regionSize).toRealNumberBox();
        List<Entity> entities = McHelper.findEntitiesByBox(
            Entity.class,
            fromWorld,
            box,
            16,
            entityPredicate
        );
        
        BlockPos transformedRegionSize = rotation.transform(regionSize);
        
        Vec3 fromOriginPos = Vec3.atLowerCornerOf(fromOrigin);
        Vec3 toOriginPos = new Vec3(
            transformedRegionSize.getX() > 0 ? toOrigin.getX() : toOrigin.getX() + 1,
            transformedRegionSize.getY() > 0 ? toOrigin.getY() : toOrigin.getY() + 1,
            transformedRegionSize.getZ() > 0 ? toOrigin.getZ() : toOrigin.getZ() + 1
        );
        for (Entity entity : entities) {
            Vec3 position = entity.position();
            Vec3 newPosition = rotation.quaternion.rotate(position.subtract(fromOriginPos)).add(toOriginPos);
            float oldXRot = entity.getXRot();
            float oldYRot = entity.getYRot();
            Direction oldGrav = GravityChangerInterface.invoker.getBaseGravityDirection(entity);
            Direction newGrav = transformGravity ? rotation.transformDirection(oldGrav) : oldGrav;
            
            Entity newEntity = PortalAPI.teleportEntity(
                entity, toWorld, newPosition
            );
            
            Tuple<Double, Double> newPitchYaw;
            if (oldGrav != newGrav) {
                GravityChangerInterface.invoker.setBaseGravityDirectionServer(entity, newGrav);
            }
            
            // rawCameraRotation comes from xRot and yRot
            // rotationForWorldRendering = rawCameraRotation * gravity
            // rotationForEntity = rotationForWorldRendering^-1
            //                   = gravity^-1 * rawCameraRotation^-1
            // rotationForEntity * rawCameraRotation = gravity^-1
            // rawCameraRotation = rotationForEntity^-1 * gravity^-1
            DQuaternion oldGravityRot = DQuaternion.fromNullable(
                GravityChangerInterface.invoker.getExtraCameraRotation(oldGrav)
            );
            DQuaternion oldRawCamRot = DQuaternion.getCameraRotation(oldXRot, oldYRot);
            DQuaternion oldEntityRot = oldGravityRot.getConjugated()
                .hamiltonProduct(oldRawCamRot.getConjugated());
            
            DQuaternion newEntityRot = rotation.quaternion.hamiltonProduct(oldEntityRot);
            DQuaternion newGravityRot = DQuaternion.fromNullable(
                GravityChangerInterface.invoker.getExtraCameraRotation(newGrav)
            );
            DQuaternion newRawCamRot = newEntityRot.getConjugated()
                .hamiltonProduct(newGravityRot.getConjugated());
            
            newPitchYaw = DQuaternion.getPitchYawFromRotation(newRawCamRot);
            
            double newXRot = newPitchYaw.getA();
            double newYRot = newPitchYaw.getB();
            newEntity.setXRot((float) newXRot);
            newEntity.setYRot((float) newYRot);
            
            LOGGER.info("Transferred entity {}", entity);
        }
    }
    
    // break headless piston, because headless piston can be used to break bedrock
    public static void cleanupInvalidStructures(
        ServerLevel world, IntBox box
    ) {
        box.fastStream().filter(pos -> {
            BlockState blockState = world.getBlockState(pos);
            if (blockState.getBlock() instanceof PistonBaseBlock pistonBaseBlock) {
                boolean extended = blockState.getValue(PistonBaseBlock.EXTENDED);
                if (!extended) {
                    return false;
                }
                
                Direction facing = blockState.getValue(BlockStateProperties.FACING);
                BlockPos headPos = pos.relative(facing);
                BlockState headState = world.getBlockState(headPos);
                
                if (!(headState.getBlock() instanceof PistonHeadBlock)) {
                    return true;
                }
                
                if (headState.getValue(PistonHeadBlock.FACING) != facing) {
                    return true;
                }
                
                return false;
            }
            
            return false;
        }).forEach(pos -> {
            LOGGER.info("Breaking invalid structure {} {}", world.dimension().location(), pos);
            world.destroyBlock(pos, true);
        });
    }
}
