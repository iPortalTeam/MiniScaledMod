package qouteall.mini_scaled;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.Portal;

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
            if (world.getTime() % 2 == 0) {
                world.getProfiler().push("validate");
                checkValidity();
                world.getProfiler().pop();
            }
        }
    }
    
    @Override
    protected void readCustomDataFromNbt(NbtCompound compoundTag) {
        super.readCustomDataFromNbt(compoundTag);
        generation = compoundTag.getInt("generation");
        boxId = compoundTag.getInt("boxId");
    }
    
    @Override
    protected void writeCustomDataToNbt(NbtCompound compoundTag) {
        super.writeCustomDataToNbt(compoundTag);
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
            remove(RemovalReason.KILLED);
            return;
        }
        
        if (generation != entry.generation) {
            System.out.println("removing old portal " + this);
            remove(RemovalReason.KILLED);
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
                showShiftDescendMessage();
                
                // not ClientPlayerEntity to avoid dedicated server crash
                PlayerEntity player = (PlayerEntity) entity;
                if (player.getPose() == EntityPose.CROUCHING) {
                    IPGlobal.clientTaskList.addTask(() -> {
                        if (player.world == world) {
                            // changing player pos immediately may cause ConcurrentModificationExcetpion
                            player.setPos(player.getX(), player.getY() - 0.01, player.getZ());
                            McHelper.updateBoundingBox(player);
                        }
                        return true;
                    });
                }
                
                Vec3d velocity = entity.getVelocity();
                if (velocity.y < 0 && player.getY() < getY()) {
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
    
    private static boolean messageShown = false;
    
    @Environment(EnvType.CLIENT)
    private void showShiftDescendMessage() {
        if (messageShown) {
            return;
        }
        messageShown = true;
        
        MinecraftClient client = MinecraftClient.getInstance();
        
        client.inGameHud.setOverlayMessage(
            new TranslatableText(
                "mini_scaled.press_shift",
                client.options.sneakKey.getBoundKeyLocalizedText()
            ),
            false
        );
    }
    
}
