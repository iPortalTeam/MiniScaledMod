package qouteall.mini_scaled;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.qouteall.immersive_portals.Helper;
import com.qouteall.immersive_portals.McHelper;
import com.qouteall.immersive_portals.api.IPDimensionAPI;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.SkyProperties;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.util.registry.MutableRegistry;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.util.registry.SimpleRegistry;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorLayer;
import net.minecraft.world.gen.chunk.StructuresConfig;

import java.util.Optional;

public class VoidDimension {
    public static final RegistryKey<World> dimensionId = RegistryKey.of(
        Registry.DIMENSION,
        new Identifier("mini_scaled:void")
    );
    
    static void initializeVoidDimension(
        GeneratorOptions generatorOptions, DynamicRegistryManager registryManager
    ) {
        SimpleRegistry<DimensionOptions> registry = generatorOptions.getDimensions();
        
        DimensionType dimType = registryManager.get(Registry.DIMENSION_TYPE_KEY).get(
            new Identifier("mini_scaled:void_dim_type")
        );
        
        if (dimType == null) {
            Helper.err("Missing dimension type mini_scaled:void_dim_type");
            return;
        }
        
        Identifier dimId = new Identifier("mini_scaled:void");
        
        IPDimensionAPI.addDimension(
            0,
            registry,
            dimId,
            () -> dimType,
            createVoidGenerator(registryManager)
        );
        IPDimensionAPI.markDimensionNonPersistent(dimId);
    }
    
    private static ChunkGenerator createVoidGenerator(DynamicRegistryManager rm) {
        MutableRegistry<Biome> biomeRegistry = rm.get(Registry.BIOME_KEY);
        
        StructuresConfig structuresConfig = new StructuresConfig(
            Optional.of(StructuresConfig.DEFAULT_STRONGHOLD),
            Maps.newHashMap(ImmutableMap.of())
        );
        FlatChunkGeneratorConfig flatChunkGeneratorConfig =
            new FlatChunkGeneratorConfig(structuresConfig, biomeRegistry);
        flatChunkGeneratorConfig.getLayers().add(new FlatChunkGeneratorLayer(1, Blocks.AIR));
        flatChunkGeneratorConfig.updateLayerBlocks();
        
        return new FlatChunkGenerator(flatChunkGeneratorConfig);
    }
    
    public static ServerWorld getVoidWorld() {
        return McHelper.getServerWorld(dimensionId);
    }
    
    @Environment(EnvType.CLIENT)
    public static class VoidSkyProperties extends SkyProperties {
        public VoidSkyProperties() {
            super(Float.NaN, true, SkyProperties.SkyType.NORMAL, false, false);
        }
        
        public Vec3d adjustFogColor(Vec3d color, float sunHeight) {
            return color.multiply((double)(sunHeight * 0.94F + 0.06F), (double)(sunHeight * 0.94F + 0.06F), (double)(sunHeight * 0.91F + 0.09F));
        }
        
        public boolean useThickFog(int camX, int camY) {
            return false;
        }
    }
}
