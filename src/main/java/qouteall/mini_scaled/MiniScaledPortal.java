package qouteall.mini_scaled;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.Portal;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class MiniScaledPortal extends Portal {
    public static EntityType<MiniScaledPortal> entityType;
    
    public int boxId = 0;
    public int generation = 0;
    
    public MiniScaledPortal(EntityType<?> entityType, Level world) {
        super(entityType, world);
    }
    
    @Override
    public void tick() {
        super.tick();
        
        if (level.isClientSide()) {
            tickClient();
        }
        else {
            if (level.getGameTime() % 2 == 0) {
                level.getProfiler().push("validate");
                checkValidity();
                level.getProfiler().pop();
            }
        }
    }
    
    @Override
    protected void readAdditionalSaveData(CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        generation = compoundTag.getInt("generation");
        boxId = compoundTag.getInt("boxId");
    }
    
    @Override
    protected void addAdditionalSaveData(CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
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
            MobCategory.MISC,
            constructor
        ).dimensions(
            new EntityDimensions(1, 1, true)
        ).fireImmune().trackable(96, 20).build();
        setEntityType.accept(entityType);
        Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            new ResourceLocation(id),
            entityType
        );
    }
    
    public static void init() {
        registerEntity(
            t -> {entityType = t;},
            () -> entityType,
            "mini_scaled:portal",
            MiniScaledPortal::new,
            BuiltInRegistries.ENTITY_TYPE
        );
    }
    
    private void checkValidity() {
        ScaleBoxRecord.Entry entry = ScaleBoxRecord.get().getEntryById(boxId);
        if (entry == null) {
            System.err.println("no scale box record " + boxId + this);
            remove(RemovalReason.KILLED);
            return;
        }
        
        if (generation != entry.generation) {
//            System.out.println("removing old portal " + this);
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
        if (level.isClientSide()) {
            onCollidingWithEntityClientOnly(entity);
        }
        
    }
    
    @Environment(EnvType.CLIENT)
    private void onCollidingWithEntityClientOnly(Entity entity) {
        if (isOuterPortal() && isFacingUp()) {
            if (entity instanceof LocalPlayer) {
                showShiftDescendMessage();
                
                // not ClientPlayerEntity to avoid dedicated server crash as it's captured
                Player player = (Player) entity;
                if (player.getPose() == Pose.CROUCHING) {
                    IPGlobal.clientTaskList.addTask(() -> {
                        if (player.level == level) {
                            // changing player pos immediately may cause ConcurrentModificationException
                            player.setPosRaw(player.getX(), player.getY() - 0.01, player.getZ());
                            McHelper.updateBoundingBox(player);
                        }
                        return true;
                    });
                }
            }
        }
        
    }
    
    private boolean isFacingUp() {
        return ((Portal) this).getNormal().y > 0.9;
    }
    
    @Override
    public void transformVelocity(Entity entity) {
        super.transformVelocity(entity);
        
        if (isOuterPortal() && isFacingUp()) {
            entity.setDeltaMovement(entity.getDeltaMovement().scale(0.5));
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
        
        Minecraft client = Minecraft.getInstance();
        
        client.gui.setOverlayMessage(
            Component.translatable(
                "mini_scaled.press_shift",
                client.options.keyShift.getTranslatedKeyMessage()
            ),
            false
        );
    }
    
    @Override
    public boolean isInteractable() {
        if (level.isClientSide()) {
            return ClientScaleBoxInteractionControl.canInteractInsideScaleBox();
        }
        else {
            return true;
        }
    }
}
