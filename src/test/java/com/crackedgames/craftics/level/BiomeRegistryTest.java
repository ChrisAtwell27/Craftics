package com.crackedgames.craftics.level;

import com.crackedgames.craftics.level.campaign.Campaign;
import com.crackedgames.craftics.level.campaign.CampaignManager;
import com.crackedgames.craftics.level.campaign.CampaignRegion;
import com.crackedgames.craftics.level.campaign.VanillaCampaign;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link BiomeRegistry#rebuildLevelNumbers()} ordering, which now flows from the active
 * campaign's biome sequence ({@link CampaignManager#orderedBiomeIds(int)} for branch 0) rather
 * than each biome's JSON {@code order}/{@code startLevel}.
 *
 * <p>{@link BiomeTemplate}'s constructor only <em>stores</em> its {@code Block[]}/{@code Item[]}/
 * {@code MobPoolEntry[]} references - it never touches Minecraft registries or requires bootstrap
 * - and the rebuild logic reads only {@code biomeId}, {@code startLevel}, and {@code levelCount}.
 * So these templates are constructed with {@code null} for the Minecraft-typed pools, keeping the
 * test runnable under plain JUnit (no Minecraft bootstrap). The campaign model
 * ({@link VanillaCampaign#build()}, {@link Campaign}) is likewise pure.
 */
class BiomeRegistryTest {

    @AfterEach
    void cleanup() {
        // Both registries are static global state; reset so a leaked biome/campaign can't bleed
        // into another test's ordering or active-campaign resolution.
        BiomeRegistry.clear();
        CampaignManager.clearAllForTest();
    }

    /**
     * Minimal {@link BiomeTemplate} carrying only the fields the rebuild reads ({@code biomeId},
     * {@code startLevel}, {@code levelCount}); every Minecraft-typed pool is {@code null}.
     */
    private static BiomeTemplate biome(String biomeId, int startLevel, int levelCount) {
        return new BiomeTemplate(
            biomeId, biomeId, startLevel, levelCount,
            10, 10, 0, 0,
            (Block[]) null, (Block[]) null,
            0f, 0f,
            (MobPoolEntry[]) null, (MobPoolEntry[]) null, null,
            (Item[]) null, (int[]) null,
            (String[]) null, (int[]) null,
            false, null);
    }

    /**
     * The canonical vanilla branch-0 play/campaign order (from {@link VanillaCampaign}). This is the
     * sequence the rebuild numbers by; it is NOT the same as the legacy JSON {@code order} sort, which
     * placed the overworld biomes as plains, forest, snowy, mountain, river, desert, jungle, cave,
     * deep_dark.
     */
    private static final List<String> VANILLA_ORDER = List.of(
        "plains", "desert", "jungle", "forest", "river", "snowy", "mountain", "cave", "deep_dark",
        "nether_wastes", "soul_sand_valley", "crimson_forest", "warped_forest", "basalt_deltas",
        "outer_end_islands", "end_city", "chorus_grove", "dragons_nest");

    @Test
    void ordersByCampaignPositionWithContiguousLevels_evenWhenRegisteredScrambled() {
        CampaignManager.register(VanillaCampaign.build());

        // Register in a deliberately scrambled order with mixed level counts; the campaign order,
        // not registration order or startLevel, must drive numbering.
        BiomeRegistry.register(biome("river", 999, 2));
        BiomeRegistry.register(biome("plains", 500, 3));
        BiomeRegistry.register(biome("dragons_nest", 1, 1));
        BiomeRegistry.register(biome("desert", 42, 2));
        BiomeRegistry.register(biome("jungle", 7, 4));

        List<BiomeTemplate> all = BiomeRegistry.getAllBiomes(); // triggers rebuild

        // Must come out in campaign order: plains, desert, jungle, river, dragons_nest.
        assertEquals(
            List.of("plains", "desert", "jungle", "river", "dragons_nest"),
            all.stream().map(b -> b.biomeId).toList());

        // Contiguous level numbers from 1, accumulating each biome's levelCount.
        assertEquals(1, BiomeRegistry.getForLevel(1).startLevel);
        assertEquals("plains", BiomeRegistry.getForLevel(1).biomeId);       // [1,3]
        assertEquals("desert", BiomeRegistry.getForLevel(4).biomeId);        // [4,5]
        assertEquals("jungle", BiomeRegistry.getForLevel(6).biomeId);        // [6,9]
        assertEquals("river", BiomeRegistry.getForLevel(10).biomeId);        // [10,11]
        assertEquals("dragons_nest", BiomeRegistry.getForLevel(12).biomeId); // [12,12]

        // startLevels are exactly the running prefix sum of levelCount.
        assertEquals(1, BiomeRegistry.getForLevel(1).startLevel);
        assertEquals(4, BiomeRegistry.getForLevel(4).startLevel);
        assertEquals(6, BiomeRegistry.getForLevel(6).startLevel);
        assertEquals(10, BiomeRegistry.getForLevel(10).startLevel);
        assertEquals(12, BiomeRegistry.getForLevel(12).startLevel);
        assertEquals(12, BiomeRegistry.getTotalLevelCount());
    }

    @Test
    void outOfCampaignBiomesSortLastBeyondTheCampaignRange() {
        CampaignManager.register(VanillaCampaign.build());

        // Two in-campaign biomes plus one biome that is NOT in the vanilla campaign.
        BiomeRegistry.register(biome("desert", 100, 2));
        BiomeRegistry.register(biome("plains", 200, 3)); // campaign position 0
        BiomeRegistry.register(biome("custom:swamp", 1, 5)); // not in campaign -> sorts last

        List<BiomeTemplate> all = BiomeRegistry.getAllBiomes();
        assertEquals(
            List.of("plains", "desert", "custom:swamp"),
            all.stream().map(b -> b.biomeId).toList());

        // plains [1,3], desert [4,5], then the out-of-campaign biome beyond the campaign range.
        assertEquals("plains", BiomeRegistry.getForLevel(1).biomeId);
        assertEquals("desert", BiomeRegistry.getForLevel(4).biomeId);
        assertEquals("custom:swamp", BiomeRegistry.getForLevel(6).biomeId); // [6,10], beyond
        assertEquals(6, all.get(2).startLevel);
        assertEquals(10, BiomeRegistry.getTotalLevelCount());
    }

    @Test
    void emptyCampaignFallsBackToStartLevelOrder() {
        // No campaign registered -> orderedBiomeIds(0) is empty -> legacy startLevel sort.
        BiomeRegistry.register(biome("c", 30, 1));
        BiomeRegistry.register(biome("a", 10, 2));
        BiomeRegistry.register(biome("b", 20, 3));

        List<BiomeTemplate> all = BiomeRegistry.getAllBiomes();
        // Sorted ascending by old startLevel: a(10), b(20), c(30).
        assertEquals(List.of("a", "b", "c"), all.stream().map(b -> b.biomeId).toList());
        assertEquals(1, BiomeRegistry.getForLevel(1).startLevel); // a [1,2]
        assertEquals("a", BiomeRegistry.getForLevel(1).biomeId);
        assertEquals("b", BiomeRegistry.getForLevel(3).biomeId);  // [3,5]
        assertEquals("c", BiomeRegistry.getForLevel(6).biomeId);  // [6,6]
        assertEquals(6, BiomeRegistry.getTotalLevelCount());
    }

    /**
     * Full-vanilla-set guard: with all 18 vanilla biomes present (registered scrambled) and the
     * vanilla campaign active, the rebuild lays them out in CAMPAIGN order with contiguous global
     * level numbers. This is the campaign-driven numbering, NOT the old JSON {@code order} sort
     * (which placed the overworld differently: plains, forest, snowy, mountain, river, desert,
     * jungle, cave, deep_dark).
     *
     * <p>Each biome uses a realistic {@code levelCount = 5} (vanilla biomes carry {@code levels: 5}),
     * so the biome at campaign position {@code p} (0-based) occupies the contiguous range
     * {@code [1 + 5*p, 5 + 5*p]} and the whole set spans levels 1..90.
     */
    @Test
    void fullVanillaSetNumbersInCampaignOrderWithContiguousRanges() {
        CampaignManager.register(VanillaCampaign.build());

        // Register all 18 in reverse to prove ordering is campaign-driven, not registration order.
        List<String> reversed = new java.util.ArrayList<>(VANILLA_ORDER);
        java.util.Collections.reverse(reversed);
        for (String id : reversed) {
            BiomeRegistry.register(biome(id, 12345, 5));
        }

        List<BiomeTemplate> all = BiomeRegistry.getAllBiomes();
        assertEquals(VANILLA_ORDER, all.stream().map(b -> b.biomeId).toList());

        // Biome at campaign position p occupies the contiguous range [1 + 5*p, 5 + 5*p].
        for (int pos = 0; pos < VANILLA_ORDER.size(); pos++) {
            String expectedId = VANILLA_ORDER.get(pos);
            int startLevel = 1 + 5 * pos;
            int endLevel = startLevel + 4;

            BiomeTemplate t = BiomeRegistry.getForLevel(startLevel);
            assertNotNull(t);
            assertEquals(expectedId, t.biomeId);
            assertEquals(startLevel, t.startLevel);
            // Every level in the range resolves to the same biome.
            assertEquals(expectedId, BiomeRegistry.getForLevel(endLevel).biomeId);
        }

        // Spot-check the documented ranges: plains [1,5], desert [6,10], jungle [11,15],
        // forest [16,20], river [21,25], snowy [26,30], mountain [31,35], cave [36,40],
        // deep_dark [41,45], nether_wastes [46,50], ... dragons_nest [86,90].
        assertEquals("plains", BiomeRegistry.getForLevel(1).biomeId);
        assertEquals("desert", BiomeRegistry.getForLevel(6).biomeId);
        assertEquals("jungle", BiomeRegistry.getForLevel(11).biomeId);
        assertEquals("forest", BiomeRegistry.getForLevel(16).biomeId);
        assertEquals("nether_wastes", BiomeRegistry.getForLevel(46).biomeId);
        assertEquals("dragons_nest", BiomeRegistry.getForLevel(86).biomeId);
        assertEquals("dragons_nest", BiomeRegistry.getForLevel(90).biomeId);

        assertEquals(90, BiomeRegistry.getTotalLevelCount());
    }

    @Test
    void datapackCampaignReordersNumbering() {
        // A custom campaign whose order differs from vanilla drives the numbering instead.
        Campaign custom = Campaign.builder("test:custom")
            .region(CampaignRegion.builder("r")
                .node("forest")
                .node("plains")
                .node("desert")
                .build())
            .build();
        CampaignManager.register(custom);
        assertTrue(CampaignManager.active().id().equals("test:custom"));

        BiomeRegistry.register(biome("desert", 1, 1));
        BiomeRegistry.register(biome("plains", 2, 1));
        BiomeRegistry.register(biome("forest", 3, 1));

        List<BiomeTemplate> all = BiomeRegistry.getAllBiomes();
        assertEquals(List.of("forest", "plains", "desert"), all.stream().map(b -> b.biomeId).toList());
        assertEquals("forest", BiomeRegistry.getForLevel(1).biomeId);
        assertEquals("plains", BiomeRegistry.getForLevel(2).biomeId);
        assertEquals("desert", BiomeRegistry.getForLevel(3).biomeId);
    }
}
