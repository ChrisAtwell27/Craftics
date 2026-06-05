package com.crackedgames.craftics.level.campaign;

import com.crackedgames.craftics.data.CampaignJsonLoader;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the CODE-built {@link VanillaCampaign#build()} — the safety-net mirror of the shipped
 * {@code data/craftics/craftics/campaigns/vanilla.json}. {@code build()} is pure (Minecraft-free),
 * so the structural assertions run under plain JUnit. A final parity test parses the same JSON
 * (inlined here) through {@link CampaignJsonLoader#parseCampaign} and asserts both branches produce
 * identical biome orders, guarding against the JSON and code drifting.
 */
class VanillaCampaignTest {

    /** Branch-0 order: overworld (warm-first) + nether + end, in declaration order. */
    private static final List<String> BRANCH_0 = List.of(
        "plains", "desert", "jungle", "forest", "river", "snowy", "mountain", "cave", "deep_dark",
        "nether_wastes", "soul_sand_valley", "crimson_forest", "warped_forest", "basalt_deltas",
        "outer_end_islands", "end_city", "chorus_grove", "dragons_nest");

    /** Branch-1 order: overworld cool-first (segment swap), nether + end unchanged. */
    private static final List<String> BRANCH_1 = List.of(
        "plains", "snowy", "mountain", "river", "desert", "jungle", "forest", "cave", "deep_dark",
        "nether_wastes", "soul_sand_valley", "crimson_forest", "warped_forest", "basalt_deltas",
        "outer_end_islands", "end_city", "chorus_grove", "dragons_nest");

    @AfterEach
    void cleanup() {
        // VanillaCampaign.register() writes to the static CampaignManager; reset between tests so
        // a leaked entry can't change another test's active-campaign resolution.
        CampaignManager.clearAllForTest();
    }

    @Test
    void totalBiomeCountIs18() {
        assertEquals(18, VanillaCampaign.build().totalBiomeCount());
    }

    @Test
    void hasThreeRegionsSized9_5_4() {
        List<CampaignRegion> regions = VanillaCampaign.build().regions();
        assertEquals(3, regions.size());

        assertEquals("overworld", regions.get(0).id());
        assertEquals(9, regions.get(0).nodes().size());

        assertEquals("nether", regions.get(1).id());
        assertEquals(5, regions.get(1).nodes().size());

        assertEquals("end", regions.get(2).id());
        assertEquals(4, regions.get(2).nodes().size());
    }

    @Test
    void branch0IsTheLinearWarmFirstOrder() {
        assertEquals(BRANCH_0, VanillaCampaign.build().orderedBiomeIds(0));
    }

    @Test
    void branch1AppliesTheOverworldSegmentSwap() {
        Campaign c = VanillaCampaign.build();
        assertTrue(c.isBranchValid());
        assertEquals(BRANCH_1, c.orderedBiomeIds(1));

        // The overworld portion is the cool-first order; nether + end are unchanged from branch 0.
        assertEquals(BRANCH_1.subList(0, 9), c.orderedBiomeIds(1).subList(0, 9));
        assertEquals(BRANCH_0.subList(9, 18), c.orderedBiomeIds(1).subList(9, 18));
    }

    @Test
    void finalBiomeIsDragonsNestOnBothBranches() {
        Campaign c = VanillaCampaign.build();
        assertTrue(c.isFinal("dragons_nest", 0));
        assertTrue(c.isFinal("dragons_nest", 1));
        assertFalse(c.isFinal("plains", 0));
        assertFalse(c.isFinal("plains", 1));
    }

    @Test
    void regionMembershipResolves() {
        Campaign c = VanillaCampaign.build();
        assertEquals("overworld", c.regionOf("plains").id());
        assertEquals("overworld", c.regionOf("deep_dark").id());
        assertEquals("nether", c.regionOf("nether_wastes").id());
        assertEquals("nether", c.regionOf("basalt_deltas").id());
        assertEquals("end", c.regionOf("dragons_nest").id());
        assertEquals("end", c.regionOf("outer_end_islands").id());
    }

    @Test
    void identityAndDisplayName() {
        Campaign c = VanillaCampaign.build();
        assertEquals(CampaignManager.VANILLA_ID, c.id());
        assertEquals("Vanilla", c.displayName());
    }

    @Test
    void nodeLabelsAreTheBiomeDisplayNames() {
        Campaign c = VanillaCampaign.build();
        assertEquals("Plains", c.nodeOf("plains").labelOverride());
        assertEquals("Scorching Desert", c.nodeOf("desert").labelOverride());
        assertEquals("The Deep Dark", c.nodeOf("deep_dark").labelOverride());
        assertEquals("Nether Wastes", c.nodeOf("nether_wastes").labelOverride());
        assertEquals("Dragon's Nest", c.nodeOf("dragons_nest").labelOverride());
    }

    @Test
    void regionThemeUsesFirstBiomeValues() {
        List<CampaignRegion> regions = VanillaCampaign.build().regions();
        // Overworld theme = plains' green sun.
        assertEquals("§a", regions.get(0).color());
        assertEquals(0xAA55FF55, regions.get(0).mapColor());
        // Nether theme = nether_wastes' red.
        assertEquals("§c", regions.get(1).color());
        assertEquals(0xAAFF5555, regions.get(1).mapColor());
        // End theme = outer_end_islands' light purple.
        assertEquals("§d", regions.get(2).color());
        assertEquals(0xAACC88FF, regions.get(2).mapColor());
    }

    @Test
    void registerMakesVanillaTheActiveCampaign() {
        // Nothing else registered, so the code-built vanilla resolves as active.
        VanillaCampaign.register();
        Campaign active = CampaignManager.active();
        assertNotNull(active);
        assertEquals(CampaignManager.VANILLA_ID, active.id());
        assertEquals(18, active.totalBiomeCount());
        assertEquals(BRANCH_0, active.orderedBiomeIds(0));
        assertEquals(BRANCH_1, active.orderedBiomeIds(1));
    }

    /**
     * JSON&harr;code parity: the inlined vanilla.json (kept byte-for-byte in sync with the shipped
     * resource) parses to the same structure as {@link VanillaCampaign#build()} — same region ids,
     * sizes, and both branch orders. Guards against the JSON and the code registrar drifting.
     */
    @Test
    void shippedJsonMatchesCodeBuild() {
        // Mirror of src/main/resources/data/craftics/craftics/campaigns/vanilla.json.
        String vanillaJson = """
            {
              "id": "craftics:vanilla",
              "display_name": "Vanilla",
              "regions": [
                {
                  "id": "overworld",
                  "display_name": "Overworld",
                  "color": "§a",
                  "icon": "☀",
                  "map_color": "AA55FF55",
                  "nodes": [
                    { "biome": "plains", "label": "Plains" },
                    { "biome": "desert", "label": "Scorching Desert" },
                    { "biome": "jungle", "label": "Dense Jungle" },
                    { "biome": "forest", "label": "Dark Forest" },
                    { "biome": "river", "label": "River Delta" },
                    { "biome": "snowy", "label": "Snowy Tundra" },
                    { "biome": "mountain", "label": "Stony Peaks" },
                    { "biome": "cave", "label": "Underground Caverns" },
                    { "biome": "deep_dark", "label": "The Deep Dark" }
                  ]
                },
                {
                  "id": "nether",
                  "display_name": "The Nether",
                  "color": "§c",
                  "icon": "♨",
                  "map_color": "AAFF5555",
                  "nodes": [
                    { "biome": "nether_wastes", "label": "Nether Wastes" },
                    { "biome": "soul_sand_valley", "label": "Soul Sand Valley" },
                    { "biome": "crimson_forest", "label": "Crimson Forest" },
                    { "biome": "warped_forest", "label": "Warped Forest" },
                    { "biome": "basalt_deltas", "label": "Basalt Deltas" }
                  ]
                },
                {
                  "id": "end",
                  "display_name": "The End",
                  "color": "§d",
                  "icon": "✦",
                  "map_color": "AACC88FF",
                  "nodes": [
                    { "biome": "outer_end_islands", "label": "Outer End Islands" },
                    { "biome": "end_city", "label": "End City" },
                    { "biome": "chorus_grove", "label": "Chorus Grove" },
                    { "biome": "dragons_nest", "label": "Dragon's Nest" }
                  ]
                }
              ],
              "branch": {
                "region": "overworld",
                "swap": [["desert", "jungle", "forest"], ["snowy", "mountain"]]
              }
            }""";

        JsonObject json = new Gson().fromJson(vanillaJson, JsonObject.class);
        Campaign fromJson = CampaignJsonLoader.parseCampaign(
            Identifier.of("craftics", "campaigns/vanilla"), json);
        assertNotNull(fromJson, "vanilla.json should parse");

        Campaign fromCode = VanillaCampaign.build();

        // Full structural equality: id, displayName, regions (with node labels + region theme),
        // and branch all match — the strongest parity guard.
        assertEquals(fromCode, fromJson);

        // Belt-and-suspenders explicit ordering checks (independent of equals()).
        assertEquals(fromCode.orderedBiomeIds(0), fromJson.orderedBiomeIds(0));
        assertEquals(fromCode.orderedBiomeIds(1), fromJson.orderedBiomeIds(1));
        assertEquals(BRANCH_0, fromJson.orderedBiomeIds(0));
        assertEquals(BRANCH_1, fromJson.orderedBiomeIds(1));
        assertEquals(fromCode.totalBiomeCount(), fromJson.totalBiomeCount());
        assertTrue(fromJson.isBranchValid());
    }

    /** Belt-and-suspenders: the build() result is stable (same structure each call). */
    @Test
    void buildIsDeterministic() {
        assertSame(true, VanillaCampaign.build().equals(VanillaCampaign.build()));
    }
}
