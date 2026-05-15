package com.crackedgames.craftics.data;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.EnchantmentEffectHandler;
import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.api.registry.EnchantmentRegistry;
import com.crackedgames.craftics.combat.TrimEffects;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads declarative stat-bonus enchantments from JSON datapacks into
 * {@link EnchantmentRegistry}.
 *
 * <p>Path: {@code data/<namespace>/craftics/enchantments/*.json}
 *
 * <pre>{@code
 * {
 *   "enchantment": "minecraft:protection",
 *   "bonuses": [
 *     { "stat": "DEFENSE", "amount": 1, "per_levels": 2 }
 *   ]
 * }
 * }</pre>
 *
 * <p>Each bonus grants {@code (enchantLevel / per_levels) * amount} of a
 * {@link TrimEffects.Bonus} stat. {@code per_levels} defaults to 1 (i.e. {@code amount}
 * per level). Enchantments that modify weapon abilities (Sharpness, Smite, …) are
 * handled by weapon ability handlers, not this loader.
 *
 * @since 0.2.0
 */
public final class EnchantmentJsonLoader extends CrafticsDataLoader<EnchantmentJsonLoader.Parsed> {

    /** One stat bonus an enchantment grants, scaled by enchant level. */
    private record StatBonus(TrimEffects.Bonus stat, int amount, int perLevels) {}

    /** A parsed enchantment definition: its id and the compiled effect handler. */
    record Parsed(String enchantmentId, EnchantmentEffectHandler handler) {}

    public EnchantmentJsonLoader() {
        super("craftics/enchantments", "enchantment");
    }

    @Override
    protected Parsed parse(Identifier fileId, JsonObject json) {
        String enchantId = json.get("enchantment").getAsString();

        List<StatBonus> bonuses = new ArrayList<>();
        if (json.has("bonuses")) {
            for (JsonElement element : json.getAsJsonArray("bonuses")) {
                JsonObject obj = element.getAsJsonObject();
                String statName = obj.get("stat").getAsString();
                TrimEffects.Bonus stat;
                try {
                    stat = TrimEffects.Bonus.valueOf(statName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    CrafticsMod.LOGGER.warn("Unknown stat '{}' in enchantment JSON {} — skipping bonus",
                        statName, fileId);
                    continue;
                }
                int amount = obj.has("amount") ? obj.get("amount").getAsInt() : 1;
                int perLevels = obj.has("per_levels") ? Math.max(1, obj.get("per_levels").getAsInt()) : 1;
                bonuses.add(new StatBonus(stat, amount, perLevels));
            }
        }

        List<StatBonus> finalBonuses = List.copyOf(bonuses);
        EnchantmentEffectHandler handler = ctx -> {
            for (StatBonus b : finalBonuses) {
                ctx.getModifiers().add(b.stat(), (ctx.getLevel() / b.perLevels()) * b.amount());
            }
        };
        return new Parsed(enchantId, handler);
    }

    @Override
    protected void register(Parsed parsed, RegistrationSource source) {
        EnchantmentRegistry.register(parsed.enchantmentId(), parsed.handler(), source);
    }

    @Override
    protected void clearDatapackEntries() {
        EnchantmentRegistry.clearDatapackEntries();
    }
}
