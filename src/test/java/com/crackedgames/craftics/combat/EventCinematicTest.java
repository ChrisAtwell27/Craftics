package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EventCinematicTest {

    @Test
    void allArrivedFiresOnceWhenLastArrives() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        int[] fired = {0};
        EventCinematic c = new EventCinematic(List.of(a, b), () -> fired[0]++, () -> {});
        c.markArrived(a);
        assertEquals(0, fired[0]);
        c.markArrived(b);
        assertEquals(1, fired[0]);
        c.markArrived(b); // duplicate must not refire
        assertEquals(1, fired[0]);
    }

    @Test
    void allFinishedFiresWhenLastFinishes() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        int[] arrived = {0};
        int[] done = {0};
        EventCinematic c = new EventCinematic(List.of(a, b), () -> arrived[0]++, () -> done[0]++);
        c.markFinished(a);
        assertEquals(0, done[0]);
        c.markFinished(b);
        assertEquals(1, done[0]);
        assertEquals(1, arrived[0], "finishing implies arrival, so onAllArrived must also have fired");
    }

    @Test
    void nonPartyUuidIsIgnored() {
        UUID a = UUID.randomUUID();
        int[] fired = {0};
        EventCinematic c = new EventCinematic(List.of(a), () -> fired[0]++, () -> {});
        c.markArrived(UUID.randomUUID());   // stranger, not in party
        assertEquals(0, fired[0]);
        c.markFinished(UUID.randomUUID());  // stranger
        assertEquals(0, fired[0]);
    }

    @Test
    void disconnectDropsFromBothSets() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        int[] fired = {0};
        EventCinematic c = new EventCinematic(List.of(a, b), () -> fired[0]++, () -> {});
        c.markArrived(a);
        c.removePlayer(b);     // b disconnects before arriving
        assertEquals(1, fired[0], "all-arrived should fire once b is no longer expected");
    }

    @Test
    void disconnectOfLastFinisherCompletes() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        int[] done = {0};
        EventCinematic c = new EventCinematic(List.of(a, b), () -> {}, () -> done[0]++);
        c.markFinished(a);
        c.removePlayer(b);     // only a finished, b leaves -> event completes
        assertEquals(1, done[0]);
    }
}
