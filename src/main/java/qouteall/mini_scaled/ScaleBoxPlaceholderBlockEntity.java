package qouteall.mini_scaled;

import com.qouteall.immersive_portals.Helper;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Tickable;
import net.minecraft.util.registry.Registry;

import java.util.UUID;

public class ScaleBoxPlaceholderBlockEntity extends BlockEntity implements Tickable {
    public static BlockEntityType<ScaleBoxPlaceholderBlockEntity> blockEntityType;
    
    public static void init() {
        //  DEMO_BLOCK_ENTITY = Registry.register(Registry.BLOCK_ENTITY_TYPE, "modid:demo", BlockEntityType.Builder.create(DemoBlockEntity::new, DEMO_BLOCK).build(null));
        blockEntityType = Registry.register(
            Registry.BLOCK_ENTITY_TYPE,
            "mini_scaled:placeholder_block_entity",
            BlockEntityType.Builder.create(
                ScaleBoxPlaceholderBlockEntity::new,
                ScaleBoxPlaceholderBlock.instance
            ).build(null)
        );
    }
    
    public ScaleBoxPlaceholderBlockEntity() {
        super(blockEntityType);
    }
    
    public int boxId;
    public UUID[] portalUuids;
    
    @Override
    public void fromTag(BlockState state, CompoundTag tag) {
        super.fromTag(state, tag);
        boxId = tag.getInt("boxId");
        portalUuids = new UUID[6];
        portalUuids[0] = tag.getUuid("portalUuid0");
        portalUuids[1] = tag.getUuid("portalUuid1");
        portalUuids[2] = tag.getUuid("portalUuid2");
        portalUuids[3] = tag.getUuid("portalUuid3");
        portalUuids[4] = tag.getUuid("portalUuid4");
        portalUuids[5] = tag.getUuid("portalUuid5");
    }
    
    @Override
    public CompoundTag toTag(CompoundTag tag) {
        super.toTag(tag);
        tag.putInt("boxId", boxId);
        tag.putUuid("portalUuid0", portalUuids[0]);
        tag.putUuid("portalUuid1", portalUuids[1]);
        tag.putUuid("portalUuid2", portalUuids[2]);
        tag.putUuid("portalUuid3", portalUuids[3]);
        tag.putUuid("portalUuid4", portalUuids[4]);
        tag.putUuid("portalUuid5", portalUuids[5]);
        return tag;
    }
    
    
    @Override
    public void tick() {
        if (world.isClient()) {
            return;
        }
        
        if (world.getTime() % 23 != 2) {
            return;
        }
    
        ScaleBoxRecord scaleBoxRecord = ScaleBoxRecord.get();
        int i = Helper.indexOf(scaleBoxRecord.entries, entry -> {
            return entry.id == this.boxId;
        });
        if (i == -1) {
            System.out.println("invalid box with id " + boxId);
        }
        ScaleBoxRecord.Entry entry = scaleBoxRecord.entries.get(i);
        boolean posEquals = entry.currentEntrancePos.equals(getPos());
        if (!posEquals) {
            System.out.println("invalid box entrance position " + boxId + getPos() + entry.currentEntrancePos);
            destroy();
            return;
        }
        
        boolean dimEquals = entry.currentEntranceDim == world.getRegistryKey();
        if (!dimEquals) {
            System.out.println("invalid box dim " + boxId + world.getRegistryKey() + entry.currentEntranceDim);
            destroy();
            return;
        }
        
        
    }
    
    void destroy() {
        System.out.println("destroy scale box " + boxId);
        markRemoved();
        for (UUID portalUuid : portalUuids) {
            ServerWorld serverWorld = (ServerWorld) this.world;
            Entity entity = serverWorld.getEntity(portalUuid);
            if (entity != null) {
                entity.remove();
            }
        }
    }
}
