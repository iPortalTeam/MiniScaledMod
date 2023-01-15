package qouteall.mini_scaled.block;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.mini_scaled.ClientScaleBoxInteractionControl;
import qouteall.mini_scaled.MiniScaledPortal;
import qouteall.q_misc_util.my_util.MyTaskList;

public class BoxBarrierBlock extends Block {
    public static final BoxBarrierBlock instance = new BoxBarrierBlock(
        AbstractBlock.Settings.of(Material.BARRIER)
            .strength(-1.0F, 3600000.0F)
            .dropsNothing().nonOpaque()
            .noCollision()
    );
    
    public static void init() {
        Registry.register(
            Registries.BLOCK,
            new Identifier("mini_scaled", "barrier"),
            BoxBarrierBlock.instance
        );
    }
    
    public BoxBarrierBlock(Settings settings) {
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
    
    // cannot be broken by creative mode player
    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player){
        super.onBreak(world, pos, state, player);
        if (!player.world.isClient()) {
            IPGlobal.serverTaskList.addTask(MyTaskList.oneShotTask(() -> {
                world.setBlockState(pos, state);
                player.sendMessage(Text.translatable("mini_scaled.cannot_break_barrier"), true);
            }));
        }
    }
}
