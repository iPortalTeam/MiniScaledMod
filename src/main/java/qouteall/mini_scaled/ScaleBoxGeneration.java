package qouteall.mini_scaled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.PortalExtension;
import qouteall.imm_ptl.core.portal.PortalManipulation;
import qouteall.imm_ptl.core.portal.animation.DeltaUnilateralPortalState;
import qouteall.imm_ptl.core.portal.animation.NormalAnimation;
import qouteall.imm_ptl.core.portal.animation.TimingFunction;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.imm_ptl.core.portal.shape.BoxPortalShape;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.AARotation;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.IntBox;

import java.util.List;
import java.util.Objects;

public class ScaleBoxGeneration {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleBoxGeneration.class);
    public static final String INNER_WRAPPING_PORTAL_TAG = "mini_scaled:scaled_box_inner_wrapping";
    
    public static final int PLACING_Y = 64;
    public static final int REGION_GRID_LEN = 16 * 32;
    
    public static void createScaleBoxPortals(
        ServerLevel innerWorld,
        ServerLevel outerWorld,
        ScaleBoxRecord.Entry entry,
        @Nullable IntBox fromWrappedBox
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
        
        MiniScaledPortal portal = MiniScaledPortal.ENTITY_TYPE.create(outerWorld);
        assert portal != null;
        
        portal.setPortalShape(BoxPortalShape.FACING_OUTWARDS);
        
        portal.setAxisW(new Vec3(1, 0, 0));
        portal.setAxisH(new Vec3(0, 1, 0));
        
        portal.setDestinationDimension(innerWorld.dimension());
        
        portal.setRotation(quaternion);
        
        if (fromWrappedBox == null) {
            portal.setOriginPos(outerAreaBox.getCenter());
            portal.setWidth(outerAreaBoxSize.getX());
            portal.setHeight(outerAreaBoxSize.getY());
            portal.setThickness(outerAreaBoxSize.getZ());
            portal.setDestination(innerAreaBox.getCenter());
            portal.setScaling(scale);
        }
        else {
            portal.setOriginPos(fromWrappedBox.getCenterVec());
            portal.setWidth(fromWrappedBox.getSize().getX());
            portal.setHeight(fromWrappedBox.getSize().getY());
            portal.setThickness(fromWrappedBox.getSize().getZ());
            portal.setDestination(innerAreaBox.getCenter());
            portal.setScaling(1);
            
            UnilateralPortalState currState = portal.getThisSideState();
            UnilateralPortalState destState = new UnilateralPortalState.Builder()
                .dimension(currState.dimension())
                .position(outerAreaBox.getCenter())
                .orientation(currState.orientation())
                .width(outerAreaBoxSize.getX())
                .height(outerAreaBoxSize.getY())
                .thickness(outerAreaBoxSize.getZ())
                .build();
            DeltaUnilateralPortalState delta =
                DeltaUnilateralPortalState.fromDiff(currState, destState);
            
            int durationTicks = MSGlobal.config.getConfig().wrappingAnimationTicks;
            portal.addThisSideAnimationDriver(new NormalAnimation.Builder()
                .startingGameTime(portal.level().getGameTime())
                .phases(List.of(
                    new NormalAnimation.Phase.Builder()
                        .durationTicks(durationTicks)
                        .delta(delta)
                        .timingFunction(TimingFunction.easeInOutCubic)
                        .build()
                ))
                .loopCount(1)
                .build()
            );
        }
        
        portal.setTeleportChangesScale(entry.teleportChangesScale);
        portal.setTeleportChangesGravity(entry.teleportChangesGravity);
        portal.setFuseView(true);
        portal.setCrossPortalCollisionEnabled(true);
        portal.portalTag = "mini_scaled:scaled_box";
        PortalExtension.get(portal).adjustPositionAfterTeleport = true;
        portal.setInteractable(true);
        portal.boxId = boxId;
        portal.generation = generation;
        portal.recordEntry = entry;
        
        MiniScaledPortal reversePortal =
            PortalManipulation.createReversePortal(portal, MiniScaledPortal.ENTITY_TYPE);
        
        reversePortal.setFuseView(false);
        reversePortal.setCrossPortalCollisionEnabled(true);
        reversePortal.setInteractable(true);
        reversePortal.boxId = boxId;
        reversePortal.generation = generation;
        reversePortal.recordEntry = entry;
        
        // When used with Iris, it renders normal portal instead of fuse view,
        // then when the player touches the portal, it wrongly renders the player.
        // It's a workaround to avoid this.
        reversePortal.setDoRenderPlayer(false);
        
        // manually bind
        PortalExtension.get(portal).reversePortalId = reversePortal.getUUID();
        PortalExtension.get(portal).reversePortal = reversePortal;
        PortalExtension.get(reversePortal).reversePortalId = portal.getUUID();
        PortalExtension.get(reversePortal).reversePortal = portal;
        
        McHelper.spawnServerEntity(portal);
        McHelper.spawnServerEntity(reversePortal);
        
        Validate.isTrue(portal.getId() != reversePortal.getId());
        Validate.isTrue(!Objects.equals(portal.getUUID(), reversePortal.getUUID()));
    }
    
    
    public static BlockPos getInnerBoxPosFromRegionId(int regionId) {
        int xIndex = regionId % 256;
        int zIndex = Mth.floorDiv(regionId, 256);
        
        return new BlockPos(xIndex * REGION_GRID_LEN, PLACING_Y, zIndex * REGION_GRID_LEN);
    }
    
    public static BlockPos getNearestPosInScaleBoxToTeleportTo(BlockPos pos) {
        double gridLen = REGION_GRID_LEN;
        return BlockPos.containing(
            Math.round(pos.getX() / gridLen) * gridLen + 2,
            PLACING_Y + 2,
            Math.round(pos.getZ() / gridLen) * gridLen + 2
        );
    }
    
    public static Block getGlassBlock(DyeColor color) {
        return BuiltInRegistries.BLOCK.get(new ResourceLocation("minecraft:" + color.getName() + "_stained_glass"));
    }
    
    public static boolean isValidScale(int scale) {
        return scale >= 2 && scale <= 64;
    }
    
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
            player,
            null
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
        
        MiniScaledPortal portal = MiniScaledPortal.ENTITY_TYPE.create(voidWorld);
        assert portal != null;
        
        portal.setPortalShape(BoxPortalShape.FACING_INWARDS);
        
        portal.setAxisW(new Vec3(1, 0, 0));
        portal.setAxisH(new Vec3(0, 1, 0));
        portal.setWidth(innerAreaBoxSize.x());
        portal.setHeight(innerAreaBoxSize.y());
        portal.setThickness(innerAreaBoxSize.z());
        
        portal.setOriginPos(innerAreaBox.getCenter());
        portal.setDestination(portal.getOriginPos().add(0, -1000, 0));
        portal.setDestinationDimension(voidWorld.dimension());
        
        portal.portalTag = INNER_WRAPPING_PORTAL_TAG;
        portal.boxId = boxId;
        portal.generation = generation;
        
        McHelper.spawnServerEntity(portal);
    }
}
