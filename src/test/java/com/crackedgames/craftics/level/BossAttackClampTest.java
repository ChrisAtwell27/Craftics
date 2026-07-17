package com.crackedgames.craftics.level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-math tests for {@link LevelGenerator#clampBossAttack(int, int)}, the boss
 * attack ceiling. Boss attack scales additively with biome progress
 * (baseAttack + atkBonus) and, unlike regular enemies, skips the per-biome damage
 * cap, so without a ceiling a late-campaign boss reaches an attack that near
 * one-shots a 20 HP player (the River Tidecaller hit 16). The clamp keeps the
 * scaled value at or below the ceiling; the two-argument overload takes the cap
 * so it needs no config (and thus no Minecraft bootstrap) to test.
 */
class BossAttackClampTest {

    /** The shipped ceiling: the strongest hand-authored boss base (Chorus Grove). */
    private static final int CAP = 12;

    @Test
    void highOrdinalBossIsCappedAtCeiling() {
        // River Tidecaller: base 5 + atkBonus climbing to +11 in a late slot = 16.
        // That near one-shots a 20 HP player and is exactly the reported bug.
        assertEquals(CAP, LevelGenerator.clampBossAttack(16, CAP));
        // Any runaway value clamps to the ceiling, never above.
        assertEquals(CAP, LevelGenerator.clampBossAttack(30, CAP));
        assertEquals(CAP, LevelGenerator.clampBossAttack(13, CAP));
    }

    @Test
    void bossAtOrBelowCeilingIsUnchanged() {
        // A low-ordinal boss (little or no scaling) is left exactly as authored.
        assertEquals(5, LevelGenerator.clampBossAttack(5, CAP));   // River base, no bonus
        assertEquals(1, LevelGenerator.clampBossAttack(1, CAP));   // Plains base
        // The strongest hand-authored boss sits right at the ceiling and is kept.
        assertEquals(CAP, LevelGenerator.clampBossAttack(CAP, CAP));
    }

    @Test
    void clampIsIdempotent() {
        int once = LevelGenerator.clampBossAttack(20, CAP);
        assertEquals(once, LevelGenerator.clampBossAttack(once, CAP));
    }

    @Test
    void capValueIsHonoured() {
        // The clamp uses whatever ceiling it is handed, so a config change moves it.
        assertEquals(9, LevelGenerator.clampBossAttack(16, 9));
        assertEquals(16, LevelGenerator.clampBossAttack(16, 20));
    }
}
