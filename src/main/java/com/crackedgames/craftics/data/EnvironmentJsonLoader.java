package com.crackedgames.craftics.data;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.EnvironmentDef;
import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.api.registry.EnvironmentRegistry;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Loads arena environment themes from JSON datapacks into {@link EnvironmentRegistry}.
 *
 * <p>Path: {@code data/<namespace>/craftics/environments/*.json}
 *
 * <pre>{@code
 * {
 *   "id": "mymod:aether",
 *   "floor_block": "minecraft:quartz_block",
 *   "post_block": "minecraft:quartz_pillar",
 *   "light_block": "minecraft:sea_lantern",
 *   "decor_style": "snowy"
 * }
 * }</pre>
 *
 * <p>Only {@code id} is required; block fields fall back to plains defaults.
 * {@code decor_style} selects the arena flavor-obstacle style - a built-in environment
 * id (e.g. {@code "forest"}) reuses that environment's decorations; it defaults to this
 * environment's own id (no flavor obstacles).
 *
 * @since 0.2.0
 */
public final class EnvironmentJsonLoader extends CrafticsDataLoader<EnvironmentDef> {

    public EnvironmentJsonLoader() {
        super("craftics/environments", "environment");
    }

    @Override
    protected EnvironmentDef parse(Identifier fileId, JsonObject json) {
        EnvironmentDef.Builder builder = EnvironmentDef.builder(json.get("id").getAsString());
        Block floor = block(json, "floor_block", fileId);
        if (floor != null) builder.floorBlock(floor);
        Block post = block(json, "post_block", fileId);
        if (post != null) builder.postBlock(post);
        Block light = block(json, "light_block", fileId);
        if (light != null) builder.lightBlock(light);
        if (json.has("decor_style")) builder.decorStyle(json.get("decor_style").getAsString());
        return builder.build();
    }

    /** Resolve an optional block field, or {@code null} (logged) if absent/unknown. */
    @Nullable
    private static Block block(JsonObject json, String key, Identifier fileId) {
        if (!json.has(key)) return null;
        String blockStr = json.get(key).getAsString();
        Identifier blockId = Identifier.of(blockStr);
        if (!Registries.BLOCK.containsId(blockId)) {
            CrafticsMod.LOGGER.warn("Unknown block '{}' for '{}' in environment JSON {} - using default",
                blockStr, key, fileId);
            return null;
        }
        return Registries.BLOCK.get(blockId);
    }

    @Override
    protected void register(EnvironmentDef parsed, RegistrationSource source) {
        EnvironmentRegistry.register(parsed, source);
    }

    @Override
    protected void clearDatapackEntries() {
        EnvironmentRegistry.clearDatapackEntries();
    }
}
