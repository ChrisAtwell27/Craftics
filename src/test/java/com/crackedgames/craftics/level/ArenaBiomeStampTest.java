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
}
