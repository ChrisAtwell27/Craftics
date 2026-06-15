package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the netherite mount's 1×3 wall sentinel (see
 * {@code CombatManager#refreshMountWall} and {@link Pathfinding#isBlockedBy}).
 * The sentinel is flagged {@code isAlly()} so it blocks enemies, but it must be
 * passable for the rider's own pathing ({@code self == null}) so a persistent
 * wall never traps the rider - that is the fix for the "mount only takes up 1x1"
 * report.
 */
class MountWallPathfindingTest {

    /** A 5×5 all-NORMAL-floor arena; tiles built with a null block to avoid Minecraft bootstrap. */
    private static GridArena flatArena() {
        GridTile[][] tiles = new GridTile[5][5];
        for (int x = 0; x < 5; x++)
            for (int z = 0; z < 5; z++)
                tiles[x][z] = new GridTile(TileType.NORMAL, null);
        return new GridArena(5, 5, tiles, new BlockPos(0, 64, 0), 1, new GridPos(2, 2));
    }

    /** A mount-wall sentinel exactly as refreshMountWall builds it, placed on {@code tile}. */
    private static CombatEntity placeSentinel(GridArena arena, GridPos tile) {
        CombatEntity wall = new CombatEntity(-1, "craftics:mount_wall", tile, 1, 0, 0, 1);
        wall.setAlly(true);
        wall.setMountWall(true);
        arena.getOccupants().put(tile, wall);
        return wall;
    }

    private static CombatEntity enemy(GridPos tile) {
        return new CombatEntity(100, "minecraft:zombie", tile, 10, 3, 0, 1); // isAlly() defaults false
    }

    @Test
    void mountWall_blocksEnemyFromStandingOnSideTile() {
        GridArena arena = flatArena();
        arena.setPlayerGridPos(new GridPos(2, 2));
        GridPos sideTile = new GridPos(3, 2);
        placeSentinel(arena, sideTile);

        CombatEntity zombie = enemy(new GridPos(4, 2));
        // Can't path to (stop on) the wall tile.
        assertTrue(Pathfinding.findPath(arena, new GridPos(4, 2), sideTile, 6, zombie).isEmpty(),
            "enemy must not be able to stop on the mount-wall side tile");
    }

    @Test
    void mountWall_blocksEnemyTraversalButAllowsGoingAround() {
        GridArena arena = flatArena();
        arena.setPlayerGridPos(new GridPos(2, 2));
        placeSentinel(arena, new GridPos(3, 2));

        CombatEntity zombie = enemy(new GridPos(4, 2));
        Set<GridPos> reach = Pathfinding.getReachableTiles(arena, new GridPos(4, 2), 5, 1, zombie, false);
        assertFalse(reach.contains(new GridPos(3, 2)),
            "the wall tile itself is not reachable by an enemy");
        assertTrue(reach.contains(new GridPos(3, 1)),
            "the enemy can still route AROUND the wall");
    }

    @Test
    void mountWall_passableForRider_canStopOnOwnSideTile() {
        GridArena arena = flatArena();
        arena.setPlayerGridPos(new GridPos(2, 2));
        GridPos sideTile = new GridPos(3, 2);
        placeSentinel(arena, sideTile);

        // Player movement pathfinds with self == null. The rider's own footprint must be
        // both reachable and a valid landing tile (so it stays in the move highlight).
        Set<GridPos> reach = Pathfinding.getReachableTiles(arena, new GridPos(2, 2), 3, false);
        assertTrue(reach.contains(sideTile),
            "the rider can move onto its own mount-wall side tile");

        assertFalse(Pathfinding.findPath(arena, new GridPos(2, 1), sideTile, 6).isEmpty(),
            "the rider (self == null) can path to and stop on the wall tile");
    }

    @Test
    void perpendicularTiles_orientToFacing() {
        GridPos center = new GridPos(5, 5);
        // Facing south (0,1): side tiles are east/west.
        assertEquals(Set.of(new GridPos(6, 5), new GridPos(4, 5)),
            Set.copyOf(center.perpendicularTiles(0, 1)));
        // Facing east (1,0): side tiles are north/south.
        assertEquals(Set.of(new GridPos(5, 4), new GridPos(5, 6)),
            Set.copyOf(center.perpendicularTiles(1, 0)));
    }
}
