package com.crackedgames.craftics.level;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for turning a biome id into its card-art texture.
 *
 * <p>The bug being pinned crashed the title screen every frame for any addon campaign, because
 * a namespaced biome id was concatenated into a resource path and {@code Identifier.of} rejects
 * a {@code :} there. The "no card art, draw a flat backdrop" fallback sat on the next line and
 * could never be reached. So the property that matters most here is not which texture comes
 * back - it is that <b>nothing throws</b>, whatever the id looks like.
 */
class BiomeCoverArtTest {

    // ── Craftics' own biomes ─────────────────────────────────────────────────

    @Test
    void bareId_resolvesInsideCraftics() {
        Identifier tex = BiomeCoverArt.coverTexture("plains");
        assertNotNull(tex);
        assertEquals("craftics", tex.getNamespace());
        assertEquals("textures/gui/biomes/plains.png", tex.getPath());
    }

    @Test
    void bareId_keepsUnderscores() {
        // deep_dark is a real Craftics biome; underscores are legal and must not be mangled.
        Identifier tex = BiomeCoverArt.coverTexture("deep_dark");
        assertNotNull(tex);
        assertEquals("textures/gui/biomes/deep_dark.png", tex.getPath());
    }

    // ── Addon biomes ─────────────────────────────────────────────────────────

    @Test
    void namespacedId_resolvesInsideThatAddonsNamespace() {
        // The case that crashed. An addon ships its art in its own assets folder.
        Identifier tex = BiomeCoverArt.coverTexture("crafticscobblemon:route_1");
        assertNotNull(tex);
        assertEquals("crafticscobblemon", tex.getNamespace());
        assertEquals("textures/gui/biomes/route_1.png", tex.getPath());
    }

    @Test
    void namespacedId_neverPutsTheColonInThePath() {
        // The specific illegal-character crash: a ':' surviving into the path.
        Identifier tex = BiomeCoverArt.coverTexture("myaddon:highlands");
        assertNotNull(tex);
        assertFalse(tex.getPath().contains(":"), "the namespace must not leak into the path");
    }

    @Test
    void twoAddonsWithTheSameBiomeNameDoNotCollide() {
        // Why the addon's own namespace is used rather than a shared folder: both of these
        // would be the same file under any scheme that flattens the namespace away.
        assertNotEquals(BiomeCoverArt.coverTexture("addon_a:route_1"),
            BiomeCoverArt.coverTexture("addon_b:route_1"));
    }

    // ── Nothing throws ───────────────────────────────────────────────────────

    @Test
    void malformedIds_returnNullRatherThanThrowing() {
        // Every one of these reached Identifier.of before. Null is the caller's cue to draw
        // its fallback, which is what it does for art that was simply never shipped.
        for (String bad : new String[]{
                "Uppercase", "has space", "myaddon:", ":route", "a:b:c", "!!!", "tab\there"}) {
            assertDoesNotThrow(() -> BiomeCoverArt.coverTexture(bad), "threw on: " + bad);
        }
    }

    @Test
    void nullAndBlankAreTolerated() {
        assertNull(BiomeCoverArt.coverTexture(null));
        assertNull(BiomeCoverArt.coverTexture(""));
        assertNull(BiomeCoverArt.coverTexture("   "));
    }

    @Test
    void emptyHalvesOfANamespacedIdReturnNull() {
        assertNull(BiomeCoverArt.coverTexture("myaddon:"));
        assertNull(BiomeCoverArt.coverTexture(":route_1"));
    }

    // ── The world-icon reader ────────────────────────────────────────────────

    @Test
    void classpathPath_onlyAnswersForCrafticsOwnBiomes() {
        assertEquals("/assets/craftics/textures/gui/biomes/plains.png",
            BiomeCoverArt.crafticsClasspathPath("plains"));
    }

    @Test
    void classpathPath_refusesAddonBiomes() {
        // Their art is in the addon's jar and this reader goes through Craftics' own
        // classpath, so building a path would only produce a confusing not-found.
        assertNull(BiomeCoverArt.crafticsClasspathPath("crafticscobblemon:route_1"));
        assertNull(BiomeCoverArt.crafticsClasspathPath(null));
        assertNull(BiomeCoverArt.crafticsClasspathPath(""));
    }
}
