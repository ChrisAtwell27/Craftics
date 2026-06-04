package com.crackedgames.craftics.level.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * Tests for the pure {@link CampaignRegion} model — presentation defaults (color, icon,
 * mapColor, displayName), value round-trips, and canonical-constructor validation.
 */
class CampaignRegionTest {

    @Test
    void builder_appliesPresentationDefaults() {
        CampaignRegion r = CampaignRegion.builder("x").node("a").build();
        assertEquals(0xFFFFFFFF, r.mapColor());
        assertEquals("§f", r.color());
        assertEquals("?", r.icon());
        // displayName defaults to id.
        assertEquals("x", r.displayName());
    }

    @Test
    void builder_customMapColorAndPresentationRoundTrip() {
        CampaignRegion r = CampaignRegion.builder("surface")
            .displayName("Surface")
            .color("§a")
            .icon("⛰")
            .mapColor(0xFF88CC44)
            .node("a")
            .build();
        assertEquals("Surface", r.displayName());
        assertEquals("§a", r.color());
        assertEquals("⛰", r.icon());
        assertEquals(0xFF88CC44, r.mapColor());
    }

    // --- FIX 5: null displayName defaults to id via the canonical constructor ---

    @Test
    void constructor_nullDisplayNameDefaultsToId() {
        CampaignRegion r = new CampaignRegion("surface", null, "§f", "?", 0xFFFFFFFF,
            List.of(CampaignNode.of("a")));
        assertEquals("surface", r.displayName());
    }

    @Test
    void constructor_rejectsBlankId() {
        assertThrows(IllegalArgumentException.class,
            () -> new CampaignRegion("  ", "X", "§f", "?", 0xFFFFFFFF,
                List.of(CampaignNode.of("a"))));
    }
}
