package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Locks in the sized-entity distance math that the 2x2 clip fix relies on.
 * A footprint "covers" a point exactly when the Manhattan distance from the
 * footprint to that point is 0; "adjacent" is distance 1. The magma cube / slime
 * AIs and the move-commit safety nets use these to refuse landings that would put
 * the mob's body on the player.
 */
class SizedEntityFootprintTest {

    @Test
    void size2Footprint_coversAllFourAnchorTiles() {
        GridPos anchor = new GridPos(4, 5);
        // Footprint of a 2x2 at (4,5) is (4,5),(5,5),(4,6),(5,6) — distance 0 to each.
        for (GridPos tile : new GridPos[]{
                new GridPos(4, 5), new GridPos(5, 5), new GridPos(4, 6), new GridPos(5, 6)}) {
            assertEquals(0, CombatEntity.minDistanceFromSizedEntity(anchor, 2, tile),
                "footprint should cover " + tile);
        }
    }

    @Test
    void anchorOffsetByOne_stillOverlapsPlayer_theClipCase() {
        // The exact bug: anchor is one tile from the player (looks "adjacent" by a
        // naive anchor check) but the 2x2 footprint actually covers the player tile.
        GridPos player = new GridPos(5, 5);
        GridPos anchorLeftOfPlayer = new GridPos(4, 5); // footprint includes (5,5)
        assertEquals(0, CombatEntity.minDistanceFromSizedEntity(anchorLeftOfPlayer, 2, player),
            "a 2x2 mob anchored at (4,5) overlaps the player at (5,5)");
        // A size-1 anchor check would wrongly call this distance 1 (safe):
        assertEquals(1, anchorLeftOfPlayer.manhattanDistance(player));
    }

    @Test
    void size2Footprint_adjacentWhenOneTileAway() {
        GridPos player = new GridPos(5, 5);
        // Anchor at (3,5): footprint (3,5),(4,5),(3,6),(4,6); nearest tile (4,5) is
        // Manhattan distance 1 from the player — adjacent, not overlapping.
        assertEquals(1, CombatEntity.minDistanceFromSizedEntity(new GridPos(3, 5), 2, player));
    }

    @Test
    void chebyshev_lets2x2BeHitFromDiagonal() {
        CombatEntity spider = new CombatEntity(1, "minecraft:spider", new GridPos(4, 4), 10, 2, 0, 1, 2, 1);
        // Player diagonally off the footprint corner: Chebyshev 1 (hittable by melee),
        // Manhattan 2 (would have been out of melee range before the fix).
        GridPos diagonal = new GridPos(3, 3);
        assertEquals(1, spider.minChebyshevDistanceTo(diagonal));
        assertEquals(2, spider.minDistanceTo(diagonal));
    }
}
