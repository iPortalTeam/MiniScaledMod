package qouteall.mini_scaled;

import com.qouteall.immersive_portals.Helper;
import com.qouteall.immersive_portals.McHelper;
import com.qouteall.immersive_portals.portal.Portal;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class MiniScaledPortal extends Portal {
    public static EntityType<MiniScaledPortal> entityType;
    
    public int boxId = 0;
    public int generation = 0;
    
    public MiniScaledPortal(EntityType<?> entityType, World world) {
        super(entityType, world);
    }
    
    @Override
    public void tick() {
        super.tick();
        
        if (world.isClient()) {
            tickClient();
        }
        else {
            if (world.getTime() % 3 == 0) {
                checkValidity();
            }
        }
    }
    
    @Override
    protected void readCustomDataFromTag(CompoundTag compoundTag) {
        super.readCustomDataFromTag(compoundTag);
        generation = compoundTag.getInt("generation");
        boxId = compoundTag.getInt("boxId");
    }
    
    @Override
    protected void writeCustomDataToTag(CompoundTag compoundTag) {
        super.writeCustomDataToTag(compoundTag);
        compoundTag.putInt("generation", generation);
        compoundTag.putInt("boxId", boxId);
    }
    
    private static <T extends Entity> void registerEntity(
        Consumer<EntityType<T>> setEntityType,
        Supplier<EntityType<T>> getEntityType,
        String id,
        EntityType.EntityFactory<T> constructor,
        Registry<EntityType<?>> registry
    ) {
        EntityType<T> entityType = FabricEntityTypeBuilder.create(
            SpawnGroup.MISC,
            constructor
        ).dimensions(
            new EntityDimensions(1, 1, true)
        ).fireImmune().trackable(96, 20).build();
        setEntityType.accept(entityType);
        Registry.register(
            Registry.ENTITY_TYPE,
            new Identifier(id),
            entityType
        );
    }
    
    public static void init() {
        registerEntity(
            t -> {entityType = t;},
            () -> entityType,
            "mini_scaled:portal",
            MiniScaledPortal::new,
            Registry.ENTITY_TYPE
        );
    }
    
    private void checkValidity() {
        ScaleBoxRecord.Entry entry = ScaleBoxRecord.getEntryById(boxId);
        if (entry == null) {
            System.err.println("no scale box record " + boxId + this);
            remove();
            return;
        }
        
        if (generation != entry.generation) {
            System.out.println("removing old portal " + this);
            remove();
            return;
        }
    }
    
    public boolean isOuterPortal() {
        return getScale() > 1;
    }
    
    @Override
    public double getDestAreaRadiusEstimation() {
        return 12 * 16;
    }
    
    @Override
    public boolean allowOverlappedTeleport() {
        return true;
    }
    
    @Override
    public void onCollidingWithEntity(Entity entity) {
        if (world.isClient()) {
            onCollidingWithEntityClientOnly(entity);
        }
        
    }
    
    @Environment(EnvType.CLIENT)
    private void onCollidingWithEntityClientOnly(Entity entity) {
        if (isOuterPortal() && (getNormal().y > 0.9)) {
            if (entity instanceof ClientPlayerEntity) {
                
                ClientPlayerEntity player = (ClientPlayerEntity) entity;
                if (player.getPose() == EntityPose.CROUCHING) {
                    player.setPos(player.getX(), player.getY() - 0.01, player.getZ());
                    McHelper.updateBoundingBox(player);
                }
                else {
                    levitatePlayer(player);
                }
                
                Vec3d velocity = entity.getVelocity();
                if (velocity.y < 0) {
                    entity.setVelocity(new Vec3d(velocity.x, velocity.y * 0.4, velocity.z));
                }
            }
        }

    }
    
    @Override
    public void transformVelocity(Entity entity) {
        super.transformVelocity(entity);

        if (isOuterPortal() && getNormal().y > 0.9) {
            entity.setVelocity(entity.getVelocity().multiply(0.5));
        }

//        if (!isOuterPortal() && getNormal().y < -0.9) {
//            entity.setVelocity(entity.getVelocity().add(0, 0.2, 0));
//        }
    }
    
    @Environment(EnvType.CLIENT)
    private void tickClient() {
    
    }
    
    private void levitatePlayer(ClientPlayerEntity player) {
        Box playerBox = player.getBoundingBox();
        Box bottomHalfBox = playerBox.shrink(0, playerBox.getYLength() / 2, 0);
        Box transformedBox = Helper.transformBox(bottomHalfBox, this::transformPoint);
        World destWorld = getDestinationWorld();
        Stream<VoxelShape> collisions = destWorld.getBlockCollisions(null, transformedBox);
        double maxCollisionY = collisions.mapToDouble(s -> s.getBoundingBox().maxY)
            .max().orElse(player.getY());
        
        Vec3d transformedPlayerPos = transformPoint(player.getPos());
        
        if (transformedPlayerPos.y < maxCollisionY) {
            Vec3d newPosOtherSide =
                new Vec3d(transformedPlayerPos.getX(), maxCollisionY, transformedPlayerPos.getZ());
            Vec3d newPosThisSide = inverseTransformPoint(newPosOtherSide);
            
            player.updatePosition(player.getX(), newPosThisSide.y, player.getZ());
        }

//        World oldWorld = player.world;
//        Vec3d oldPos = player.getPos();
//        Box oldBB = player.getBoundingBox();
//
//        player.world = getDestinationWorld();
//        Vec3d newPos = transformPoint(oldPos);
//        player.setPos(newPos.x, newPos.y, newPos.z);
//        player.setBoundingBox(Helper.transformBox(oldBB, this::transformPoint));
//
//        try {
//
//        }
//        finally {
//            player.world = oldWorld;
//            player.setPos(oldPos.x, oldPos.y, oldPos.z);
//            player.setBoundingBox(oldBB);
//        }
    }
}
