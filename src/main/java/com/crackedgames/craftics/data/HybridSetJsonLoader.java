package com.crackedgames.craftics.data;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.api.registry.HybridSetEntry;
import com.crackedgames.craftics.api.registry.HybridSetRegistry;
import com.crackedgames.craftics.combat.HybridEffect;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;

/**
 * Loads hybrid armor set definitions from JSON datapacks into {@link HybridSetRegistry}.
 *
 * <p>Path: {@code data/<namespace>/craftics/hybrid_sets/*.json}
 *
 * <pre>{@code
 * {
 *   "material_a": "iron",
 *   "material_b": "diamond",
 *   "class_name": "Warlord",
 *   "description": "Signature mechanic text shown on the armor tooltip.",
 *   "effect": "WARLORD"
 * }
 * }</pre>
 *
 * <p>{@code material_a}, {@code material_b}, and {@code effect} are required;
 * {@code effect} accepts any {@link HybridEffect} name. The material pair is unordered
 * — {iron, diamond} and {diamond, iron} are the same hybrid.
 *
 * @since 0.2.0
 */
public final class HybridSetJsonLoader extends CrafticsDataLoader<HybridSetEntry> {

    public HybridSetJsonLoader() {
        super("craftics/hybrid_sets", "hybrid set");
    }

    @Override
    protected HybridSetEntry parse(Identifier fileId, JsonObject json) {
        if (!json.has("material_a") || !json.has("material_b") || !json.has("effect")) {
            CrafticsMod.LOGGER.warn(
                "Hybrid set JSON {} missing required 'material_a', 'material_b', or 'effect' — skipping",
                fileId);
            return null;
        }

        String effectName = json.get("effect").getAsString();
        HybridEffect effect;
        try {
            effect = HybridEffect.valueOf(effectName.toUpperCase());
        } catch (IllegalArgumentException e) {
            CrafticsMod.LOGGER.warn("Unknown hybrid effect '{}' in {} — skipping", effectName, fileId);
            return null;
        }

        HybridSetEntry.Builder builder = HybridSetEntry.builder(
                json.get("material_a").getAsString(),
                json.get("material_b").getAsString())
            .effect(effect);
        if (json.has("class_name")) builder.className(json.get("class_name").getAsString());
        if (json.has("description")) builder.description(json.get("description").getAsString());
        return builder.build();
    }

    @Override
    protected void register(HybridSetEntry parsed, RegistrationSource source) {
        HybridSetRegistry.register(parsed, source);
    }

    @Override
    protected void clearDatapackEntries() {
        HybridSetRegistry.clearDatapackEntries();
    }
}
