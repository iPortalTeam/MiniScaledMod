package qouteall.mini_scaled.block;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import qouteall.mini_scaled.ScaleBoxGeneration;
import qouteall.mini_scaled.ScaleBoxRecord;
import qouteall.mini_scaled.item.ScaleBoxEntranceItem;
import qouteall.q_misc_util.MiscHelper;
import qouteall.q_misc_util.my_util.IntBox;

public class ScaleBoxPlaceholderBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogManager.getLogger(ScaleBoxPlaceholderBlockEntity.class);
    
    public static BlockEntityType<ScaleBoxPlaceholderBlockEntity> blockEntityType;
    
    public static void init() {
        blockEntityType = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            "mini_scaled:placeholder_block_entity",
            FabricBlockEntityTypeBuilder.create(
                ScaleBoxPlaceholderBlockEntity::new,
                ScaleBoxPlaceholderBlock.INSTANCE
            ).build()
        );
    }
    
    public ScaleBoxPlaceholderBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }
    
    public int boxId;
    public boolean isBasePos = true;
    
    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        boxId = tag.getInt("boxId");
        if (tag.contains("isBasePos")) {
            isBasePos = tag.getBoolean("isBasePos");
        }
    }
    
    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("boxId", boxId);
        tag.putBoolean("isBasePos", isBasePos);
    }
    
    public void doTick() {
        if (level.isClientSide()) {
            return;
        }
        
        if (level.getGameTime() % 7 != 2) {
            return;
        }
        
        checkValidity();
    }
    
    public static void staticTick(Level world, BlockPos pos, BlockState state, ScaleBoxPlaceholderBlockEntity blockEntity) {
        blockEntity.doTick();
    }
    
    public void checkValidity() {
        ScaleBoxRecord.Entry entry = ScaleBoxRecord.get(level.getServer()).getEntryById(boxId);
        
        if (entry == null) {
            LOGGER.info("invalid box with id {}", boxId);
            destroyBlockAndBlockEntity();
            return;
        }
        
        IntBox scaleBoxOuterArea = entry.getOuterAreaBox();
        
        boolean posEquals = scaleBoxOuterArea.contains(getBlockPos());
        if (!posEquals) {
            LOGGER.info("invalid box entrance position {} {} {}", boxId, getBlockPos(), entry.currentEntrancePos);
            destroyBlockAndBlockEntity();
            return;
        }
        
        boolean dimEquals = entry.currentEntranceDim == level.dimension();
        if (!dimEquals) {
            LOGGER.info("invalid box dim {} {} {}", boxId, level.dimension(), entry.currentEntranceDim);
            destroyBlockAndBlockEntity();
            return;
        }
    }
    
    private void destroyBlockAndBlockEntity() {
        Validate.isTrue(!level.isClientSide());
        LOGGER.info("destroy scale box {}", boxId);
        level.setBlockAndUpdate(getBlockPos(), Blocks.AIR.defaultBlockState());
        setRemoved();
        
        dropItemIfNecessary();
        
        // don't notifyPortalBreak()
    }
    
    public void dropItemIfNecessary() {
        if (isBasePos) {
            // the up-facing outer portal breaks. drop item
            ItemStack itemToDrop = ScaleBoxEntranceItem.boxIdToItem(level.getServer(), boxId);
            if (itemToDrop != null) {
                Containers.dropItemStack(
                    level, getBlockPos().getX() + 0.5, getBlockPos().getY() + 0.5, getBlockPos().getZ() + 0.5, itemToDrop
                );
            }
            
            // avoid dropping item twice
            isBasePos = false;
        }
    }
    
    private static void notifyPortalBreak(MinecraftServer server, int boxId) {
        ScaleBoxRecord record = ScaleBoxRecord.get(server);
        ScaleBoxRecord.Entry entry = record.getEntryById(boxId);
        if (entry != null) {
            entry.generation++;
            record.setDirty(true);
        }
    }
    
    /**
     * there are 2 cases
     * 1. placed the same entrance elsewhere, the old entrance should break
     * in this case this integrity check will pass.
     * the generation counter was already incremented and the old portals will break.
     * in {@link ScaleBoxPlaceholderBlockEntity#checkValidity()} it will break the blocks.
     * don't {@link ScaleBoxPlaceholderBlockEntity#notifyPortalBreak(MinecraftServer, int)} as it will break the new portals
     * <br>
     * 2. break the old entrance
     * in this case this integrity check will fail.
     * the generation counter will increment and the portal will break.
     * in {@link ScaleBoxPlaceholderBlockEntity#checkValidity()} it will break the blocks.
     * <br>
     * Now, the outer portal breaks but the inner portal turns to point to the void underneath.
     */
    public static void checkShouldRemovePortals(
        int boxId,
        ServerLevel world,
        BlockPos pos
    ) {
        MinecraftServer server = world.getServer();
        ScaleBoxRecord record = ScaleBoxRecord.get(server);
        ScaleBoxRecord.Entry entry = record.getEntryById(boxId);
        
        if (entry == null) {
            return;
        }
        
        ResourceKey<Level> currentEntranceDim = entry.currentEntranceDim;
        if (currentEntranceDim == null) {
            notifyPortalBreak(server, boxId);
            return;
        }
        
        ServerLevel entranceWorld = MiscHelper.getServer().getLevel(currentEntranceDim);
        if (entranceWorld == null) {
            LOGGER.info("invalid entrance dim {}", currentEntranceDim);
            entry.currentEntranceDim = Level.OVERWORLD;
            return;
        }
        
        boolean chunkLoaded = entranceWorld.hasChunkAt(entry.currentEntrancePos);
        if (!chunkLoaded) {
            return;
        }
        
        boolean blocksValid = entry.getOuterAreaBox().stream().allMatch(blockPos ->
            entranceWorld.getBlockState(blockPos).getBlock() == ScaleBoxPlaceholderBlock.INSTANCE
        );
        
        if (!blocksValid) {
            entry.currentEntranceDim = null;
            record.setDirty(true);
            
            notifyPortalBreak(server, boxId);
            
            ScaleBoxGeneration.createInnerPortalsPointingToVoidUnderneath(
                server, entry
            );
        }
    }
    
    
}
