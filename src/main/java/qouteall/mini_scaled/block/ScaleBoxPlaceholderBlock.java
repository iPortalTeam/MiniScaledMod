package qouteall.mini_scaled.block;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.mini_scaled.ClientScaleBoxInteractionControl;
import qouteall.q_misc_util.my_util.MyTaskList;

public class ScaleBoxPlaceholderBlock extends BaseEntityBlock {
    public static final ScaleBoxPlaceholderBlock instance = new ScaleBoxPlaceholderBlock(
        BlockBehaviour.Properties.of()
            .strength(0.3F)
            .noLootTable().noOcclusion()
            .noCollission()
    );
    
    public static void init() {
        Registry.register(
            BuiltInRegistries.BLOCK,
            new ResourceLocation("mini_scaled", "scale_box_placeholder"),
            ScaleBoxPlaceholderBlock.instance
        );
    }
    
    private ScaleBoxPlaceholderBlock(Properties settings) {
        super(settings);
    }
    
    public boolean propagatesSkylightDown(BlockState state, BlockGetter world, BlockPos pos) {
        return true;
    }
    
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
    
    @Environment(EnvType.CLIENT)
    public float getShadeBrightness(BlockState state, BlockGetter world, BlockPos pos) {
        return 1.0F;
    }
    
    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (!world.isClientSide()) {
            if (newState.getBlock() != this) {
                BlockEntity blockEntity = world.getBlockEntity(pos);
                if (blockEntity != null) {
                    ScaleBoxPlaceholderBlockEntity be = (ScaleBoxPlaceholderBlockEntity) blockEntity;
                    int boxId = be.boxId;
                    IPGlobal.serverTaskList.addTask(MyTaskList.oneShotTask(() -> {
                        ScaleBoxPlaceholderBlockEntity.checkShouldRemovePortals(boxId, ((ServerLevel) world), pos);
                    }));
                    be.dropItemIfNecessary();
                }
            }
        }
        
        
        super.onRemove(state, world, pos, newState, moved);
    }
    
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScaleBoxPlaceholderBlockEntity(pos, state);
    }
    
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(
            type, ScaleBoxPlaceholderBlockEntity.blockEntityType,
            ScaleBoxPlaceholderBlockEntity::staticTick
        );
    }
    
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (world instanceof Level world1) {
            if (world1.isClientSide()) {
                if (ClientScaleBoxInteractionControl.canInteractInsideScaleBox()) {
                    return Shapes.empty();
                }
            }
        }
        
        return Shapes.block();
    }
    
}
