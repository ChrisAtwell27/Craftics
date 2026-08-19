package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.HudPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for combat HUD panel suppression.
 *
 * <p>No config is loaded in a test JVM, so {@code isVisible} falls back to "the player has not
 * turned this off" - which is exactly the path being checked here, since the interesting
 * behaviour is the addon side and the defensive read that keeps a panel drawn when the config
 * is not available yet.
 */
class HudPanelRegistryTest {

    @BeforeEach
    @AfterEach
    void reset() {
        HudPanelRegistry.clear();
    }

    @Test
    void everyPanelIsVisibleByDefault() {
        // Craftics with no addon installed must look exactly as it always did.
        for (HudPanel panel : HudPanel.values()) {
            assertTrue(HudPanelRegistry.isVisible(panel), panel + " should start visible");
        }
    }

    @Test
    void hide_removesOnlyThatPanel() {
        HudPanelRegistry.hide(HudPanel.ALLY_ROSTER);
        assertFalse(HudPanelRegistry.isVisible(HudPanel.ALLY_ROSTER));
        assertTrue(HudPanelRegistry.isVisible(HudPanel.ENEMY_ROSTER));
        assertTrue(HudPanelRegistry.isVisible(HudPanel.TURN_ORDER));
        assertTrue(HudPanelRegistry.isVisible(HudPanel.PLAYER_STATUS));
    }

    @Test
    void hide_isIdempotent() {
        // An addon re-declaring its suppression on a reload must not need to track whether
        // it already did.
        HudPanelRegistry.hide(HudPanel.ALLY_ROSTER);
        HudPanelRegistry.hide(HudPanel.ALLY_ROSTER);
        HudPanelRegistry.show(HudPanel.ALLY_ROSTER);
        assertTrue(HudPanelRegistry.isVisible(HudPanel.ALLY_ROSTER),
            "one show must undo any number of hides");
    }

    @Test
    void show_bringsAPanelBack() {
        HudPanelRegistry.hide(HudPanel.TURN_ORDER);
        HudPanelRegistry.show(HudPanel.TURN_ORDER);
        assertTrue(HudPanelRegistry.isVisible(HudPanel.TURN_ORDER));
    }

    @Test
    void isHiddenByAddon_reportsTheAddonRequestOnly() {
        assertFalse(HudPanelRegistry.isHiddenByAddon(HudPanel.ALLY_ROSTER));
        HudPanelRegistry.hide(HudPanel.ALLY_ROSTER);
        assertTrue(HudPanelRegistry.isHiddenByAddon(HudPanel.ALLY_ROSTER));
    }

    @Test
    void panelsCanBeHiddenIndependently() {
        HudPanelRegistry.hide(HudPanel.ALLY_ROSTER);
        HudPanelRegistry.hide(HudPanel.ENEMY_ROSTER);
        assertFalse(HudPanelRegistry.isVisible(HudPanel.ALLY_ROSTER));
        assertFalse(HudPanelRegistry.isVisible(HudPanel.ENEMY_ROSTER));
        HudPanelRegistry.show(HudPanel.ALLY_ROSTER);
        assertTrue(HudPanelRegistry.isVisible(HudPanel.ALLY_ROSTER));
        assertFalse(HudPanelRegistry.isVisible(HudPanel.ENEMY_ROSTER),
            "showing one panel must not resurrect another");
    }

    @Test
    void nullIsTolerated() {
        // These run inside a render loop; a null must never take the HUD down.
        assertDoesNotThrow(() -> HudPanelRegistry.hide(null));
        assertDoesNotThrow(() -> HudPanelRegistry.show(null));
        assertTrue(HudPanelRegistry.isVisible(null));
        assertFalse(HudPanelRegistry.isHiddenByAddon(null));
    }

    @Test
    void clear_forgetsEverySuppression() {
        for (HudPanel panel : HudPanel.values()) HudPanelRegistry.hide(panel);
        HudPanelRegistry.clear();
        for (HudPanel panel : HudPanel.values()) {
            assertTrue(HudPanelRegistry.isVisible(panel));
        }
    }

    @Test
    void missingConfigKeepsPanelsDrawn() {
        // The HUD can render before the config finishes loading. Defaulting to hidden there
        // would read as panels flickering out, so the defensive read defaults to visible.
        assertTrue(HudPanelRegistry.isVisible(HudPanel.ALLY_ROSTER));
        assertTrue(HudPanelRegistry.isVisible(HudPanel.ENEMY_ROSTER));
    }
}
