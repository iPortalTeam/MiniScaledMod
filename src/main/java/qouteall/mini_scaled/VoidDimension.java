package qouteall.mini_scaled;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.Lifecycle;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureSet;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorLayer;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.api.DimensionAPI;

import java.util.ArrayList;
import java.util.Optional;

public class VoidDimension {
    public static final RegistryKey<World> dimensionId = RegistryKey.of(
        RegistryKeys.WORLD,
        new Identifier("mini_scaled:void")
    );
    
    static void initializeVoidDimension(
        GeneratorOptions generatorOptions, DynamicRegistryManager registryManager
    ) {
        Registry<DimensionOptions> registry = registryManager.get(RegistryKeys.DIMENSION);
        
        RegistryEntry<DimensionType> dimType = registryManager.get(RegistryKeys.DIMENSION_TYPE).getEntry(
            RegistryKey.of(RegistryKeys.DIMENSION_TYPE, new Identifier("mini_scaled:void_dim_type"))
        ).orElseThrow(() -> new RuntimeException("Missing dimension type mini_scaled:void_dim_type"));
        
        Identifier dimId = new Identifier("mini_scaled:void");
        
        DimensionAPI.addDimension(
            registry, dimId, dimType,
            createVoidGenerator(registryManager)
        );
    }
    
    private static ChunkGenerator createVoidGenerator(DynamicRegistryManager rm) {
        Registry<Biome> biomeRegistry = rm.get(RegistryKeys.BIOME);
        
        RegistryEntry.Reference<Biome> plains = biomeRegistry.getEntry(BiomeKeys.PLAINS).orElseThrow();
        
        FlatChunkGeneratorConfig flatChunkGeneratorConfig =
            new FlatChunkGeneratorConfig(Optional.empty(), plains, new ArrayList<>());
        flatChunkGeneratorConfig.getLayers().add(new FlatChunkGeneratorLayer(1, Blocks.AIR));
        flatChunkGeneratorConfig.updateLayerBlocks();
        
        return new FlatChunkGenerator(
            flatChunkGeneratorConfig
        );
    }
    
    public static ServerWorld getVoidWorld() {
        return McHelper.getServerWorld(dimensionId);
    }
    
    @Environment(EnvType.CLIENT)
    public static class VoidSkyProperties extends DimensionEffects {
        public VoidSkyProperties() {
            super(Float.NaN, true, DimensionEffects.SkyType.NORMAL, false, false);
        }
        
        public Vec3d adjustFogColor(Vec3d color, float sunHeight) {
            return color.multiply((double) (sunHeight * 0.94F + 0.06F), (double) (sunHeight * 0.94F + 0.06F), (double) (sunHeight * 0.91F + 0.09F));
        }
        
        public boolean useThickFog(int camX, int camY) {
            return false;
        }
    }
}
