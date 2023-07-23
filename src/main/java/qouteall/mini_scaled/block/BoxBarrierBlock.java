package qouteall.mini_scaled.block;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.mini_scaled.ClientScaleBoxInteractionControl;
import qouteall.q_misc_util.my_util.MyTaskList;

public class BoxBarrierBlock extends Block {
    public static final BoxBarrierBlock instance = new BoxBarrierBlock(
        BlockBehaviour.Properties.of()
            .strength(-1.0F, 3600000.0F)
            .noLootTable().noOcclusion()
            .noCollission()
    );
    
    public static void init() {
        Registry.register(
            BuiltInRegistries.BLOCK,
            new ResourceLocation("mini_scaled", "barrier"),
            BoxBarrierBlock.instance
        );
    }
    
    public BoxBarrierBlock(Properties settings) {
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
    
    // cannot be broken by creative mode player
    @Override
    public void playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player){
        super.playerWillDestroy(world, pos, state, player);
        if (!player.level().isClientSide()) {
            IPGlobal.serverTaskList.addTask(MyTaskList.oneShotTask(() -> {
                world.setBlockAndUpdate(pos, state);
                player.displayClientMessage(Component.translatable("mini_scaled.cannot_break_barrier"), true);
            }));
        }
    }
}
