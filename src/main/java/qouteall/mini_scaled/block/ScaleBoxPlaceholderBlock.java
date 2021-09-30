package qouteall.mini_scaled.block;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Material;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.mini_scaled.ScaleBoxEntranceItem;
import qouteall.q_misc_util.my_util.MyTaskList;

public class ScaleBoxPlaceholderBlock extends BlockWithEntity {
    public static final ScaleBoxPlaceholderBlock instance = new ScaleBoxPlaceholderBlock(
        AbstractBlock.Settings.of(Material.BARRIER)
            .strength(0.3F)
            .dropsNothing().nonOpaque()
            .noCollision()
    );
    
    public static void init() {
        Registry.register(
            Registry.BLOCK,
            new Identifier("mini_scaled", "scale_box_placeholder"),
            ScaleBoxPlaceholderBlock.instance
        );
    }
    
    private ScaleBoxPlaceholderBlock(Settings settings) {
        super(settings);
    }
    
    public boolean isTranslucent(BlockState state, BlockView world, BlockPos pos) {
        return true;
    }
    
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }
    
    @Environment(EnvType.CLIENT)
    public float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) {
        return 1.0F;
    }
    
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (newState.getBlock() != this) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity != null) {
                ScaleBoxPlaceholderBlockEntity be = (ScaleBoxPlaceholderBlockEntity) blockEntity;
                int boxId = be.boxId;
                IPGlobal.serverTaskList.addTask(MyTaskList.oneShotTask(() -> {
                    ScaleBoxPlaceholderBlockEntity.checkBlockIntegrity(boxId);
                }));
                ItemStack itemToDrop = ScaleBoxEntranceItem.boxIdToItem(boxId);
                if (itemToDrop != null) {
                    ItemScatterer.spawn(
                        world, pos.getX(), pos.getY(), pos.getZ(), itemToDrop
                    );
                }
            }
        }
        
        super.onStateReplaced(state, world, pos, newState, moved);
    }
    
    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ScaleBoxPlaceholderBlockEntity(pos, state);
    }
    
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return checkType(
            type, ScaleBoxPlaceholderBlockEntity.blockEntityType,
            ScaleBoxPlaceholderBlockEntity::staticTick
        );
    }
    
}
