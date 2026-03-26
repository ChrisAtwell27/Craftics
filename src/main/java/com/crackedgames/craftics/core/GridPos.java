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
}
