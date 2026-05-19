package com.crackedgames.craftics.data;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.BiomePathEntry;
import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.api.registry.BiomePathRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;

/**
 * Loads biome progression paths from JSON datapacks into {@link BiomePathRegistry}.
 *
 * <p>Path: {@code data/<namespace>/craftics/paths/*.json}
 *
 * <pre>{@code
 * {
 *   "id": "mymod:aether",
 *   "display_name": "The Aether",
 *   "biomes": ["mymod:aether_meadow", "mymod:aether_dungeon", "mymod:aether_throne"]
 * }
 * }</pre>
 *
 * <p>{@code id} and {@code biomes} are required. The biome ids should match registered
 * biomes (built-in or from a {@code craftics/biomes/} datapack).
 *
 * @since 0.2.0
 */
public final class BiomePathJsonLoader extends CrafticsDataLoader<BiomePathEntry> {

    public BiomePathJsonLoader() {
        super("craftics/paths", "biome path");
    }

    @Override
    protected BiomePathEntry parse(Identifier fileId, JsonObject json) {
        if (!json.has("id") || !json.has("biomes")) {
            CrafticsMod.LOGGER.warn("Biome path JSON {} missing required 'id' or 'biomes' — skipping", fileId);
            return null;
        }
        BiomePathEntry.Builder builder = BiomePathEntry.builder(json.get("id").getAsString());
        if (json.has("display_name")) {
            builder.displayName(json.get("display_name").getAsString());
        }
        for (JsonElement element : json.getAsJsonArray("biomes")) {
            builder.biome(element.getAsString());
        }
        return builder.build();
    }

    @Override
    protected void register(BiomePathEntry parsed, RegistrationSource source) {
        BiomePathRegistry.register(parsed, source);
    }

    @Override
    protected void clearDatapackEntries() {
        BiomePathRegistry.clearDatapackEntries();
    }
}
