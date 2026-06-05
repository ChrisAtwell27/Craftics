package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AoeShapesInstrumentTest {

    private static boolean has(List<GridPos> tiles, int x, int z) {
        return tiles.contains(new GridPos(x, z));
    }

    @Test
    void filledDisc_radius2_covers25TilesIncludingCenter() {
        List<GridPos> tiles = AoeShapes.filledDisc(new GridPos(0, 0), 2);
        assertEquals(25, tiles.size(), "5x5 Chebyshev disc is 25 tiles");
        assertTrue(has(tiles, 0, 0), "includes center");
        assertTrue(has(tiles, 2, 2), "includes far corner");
        assertTrue(has(tiles, -2, 1), "includes edge tile");
        assertFalse(has(tiles, 3, 0), "excludes radius 3");
    }

    @Test
    void diagonals_length3_hasFourArmsNoCenter() {
        List<GridPos> tiles = AoeShapes.diagonals(new GridPos(0, 0), 3);
        assertEquals(12, tiles.size());
        assertFalse(has(tiles, 0, 0), "center excluded");
        assertTrue(has(tiles, 1, 1));
        assertTrue(has(tiles, 3, 3));
        assertTrue(has(tiles, -3, 3));
        assertTrue(has(tiles, 2, -2));
        assertFalse(has(tiles, 1, 0), "cardinal tile not in diagonals");
    }

    @Test
    void expandingRingTiers_returnsRingsR1R2R3InOrder() {
        List<List<GridPos>> tiers = AoeShapes.expandingRingTiers(new GridPos(0, 0), 3);
        assertEquals(3, tiers.size());
        assertEquals(8, tiers.get(0).size(), "ring r1 = 8 tiles");
        assertEquals(16, tiers.get(1).size(), "ring r2 = 16 tiles");
        assertEquals(24, tiers.get(2).size(), "ring r3 = 24 tiles");
    }

    @Test
    void starArms_eightArmsEachReachingRadius() {
        List<List<GridPos>> arms = AoeShapes.starArms(new GridPos(0, 0), 3);
        assertEquals(8, arms.size(), "8 arms (4 cardinal + 4 diagonal)");
        for (List<GridPos> arm : arms) {
            assertEquals(3, arm.size(), "each arm has 'radius' tiles");
            assertFalse(arm.contains(new GridPos(0, 0)), "arms exclude center");
        }
    }
}
