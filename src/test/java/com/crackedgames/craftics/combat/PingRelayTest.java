package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ping rate limiter.
 *
 * <p>The clock is a parameter, so these run instantly and deterministically rather than by
 * sleeping. A limiter tested with real sleeps is a limiter tested at one timing, on one machine,
 * and flaky on CI.
 */
class PingRelayTest {

    private static final UUID ALICE = UUID.nameUUIDFromBytes("alice".getBytes());
    private static final UUID BOB = UUID.nameUUIDFromBytes("bob".getBytes());

    @BeforeEach
    @AfterEach
    void reset() {
        PingRelay.reset();
    }

    @Test
    void firstPingIsAlwaysAllowed() {
        assertTrue(PingRelay.allow(ALICE, 1_000));
    }

    @Test
    void aSecondPingInsideTheWindowIsRefused() {
        assertTrue(PingRelay.allow(ALICE, 1_000));
        assertFalse(PingRelay.allow(ALICE, 1_000 + PingRelay.MIN_INTERVAL_MS - 1));
    }

    @Test
    void aPingExactlyOnTheWindowIsAllowed() {
        // The boundary is where rate limiters are wrong. A strict ">" here would make the
        // effective interval one millisecond longer than the constant says.
        assertTrue(PingRelay.allow(ALICE, 1_000));
        assertTrue(PingRelay.allow(ALICE, 1_000 + PingRelay.MIN_INTERVAL_MS));
    }

    @Test
    void aRefusedPingDoesNotRestartTheWindow() {
        // Holding the key down produces a stream of attempts. If each refusal pushed the
        // deadline out, a player leaning on the key would be locked out for as long as they
        // held it rather than pinging at the intended rate.
        assertTrue(PingRelay.allow(ALICE, 1_000));
        assertFalse(PingRelay.allow(ALICE, 1_100));
        assertFalse(PingRelay.allow(ALICE, 1_200));
        assertTrue(PingRelay.allow(ALICE, 1_000 + PingRelay.MIN_INTERVAL_MS));
    }

    @Test
    void everyPlayerHasTheirOwnWindow() {
        // One player pinging must never silence anyone else's.
        assertTrue(PingRelay.allow(ALICE, 1_000));
        assertTrue(PingRelay.allow(BOB, 1_000));
        assertFalse(PingRelay.allow(ALICE, 1_050));
        assertFalse(PingRelay.allow(BOB, 1_050));
    }

    @Test
    void anAcceptedPingRecordsItself() {
        // allow() both decides and records. A version that only decided would say yes twice in
        // a row, which is the exact bug the limiter exists to prevent.
        assertTrue(PingRelay.allow(ALICE, 5_000));
        assertFalse(PingRelay.allow(ALICE, 5_000));
    }

    @Test
    void nullPlayerIsRefused() {
        assertFalse(PingRelay.allow(null, 1_000));
    }

    @Test
    void survivesFarMoreCallersThanTheHousekeepingThreshold() {
        // The map prunes itself once it outgrows a plausible party. Push well past that and
        // confirm both that nothing throws and that the pruning does not evict a live window -
        // dropping a recent entry would silently un-limit whoever it belonged to.
        long now = 10_000;
        for (int i = 0; i < 1_000; i++) {
            assertTrue(PingRelay.allow(UUID.nameUUIDFromBytes(("p" + i).getBytes()), now));
        }
        UUID recent = UUID.nameUUIDFromBytes("p999".getBytes());
        assertFalse(PingRelay.allow(recent, now + 1));
    }

    @Test
    void resetForgetsEveryWindow() {
        assertTrue(PingRelay.allow(ALICE, 1_000));
        PingRelay.reset();
        assertTrue(PingRelay.allow(ALICE, 1_000));
    }
}
