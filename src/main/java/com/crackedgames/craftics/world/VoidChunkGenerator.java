package com.crackedgames.craftics.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.FixedBiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
//? if <=1.21.1 {
import net.minecraft.world.gen.GenerationStep;
//?}
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class VoidChunkGenerator extends ChunkGenerator {

    public static final MapCodec<VoidChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            Biome.REGISTRY_CODEC.fieldOf("biome").forGetter(gen -> gen.biomeEntry)
        ).apply(instance, VoidChunkGenerator::new)
    );

    private final RegistryEntry<Biome> biomeEntry;

    public VoidChunkGenerator(RegistryEntry<Biome> biomeEntry) {
        super(new FixedBiomeSource(biomeEntry));
        this.biomeEntry = biomeEntry;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    @Override
    //? if <=1.21.1 {
    public void carve(ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig,
                      BiomeAccess biomeAccess, StructureAccessor structureAccessor,
                      Chunk chunk, GenerationStep.Carver carverStep) {
        // No carving in void world
    }
    //?} else {
    /*public void carve(ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig,
                      BiomeAccess biomeAccess, StructureAccessor structureAccessor,
                      Chunk chunk) {
        // No carving in void world
    }
    *///?}

    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structures,
                             NoiseConfig noiseConfig, Chunk chunk) {
        // No surface in void world
    }

    @Override
    public void populateEntities(ChunkRegion region) {
        // No entity spawning in void world
    }

    @Override
    public int getWorldHeight() {
        return 384;
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Blender blender,
                                                   NoiseConfig noiseConfig,
                                                   StructureAccessor structureAccessor,
                                                   Chunk chunk) {
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getSeaLevel() {
        return -63;
    }

    @Override
    public int getMinimumY() {
        return -64;
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world,
                         NoiseConfig noiseConfig) {
        return 0;
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world,
                                                NoiseConfig noiseConfig) {
        return new VerticalBlockSample(0, new BlockState[0]);
    }

    @Override
    public void getDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
        // No debug info
    }
}
