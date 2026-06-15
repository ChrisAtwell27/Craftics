package com.crackedgames.craftics.data;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.api.registry.AllyEntry;
import com.crackedgames.craftics.api.registry.AllyRegistry;
import com.google.gson.JsonObject;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Loads combat ally definitions from JSON datapacks into {@link AllyRegistry}.
 *
 * <p>Path: {@code data/<namespace>/craftics/allies/*.json}
 *
 * <pre>{@code
 * {
 *   "entity": "minecraft:wolf",
 *   "hp": 12, "attack": 4, "defense": 1, "range": 1, "speed": 3,
 *   "recruit_mode": "TAMED",
 *   "scales_with_owner_gear": true,
 *   "heal_item": "minecraft:bone",
 *   "heal_amount": 6
 * }
 * }</pre>
 *
 * <p>{@code entity} is required; {@code recruit_mode} accepts any
 * {@link AllyEntry.RecruitMode} name (default {@code TAMED}). Datapack allies use the
 * default melee AI and have no per-round hook - allies needing custom behavior must be
 * registered through {@code CrafticsAPI.registerAlly}.
 *
 * @since 0.2.0
 */
public final class AllyJsonLoader extends CrafticsDataLoader<AllyEntry> {

    public AllyJsonLoader() {
        super("craftics/allies", "ally");
    }

    @Override
    protected AllyEntry parse(Identifier fileId, JsonObject json) {
        if (!json.has("entity")) {
            CrafticsMod.LOGGER.warn("Ally JSON {} missing required 'entity' - skipping", fileId);
            return null;
        }
        String entity = json.get("entity").getAsString();
        if (!Registries.ENTITY_TYPE.containsId(Identifier.of(entity))) {
            CrafticsMod.LOGGER.warn("Unknown entity type '{}' in ally JSON {} - skipping", entity, fileId);
            return null;
        }

        AllyEntry.Builder builder = AllyEntry.builder(entity);
        if (json.has("hp")) builder.hp(json.get("hp").getAsInt());
        if (json.has("attack")) builder.attack(json.get("attack").getAsInt());
        if (json.has("defense")) builder.defense(json.get("defense").getAsInt());
        if (json.has("range")) builder.range(json.get("range").getAsInt());
        if (json.has("speed")) builder.speed(json.get("speed").getAsInt());
        if (json.has("scales_with_owner_gear")) {
            builder.scalesWithOwnerGear(json.get("scales_with_owner_gear").getAsBoolean());
        }
        if (json.has("recruit_mode")) {
            String mode = json.get("recruit_mode").getAsString();
            try {
                builder.recruitMode(AllyEntry.RecruitMode.valueOf(mode.toUpperCase()));
            } catch (IllegalArgumentException e) {
                CrafticsMod.LOGGER.warn("Unknown recruit mode '{}' in ally JSON {} - using TAMED",
                    mode, fileId);
            }
        }
        if (json.has("heal_item")) {
            String healItemStr = json.get("heal_item").getAsString();
            Identifier healItemId = Identifier.of(healItemStr);
            if (Registries.ITEM.containsId(healItemId)) {
                int amount = json.has("heal_amount") ? json.get("heal_amount").getAsInt() : 0;
                builder.healItem(Registries.ITEM.get(healItemId), amount);
            } else {
                CrafticsMod.LOGGER.warn("Unknown heal item '{}' in ally JSON {} - ignoring",
                    healItemStr, fileId);
            }
        }
        return builder.build();
    }

    @Override
    protected void register(AllyEntry parsed, RegistrationSource source) {
        AllyRegistry.register(parsed, source);
    }

    @Override
    protected void clearDatapackEntries() {
        AllyRegistry.clearDatapackEntries();
    }
}
