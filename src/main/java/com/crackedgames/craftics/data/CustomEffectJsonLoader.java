package com.crackedgames.craftics.data;

import com.crackedgames.craftics.api.CustomEffectDef;
import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.api.registry.CombatEffectRegistry;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;

/**
 * Loads custom status effects from JSON datapacks into {@link CombatEffectRegistry}.
 *
 * <p>Path: {@code data/<namespace>/craftics/effects/*.json}
 *
 * <pre>{@code
 * {
 *   "id": "mymod:frostbite",
 *   "name": "Frostbite",
 *   "description": "-2 HP per turn",
 *   "color": "§b",
 *   "harmful": true,
 *   "hp_change_per_turn": -2
 * }
 * }</pre>
 *
 * <p>Datapack effects are declarative — a flat per-turn HP change. Effects needing
 * scripted per-turn logic must be registered through {@code CrafticsAPI.registerEffect}
 * with a {@code CustomEffectTickHandler}.
 *
 * @since 0.2.0
 */
public final class CustomEffectJsonLoader extends CrafticsDataLoader<CustomEffectDef> {

    public CustomEffectJsonLoader() {
        super("craftics/effects", "status effect");
    }

    @Override
    protected CustomEffectDef parse(Identifier fileId, JsonObject json) {
        CustomEffectDef.Builder builder = CustomEffectDef.builder(json.get("id").getAsString());
        if (json.has("name")) builder.displayName(json.get("name").getAsString());
        if (json.has("description")) builder.description(json.get("description").getAsString());
        if (json.has("color")) builder.colorCode(json.get("color").getAsString());
        if (json.has("harmful")) builder.harmful(json.get("harmful").getAsBoolean());
        if (json.has("hp_change_per_turn")) {
            builder.hpChangePerTurn(json.get("hp_change_per_turn").getAsInt());
        }
        return builder.build();
    }

    @Override
    protected void register(CustomEffectDef parsed, RegistrationSource source) {
        CombatEffectRegistry.register(parsed, source);
    }

    @Override
    protected void clearDatapackEntries() {
        CombatEffectRegistry.clearDatapackEntries();
    }
}
