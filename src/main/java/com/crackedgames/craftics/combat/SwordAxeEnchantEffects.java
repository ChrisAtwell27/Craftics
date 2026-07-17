package com.crackedgames.craftics.combat;

/**
 * Pure math for the sword and axe enchantments added in the eight-enchantment batch. Minecraft
 * free so the whole ruleset is unit-testable without a bootstrap. The combat hooks in
 * CombatManager and VanillaWeapons read enchant levels and call these.
 */
public final class SwordAxeEnchantEffects {

    private SwordAxeEnchantEffects() {}

    /** Executioner: +1 damage per debuff on the target, per enchant level. */
    public static int executionerBonus(int targetDebuffCount, int level) {
        if (level <= 0 || targetDebuffCount <= 0) return 0;
        return targetDebuffCount * level;
    }

    /** Pack Bond: each pet deals +1 per OTHER living pet, per level. livingPetCount is the total
     *  alive including the acting pet, so the "others" is count - 1. */
    public static int packBondBonus(int livingPetCount, int level) {
        if (level <= 0) return 0;
        int others = Math.max(0, livingPetCount - 1);
        return others * level;
    }

    /** Serrated: Bleed stacks applied per hit equals the enchant level. */
    public static int serratedBleedStacks(int level) {
        return Math.max(0, level);
    }

    /** Reversal deals this multiplier while the wielder is at or below a quarter HP. */
    public static final double REVERSAL_MULT = 1.5;

    /** True when currentHp is at or below 25% of maxHp. */
    public static boolean isLowHp(int currentHp, int maxHp) {
        if (maxHp <= 0) return false;
        return currentHp * 4 <= maxHp;
    }

    /** Hilt cuts a hit to this fraction (a 75% reduction) on top of redirecting its affinity. */
    public static final double HILT_MULT = 0.25;
    /** Dull cuts a hit to this fraction (a 50% reduction) on top of redirecting its affinity. */
    public static final double DULL_MULT = 0.5;

    /** Hilt: hit for a quarter, never below 1 on a hit that was already positive. */
    public static int hiltDamage(int baseDamage) {
        if (baseDamage <= 0) return baseDamage;
        return Math.max(1, (int) Math.round(baseDamage * HILT_MULT));
    }

    /** Dull: hit for a half, never below 1 on a hit that was already positive. */
    public static int dullDamage(int baseDamage) {
        if (baseDamage <= 0) return baseDamage;
        return Math.max(1, (int) Math.round(baseDamage * DULL_MULT));
    }
}
