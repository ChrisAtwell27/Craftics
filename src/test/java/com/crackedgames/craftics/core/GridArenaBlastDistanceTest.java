package com.crackedgames.craftics.core;

import com.crackedgames.craftics.combat.CombatEntity;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How far a ground blast (end crystal, TNT, cactus, lightning rod) is from something it might hit.
 *
 * <p>The bug this guards: blasts measured to {@code getGridPos()}, and a background boss's grid
 * position is a sentinel, not a body. The Ender Dragon's sentinel is a corner of its perch zone and
 * stays there while the dragon is parked in the sky, so a crystal placed beside that corner and
 * shot took a slice of the dragon's max HP while it was untargetable by anything else.
 */
class GridArenaBlastDistanceTest {

    private static GridArena arena() {
        GridTile[][] tiles = new GridTile[12][12];
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 12; z++) {
                tiles[x][z] = new GridTile(TileType.NORMAL, null);
            }
        }
        return new GridArena(12, 12, tiles, BlockPos.ORIGIN, 1, new GridPos(0, 0));
    }

    private static CombatEntity mob(int id, int x, int z) {
        return new CombatEntity(id, "minecraft:zombie", new GridPos(x, z), 20, 3, 0, 1, 1, 1);
    }

    /** A background boss parked off the grid: sentinel grid position, no registered tiles. */
    private static CombatEntity airborneDragon(int x, int z) {
        CombatEntity d = mob(500, x, z);
        d.setBoss(true);
        d.setBackgroundBoss(true);
        return d;
    }

    @Test
    void ordinaryMobIsMeasuredToItsTile() {
        GridArena a = arena();
        CombatEntity m = mob(1, 5, 5);
        assertEquals(3, a.blastDistance(m, new GridPos(4, 3)));
        assertEquals(0, a.blastDistance(m, new GridPos(5, 5)));
    }

    @Test
    void airborneBackgroundBossIsOutOfEveryBlast() {
        GridArena a = arena();
        CombatEntity dragon = airborneDragon(4, 2);
        // Right on the sentinel corner - the exact spot the exploit used.
        assertEquals(Integer.MAX_VALUE, a.blastDistance(dragon, new GridPos(4, 2)));
    }

    @Test
    void perchedBackgroundBossIsMeasuredToItsNearestLandingTile() {
        GridArena a = arena();
        CombatEntity dragon = airborneDragon(4, 2);
        // Perched: a 3x7 landing zone registered from the sentinel corner, as CombatManager does.
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 7; dz++) {
                a.getOccupants().put(new GridPos(4 + dx, 2 + dz), dragon);
            }
        }
        assertEquals(0, a.blastDistance(dragon, new GridPos(5, 5)));   // inside the zone
        assertEquals(2, a.blastDistance(dragon, new GridPos(8, 5)));   // two east of the x=6 edge
        assertEquals(1, a.blastDistance(dragon, new GridPos(6, 9)));   // one south of the z=8 edge
    }
}
