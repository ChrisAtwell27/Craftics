package com.crackedgames.craftics.api;

import com.crackedgames.craftics.api.registry.BiomePathRegistry;
import com.crackedgames.craftics.level.BiomePath;

/**
 * Registers Craftics' built-in Overworld / Nether / End progression paths into
 * {@link BiomePathRegistry}. Called once from {@code CrafticsMod.onInitialize()}.
 *
 * @since 0.2.0
 */
public final class VanillaBiomePaths {

    private VanillaBiomePaths() {}

    /** Register every built-in biome path. */
    public static void registerAll() {
        BiomePathRegistry.register(BiomePathEntry.builder("overworld")
            .displayName("Overworld").biomes(BiomePath.getPath(0)).build());
        BiomePathRegistry.register(BiomePathEntry.builder("nether")
            .displayName("The Nether").biomes(BiomePath.getNetherPath()).build());
        BiomePathRegistry.register(BiomePathEntry.builder("end")
            .displayName("The End").biomes(BiomePath.getEndPath()).build());
    }
}
