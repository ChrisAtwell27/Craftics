package com.crackedgames.craftics.data;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.ItemEffects;
import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.api.TargetType;
import com.crackedgames.craftics.api.UsableItemHandler;
import com.crackedgames.craftics.api.registry.UsableItemEntry;
import com.crackedgames.craftics.api.registry.UsableItemRegistry;
import com.crackedgames.craftics.combat.CombatEffects;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Loads usable-item definitions from JSON datapacks into {@link UsableItemRegistry}.
 *
 * <p>Path: {@code data/<namespace>/craftics/items/*.json}
 *
 * <pre>{@code
 * {
 *   "item": "minecraft:golden_apple",
 *   "ap_cost": 1,
 *   "range": 0,
 *   "target": "SELF",
 *   "consumed": true,
 *   "effects": [
 *     { "kind": "heal", "amount": 4 },
 *     { "kind": "apply_self", "effect": "ABSORPTION", "turns": 4, "amplifier": 0 }
 *   ]
 * }
 * }</pre>
 *
 * <p>{@code target} accepts any {@link TargetType} name. Each {@code effects} entry maps
 * to an {@link ItemEffects} building block via its {@code "kind"} (see {@link #parseEffect});
 * blocks run in order. Items needing scripted logic must be registered through
 * {@code CrafticsAPI.registerUsableItem}.
 *
 * @since 0.2.0
 */
public final class UsableItemJsonLoader extends CrafticsDataLoader<UsableItemEntry> {

    public UsableItemJsonLoader() {
        super("craftics/items", "usable item");
    }

    @Override
    protected UsableItemEntry parse(Identifier fileId, JsonObject json) {
        String itemStr = json.get("item").getAsString();
        Identifier itemId = Identifier.of(itemStr);
        if (!Registries.ITEM.containsId(itemId)) {
            CrafticsMod.LOGGER.warn("Unknown item '{}' in usable item JSON {} — skipping", itemStr, fileId);
            return null;
        }
        Item item = Registries.ITEM.get(itemId);

        UsableItemEntry.Builder builder = UsableItemEntry.builder(item);
        if (json.has("ap_cost")) builder.apCost(json.get("ap_cost").getAsInt());
        if (json.has("range")) builder.range(json.get("range").getAsInt());
        if (json.has("consumed")) builder.consumedOnUse(json.get("consumed").getAsBoolean());
        if (json.has("target")) {
            String target = json.get("target").getAsString();
            try {
                builder.targetType(TargetType.valueOf(target.toUpperCase()));
            } catch (IllegalArgumentException e) {
                CrafticsMod.LOGGER.warn("Unknown target type '{}' in usable item JSON {} — defaulting to SELF",
                    target, fileId);
            }
        }

        UsableItemHandler handler = null;
        if (json.has("effects")) {
            for (JsonElement element : json.getAsJsonArray("effects")) {
                UsableItemHandler block = parseEffect(element.getAsJsonObject(), fileId);
                if (block == null) continue;
                handler = (handler == null) ? block : handler.and(block);
            }
        }
        if (handler == null) {
            CrafticsMod.LOGGER.warn("Usable item JSON {} has no valid effects — skipping", fileId);
            return null;
        }
        builder.handler(handler);
        return builder.build();
    }

    /** Map one {@code "effects"} JSON object to an {@link ItemEffects} building block. */
    private UsableItemHandler parseEffect(JsonObject obj, Identifier fileId) {
        String kind = obj.get("kind").getAsString();
        return switch (kind) {
            case "heal" -> ItemEffects.heal(getInt(obj, "amount", 1));
            case "damage_target" -> ItemEffects.damageTarget(getInt(obj, "amount", 1));
            case "stun_target" -> ItemEffects.stunTarget();
            case "knockback_target" -> ItemEffects.knockbackTarget(getInt(obj, "distance", 1));
            case "aoe_damage" -> ItemEffects.aoeDamage(getInt(obj, "radius", 1), getInt(obj, "amount", 1));
            case "teleport" -> ItemEffects.teleportToTarget();
            case "place_tile" -> ItemEffects.placeTile(obj.get("tile").getAsString());
            case "message" -> ItemEffects.message(obj.get("text").getAsString());
            case "apply_self", "apply_target" -> {
                String effectName = obj.get("effect").getAsString();
                CombatEffects.EffectType type;
                try {
                    type = CombatEffects.EffectType.valueOf(effectName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    CrafticsMod.LOGGER.warn("Unknown effect '{}' in usable item JSON {} — skipping effect",
                        effectName, fileId);
                    yield null;
                }
                int turns = getInt(obj, "turns", 3);
                int amplifier = getInt(obj, "amplifier", 0);
                yield kind.equals("apply_self")
                    ? ItemEffects.applyToSelf(type, turns, amplifier)
                    : ItemEffects.applyToTarget(type, turns, amplifier);
            }
            default -> {
                CrafticsMod.LOGGER.warn("Unknown usable item effect kind '{}' in {} — skipping", kind, fileId);
                yield null;
            }
        };
    }

    private static int getInt(JsonObject obj, String key, int fallback) {
        return obj.has(key) ? obj.get(key).getAsInt() : fallback;
    }

    @Override
    protected void register(UsableItemEntry parsed, RegistrationSource source) {
        UsableItemRegistry.register(parsed.item(), parsed, source);
    }

    @Override
    protected void clearDatapackEntries() {
        UsableItemRegistry.clearDatapackEntries();
    }
}
