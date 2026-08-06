package com.crackedgames.craftics.raid;

import java.util.Locale;

/**
 * A raid boss's permanent buff. Values mirror the player-side CombatEffects
 * numbers (+3 attack per Strength level, -2 damage per Resistance level, and so
 * on) so a boss with Strength II hits as hard as a player with Strength II.
 *
 * <p>Everything here is pure arithmetic. Application to a CombatEntity lives in
 * RaidBossAI, which re-asserts the buff at the start of every boss action so it
 * can never expire or be cleansed. ABSORPTION is the one exception: re-applying
 * it would heal the boss, so it is a spawn-time max-HP addition only.
 */
public enum RaidBossBuff {
    STRENGTH("Strength"),
    RESISTANCE("Resistance"),
    SPEED("Speed"),
    REGENERATION("Regeneration"),
    ABSORPTION("Absorption"),
    FIRE_RESISTANCE("Fire Resistance");

    private final String displayName;

    RaidBossBuff(String displayName) { this.displayName = displayName; }

    public String displayName() { return displayName; }

    /** Resolve a JSON effect id; null when it names no known buff. */
    public static RaidBossBuff of(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static int level(int amplifier) { return Math.max(0, amplifier) + 1; }

    public static int attackBonus(int amplifier) { return 3 * level(amplifier); }

    public static int defenseBonus(int amplifier) { return 2 * level(amplifier); }

    public static int speedBonus(int amplifier) { return 2 * level(amplifier); }

    public static int regenPerTurn(int amplifier) { return 2 * level(amplifier); }

    /** A quarter of the boss's authored max HP per level, added once at spawn. */
    public static int absorptionBonus(int baseMaxHp, int amplifier) {
        return Math.max(0, baseMaxHp) * level(amplifier) / 4;
    }
}
