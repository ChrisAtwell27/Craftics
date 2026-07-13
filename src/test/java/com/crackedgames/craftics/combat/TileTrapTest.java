package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the pure trap bookkeeping in {@link TileTrap} - how long a trap lives, when it
 * counts as spent, and which kinds survive being triggered. The parts that touch the arena
 * are driven from {@code CombatManager} and are covered by the in-game smoke checklist.
 */
class TileTrapTest {

    private static final UUID OWNER = new UUID(1L, 2L);

    @Test
    void freshTrap_startsAtItsKindsDuration() {
        assertEquals(TileTrap.FLOWER_FIELD_TURNS,
            TileTrap.fresh(TileTrap.Kind.FLOWER_FIELD, OWNER).turnsLeft());
        assertEquals(TileTrap.BUBBLE_COLUMN_TURNS,
            TileTrap.fresh(TileTrap.Kind.BUBBLE_COLUMN, OWNER).turnsLeft());
    }

    @Test
    void freshTrap_isNotExpired() {
        for (TileTrap.Kind kind : TileTrap.Kind.values()) {
            assertFalse(TileTrap.fresh(kind, OWNER).expired(), kind + " should start alive");
        }
    }

    @Test
    void agingExactlyItsDuration_expiresAndNotSooner() {
        for (TileTrap.Kind kind : TileTrap.Kind.values()) {
            TileTrap trap = TileTrap.fresh(kind, OWNER);
            for (int round = 1; round < kind.duration(); round++) {
                trap = trap.aged();
                assertFalse(trap.expired(), kind + " expired early, after " + round + " rounds");
            }
            assertTrue(trap.aged().expired(), kind + " outlived its duration");
        }
    }

    @Test
    void bonusTurns_extendTheTrap_andNeverShortenIt() {
        for (TileTrap.Kind kind : TileTrap.Kind.values()) {
            int normal = TileTrap.fresh(kind, OWNER).turnsLeft();
            assertEquals(normal + 1, TileTrap.fresh(kind, OWNER, 1).turnsLeft(), kind + " +1");
            assertEquals(normal, TileTrap.fresh(kind, OWNER, 0).turnsLeft(), kind + " +0");
            assertEquals(normal, TileTrap.fresh(kind, OWNER, -5).turnsLeft(),
                kind + " a negative bonus must not cut the trap short");
        }
    }

    @Test
    void agingPreservesKindAndOwner() {
        TileTrap aged = TileTrap.fresh(TileTrap.Kind.FLOWER_FIELD, OWNER).aged();
        assertEquals(TileTrap.Kind.FLOWER_FIELD, aged.kind());
        assertEquals(OWNER, aged.owner());
    }

    @Test
    void onlyTheBubbleColumn_isSpentOnTrigger() {
        assertTrue(TileTrap.Kind.BUBBLE_COLUMN.consumedOnTrigger(),
            "the column pops behind whoever walks into it");
        assertFalse(TileTrap.Kind.FLOWER_FIELD.consumedOnTrigger(),
            "the field keeps working for its whole duration");
    }

    /**
     * The particle accessors resolve real Minecraft registry objects, so a unit test can't
     * touch them without a bootstrap. Durations are pure, and a trap that lasted zero
     * rounds would never be seen at all.
     */
    @Test
    void everyKind_lastsAtLeastOneRound() {
        for (TileTrap.Kind kind : TileTrap.Kind.values()) {
            assertTrue(kind.duration() > 0, kind + " must last at least a round");
        }
    }
}
