package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.crackedgames.craftics.core.GridPos;
import java.util.List;

/** Pure-logic tests for {@link MoreTotemsEffects}. */
class MoreTotemsEffectsTest {

    @Test
    void explosionDamage_halfMaxHpPlusOrdinal() {
        // 20 max HP, biome ordinal 3 -> 10 + 3 = 13.
        assertEquals(13, MoreTotemsEffects.explosionDamage(20, 3));
    }

    @Test
    void explosionDamage_oddMaxHpFloorsHalf() {
        // 7 max HP, ordinal 0 -> floor(3.5) + 0 = 3.
        assertEquals(3, MoreTotemsEffects.explosionDamage(7, 0));
    }

    @Test
    void explosionDamage_neverBelowOne() {
        assertEquals(1, MoreTotemsEffects.explosionDamage(1, 0));
        assertEquals(1, MoreTotemsEffects.explosionDamage(0, 0));
    }

    @Test
    void explosionDamage_negativeOrdinalClampedToZero() {
        // 10 max HP, biome ordinal -5 -> max(0,-5) = 0 -> 5 + 0 = 5.
        assertEquals(5, MoreTotemsEffects.explosionDamage(10, -5));
    }

    @Test
    void safestTile_picksFarthestFromNearestEnemy() {
        List<GridPos> candidates = List.of(
            new GridPos(0, 0),   // nearest enemy dist 1
            new GridPos(9, 9));  // nearest enemy dist 18
        List<GridPos> enemies = List.of(
            new GridPos(1, 0));
        assertEquals(new GridPos(9, 9),
            MoreTotemsEffects.safestTile(candidates, enemies));
    }

    @Test
    void safestTile_tieGoesToFirstCandidate() {
        List<GridPos> candidates = List.of(
            new GridPos(5, 0),
            new GridPos(0, 5));  // both manhattan dist 5
        List<GridPos> enemies = List.of(
            new GridPos(0, 0));
        assertEquals(new GridPos(5, 0),
            MoreTotemsEffects.safestTile(candidates, enemies));
    }

    @Test
    void safestTile_noEnemiesReturnsFirstCandidate() {
        List<GridPos> candidates = List.of(
            new GridPos(2, 2),
            new GridPos(3, 3));
        assertEquals(new GridPos(2, 2),
            MoreTotemsEffects.safestTile(candidates, List.of()));
    }

    @Test
    void safestTile_noCandidatesReturnsNull() {
        assertNull(MoreTotemsEffects.safestTile(List.of(),
            List.of(new GridPos(0, 0))));
    }
}
