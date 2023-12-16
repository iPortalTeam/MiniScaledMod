package qouteall.mini_scaled;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.FillCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Tuple;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.redstone.NeighborUpdater;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.compat.GravityChangerInterface;
import qouteall.imm_ptl.core.network.PacketRedirection;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalExtension;
import qouteall.imm_ptl.core.portal.animation.DeltaUnilateralPortalState;
import qouteall.imm_ptl.core.portal.animation.NormalAnimation;
import qouteall.imm_ptl.core.portal.animation.TimingFunction;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.imm_ptl.core.portal.shape.BoxPortalShape;
import qouteall.imm_ptl.core.portal.shape.PortalShape;
import qouteall.mini_scaled.block.BoxBarrierBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlockEntity;
import qouteall.mini_scaled.gui.ScaleBoxInteractionManager;
import qouteall.q_misc_util.api.McRemoteProcedureCall;
import qouteall.q_misc_util.my_util.AARotation;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.IntBox;
import qouteall.q_misc_util.my_util.MyTaskList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
    
    public static List<ItemStack> getCost(
        BlockPos boxSize, int scale
    ) {
        int product = boxSize.getX() * boxSize.getY() * boxSize.getZ();
        
        double r = Math.pow((double) product, 1.0 / 3) / 8;
        
        double r2 = scale / 8.0;
        
        int itemNum = (int) Math.ceil(r + r2);
        
        return List.of(
            new ItemStack(
                ScaleBoxEntranceCreation.getCreationItem(),
                itemNum
            )
        );
    }
    
    public static void doWrap(
        ServerLevel world,
        ServerPlayer player,
        ScaleBoxRecord.Entry entry,
        IntBox wrappedBox,
        IntBox entranceBox
    ) {
        MinecraftServer server = world.getServer();
        
        ServerLevel voidDim = VoidDimension.getVoidServerWorld(server);
        
        BlockPos boxSize = wrappedBox.getSize();
        
        ScaleBoxRecord scaleBoxRecord = ScaleBoxRecord.get(server);
        
        entry.id = scaleBoxRecord.allocateId();
        entry.currentEntranceDim = world.dimension();
        entry.currentEntrancePos = entranceBox.l;
        entry.entranceRotation = null;
        entry.generation++;
        
        Validate.isTrue(entry.regionId != 0);
        Validate.isTrue(entry.innerBoxPos != null);
        Validate.isTrue(entry.currentEntranceSize != null);
        Validate.isTrue(entry.ownerId != null);
        
        scaleBoxRecord.addEntry(entry);
        scaleBoxRecord.setDirty(true);
        
        ServerLevel voidWorld = VoidDimension.getVoidServerWorld(server);
        
        forceBundleConditionally(() -> {
            transferRegion(
                world,
                wrappedBox.l,
                voidDim,
                entry.innerBoxPos,
                boxSize,
                AARotation.IDENTITY,
                MSGlobal.config.getConfig().serverBetterAnimation,
                MSGlobal.config.getConfig().serverBetterAnimation,
                entry.teleportChangesGravity
            );
            
            // should create portal after transferring region
            // otherwise these portals may be transferred
            ScaleBoxGeneration.createScaleBoxPortals(voidWorld, world, entry, wrappedBox);
            
            putScaleBoxEntranceBlocks(world, entry);
            
            // setup barrier blocks
            IntBox barrierBox = entry.getInnerAreaBox()
                .getAdjusted(-1, -1, -1, 1, 1, 1);
            for (Direction direction : Direction.values()) {
                barrierBox.getSurfaceLayer(direction).fastStream().forEach(blockPos -> {
                    voidDim.setBlockAndUpdate(blockPos, BoxBarrierBlock.INSTANCE.defaultBlockState());
                });
            }
            
            tellClientToForceMainThreadRebuildTemporarily(player);
            
            return null;
        });
    }
    
    private static void tellClientToForceMainThreadRebuildTemporarily(ServerPlayer player) {
        /**{@link ScaleBoxInteractionManager.RemoteCallables#forceClientToRebuildTemporarily()}*/
        McRemoteProcedureCall.tellClientToInvoke(
            player,
            "qouteall.mini_scaled.gui.ScaleBoxInteractionManager.RemoteCallables.forceClientToRebuildTemporarily"
        );
    }
    
    public static void preUnwrap(
        ServerPlayer player,
        ScaleBoxRecord.Entry entry,
        IntBox unwrappedBox,
        AARotation rotationFromInnerToOuter
    ) {
        MinecraftServer server = player.server;
        
        int durationTicks = MSGlobal.config.getConfig().unwrappingAnimationTicks;
        
        if (entry.currentEntranceDim == null || entry.currentEntrancePos == null) {
            player.sendSystemMessage(Component.literal("The scale box entrance does not exist now"));
            return;
        }
        
        ServerLevel entranceWorld = server.getLevel(entry.currentEntranceDim);
        if (entranceWorld == null) {
            LOGGER.error("cannot find world {} to unwrap scale box", entry.currentEntranceDim.location());
            return;
        }
        
        if (!ScaleBoxOperations.validateUnwrappingRegion(unwrappedBox, entry)) {
            player.sendSystemMessage(Component.literal("Invalid box argument"));
            return;
        }
        
        if (!ScaleBoxOperations.validateUnwrappingRegionBlocks(unwrappedBox, entry, entranceWorld)) {
            player.sendSystemMessage(Component.translatable("mini_scaled.unwrapping_area_not_empty"));
            return;
        }
        
        if (unwrappedBox.h.getY() >= entranceWorld.getMaxBuildHeight() ||
            unwrappedBox.l.getY() < entranceWorld.getMinBuildHeight()
        ) {
            player.sendSystemMessage(
                Component.translatable("mini_scaled.unwrapping_exceed_height_limit")
            );
            return;
        }
        
        // find the portal and start animation
        List<MiniScaledPortal> portals = entranceWorld.getEntities(
            EntityTypeTest.forClass(MiniScaledPortal.class),
            unwrappedBox.toRealNumberBox(),
            p -> p.recordEntry != null && p.recordEntry.id == entry.id
        );
        
        if (portals.isEmpty()) {
            LOGGER.error("cannot find portal to unwrap scale box {}", entry);
            player.sendSystemMessage(Component.literal("Valid scale box portal not found"));
            return;
        }
        
        if (portals.stream().anyMatch(p -> p.getPortalShape().isPlanar())) {
            player.sendSystemMessage(Component.literal(
                "Scale box portal not upgraded. Try to break the scale box and place it again."
            ));
            return;
        }
        
        portals = portals.stream().filter(p -> {
            PortalShape portalShape = p.getPortalShape();
            if (!(portalShape instanceof BoxPortalShape boxPortalShape)) {
                return false;
            }
            
            return boxPortalShape.facingOutwards;
        }).toList();
        
        if (portals.isEmpty()) {
            LOGGER.error("cannot find portal to unwrap scale box {} after filtering", entry);
            player.sendSystemMessage(Component.literal("Valid scale box portal not found"));
            return;
        }
        
        MiniScaledPortal portal = portals.get(0);
        UnilateralPortalState currState = portal.getThisSideState();
        UnilateralPortalState destState = new UnilateralPortalState.Builder()
            .dimension(currState.dimension())
            .position(unwrappedBox.getCenterVec())
            .orientation(currState.orientation())
            .width(unwrappedBox.getSize().getX())
            .height(unwrappedBox.getSize().getY())
            .thickness(unwrappedBox.getSize().getZ())
            .build();
        DeltaUnilateralPortalState diff = DeltaUnilateralPortalState.fromDiff(currState, destState);
        
        long currTime = entranceWorld.getGameTime();
        portal.addThisSideAnimationDriver(
            new NormalAnimation.Builder()
                .startingGameTime(currTime)
                .loopCount(1)
                .phases(List.of(
                    new NormalAnimation.Phase(durationTicks, diff, TimingFunction.easeInOutCubic)
                ))
                .build()
        );
        
        entry.scheduledUnwrapTime = currTime + durationTicks;
        ScaleBoxRecord.get(server).setDirty();
        
        int originalGeneration = entry.generation;
        
        IPGlobal.serverTaskList.addTask(MyTaskList.withDelay(
            durationTicks, () -> {
                // validate again because it may change during animation
                if (server.getLevel(entranceWorld.dimension()) == null) {
                    player.sendSystemMessage(Component.literal(
                        "Unwrapping failed because the scale box entrance world missing"
                    ));
                    return true;
                }
                
                if (entry.generation != originalGeneration) {
                    player.sendSystemMessage(Component.literal(
                        "Unwrapping failed because the scale box entrance changed"
                    ));
                    return true;
                }
                
                if (entry.currentEntranceDim != entranceWorld.dimension()
                    || entry.currentEntrancePos == null
                ) {
                    player.sendSystemMessage(Component.literal(
                        "Unwrapping failed because the scale box entrance is invalid"
                    ));
                    return true;
                }
                
                if (!validateUnwrappingRegion(unwrappedBox, entry)) {
                    player.sendSystemMessage(Component.literal(
                        "Unwrapping failed because the scale box entrance is invalid"
                    ));
                    return true;
                }
                
                if (!validateUnwrappingRegionBlocks(unwrappedBox, entry, entranceWorld)) {
                    player.sendSystemMessage(Component.literal(
                        "Unwrapping failed because the unwrapped region is not clear"
                    ));
                    return true;
                }
                
                doUnwrap(
                    player,
                    entranceWorld,
                    entry,
                    unwrappedBox,
                    rotationFromInnerToOuter,
                    portal
                );
                
                tellClientToForceMainThreadRebuildTemporarily(player);
                
                return true;
            }
        ));
    }
    
    public static void doUnwrap(
        ServerPlayer player, ServerLevel entranceWorld,
        ScaleBoxRecord.Entry entry,
        IntBox expandedBox,
        AARotation rotationFromInnerToOuter,
        MiniScaledPortal portal
    ) {
        MinecraftServer server = entranceWorld.getServer();
        
        ScaleBoxRecord scaleBoxRecord = ScaleBoxRecord.get(server);
        if (scaleBoxRecord.getEntryById(entry.id) != entry) {
            player.sendSystemMessage(Component.literal(
                "Unwrapping failed because box entry invalid"
            ));
            return;
        }
        
        if (entry.currentEntranceDim == null || entry.currentEntrancePos == null) {
            player.sendSystemMessage(Component.literal(
                "Unwrapping failed because the scale box entrance is missing"
            ));
            return;
        }
        
        ServerLevel voidDim = VoidDimension.getVoidServerWorld(server);
        
        IntBox innerAreaBox = entry.getInnerAreaBox();
        
        scaleBoxRecord.removeEntry(entry.id);
        scaleBoxRecord.setDirty();
        
        BlockPos innerDiagVec = innerAreaBox.getSize().multiply(entry.scale);
        BlockPos outerDiagVec = rotationFromInnerToOuter.transform(innerDiagVec);
        BlockPos outerBoxBasePos = new BlockPos(
            outerDiagVec.getX() > 0 ? expandedBox.l.getX() : expandedBox.h.getX(),
            outerDiagVec.getY() > 0 ? expandedBox.l.getY() : expandedBox.h.getY(),
            outerDiagVec.getZ() > 0 ? expandedBox.l.getZ() : expandedBox.h.getZ()
        );
        
        forceBundleConditionally(() -> {
            transferRegion(
                voidDim,
                innerAreaBox.l,
                entranceWorld,
                outerBoxBasePos,
                innerAreaBox.getSize(),
                rotationFromInnerToOuter,
                MSGlobal.config.getConfig().serverBetterAnimation,
                MSGlobal.config.getConfig().serverBetterAnimation,
                entry.teleportChangesGravity
            );
            
            portal.remove(Entity.RemovalReason.KILLED);
            
            Portal reversePortal = PortalExtension.get(portal).reversePortal;
            if (reversePortal != null) {
                reversePortal.remove(Entity.RemovalReason.KILLED);
            }
            
            return null;
        });
        
        // clear barrier blocks
        IntBox barrierBox = innerAreaBox.getAdjusted(-1, -1, -1, 1, 1, 1);
        for (Direction direction : Direction.values()) {
            barrierBox.getSurfaceLayer(direction).fastStream().forEach(blockPos -> {
                voidDim.setBlockAndUpdate(blockPos, Blocks.AIR.defaultBlockState());
            });
        }
    }
    
    /**
     * @param box       the region to check. must be 1-layer
     * @param direction the direction to check
     * @return not null if there is one block in box that expands along direction out of the box
     */
    public static @Nullable BlockPos checkCrossBoundaryMultiBlockStructure(
        ServerLevel world, IntBox box, Direction direction
    ) {
        return box.fastStream().filter(pos -> {
            // currently only consider piston
            
            BlockState blockState = world.getBlockState(pos);
            if (blockState.getBlock() instanceof PistonBaseBlock pistonBaseBlock) {
                Direction facing = blockState.getValue(PistonBaseBlock.FACING);
                if (facing == direction) {
                    return true;
                }
            }
            
            if (blockState.getBlock() instanceof PistonHeadBlock pistonHeadBlock) {
                Direction facing = blockState.getValue(PistonHeadBlock.FACING);
                if (facing == direction.getOpposite()) {
                    return true;
                }
            }
            
            return false;
        }).findFirst().orElse(null);
    }
    
    public static void transferRegion(
        ServerLevel srcWorld,
        BlockPos srcOrigin,
        ServerLevel dstWorld,
        BlockPos dstOrigin,
        BlockPos regionSize,
        AARotation rotation,
        boolean syncSrcSideBlockUpdateToClientImmediately,
        boolean syncDstSideBlockUpdateToClientImmediately,
        boolean transformGravity
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
        
        transferEntities(srcWorld, srcOrigin, dstWorld, dstOrigin, regionSize, rotation, transformGravity);
        
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
     *
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
    }
    
    // Piston head block is special. If we break piston head before breaking base,
    // piston base will break and drop item.
    // Vanilla code does not allow suppressing this by the flag.
    private static boolean shouldDelayClear(BlockState blockState) {
        return blockState.getBlock() instanceof PistonHeadBlock;
    }
    
    /**
     * Only sets block. Will not cause any block update.
     */
    public static BlockState rawSetBlock(ServerLevel world, BlockPos blockPos, BlockState blockState) {
        LevelChunk chunk = world.getChunk(
            SectionPos.blockToSectionCoord(blockPos.getX()),
            SectionPos.blockToSectionCoord(blockPos.getZ())
        );
        int sectionIndex = world.getSectionIndex(blockPos.getY());
        LevelChunkSection[] sections = chunk.getSections();
        if (sectionIndex < 0 || sectionIndex >= sections.length) {
            return null;
        }
        LevelChunkSection section = sections[sectionIndex];
        return section.setBlockState(
            blockPos.getX() & 15,
            blockPos.getY() & 15,
            blockPos.getZ() & 15,
            blockState
        );
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
        boolean transformGravity
    ) {
        AABB box = IntBox.fromBasePointAndSize(fromOrigin, regionSize).toRealNumberBox();
        List<Entity> entities = McHelper.findEntitiesByBox(
            Entity.class,
            fromWorld,
            box,
            16,
            ScaleBoxOperations::isEntityMovable
        );
        
        BlockPos transformedRegionSize = rotation.transform(regionSize);
        
        Vec3 fromOriginPos = Vec3.atLowerCornerOf(fromOrigin);
        Vec3 toOriginPos = new Vec3(
            transformedRegionSize.getX() > 0 ? toOrigin.getX() : toOrigin.getX() + 1,
            transformedRegionSize.getY() > 0 ? toOrigin.getY() : toOrigin.getY() + 1,
            transformedRegionSize.getZ() > 0 ? toOrigin.getZ() : toOrigin.getZ() + 1
        );
        for (Entity entity : entities) {
            if (!canReallyMoveEntity(entity)) {
                LOGGER.info("Skipping non-transferable entity {}", entity);
                continue;
            }
            
            Vec3 position = entity.position();
            Vec3 newPosition = rotation.quaternion.rotate(position.subtract(fromOriginPos)).add(toOriginPos);
            float oldXRot = entity.getXRot();
            float oldYRot = entity.getYRot();
            Direction oldGrav = GravityChangerInterface.invoker.getBaseGravityDirection(entity);
            
            Entity newEntity = PortalAPI.teleportEntity(
                entity, toWorld, newPosition
            );
            
            if (transformGravity) {
                Direction newGrav = rotation.transformDirection(oldGrav);
                GravityChangerInterface.invoker.setBaseGravityDirectionServer(entity, newGrav);
            }
            
            DQuaternion oldCameraRot = DQuaternion.getCameraRotation(
                oldXRot, oldYRot
            ).getConjugated();
            DQuaternion newCameraRot = oldCameraRot.hamiltonProduct(rotation.quaternion).getConjugated();
            Tuple<Double, Double> newPitchYaw = DQuaternion.getPitchYawFromRotation(newCameraRot);
            double newXRot = newPitchYaw.getA();
            double newYRot = newPitchYaw.getB();
            newEntity.setXRot((float) newXRot);
            newEntity.setYRot((float) newYRot);
            
            LOGGER.info("Transferred entity {}", entity);
        }
    }
    
    public static boolean isEntityMovable(Entity entity) {
        // when a region adjacent to a scale box is wrapped,
        // the scale box portal bounding box has thickness,
        // so it will test that portal even though the area does not overlap
        // testing the placeholder block will be more accurate
        if (entity instanceof MiniScaledPortal) {
            return true;
        }
        
        return canReallyMoveEntity(entity);
    }
    
    public static boolean canReallyMoveEntity(Entity entity) {
        // portal is not movable
        if (entity instanceof Portal) {
            return false;
        }
        
        return entity.canChangeDimensions();
    }
    
    public static boolean isBlockMovable(ServerLevel world, BlockPos p, BlockState blockState) {
        if (blockState.getBlock() == ScaleBoxPlaceholderBlock.INSTANCE) {
            // not allowing wrapping scale box for now
            return false;
        }
        
        if (blockState.getBlock() instanceof LiquidBlock) {
            return true;
        }
        
        float destroySpeed = blockState.getDestroySpeed(world, p);
        // bedrock is -1, obsidian is 50
        return destroySpeed >= 0 && destroySpeed < 50;
    }
    
    public static void putScaleBoxEntranceIntoWorld(
        ScaleBoxRecord.Entry entry,
        ServerLevel world, BlockPos outerBoxBasePos,
        AARotation rotation,
        ServerPlayer player,
        @Nullable IntBox fromWrappedBox
    ) {
        if (!Objects.equals(entry.ownerId, player.getUUID())) {
            if (player.hasPermissions(2)) {
                player.displayClientMessage(
                    Component.translatable("mini_scaled.placed_other_players_box_with_permission"),
                    false
                );
            }
            else {
                player.displayClientMessage(
                    Component.translatable("mini_scaled.cannot_place_other_players_box"),
                    true
                );
                return;
            }
        }
        
        MinecraftServer server = world.getServer();
        
        entry.currentEntranceDim = world.dimension();
        entry.currentEntrancePos = outerBoxBasePos;
        entry.entranceRotation = rotation;
        entry.generation++;
        
        ScaleBoxRecord.get(server).setDirty(true);
        
        doPutScaleBoxEntranceIntoWorld(server, entry, fromWrappedBox);
    }
    
    public static void doPutScaleBoxEntranceIntoWorld(
        MinecraftServer server, ScaleBoxRecord.Entry entry, @Nullable IntBox fromWrappedBox
    ) {
        if (entry.currentEntranceDim == null) {
            LOGGER.error("entrance dim is null {}", entry);
            return;
        }
        
        ServerLevel world = server.getLevel(entry.currentEntranceDim);
        
        if (world == null) {
            LOGGER.error("cannot find world {} to place scale box", entry.currentEntranceDim.location());
            return;
        }
        
        ServerLevel voidWorld = VoidDimension.getVoidServerWorld(server);
        ScaleBoxGeneration.createScaleBoxPortals(voidWorld, world, entry, fromWrappedBox);
        
        putScaleBoxEntranceBlocks(world, entry);
    }
    
    private static void putScaleBoxEntranceBlocks(ServerLevel world, ScaleBoxRecord.Entry entry) {
        entry.getOuterAreaBox().stream().forEach(outerPos -> {
            world.setBlockAndUpdate(outerPos, ScaleBoxPlaceholderBlock.INSTANCE.defaultBlockState());
            
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
    
    public static @Nullable BlockPos checkNonWrappableBlock(
        ServerLevel world, IntBox area
    ) {
        return area.fastStream().filter(
            p -> !isBlockMovable(world, p, world.getBlockState(p))
        ).findFirst().map(BlockPos::immutable).orElse(null);
    }
    
    public static @Nullable Entity checkNonWrappableEntity(
        ServerLevel world, IntBox area
    ) {
        List<Entity> entities = world.getEntitiesOfClass(
            Entity.class,
            area.toRealNumberBox()
        );
        
        for (Entity entity : entities) {
            if (!isEntityMovable(entity)) {
                return entity;
            }
        }
        
        return null;
    }
    
    public static boolean validateUnwrappingRegionBlocks(
        IntBox unwrappingArea, ScaleBoxRecord.Entry entry, ServerLevel world
    ) {
        IntBox entranceBox = entry.getOuterAreaBox();
        return unwrappingArea.fastStream().allMatch(
            p -> entranceBox.contains(p) || world.getBlockState(p).isAir()
        );
    }
    
    public static boolean validateUnwrappingRegion(
        IntBox unwrappingArea, ScaleBoxRecord.Entry entry
    ) {
        IntBox entryOuterAreaBox = entry.getOuterAreaBox();
        return unwrappingArea.contains(entryOuterAreaBox.l)
            && unwrappingArea.contains(entryOuterAreaBox.h)
            && entryOuterAreaBox.getSize().multiply(entry.scale).equals(unwrappingArea.getSize());
    }
    
    private static <R> R forceBundleConditionally(Supplier<R> func) {
        if (MSGlobal.config.getConfig().serverBetterAnimation) {
            return PacketRedirection.withForceBundle(func);
        }
        else {
            return func.get();
        }
    }
}
