package qouteall.mini_scaled.block;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Material;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.mini_scaled.ClientScaleBoxInteractionControl;
import qouteall.mini_scaled.MiniScaledPortal;
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
            Registries.BLOCK,
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
        if (!world.isClient()) {
            if (newState.getBlock() != this) {
                BlockEntity blockEntity = world.getBlockEntity(pos);
                if (blockEntity != null) {
                    ScaleBoxPlaceholderBlockEntity be = (ScaleBoxPlaceholderBlockEntity) blockEntity;
                    int boxId = be.boxId;
                    IPGlobal.serverTaskList.addTask(MyTaskList.oneShotTask(() -> {
                        ScaleBoxPlaceholderBlockEntity.checkShouldRemovePortals(boxId, ((ServerWorld) world), pos);
                    }));
                    be.dropItemIfNecessary();
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
    
    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (world instanceof World world1) {
            if (world1.isClient()) {
                if (ClientScaleBoxInteractionControl.canInteractInsideScaleBox()) {
                    return VoxelShapes.empty();
                }
            }
        }
        
        return VoxelShapes.fullCube();
    }
    
}
