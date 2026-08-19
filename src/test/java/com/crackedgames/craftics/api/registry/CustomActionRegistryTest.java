package com.crackedgames.craftics.api.registry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for addon-defined enemy action dispatch.
 *
 * <p>The handlers here ignore their context, so the tests pass null for it. That is not a
 * shortcut around a Minecraft type - it is the point being tested. Dispatch is a string
 * lookup and a guarded call, and it has to behave the same whatever the fight looks like.
 */
class CustomActionRegistryTest {

    private static final String ID = "test:flamethrower";

    @BeforeEach
    @AfterEach
    void reset() {
        CustomActionRegistry.clear();
    }

    @Test
    void resolve_runsTheRegisteredHandler() {
        AtomicInteger runs = new AtomicInteger();
        CustomActionRegistry.register(ID, ctx -> runs.incrementAndGet());
        assertTrue(CustomActionRegistry.resolve(ID, null));
        assertEquals(1, runs.get());
    }

    @Test
    void resolve_reportsFalseForAnUnregisteredId() {
        // An addon can be uninstalled while a save still holds an AI naming its actions. The
        // enemy passes its turn; it must not wedge the fight waiting for a handler that will
        // never arrive.
        assertFalse(CustomActionRegistry.resolve("test:gone", null));
    }

    @Test
    void resolve_reportsFalseForANullId() {
        assertFalse(CustomActionRegistry.resolve(null, null));
    }

    @Test
    void resolve_survivesAThrowingHandler() {
        // Same bargain as the missing handler: the addon loses its action, the fight carries on.
        CustomActionRegistry.register(ID, ctx -> {
            throw new IllegalStateException("boom");
        });
        assertFalse(CustomActionRegistry.resolve(ID, null));
    }

    @Test
    void resolve_aThrowingHandlerDoesNotPoisonLaterCalls() {
        AtomicInteger runs = new AtomicInteger();
        CustomActionRegistry.register("test:bad", ctx -> {
            throw new IllegalStateException("boom");
        });
        CustomActionRegistry.register("test:good", ctx -> runs.incrementAndGet());
        assertFalse(CustomActionRegistry.resolve("test:bad", null));
        assertTrue(CustomActionRegistry.resolve("test:good", null));
        assertEquals(1, runs.get());
    }

    @Test
    void register_replacesAHandlerWithTheSameId() {
        // How an addon overrides another addon's action, or its own on a reload.
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        CustomActionRegistry.register(ID, ctx -> first.incrementAndGet());
        CustomActionRegistry.register(ID, ctx -> second.incrementAndGet());
        CustomActionRegistry.resolve(ID, null);
        assertEquals(0, first.get());
        assertEquals(1, second.get());
    }

    @Test
    void register_ignoresBlankIdsAndNullHandlers() {
        CustomActionRegistry.register(null, ctx -> {});
        CustomActionRegistry.register(" ", ctx -> {});
        CustomActionRegistry.register("test:x", null);
        assertFalse(CustomActionRegistry.isRegistered("test:x"));
        assertNull(CustomActionRegistry.getOrNull(" "));
    }

    @Test
    void lookup_reflectsRegistration() {
        assertFalse(CustomActionRegistry.isRegistered(ID));
        assertNull(CustomActionRegistry.getOrNull(ID));
        CustomActionRegistry.register(ID, ctx -> {});
        assertTrue(CustomActionRegistry.isRegistered(ID));
        assertNotNull(CustomActionRegistry.getOrNull(ID));
    }

    @Test
    void missingHandler_warnsOnceButKeepsReportingFalse() {
        // The warning is once-per-id so a missing handler does not fill the log with one line
        // per turn for the rest of the run. The RESULT must not be deduplicated with it -
        // every call still has to say the action did not resolve.
        assertFalse(CustomActionRegistry.resolve("test:missing", null));
        assertFalse(CustomActionRegistry.resolve("test:missing", null));
        assertFalse(CustomActionRegistry.resolve("test:missing", null));
    }

    @Test
    void clear_forgetsHandlersAndTheWarnedSet() {
        CustomActionRegistry.register(ID, ctx -> {});
        CustomActionRegistry.resolve("test:missing", null);
        CustomActionRegistry.clear();
        assertFalse(CustomActionRegistry.isRegistered(ID));
        // Registering it after a clear must work again, proving the clear did not leave the
        // id stranded in the warned set.
        AtomicInteger runs = new AtomicInteger();
        CustomActionRegistry.register("test:missing", ctx -> runs.incrementAndGet());
        assertTrue(CustomActionRegistry.resolve("test:missing", null));
        assertEquals(1, runs.get());
    }
}
