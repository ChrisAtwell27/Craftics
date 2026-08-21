package com.crackedgames.craftics.level;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that the atlas is <em>derived</em> from a biome's template rather than authored beside
 * it.
 *
 * <p>This is the property the whole feature rests on: retuning a loot weight, swapping a mob
 * into a pool or adding a biome has to change the guide book with it, because the alternative -
 * a second, hand-written copy of the content - is a guide book that is confidently wrong the
 * first time somebody rebalances anything.
 *
 * <p>So these tests do not check that the Deep Dark drops echo shards. They check that whatever
 * a template says comes out the other end, using biomes invented here that no code has ever
 * heard of. A hardcoded answer anywhere in the derivation fails them all.
 */
class BiomeAtlasSyncTest {

    /** A template with an arbitrary pool, built the way the datapack loader builds them. */
    private static BiomeTemplate template(String id, String name, int levelCount,
                                          MobPoolEntry[] passive, MobPoolEntry[] hostile,
                                          MobPoolEntry boss, int[] lootWeights,
                                          String[] enchantIds, int[] enchantWeights,
                                          boolean night, String effectId, int effectStart) {
        return new BiomeTemplate(
            id, name, 1, levelCount,
            10, 10, 0, 0,
            (Block[]) null, (Block[]) null,
            0f, 0f,
            passive, hostile, boss,
            (Item[]) null, lootWeights,
            enchantIds, enchantWeights,
            night, null, effectId, effectStart);
    }

    private static MobPoolEntry mob(String typeId) {
        return new MobPoolEntry(typeId, 1, 10, 2, 0, 1, false);
    }

    /** A stand-in for the live per-mob drop tables. */
    private static Function<String, List<BiomeAtlasCodec.Drop>> dropTable(
            Map<String, List<BiomeAtlasCodec.Drop>> table) {
        return id -> table.getOrDefault(id, List.of());
    }

    private static final Function<String, List<BiomeAtlasCodec.Drop>> NO_DROPS = id -> List.of();

    private static BiomeAtlasCodec.Drop drop(String id, int weight) {
        return new BiomeAtlasCodec.Drop(id, weight);
    }

    // ── Derivation ────────────────────────────────────────────────────────

    @Test
    void reportsWhateverTheTemplateSays() {
        // A biome nothing in Craftics has heard of. Every field below has to arrive intact
        // without anyone having written a page for it.
        BiomeTemplate t = template("myaddon:glass_desert", "Glass Desert", 7,
            new MobPoolEntry[]{mob("minecraft:rabbit")},
            new MobPoolEntry[]{mob("myaddon:shard_walker"), mob("minecraft:husk")},
            mob("myaddon:glass_titan"),
            new int[]{5, 1},
            new String[]{"minecraft:fire_aspect"}, new int[]{2},
            true, "heat_haze", 3);

        BiomeAtlasCodec.Entry e = BiomeAtlasSync.entryFor(t, true,
            new String[]{"myaddon:glass_shard", "minecraft:diamond"}, NO_DROPS);

        assertEquals("myaddon:glass_desert", e.biomeId());
        assertEquals("Glass Desert", e.displayName());
        assertEquals(7, e.levelCount());
        assertTrue(e.nightLevel());
        assertEquals("myaddon:glass_titan", e.bossId());
        assertEquals(List.of("myaddon:shard_walker", "minecraft:husk"), e.hostileIds());
        assertEquals(List.of("minecraft:rabbit"), e.passiveIds());
        assertEquals("heat_haze", e.effectId());
        assertEquals(3, e.effectStartLevel());
        assertEquals("myaddon:glass_shard", e.loot().get(0).id());
        assertEquals(5, e.loot().get(0).weight());
        assertEquals("minecraft:fire_aspect", e.enchants().get(0).id());
    }

    @Test
    void retuningAWeightChangesTheAtlas() {
        // The change a balance pass actually makes. If the atlas were authored rather than
        // derived, this is the test that would catch it going stale.
        String[] ids = {"minecraft:coal", "minecraft:diamond"};
        BiomeTemplate before = template("cave", "Cave", 3, null, null, null,
            new int[]{10, 1}, null, null, false, null, 0);
        BiomeTemplate after = template("cave", "Cave", 3, null, null, null,
            new int[]{1, 10}, null, null, false, null, 0);

        assertEquals("minecraft:coal", BiomeAtlasSync.entryFor(before, true, ids, NO_DROPS).loot().get(0).id());
        assertEquals("minecraft:diamond", BiomeAtlasSync.entryFor(after, true, ids, NO_DROPS).loot().get(0).id());
    }

    @Test
    void addingAMobToAPoolAddsItToTheAtlas() {
        BiomeTemplate before = template("swamp", "Swamp", 3, null,
            new MobPoolEntry[]{mob("minecraft:slime")}, null, null, null, null, false, null, 0);
        BiomeTemplate after = template("swamp", "Swamp", 3, null,
            new MobPoolEntry[]{mob("minecraft:slime"), mob("minecraft:witch")},
            null, null, null, null, false, null, 0);

        assertEquals(1, BiomeAtlasSync.entryFor(before, true, new String[0], NO_DROPS).hostileIds().size());
        assertEquals(List.of("minecraft:slime", "minecraft:witch"),
            BiomeAtlasSync.entryFor(after, true, new String[0], NO_DROPS).hostileIds());
    }

    @Test
    void aBiomeWithNoBossOrEffectSaysSoRatherThanNull() {
        BiomeTemplate t = template("plains", "Plains", 3, null, null, null,
            null, null, null, false, null, 0);
        BiomeAtlasCodec.Entry e = BiomeAtlasSync.entryFor(t, true, new String[0], NO_DROPS);
        assertEquals("", e.bossId());
        assertEquals("", e.effectId());
        assertEquals(0, e.effectStartLevel());
        assertTrue(e.loot().isEmpty());
    }

    // ── Hiding the undiscovered ───────────────────────────────────────────

    @Test
    void anUndiscoveredBiomeIsStrippedBeforeItLeavesTheServer() {
        // Not a display concern. If the contents were sent and merely hidden by the client,
        // the answer would already be on the machine of the person it is hidden from.
        BiomeTemplate t = template("end", "The End", 4, null,
            new MobPoolEntry[]{mob("minecraft:enderman")}, mob("minecraft:ender_dragon"),
            new int[]{5}, new String[]{"minecraft:mending"}, new int[]{1}, false, "void_pull", 1);

        BiomeAtlasCodec.Entry e = BiomeAtlasSync.entryFor(t, false, new String[]{"minecraft:elytra"},
            dropTable(Map.of("minecraft:enderman", List.of(drop("minecraft:ender_pearl", 5)))));

        assertEquals("The End", e.displayName(), "the name is still shown, so the page exists");
        assertEquals(4, e.levelCount());
        assertFalse(e.discovered());
        assertTrue(e.loot().isEmpty());
        assertTrue(e.enchants().isEmpty());
        assertTrue(e.hostileIds().isEmpty());
        assertTrue(e.mobDrops().isEmpty(), "a mob's drop table gives away who lives there");
        assertEquals("", e.bossId());
        assertEquals("", e.effectId());
    }

    // ── Per-mob drops ─────────────────────────────────────────────────────

    @Test
    void everyResidentsDropTableIsIncluded() {
        // The whole reason this field exists. A biome's own loot pool is rolled once per level
        // clear; these are rolled per kill, and they are where most of what a player walks out
        // with actually comes from. Listing only the biome pool described a minority of a run.
        BiomeTemplate t = template("graveyard", "Graveyard", 3,
            new MobPoolEntry[]{mob("minecraft:bat")},
            new MobPoolEntry[]{mob("minecraft:zombie"), mob("minecraft:skeleton")},
            mob("minecraft:wither"), null, null, null, false, null, 0);

        BiomeAtlasCodec.Entry e = BiomeAtlasSync.entryFor(t, true, new String[0], dropTable(Map.of(
            "minecraft:zombie", List.of(drop("minecraft:rotten_flesh", 8)),
            "minecraft:skeleton", List.of(drop("minecraft:bone", 5), drop("minecraft:arrow", 3)),
            "minecraft:bat", List.of(drop("minecraft:leather", 1)),
            "minecraft:wither", List.of(drop("minecraft:nether_star", 1)))));

        assertEquals(List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:bat", "minecraft:wither"),
            e.mobDrops().stream().map(BiomeAtlasCodec.MobDrops::mobId).toList(),
            "hostiles, then passives, then the boss");
        assertEquals(2, e.mobDrops().get(1).drops().size());
    }

    @Test
    void aMobThatDropsNothingIsOmittedFromTheDropTables() {
        // It still appears in the roster - the page says "drops nothing" rather than hiding
        // the creature - but it gets no drop row.
        BiomeTemplate t = template("cave", "Cave", 3, null,
            new MobPoolEntry[]{mob("minecraft:silverfish"), mob("minecraft:zombie")},
            null, null, null, null, false, null, 0);

        BiomeAtlasCodec.Entry e = BiomeAtlasSync.entryFor(t, true, new String[0],
            dropTable(Map.of("minecraft:zombie", List.of(drop("minecraft:rotten_flesh", 8)))));

        assertEquals(2, e.hostileIds().size(), "both are still listed as living here");
        assertEquals(1, e.mobDrops().size());
        assertEquals("minecraft:zombie", e.mobDrops().get(0).mobId());
    }

    @Test
    void aMobsDropsComeBackHeaviestFirst() {
        BiomeTemplate t = template("cave", "Cave", 3, null,
            new MobPoolEntry[]{mob("minecraft:skeleton")}, null, null, null, null, false, null, 0);

        BiomeAtlasCodec.Entry e = BiomeAtlasSync.entryFor(t, true, new String[0],
            dropTable(Map.of("minecraft:skeleton",
                List.of(drop("minecraft:arrow", 1), drop("minecraft:bone", 9)))));

        assertEquals("minecraft:bone", e.mobDrops().get(0).drops().get(0).id());
    }

    @Test
    void aMobAppearingInTwoRolesGetsOneDropRow() {
        // A boss that also appears in the hostile pool, or a mob listed twice for weight.
        // Two rows would show the same drop table twice on the page.
        BiomeTemplate t = template("nest", "Nest", 3, null,
            new MobPoolEntry[]{mob("minecraft:spider"), mob("minecraft:spider")},
            mob("minecraft:spider"), null, null, null, false, null, 0);

        BiomeAtlasCodec.Entry e = BiomeAtlasSync.entryFor(t, true, new String[0],
            dropTable(Map.of("minecraft:spider", List.of(drop("minecraft:string", 5)))));

        assertEquals(1, e.mobDrops().size());
    }

    @Test
    void aBiomeWithNoDropLookupStillBuilds() {
        // sendToAll runs on a datapack reload, and the compat modules that resolve the modded
        // half of the drop switch can be absent. No drops is a page with no drop rows, not a
        // failed join.
        BiomeTemplate t = template("plains", "Plains", 3, null,
            new MobPoolEntry[]{mob("minecraft:zombie")}, null, null, null, null, false, null, 0);
        assertTrue(BiomeAtlasSync.entryFor(t, true, new String[0], null).mobDrops().isEmpty());
        assertTrue(BiomeAtlasSync.entryFor(t, true, new String[0], NO_DROPS).mobDrops().isEmpty());
    }

    // ── Drop pairing ──────────────────────────────────────────────────────

    @Test
    void dropsComeBackHeaviestFirst() {
        List<BiomeAtlasCodec.Drop> out = BiomeAtlasSync.drops(
            new String[]{"a", "b", "c"}, new int[]{1, 9, 5});
        assertEquals(List.of("b", "c", "a"), out.stream().map(BiomeAtlasCodec.Drop::id).toList());
    }

    @Test
    void mismatchedItemAndWeightArraysDoNotThrow() {
        // Two separate fields on the template, both filled by a datapack loader, so a malformed
        // pack can absolutely deliver a different count of each. The guide book is the wrong
        // place to discover that via an exception on player join.
        assertEquals(1, BiomeAtlasSync.drops(new String[]{"a", "b"}, new int[]{3}).size());
        assertEquals(1, BiomeAtlasSync.drops(new String[]{"a"}, new int[]{3, 4}).size());
        assertTrue(BiomeAtlasSync.drops(null, new int[]{1}).isEmpty());
        assertTrue(BiomeAtlasSync.drops(new String[]{"a"}, null).isEmpty());
    }

    @Test
    void zeroWeightAndBlankIdEntriesAreSkipped() {
        List<BiomeAtlasCodec.Drop> out = BiomeAtlasSync.drops(
            new String[]{"a", "", "c"}, new int[]{5, 5, 0});
        assertEquals(1, out.size());
        assertEquals("a", out.get(0).id());
    }

    @Test
    void aVeryLongPoolIsCappedRatherThanSentWhole() {
        String[] ids = new String[100];
        int[] weights = new int[100];
        for (int i = 0; i < 100; i++) {
            ids[i] = "mod:item_" + i;
            weights[i] = 100 - i;
        }
        List<BiomeAtlasCodec.Drop> out = BiomeAtlasSync.drops(ids, weights);
        assertEquals(BiomeAtlasSync.MAX_DROPS, out.size());
        // The cap keeps the heaviest, not an arbitrary slice - dropping the common drops and
        // keeping the rare ones would make the page actively misleading.
        assertEquals("mod:item_0", out.get(0).id());
        assertEquals(100, out.get(0).weight());
    }

    @Test
    void aPoolWithDuplicateMobEntriesListsEachTypeOnce() {
        // Pools weight a mob by repeating it. The atlas is a roster, not a spawn table, so
        // "Zombie, Zombie, Zombie" would be noise.
        BiomeTemplate t = template("graveyard", "Graveyard", 3, null,
            new MobPoolEntry[]{mob("minecraft:zombie"), mob("minecraft:zombie"), mob("minecraft:husk")},
            null, null, null, null, false, null, 0);
        assertEquals(List.of("minecraft:zombie", "minecraft:husk"),
            BiomeAtlasSync.entryFor(t, true, new String[0], NO_DROPS).hostileIds());
    }

    // ── End to end through the wire format ────────────────────────────────

    @Test
    void aDerivedEntrySurvivesEncodingAndDecoding() {
        BiomeTemplate t = template("myaddon:glass_desert", "Glass Desert", 7,
            new MobPoolEntry[]{mob("minecraft:rabbit")},
            new MobPoolEntry[]{mob("myaddon:shard_walker")},
            mob("myaddon:glass_titan"),
            new int[]{5}, new String[]{"minecraft:fire_aspect"}, new int[]{2},
            true, "heat_haze", 3);

        BiomeAtlasCodec.Entry built = BiomeAtlasSync.entryFor(t, true,
            new String[]{"myaddon:glass_shard"}, NO_DROPS);
        BiomeAtlasCodec.Entry wired =
            BiomeAtlasCodec.decode(BiomeAtlasCodec.encode(List.of(built))).get(0);

        assertEquals(built, wired, "what the template said must be what the client receives");
    }
}
