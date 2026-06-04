package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

/** Pure-logic tests for {@link PlayerCombatStats#recognizedEffectNames(List)}. */
class PlayerCombatStatsTest {

    @Test
    void singleRecognizedPath_mapsToOneName() {
        assertEquals(List.of("poison"),
            PlayerCombatStats.recognizedEffectNames(List.of("poison")));
    }

    @Test
    void multipleRecognizedPaths_mapAllInInputOrder() {
        assertEquals(List.of("poison", "wither", "slowness"),
            PlayerCombatStats.recognizedEffectNames(List.of("poison", "wither", "slowness")));
    }

    @Test
    void renamedPaths_instantDamageAndHealth() {
        assertEquals(List.of("harming", "healing"),
            PlayerCombatStats.recognizedEffectNames(List.of("instant_damage", "instant_health")));
    }

    @Test
    void allSevenRecognized() {
        assertEquals(
            List.of("poison", "slowness", "weakness", "harming", "healing", "fire_resistance", "wither"),
            PlayerCombatStats.recognizedEffectNames(List.of(
                "poison", "slowness", "weakness", "instant_damage", "instant_health",
                "fire_resistance", "wither")));
    }

    @Test
    void unrecognizedOnly_returnsEmpty() {
        assertEquals(List.of(),
            PlayerCombatStats.recognizedEffectNames(List.of("luck")));
    }

    @Test
    void mixedRecognizedAndUnrecognized_keepsOnlyRecognizedInOrder() {
        assertEquals(List.of("poison", "wither"),
            PlayerCombatStats.recognizedEffectNames(List.of("regeneration", "poison", "luck", "wither")));
    }

    @Test
    void duplicateRecognizedPath_isDeduped() {
        assertEquals(List.of("poison"),
            PlayerCombatStats.recognizedEffectNames(List.of("poison", "poison")));
    }

    @Test
    void emptyInput_returnsEmpty() {
        assertEquals(List.of(),
            PlayerCombatStats.recognizedEffectNames(List.of()));
    }
}
