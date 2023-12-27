package qouteall.mini_scaled;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.mc_utils.ServerTaskList;
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
import qouteall.q_misc_util.my_util.IntBox;
import qouteall.q_misc_util.my_util.MyTaskList;

import java.util.List;
import java.util.Objects;
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
            RegionTransfer.transferRegion(
                world,
                wrappedBox.l,
                voidDim,
                entry.innerBoxPos,
                boxSize,
                AARotation.IDENTITY,
                MSGlobal.config.getConfig().serverBetterAnimation,
                MSGlobal.config.getConfig().serverBetterAnimation,
                entry.teleportChangesGravity,
                ScaleBoxOperations::canReallyMoveEntity
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
        
        ServerTaskList.of(server).addTask(MyTaskList.withDelay(
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
            RegionTransfer.transferRegion(
                voidDim,
                innerAreaBox.l,
                entranceWorld,
                outerBoxBasePos,
                innerAreaBox.getSize(),
                rotationFromInnerToOuter,
                MSGlobal.config.getConfig().serverBetterAnimation,
                MSGlobal.config.getConfig().serverBetterAnimation,
                entry.teleportChangesGravity,
                ScaleBoxOperations::canReallyMoveEntity
            );
            
            portal.remove(Entity.RemovalReason.KILLED);
            
            Portal reversePortal = PortalExtension.get(portal).reversePortal;
            if (reversePortal != null) {
                reversePortal.remove(Entity.RemovalReason.KILLED);
            }
            
            tellClientToForceMainThreadRebuildTemporarily(player);
            
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
    
    /**
     * On client side, packets are handled in gaps of frames.
     * To make the wrapping/unwrapping seamless, we need to bundle the packets to make them handled in one frame-gap.
     * We also tell client to do immediate section mesh rebuild.
     */
    private static <R> R forceBundleConditionally(Supplier<R> func) {
        if (MSGlobal.config.getConfig().serverBetterAnimation) {
            return PacketRedirection.withForceBundle(func);
        }
        else {
            return func.get();
        }
    }
}
