package com.crackedgames.craftics.combat.barter;

import com.crackedgames.craftics.level.campaign.CampaignManager;
import com.crackedgames.craftics.level.campaign.VanillaCampaign;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pure region-gating test for the piglin barter feature: confirms that the vanilla campaign places
 * every Nether biome node in the {@code "nether"} region and keeps Overworld/End nodes out of it.
 * The barter station only appears in the Nether region, so this guards the gating signal the barter
 * system reads. No items are constructed, so it runs under the plain JUnit harness.
 */
class BarterGatingTest {

    @BeforeAll
    static void ensureCampaign() {
        // Same registration path the other campaign tests use (writes craftics:vanilla into the
        // static CampaignManager, where it resolves as active when nothing else is registered).
        VanillaCampaign.register();
    }

    @AfterAll
    static void cleanup() {
        // Reset the static CampaignManager so a leaked entry can't change another test's resolution.
        CampaignManager.clearAllForTest();
    }

    @Test
    void netherNodesResolveToNetherRegion() {
        for (String n : new String[]{"nether_wastes", "soul_sand_valley", "crimson_forest",
                                     "warped_forest", "basalt_deltas"}) {
            var region = CampaignManager.regionOf(n);
            assertNotNull(region, "no region for " + n);
            assertEquals("nether", region.id(), n + " should be in the nether region");
        }
    }

    @Test
    void overworldAndEndNodesAreNotNether() {
        for (String n : new String[]{"plains", "desert", "end_city", "dragons_nest"}) {
            var region = CampaignManager.regionOf(n);
            assertNotNull(region, "no region for " + n);
            assertNotEquals("nether", region.id(), n + " must not be nether");
        }
    }
}
