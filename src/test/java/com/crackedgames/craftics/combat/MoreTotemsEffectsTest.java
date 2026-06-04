package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

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
    void safestTile_picksFarthestFromNearestEnemy() {
        java.util.List<com.crackedgames.craftics.core.GridPos> candidates = java.util.List.of(
            new com.crackedgames.craftics.core.GridPos(0, 0),   // nearest enemy dist 1
            new com.crackedgames.craftics.core.GridPos(9, 9));  // nearest enemy dist 18
        java.util.List<com.crackedgames.craftics.core.GridPos> enemies = java.util.List.of(
            new com.crackedgames.craftics.core.GridPos(1, 0));
        assertEquals(new com.crackedgames.craftics.core.GridPos(9, 9),
            MoreTotemsEffects.safestTile(candidates, enemies));
    }

    @Test
    void safestTile_tieGoesToFirstCandidate() {
        java.util.List<com.crackedgames.craftics.core.GridPos> candidates = java.util.List.of(
            new com.crackedgames.craftics.core.GridPos(5, 0),
            new com.crackedgames.craftics.core.GridPos(0, 5));  // both manhattan dist 5
        java.util.List<com.crackedgames.craftics.core.GridPos> enemies = java.util.List.of(
            new com.crackedgames.craftics.core.GridPos(0, 0));
        assertEquals(new com.crackedgames.craftics.core.GridPos(5, 0),
            MoreTotemsEffects.safestTile(candidates, enemies));
    }

    @Test
    void safestTile_noEnemiesReturnsFirstCandidate() {
        java.util.List<com.crackedgames.craftics.core.GridPos> candidates = java.util.List.of(
            new com.crackedgames.craftics.core.GridPos(2, 2),
            new com.crackedgames.craftics.core.GridPos(3, 3));
        assertEquals(new com.crackedgames.craftics.core.GridPos(2, 2),
            MoreTotemsEffects.safestTile(candidates, java.util.List.of()));
    }

    @Test
    void safestTile_noCandidatesReturnsNull() {
        assertNull(MoreTotemsEffects.safestTile(java.util.List.of(),
            java.util.List.of(new com.crackedgames.craftics.core.GridPos(0, 0))));
    }
}
