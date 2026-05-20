package com.crackedgames.craftics.combat;

import java.util.Random;

/**
 * Armor Class dodge resolution.
 *
 * <p>When an enemy attacks a player, the player has a chance to fully dodge the
 * attack (0 damage); otherwise the hit lands for flat enemy attack damage.
 * Dodge is deliberately uncommon — a lucky break, not a reliable wall.
 *
 * <p>Formula: {@code dodge% = clamp(diff*diff/3 + diff, 3, 60)} where
 * {@code diff = AC - enemyEffectiveAttack}.
 * <ul>
 *   <li>Smooth curve — small AC advantages give modest dodge, big
 *       advantages compound gradually (not as steep as a pure triangular).
 *       AC 8 vs 5-ATK (diff 3) → 6%; AC 8 vs 3-ATK (diff 5) → 13%;
 *       AC 10 vs 3-ATK (diff 7) → 23%; AC 13 vs 4-ATK (diff 9) → 36%.</li>
 *   <li>3% floor — outmatched / old gear is never completely useless.</li>
 *   <li>60% cap — high AC is meaningful but still no full wall.</li>
 * </ul>
 * See {@code docs/combat.html#armor-class}.
 */
public final class DodgeRoll {

    private DodgeRoll() {}

    /** Minimum dodge chance, in percent — applies even when badly outmatched. */
    public static final int FLOOR = 3;
    /** Maximum dodge chance, in percent — bounds the benefit of stacking AC. */
    public static final int CAP = 60;

    /** Result of a dodge roll: whether it dodged, the chance used, and the d100 roll. */
    public record DodgeResult(boolean dodged, int dodgePercent, int rolled) {}

    /**
     * Dodge chance (in percent, {@value #FLOOR}–{@value #CAP}) for the given
     * player AC against an enemy's effective attack value.
     */
    public static int dodgePercent(int armorClass, int enemyEffectiveAttack) {
        int diff = Math.max(0, armorClass - enemyEffectiveAttack);
        int pct = (diff * diff) / 3 + diff;
        return Math.max(FLOOR, Math.min(CAP, pct));
    }

    /**
     * Rolls a d100 against the computed dodge chance.
     *
     * @return a {@link DodgeResult} — {@code dodged} is true when the attack misses.
     */
    public static DodgeResult roll(int armorClass, int enemyEffectiveAttack, Random rng) {
        int pct = dodgePercent(armorClass, enemyEffectiveAttack);
        int rolled = rng.nextInt(100) + 1; // 1..100
        return new DodgeResult(rolled <= pct, pct, rolled);
    }
}
