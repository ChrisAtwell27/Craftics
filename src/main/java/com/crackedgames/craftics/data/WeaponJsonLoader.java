package com.crackedgames.craftics.data;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.Abilities;
import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.api.WeaponAbilityHandler;
import com.crackedgames.craftics.api.registry.WeaponEntry;
import com.crackedgames.craftics.api.registry.WeaponRegistry;
import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.DamageType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Loads weapon definitions from JSON datapacks into {@link WeaponRegistry}.
 *
 * <p>Path: {@code data/<namespace>/craftics/weapons/*.json}
 *
 * <pre>{@code
 * {
 *   "item": "minecraft:diamond_sword",
 *   "damage_type": "slashing",
 *   "attack_power": 7,
 *   "ap_cost": 1,
 *   "range": 1,
 *   "ranged": false,
 *   "break_chance": 0.0,
 *   "ability": [
 *     { "kind": "bleed" },
 *     { "kind": "sweep", "base_chance": 0.1, "bonus_per_point": 0.05 }
 *   ]
 * }
 * }</pre>
 *
 * <p>{@code damage_type} accepts any {@link DamageType} name. {@code ability} is an
 * ordered list of building blocks (see {@link #parseAbility}); anything needing
 * scripted logic must be registered through {@code CrafticsAPI.registerWeapon}.
 *
 * @since 0.2.0
 */
public final class WeaponJsonLoader extends CrafticsDataLoader<WeaponEntry> {

    public WeaponJsonLoader() {
        super("craftics/weapons", "weapon");
    }

    @Override
    protected WeaponEntry parse(Identifier fileId, JsonObject json) {
        String itemStr = json.get("item").getAsString();
        Identifier itemId = Identifier.of(itemStr);
        if (!Registries.ITEM.containsId(itemId)) {
            CrafticsMod.LOGGER.warn("Unknown item '{}' in weapon JSON {} — skipping", itemStr, fileId);
            return null;
        }
        Item item = Registries.ITEM.get(itemId);

        WeaponEntry.Builder builder = WeaponEntry.builder(item);
        if (json.has("damage_type")) {
            builder.damageType(DamageType.valueOf(json.get("damage_type").getAsString().toUpperCase()));
        }
        if (json.has("attack_power")) builder.attackPower(json.get("attack_power").getAsInt());
        if (json.has("ap_cost")) builder.apCost(json.get("ap_cost").getAsInt());
        if (json.has("range")) builder.range(json.get("range").getAsInt());
        if (json.has("ranged")) builder.ranged(json.get("ranged").getAsBoolean());
        if (json.has("break_chance")) builder.breakChance(json.get("break_chance").getAsDouble());

        if (json.has("ability")) {
            WeaponAbilityHandler chain = null;
            for (JsonElement element : json.getAsJsonArray("ability")) {
                WeaponAbilityHandler block = parseAbility(element.getAsJsonObject(), fileId);
                if (block == null) continue;
                chain = (chain == null) ? block : chain.and(block);
            }
            if (chain != null) builder.ability(chain);
        }
        return builder.build();
    }

    /** Map one {@code "ability"} JSON object to an {@link Abilities} building block. */
    private WeaponAbilityHandler parseAbility(JsonObject obj, Identifier fileId) {
        String kind = obj.get("kind").getAsString();
        return switch (kind) {
            case "bleed" -> Abilities.bleed();
            case "pierce" -> Abilities.pierce();
            case "sweep" -> Abilities.sweepAdjacent(
                getDouble(obj, "base_chance", 0.10), getDouble(obj, "bonus_per_point", 0.05));
            case "armor_ignore" -> Abilities.armorIgnore(
                getDouble(obj, "base_chance", 0.10), getDouble(obj, "bonus_per_point", 0.05));
            case "stun" -> Abilities.stun(
                getDouble(obj, "base_chance", 0.10), getDouble(obj, "bonus_per_point", 0.05));
            case "knockback" -> Abilities.knockbackDirection(getInt(obj, "distance", 1));
            case "aoe" -> Abilities.aoe(getInt(obj, "radius", 1), getDouble(obj, "damage_multiplier", 0.5));
            case "fire_damage" -> Abilities.fireDamage(getInt(obj, "bonus_damage", 2));
            case "apply_effect" -> Abilities.applyEffect(
                CombatEffects.EffectType.valueOf(obj.get("effect").getAsString().toUpperCase()),
                getInt(obj, "turns", 3), getInt(obj, "amplifier", 0));
            default -> {
                CrafticsMod.LOGGER.warn("Unknown weapon ability kind '{}' in {} — skipping", kind, fileId);
                yield null;
            }
        };
    }

    private static double getDouble(JsonObject o, String key, double fallback) {
        return o.has(key) ? o.get(key).getAsDouble() : fallback;
    }

    private static int getInt(JsonObject o, String key, int fallback) {
        return o.has(key) ? o.get(key).getAsInt() : fallback;
    }

    @Override
    protected void register(WeaponEntry parsed, RegistrationSource source) {
        WeaponRegistry.register(parsed.item(), parsed, source);
    }

    @Override
    protected void clearDatapackEntries() {
        WeaponRegistry.clearDatapackEntries();
    }
}
