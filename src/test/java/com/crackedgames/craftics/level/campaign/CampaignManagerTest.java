package com.crackedgames.craftics.level.campaign;

import com.crackedgames.craftics.api.RegistrationSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link CampaignManager} — active resolution ("most-recently-registered
 * non-vanilla wins; else vanilla"), source tagging, datapack clearing, and the null-safe
 * query delegators. Pure logic: runs under plain JUnit with no Minecraft bootstrap.
 */
class CampaignManagerTest {

    /** A small two-region campaign: [a, b] then [c]. Final biome is "c". */
    private static Campaign campaign(String id) {
        return Campaign.builder(id)
            .region(CampaignRegion.builder("r1")
                .node(id + ":a")
                .node(id + ":b")
                .build())
            .region(CampaignRegion.builder("r2")
                .node(id + ":c")
                .build())
            .build();
    }

    @AfterEach
    void cleanup() {
        // CampaignManager is static global state. "Most-recently-registered non-vanilla wins"
        // means a leaked CODE entry could change another test's active campaign, and
        // clearDatapackEntries cannot remove code entries — so do a full reset between tests.
        CampaignManager.clearAllForTest();
    }

    @Test
    void singleNonVanilla_isActive_andDelegatorsMatch() {
        Campaign c = campaign("test:solo");
        CampaignManager.register(c);

        assertTrue(CampaignManager.hasActive());
        assertSame(c, CampaignManager.active());

        // Delegators mirror the active campaign.
        assertEquals(c.ordinalOf("test:solo:a", 0), CampaignManager.ordinalOf("test:solo:a", 0));
        assertEquals(0, CampaignManager.ordinalOf("test:solo:a", 0));
        assertEquals(2, CampaignManager.ordinalOf("test:solo:c", 0));

        assertSame(c.regionOf("test:solo:c"), CampaignManager.regionOf("test:solo:c"));
        assertEquals("r2", CampaignManager.regionOf("test:solo:c").id());

        assertTrue(CampaignManager.isFinalBiome("test:solo:c", 0));
        assertFalse(CampaignManager.isFinalBiome("test:solo:a", 0));

        assertEquals(3, CampaignManager.totalBiomes());
        assertEquals(List.of("test:solo:a", "test:solo:b", "test:solo:c"),
            CampaignManager.orderedBiomeIds(0));
    }

    @Test
    void vanillaPlusAddon_addonWins() {
        Campaign vanilla = campaign(CampaignManager.VANILLA_ID);
        Campaign addon = campaign("test:addon");
        CampaignManager.register(vanilla);
        CampaignManager.register(addon);

        assertSame(addon, CampaignManager.active(),
            "a non-vanilla campaign should replace the built-in vanilla one");
    }

    @Test
    void onlyVanilla_vanillaIsActive() {
        Campaign vanilla = campaign(CampaignManager.VANILLA_ID);
        CampaignManager.register(vanilla);

        assertSame(vanilla, CampaignManager.active());
    }

    @Test
    void twoNonVanilla_mostRecentlyRegisteredWins() {
        Campaign first = campaign("test:first");
        Campaign second = campaign("test:second");
        CampaignManager.register(first);
        CampaignManager.register(second);

        assertSame(second, CampaignManager.active());

        // Re-registering the first makes it the most recent again.
        Campaign firstAgain = campaign("test:first");
        CampaignManager.register(firstAgain);
        assertSame(firstAgain, CampaignManager.active());
    }

    @Test
    void datapackCleared_fallsBackToCodeVanilla() {
        Campaign vanilla = campaign(CampaignManager.VANILLA_ID);
        Campaign pack = campaign("test:pack");
        CampaignManager.register(vanilla, RegistrationSource.CODE);
        CampaignManager.register(pack, RegistrationSource.DATAPACK);

        // Datapack campaign is newer and non-vanilla, so it's active.
        assertSame(pack, CampaignManager.active());

        CampaignManager.clearDatapackEntries();

        // Datapack dropped; the code vanilla survives and becomes active again.
        assertSame(vanilla, CampaignManager.active());
        assertTrue(CampaignManager.hasActive());
    }

    @Test
    void ordinalOf_unknownBiome_returnsMinusOne() {
        CampaignManager.register(campaign("test:unknown_lookup"));
        assertEquals(-1, CampaignManager.ordinalOf("test:does_not_exist", 0));
    }

    @Test
    void noActiveCampaign_delegatorsAreNullSafe() {
        // Empty registry (after the @AfterEach full reset semantics apply mid-test too).
        CampaignManager.clearAllForTest();

        assertFalse(CampaignManager.hasActive());
        assertNull(CampaignManager.active());
        assertEquals(-1, CampaignManager.ordinalOf("anything", 0));
        assertNull(CampaignManager.regionOf("anything"));
        assertFalse(CampaignManager.isFinalBiome("anything", 0));
        assertEquals(0, CampaignManager.totalBiomes());
        assertTrue(CampaignManager.orderedBiomeIds(0).isEmpty());
    }

    @Test
    void sameId_datapackOverridesCode_andClearRemovesId() {
        // A CODE campaign claims id "test:x"...
        Campaign code = Campaign.builder("test:x")
            .region(CampaignRegion.builder("r1").node("test:x:code").build())
            .build();
        CampaignManager.register(code, RegistrationSource.CODE);
        assertSame(code, CampaignManager.active());

        // ...then a DATAPACK campaign with the SAME id but different content overwrites it.
        Campaign pack = Campaign.builder("test:x")
            .region(CampaignRegion.builder("r1").node("test:x:pack").build())
            .build();
        CampaignManager.register(pack, RegistrationSource.DATAPACK);
        assertSame(pack, CampaignManager.active(),
            "the datapack entry has the newest seq and overwrote the code entry for this id");

        // Clearing datapack entries removes the id ENTIRELY. The earlier code version is NOT
        // restored: the single REGISTRY slot for "test:x" was overwritten, so when the datapack
        // entry is dropped there is nothing left under that id.
        CampaignManager.clearDatapackEntries();
        assertNull(CampaignManager.active(),
            "clearing the datapack-tagged id removes it; the overwritten code version is not resurrected");
        assertFalse(CampaignManager.hasActive());
    }

    @Test
    void reRegisteringVanilla_doesNotOverrideActiveAddon() {
        Campaign vanilla = campaign(CampaignManager.VANILLA_ID);
        Campaign addon = campaign("test:addon2");
        CampaignManager.register(vanilla, RegistrationSource.CODE);
        CampaignManager.register(addon, RegistrationSource.CODE);
        assertSame(addon, CampaignManager.active());

        // Re-registering vanilla bumps vanilla's seq, but vanilla is excluded from the
        // "most-recently-registered non-vanilla wins" comparison, so the addon stays active.
        Campaign vanillaAgain = campaign(CampaignManager.VANILLA_ID);
        CampaignManager.register(vanillaAgain, RegistrationSource.CODE);
        assertSame(addon, CampaignManager.active(),
            "re-registering vanilla must never make vanilla win over an active addon");
    }

    @Test
    void noActiveCampaign_newDelegatorsAreNullSafe() {
        CampaignManager.clearAllForTest();

        assertTrue(CampaignManager.orderedNodes(0).isEmpty());
        assertNull(CampaignManager.nodeOf("anything"));
        assertTrue(CampaignManager.regions().isEmpty());
        assertNull(CampaignManager.regionById("surface"));
        assertEquals("", CampaignManager.activeDisplayName());
        assertFalse(CampaignManager.isBranchValid());
    }

    @Test
    void newDelegators_delegateToActiveCampaign() {
        Campaign c = Campaign.builder("test:deleg")
            .displayName("Delegation Test")
            .region(CampaignRegion.builder("surface")
                .node("test:deleg:a")
                .node("test:deleg:b")
                .build())
            .region(CampaignRegion.builder("caves")
                .node("test:deleg:c")
                .build())
            .build();
        CampaignManager.register(c);

        assertEquals("Delegation Test", CampaignManager.activeDisplayName());
        assertEquals(c.regions(), CampaignManager.regions());
        assertEquals(c.orderedNodes(0), CampaignManager.orderedNodes(0));

        assertSame(c.nodeOf("test:deleg:b"), CampaignManager.nodeOf("test:deleg:b"));
        assertEquals("test:deleg:b", CampaignManager.nodeOf("test:deleg:b").biomeId());

        CampaignRegion surface = CampaignManager.regionById("surface");
        assertNotNull(surface);
        assertEquals("surface", surface.id());
        assertSame(c.regions().get(0), surface);
        assertNull(CampaignManager.regionById("nope"));

        // No branch configured -> not valid.
        assertFalse(CampaignManager.isBranchValid());
    }
}
