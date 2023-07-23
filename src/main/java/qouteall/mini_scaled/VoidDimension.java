package qouteall.mini_scaled;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.api.DimensionAPI;

import java.util.ArrayList;
import java.util.Optional;

public class VoidDimension {
    public static final ResourceKey<Level> dimensionId = ResourceKey.create(
        Registries.DIMENSION,
        new ResourceLocation("mini_scaled:void")
    );
    
    static void initializeVoidDimension(
        WorldOptions generatorOptions, RegistryAccess registryManager
    ) {
        Registry<LevelStem> registry = registryManager.registryOrThrow(Registries.LEVEL_STEM);
        
        Holder<DimensionType> dimType = registryManager.registryOrThrow(Registries.DIMENSION_TYPE).getHolder(
            ResourceKey.create(Registries.DIMENSION_TYPE, new ResourceLocation("mini_scaled:void_dim_type"))
        ).orElseThrow(() -> new RuntimeException("Missing dimension type mini_scaled:void_dim_type"));
        
        ResourceLocation dimId = new ResourceLocation("mini_scaled:void");
        
        DimensionAPI.addDimension(
            registry, dimId, dimType,
            createVoidGenerator(registryManager)
        );
    }
    
    private static ChunkGenerator createVoidGenerator(RegistryAccess rm) {
        Registry<Biome> biomeRegistry = rm.registryOrThrow(Registries.BIOME);
        
        Holder.Reference<Biome> plains = biomeRegistry.getHolder(Biomes.PLAINS).orElseThrow();
        
        FlatLevelGeneratorSettings flatChunkGeneratorConfig =
            new FlatLevelGeneratorSettings(
                Optional.of(HolderSet.direct()), // disable structure generation
                plains,
                new ArrayList<>()
            );
        flatChunkGeneratorConfig.getLayersInfo().add(new FlatLayerInfo(1, Blocks.AIR));
        flatChunkGeneratorConfig.updateLayers();
        
        return new FlatLevelSource(
            flatChunkGeneratorConfig
        );
    }
    
    public static ServerLevel getVoidWorld() {
        return McHelper.getServerWorld(dimensionId);
    }
    
    @Environment(EnvType.CLIENT)
    public static class VoidSkyProperties extends DimensionSpecialEffects {
        public VoidSkyProperties() {
            super(Float.NaN, true, DimensionSpecialEffects.SkyType.NORMAL, false, false);
        }
        
        public Vec3 getBrightnessDependentFogColor(Vec3 color, float sunHeight) {
            return color.multiply((double) (sunHeight * 0.94F + 0.06F), (double) (sunHeight * 0.94F + 0.06F), (double) (sunHeight * 0.91F + 0.09F));
        }
        
        public boolean isFoggyAt(int camX, int camY) {
            return false;
        }
    }
}
