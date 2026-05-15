package com.crackedgames.craftics.data;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.api.registry.TrimMaterialEntry;
import com.crackedgames.craftics.api.registry.TrimMaterialRegistry;
import com.crackedgames.craftics.combat.TrimEffects;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;

/**
 * Loads armor trim material effects from JSON datapacks into {@link TrimMaterialRegistry}.
 *
 * <p>Path: {@code data/<namespace>/craftics/trim_materials/*.json}
 *
 * <pre>{@code
 * {
 *   "id": "iron",
 *   "stat": "DEFENSE",
 *   "value_per_piece": 1,
 *   "description": "+1 Defense per trimmed piece"
 * }
 * }</pre>
 *
 * <p>{@code stat} accepts any {@link TrimEffects.Bonus} name. {@code value_per_piece}
 * defaults to 1. {@code id} must match the trim material's registry path
 * (e.g. {@code minecraft:iron} → {@code "iron"}).
 *
 * @since 0.2.0
 */
public final class TrimMaterialJsonLoader extends CrafticsDataLoader<TrimMaterialEntry> {

    public TrimMaterialJsonLoader() {
        super("craftics/trim_materials", "trim material");
    }

    @Override
    protected TrimMaterialEntry parse(Identifier fileId, JsonObject json) {
        String id = json.get("id").getAsString();

        String statName = json.get("stat").getAsString();
        TrimEffects.Bonus stat;
        try {
            stat = TrimEffects.Bonus.valueOf(statName.toUpperCase());
        } catch (IllegalArgumentException e) {
            CrafticsMod.LOGGER.warn("Unknown trim stat '{}' in trim material JSON {} — skipping file",
                statName, fileId);
            return null;
        }

        int valuePerPiece = json.has("value_per_piece") ? json.get("value_per_piece").getAsInt() : 1;
        String description = json.has("description") ? json.get("description").getAsString() : "";

        return new TrimMaterialEntry(id, stat, valuePerPiece, description);
    }

    @Override
    protected void register(TrimMaterialEntry parsed, RegistrationSource source) {
        TrimMaterialRegistry.register(parsed, source);
    }

    @Override
    protected void clearDatapackEntries() {
        TrimMaterialRegistry.clearDatapackEntries();
    }
}
