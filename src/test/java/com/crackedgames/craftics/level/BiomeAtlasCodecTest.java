package com.crackedgames.craftics.level;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the biome atlas wire format.
 *
 * <p>A delimited format has exactly one interesting failure mode - a value containing a
 * delimiter - and one interesting property: whatever went in comes back out. Both are checked
 * here, along with the malformed-input cases, because this is decoded inside a packet handler
 * where an exception is a disconnect rather than a log line.
 */
class BiomeAtlasCodecTest {

    private static BiomeAtlasCodec.Entry sample() {
        return new BiomeAtlasCodec.Entry(
            "deep_dark", "Deep Dark", 5, true, true, "minecraft:warden",
            List.of("minecraft:skeleton", "minecraft:sculk_creeper"),
            List.of("minecraft:bat"),
            List.of(new BiomeAtlasCodec.Drop("minecraft:echo_shard", 10),
                    new BiomeAtlasCodec.Drop("minecraft:diamond", 3)),
            List.of(new BiomeAtlasCodec.Drop("minecraft:mending", 1)),
            "darkness", 2,
            List.of(new BiomeAtlasCodec.MobDrops("minecraft:skeleton",
                        List.of(new BiomeAtlasCodec.Drop("minecraft:bone", 5),
                                new BiomeAtlasCodec.Drop("minecraft:arrow", 3))),
                    new BiomeAtlasCodec.MobDrops("minecraft:warden",
                        List.of(new BiomeAtlasCodec.Drop("minecraft:echo_shard", 4)))));
    }

    /** A biome carrying nothing but a name, for the empty-list cases. */
    private static BiomeAtlasCodec.Entry bare(String id, String name, boolean discovered) {
        return new BiomeAtlasCodec.Entry(id, name, 3, discovered, false, "",
            List.of(), List.of(), List.of(), List.of(), "", 0, List.of());
    }

    @Test
    void roundTripsEveryField() {
        List<BiomeAtlasCodec.Entry> decoded =
            BiomeAtlasCodec.decode(BiomeAtlasCodec.encode(List.of(sample())));
        assertEquals(1, decoded.size());
        // Records compare by value, so this covers every field at once - including any field
        // added later, which is the case a hand-written list of assertions quietly misses.
        assertEquals(sample(), decoded.get(0));
    }

    @Test
    void fieldsDoNotShiftPositions() {
        // The named check behind the equality above: a field inserted rather than appended
        // shifts everything after it, and every value still decodes - into the wrong slot.
        BiomeAtlasCodec.Entry e = BiomeAtlasCodec.decode(BiomeAtlasCodec.encode(List.of(sample()))).get(0);
        assertEquals("deep_dark", e.biomeId());
        assertEquals("Deep Dark", e.displayName());
        assertEquals(5, e.levelCount());
        assertTrue(e.discovered());
        assertTrue(e.nightLevel());
        assertEquals("minecraft:warden", e.bossId());
        assertEquals(List.of("minecraft:skeleton", "minecraft:sculk_creeper"), e.hostileIds());
        assertEquals(List.of("minecraft:bat"), e.passiveIds());
        assertEquals(10, e.loot().get(0).weight());
        assertEquals("minecraft:mending", e.enchants().get(0).id());
        assertEquals("darkness", e.effectId());
        assertEquals(2, e.effectStartLevel());
        assertEquals(2, e.mobDrops().size());
        assertEquals("minecraft:skeleton", e.mobDrops().get(0).mobId());
        assertEquals("minecraft:bone", e.mobDrops().get(0).drops().get(0).id());
        assertEquals(5, e.mobDrops().get(0).drops().get(0).weight());
    }

    @Test
    void mobDropsSurviveTheirTwoExtraNestingLevels() {
        // The per-mob field nests a list of lists inside one delimited field. Two more
        // separators means two more chances to split on the wrong one, and the failure is
        // quiet: a mob's drops attach to the wrong mob, or a drop list collapses into an id.
        BiomeAtlasCodec.Entry out =
            BiomeAtlasCodec.decode(BiomeAtlasCodec.encode(List.of(sample()))).get(0);
        assertEquals(sample().mobDrops(), out.mobDrops());
    }

    @Test
    void aMobThatDropsNothingIsNotGivenARow() {
        // An empty drop list encodes to nothing, so a row for it would decode as malformed
        // and be dropped anyway. Skipping it on the way out keeps the two sides agreeing.
        BiomeAtlasCodec.Entry in = new BiomeAtlasCodec.Entry(
            "plains", "Plains", 3, true, false, "", List.of(), List.of(), List.of(), List.of(),
            "", 0, List.of(new BiomeAtlasCodec.MobDrops("minecraft:bat", List.of())));
        assertTrue(BiomeAtlasCodec.decode(BiomeAtlasCodec.encode(List.of(in))).get(0)
            .mobDrops().isEmpty());
    }

    @Test
    void aDelimiterInAMobIdCannotDetachItsDrops() {
        BiomeAtlasCodec.Entry in = new BiomeAtlasCodec.Entry(
            "plains", "Plains", 3, true, false, "", List.of(), List.of(), List.of(), List.of(),
            "", 0, List.of(new BiomeAtlasCodec.MobDrops("mod:we~ird=mob",
                List.of(new BiomeAtlasCodec.Drop("minecraft:bone", 2)))));
        List<BiomeAtlasCodec.Entry> out =
            BiomeAtlasCodec.decode(BiomeAtlasCodec.encode(List.of(in)));
        assertEquals(1, out.size());
        assertEquals(1, out.get(0).mobDrops().size(), "the id must not split into extra mobs");
        assertEquals(1, out.get(0).mobDrops().get(0).drops().size());
    }

    @Test
    void itemIdsKeepTheirNamespaceColon() {
        // The weight separator is deliberately not a colon, because every id contains one.
        // Splitting on the wrong character would turn "minecraft:diamond*3" into "minecraft".
        List<BiomeAtlasCodec.Entry> decoded =
            BiomeAtlasCodec.decode(BiomeAtlasCodec.encode(List.of(sample())));
        assertEquals("minecraft:diamond", decoded.get(0).loot().get(1).id());
        assertEquals(3, decoded.get(0).loot().get(1).weight());
    }

    @Test
    void modIdsWithUnderscoresAndDotsSurvive() {
        BiomeAtlasCodec.Entry in = new BiomeAtlasCodec.Entry(
            "someaddon:the_burning_wastes", "The Burning Wastes", 3, true, false,
            "some.addon:fire_lord", List.of("some.addon:ash_walker"), List.of(),
            List.of(new BiomeAtlasCodec.Drop("some.addon:ember_core", 7)), List.of(),
            "some.addon:ashfall", 1,
            List.of(new BiomeAtlasCodec.MobDrops("some.addon:ash_walker",
                List.of(new BiomeAtlasCodec.Drop("some.addon:cinder", 2)))));
        BiomeAtlasCodec.Entry out = BiomeAtlasCodec.decode(BiomeAtlasCodec.encode(List.of(in))).get(0);
        assertEquals(in, out);
    }

    @Test
    void multipleBiomesStayInOrderAndStaySeparate() {
        List<BiomeAtlasCodec.Entry> out = BiomeAtlasCodec.decode(
            BiomeAtlasCodec.encode(List.of(bare("plains", "Plains", true),
                                           bare("desert", "Desert", false))));
        assertEquals(2, out.size());
        assertEquals("plains", out.get(0).biomeId());
        assertEquals("desert", out.get(1).biomeId());
        assertTrue(out.get(0).discovered());
        assertFalse(out.get(1).discovered());
    }

    @Test
    void aDelimiterInADisplayNameCannotCorruptTheStream() {
        // A datapack picks display names, so this is reachable by anyone shipping a pack. If
        // the delimiter survived encoding, this one biome would be read as several, and every
        // field after it would be shifted.
        BiomeAtlasCodec.Entry hostile = bare("weird", "Fire; Ice | Ash, Dust * Bone", true);

        List<BiomeAtlasCodec.Entry> out = BiomeAtlasCodec.decode(
            BiomeAtlasCodec.encode(List.of(hostile, bare("plains", "Plains", true))));
        assertEquals(2, out.size(), "delimiters in a name must not split it into extra biomes");
        assertEquals("weird", out.get(0).biomeId());
        assertEquals(3, out.get(0).levelCount(), "fields must not shift");
        assertEquals("plains", out.get(1).biomeId());
    }

    @Test
    void cleanStripsEveryDelimiter() {
        String cleaned = BiomeAtlasCodec.clean("a;b|c,d*e");
        assertFalse(cleaned.contains(";"));
        assertFalse(cleaned.contains("|"));
        assertFalse(cleaned.contains(","));
        assertFalse(cleaned.contains("*"));
    }

    @Test
    void emptyListsRoundTripAsEmptyRatherThanAsOneBlankEntry() {
        // The classic split() trap: "".split(",") yields one empty string, not none.
        BiomeAtlasCodec.Entry out =
            BiomeAtlasCodec.decode(BiomeAtlasCodec.encode(List.of(bare("void", "Void", true)))).get(0);
        assertTrue(out.hostileIds().isEmpty());
        assertTrue(out.passiveIds().isEmpty());
        assertTrue(out.loot().isEmpty());
        assertTrue(out.enchants().isEmpty());
        assertEquals("", out.bossId());
        assertEquals("", out.effectId());
        assertTrue(out.mobDrops().isEmpty());
    }

    @Test
    void malformedInputYieldsFewerEntriesRatherThanAnException() {
        assertTrue(BiomeAtlasCodec.decode(null).isEmpty());
        assertTrue(BiomeAtlasCodec.decode("").isEmpty());
        assertTrue(BiomeAtlasCodec.decode("nonsense").isEmpty(), "too few fields, so skipped");
        assertTrue(BiomeAtlasCodec.decode(";;;").isEmpty());
        assertTrue(BiomeAtlasCodec.decode("||||||||||||").isEmpty(), "empty id, so skipped");
    }

    @Test
    void aTruncatedEntryIsDroppedWithoutTakingItsNeighbourWithIt() {
        String good = BiomeAtlasCodec.encode(List.of(bare("plains", "Plains", true)));
        List<BiomeAtlasCodec.Entry> out = BiomeAtlasCodec.decode("broken|entry;" + good);
        assertEquals(1, out.size());
        assertEquals("plains", out.get(0).biomeId());
    }

    @Test
    void aShortEntryFromAnOlderFormatIsDroppedRatherThanMisread() {
        // Nine fields was the shape before passives, biome effects and mob drops were added.
        // Reading one as if it were current would put a loot pool in the passive-mob slot.
        assertTrue(BiomeAtlasCodec.decode("plains|Plains|3|1|0||||").isEmpty());
        assertTrue(BiomeAtlasCodec.decode("plains|Plains|3|1|0|||||||").isEmpty(),
            "twelve fields, the shape before mob drops");
    }

    @Test
    void nonNumericCountsDecodeToZeroRatherThanThrowing() {
        List<BiomeAtlasCodec.Entry> out = BiomeAtlasCodec.decode("plains|Plains|x|1|0|||||||y|");
        assertEquals(1, out.size());
        assertEquals(0, out.get(0).levelCount());
        assertEquals(0, out.get(0).effectStartLevel());
    }

    @Test
    void encodingNothingProducesNothing() {
        assertEquals("", BiomeAtlasCodec.encode(null));
        assertEquals("", BiomeAtlasCodec.encode(List.of()));
    }
}
