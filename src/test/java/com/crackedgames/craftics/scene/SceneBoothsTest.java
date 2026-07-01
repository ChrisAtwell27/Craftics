package com.crackedgames.craftics.scene;

import com.crackedgames.craftics.combat.TraderSystem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SceneBoothsTest {
    @Test
    void traderTypeForStripsNamespaceAndMatchesEnum() {
        assertEquals(TraderSystem.TraderType.WEAPONSMITH, SceneBooths.traderTypeFor("craftics:weaponsmith"));
        assertEquals(TraderSystem.TraderType.ARMORER, SceneBooths.traderTypeFor("craftics:armorer"));
        assertEquals(TraderSystem.TraderType.PROVISIONER, SceneBooths.traderTypeFor("craftics:provisioner"));
    }

    @Test
    void traderTypeForUnknownReturnsNull() {
        assertNull(SceneBooths.traderTypeFor("craftics:not_a_trader"));
        assertNull(SceneBooths.traderTypeFor(""));
        assertNull(SceneBooths.traderTypeFor("villager:addon"));
    }

    @Test
    void villageOccupantsAreAllValidTraderTypes() {
        assertEquals(3, SceneBooths.VILLAGE_OCCUPANTS.length);
        for (String occ : SceneBooths.VILLAGE_OCCUPANTS) {
            assertNotNull(SceneBooths.traderTypeFor(occ), occ + " must map to a TraderType");
        }
    }

    @Test
    void occupantForPicksByIndexAndScene() {
        assertEquals(SceneBooths.VILLAGE_OCCUPANTS[1], SceneBooths.occupantFor("village", 1));
        assertEquals(SceneBooths.BARTER_OCCUPANTS[0], SceneBooths.occupantFor("barter_station", 0));
        // Out-of-range index → empty string (no booth occupant)
        assertEquals("", SceneBooths.occupantFor("village", 99));
    }

    @Test
    void barterOccupantsHaveNamespacedIds() {
        assertEquals(3, SceneBooths.BARTER_OCCUPANTS.length);
        for (String occ : SceneBooths.BARTER_OCCUPANTS) {
            assertTrue(occ.startsWith("craftics:"), occ + " must be namespaced");
        }
    }
}
