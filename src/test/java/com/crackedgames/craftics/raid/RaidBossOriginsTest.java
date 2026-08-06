package com.crackedgames.craftics.raid;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RaidBossOriginsTest {

    private static final UUID A = new UUID(1L, 1L);
    private static final UUID B = new UUID(2L, 2L);

    @BeforeEach
    void reset() {
        RaidBossOrigins.clear();
    }

    @Test
    void remembersAndReturnsAnOrigin() {
        RaidBossOrigins.Origin o = new RaidBossOrigins.Origin(
            "craftics:island/abc", 10.5, 65.0, -3.5, 90f, 0f);
        RaidBossOrigins.remember(A, o);
        assertEquals(o, RaidBossOrigins.peek(A));
        assertEquals(1, RaidBossOrigins.size());
    }

    @Test
    void takeRemovesTheEntry() {
        RaidBossOrigins.remember(A, new RaidBossOrigins.Origin("x", 0, 0, 0, 0f, 0f));
        assertNotNull(RaidBossOrigins.take(A));
        assertNull(RaidBossOrigins.take(A));
        assertEquals(0, RaidBossOrigins.size());
    }

    @Test
    void unknownPlayersReturnNull() {
        assertNull(RaidBossOrigins.peek(B));
        assertNull(RaidBossOrigins.take(B));
        assertNull(RaidBossOrigins.peek(null));
    }

    @Test
    void rememberingTwiceKeepsTheFirstOrigin() {
        RaidBossOrigins.Origin first = new RaidBossOrigins.Origin("first", 1, 2, 3, 0f, 0f);
        RaidBossOrigins.remember(A, first);
        RaidBossOrigins.remember(A, new RaidBossOrigins.Origin("second", 9, 9, 9, 0f, 0f));
        assertEquals(first, RaidBossOrigins.peek(A));
    }

    @Test
    void forgetDropsWithoutReturning() {
        RaidBossOrigins.remember(A, new RaidBossOrigins.Origin("x", 0, 0, 0, 0f, 0f));
        RaidBossOrigins.forget(A);
        assertEquals(0, RaidBossOrigins.size());
    }
}
