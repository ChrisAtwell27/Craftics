package com.crackedgames.craftics.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ping vocabulary and the wheel layout that selects from it.
 *
 * <p>The layout is the part worth testing. A ping wheel that highlights the wrong option is not
 * a crash and not obviously a bug - every option lights up, the wheel feels responsive, and the
 * only symptom is teammates receiving the wrong message. The failure is invisible from inside
 * the game, which is exactly why it belongs in a test.
 */
class PingTypeTest {

    private static final int DEAD_ZONE = 18;

    // ── Wheel layout ──────────────────────────────────────────────────────

    @Test
    void flickingAtAnOptionSelectsThatOption() {
        // Walk each option's own direction and check it comes back. Straight up is the first
        // option and they run clockwise; if the layout is ever rotated or reversed, every one
        // of these lands on a neighbour.
        PingType[] all = PingType.values();
        for (int i = 0; i < all.length; i++) {
            double angle = i * (Math.PI * 2 / all.length);
            int dx = (int) Math.round(Math.sin(angle) * 60);
            int dy = (int) Math.round(-Math.cos(angle) * 60);
            assertEquals(all[i], PingType.fromOffset(dx, dy, DEAD_ZONE),
                "flicking toward option " + i + " must select it");
        }
    }

    @Test
    void straightUpIsTheFirstOption() {
        // Screen Y grows downward, so "up" is negative dy. Getting this backwards would put
        // every option exactly opposite where it is drawn - the single most likely mistake here.
        assertEquals(PingType.LOOK, PingType.fromOffset(0, -60, DEAD_ZONE));
    }

    @Test
    void deadZoneSelectsNothing() {
        // A tap must not resolve to whichever option the cursor's sub-pixel jitter pointed at.
        assertNull(PingType.fromOffset(0, 0, DEAD_ZONE));
        assertNull(PingType.fromOffset(5, 5, DEAD_ZONE));
        assertNull(PingType.fromOffset(0, -(DEAD_ZONE - 1), DEAD_ZONE));
    }

    @Test
    void justOutsideTheDeadZoneSelects() {
        assertNotNull(PingType.fromOffset(0, -(DEAD_ZONE + 1), DEAD_ZONE));
    }

    @Test
    void selectionIgnoresDistanceOnceOutsideTheDeadZone() {
        // Flicking further must not change the answer - only the direction says anything.
        assertEquals(PingType.fromOffset(0, -30, DEAD_ZONE), PingType.fromOffset(0, -900, DEAD_ZONE));
        assertEquals(PingType.fromOffset(40, 40, DEAD_ZONE), PingType.fromOffset(400, 400, DEAD_ZONE));
    }

    @Test
    void everyDirectionSelectsSomething() {
        // Sweep the full circle. A slice-arithmetic slip (a missing wrap, an off-by-one modulo)
        // shows up as a null or an exception at one specific angle, usually the seam at the top.
        for (int deg = 0; deg < 360; deg++) {
            double rad = Math.toRadians(deg);
            int dx = (int) Math.round(Math.sin(rad) * 80);
            int dy = (int) Math.round(-Math.cos(rad) * 80);
            assertNotNull(PingType.fromOffset(dx, dy, DEAD_ZONE), "no option at " + deg + " degrees");
        }
    }

    @Test
    void adjacentOptionsSplitTheBoundaryBetweenThem() {
        // Exactly on the seam between two chips the answer may be either, but it must be one of
        // the two - never a chip on the far side of the wheel.
        PingType[] all = PingType.values();
        double slice = Math.PI * 2 / all.length;
        double seam = slice / 2;
        int dx = (int) Math.round(Math.sin(seam) * 80);
        int dy = (int) Math.round(-Math.cos(seam) * 80);
        PingType got = PingType.fromOffset(dx, dy, DEAD_ZONE);
        assertTrue(got == all[0] || got == all[1], "seam resolved to " + got);
    }

    // ── Wire decoding ─────────────────────────────────────────────────────

    @Test
    void byId_roundTripsEveryOption() {
        for (PingType type : PingType.values()) {
            assertEquals(type, PingType.byId(type.ordinal()));
        }
    }

    @Test
    void byId_refusesGarbageInsteadOfThrowing() {
        // This decodes inside a packet handler, where a throw is a disconnect.
        assertEquals(PingType.LOOK, PingType.byId(-1));
        assertEquals(PingType.LOOK, PingType.byId(999));
        assertEquals(PingType.LOOK, PingType.byId(Integer.MIN_VALUE));
        assertEquals(PingType.LOOK, PingType.byId(PingType.values().length));
    }

    // ── Presentation ──────────────────────────────────────────────────────

    @Test
    void everyOptionIsDistinguishable() {
        // Two options sharing a glyph or a colour makes the wheel unreadable at a glance, which
        // is the only speed it is ever read at.
        Set<String> glyphs = new HashSet<>();
        Set<Integer> colors = new HashSet<>();
        for (PingType type : PingType.values()) {
            assertTrue(glyphs.add(type.glyph), "duplicate glyph: " + type.glyph);
            assertTrue(colors.add(type.color), "duplicate color on " + type);
            assertFalse(type.label.isBlank(), type + " has no label");
        }
    }

    @Test
    void colorChannelsMatchThePackedColor() {
        // The renderer works in floats and the HUD in packed ARGB; they have to agree, or a
        // ping's pillar is a different colour from its own chip on the wheel.
        for (PingType type : PingType.values()) {
            assertEquals(((type.color >> 16) & 0xFF) / 255f, type.red(), 0.0001f);
            assertEquals(((type.color >> 8) & 0xFF) / 255f, type.green(), 0.0001f);
            assertEquals((type.color & 0xFF) / 255f, type.blue(), 0.0001f);
        }
    }

    @Test
    void everyOptionHasAChatColor() {
        // chatColor switches exhaustively over the enum. Adding an option without extending it
        // is a compile error, but a stray null would not be - check anyway.
        for (PingType type : PingType.values()) {
            assertNotNull(type.chatColor());
            assertTrue(type.chatColor().startsWith("§"), type + " chat color is not a format code");
        }
    }
}
