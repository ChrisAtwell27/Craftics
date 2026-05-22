package com.crackedgames.craftics.combat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks a party's progression through an event: who has arrived at the talk tile
 * (gates {@code onAllArrived} once) and who has finished (gates {@code onAllFinished}
 * once). Disconnected players are removed so they never block the gates.
 *
 * Pure state machine — the live walking + dialogue dispatch is driven by the owner
 * (CombatManager), which supplies the callbacks.
 */
public final class EventCinematic {

    private final Set<UUID> expected = new HashSet<>();
    private final Set<UUID> arrived = new HashSet<>();
    private final Set<UUID> finished = new HashSet<>();
    private final Runnable onAllArrived;
    private final Runnable onAllFinished;
    private boolean arrivedFired = false;
    private boolean finishedFired = false;

    public EventCinematic(List<UUID> party, Runnable onAllArrived, Runnable onAllFinished) {
        this.expected.addAll(party);
        this.onAllArrived = onAllArrived;
        this.onAllFinished = onAllFinished;
    }

    public void markArrived(UUID player) {
        if (!expected.contains(player)) return;
        arrived.add(player);
        checkAllArrived();
    }

    /**
     * Mark a player finished. Counts as arrival too (adds to the arrived set), so
     * {@code onAllArrived} fires before {@code onAllFinished} even if {@code markArrived}
     * was never called for this player. Do not remove the arrived-add — the finished
     * gate requires the arrived gate to have fired first.
     */
    public void markFinished(UUID player) {
        if (!expected.contains(player)) return;
        arrived.add(player);     // finishing implies arrival
        finished.add(player);
        checkAllArrived();
        checkAllFinished();
    }

    /** Drop a disconnected player from all tracking; may complete a pending gate. */
    public void removePlayer(UUID player) {
        expected.remove(player);
        arrived.remove(player);
        finished.remove(player);
        checkAllArrived();
        checkAllFinished();
    }

    public boolean hasArrived(UUID player) { return arrived.contains(player); }

    private void checkAllArrived() {
        if (!arrivedFired && !expected.isEmpty() && arrived.containsAll(expected)) {
            arrivedFired = true;
            onAllArrived.run();
        }
    }

    private void checkAllFinished() {
        if (!finishedFired && arrivedFired && !expected.isEmpty() && finished.containsAll(expected)) {
            finishedFired = true;
            onAllFinished.run();
        }
    }
}
