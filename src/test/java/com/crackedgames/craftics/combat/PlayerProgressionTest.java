package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link PlayerProgression.PlayerStats} level-to-point reconciliation.
 * These guard the fix for force-given levels (e.g. {@code /craftics set_level}) and
 * older saves not surfacing their owed points in the respec menus.
 */
class PlayerProgressionTest {

    @Test
    void expectedPoints_followTheLevelFormula() {
        PlayerProgression.PlayerStats ps = new PlayerProgression.PlayerStats();

        ps.level = 1;
        assertEquals(0, ps.expectedStatPoints());
        assertEquals(0, ps.expectedAffinityPoints());

        // Level 15: even levels 2..14 -> 7 stat points; odd levels 3..15 -> 7 affinities.
        ps.level = 15;
        assertEquals(7, ps.expectedStatPoints());
        assertEquals(7, ps.expectedAffinityPoints());

        ps.level = 20;
        assertEquals(10, ps.expectedStatPoints());
        assertEquals(9, ps.expectedAffinityPoints());
    }

    @Test
    void reconcile_grantsMissingStatPoints_afterForceLevel() {
        PlayerProgression.PlayerStats ps = new PlayerProgression.PlayerStats();
        ps.level = 20;          // force-leveled with no points credited
        ps.unspentPoints = 0;

        ps.reconcilePoints();

        assertEquals(10, ps.unspentPoints, "level 20 owes 10 stat points");
    }

    @Test
    void reconcile_accountsForAlreadyAllocatedPoints() {
        PlayerProgression.PlayerStats ps = new PlayerProgression.PlayerStats();
        ps.level = 20;
        ps.unspentPoints = 0;
        ps.setPoints(PlayerProgression.Stat.MELEE_POWER, 4);   // 4 already spent

        ps.reconcilePoints();

        assertEquals(6, ps.unspentPoints, "10 owed minus 4 allocated = 6 unspent");
    }

    @Test
    void reconcile_neverRemovesPoints() {
        PlayerProgression.PlayerStats ps = new PlayerProgression.PlayerStats();
        ps.level = 4;            // owes only 2
        ps.unspentPoints = 99;   // over-credited by an old buggy command

        ps.reconcilePoints();

        assertEquals(99, ps.unspentPoints, "reconcile must not claw points back");
    }

    @Test
    void reconcile_isIdempotent() {
        PlayerProgression.PlayerStats ps = new PlayerProgression.PlayerStats();
        ps.level = 13;
        ps.unspentPoints = 0;

        ps.reconcilePoints();
        int afterFirst = ps.unspentPoints;
        ps.reconcilePoints();

        assertEquals(afterFirst, ps.unspentPoints);
        assertEquals(6, afterFirst, "level 13 owes 6 stat points");
    }
}
