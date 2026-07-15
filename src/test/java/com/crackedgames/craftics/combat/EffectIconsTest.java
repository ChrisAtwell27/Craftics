package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.combat.CombatEffects.EffectType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The icon table must cover BOTH effect vocabularies. Players use
 * {@link CombatEffects.EffectType} display names ("Regeneration"); enemies send past-tense
 * verbs from the combat sync ("Regenerating"). If either side drifts from the table, the
 * effect silently renders as the unknown marker - which looks like a bug but throws nothing,
 * so only a test catches it.
 */
class EffectIconsTest {

    /** Exactly the names CombatManager appends to the enemy sync blob. Keep in step with it. */
    private static final List<String> ENEMY_SYNC_NAMES = List.of(
        "Stunned", "Enraged", "Marked(3t)", "Slowed(2t)", "Poisoned(3t)",
        "Weakened(-2ATK,3t)", "Burning(2t)", "Soaked(3t)", "Confused(2t)",
        "Blinded(2t)", "Exposed(-2DEF,3t)", "Bleeding(3 stacks)",
        "Withered(3t)", "Frozen", "Taunting",
        "Regenerating(3t)", "Absorption(4HP,3t)", "Resistant(3t)",
        "Strengthened(+3ATK,3t)", "Hastened(+1SPD,3t)", "SlowFalling(3t)");

    /** Every player effect must have a real icon - none may fall through to UNKNOWN. */
    @Test
    void everyPlayerEffectHasAnIcon() {
        List<String> missing = new ArrayList<>();
        for (EffectType type : EffectType.values()) {
            if (!EffectIcons.isKnown(type.displayName)) {
                missing.add(type.displayName);
            }
        }
        assertTrue(missing.isEmpty(),
            "these player effects would render as the unknown marker: " + missing);
    }

    /** Every name the enemy sync actually emits must have a real icon. */
    @Test
    void everyEnemySyncEffectHasAnIcon() {
        List<String> missing = new ArrayList<>();
        for (String name : ENEMY_SYNC_NAMES) {
            if (!EffectIcons.isKnown(name)) missing.add(name);
        }
        assertTrue(missing.isEmpty(),
            "these enemy effects would render as the unknown marker: " + missing);
    }

    /**
     * The reconciliation that justifies this table existing: the same condition on a player
     * and on an enemy must produce the SAME icon, despite arriving under different names.
     */
    @Test
    void theTwoVocabulariesAgreeOnSharedEffects() {
        assertSameIcon("Regeneration", "Regenerating(3t)");
        assertSameIcon("Poison", "Poisoned(3t)");
        assertSameIcon("Wither", "Withered(3t)");
        assertSameIcon("Burning", "Burning(2t)");
        assertSameIcon("Bleeding", "Bleeding(3 stacks)");
        assertSameIcon("Slowness", "Slowed(2t)");
        assertSameIcon("Weakness", "Weakened(-2ATK,3t)");
        assertSameIcon("Blindness", "Blinded(2t)");
        assertSameIcon("Confusion", "Confused(2t)");
        assertSameIcon("Resistance", "Resistant(3t)");
        assertSameIcon("Strength", "Strengthened(+3ATK,3t)");
        assertSameIcon("Haste", "Hastened(+1SPD,3t)");
        assertSameIcon("Soaked", "Soaked(3t)");
    }

    private static void assertSameIcon(String playerName, String enemyName) {
        EffectIcons.Icon a = EffectIcons.forName(playerName);
        EffectIcons.Icon b = EffectIcons.forName(enemyName);
        assertEquals(a, b,
            playerName + " and " + enemyName + " are the same condition and must share an icon");
        assertNotEquals(EffectIcons.UNKNOWN, a, playerName + " resolved to the unknown marker");
    }

    /** The wire format appends duration/magnitude detail; the lookup must see through it. */
    @Test
    void trailingDetailIsStrippedForLookup() {
        assertEquals("poisoned", EffectIcons.baseName("Poisoned(3t)"));
        assertEquals("weakened", EffectIcons.baseName("Weakened(-2ATK,3t)"));
        assertEquals("bleeding", EffectIcons.baseName("Bleeding(3 stacks)"));
        assertEquals("regeneration", EffectIcons.baseName("Regeneration II"), "roman level dropped");
        assertEquals("strength", EffectIcons.baseName("  Strength  "), "trimmed");
    }

    /** An unrecognized addon effect must still render as SOMETHING, never crash or vanish. */
    @Test
    void unknownEffectsFallBackRatherThanVanish() {
        EffectIcons.Icon icon = EffectIcons.forName("mymod:frostbite");
        assertEquals(EffectIcons.UNKNOWN, icon);
        assertFalse(icon.texture().isEmpty(), "the fallback must still name a sprite");
        assertFalse(EffectIcons.isKnown("mymod:frostbite"));
    }

    @Test
    void nullAndBlankNamesAreSafe() {
        assertEquals(EffectIcons.UNKNOWN, EffectIcons.forName(null));
        assertEquals(EffectIcons.UNKNOWN, EffectIcons.forName("   "));
        assertEquals("", EffectIcons.baseName(null));
    }

    /** Debuffs must be flagged harmful so they tint red rather than green. */
    @Test
    void harmfulFlagMatchesTheDebuffClassification() {
        assertTrue(EffectIcons.forName("Poison").harmful());
        assertTrue(EffectIcons.forName("Wither").harmful());
        assertTrue(EffectIcons.forName("Bleeding").harmful());
        assertTrue(EffectIcons.forName("Stunned").harmful());
        assertFalse(EffectIcons.forName("Regeneration").harmful());
        assertFalse(EffectIcons.forName("Strength").harmful());
        assertFalse(EffectIcons.forName("Absorption").harmful());
    }

    /**
     * The harmful flag must agree with {@link CombatEffects#isDebuff} for every player effect.
     * Two independent classifications of the same thing WILL drift apart otherwise.
     */
    @Test
    void harmfulFlagAgreesWithCombatEffectsIsDebuff() {
        for (EffectType type : EffectType.values()) {
            boolean tableSaysHarmful = EffectIcons.forName(type.displayName).harmful();
            boolean enumSaysDebuff = CombatEffects.isDebuff(type);
            assertEquals(enumSaysDebuff, tableSaysHarmful,
                type.displayName + ": EffectIcons and CombatEffects.isDebuff disagree on whether "
                    + "this is a debuff");
        }
    }

    /** Icons must be visually distinguishable - two effects sharing a sprite is a bug. */
    @Test
    void everyEffectIsVisuallyDistinct() {
        Set<String> seen = new HashSet<>();
        List<String> clashes = new ArrayList<>();
        for (EffectType type : EffectType.values()) {
            EffectIcons.Icon icon = EffectIcons.forName(type.displayName);
            if (!seen.add(icon.texture())) {
                clashes.add(type.displayName + " -> " + icon.texture());
            }
        }
        assertTrue(clashes.isEmpty(),
            "these effects reuse an earlier effect's sprite: " + clashes);
    }

    /**
     * Every sprite an effect names must actually EXIST, as a real 8x8 PNG.
     *
     * <p>This is the test that matters most. A missing icon does not throw - the renderer just
     * draws a missing-texture quad, or nothing - so the failure is silent and invisible, which
     * is exactly how the first version of this feature shipped broken. Adding an effect to the
     * table without re-running tools/generate_effect_icons.py fails here instead of in game.
     */
    @Test
    void everyReferencedSpriteExistsAndIsEightByEight() {
        java.nio.file.Path dir = effectsDir();

        List<String> names = new ArrayList<>();
        for (EffectType type : EffectType.values()) {
            names.add(EffectIcons.forName(type.displayName).texture());
        }
        for (String enemyName : ENEMY_SYNC_NAMES) {
            names.add(EffectIcons.forName(enemyName).texture());
        }
        names.add(EffectIcons.UNKNOWN.texture());

        List<String> problems = new ArrayList<>();
        for (String name : names) {
            java.nio.file.Path png = dir.resolve(name + ".png");
            if (!java.nio.file.Files.isRegularFile(png)) {
                problems.add("missing sprite: " + png);
                continue;
            }
            try {
                byte[] data = java.nio.file.Files.readAllBytes(png);
                // PNG magic, then IHDR width/height as big-endian ints at bytes 16..23.
                assertEquals((byte) 0x89, data[0], name + " is not a PNG");
                int w = ((data[16] & 0xFF) << 24) | ((data[17] & 0xFF) << 16)
                    | ((data[18] & 0xFF) << 8) | (data[19] & 0xFF);
                int h = ((data[20] & 0xFF) << 24) | ((data[21] & 0xFF) << 16)
                    | ((data[22] & 0xFF) << 8) | (data[23] & 0xFF);
                if (w != 8 || h != 8) {
                    problems.add(name + ".png is " + w + "x" + h + ", expected 8x8");
                }
            } catch (java.io.IOException e) {
                problems.add("could not read " + png + ": " + e.getMessage());
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * Locate the shared {@code effects} sprite directory, whatever directory the test runs from.
     *
     * <p>The sprites live once at the repo root, but under Stonecutter the test JVM's working
     * directory is a per-version subproject ({@code versions/1.21.1}), not the root - so a plain
     * relative {@code src/main/resources/...} path resolves to a directory that does not exist
     * and every sprite reads as missing. Walking up from the working directory finds the real
     * tree from either location.
     */
    private static java.nio.file.Path effectsDir() {
        String rel = "src/main/resources/assets/craftics/textures/gui/effects";
        java.nio.file.Path start = java.nio.file.Path.of("").toAbsolutePath();
        for (java.nio.file.Path p = start; p != null; p = p.getParent()) {
            java.nio.file.Path candidate = p.resolve(rel);
            if (java.nio.file.Files.isDirectory(candidate)) return candidate;
        }
        throw new IllegalStateException("could not find " + rel + " above " + start);
    }

    /**
     * The enemy blob mixes effects with metadata. Pulling out the effects means everything
     * that ISN'T a known key= tag, the entity type id, or the bare "ally" marker.
     */
    @Test
    void enemyBlobYieldsOnlyTheEffects() {
        String blob = "zombie;name=Rotter;atk=5;def=2;spd=3;range=1;mv=walk;ally"
            + ";Poisoned(3t);Burning(2t);Weakened(-2ATK,3t)";
        List<String> effects = EffectIcons.parseEnemyEffects(blob);
        assertEquals(List.of("Poisoned(3t)", "Burning(2t)", "Weakened(-2ATK,3t)"), effects);
    }

    /** The type id is the first segment and must never be mistaken for an effect. */
    @Test
    void enemyBlobWithNoEffectsYieldsNothing() {
        assertTrue(EffectIcons.parseEnemyEffects("skeleton;atk=4;def=1").isEmpty());
        assertTrue(EffectIcons.parseEnemyEffects("skeleton").isEmpty());
        assertTrue(EffectIcons.parseEnemyEffects("").isEmpty());
        assertTrue(EffectIcons.parseEnemyEffects(null).isEmpty());
    }

    /** Enchantments ride the same blob and must not be drawn as status effects. */
    @Test
    void enemyBlobDoesNotTreatEnchantsAsEffects() {
        List<String> effects =
            EffectIcons.parseEnemyEffects("zombie;ench=sharpness:3,unbreaking:2;Burning(2t)");
        assertEquals(List.of("Burning(2t)"), effects, "ench= is metadata, not an effect");
    }

    /** Players use a different string format again: pipe-joined with duration suffixes. */
    @Test
    void playerEffectStringIsSplitOnPipes() {
        List<String> effects = EffectIcons.parsePlayerEffects("Poison (2t) | Strength II (5t)");
        assertEquals(List.of("Poison (2t)", "Strength II (5t)"), effects);
        assertEquals(EffectIcons.forName("Poison"), EffectIcons.forName(effects.get(0)));
        assertEquals(EffectIcons.forName("Strength"), EffectIcons.forName(effects.get(1)));
    }

    /** "Hidden" is a stealth HUD marker the server injects, not a real effect. */
    @Test
    void playerHiddenMarkerIsNotAnEffect() {
        List<String> effects = EffectIcons.parsePlayerEffects("Hidden | Poison (2t)");
        assertEquals(List.of("Poison (2t)"), effects);
        assertTrue(EffectIcons.parsePlayerEffects("Hidden").isEmpty());
    }

    @Test
    void emptyPlayerEffectStringYieldsNothing() {
        assertTrue(EffectIcons.parsePlayerEffects("").isEmpty());
        assertTrue(EffectIcons.parsePlayerEffects(null).isEmpty());
    }

    /** Particles are limited to the physically obvious effects, not sprayed on everything. */
    @Test
    void particlesAreLimitedToTheObviousEffects() {
        assertTrue(EffectIcons.hasParticles("Burning(2t)"));
        assertTrue(EffectIcons.hasParticles("Poisoned(3t)"));
        assertTrue(EffectIcons.hasParticles("Soaked"));
        assertTrue(EffectIcons.hasParticles("Regeneration"));

        assertFalse(EffectIcons.hasParticles("Marked(3t)"), "no natural particle read");
        assertFalse(EffectIcons.hasParticles("Exposed(-2DEF,3t)"));
        assertFalse(EffectIcons.hasParticles("Haste"));
        assertFalse(EffectIcons.hasParticles("Luck"));
    }
}
