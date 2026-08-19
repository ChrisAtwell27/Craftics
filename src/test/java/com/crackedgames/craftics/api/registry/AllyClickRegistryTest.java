package com.crackedgames.craftics.api.registry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ally-click dispatch.
 *
 * <p>Handlers here ignore their arguments, so the tests pass null for the player and the ally.
 * That is the point rather than a shortcut around a Minecraft type: dispatch is an ordered walk
 * with a guard, and the ordering and the claim rule have to hold whatever the fight looks like.
 * Null player or ally is refused outright, which is checked separately.
 */
class AllyClickRegistryTest {

    @BeforeEach
    @AfterEach
    void reset() {
        AllyClickRegistry.clear();
    }

    @Test
    void noHandlers_meansNothingClaimsTheClick() {
        // Craftics with no addon must behave exactly as it always did: the click falls
        // through to the heal-item check.
        assertTrue(AllyClickRegistry.isEmpty());
        assertFalse(AllyClickRegistry.handle(null, null, null));
    }

    @Test
    void aClaimingHandlerStopsTheClick() {
        AllyClickRegistry.register((p, a, h) -> true);
        assertFalse(AllyClickRegistry.isEmpty());
        // Null player/ally are refused before any handler runs, so this must still be false.
        assertFalse(AllyClickRegistry.handle(null, null, null));
    }

    @Test
    void nullPlayerOrAllyNeverReachesAHandler() {
        List<String> ran = new ArrayList<>();
        AllyClickRegistry.register((p, a, h) -> { ran.add("x"); return true; });
        AllyClickRegistry.handle(null, null, null);
        assertTrue(ran.isEmpty(), "a handler must not be handed a null ally to dereference");
    }

    @Test
    void register_ignoresNullAndDuplicates() {
        AllyClickRegistry.register(null);
        assertTrue(AllyClickRegistry.isEmpty());

        com.crackedgames.craftics.api.AllyClickHandler handler = (p, a, h) -> false;
        AllyClickRegistry.register(handler);
        AllyClickRegistry.register(handler);
        // Registering twice must not make a declining handler run twice, which would double
        // any side effect an addon does before deciding.
        AllyClickRegistry.clear();
        assertTrue(AllyClickRegistry.isEmpty());
    }

    @Test
    void clear_forgetsEveryHandler() {
        AllyClickRegistry.register((p, a, h) -> true);
        AllyClickRegistry.clear();
        assertTrue(AllyClickRegistry.isEmpty());
    }

    @Test
    void isEmpty_tracksRegistration() {
        // The combat path short-circuits on this, so it has to be honest.
        assertTrue(AllyClickRegistry.isEmpty());
        AllyClickRegistry.register((p, a, h) -> false);
        assertFalse(AllyClickRegistry.isEmpty());
    }
}
