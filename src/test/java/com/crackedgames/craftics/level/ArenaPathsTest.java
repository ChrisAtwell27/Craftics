package com.crackedgames.craftics.level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Where a biome's arena schematics are looked up.
 *
 * <p>The bug this guards: the lookup glued the biome id straight into a {@code craftics:} resource
 * path. Every built-in biome has a bare id ({@code plains}), so that worked for all of them. An
 * addon biome is told to use a namespaced id ({@code mymod:cavern}), and a colon is not legal in
 * a resource path, so building its arena threw before it could even fall back to a generated
 * one. Addon biomes now keep their arenas under their own namespace.
 */
class ArenaPathsTest {

    @Test
    void builtInBiomeLivesUnderCraftics() {
        ArenaPaths.Location loc = ArenaPaths.of("plains");

        assertEquals("craftics", loc.namespace());
        assertEquals("plains", loc.folder());
        assertFalse(loc.namespaced());
        assertEquals("arenas/plains/1.schem", loc.file("1.schem"));
        assertEquals("arenas/plains/boss.schem", loc.file("boss.schem"));
    }

    @Test
    void subBiomeKeepsItsSlashAndHasASingleFile() {
        ArenaPaths.Location loc = ArenaPaths.of("forest/pale_garden");

        assertEquals("craftics", loc.namespace());
        assertEquals("forest/pale_garden", loc.folder());
        assertEquals("arenas/forest/pale_garden.schem", loc.singleFile());
    }

    @Test
    void addonBiomeLivesUnderItsOwnNamespace() {
        ArenaPaths.Location loc = ArenaPaths.of("mymod:cavern");

        assertEquals("mymod", loc.namespace());
        assertEquals("cavern", loc.folder());
        assertTrue(loc.namespaced());
        assertEquals("arenas/cavern/1.schem", loc.file("1.schem"));
    }

    @Test
    void spellingOutTheCrafticsNamespaceChangesNothing() {
        ArenaPaths.Location loc = ArenaPaths.of("craftics:plains");

        assertEquals("craftics", loc.namespace());
        assertEquals("plains", loc.folder());
        assertFalse(loc.namespaced());
    }

    @Test
    void everyBuiltInBiomeIdResolvesToItself() {
        // The fix must not move a single shipped arena.
        String[] builtIn = {
            "plains", "forest", "desert", "jungle", "river", "mountain", "snowy", "cave", "deep_dark",
            "nether_wastes", "soul_sand_valley", "crimson_forest", "warped_forest", "basalt_deltas",
            "outer_end_islands", "end_city", "chorus_grove", "dragons_nest",
            "trial_chamber", "trial_chamber_ominous", "pillager_camp", "bastille", "forest/pale_garden",
        };
        for (String id : builtIn) {
            ArenaPaths.Location loc = ArenaPaths.of(id);
            assertNotNull(loc, id);
            assertEquals("craftics", loc.namespace(), id);
            assertEquals(id, loc.folder(), id);
        }
    }

    @Test
    void anIdThatCannotNameAFileResolvesToNothing() {
        // null rather than an exception: the caller falls back to a generated arena.
        assertNull(ArenaPaths.of(null));
        assertNull(ArenaPaths.of(""));
        assertNull(ArenaPaths.of("   "));
        assertNull(ArenaPaths.of("My Mod:cavern"));
        assertNull(ArenaPaths.of("mymod:Cavern"));
        assertNull(ArenaPaths.of("a:b:c"));
        assertNull(ArenaPaths.of(":cavern"));
        assertNull(ArenaPaths.of("mymod:"));
    }

    @Test
    void anIdCannotClimbOutOfTheArenaFolder() {
        // Biome ids come from datapack JSON and are used to build paths on disk.
        assertNull(ArenaPaths.of("../secrets"));
        assertNull(ArenaPaths.of("mymod:../../secrets"));
        assertNull(ArenaPaths.of("mymod:a/./b"));
        assertNull(ArenaPaths.of("/plains"));
        assertNull(ArenaPaths.of("plains/"));
        assertNull(ArenaPaths.of("forest//pale_garden"));
    }
}
