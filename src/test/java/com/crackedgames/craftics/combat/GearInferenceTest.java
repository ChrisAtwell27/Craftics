package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for inferring combat stats from a modded item's own name and numbers.
 *
 * <p>Only the registry-free half is exercised here - shape detection, the ladder, and the
 * material-to-affinity map. That is deliberate rather than a limitation: those three are where
 * the mistakes live. Reading an attribute off an item is a component lookup that either works
 * or throws, while "does greataxe read as an axe or a greataxe" is a judgement encoded in
 * ordering, and ordering is exactly what silently rots when someone adds a keyword.
 */
class GearInferenceTest {

    // ── Shape: the families Craftics already supports ─────────────────────

    @Test
    void shape_readsEachFamilyCraftlicsAlreadyHas() {
        assertEquals(GearInference.Shape.LIGHT_BLADE, GearInference.shapeOf("iron_sword"));
        assertEquals(GearInference.Shape.DAGGER, GearInference.shapeOf("steel_dagger"));
        assertEquals(GearInference.Shape.DAGGER, GearInference.shapeOf("iron_sai"));
        assertEquals(GearInference.Shape.THROWN, GearInference.shapeOf("gold_chakram"));
        assertEquals(GearInference.Shape.POLEARM, GearInference.shapeOf("iron_glaive"));
        assertEquals(GearInference.Shape.POLEARM, GearInference.shapeOf("steel_halberd"));
        assertEquals(GearInference.Shape.SPEAR, GearInference.shapeOf("iron_spear"));
        assertEquals(GearInference.Shape.SCYTHE, GearInference.shapeOf("bone_scythe"));
        assertEquals(GearInference.Shape.HAMMER, GearInference.shapeOf("stone_mace"));
        assertEquals(GearInference.Shape.AXE, GearInference.shapeOf("iron_battleaxe"));
        assertEquals(GearInference.Shape.BOW, GearInference.shapeOf("yew_longbow"));
    }

    @Test
    void shape_compoundNamesBeatTheWordsInsideThem() {
        // The whole reason shapeOf is an ordered chain. Each of these contains a shorter
        // keyword that appears later in the list, and matching the short one first would give
        // a two-handed weapon a one-handed profile.
        assertEquals(GearInference.Shape.GREATAXE, GearInference.shapeOf("iron_greataxe"));
        assertEquals(GearInference.Shape.GREATBLADE, GearInference.shapeOf("iron_greatsword"));
        assertEquals(GearInference.Shape.GREATHAMMER, GearInference.shapeOf("iron_warhammer"));
        assertEquals(GearInference.Shape.WARGLAIVE, GearInference.shapeOf("iron_warglaive"));
    }

    @Test
    void shape_warglaiveIsNotAGlaive() {
        // Paired blades at arm's length versus a hafted polearm - different AP and different
        // reach. "warglaive" contains "glaive", so only ordering keeps them apart.
        assertNotEquals(GearInference.shapeOf("iron_glaive"), GearInference.shapeOf("iron_warglaive"));
        assertEquals(1, GearInference.Shape.WARGLAIVE.apCost);
        assertEquals(2, GearInference.Shape.POLEARM.range);
    }

    @Test
    void shape_thrownIsNotABow() {
        // A chakram carries its own attack rating and comes back; a bow's damage is its ammo.
        assertEquals(GearInference.Shape.THROWN, GearInference.shapeOf("chakram"));
        assertFalse(GearInference.Shape.THROWN.firesAmmunition());
        assertTrue(GearInference.Shape.BOW.firesAmmunition());
        assertTrue(GearInference.Shape.THROWN.ranged, "a chakram is still thrown");
    }

    @Test
    void shape_ignoresTools() {
        // "pickaxe" contains "axe". A modded pickaxe must not become a battleaxe.
        assertNull(GearInference.shapeOf("iron_pickaxe"));
        assertNull(GearInference.shapeOf("diamond_shovel"));
        assertNull(GearInference.shapeOf("netherite_hoe"));
        assertNull(GearInference.shapeOf("shears"));
    }

    @Test
    void shape_ignoresThingsThatAreNotWeapons() {
        // Returning a shape for everything would replace a visible gap with a silent wrong
        // answer, which is worse.
        assertNull(GearInference.shapeOf("oak_planks"));
        assertNull(GearInference.shapeOf("cobblestone"));
        assertNull(GearInference.shapeOf(""));
        assertNull(GearInference.shapeOf(null));
    }

    @Test
    void shape_isCaseInsensitive() {
        assertEquals(GearInference.Shape.LIGHT_BLADE, GearInference.shapeOf("Iron_Sword"));
    }

    @Test
    void shape_heavyFamiliesCostMoreThanLightOnes() {
        // The profiles have to stay ordered, or a two-handed weapon becomes strictly better
        // than a dagger rather than a trade.
        assertTrue(GearInference.Shape.GREATBLADE.apCost > GearInference.Shape.LIGHT_BLADE.apCost);
        assertTrue(GearInference.Shape.GREATAXE.apCost > GearInference.Shape.AXE.apCost);
        assertTrue(GearInference.Shape.GREATHAMMER.apCost > GearInference.Shape.HAMMER.apCost);
    }

    @Test
    void shape_haftedWeaponsReachFurtherThanBlades() {
        assertTrue(GearInference.Shape.POLEARM.range > GearInference.Shape.LIGHT_BLADE.range);
        assertTrue(GearInference.Shape.SPEAR.range > GearInference.Shape.DAGGER.range);
    }

    // ── The ladder ────────────────────────────────────────────────────────

    private static final double[] XS = {4, 5, 6, 7, 8};   // vanilla sword attack damage
    private static final int[] YS = {5, 6, 9, 12, 15};    // what Craftics authored for them

    @Test
    void ladder_reproducesTheReferencePointsExactly() {
        // The property the whole design rests on: a vanilla item fed through inference comes
        // out at the number Craftics already hand-authored for it. If this drifts, inference
        // is quietly disagreeing with the game it is extending.
        for (int i = 0; i < XS.length; i++) {
            assertEquals(YS[i], GearInference.onLadder(XS, YS, XS[i]),
                "reference point " + XS[i] + " must map to its authored value");
        }
    }

    @Test
    void ladder_interpolatesBetweenReferencePoints() {
        // A modded sword halfway between iron (6 → 9) and diamond (7 → 12) lands between.
        int mid = GearInference.onLadder(XS, YS, 6.5);
        assertTrue(mid > 9 && mid < 12, "expected between 9 and 12, got " + mid);
    }

    @Test
    void ladder_clampsAtBothEnds() {
        // A pack handing out a sword with 400 attack damage must not get 400 damage here.
        // The ladder is the balance statement and the top of it is the top.
        assertEquals(15, GearInference.onLadder(XS, YS, 400));
        assertEquals(5, GearInference.onLadder(XS, YS, 0));
        assertEquals(5, GearInference.onLadder(XS, YS, -10));
    }

    @Test
    void ladder_isMonotonic() {
        // More vanilla damage must never mean less Craftics damage, or a mod's own tiers
        // would invert on the way in.
        int previous = Integer.MIN_VALUE;
        for (double x = 0; x <= 12; x += 0.25) {
            int value = GearInference.onLadder(XS, YS, x);
            assertTrue(value >= previous, "went backwards at " + x);
            previous = value;
        }
    }

    @Test
    void ladder_toleratesMalformedInput() {
        assertEquals(0, GearInference.onLadder(null, YS, 5));
        assertEquals(0, GearInference.onLadder(XS, null, 5));
        assertEquals(0, GearInference.onLadder(new double[0], new int[0], 5));
        assertEquals(0, GearInference.onLadder(new double[]{1, 2}, new int[]{1}, 5));
    }

    // ── Armor affinity ────────────────────────────────────────────────────

    @Test
    void affinity_matchesWhatCrafticsGivesTheVanillaMaterial() {
        // A modded variant of a vanilla material must land on the affinity the player already
        // associates with it, or their mental model breaks the moment they install a mod.
        assertEquals(DamageType.PHYSICAL, GearInference.affinityForMaterial("leather"));
        assertEquals(DamageType.SLASHING, GearInference.affinityForMaterial("chainmail"));
        assertEquals(DamageType.CLEAVING, GearInference.affinityForMaterial("iron"));
        assertEquals(DamageType.SPECIAL, GearInference.affinityForMaterial("gold"));
        assertEquals(DamageType.BLUNT, GearInference.affinityForMaterial("diamond"));
        assertEquals(DamageType.WATER, GearInference.affinityForMaterial("turtle"));
    }

    @Test
    void affinity_readsModdedMaterialsByFamily() {
        assertEquals(DamageType.CLEAVING, GearInference.affinityForMaterial("steel"));
        assertEquals(DamageType.CLEAVING, GearInference.affinityForMaterial("reinforced_iron"));
        assertEquals(DamageType.SLASHING, GearInference.affinityForMaterial("brigandine"));
        assertEquals(DamageType.BLUNT, GearInference.affinityForMaterial("ruby"));
        assertEquals(DamageType.SPECIAL, GearInference.affinityForMaterial("bronze"));
        assertEquals(DamageType.WATER, GearInference.affinityForMaterial("prismarine"));
        assertEquals(DamageType.PHYSICAL, GearInference.affinityForMaterial("fur"));
    }

    @Test
    void affinity_fallsBackToPhysicalRatherThanNothing() {
        // Every armor set in Craftics grants an affinity; one that granted none would read as
        // broken next to the rest. Physical is leather's - the plainest answer.
        assertEquals(DamageType.PHYSICAL, GearInference.affinityForMaterial("unobtainium"));
        assertEquals(DamageType.PHYSICAL, GearInference.affinityForMaterial(""));
        assertEquals(DamageType.PHYSICAL, GearInference.affinityForMaterial(null));
    }

    // ── Defensive score ───────────────────────────────────────────────────

    @Test
    void defensiveScore_separatesDiamondFromNetherite() {
        // They carry identical vanilla armor points and differ only in toughness, yet Craftics
        // prices netherite well above diamond. A score ignoring toughness would give every
        // modded end-tier armor diamond's number.
        double diamond = GearInference.defensiveScore(20, 2);
        double netherite = GearInference.defensiveScore(20, 3);
        assertTrue(netherite > diamond, "toughness has to count for something");
    }

    @Test
    void defensiveScore_risesWithBothInputs() {
        assertTrue(GearInference.defensiveScore(15, 0) > GearInference.defensiveScore(12, 0));
        assertTrue(GearInference.defensiveScore(12, 1) > GearInference.defensiveScore(12, 0));
    }
}
