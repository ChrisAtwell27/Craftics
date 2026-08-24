package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The enchanter's shortlist.
 *
 * <p>Two of these tests are the feature rather than a detail of it. The shortlist must always
 * contain the outcome that actually happens, or players learn it lies and it stops softening
 * anything. And the real entry's position must carry no information, or the shortlist becomes a
 * spoiler and the gamble disappears.
 */
class EnchanterPreviewTest {

    private static final List<String> POOL = List.of(
        "Sharpness III", "Fire Aspect II", "Dull", "Hilt", "Looting II", "Unbreaking III");

    @Test
    @DisplayName("the real outcome is always on the list")
    void alwaysContainsTheTruth() {
        // The whole promise. Checked across many seeds because a shortlist that is honest most of
        // the time is the one failure mode that would poison the feature.
        for (long seed = 0; seed < 500; seed++) {
            List<String> out = EnchanterPreview.shortlist("Dull", POOL, seed);
            assertTrue(out.contains("Dull"), "seed " + seed + " dropped the real outcome");
        }
    }

    @Test
    @DisplayName("the real outcome appears exactly once")
    void realOutcomeIsNotDuplicated() {
        // The pool deliberately contains the real entry: a duplicate would narrow the odds the
        // player is reading and look like a bug besides.
        for (long seed = 0; seed < 200; seed++) {
            List<String> out = EnchanterPreview.shortlist("Dull", POOL, seed);
            assertEquals(1, out.stream().filter("Dull"::equals).count(), "seed " + seed);
        }
    }

    @Test
    @DisplayName("the real outcome's position gives nothing away")
    void positionIsNotAGiveaway() {
        // If the truth were always first or always last the shortlist would spoil the roll rather
        // than warn about it. Every slot must be reachable.
        Set<Integer> positions = new HashSet<>();
        for (long seed = 0; seed < 500; seed++) {
            positions.add(EnchanterPreview.shortlist("Dull", POOL, seed).indexOf("Dull"));
        }
        assertEquals(Set.of(0, 1, 2), positions,
            "the real outcome should be able to land in any of the three slots");
    }

    @Test
    @DisplayName("entries are distinct, so nothing reads as a duplicate line")
    void entriesAreDistinct() {
        List<String> padded = new ArrayList<>(POOL);
        padded.add("Sharpness III");
        padded.add("Sharpness III");
        for (long seed = 0; seed < 100; seed++) {
            List<String> out = EnchanterPreview.shortlist("Dull", padded, seed);
            assertEquals(out.size(), new HashSet<>(out).size(), "seed " + seed + " repeated an entry");
        }
    }

    @Test
    @DisplayName("the same seed shows the same list, so hovering twice agrees")
    void stableForASeed() {
        assertEquals(EnchanterPreview.shortlist("Dull", POOL, 12345L),
                     EnchanterPreview.shortlist("Dull", POOL, 12345L));
    }

    @Test
    @DisplayName("three options by default")
    void defaultSize() {
        assertEquals(EnchanterPreview.OPTION_COUNT,
            EnchanterPreview.shortlist("Dull", POOL, 7L).size());
    }

    @Test
    @DisplayName("a pool too small to fill the list shrinks it rather than padding or repeating")
    void smallPoolShrinks() {
        List<String> out = EnchanterPreview.shortlist("Dull", List.of("Hilt"), 3L);
        assertEquals(2, out.size());
        assertTrue(out.contains("Dull"));
        assertTrue(out.contains("Hilt"));
    }

    @Test
    @DisplayName("with nothing else possible, the shortlist is just the truth")
    void emptyPoolYieldsTheRealOutcomeAlone() {
        assertEquals(List.of("Dull"), EnchanterPreview.shortlist("Dull", List.of(), 1L));
        assertEquals(List.of("Dull"), EnchanterPreview.shortlist("Dull", null, 1L));
    }

    @Test
    @DisplayName("a pool of nothing but the real outcome does not pad with copies of it")
    void poolOfOnlyTheRealOutcome() {
        assertEquals(List.of("Dull"),
            EnchanterPreview.shortlist("Dull", List.of("Dull", "Dull"), 1L));
    }

    @Test
    @DisplayName("no real outcome, no shortlist")
    void emptyRealYieldsNothing() {
        assertTrue(EnchanterPreview.shortlist("", POOL, 1L).isEmpty());
        assertTrue(EnchanterPreview.shortlist(null, POOL, 1L).isEmpty());
    }

    // -- Labels ---------------------------------------------------------------

    @Test
    @DisplayName("enchantments read the way they do on the item")
    void enchantLabels() {
        assertEquals("Fire Aspect II", EnchanterPreview.label("fire_aspect", 2));
        assertEquals("Sharpness V", EnchanterPreview.label("sharpness", 5));
        assertEquals("Looting III", EnchanterPreview.label("looting", 3));
    }

    @Test
    @DisplayName("level I is left off, as vanilla does")
    void levelOneIsUnmarked() {
        assertEquals("Mending", EnchanterPreview.label("mending", 1));
        assertEquals("Hilt", EnchanterPreview.label("hilt", 1));
    }

    @Test
    @DisplayName("an out-of-range level falls back to digits rather than inventing a numeral")
    void unusualLevelsDegradeHonestly() {
        assertEquals("7", EnchanterPreview.roman(7));
        assertEquals("Sharpness 10", EnchanterPreview.label("sharpness", 10));
    }

    @Test
    @DisplayName("trims read as a pattern and a material")
    void trimLabels() {
        assertEquals("Dune trim in Copper", EnchanterPreview.trimLabel("dune", "copper"));
        assertEquals("Wayfinder trim in Netherite",
            EnchanterPreview.trimLabel("wayfinder", "netherite"));
    }

    @Test
    @DisplayName("an empty enchant key yields no label rather than a stray numeral")
    void emptyKeyIsEmpty() {
        assertEquals("", EnchanterPreview.label("", 3));
        assertEquals("", EnchanterPreview.label(null, 3));
    }

    @Test
    @DisplayName("bad outcomes really can show up beside good ones")
    void badOutcomesSurfaceAlongsideGoodOnes() {
        // The point of the whole feature: a player handing over a good weapon should be able to
        // see that Dull and Hilt are on the table before they commit to it.
        boolean sawBadNextToGood = false;
        for (long seed = 0; seed < 200 && !sawBadNextToGood; seed++) {
            List<String> out = EnchanterPreview.shortlist("Sharpness III", POOL, seed);
            sawBadNextToGood = out.contains("Dull") || out.contains("Hilt");
        }
        assertTrue(sawBadNextToGood);
    }

    @Test
    @DisplayName("a shortlist of one is still honest")
    void countOfOneIsJustTheTruth() {
        List<String> out = EnchanterPreview.shortlist("Dull", POOL, 1, 5L);
        assertEquals(List.of("Dull"), out);
        assertFalse(out.isEmpty());
    }
}
