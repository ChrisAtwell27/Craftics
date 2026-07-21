package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SculkRangeTest {
    @Test void sameTileInRange() { assertTrue(SculkRange.within(new GridPos(5,5), new GridPos(5,5), 2)); }
    @Test void twoTilesDiagonalInRange() { assertTrue(SculkRange.within(new GridPos(5,5), new GridPos(7,7), 2)); }
    @Test void threeTilesOut() { assertFalse(SculkRange.within(new GridPos(5,5), new GridPos(8,5), 2)); }
    @Test void twoStraightIn() { assertTrue(SculkRange.within(new GridPos(5,5), new GridPos(5,7), 2)); }
}
