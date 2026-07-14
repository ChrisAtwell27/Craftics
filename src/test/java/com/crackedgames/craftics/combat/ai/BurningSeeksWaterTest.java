package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A burning mob breaks off and runs for water to put itself out.
 *
 * <p>{@code seekWaterIfBurning} returns null to mean "no opinion", which lets the mob's own AI
 * run untouched. Returning a Move when it shouldn't would hijack every mob's turn, so the
 * null cases matter as much as the positive one.
 */
class BurningSeeksWaterTest {

    private static final int SIZE = 8;

    private static GridArena arena() {
        GridTile[][] tiles = new GridTile[SIZE][SIZE];
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                tiles[x][z] = new GridTile(TileType.NORMAL, null);
            }
        }
        return new GridArena(SIZE, SIZE, tiles, BlockPos.ORIGIN, 1, new GridPos(0, 0));
    }

    private static void paint(GridArena a, TileType type, int x, int z) {
        a.setTile(new GridPos(x, z), new GridTile(type, null));
    }

    /**
     * A zombie with an explicit move speed. The speed MUST be overridden (the last arg):
     * the default is derived from the live entity type, which resolves to nothing without a
     * Minecraft bootstrap, leaving the mob unable to take a single step.
     */
    private static CombatEntity mobAt(int x, int z) {
        return new CombatEntity(1, "minecraft:zombie", new GridPos(x, z),
            /* maxHp */ 20, /* attack */ 3, /* defense */ 0, /* range */ 1,
            /* sizeOverride */ 1, /* speedOverride */ 6);
    }

    @Test
    void aBurningMobMovesTowardWater() {
        GridArena a = arena();
        paint(a, TileType.WATER, 3, 0);

        CombatEntity mob = mobAt(0, 0);
        mob.stackBurning(3, 2);

        EnemyAction action = AIUtils.seekWaterIfBurning(mob, a);
        assertInstanceOf(EnemyAction.Move.class, action, "a burning mob must run for the water");

        var path = ((EnemyAction.Move) action).path();
        assertFalse(path.isEmpty());
        assertEquals(new GridPos(3, 0), path.get(path.size() - 1), "and end up IN the water");
    }

    /** A mob that isn't on fire has no business abandoning its turn. */
    @Test
    void anUnburntMobIgnoresWaterEntirely() {
        GridArena a = arena();
        paint(a, TileType.WATER, 3, 0);

        CombatEntity mob = mobAt(0, 0); // not burning
        assertNull(AIUtils.seekWaterIfBurning(mob, a),
            "no fire, no opinion - the mob's normal AI must run");
    }

    /** No water on the map: the mob must keep fighting rather than freeze up. */
    @Test
    void aBurningMobWithNoWaterKeepsFighting() {
        GridArena a = arena(); // all NORMAL, no water anywhere

        CombatEntity mob = mobAt(0, 0);
        mob.stackBurning(3, 2);

        assertNull(AIUtils.seekWaterIfBurning(mob, a),
            "nothing to run to, so the mob's normal AI must run");
    }

    /** Deep water is an instant kill. Fleeing a burn by drowning is not an improvement. */
    @Test
    void aBurningMobNeverRunsIntoDeepWater() {
        GridArena a = arena();
        paint(a, TileType.DEEP_WATER, 3, 0);

        CombatEntity mob = mobAt(0, 0);
        mob.stackBurning(3, 2);

        assertNull(AIUtils.seekWaterIfBurning(mob, a),
            "deep water kills outright - it must never be treated as a way to douse a burn");
    }

    /** Already standing in water: hold still and let the tile do its work. */
    @Test
    void aBurningMobStandingInWaterStaysPut() {
        GridArena a = arena();
        paint(a, TileType.WATER, 0, 0);

        CombatEntity mob = mobAt(0, 0);
        mob.stackBurning(3, 2);

        assertInstanceOf(EnemyAction.Idle.class, AIUtils.seekWaterIfBurning(mob, a),
            "it is already in the water - wandering back out would be daft");
    }

    /**
     * The nearest pool by raw distance may be unreachable. The mob must still find a farther
     * pool it CAN get to, rather than giving up because its first pick was walled off.
     */
    @Test
    void aBurningMobRoutesToReachableWaterNotJustTheClosest() {
        GridArena a = arena();
        // A near pool at (0,2), sealed behind a wall of obstacles.
        paint(a, TileType.WATER, 0, 2);
        for (int x = 0; x < SIZE; x++) paint(a, TileType.OBSTACLE, x, 1);
        // A farther, open pool at (5,0) on the mob's own side of the wall.
        paint(a, TileType.WATER, 5, 0);

        CombatEntity mob = mobAt(0, 0);
        mob.stackBurning(3, 2);

        EnemyAction action = AIUtils.seekWaterIfBurning(mob, a);
        assertInstanceOf(EnemyAction.Move.class, action,
            "the walled-off pool is closer, but the open one is reachable");

        var path = ((EnemyAction.Move) action).path();
        assertEquals(new GridPos(5, 0), path.get(path.size() - 1),
            "must route to the water it can actually reach");
    }

    /** Water exists but is entirely walled off: keep fighting rather than stall. */
    @Test
    void unreachableWaterIsTreatedAsNoWater() {
        GridArena a = arena();
        paint(a, TileType.WATER, 0, 2);
        for (int x = 0; x < SIZE; x++) paint(a, TileType.OBSTACLE, x, 1); // seals it off

        CombatEntity mob = mobAt(0, 0);
        mob.stackBurning(3, 2);

        assertNull(AIUtils.seekWaterIfBurning(mob, a),
            "it cannot get there, so it must fall back to its normal AI");
    }

    /**
     * The douse still holds for a mob that reaches the water: Soaked puts the fire out, so it
     * stops seeking and goes back to fighting.
     *
     * <p>(Fire immunity is NOT covered here: {@code isFireImmune} delegates to the live
     * {@code MobEntity}, which is null without a Minecraft bootstrap, so a blaze cannot be
     * exercised in a unit test. In game a fire-immune mob never gets burningTurns > 0 at all,
     * so it never reaches this code path.)
     */
    @Test
    void aDousedMobStopsSeekingWater() {
        GridArena a = arena();
        paint(a, TileType.WATER, 3, 0);

        CombatEntity mob = mobAt(0, 0);
        mob.stackBurning(3, 2);
        assertInstanceOf(EnemyAction.Move.class, AIUtils.seekWaterIfBurning(mob, a));

        mob.stackSoaked(2, 1); // it reached the water

        assertEquals(0, mob.getBurningTurns(), "the water put it out");
        assertNull(AIUtils.seekWaterIfBurning(mob, a),
            "no longer on fire, so it goes back to its normal AI");
    }
}
