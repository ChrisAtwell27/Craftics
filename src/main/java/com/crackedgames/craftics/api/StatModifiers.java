package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.TrimEffects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable accumulator for combat stat bonuses contributed by trim patterns, trim
 * materials, armor sets, enchantments, and equipment scanners.
 *
 * <p>One instance is assembled before each fight by scanning the player's equipment.
 * {@link EnchantmentEffectHandler} and {@link EquipmentScanner} implementations write
 * into a {@code StatModifiers} instance; Craftics reads it back to compute the player's
 * effective stats for the fight.
 *
 * @since 0.2.0
 */
public class StatModifiers {
    private final Map<TrimEffects.Bonus, Integer> bonuses = new HashMap<>();
    private TrimEffects.SetBonus setBonus = TrimEffects.SetBonus.NONE;
    private String setBonusName = "";
    private final List<NamedCombatEffect> combatEffects = new ArrayList<>();

    /**
     * Add {@code value} to the accumulator for {@code bonus}, summing with any existing value.
     *
     * @param bonus the stat to increment
     * @param value the amount to add; may be negative
     */
    public void add(TrimEffects.Bonus bonus, int value) {
        bonuses.merge(bonus, value, Integer::sum);
    }

    /**
     * Set the active set bonus. Only one set bonus is tracked; calling this a second time
     * overwrites the first.
     *
     * @param bonus the set bonus mechanic to activate
     * @param name  display name shown to the player
     */
    public void addSetBonus(TrimEffects.SetBonus bonus, String name) {
        this.setBonus = bonus;
        this.setBonusName = name;
    }

    /** Register a custom combat effect with lifecycle callbacks. */
    public void addCombatEffect(String name, CombatEffectHandler handler) {
        combatEffects.add(new NamedCombatEffect(name, handler));
    }

    /**
     * The accumulated value for {@code bonus}, or {@code 0} if nothing has been added for it.
     *
     * @param bonus the stat to query
     * @return the total accumulated value
     */
    public int get(TrimEffects.Bonus bonus) {
        return bonuses.getOrDefault(bonus, 0);
    }

    /** All accumulated stat bonuses as a mutable map. Treat as read-only outside this class. */
    public Map<TrimEffects.Bonus, Integer> getAll() {
        return bonuses;
    }

    /** The active set bonus mechanic, or {@code NONE} if none has been set. */
    public TrimEffects.SetBonus getSetBonus() {
        return setBonus;
    }

    /** Display name of the active set bonus, or an empty string if none is active. */
    public String getSetBonusName() {
        return setBonusName;
    }

    /** All registered custom combat effect handlers, in registration order. */
    public List<NamedCombatEffect> getCombatEffects() {
        return combatEffects;
    }
}
