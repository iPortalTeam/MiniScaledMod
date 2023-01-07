package qouteall.mini_scaled.block;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import qouteall.mini_scaled.ClientScaleBoxInteractionControl;
import qouteall.mini_scaled.MiniScaledPortal;

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
}
