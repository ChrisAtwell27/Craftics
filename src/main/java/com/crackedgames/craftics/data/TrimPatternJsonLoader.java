package com.crackedgames.craftics.data;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.api.registry.TrimPatternEntry;
import com.crackedgames.craftics.api.registry.TrimPatternRegistry;
import com.crackedgames.craftics.combat.TrimEffects;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;

/**
 * Loads armor trim pattern effects from JSON datapacks into {@link TrimPatternRegistry}.
 *
 * <p>Path: {@code data/<namespace>/craftics/trim_patterns/*.json}
 *
 * <pre>{@code
 * {
 *   "id": "sentry",
 *   "per_piece_stat": "RANGED_POWER",
 *   "per_piece_description": "+1 Ranged Power per trimmed piece",
 *   "set_bonus": "OVERWATCH",
 *   "set_bonus_name": "Overwatch",
 *   "set_bonus_description": "Counter-attack ranged enemies"
 * }
 * }</pre>
 *
 * <p>{@code per_piece_stat} accepts any {@link TrimEffects.Bonus} name; {@code set_bonus}
 * any {@link TrimEffects.SetBonus} name. {@code id} must match the trim pattern's
 * registry path (e.g. {@code minecraft:sentry} → {@code "sentry"}).
 *
 * @since 0.2.0
 */
public final class TrimPatternJsonLoader extends CrafticsDataLoader<TrimPatternEntry> {

    public TrimPatternJsonLoader() {
        super("craftics/trim_patterns", "trim pattern");
    }

    @Override
    protected TrimPatternEntry parse(Identifier fileId, JsonObject json) {
        String id = json.get("id").getAsString();

        TrimEffects.Bonus perPieceStat = null;
        if (json.has("per_piece_stat")) {
            String name = json.get("per_piece_stat").getAsString();
            try {
                perPieceStat = TrimEffects.Bonus.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                CrafticsMod.LOGGER.warn("Unknown trim stat '{}' in {} — pattern gets no per-piece bonus",
                    name, fileId);
            }
        }

        TrimEffects.SetBonus setBonus = TrimEffects.SetBonus.NONE;
        if (json.has("set_bonus")) {
            String name = json.get("set_bonus").getAsString();
            try {
                setBonus = TrimEffects.SetBonus.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                CrafticsMod.LOGGER.warn("Unknown set bonus '{}' in {} — defaulting to NONE", name, fileId);
            }
        }

        String perPieceDesc = json.has("per_piece_description")
            ? json.get("per_piece_description").getAsString() : "";
        String setName = json.has("set_bonus_name")
            ? json.get("set_bonus_name").getAsString() : "";
        String setDesc = json.has("set_bonus_description")
            ? json.get("set_bonus_description").getAsString() : "";

        return new TrimPatternEntry(id, perPieceStat, perPieceDesc, setBonus, setName, setDesc);
    }

    @Override
    protected void register(TrimPatternEntry parsed, RegistrationSource source) {
        TrimPatternRegistry.register(parsed, source);
    }

    @Override
    protected void clearDatapackEntries() {
        TrimPatternRegistry.clearDatapackEntries();
    }
}
