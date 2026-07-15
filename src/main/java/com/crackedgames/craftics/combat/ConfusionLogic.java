package com.crackedgames.craftics.combat;

/**
 * Pure roll helpers for the confusion nerf. Kept separate + pure so the thresholds
 * are unit-testable without a Minecraft bootstrap.
 * See docs/superpowers/specs/2026-07-09-confusion-nerf-design.md.
 */
public final class ConfusionLogic {

    private ConfusionLogic() {}

    /** True when {@code roll} (0..1) lands under {@code chance}. Boundary: roll==chance misses. */
    public static boolean rollHits(double roll, double chance) {
        return roll < chance;
    }
}
