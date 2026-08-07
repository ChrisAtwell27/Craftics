package com.crackedgames.craftics.combat;

import java.util.function.DoubleSupplier;

/** Pure, unit-testable math for the tactical-combat enchant effects. No Minecraft types. */
public final class CombatEnchantHelpers {
    private CombatEnchantHelpers() {}

    private static final double AP_REDUCE_CHANCE_PER_LEVEL = 0.10;

    /** How many AP to shave: {@code level} independent 10% rolls, one -1 per success. */
    public static int apReduction(int level, DoubleSupplier roll) {
        int cut = 0;
        for (int i = 0; i < level; i++) {
            if (roll.getAsDouble() < AP_REDUCE_CHANCE_PER_LEVEL) cut++;
        }
        return cut;
    }

    /** Air after a turn: full ({@code level}) when out of deep water, else one less. */
    public static int respirationNextAir(int currentAir, boolean inDeepWater, int level) {
        if (level <= 0) return 0;
        return inDeepWater ? currentAir - 1 : level;
    }

    /** Drown iff the turn ends in deep water with no air left. */
    public static boolean respirationDrowns(int airBeforeTick, boolean endsTurnInDeepWater) {
        return endsTurnInDeepWater && airBeforeTick <= 0;
    }

    /** Loot-pool index (0/1/2) from a [0,1) roll, split in thirds. */
    public static int fortunePick(double roll) {
        if (roll < 1.0 / 3.0) return 0;
        if (roll < 2.0 / 3.0) return 1;
        return 2;
    }

    /**
     * The level actually applied to an item, given a blind roll that does not know the
     * enchantment's own maximum. The lootbox polish and mob-gear enchanting both roll a
     * level 1..N uniformly with no idea which enchantment they picked, so without this a
     * single-level enchantment like Hilt can come out "Hilt V" - a level that does not exist
     * and, since Hilt is a binary damage-type conversion, would not do anything different
     * from Hilt I even if it did.
     *
     * @param enchantPath registry path of the enchantment being applied (e.g. {@code "hilt"})
     * @param rolledLevel the blind roll, before knowing the enchantment's cap
     * @param maxLevel    the enchantment's own registry maximum ({@code Enchantment.getMaxLevel()})
     * @param uncapped    enchantments allowed to roll ABOVE their own maximum on purpose
     *                    (Knockback: Craftics' push scales with level with no cap of its own)
     */
    public static int clampEnchantLevel(String enchantPath, int rolledLevel, int maxLevel,
                                        java.util.Set<String> uncapped) {
        if (uncapped.contains(enchantPath)) return rolledLevel;
        return Math.min(rolledLevel, maxLevel);
    }
}
