package com.crackedgames.craftics.core;

import net.minecraft.util.math.BlockPos;

/**
 * A 2D grid coordinate (x, z) within an arena.
 */
public record GridPos(int x, int z) {

    public BlockPos toBlockPos(BlockPos arenaOrigin, int yOffset) {
        return new BlockPos(arenaOrigin.getX() + x, arenaOrigin.getY() + yOffset, arenaOrigin.getZ() + z);
    }

    public BlockPos toBlockPos(BlockPos arenaOrigin) {
        return toBlockPos(arenaOrigin, 0);
    }

    public double distanceTo(GridPos other) {
        int dx = this.x - other.x;
        int dz = this.z - other.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public int manhattanDistance(GridPos other) {
        return Math.abs(this.x - other.x) + Math.abs(this.z - other.z);
    }

    /**
     * The two cardinal-perpendicular neighbours of this tile for a forward direction
     * {@code (faceDx, faceDz)} (each component -1/0/1). For a forward of (0,1) this
     * returns the tiles to the left and right (x-1, x+1) — used to build the netherite
     * golem mount's 1×3 facing-relative wall (this tile plus these two).
     */
    public java.util.List<GridPos> perpendicularTiles(int faceDx, int faceDz) {
        return java.util.List.of(
            new GridPos(x + faceDz, z - faceDx),
            new GridPos(x - faceDz, z + faceDx));
    }
}
