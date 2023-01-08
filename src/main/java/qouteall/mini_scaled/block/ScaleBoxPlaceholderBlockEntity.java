package qouteall.mini_scaled.block;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.commons.lang3.Validate;
import qouteall.mini_scaled.ScaleBoxEntranceItem;
import qouteall.mini_scaled.ScaleBoxRecord;
import qouteall.q_misc_util.MiscHelper;
import qouteall.q_misc_util.my_util.IntBox;

public class ScaleBoxPlaceholderBlockEntity extends BlockEntity {
    public static BlockEntityType<ScaleBoxPlaceholderBlockEntity> blockEntityType;
    
    public static void init() {
        blockEntityType = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            "mini_scaled:placeholder_block_entity",
            FabricBlockEntityTypeBuilder.create(
                ScaleBoxPlaceholderBlockEntity::new,
                ScaleBoxPlaceholderBlock.instance
            ).build()
        );
    }
    
    public ScaleBoxPlaceholderBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }
    
    public int boxId;
    public boolean isBasePos = true;
    
    @Override
    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);
        boxId = tag.getInt("boxId");
        if (tag.contains("isBasePos")) {
            isBasePos = tag.getBoolean("isBasePos");
        }
    }
    
    @Override
    public void writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        tag.putInt("boxId", boxId);
        tag.putBoolean("isBasePos", isBasePos);
    }
    
    public void doTick() {
        if (world.isClient()) {
            return;
        }
        
        if (world.getTime() % 7 != 2) {
            return;
        }
        
        checkValidity();
    }
    
    public static void staticTick(World world, BlockPos pos, BlockState state, ScaleBoxPlaceholderBlockEntity blockEntity) {
        blockEntity.doTick();
    }
    
    public void checkValidity() {
        ScaleBoxRecord.Entry entry = ScaleBoxRecord.get().getEntryById(boxId);
        
        if (entry == null) {
            System.out.println("invalid box with id " + boxId);
            destroyBlockAndBlockEntity();
            return;
        }
    
        IntBox scaleBoxOuterArea = IntBox.fromBasePointAndSize(entry.currentEntrancePos, entry.currentEntranceSize);
        
        boolean posEquals = scaleBoxOuterArea.contains(getPos());
        if (!posEquals) {
            System.out.println("invalid box entrance position " + boxId + getPos() + entry.currentEntrancePos);
            destroyBlockAndBlockEntity();
            return;
        }
        
        boolean dimEquals = entry.currentEntranceDim == world.getRegistryKey();
        if (!dimEquals) {
            System.out.println("invalid box dim " + boxId + world.getRegistryKey() + entry.currentEntranceDim);
            destroyBlockAndBlockEntity();
            return;
        }
    }
    
    private void destroyBlockAndBlockEntity() {
        Validate.isTrue(!world.isClient());
        System.out.println("destroy scale box " + boxId);
        world.setBlockState(getPos(), Blocks.AIR.getDefaultState());
        markRemoved();
        
        dropItemIfNecessary();
        
        // don't notifyPortalBreak()
    }
    
    public void dropItemIfNecessary() {
        if (isBasePos) {
            // the up-facing outer portal breaks. drop item
            ItemStack itemToDrop = ScaleBoxEntranceItem.boxIdToItem(boxId);
            if (itemToDrop != null) {
                ItemScatterer.spawn(
                    world, getPos().getX() + 0.5, getPos().getY() + 0.5, getPos().getZ() + 0.5, itemToDrop
                );
            }
            
            // avoid dropping item twice
            isBasePos = false;
        }
    }
    
    private static void notifyPortalBreak(int boxId) {
        ScaleBoxRecord.Entry entry = ScaleBoxRecord.get().getEntryById(boxId);
        if (entry != null) {
            entry.generation++;
            ScaleBoxRecord.get().setDirty(true);
        }
    }
    
    /**
     * there are 2 cases
     * 1. placed the same entrance elsewhere, the old entrance should break
     * in this case this integrity check will pass.
     * the generation counter was already incremented and the old portals will break.
     * in {@link ScaleBoxPlaceholderBlockEntity#checkValidity()} it will break the blocks.
     * don't {@link ScaleBoxPlaceholderBlockEntity#notifyPortalBreak(int)} as it will break the new portals
     * <p>
     * 2. break the old entrance
     * in this case this integrity check will fail.
     * the generation counter will increment and the portal will break.
     * in {@link ScaleBoxPlaceholderBlockEntity#checkValidity()} it will break the blocks.
     */
    public static void checkShouldRemovePortals(
        int boxId,
        ServerWorld world,
        BlockPos pos
    ) {
        ScaleBoxRecord record = ScaleBoxRecord.get();
        ScaleBoxRecord.Entry entry = record.getEntryById(boxId);
        
        if (entry == null) {
            return;
        }
        
        RegistryKey<World> currentEntranceDim = entry.currentEntranceDim;
        if (currentEntranceDim == null) {
            notifyPortalBreak(boxId);
            return;
        }
        
        ServerWorld entranceWorld = MiscHelper.getServer().getWorld(currentEntranceDim);
        if (entranceWorld == null) {
            System.err.println("invalid entrance dim " + currentEntranceDim);
            entry.currentEntranceDim = World.OVERWORLD;
            return;
        }
        
        boolean chunkLoaded = entranceWorld.isChunkLoaded(entry.currentEntrancePos);
        if (!chunkLoaded) {
            return;
        }
        
        boolean blocksValid = entry.getOuterAreaBox().stream().allMatch(blockPos ->
            entranceWorld.getBlockState(blockPos).getBlock() == ScaleBoxPlaceholderBlock.instance
        );
        
        if (!blocksValid) {
            entry.currentEntranceDim = null;
            record.setDirty(true);
            
            notifyPortalBreak(boxId);
        }
    }
    
    
}
