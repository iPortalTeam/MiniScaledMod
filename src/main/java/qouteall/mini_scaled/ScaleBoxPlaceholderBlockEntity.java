package qouteall.mini_scaled;

import com.qouteall.immersive_portals.McHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Tickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import org.apache.commons.lang3.Validate;

public class ScaleBoxPlaceholderBlockEntity extends BlockEntity implements Tickable {
    public static BlockEntityType<ScaleBoxPlaceholderBlockEntity> blockEntityType;
    
    public static void init() {
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
    
    @Override
    public void fromTag(BlockState state, CompoundTag tag) {
        super.fromTag(state, tag);
        boxId = tag.getInt("boxId");
    }
    
    @Override
    public CompoundTag toTag(CompoundTag tag) {
        super.toTag(tag);
        tag.putInt("boxId", boxId);
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
        
        checkValidity();
    }
    
    public void checkValidity() {
        ScaleBoxRecord.Entry entry = ScaleBoxRecord.getEntryById(boxId);
        
        if (entry == null) {
            System.out.println("invalid box with id " + boxId);
            destroy();
            return;
        }
        
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
    
    private void destroy() {
        Validate.isTrue(!world.isClient());
        System.out.println("destroy scale box " + boxId);
        world.setBlockState(getPos(), Blocks.AIR.getDefaultState());
        markRemoved();
    }
    
    private static void notifyPortalBreak(int boxId) {
        ScaleBoxRecord.Entry entry = ScaleBoxRecord.getEntryById(boxId);
        if (entry != null) {
            entry.generation++;
            ScaleBoxRecord.get().setDirty(true);
        }
    }
    
    public static void checkBlockIntegrity(
        int boxId
    ) {
        ScaleBoxRecord.Entry entry = ScaleBoxRecord.getEntryById(boxId);
        
        if (entry == null) {
            return;
        }
        
        RegistryKey<World> currentEntranceDim = entry.currentEntranceDim;
        if (currentEntranceDim == null) {
            System.err.println("null entrance dim " + boxId);
            return;
        }
        BlockPos currentEntrancePos = entry.currentEntrancePos;
        
        ServerWorld entranceWorld = McHelper.getServer().getWorld(currentEntranceDim);
        if (entranceWorld == null) {
            System.err.println("invalid entrance dim " + currentEntranceDim);
            return;
        }
        
        boolean chunkLoaded = entranceWorld.isChunkLoaded(currentEntrancePos);
        if (!chunkLoaded) {
            return;
        }
        
        boolean blockValid =
            entranceWorld.getBlockState(currentEntrancePos).getBlock() == ScaleBoxPlaceholderBlock.instance;
        
        if (!blockValid) {
            notifyPortalBreak(boxId);
        }
    }
}
