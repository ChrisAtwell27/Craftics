package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the trial chamber's attack scaling.
 *
 * <p>Trial mobs were scaled by one multiplier for both health and damage, and those are not the
 * same problem: triple health makes a fight longer, triple damage makes it shorter and the
 * player is the one it ends. Trial spawns also skip the per-biome damage cap that restrains
 * every campaign enemy - deliberately, so a trial can be a step up - which removed the one thing
 * that would otherwise have noticed. By the Nether a Breeze was swinging for about eight hearts.
 *
 * <p>These pin the shape of the fix rather than exact numbers: attack scales, it scales less
 * steeply than it did, and it never drops below the mob's own authored base.
 */
class TrialAttackScalingTest {

    /** The multiplier the generator builds: 1.35 at the first biome, +0.03 per biome after. */
    private static float diffAt(int biomeOrdinal) {
        return 1.35f + (biomeOrdinal * 0.03f);
    }

    /**
     * The absolute ceiling, mirrored from {@code CombatManager.MAX_ENEMY_ATTACK} so this test
     * never has to load that class - it drags in the whole game. Kept equal to it on purpose:
     * a mirror that quietly disagrees is worse than no mirror.
     */
    private static final int CAP = 18;

    private static int ATK(int base, int atkBonus, float diff) {
        return TrialScaling.attack(base, atkBonus, diff, CAP);
    }

    /** What the old formula produced, for comparison. */
    private static int oldAttack(int base, int atkBonus, float diff) {
        return (int) ((base + atkBonus) * diff);
    }

    @Test
    void lateGameDamageIsRoughlyHalved() {
        // The reported case: a Breeze (base 5) deep into the run.
        int base = 5, atkBonus = 4;
        float diff = diffAt(14);

        int before = oldAttack(base, atkBonus, diff);
        int after = ATK(base, atkBonus, diff);

        assertTrue(before >= 14, "the old formula should be the painful one, got " + before);
        assertTrue(after <= before / 2 + 1,
            "expected roughly half of " + before + ", got " + after);
    }

    @Test
    void aTrialMobNeverHitsSofterThanItsOwnBase() {
        // The floor. At the first biome the halved surcharge lands under the base value, and a
        // trial zombie weaker than an ordinary one would be a worse bug than the one being fixed.
        for (int ordinal = 0; ordinal <= 20; ordinal++) {
            for (int base : new int[] {2, 3, 4, 5, 10}) {
                int atk = ATK(base, ordinal / 3, diffAt(ordinal));
                assertTrue(atk >= base,
                    "base " + base + " at ordinal " + ordinal + " scaled DOWN to " + atk);
            }
        }
    }

    @Test
    void damageStillGrowsWithDepth() {
        // Halving the scaling must not flatten it - a deep trial should still hit harder than
        // a shallow one, or the encounter stops being a step up at all.
        int shallow = ATK(4, 0, diffAt(0));
        int deep = ATK(4, 6, diffAt(20));
        assertTrue(deep > shallow, "deep " + deep + " did not exceed shallow " + shallow);
    }

    @Test
    void itIsMonotonicInDepth() {
        int previous = Integer.MIN_VALUE;
        for (int ordinal = 0; ordinal <= 24; ordinal++) {
            int atk = ATK(4, ordinal / 3, diffAt(ordinal));
            assertTrue(atk >= previous, "attack went backwards at ordinal " + ordinal);
            previous = atk;
        }
    }

    @Test
    void itNeverExceedsTheGlobalCeiling() {
        // The absolute ceiling still applies, however absurd the inputs get.
        assertTrue(ATK(50, 200, 9.0f) <= CAP);
    }

    @Test
    void theWardenStillHitsLikeAWarden() {
        // Halved along with everything else, but floored at its own base - the scariest thing
        // in the game must not come out of this softer than the adds around it.
        for (int ordinal = 10; ordinal <= 20; ordinal++) {
            // Mirrors ominousWardenAttack: base 10, +1 per biome past the ominous start.
            int wardenBase = 10 + Math.max(0, ordinal - 10);
            int warden = ATK(wardenBase, 0, diffAt(ordinal));
            int add = ATK(4, ordinal / 3, diffAt(ordinal));
            assertTrue(warden >= 10, "warden fell below its base at ordinal " + ordinal);
            assertTrue(warden > add,
                "warden " + warden + " did not out-hit a regular add " + add
                + " at ordinal " + ordinal);
        }
    }
}
