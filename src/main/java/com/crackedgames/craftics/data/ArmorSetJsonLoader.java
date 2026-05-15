package com.crackedgames.craftics.data;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.api.registry.ArmorSetEntry;
import com.crackedgames.craftics.api.registry.ArmorSetRegistry;
import com.crackedgames.craftics.combat.DamageType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;

/**
 * Loads armor set bonus definitions from JSON datapacks into {@link ArmorSetRegistry}.
 *
 * <p>Path: {@code data/<namespace>/craftics/armor_sets/*.json}
 *
 * <pre>{@code
 * {
 *   "id": "mythril",
 *   "description": "Mythril Set: +3 Special, +2 Defense",
 *   "damage_bonuses": [
 *     { "type": "SPECIAL", "amount": 3 },
 *     { "type": "WATER", "amount": 1 }
 *   ],
 *   "all_damage_bonus": 0,
 *   "speed_bonus": 0,
 *   "ap_bonus": 0,
 *   "defense_bonus": 2,
 *   "attack_bonus": 0,
 *   "ap_cost_reduction": 0
 * }
 * }</pre>
 *
 * <p>The {@code id} must match the armor-set name {@code PlayerCombatStats.getArmorSet()}
 * derives from a player's equipped armor. {@code all_damage_bonus} is applied first, so
 * any per-type {@code damage_bonuses} entry overrides it for that type.
 *
 * @since 0.2.0
 */
public final class ArmorSetJsonLoader extends CrafticsDataLoader<ArmorSetEntry> {

    public ArmorSetJsonLoader() {
        super("craftics/armor_sets", "armor set");
    }

    @Override
    protected ArmorSetEntry parse(Identifier fileId, JsonObject json) {
        ArmorSetEntry.Builder builder = ArmorSetEntry.builder(json.get("id").getAsString());

        if (json.has("description")) builder.description(json.get("description").getAsString());
        if (json.has("all_damage_bonus")) builder.allDamageBonus(json.get("all_damage_bonus").getAsInt());

        if (json.has("damage_bonuses")) {
            for (JsonElement element : json.getAsJsonArray("damage_bonuses")) {
                JsonObject obj = element.getAsJsonObject();
                String typeName = obj.get("type").getAsString();
                DamageType type;
                try {
                    type = DamageType.valueOf(typeName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    CrafticsMod.LOGGER.warn("Unknown damage type '{}' in armor set JSON {} — skipping bonus",
                        typeName, fileId);
                    continue;
                }
                builder.damageBonus(type, obj.has("amount") ? obj.get("amount").getAsInt() : 0);
            }
        }

        if (json.has("speed_bonus")) builder.speedBonus(json.get("speed_bonus").getAsInt());
        if (json.has("ap_bonus")) builder.apBonus(json.get("ap_bonus").getAsInt());
        if (json.has("defense_bonus")) builder.defenseBonus(json.get("defense_bonus").getAsInt());
        if (json.has("attack_bonus")) builder.attackBonus(json.get("attack_bonus").getAsInt());
        if (json.has("ap_cost_reduction")) builder.apCostReduction(json.get("ap_cost_reduction").getAsInt());

        return builder.build();
    }

    @Override
    protected void register(ArmorSetEntry parsed, RegistrationSource source) {
        ArmorSetRegistry.register(parsed, source);
    }

    @Override
    protected void clearDatapackEntries() {
        ArmorSetRegistry.clearDatapackEntries();
    }
}
