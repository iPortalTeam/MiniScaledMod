package qouteall.mini_scaled;

import com.mojang.logging.LogUtils;
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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.mini_scaled.util.MSUtil;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MiniScaledPortal extends Portal {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static EntityType<MiniScaledPortal> entityType;
    
    public int boxId = 0;
    public int generation = 0;
    
    /**
     * This field is added in newer versions of MiniScaled.
     * The boxId and generation is still used to check if the portal is valid.
     */
    @Nullable
    public ScaleBoxRecord.Entry recordEntry;
    
    public MiniScaledPortal(EntityType<?> entityType, Level world) {
        super(entityType, world);
    }
    
    @Override
    public void tick() {
        super.tick();
        
        // in old versions it set interactable to false.
        // change it to true
        setInteractable(true);
        
        if (level().isClientSide()) {
            tickClient();
        }
        else {
            if (recordEntry == null) {
                recordEntry = ScaleBoxRecord.get().getEntryById(boxId);
                if (recordEntry == null) {
                    kill();
                    return;
                }
            }
            
            if (level().getGameTime() % 2 == 0) {
                level().getProfiler().push("scale_box_portal_update");
                ScaleBoxRecord.Entry entry = ScaleBoxRecord.get().getEntryById(boxId);
                if (entry == null) {
                    LOGGER.error("no scale box record {} {}", boxId, this);
                    kill();
                }
                else if (generation != entry.generation) {
                    kill();
                }
                level().getProfiler().pop();
            }
        }
    }
    
    @Override
    protected void readAdditionalSaveData(CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        generation = compoundTag.getInt("generation");
        boxId = compoundTag.getInt("boxId");
        
        if (compoundTag.contains("recordEntry")) {
            ScaleBoxRecord.Entry entry = new ScaleBoxRecord.Entry();
            entry.readFromNbt(compoundTag.getCompound("recordEntry"));
            recordEntry = entry;
        }
        else {
            // the recordEntry field is added in newer versions of MiniScaled
            // so the old portals don't have that.
            if (level().isClientSide()) {
                recordEntry = null;
                LOGGER.error("Deserialized MiniScaledPortal without recordEntry {}", compoundTag);
            }
            else {
                recordEntry = ScaleBoxRecord.get().getEntryById(boxId);
            }
        }
    }
    
    @Override
    protected void addAdditionalSaveData(CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        compoundTag.putInt("generation", generation);
        compoundTag.putInt("boxId", boxId);
        if (recordEntry != null) {
            CompoundTag recordEntryTag = new CompoundTag();
            recordEntry.writeToNbt(recordEntryTag);
            compoundTag.put("recordEntry", recordEntryTag);
        }
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
        if (level().isClientSide()) {
            onCollidingWithEntityClientOnly(entity);
        }
        
    }
    
    @Environment(EnvType.CLIENT)
    private void onCollidingWithEntityClientOnly(Entity entity) {
        if (isOuterPortal()) {
            if (entity instanceof LocalPlayer) {
                Vec3 gravityVec = MSUtil.getGravityVec(entity);
                if (getNormal().dot(gravityVec) < -0.5) {
                    showShiftDescendMessage();
                    
                    // not ClientPlayerEntity to avoid dedicated server crash as it's captured in lambda
                    Player player = (Player) entity;
                    if (player.getPose() == Pose.CROUCHING) {
                        IPGlobal.clientTaskList.addTask(() -> {
                            if (player.level() == level()) {
                                Vec3 posDelta = gravityVec.scale(0.01);
                                
                                // changing player pos immediately may cause ConcurrentModificationException
                                player.setPosRaw(
                                    player.getX() + posDelta.x,
                                    player.getY() + posDelta.y,
                                    player.getZ() + posDelta.z
                                );
                                McHelper.updateBoundingBox(player);
                            }
                            return true;
                        });
                    }
                }
            }
        }
        
    }
    
    
    @Override
    public Vec3 transformVelocityRelativeToPortal(Vec3 v, Entity entity) {
        Vec3 velocity = super.transformVelocityRelativeToPortal(v, entity);
        
        if (isOuterPortal()) {
            Vec3 gravityVec = MSUtil.getGravityVec(entity);
            if (getNormal().dot(gravityVec) < -0.5) {
                return velocity.scale(0.5);
            }
        }
        
        return velocity;
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
    public boolean isInteractableBy(Player player) {
        if (level().isClientSide()) {
            return ClientScaleBoxInteractionControl.canInteractInsideScaleBox();
        }
        
        if (recordEntry != null) {
            if (recordEntry.accessControl) {
                boolean idMatches = Objects.equals(recordEntry.ownerId, player.getUUID());
                if (!idMatches) {
                    return false;
                }
            }
        }
        
        return super.isInteractableBy(player);
    }
    
    public boolean canCollideWithEntity(Entity entity) {
        // if access control is enabled and the entity is not the owner
        // it will not be teleportable
        // but we want it to still have cross portal collision
        return true;
    }
    
    @Override
    public boolean canTeleportEntity(Entity entity) {
        if (recordEntry != null) {
            if (recordEntry.accessControl) {
                boolean idMatches = Objects.equals(recordEntry.ownerId, entity.getUUID());
                if (!idMatches) {
                    return false;
                }
            }
        }
        
        return super.canTeleportEntity(entity);
    }
}
