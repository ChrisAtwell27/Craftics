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
}
