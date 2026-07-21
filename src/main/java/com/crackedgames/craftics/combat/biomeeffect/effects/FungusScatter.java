package com.crackedgames.craftics.combat.biomeeffect.effects;

import com.crackedgames.craftics.combat.miniboss.MinibossContext;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;

import java.util.Random;

/**
 * Scatters a handful of walkable fungus-hazard tiles across the arena floor at fight start -
 * the crimson/warped forest's cobweb-like obstacle. Crossing one inflicts a status effect
 * (handled by the fungus scan in CombatManager.handleMove); it never blocks the player.
 *
 * <p>Placed via {@link MinibossContext#placeTemporaryTile} with a near-permanent duration
 * (the arena rebuild on the next level resets it), matching how the jungle bog patch persists
 * for the whole encounter rather than reverting after a few rounds.
 */
final class FungusScatter {

    private static final int COUNT_MIN = 3;
    private static final int COUNT_MAX = 5;  // inclusive - a light scatter, not a maze
    private static final int DURATION = 999; // effectively "lasts the level"
    private static final int PLACEMENT_ATTEMPTS = 12; // per tile, before giving up on it

    private FungusScatter() {}

    static void scatter(MinibossContext ctx, TileType fungusType) {
        GridArena arena = ctx.arena();
        int width = arena.getWidth();
        int height = arena.getHeight();
        Random rng = ctx.rng();
        GridPos playerStart = new GridPos(width / 2, 0);

        int target = COUNT_MIN + rng.nextInt(COUNT_MAX - COUNT_MIN + 1);
        for (int i = 0; i < target; i++) {
            GridPos pos = findOpenFloor(arena, width, height, playerStart, rng);
            if (pos == null) continue;
            ctx.placeTemporaryTile(pos, fungusType, DURATION);
        }
    }

    /** A random walkable NORMAL floor tile (not the player start, not an already-special tile),
     *  a few attempts at a time. Null if nothing suitable was found within the budget. */
    private static GridPos findOpenFloor(GridArena arena, int width, int height,
                                         GridPos playerStart, Random rng) {
        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
            int x = rng.nextInt(Math.max(1, width));
            int z = rng.nextInt(Math.max(1, height));
            GridPos pos = new GridPos(x, z);
            if (pos.equals(playerStart)) continue;
            GridTile tile = arena.getTile(pos);
            if (tile == null || !tile.isWalkable()) continue;
            // Only take plain floor, so we don't paint over water/mud/hazard tiles.
            if (tile.getType() != TileType.NORMAL) continue;
            if (arena.getOccupant(pos) != null) continue;
            return pos;
        }
        return null;
    }
}
