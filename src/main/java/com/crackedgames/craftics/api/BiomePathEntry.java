package com.crackedgames.craftics.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Immutable definition of a named biome progression path — an ordered sequence of
 * biome ids forming a themed "realm" the player advances through.
 *
 * <p>Craftics registers its built-in Overworld / Nether / End paths at startup
 * ({@code VanillaBiomePaths}); addons register their own via
 * {@code CrafticsAPI.registerBiomePath} or a {@code craftics/paths/} datapack.
 *
 * <p>Note: Craftics already orders any registered biome into the run by its biome
 * {@code order} value, so a path is primarily a grouping/metadata construct — an addon
 * biome does not need a path to be playable.
 *
 * @param id          unique path id, e.g. {@code "mymod:aether"}
 * @param displayName name shown to players
 * @param biomeIds    ordered biome ids that make up the path
 * @since 0.2.0
 */
public record BiomePathEntry(
    String id,
    String displayName,
    List<String> biomeIds
) {
    public BiomePathEntry {
        biomeIds = List.copyOf(biomeIds);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** Fluent builder for {@link BiomePathEntry}. */
    public static class Builder {
        private final String id;
        private String displayName;
        private final List<String> biomeIds = new ArrayList<>();

        public Builder(String id) {
            this.id = id;
            this.displayName = id;
        }

        /** Name shown to players. Defaults to the id. */
        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        /** Append one biome id to the path. */
        public Builder biome(String biomeId) {
            this.biomeIds.add(biomeId);
            return this;
        }

        /** Append every biome id in {@code ids} to the path, in order. */
        public Builder biomes(Collection<String> ids) {
            this.biomeIds.addAll(ids);
            return this;
        }

        public BiomePathEntry build() {
            return new BiomePathEntry(id, displayName, biomeIds);
        }
    }
}
