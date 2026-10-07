package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A mob that fire, lava and decay cannot hurt walks through them.
 *
 * <p>The bug this guards: every boss is hazard-immune, but pathfinding priced any damaging tile
 * at 50 steps for everyone and never asked who was walking. So bosses treated their own hazards
 * as walls. The Bastion Brute boxed itself in with its own fire cross, and the Wither, which
 * rots every tile it stands on, could not walk back over ground it had already crossed.
 */
class HazardImmunePathfindingTest {

    /** 5 wide, 3 deep, with a wall of fire across the middle column: no way around it. */
    private static GridArena fireWalledArena() {
        GridTile[][] tiles = new GridTile[5][3];
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 3; z++) {
                tiles[x][z] = new GridTile(x == 2 ? TileType.FIRE : TileType.NORMAL, null);
            }
        }
        // Player parked in a corner, out of the lane the tests walk.
        return new GridArena(5, 3, tiles, BlockPos.ORIGIN, 1, new GridPos(4, 2));
    }

    private static CombatEntity mob(boolean hazardImmune) {
        CombatEntity e = new CombatEntity(7, "minecraft:skeleton", new GridPos(0, 1), 40, 6, 0, 1, 1, 4);
        e.setHazardImmune(hazardImmune);
        return e;
    }

    private static final GridPos START = new GridPos(0, 1);
    private static final GridPos FAR_SIDE = new GridPos(4, 1);

    @Test
    void hazardImmuneMobWalksStraightThroughFire() {
        GridArena a = fireWalledArena();
        List<GridPos> path = Pathfinding.findPath(a, START, FAR_SIDE, 4, mob(true));

        assertEquals(List.of(new GridPos(1, 1), new GridPos(2, 1), new GridPos(3, 1), FAR_SIDE), path);
    }

    @Test
    void ordinaryMobStillWillNotCrossIt() {
        GridArena a = fireWalledArena();
        List<GridPos> path = Pathfinding.findPath(a, START, FAR_SIDE, 4, mob(false));

        assertFalse(path.contains(new GridPos(2, 1)), "fire must still price an ordinary mob out");
        assertFalse(path.contains(FAR_SIDE));
    }

    @Test
    void hazardImmuneMobCanReachTheFarSide() {
        GridArena a = fireWalledArena();
        CombatEntity boss = mob(true);
        Set<GridPos> reach = Pathfinding.getReachableTiles(a, START, 4, 1, 1, boss, false, false, false);
        assertTrue(reach.contains(FAR_SIDE));

        assertEquals(FAR_SIDE, Pathfinding.findClosestReachableTo(a, START, FAR_SIDE, 4, boss));
    }

    @Test
    void ordinaryMobCannotReachTheFarSide() {
        GridArena a = fireWalledArena();
        CombatEntity grunt = mob(false);
        Set<GridPos> reach = Pathfinding.getReachableTiles(a, START, 4, 1, 1, grunt, false, false, false);
        assertFalse(reach.contains(FAR_SIDE));
        assertFalse(reach.contains(new GridPos(2, 1)));
    }

    @Test
    void mudStillSlowsAHazardImmuneMob() {
        // Immunity is to damage. Mud does not hurt, it bogs you down, and it still should.
        GridTile[][] tiles = new GridTile[5][1];
        for (int x = 0; x < 5; x++) tiles[x][0] = new GridTile(x == 1 ? TileType.MUD : TileType.NORMAL, null);
        GridArena a = new GridArena(5, 1, tiles, BlockPos.ORIGIN, 1, new GridPos(4, 0));
        CombatEntity boss = new CombatEntity(7, "minecraft:skeleton", new GridPos(0, 0), 40, 6, 0, 1, 1, 4);
        boss.setHazardImmune(true);

        Set<GridPos> reach = Pathfinding.getReachableTiles(a, new GridPos(0, 0), 4, 1, 1, boss, false, false, false);

        assertTrue(reach.contains(new GridPos(2, 0)), "mud (3) + one step = 4, within reach");
        assertFalse(reach.contains(new GridPos(3, 0)), "one further is out of a 4-step budget");
    }
}
