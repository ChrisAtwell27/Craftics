package com.crackedgames.craftics.combat.ai.ally;

import com.crackedgames.craftics.combat.CombatEntity;

import java.util.Map;

/**
 * Per-mob-type on-hit status effects — the "special abilities" some allies bring
 * to a fight. Applied in {@code CombatManager} right after an ally's attack
 * damages its target, independent of the ally's AI archetype.
 *
 * @since 0.3.0
 */
public final class AllyAbilities {

    private AllyAbilities() {}

    /** A status an ally inflicts on whatever it attacks. */
    public enum OnHitEffect { NONE, BURN, SOAK, SLOW, POISON }

    private static final Map<String, OnHitEffect> BY_TYPE = Map.ofEntries(
        // Striders set their target alight.
        Map.entry("minecraft:strider", OnHitEffect.BURN),
        // Watery allies leave their target Soaked.
        Map.entry("minecraft:axolotl", OnHitEffect.SOAK),
        Map.entry("minecraft:llama", OnHitEffect.SOAK),
        Map.entry("minecraft:trader_llama", OnHitEffect.SOAK),
        Map.entry("minecraft:dolphin", OnHitEffect.SOAK),
        Map.entry("minecraft:frog", OnHitEffect.SOAK),
        // Cold allies chill their target with Slowness.
        Map.entry("minecraft:snow_golem", OnHitEffect.SLOW),
        Map.entry("minecraft:polar_bear", OnHitEffect.SLOW),
        // Bees sting their target with Poison, like their hostile counterparts.
        Map.entry("minecraft:bee", OnHitEffect.POISON)
    );

    /** The on-hit effect for a given ally type, or {@link OnHitEffect#NONE}. */
    public static OnHitEffect effectFor(String entityTypeId) {
        if (entityTypeId == null) return OnHitEffect.NONE;
        return BY_TYPE.getOrDefault(entityTypeId, OnHitEffect.NONE);
    }

    /**
     * Apply {@code ally}'s on-hit effect to the mob it just struck. Returns a
     * short coloured chat fragment naming the effect, or {@code ""} if the ally
     * has no special ability.
     */
    public static String applyOnHit(CombatEntity ally, CombatEntity target) {
        switch (effectFor(ally.getEntityTypeId())) {
            case BURN   -> { target.stackBurning(3, 2);  return " §6Burning!"; }
            case SOAK   -> { target.stackSoaked(3, 0);   return " §bSoaked!"; }
            case SLOW   -> { target.stackSlowness(3, 1); return " §7Slowed!"; }
            case POISON -> { target.stackPoison(3, 0);   return " §2Poisoned!"; }
            default     -> { return ""; }
        }
    }
}
