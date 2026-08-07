package com.crackedgames.craftics.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GridPosTest {
    @Test
    void diagonalTilesCountAsOneStep() {
        GridPos origin = new GridPos(0, 0);
        assertEquals(1, origin.chebyshevDistanceTo(new GridPos(1, 1)));
        assertEquals(1, origin.chebyshevDistanceTo(new GridPos(-1, 1)));
    }

    @Test
    void fartherTilesUseTheHigherAxisDelta() {
        GridPos origin = new GridPos(2, 4);
        assertEquals(3, origin.chebyshevDistanceTo(new GridPos(5, 1)));
        assertEquals(2, origin.chebyshevDistanceTo(new GridPos(4, 2)));
    }
}
