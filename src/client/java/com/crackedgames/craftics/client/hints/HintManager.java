package com.crackedgames.craftics.client.hints;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class HintManager {

    private static final long COOLDOWN_MS = 30_000L;

    private static final HintManager INSTANCE = new HintManager();
    public static HintManager get() { return INSTANCE; }

    public interface DismissalSink {
        boolean isDismissed(String id);
        void markDismissed(String id);
    }

    private final Map<String, Hint> registered = new LinkedHashMap<>();
    private final Map<String, Long> sensorTrueSinceMs = new HashMap<>();
    private final Map<String, Long> lastClearedAtMs = new HashMap<>();
    private long lastInputAtMs = 0L;
    private boolean tookActionThisTurn = false;
    private float idleMultiplier = 1.0f;
    private Hint activeHud = null;
    private final Set<String> activeWorldHintIds = new LinkedHashSet<>();
    private DismissalSink dismissalSink = new DismissalSink() {
        @Override public boolean isDismissed(String id) { return false; }
        @Override public void markDismissed(String id) {}
    };

    HintManager() {}

    public void register(Hint hint) {
        if (registered.containsKey(hint.id())) {
            throw new IllegalStateException("Hint id already registered: " + hint.id());
        }
        registered.put(hint.id(), hint);
    }

    public void setDismissalStore(DismissalSink sink) {
        this.dismissalSink = sink;
    }

    public void setIdleMultiplier(float m) {
        this.idleMultiplier = Math.max(0.1f, m);
    }

    public void notifyInput(long nowMs) {
        // Don't clear sensorTrueSinceMs — instead use lastInputAtMs as a floor
        // when computing elapsed idle time. That way "reset" means "the idle
        // clock effectively restarts from the input moment", not "the next
        // sensor-true tick rearms," which would over-delay by one tick.
        this.lastInputAtMs = nowMs;
    }

    public void notifyAction(ActionKind kind) {
        this.tookActionThisTurn = true;
    }

    public void onPlayerTurnStart() {
        this.tookActionThisTurn = false;
    }

    public boolean tookActionThisTurn() { return tookActionThisTurn; }

    public Optional<Hint> getActiveHudHint() { return Optional.ofNullable(activeHud); }

    public List<Hint> getActiveWorldHints() {
        return activeWorldHintIds.stream().map(registered::get).filter(h -> h != null).toList();
    }

    public void dismiss(String id) {
        Hint h = registered.get(id);
        if (h == null) return;
        if (h.mode() == Hint.Mode.ONE_SHOT_PERSISTENT) {
            dismissalSink.markDismissed(id);
        }
        if (activeHud != null && activeHud.id().equals(id)) activeHud = null;
        activeWorldHintIds.remove(id);
        lastClearedAtMs.put(id, System.currentTimeMillis());
    }

    /** Test entry point — production callers build the snapshot themselves and call this. */
    public void tickWith(HintContext ctx, long nowMs) {
        // Any open GUI suppresses every hint — they reappear once the screen closes.
        if (ctx.isAnyScreenOpen()) {
            if (activeHud != null) lastClearedAtMs.put(activeHud.id(), nowMs);
            for (String id : activeWorldHintIds) lastClearedAtMs.put(id, nowMs);
            this.activeHud = null;
            this.activeWorldHintIds.clear();
            return;
        }

        Hint nextHud = null;
        Set<String> nextWorld = new LinkedHashSet<>();

        for (Hint h : registered.values()) {
            // Persistent dismissal gate
            if (h.mode() == Hint.Mode.ONE_SHOT_PERSISTENT
                    && dismissalSink.isDismissed(h.id())) {
                continue;
            }
            // Cooldown gate
            Long clearedAt = lastClearedAtMs.get(h.id());
            if (clearedAt != null && (nowMs - clearedAt) < COOLDOWN_MS) continue;

            boolean sensorTrue;
            try { sensorTrue = h.sensor().test(ctx); }
            catch (Exception e) { sensorTrue = false; }

            if (!sensorTrue) {
                sensorTrueSinceMs.remove(h.id());
                continue;
            }

            Long since = sensorTrueSinceMs.get(h.id());
            if (since == null) {
                sensorTrueSinceMs.put(h.id(), nowMs);
                since = nowMs;
            }

            long effectiveSince = Math.max(since, lastInputAtMs);
            long elapsedMs = nowMs - effectiveSince;
            long thresholdMs = (long)(h.idleSeconds() * 1000L * idleMultiplier);
            if (elapsedMs < thresholdMs) continue;

            switch (h.presenter()) {
                case HintPresenter.HudPopup ignored -> {
                    if (nextHud == null
                            || h.priority().ordinal() > nextHud.priority().ordinal()) {
                        nextHud = h;
                    }
                }
                case HintPresenter.WorldArrow ignored -> nextWorld.add(h.id());
            }
        }

        // Mark cleared hints (was active last tick, not active this tick) for cooldown.
        if (activeHud != null && (nextHud == null || !activeHud.id().equals(nextHud.id()))) {
            lastClearedAtMs.put(activeHud.id(), nowMs);
        }
        for (String prevId : activeWorldHintIds) {
            if (!nextWorld.contains(prevId)) lastClearedAtMs.put(prevId, nowMs);
        }

        this.activeHud = nextHud;
        this.activeWorldHintIds.clear();
        this.activeWorldHintIds.addAll(nextWorld);
    }
}
