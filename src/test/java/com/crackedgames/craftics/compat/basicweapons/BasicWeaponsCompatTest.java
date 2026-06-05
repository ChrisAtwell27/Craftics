package com.crackedgames.craftics.compat.basicweapons;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure-logic tests for BasicWeaponsCompat (no Minecraft registry). */
class BasicWeaponsCompatTest {

    @Test
    void weaponType_recognizesAllSixSuffixes() {
        assertEquals("dagger", BasicWeaponsCompat.weaponType("iron_dagger"));
        assertEquals("spear", BasicWeaponsCompat.weaponType("netherite_spear"));
        assertEquals("quarterstaff", BasicWeaponsCompat.weaponType("wooden_quarterstaff"));
        assertEquals("club", BasicWeaponsCompat.weaponType("stone_club"));
        assertEquals("hammer", BasicWeaponsCompat.weaponType("golden_hammer"));
        assertEquals("glaive", BasicWeaponsCompat.weaponType("diamond_glaive"));
    }

    @Test
    void weaponType_returnsNullForUnknown() {
        // bronze tier is out of scope but its suffix still parses to a type;
        // weaponType only parses the suffix, registration/gating excludes bronze.
        assertEquals("dagger", BasicWeaponsCompat.weaponType("bronze_dagger"));
        assertNull(BasicWeaponsCompat.weaponType("iron_sword"));
        assertNull(BasicWeaponsCompat.weaponType("totem"));
    }

    @Test
    void isBluntType_onlyClubHammerQuarterstaff() {
        assertTrue(BasicWeaponsCompat.isBluntType("club"));
        assertTrue(BasicWeaponsCompat.isBluntType("hammer"));
        assertTrue(BasicWeaponsCompat.isBluntType("quarterstaff"));
        assertFalse(BasicWeaponsCompat.isBluntType("dagger"));
        assertFalse(BasicWeaponsCompat.isBluntType("spear"));
        assertFalse(BasicWeaponsCompat.isBluntType("glaive"));
        assertFalse(BasicWeaponsCompat.isBluntType(null));
    }

    @Test
    void baseTraderCost_scalesByDamageClass() {
        // dagger cheapest, hammer/glaive dearest; spear/quarterstaff/club between.
        int dagger = BasicWeaponsCompat.baseTraderCost("dagger");
        int hammer = BasicWeaponsCompat.baseTraderCost("hammer");
        int glaive = BasicWeaponsCompat.baseTraderCost("glaive");
        assertTrue(dagger < hammer, "dagger cheaper than hammer");
        assertEquals(hammer, glaive, "hammer and glaive same class");
        assertTrue(dagger >= 1, "cost at least 1");
    }

    @Test
    void tiersForTier_gatesByMaterial() {
        // wooden/stone low, iron mid, diamond high, netherite top.
        assertEquals(java.util.List.of("wooden", "stone"), BasicWeaponsLootRoller.tiersForTier(1));
        assertEquals(java.util.List.of("iron"), BasicWeaponsLootRoller.tiersForTier(4));
        assertTrue(BasicWeaponsLootRoller.tiersForTier(7).contains("diamond"));
        assertTrue(BasicWeaponsLootRoller.tiersForTier(9).contains("netherite"));
    }
}
