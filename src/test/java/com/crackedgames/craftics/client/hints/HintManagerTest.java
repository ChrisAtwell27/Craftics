package com.crackedgames.craftics.client.hints;

import net.minecraft.text.Text;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HintManagerTest {

    private HintManager manager;

    @BeforeEach
    void setUp() {
        manager = new HintManager(); // package-private no-arg ctor for tests
    }

    private static HintContext ctx(long now, long lastInputAtMs, boolean inCombat, boolean playerTurn) {
        return new HintContext(
            null, null,
            !inCombat, inCombat, playerTurn,
            3, 3, 3, 3,
            20, 20, 1,
            false, false, false, false,
            now, lastInputAtMs
        );
    }

    private static HintContext ctxWithScreen(long now, long lastInputAtMs, boolean inCombat, boolean playerTurn) {
        return new HintContext(
            null, null,
            !inCombat, inCombat, playerTurn,
            3, 3, 3, 3,
            20, 20, 1,
            false, false, false, /*isAnyScreenOpen*/ true,
            now, lastInputAtMs
        );
    }

    private static Hint alwaysTrue(String id, int idleSeconds) {
        return new Hint(
            id, c -> true, idleSeconds,
            new HintPresenter.HudPopup(Text.literal("hi"), 120),
            Hint.Priority.MEDIUM, Hint.Mode.RECURRING
        );
    }

    private static Hint hintWithPriority(String id, Hint.Priority p) {
        return new Hint(
            id, c -> true, 5,
            new HintPresenter.HudPopup(Text.literal(id), 120),
            p, Hint.Mode.RECURRING
        );
    }

    @Test
    void noActiveHint_beforeIdleThresholdElapses() {
        manager.register(alwaysTrue("h", 5));
        long t0 = 1000L;
        manager.tickWith(ctx(t0, t0, true, true), t0);
        manager.tickWith(ctx(t0 + 4000, t0, true, true), t0 + 4000);
        assertTrue(manager.getActiveHudHint().isEmpty());
    }

    @Test
    void hintBecomesActive_afterIdleThresholdElapses() {
        manager.register(alwaysTrue("h", 5));
        long t0 = 1000L;
        manager.tickWith(ctx(t0, t0, true, true), t0);
        manager.tickWith(ctx(t0 + 5500, t0, true, true), t0 + 5500);
        Optional<Hint> active = manager.getActiveHudHint();
        assertTrue(active.isPresent());
        assertEquals("h", active.get().id());
    }

    @Test
    void notifyInput_resetsIdleClock() {
        manager.register(alwaysTrue("h", 5));
        long t0 = 1000L;
        manager.tickWith(ctx(t0, t0, true, true), t0);
        manager.notifyInput(t0 + 4000);
        manager.tickWith(ctx(t0 + 8000, t0 + 4000, true, true), t0 + 8000);
        assertTrue(manager.getActiveHudHint().isEmpty(), "input should have reset clock");
        manager.tickWith(ctx(t0 + 10000, t0 + 4000, true, true), t0 + 10000);
        assertTrue(manager.getActiveHudHint().isPresent(), "should fire after 5s post-input");
    }

    @Test
    void sensorTurningFalse_clearsActiveHint() {
        Hint h = new Hint(
            "h", c -> c.inCombat(), 5,
            new HintPresenter.HudPopup(Text.literal("hi"), 120),
            Hint.Priority.MEDIUM, Hint.Mode.RECURRING
        );
        manager.register(h);
        long t0 = 1000L;
        manager.tickWith(ctx(t0, t0, true, true), t0);
        manager.tickWith(ctx(t0 + 6000, t0, true, true), t0 + 6000);
        assertTrue(manager.getActiveHudHint().isPresent());
        manager.tickWith(ctx(t0 + 7000, t0, false, false), t0 + 7000); // out of combat
        assertTrue(manager.getActiveHudHint().isEmpty());
    }

    @Test
    void registeringSameIdTwice_throws() {
        manager.register(alwaysTrue("h", 5));
        assertThrows(IllegalStateException.class, () -> manager.register(alwaysTrue("h", 7)));
    }

    @Test
    void higherPriority_preemptsLower() {
        manager.register(hintWithPriority("low", Hint.Priority.LOW));
        manager.register(hintWithPriority("high", Hint.Priority.HIGH));
        long t0 = 1000L;
        manager.tickWith(ctx(t0, t0, true, true), t0);
        manager.tickWith(ctx(t0 + 6000, t0, true, true), t0 + 6000);
        assertEquals("high", manager.getActiveHudHint().orElseThrow().id());
    }

    @Test
    void cooldown_blocksRefire_for30s() {
        boolean[] sensorOn = {true};
        Hint h = new Hint("h", c -> sensorOn[0], 5,
            new HintPresenter.HudPopup(Text.literal("x"), 120),
            Hint.Priority.MEDIUM, Hint.Mode.RECURRING);
        manager.register(h);

        long t0 = 1000L;
        manager.tickWith(ctx(t0, t0, true, true), t0);
        manager.tickWith(ctx(t0 + 6000, t0, true, true), t0 + 6000);
        assertTrue(manager.getActiveHudHint().isPresent(), "should fire initially");

        sensorOn[0] = false;
        manager.tickWith(ctx(t0 + 7000, t0, true, true), t0 + 7000);
        assertTrue(manager.getActiveHudHint().isEmpty());

        sensorOn[0] = true;
        manager.tickWith(ctx(t0 + 17000, t0, true, true), t0 + 17000);
        manager.tickWith(ctx(t0 + 23000, t0, true, true), t0 + 23000);
        assertTrue(manager.getActiveHudHint().isEmpty(), "should be in cooldown");

        manager.tickWith(ctx(t0 + 38000, t0, true, true), t0 + 38000);
        manager.tickWith(ctx(t0 + 44000, t0, true, true), t0 + 44000);
        assertTrue(manager.getActiveHudHint().isPresent(), "should fire after cooldown");
    }

    @Test
    void notifyAction_setsTookActionFlag() {
        manager.notifyAction(ActionKind.MOVED);
        assertTrue(manager.tookActionThisTurn());
        manager.onPlayerTurnStart();
        assertFalse(manager.tookActionThisTurn());
    }

    @Test
    void oneShotPersistent_filteredAfterDismissal() {
        Set<String> store = new HashSet<>();
        manager.setDismissalStore(new HintManager.DismissalSink() {
            @Override public boolean isDismissed(String id) { return store.contains(id); }
            @Override public void markDismissed(String id) { store.add(id); }
        });

        Hint oneshot = new Hint("once", c -> true, 0,
            new HintPresenter.HudPopup(Text.literal("hi"), 120),
            Hint.Priority.HIGH, Hint.Mode.ONE_SHOT_PERSISTENT);
        manager.register(oneshot);

        long t0 = 1000L;
        manager.tickWith(ctx(t0, t0, true, true), t0);
        manager.tickWith(ctx(t0 + 100, t0, true, true), t0 + 100);
        assertTrue(manager.getActiveHudHint().isPresent());

        manager.dismiss("once");

        manager.tickWith(ctx(t0 + 1000, t0, true, true), t0 + 1000);
        assertTrue(manager.getActiveHudHint().isEmpty(), "should not re-fire after dismiss");
        assertTrue(store.contains("once"), "store should be persisted");
    }

    @Test
    void oneShotPersistent_skippedIfAlreadyInStore() {
        Set<String> store = new HashSet<>(Set.of("once"));
        manager.setDismissalStore(new HintManager.DismissalSink() {
            @Override public boolean isDismissed(String id) { return store.contains(id); }
            @Override public void markDismissed(String id) { store.add(id); }
        });

        Hint oneshot = new Hint("once", c -> true, 0,
            new HintPresenter.HudPopup(Text.literal("hi"), 120),
            Hint.Priority.HIGH, Hint.Mode.ONE_SHOT_PERSISTENT);
        manager.register(oneshot);

        long t0 = 1000L;
        manager.tickWith(ctx(t0, t0, true, true), t0);
        manager.tickWith(ctx(t0 + 100, t0, true, true), t0 + 100);
        assertTrue(manager.getActiveHudHint().isEmpty());
    }

    @Test
    void anyOpenScreen_suppressesAllHints() {
        manager.register(alwaysTrue("h", 5));
        long t0 = 1000L;
        manager.tickWith(ctx(t0, t0, true, true), t0);
        manager.tickWith(ctx(t0 + 6000, t0, true, true), t0 + 6000);
        assertTrue(manager.getActiveHudHint().isPresent());
        manager.tickWith(ctxWithScreen(t0 + 7000, t0, true, true), t0 + 7000);
        assertTrue(manager.getActiveHudHint().isEmpty());
    }

    @Test
    void idleMultiplier_scalesThreshold() {
        manager.register(alwaysTrue("h", 10));
        manager.setIdleMultiplier(0.5f); // 5s instead of 10s
        long t0 = 1000L;
        manager.tickWith(ctx(t0, t0, true, true), t0);
        manager.tickWith(ctx(t0 + 6000, t0, true, true), t0 + 6000);
        assertTrue(manager.getActiveHudHint().isPresent(), "should fire at 6s with 0.5x multiplier");
    }
}
