package qouteall.mini_scaled;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.qouteall.immersive_portals.Helper;
import com.qouteall.immersive_portals.api.IPDimensionAPI;
import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Blocks;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.util.registry.MutableRegistry;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.SimpleRegistry;
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

public class MiniScaledModInitializer implements ModInitializer {
    private static void initializeVoidDimension(
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
    
    @Override
    public void onInitialize() {
        
        IPDimensionAPI.onServerWorldInit.connect(MiniScaledModInitializer::initializeVoidDimension);
        
        ScaleBoxPlaceholderBlock.init();
        
        System.out.println("MiniScaled Mod Initializing");
    }
}
