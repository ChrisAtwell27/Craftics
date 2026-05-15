package com.crackedgames.craftics.combat;

import java.util.Random;

/**
 * Armor Class dodge resolution.
 *
 * <p>When an enemy attacks a player, the player has a chance to fully dodge the
 * attack (0 damage); otherwise the hit lands for flat enemy attack damage.
 * Dodge is deliberately uncommon — a lucky break, not a reliable wall.
 *
 * <p>Formula: {@code dodge% = clamp(3 * (AC - enemyEffectiveAttack) - 8, 5, 40)}.
 * <ul>
 *   <li>Slope 3 — each point of AC advantage adds ~3% dodge.</li>
 *   <li>Offset −8 — anchors full leather (AC 7) vs a starting enemy at ~7%.</li>
 *   <li>5% floor — outmatched / old gear is never completely useless.</li>
 *   <li>40% cap — dodge never becomes a reliable defense.</li>
 * </ul>
 * See {@code docs/superpowers/specs/2026-05-10-armor-class-overhaul-design.md}.
 */
public final class DodgeRoll {

    private DodgeRoll() {}

    /** Minimum dodge chance, in percent — applies even when badly outmatched. */
    public static final int FLOOR = 5;
    /** Maximum dodge chance, in percent — bounds the benefit of stacking AC. */
    public static final int CAP = 40;

    /** Result of a dodge roll: whether it dodged, the chance used, and the d100 roll. */
    public record DodgeResult(boolean dodged, int dodgePercent, int rolled) {}

    /**
     * Dodge chance (in percent, 5–40) for the given player AC against an enemy's
     * effective attack value.
     */
    public static int dodgePercent(int armorClass, int enemyEffectiveAttack) {
        int pct = 3 * (armorClass - enemyEffectiveAttack) - 8;
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
