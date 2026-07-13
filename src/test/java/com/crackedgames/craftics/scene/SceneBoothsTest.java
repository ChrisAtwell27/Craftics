package com.crackedgames.craftics.scene;

import com.crackedgames.craftics.api.registry.TraderCategoryRegistry;
import com.crackedgames.craftics.combat.VanillaTraderContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SceneBoothsTest {

    @BeforeEach
    void registerVanillaTraders() {
        TraderCategoryRegistry.clearAllForTest();
        VanillaTraderContent.register();
    }

    @Test
    void traderTypeForResolvesNamespacedIds() {
        assertEquals(VanillaTraderContent.WEAPONSMITH,
            SceneBooths.traderTypeFor("craftics:weaponsmith").id());
        assertEquals(VanillaTraderContent.ARMORER,
            SceneBooths.traderTypeFor("craftics:armorer").id());
        assertEquals(VanillaTraderContent.PROVISIONER,
            SceneBooths.traderTypeFor("craftics:provisioner").id());
    }

    /**
     * Saves written before 0.2.10 stored the bare enum name ("WEAPONSMITH"). If this regresses,
     * every existing player silently loses every merchant they had already met.
     */
    @Test
    void traderTypeForStillResolvesLegacyEnumNames() {
        assertEquals(VanillaTraderContent.WEAPONSMITH,
            SceneBooths.traderTypeFor("WEAPONSMITH").id());
        assertEquals(VanillaTraderContent.CURIOSITY_DEALER,
            SceneBooths.traderTypeFor("CURIOSITY_DEALER").id());
    }

    @Test
    void traderTypeForUnknownReturnsNull() {
        assertNull(SceneBooths.traderTypeFor("craftics:not_a_trader"));
        assertNull(SceneBooths.traderTypeFor(""));
        assertNull(SceneBooths.traderTypeFor(null));
        // An addon trader whose mod has been uninstalled must resolve to null rather than throw,
        // so the hall leaves that booth empty instead of failing to build.
        assertNull(SceneBooths.traderTypeFor("someaddon:blacksmith"));
    }

    @Test
    void occupantForFollowsRegistrationOrder() {
        // Booth index maps onto registry order - which is exactly why that order is load-bearing.
        assertEquals(VanillaTraderContent.WEAPONSMITH, SceneBooths.occupantFor("village", 0));
        assertEquals(VanillaTraderContent.ARMORER, SceneBooths.occupantFor("village", 1));
        // Past the end of the registry (a schematic with more stalls than merchants) is empty.
        assertEquals("", SceneBooths.occupantFor("village", 99));
        assertEquals("", SceneBooths.occupantFor("nope", 0));
    }
}
