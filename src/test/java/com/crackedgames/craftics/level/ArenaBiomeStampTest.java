package com.crackedgames.craftics.level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The arena cache is keyed by level number and validated by a biome stamp. The stamp has to
 * describe the arena that was actually BUILT, not just the biome the level registry resolves
 * to, or a sub-biome arena silently reuses its parent biome's terrain.
 *
 * <p>Real bug this pins: the Pale Garden (a "forest/pale_garden" sub-biome of forest, level 18)
 * was stamped as plain "forest", because the stamp took BiomeTemplate.biomeId and ignored the
 * level's arena override. On re-entry the check read "forest".equals("forest"), hit the cache,
 * and returned a scan of the stale plain-forest blocks. The player fought Creakings standing in
 * an ordinary Dark Forest arena, and pale_garden.schem was never loaded.
 *
 * <p>These are pure string/arithmetic checks on {@link ArenaBiomeStamp}, so they run with no
 * Minecraft bootstrap.
 */
class ArenaBiomeStampTest {

    /**
     * A biome whose levels are not all one kind of place says which part a level is and how
     * big it was built. Real case: the Aether dungeons were cached as 14x14 rooms under the
     * bare biome name, then given three levels above ground and smaller rooms. The name alone
     * still matched, so the old rooms would have been reused for all of them.
     */
    @Test
    void layoutStampTellsPartsAndSizesApart() {
        String bare = ArenaBiomeStamp.effectiveBiomeId("aether_bronze_dungeon", null);
        String surface = ArenaBiomeStamp.withLayout(bare, 'p', 10, 10);
        String room = ArenaBiomeStamp.withLayout(bare, 'r', 9, 9);
        String bossRoom = ArenaBiomeStamp.withLayout(bare, 'b', 11, 11);

        assertFalse(ArenaBiomeStamp.stampMatches(bare, surface), "a cache from before is rebuilt");
        assertFalse(ArenaBiomeStamp.stampMatches(bare, room));
        assertFalse(ArenaBiomeStamp.stampMatches(room, surface), "a room is not open ground");
        assertFalse(ArenaBiomeStamp.stampMatches(room, bossRoom));
        assertFalse(ArenaBiomeStamp.stampMatches(room, ArenaBiomeStamp.withLayout(bare, 'r', 14, 14)),
            "nor a room of another size");
        assertTrue(ArenaBiomeStamp.stampMatches(room, ArenaBiomeStamp.withLayout(bare, 'r', 9, 9)),
            "built once, reused after");
        // The save packs its fields with commas and finds the stamp by its "b=" prefix.
        assertFalse(surface.contains(","));
        assertTrue(surface.startsWith(bare));
    }

    @Test
    void plainBiomeStampsAsItsOwnId() {
        assertEquals("forest", ArenaBiomeStamp.effectiveBiomeId("forest", null));
        assertEquals("desert", ArenaBiomeStamp.effectiveBiomeId("desert", null));
    }

    @Test
    void blankOverrideIsIgnored() {
        assertEquals("forest", ArenaBiomeStamp.effectiveBiomeId("forest", ""));
        assertEquals("forest", ArenaBiomeStamp.effectiveBiomeId("forest", "   "));
    }

    /** The bug: a sub-biome override must produce a DIFFERENT stamp than its parent. */
    @Test
    void subBiomeOverrideStampsAsTheOverride() {
        assertEquals("forest/pale_garden",
            ArenaBiomeStamp.effectiveBiomeId("forest", "forest/pale_garden"));
    }

    /**
     * The exact cache-validation the Pale Garden hit. A plain-forest stamp must NOT satisfy a
     * Pale Garden level, or the stale arena gets reused.
     */
    @Test
    void staleParentBiomeStampDoesNotSatisfyASubBiomeLevel() {
        String cached = "forest";                                  // what the old build stored
        String wanted = ArenaBiomeStamp.effectiveBiomeId("forest", "forest/pale_garden");
        assertFalse(ArenaBiomeStamp.stampMatches(cached, wanted),
            "a plain 'forest' arena must not be reused for the Pale Garden");
    }

    /** ...and the reverse: a Pale Garden arena must not be reused for a plain forest level. */
    @Test
    void staleSubBiomeStampDoesNotSatisfyAPlainLevel() {
        String cached = "forest/pale_garden";
        String wanted = ArenaBiomeStamp.effectiveBiomeId("forest", null);
        assertFalse(ArenaBiomeStamp.stampMatches(cached, wanted),
            "a Pale Garden arena must not be reused for an ordinary Dark Forest level");
    }

    @Test
    void matchingStampIsReused() {
        assertTrue(ArenaBiomeStamp.stampMatches("forest", "forest"));
        assertTrue(ArenaBiomeStamp.stampMatches("forest/pale_garden", "forest/pale_garden"));
    }

    /**
     * Older saves predate the stamp entirely, and an unstamped arena is rebuilt rather than
     * reused. Biomes went from five levels to seven, so every global level number resolves to a
     * different biome than it did when those arenas were built: global 51 was Soul Sand Valley I
     * and is now Underground Caverns II. Trusting the unstamped row is what left a cave level
     * being fought in a netherrack arena, permanently - the corruption check only looks for
     * missing floor, and that arena's floor is intact.
     */
    @Test
    void unstampedLegacySavesAreRebuilt() {
        assertFalse(ArenaBiomeStamp.stampMatches(null, "forest"),
            "an arena with no stamp cannot be shown to belong to this level's biome");
        assertFalse(ArenaBiomeStamp.stampMatches(null, "cave"),
            "the cave-level-in-a-nether-arena case: unstamped must not be reused");
    }

    /**
     * A hand-built room cached before it was laid down the way it is now is rebuilt:
     * the same level, the same size, and still not the same room.
     */
    @Test
    void aRoomLaidDownTheOldWayIsRebuilt() {
        String old = ArenaBiomeStamp.withLayout("aether_silver_dungeon", 'b', 25, 21);
        String now = ArenaBiomeStamp.withRevision(old, 2);
        assertEquals("aether_silver_dungeon@b25x21#2", now);
        assertFalse(ArenaBiomeStamp.stampMatches(old, now));
        assertTrue(ArenaBiomeStamp.stampMatches(now, ArenaBiomeStamp.withRevision(old, 2)));
        assertFalse(ArenaBiomeStamp.stampMatches(now, ArenaBiomeStamp.withRevision(old, 3)));
    }
}
