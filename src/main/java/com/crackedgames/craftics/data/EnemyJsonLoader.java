package com.crackedgames.craftics.data;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.api.registry.EnemyEntry;
import com.crackedgames.craftics.api.registry.EnemyRegistry;
import com.google.gson.JsonObject;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Loads enemy templates from JSON datapacks into {@link EnemyRegistry}.
 *
 * <p>Path: {@code data/<namespace>/craftics/enemies/*.json}
 *
 * <pre>{@code
 * {
 *   "id": "craftics:parched_husk",
 *   "entity": "minecraft:husk",
 *   "ai": "minecraft:skeleton",
 *   "hp": 10, "attack": 3, "defense": 1, "range": 1, "speed": 2
 * }
 * }</pre>
 *
 * <p>{@code id} and {@code entity} are required. {@code ai} defaults to {@code entity};
 * the stat fields use {@link EnemyEntry} defaults. Biome JSON references a loaded
 * template with {@code "enemy": "<id>"}.
 *
 * @since 0.2.0
 */
public final class EnemyJsonLoader extends CrafticsDataLoader<EnemyEntry> {

    public EnemyJsonLoader() {
        super("craftics/enemies", "enemy");
    }

    @Override
    protected EnemyEntry parse(Identifier fileId, JsonObject json) {
        if (!json.has("id") || !json.has("entity")) {
            CrafticsMod.LOGGER.warn("Enemy JSON {} missing required 'id' or 'entity' - skipping", fileId);
            return null;
        }
        String id = json.get("id").getAsString();
        String entity = json.get("entity").getAsString();

        Identifier entityId = Identifier.of(entity);
        if (!Registries.ENTITY_TYPE.containsId(entityId)) {
            CrafticsMod.LOGGER.warn("Unknown entity type '{}' in enemy JSON {} - skipping", entity, fileId);
            return null;
        }

        EnemyEntry.Builder builder = EnemyEntry.builder(id, entity);
        // 'ai' is not validated here - AIRegistry resolves it at runtime and falls
        // back to its default strategy for an unknown key.
        if (json.has("ai")) builder.ai(json.get("ai").getAsString());
        if (json.has("hp")) builder.hp(json.get("hp").getAsInt());
        if (json.has("attack")) builder.attack(json.get("attack").getAsInt());
        if (json.has("defense")) builder.defense(json.get("defense").getAsInt());
        if (json.has("range")) builder.range(json.get("range").getAsInt());
        if (json.has("speed")) builder.speed(json.get("speed").getAsInt());
        return builder.build();
    }

    @Override
    protected void register(EnemyEntry parsed, RegistrationSource source) {
        EnemyRegistry.register(parsed, source);
    }

    @Override
    protected void clearDatapackEntries() {
        EnemyRegistry.clearDatapackEntries();
    }
}
