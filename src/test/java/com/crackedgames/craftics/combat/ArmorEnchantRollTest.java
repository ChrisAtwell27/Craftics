package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The high-roll level curve used by {@code CombatManager.variedHeavyEnchant}.
 *
 * <p>The roll itself lives inside a private, registry-bound method, so this pins the pure
 * arithmetic it uses. The property that matters: a high roll is STRONG but VARIED, and it
 * can never exceed an enchantment's own cap - overshooting would mint illegal gear like
 * "Mending II".
 */
class ArmorEnchantRollTest {

    /** Mirror of the level choice in {@code CombatManager.applyEnchants} under highRoll. */
    private static int highRoll(int cap, Random rng) {
        int floor = Math.max(1, (cap + 1) / 2);
        return floor + (cap > floor ? rng.nextInt(cap - floor + 1) : 0);
    }

    @Test
    void neverExceedsTheEnchantmentsOwnCap() {
        Random rng = new Random(1234);
        for (int cap = 1; cap <= 5; cap++) {
            for (int i = 0; i < 500; i++) {
                int lvl = highRoll(cap, rng);
                assertTrue(lvl >= 1, "level must be at least I, got " + lvl);
                assertTrue(lvl <= cap,
                    "cap " + cap + " must never roll " + lvl + " - that would be illegal gear");
            }
        }
    }

    /** Mending / Infinity are single-level. They must stay at I, never "Mending II". */
    @Test
    void singleLevelEnchantsStayAtOne() {
        Random rng = new Random(7);
        for (int i = 0; i < 200; i++) {
            assertEquals(1, highRoll(1, rng));
        }
    }

    /** A high roll is high: a 5-level enchant (Protection, Sharpness) lands III-V, never I. */
    @Test
    void fiveLevelEnchantsRollInTheUpperHalf() {
        Random rng = new Random(99);
        boolean sawBelowMax = false;
        for (int i = 0; i < 500; i++) {
            int lvl = highRoll(5, rng);
            assertTrue(lvl >= 3, "a high roll on a 5-level enchant must be at least III, got " + lvl);
            assertTrue(lvl <= 5);
            if (lvl < 5) sawBelowMax = true;
        }
        assertTrue(sawBelowMax,
            "the whole point is variety - a high roll must NOT always come out maxed");
    }

    /** Three-level enchants (Thorns, Respiration, Feather Falling caps at 4) roll II-III. */
    @Test
    void threeLevelEnchantsRollTwoToThree() {
        Random rng = new Random(5);
        boolean sawTwo = false, sawThree = false;
        for (int i = 0; i < 500; i++) {
            int lvl = highRoll(3, rng);
            assertTrue(lvl == 2 || lvl == 3, "expected II or III, got " + lvl);
            if (lvl == 2) sawTwo = true;
            if (lvl == 3) sawThree = true;
        }
        assertTrue(sawTwo && sawThree, "both II and III must be reachable");
    }

    /**
     * The armorer's tier gate reads a material's BASE armor class, which ranks sets cleanly
     * (iron 4, diamond 6, netherite 7) where per-piece AC would not - a diamond helmet and a
     * netherite helmet are both 4.
     */
    @Test
    void tierGateRanksMaterialsNotPieces() {
        int ironBase = ArmorClassTable.baseAC("iron");
        int diamondBase = ArmorClassTable.baseAC("diamond");
        int netheriteBase = ArmorClassTable.baseAC("netherite");
        int leatherBase = ArmorClassTable.baseAC("leather");

        assertTrue(leatherBase < ironBase);
        assertTrue(ironBase < diamondBase);
        assertTrue(diamondBase < netheriteBase);

        // Per-piece AC collides ACROSS slots: iron leggings and a netherite helmet are both 4.
        // A gate reading the piece value would therefore admit iron leggings wherever it admits
        // a netherite helmet, which is exactly why minArmorBaseACForTier reads the base instead.
        assertEquals(
            ArmorClassTable.pieceAC(ironBase, ArmorClassTable.Slot.LEGGINGS),
            ArmorClassTable.pieceAC(netheriteBase, ArmorClassTable.Slot.HELMET),
            "iron leggings and a netherite helmet share a piece AC - gating on the piece value "
                + "could not tell those two materials apart");
    }
}
