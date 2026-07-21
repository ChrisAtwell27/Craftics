package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;

/** Pure geometry for sculk-sensor proximity. Chebyshev (king-move) distance. */
public final class SculkRange {
    private SculkRange() {}

    /** True if {@code a} is within {@code r} tiles of {@code b} (Chebyshev / 8-direction). */
    public static boolean within(GridPos a, GridPos b, int r) {
        return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.z() - b.z())) <= r;
    }
}
