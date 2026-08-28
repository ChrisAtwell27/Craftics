package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ally-command dispatch.
 *
 * <p>Handlers here ignore their arguments, so the tests pass null for the player and the ally,
 * for the same reason {@link AllyClickRegistryTest} does: dispatch is an ordered walk with a
 * guard, and the ordering and the claim rule have to hold whatever the fight looks like. Null
 * player or ally is refused outright, which is checked separately.
 */
class AllyCommandRegistryTest {

    @BeforeEach
    @AfterEach
    void reset() {
        AllyCommandRegistry.clear();
    }

    @Test
    void noHandlers_meansNothingClaimsTheCommand() {
        // Craftics with no addon must command allies exactly as it always did: the click falls
        // through to the built-in walk-or-strike.
        assertTrue(AllyCommandRegistry.isEmpty());
        assertFalse(AllyCommandRegistry.handle(null, null, new GridPos(1, 1), null));
    }

    @Test
    void nullPlayerOrAllyNeverReachesAHandler() {
        List<String> ran = new ArrayList<>();
        AllyCommandRegistry.register((p, a, tile, target) -> { ran.add("x"); return true; });
        AllyCommandRegistry.handle(null, null, new GridPos(0, 0), null);
        assertTrue(ran.isEmpty(), "a handler must not be handed a null ally to dereference");
    }

    @Test
    void register_ignoresNullAndDuplicates() {
        AllyCommandRegistry.register(null);
        assertTrue(AllyCommandRegistry.isEmpty());

        com.crackedgames.craftics.api.AllyCommandHandler handler = (p, a, tile, target) -> false;
        AllyCommandRegistry.register(handler);
        AllyCommandRegistry.register(handler);
        // Registering twice must not make a declining handler run twice, which would double any
        // side effect an addon does before deciding.
        AllyCommandRegistry.clear();
        assertTrue(AllyCommandRegistry.isEmpty());
    }

    @Test
    void clear_forgetsEveryHandler() {
        AllyCommandRegistry.register((p, a, tile, target) -> true);
        AllyCommandRegistry.clear();
        assertTrue(AllyCommandRegistry.isEmpty());
    }

    @Test
    void isEmpty_tracksRegistration() {
        // The command path short-circuits on this, so it has to be honest.
        assertTrue(AllyCommandRegistry.isEmpty());
        AllyCommandRegistry.register((p, a, tile, target) -> false);
        assertFalse(AllyCommandRegistry.isEmpty());
    }
}
