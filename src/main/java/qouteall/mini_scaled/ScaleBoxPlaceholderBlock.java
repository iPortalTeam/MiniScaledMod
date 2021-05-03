package qouteall.mini_scaled;

import com.qouteall.immersive_portals.portal.PortalPlaceholderBlock;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.BlockView;

public class ScaleBoxPlaceholderBlock extends Block {
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
    
}
