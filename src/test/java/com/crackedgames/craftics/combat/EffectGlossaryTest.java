package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.combat.CombatEffects.EffectType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Every effect pill the HUD can draw must have something to say when hovered.
 *
 * <p>A missing entry throws nothing: the pill simply has no tooltip, which reads as "hover is
 * broken on this one" and is exactly the kind of gap nobody reports. So the two vocabularies
 * are walked here, the same way {@link EffectIconsTest} walks them for icons.
 */
class EffectGlossaryTest {

    /** Exactly the names CombatManager.sendSync appends to the enemy blob. Keep in step with it. */
    private static final List<String> ENEMY_SYNC_NAMES = List.of(
        "Stunned", "Enraged", "Marked(3t)", "Slowed(2t)", "Slowed", "Poisoned(3t)",
        "Weakened(-2ATK,3t)", "Soul Burning(2t)", "Burning(2t)", "Burning", "Soaked(3t)",
        "Confused(2t)", "Blinded(2t)", "Exposed(-2DEF,3t)", "Bleeding(3 stacks)",
        "Withered(3t)", "Frozen", "Taunting",
        "Regenerating(3t)", "Absorption(4HP,3t)", "Resistant(3t)",
        "Strengthened(+3ATK,3t)", "Hastened(+1SPD,3t)", "SlowFalling(3t)",
        "Airtime(2t)", "Levitation(2t)");

    @Test
    @DisplayName("every label the server sends for a mob is described")
    void everyMobLabelIsDescribed() {
        for (String label : ENEMY_SYNC_NAMES) {
            String text = EffectGlossary.onMob(label);
            assertNotNull(text, label + " has no mob-side description");
            assertFalse(text.isBlank(), label);
        }
    }

    @Test
    @DisplayName("every player effect is described, in the form the HUD shows it")
    void everyPlayerEffectIsDescribed() {
        for (EffectType type : EffectType.values()) {
            assertNotNull(EffectGlossary.onPlayer(type.displayName), type + " (bare name)");
            assertNotNull(EffectGlossary.onPlayer(type.displayName + " II (3t)"),
                type + " (with level and duration)");
        }
        assertNotNull(EffectGlossary.onPlayer("Hidden"));
    }

    @Test
    @DisplayName("the same word can mean different things on each side")
    void sidesAreIndependent() {
        // On a player Airtime sharpens the next shot; on a mob it is just being airborne.
        assertNotEquals(EffectGlossary.onPlayer("Airtime"), EffectGlossary.onMob("Airtime(2t)"));
    }

    @Test
    @DisplayName("an effect nobody described has no tooltip rather than a wrong one")
    void unknownEffectsAreNull() {
        assertNull(EffectGlossary.onMob("Some Addon Curse(2t)"));
        assertNull(EffectGlossary.onPlayer("Some Addon Curse (2t)"));
    }

    @Test
    @DisplayName("player descriptions read as sentences")
    void playerTextIsASentence() {
        assertEquals("+2 movement/level.", EffectGlossary.onPlayer("Speed"));
    }
}
