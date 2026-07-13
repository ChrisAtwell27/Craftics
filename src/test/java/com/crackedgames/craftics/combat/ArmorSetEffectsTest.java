package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the pure per-set mechanics in {@link ArmorSetEffects}. Everything here is
 * keyed by the armor-set name {@code PlayerCombatStats.getArmorSet} returns, so no
 * Minecraft bootstrap is needed. The wiring that calls these from {@code CombatManager}
 * is covered by the in-game smoke checklist.
 */
class ArmorSetEffectsTest {

    /** A set nobody registered, and the sentinel a partial/mismatched loadout returns. */
    private static final String[] NOT_A_SET = {"mixed", "iron", "netherite", "", null};

    // ---- Full-set gating ----

    @Test
    void everyEffect_isInertForNonMatchingSets() {
        for (String set : NOT_A_SET) {
            assertEquals(0.0, ArmorSetEffects.incomingResistance(set, DamageType.RANGED), set + " resist");
            assertFalse(ArmorSetEffects.isFragile(set), set + " fragile");
            assertFalse(ArmorSetEffects.ignoresKnockback(set), set + " kb immune");
            assertFalse(ArmorSetEffects.hasDivineDeflect(set), set + " divine");
            assertEquals(0, ArmorSetEffects.witherRetaliationTurns(set), set + " wither");
            assertEquals(0, ArmorSetEffects.contactKnockbackTiles(set), set + " slime");
            assertEquals(0, ArmorSetEffects.missingHealthDamage(set, 1f, 20f), set + " warrior");
            assertEquals(0.0, ArmorSetEffects.ammoSaveChance(set), set + " ammo");
            assertFalse(ArmorSetEffects.sherdsNeverBreak(set), set + " sherd break");
            assertEquals(0, ArmorSetEffects.sherdApDiscount(set), set + " sherd ap");
            assertFalse(ArmorSetEffects.hasPrismarineDischarge(set), set + " prismarine");
            assertFalse(ArmorSetEffects.revealsEnemyIntent(set), set + " radar");
        }
    }

    // ---- Wooden: typed resistance ----

    @Test
    void wooden_resistsOnlyRangedAndBlunt() {
        assertEquals(ArmorSetEffects.WOODEN_TYPE_RESIST,
            ArmorSetEffects.incomingResistance(ArmorSetEffects.WOODEN, DamageType.RANGED));
        assertEquals(ArmorSetEffects.WOODEN_TYPE_RESIST,
            ArmorSetEffects.incomingResistance(ArmorSetEffects.WOODEN, DamageType.BLUNT));
        assertEquals(0.0, ArmorSetEffects.incomingResistance(ArmorSetEffects.WOODEN, DamageType.SLASHING));
        assertEquals(0.0, ArmorSetEffects.incomingResistance(ArmorSetEffects.WOODEN, DamageType.WATER));
    }

    @Test
    void onlyWoodenAndBone_areFragile() {
        assertTrue(ArmorSetEffects.isFragile(ArmorSetEffects.WOODEN));
        assertTrue(ArmorSetEffects.isFragile(ArmorSetEffects.BONE));
        assertFalse(ArmorSetEffects.isFragile(ArmorSetEffects.HEAVY));
    }

    // ---- Ammo save: wither strictly better than bone ----

    @Test
    void ammoSave_witherBeatsBone_othersSaveNothing() {
        double bone = ArmorSetEffects.ammoSaveChance(ArmorSetEffects.BONE);
        double wither = ArmorSetEffects.ammoSaveChance(ArmorSetEffects.WITHER);
        assertTrue(bone > 0.0, "bone saves ammo");
        assertTrue(wither > bone, "wither is the upgrade");
        assertEquals(0.0, ArmorSetEffects.ammoSaveChance(ArmorSetEffects.WARRIOR));
    }

    // ---- Luck ----

    @Test
    void luck_raisesAmmoSave_butNeverToCertainty() {
        String bone = ArmorSetEffects.BONE;
        double none = ArmorSetEffects.ammoSaveChance(bone, 0);
        assertEquals(ArmorSetEffects.ammoSaveChance(bone), none, "zero luck changes nothing");
        assertTrue(ArmorSetEffects.ammoSaveChance(bone, 5) > none, "luck helps");
        assertTrue(ArmorSetEffects.ammoSaveChance(bone, 1000) < 1.0, "an infinite quiver is not on offer");
    }

    @Test
    void luck_neverGivesAmmoSaveToASetWithout() {
        assertEquals(0.0, ArmorSetEffects.ammoSaveChance(ArmorSetEffects.WARRIOR, 50));
        assertEquals(0.0, ArmorSetEffects.ammoSaveChance("mixed", 50));
    }

    @Test
    void luck_lowersFragileBreak_butNeverBelowTheFloor() {
        String wooden = ArmorSetEffects.WOODEN;
        assertEquals(ArmorSetEffects.FRAGILE_BREAK_CHANCE,
            ArmorSetEffects.fragileBreakChance(wooden, 0), 1e-9);
        assertTrue(ArmorSetEffects.fragileBreakChance(wooden, 4)
            < ArmorSetEffects.fragileBreakChance(wooden, 0), "luck steadies the gear");
        assertEquals(ArmorSetEffects.FRAGILE_BREAK_FLOOR,
            ArmorSetEffects.fragileBreakChance(wooden, 1000), 1e-9);
    }

    @Test
    void luck_neverMakesASturdySetBreakable() {
        assertEquals(0.0, ArmorSetEffects.fragileBreakChance(ArmorSetEffects.HEAVY, 0));
        assertEquals(0.0, ArmorSetEffects.fragileBreakChance(ArmorSetEffects.HEAVY, 50));
        assertEquals(0.0, ArmorSetEffects.fragileBreakChance("mixed", 50));
    }

    // ---- Warrior: one point per full missing heart ----

    @Test
    void warrior_scalesWithFullMissingHearts() {
        String w = ArmorSetEffects.WARRIOR;
        assertEquals(0, ArmorSetEffects.missingHealthDamage(w, 20f, 20f), "unhurt");
        assertEquals(0, ArmorSetEffects.missingHealthDamage(w, 19f, 20f), "half a heart rounds down");
        assertEquals(1, ArmorSetEffects.missingHealthDamage(w, 18f, 20f), "one heart");
        assertEquals(5, ArmorSetEffects.missingHealthDamage(w, 10f, 20f), "half health");
        assertEquals(10, ArmorSetEffects.missingHealthDamage(w, 0f, 20f), "dead on your feet");
    }

    @Test
    void warrior_neverNegative_whenOverhealed() {
        assertEquals(0, ArmorSetEffects.missingHealthDamage(ArmorSetEffects.WARRIOR, 24f, 20f));
    }

    // ---- Robe / Heavy / Divine / Prismarine / Slime / Wither: single-set identities ----

    @Test
    void eachSignatureEffect_belongsToExactlyOneSet() {
        assertTrue(ArmorSetEffects.sherdsNeverBreak(ArmorSetEffects.ROBE));
        assertEquals(ArmorSetEffects.ROBE_SHERD_AP_DISCOUNT,
            ArmorSetEffects.sherdApDiscount(ArmorSetEffects.ROBE));

        assertTrue(ArmorSetEffects.ignoresKnockback(ArmorSetEffects.HEAVY));
        assertFalse(ArmorSetEffects.ignoresKnockback(ArmorSetEffects.SLIME));

        assertTrue(ArmorSetEffects.hasDivineDeflect(ArmorSetEffects.DIVINE));
        assertTrue(ArmorSetEffects.hasPrismarineDischarge(ArmorSetEffects.PRISMARINE));
        assertTrue(ArmorSetEffects.revealsEnemyIntent(ArmorSetEffects.STEAMPUNK));

        assertEquals(ArmorSetEffects.SLIME_KNOCKBACK_TILES,
            ArmorSetEffects.contactKnockbackTiles(ArmorSetEffects.SLIME));
        assertEquals(0, ArmorSetEffects.contactKnockbackTiles(ArmorSetEffects.HEAVY));

        assertEquals(ArmorSetEffects.WITHER_RETALIATION_TURNS,
            ArmorSetEffects.witherRetaliationTurns(ArmorSetEffects.WITHER));
        assertEquals(0, ArmorSetEffects.witherRetaliationTurns(ArmorSetEffects.BONE));
    }
}
