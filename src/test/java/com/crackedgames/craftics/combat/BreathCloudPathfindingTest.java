package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A dragon-breath cloud is priced like lava for a walking player, so a clicked path goes around
 * one when there is a way around, and wading through costs the same extra Speed lava does.
 */
class BreathCloudPathfindingTest {

    private static GridArena arena() {
        GridTile[][] tiles = new GridTile[5][5];
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                tiles[x][z] = new GridTile(TileType.NORMAL, null);
            }
        }
        return new GridArena(5, 5, tiles, BlockPos.ORIGIN, 1, new GridPos(0, 2));
    }

    @Test
    void walkRoutesAroundACloudInTheWay() {
        GridArena a = arena();
        GridPos cloud = new GridPos(2, 2);
        a.setBreathCloud(cloud, 3, 4);

        Pathfinding.Path p = Pathfinding.findPlayerPathWithJumps(
            a, new GridPos(0, 2), new GridPos(4, 2), 10, false, false, false);

        assertFalse(p.isEmpty());
        assertFalse(p.steps().contains(cloud), "straight through the cloud must lose to the detour");
        assertEquals(6, p.cost(), "the 6-step detour beats 3 steps plus a lava-priced cloud tile");
    }

    @Test
    void wadingACloudCostsWhatLavaCosts() {
        GridArena a = arena();
        // A wall of cloud across the whole arena: no way around.
        for (int z = 0; z < 5; z++) a.setBreathCloud(new GridPos(2, z), 3, 4);

        Pathfinding.Path p = Pathfinding.findPlayerPathWithJumps(
            a, new GridPos(0, 2), new GridPos(4, 2), 10, false, false, false);

        assertFalse(p.isEmpty());
        assertEquals(3 + Pathfinding.LAVA_STEP_COST, p.cost());
    }
}
