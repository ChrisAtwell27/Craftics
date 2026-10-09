package com.crackedgames.craftics.level;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A biome's prelude: opening levels built from another template, for a dungeon that is reached
 * by crossing the country it stands in. And the boss level's own room size.
 *
 * <p>Like {@link BiomeRegistryTest}, every Minecraft-typed pool here is {@code null}: the
 * template only stores them, so none of this needs a bootstrap.
 */
class BiomePreludeTest {

    private static BiomeTemplate template(String id, String name, int startLevel, int levelCount,
                                          MobPoolEntry[] passive, MobPoolEntry[] hostile) {
        return new BiomeTemplate(
            id, name, startLevel, levelCount,
            5, 5, 0, 0,
            (Block[]) null, (Block[]) null,
            0f, 0f,
            passive, hostile, null,
            (Item[]) null, (int[]) null,
            (String[]) null, (int[]) null,
            false, null);
    }

    private static MobPoolEntry mob(String typeId) {
        return new MobPoolEntry(typeId, 1, 10, 2, 0, 1, false);
    }

    private static List<String> types(MobPoolEntry[] pool) {
        return Arrays.stream(pool).map(MobPoolEntry::entityTypeId).toList();
    }

    @Test
    @DisplayName("with no prelude every level is the biome's own")
    void noPrelude() {
        BiomeTemplate dungeon = template("dungeon", "Dungeon", 1, 7, null, null);
        assertNull(dungeon.getPrelude());
        assertEquals(0, dungeon.getPreludeLevels());
        for (int index = -1; index < 9; index++) assertSame(dungeon, dungeon.themeAt(index));
        assertSame(dungeon, dungeon.withPreludeFolded());
    }

    @Test
    @DisplayName("the opening levels come from the prelude, the rest from the biome")
    void openingLevels() {
        BiomeTemplate dungeon = template("dungeon", "Dungeon", 1, 7, null, null);
        BiomeTemplate highlands = template("dungeon", "Highlands", 1, 7, null, null);
        dungeon.withPrelude(highlands, 3);

        assertEquals(3, dungeon.getPreludeLevels());
        assertSame(highlands, dungeon.themeAt(0));
        assertSame(highlands, dungeon.themeAt(2));
        assertSame(dungeon, dungeon.themeAt(3), "the first level of the dungeon proper");
        assertSame(dungeon, dungeon.themeAt(6), "the boss level");
        assertSame(dungeon, dungeon.themeAt(-1), "an index off the front is never the prelude");
    }

    @Test
    @DisplayName("a prelude never swallows the boss level, however many levels it asks for")
    void neverTheBossLevel() {
        BiomeTemplate dungeon = template("dungeon", "Dungeon", 1, 4, null, null);
        dungeon.withPrelude(template("dungeon", "Highlands", 1, 4, null, null), 99);
        assertEquals(3, dungeon.getPreludeLevels());
        assertSame(dungeon, dungeon.themeAt(3));

        BiomeTemplate oneRoom = template("room", "Room", 1, 1, null, null);
        oneRoom.withPrelude(template("room", "Outside", 1, 1, null, null), 1);
        assertNull(oneRoom.getPrelude(), "a biome that is only its boss has no room for one");

        BiomeTemplate asked = template("dungeon", "Dungeon", 1, 7, null, null);
        asked.withPrelude(template("dungeon", "Highlands", 1, 7, null, null), 0);
        assertNull(asked.getPrelude(), "no levels is no prelude");
        asked.withPrelude(null, 3);
        assertNull(asked.getPrelude());
    }

    @Test
    @DisplayName("a level built from the prelude is still a level of the biome: same id, same range")
    void sameBiomeToEverythingThatAsks() {
        BiomeTemplate dungeon = template("dungeon", "Dungeon", 8, 7, null, null);
        BiomeTemplate highlands = template("dungeon", "Highlands", 8, 7, null, null);
        dungeon.withPrelude(highlands, 3);

        BiomeTemplate look = dungeon.themeAt(1);
        assertEquals(dungeon.biomeId, look.biomeId);
        for (int level = 8; level <= 14; level++) {
            assertEquals(dungeon.getBiomeLevelIndex(level), look.getBiomeLevelIndex(level), "level " + level);
            assertEquals(dungeon.isBossLevel(level), look.isBossLevel(level), "level " + level);
        }
    }

    @Test
    @DisplayName("moving a biome moves its prelude with it, and keeps the boss's room")
    void renumbering() {
        BiomeTemplate dungeon = template("dungeon", "Dungeon", 101, 7, null, null);
        dungeon.withPrelude(template("dungeon", "Highlands", 101, 7, null, null), 3).withBossRoom(7, 9);

        BiomeTemplate moved = dungeon.withStartLevel(15);
        assertEquals(15, moved.startLevel);
        assertEquals(7, moved.levelCount);
        assertEquals(3, moved.getPreludeLevels());
        assertNotNull(moved.getPrelude());
        assertEquals(15, moved.getPrelude().startLevel, "or its levels would count from somewhere else");
        assertEquals("Highlands", moved.getPrelude().displayName);
        assertEquals(1, moved.themeAt(1).getBiomeLevelIndex(16));
        assertTrue(moved.hasBossRoom());
        assertEquals(7, moved.getBossGridWidth());
        assertEquals(9, moved.getBossGridHeight());
    }

    @Test
    @DisplayName("the boss's room is its own size only when both sides are given")
    void bossRoom() {
        BiomeTemplate dungeon = template("dungeon", "Dungeon", 1, 7, null, null);
        assertFalse(dungeon.hasBossRoom());
        assertTrue(dungeon.withBossRoom(7, 7).hasBossRoom());
        assertFalse(dungeon.withBossRoom(7, 0).hasBossRoom());
        assertFalse(dungeon.withBossRoom(-1, 7).hasBossRoom());
        assertFalse(dungeon.hasBossLoot(), "and no treasure unless it is given some");
    }

    @Test
    @DisplayName("each part of the biome is built to its own size: open ground, rooms, the boss's room")
    void gridSizes() {
        // Rooms 5 across before the builder's edge, open ground 6, the boss's room 7.
        BiomeTemplate dungeon = template("dungeon", "Dungeon", 1, 7, null, null);
        BiomeTemplate highlands = new BiomeTemplate(
            "dungeon", "Highlands", 1, 7, 6, 6, 0, 0,
            (Block[]) null, (Block[]) null, 0f, 0f,
            (MobPoolEntry[]) null, (MobPoolEntry[]) null, null,
            (Item[]) null, (int[]) null, (String[]) null, (int[]) null, false, null);
        dungeon.withPrelude(highlands, 3).withBossRoom(7, 7);

        for (int index = 0; index < 3; index++) {
            assertArrayEquals(new int[]{10, 10}, LevelGenerator.gridSizeFor(dungeon, index, false), "level " + index);
        }
        for (int index = 3; index < 6; index++) {
            assertArrayEquals(new int[]{9, 9}, LevelGenerator.gridSizeFor(dungeon, index, false), "level " + index);
        }
        assertArrayEquals(new int[]{11, 11}, LevelGenerator.gridSizeFor(dungeon, 6, true));

        // With no room of its own the boss gets whatever the biome has grown to, as before.
        BiomeTemplate growing = new BiomeTemplate(
            "old", "Old", 1, 7, 10, 10, 1, 1,
            (Block[]) null, (Block[]) null, 0f, 0f,
            (MobPoolEntry[]) null, (MobPoolEntry[]) null, null,
            (Item[]) null, (int[]) null, (String[]) null, (int[]) null, false, null);
        assertArrayEquals(new int[]{14, 14}, LevelGenerator.gridSizeFor(growing, 0, false));
        assertArrayEquals(new int[]{20, 20}, LevelGenerator.gridSizeFor(growing, 6, true));
    }

    @Test
    @DisplayName("listing what a biome holds counts the prelude's creatures once each")
    void foldedPools() {
        BiomeTemplate dungeon = template("dungeon", "Dungeon", 1, 7,
            new MobPoolEntry[0], new MobPoolEntry[]{mob("sentry"), mob("swet")});
        BiomeTemplate highlands = template("dungeon", "Highlands", 1, 7,
            new MobPoolEntry[]{mob("phyg")}, new MobPoolEntry[]{mob("swet"), mob("zephyr")});
        dungeon.withPrelude(highlands, 3);

        BiomeTemplate folded = dungeon.withPreludeFolded();
        assertNotSame(dungeon, folded);
        assertEquals("dungeon", folded.biomeId);
        assertEquals("Dungeon", folded.displayName, "it is listed under the biome's own name");
        assertEquals(List.of("sentry", "swet", "zephyr"), types(folded.hostileMobs));
        assertEquals(List.of("phyg"), types(folded.passiveMobs));
        // The biome itself is untouched: levels are still built from the two separately.
        assertEquals(List.of("sentry", "swet"), types(dungeon.hostileMobs));
    }
}
