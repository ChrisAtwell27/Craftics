package com.crackedgames.craftics.compat;

import com.crackedgames.craftics.compat.PanelExclusion.State;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A recipe viewer (EMI or JEI) and the Craftics inventory panels must never both be on screen.
 *
 * <p>The interesting tests are the last two: rather than checking the cases anyone thought of,
 * they walk every reachable state through every key press and assert the rule never breaks. That
 * is the difference between believing an invariant and knowing it.
 */
class PanelExclusionTest {

    /** Every combination of what the player asked for and what the viewer is doing. */
    private static List<State> allStates() {
        List<State> out = new ArrayList<>();
        for (boolean stats : new boolean[] { false, true }) {
            for (boolean viewer : new boolean[] { false, true }) {
                out.add(new State(stats, viewer));
            }
        }
        return out;
    }

    private static boolean bothOnScreen(State s) {
        return PanelExclusion.statsVisible(s.statsRequested(), s.viewerEnabled()) && s.viewerEnabled();
    }

    @Test
    @DisplayName("the panels stand down whenever a viewer is up")
    void panelsYieldToTheViewer() {
        assertFalse(PanelExclusion.statsVisible(true, true),
            "asked for AND a viewer up: the viewer wins, because it is the one that was just "
                + "turned on");
        assertTrue(PanelExclusion.statsVisible(true, false));
        assertFalse(PanelExclusion.statsVisible(false, false), "not asked for");
        assertFalse(PanelExclusion.statsVisible(false, true));
    }

    @Test
    @DisplayName("with a viewer installed, the key swaps which one has the space")
    void theKeySwapsWhenAViewerIsInstalled() {
        State panels = new State(true, false);

        State viewer = PanelExclusion.toggle(panels, true);
        assertTrue(viewer.viewerEnabled(), "turning the panels off hands the space to the viewer");
        assertFalse(PanelExclusion.statsVisible(viewer.statsRequested(), viewer.viewerEnabled()));

        State back = PanelExclusion.toggle(viewer, true);
        assertFalse(back.viewerEnabled(), "and pressing it again takes the space back");
        assertTrue(PanelExclusion.statsVisible(back.statsRequested(), back.viewerEnabled()));
    }

    @Test
    @DisplayName("the key never leaves the space empty while a viewer is installed")
    void theSpaceIsNeverLeftEmptyWithAViewer() {
        // The two share one screen region, so "off" for one is "on" for the other. Emptying the
        // space and then needing a second key to fill it again is the behaviour this replaced.
        for (State start : allStates()) {
            State after = PanelExclusion.toggle(start, true);
            boolean somethingOnScreen =
                PanelExclusion.statsVisible(after.statsRequested(), after.viewerEnabled())
                    || after.viewerEnabled();
            assertTrue(somethingOnScreen, "pressing the key from " + start + " emptied the space");
        }
    }

    @Test
    @DisplayName("with no viewer installed the key just flips the panels, as it always did")
    void noViewerMeansTheOldBehaviour() {
        State on = new State(true, false);
        State off = PanelExclusion.toggle(on, false);
        assertFalse(off.statsRequested(), "the panels go down");
        assertFalse(off.viewerEnabled(), "and nothing takes their place");

        State backOn = PanelExclusion.toggle(off, false);
        assertTrue(backOn.statsRequested());
        assertFalse(backOn.viewerEnabled());
    }

    @Test
    @DisplayName("no key press from any state can put both on screen")
    void oneKeyPressCanNeverShowBoth() {
        for (State start : allStates()) {
            for (boolean viewerPresent : new boolean[] { false, true }) {
                assertFalse(bothOnScreen(PanelExclusion.toggle(start, viewerPresent)),
                    "key from " + start + " (viewer installed: " + viewerPresent + ") showed both");
            }
        }
    }

    @Test
    @DisplayName("no sequence of key presses can put both on screen either")
    void noSequenceOfPressesCanShowBoth() {
        // Presses to any depth, with the viewer installed or not at each step - a player can
        // install one between sessions, and the rule has to hold across that too. The state space
        // is four wide, so eight presses walks every cycle in it many times over.
        for (State start : allStates()) {
            walk(start, 8, "" + start);
        }
    }

    private void walk(State state, int depth, String path) {
        assertFalse(bothOnScreen(state), "both on screen after " + path);
        if (depth == 0) return;
        walk(PanelExclusion.toggle(state, true), depth - 1, path + " -key(viewer)-> ");
        walk(PanelExclusion.toggle(state, false), depth - 1, path + " -key(none)-> ");
    }
}
