package com.crackedgames.craftics.combat.ai.deeperanddarker;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Deeper-and-Darker Angler Fish (Blooming Caverns).
 *
 * <p>A water-locked ambusher: it can only travel across {@code WATER} tiles, so
 * on dry ground it's harmless and drifts. But the moment the player steps into
 * the water with it, it turns lethal - it surges at up to speed 5 and bites for
 * its full attack power. Stay out of the water and the angler is a non-threat;
 * fight it in the water on its terms and it's one of the deadliest things in the
 * biome.
 */
public class AnglerFishAI implements EnemyAI {

    /** Burst speed once the player is in the water with it. */
    private static final int LUNGE_SPEED = 5;

    /**
     * Only a threat while the player is in the water. On land the angler can't
     * reach anyone, so the anti-farm auto-end treats it as passive - a room of
     * beached anglers won't stall the fight forever.
     */
    @Override
    public boolean isHostileThreat(CombatEntity self, GridArena arena, GridPos playerPos) {
        return isWater(arena, playerPos);
    }

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();

        // Adjacent bite - full damage. (Only reachable when the player is in or
        // beside the water the angler swims in.)
        if (self.minDistanceTo(playerPos) == 1 && isWater(arena, myPos)) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        // Player in the water: hunt at burst speed along water tiles.
        if (isWater(arena, playerPos)) {
            GridPos target = findWaterAdjacentToPlayer(arena, myPos, playerPos);
            if (target != null) {
                List<GridPos> path = waterPath(arena, myPos, target, self, LUNGE_SPEED);
                if (!path.isEmpty()) {
                    GridPos end = path.get(path.size() - 1);
                    if (end.manhattanDistance(playerPos) == 1) {
                        return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                    }
                    return new EnemyAction.Move(path);
                }
            }
        }

        // Player on dry land (or unreachable water): drift idly on water tiles.
        return driftOnWater(self, arena);
    }

    /** A water tile adjacent to the player that the angler can occupy. */
    private GridPos findWaterAdjacentToPlayer(GridArena arena, GridPos myPos, GridPos playerPos) {
        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            GridPos p = new GridPos(playerPos.x() + d[0], playerPos.z() + d[1]);
            if (!arena.isInBounds(p) || arena.isOccupied(p) || !isWater(arena, p)) continue;
            int dist = myPos.manhattanDistance(p);
            if (dist < bestDist) {
                bestDist = dist;
                best = p;
            }
        }
        return best;
    }

    /** A water-only path (every tile of the route must be water). Empty if none. */
    private List<GridPos> waterPath(GridArena arena, GridPos from, GridPos to,
                                    CombatEntity self, int speed) {
        List<GridPos> path = Pathfinding.findPath(arena, from, to, speed, self, false, false, true);
        if (path.isEmpty() || !allWater(arena, path)) return List.of();
        return path;
    }

    /** Idle wander on water when there's nothing to hunt. */
    private EnemyAction driftOnWater(CombatEntity self, GridArena arena) {
        GridPos myPos = self.getGridPos();
        if (!isWater(arena, myPos)) return new EnemyAction.Idle();
        List<int[]> dirs = new ArrayList<>(List.of(
            new int[]{1, 0}, new int[]{-1, 0}, new int[]{0, 1}, new int[]{0, -1}));
        Collections.shuffle(dirs, ThreadLocalRandom.current());
        for (int[] dir : dirs) {
            GridPos target = new GridPos(myPos.x() + dir[0], myPos.z() + dir[1]);
            if (!arena.isInBounds(target) || arena.isOccupied(target) || !isWater(arena, target)) continue;
            List<GridPos> path = waterPath(arena, myPos, target, self, 1);
            if (!path.isEmpty()) return new EnemyAction.Move(path);
        }
        return new EnemyAction.Idle();
    }

    private boolean isWater(GridArena arena, GridPos pos) {
        var tile = arena.getTile(pos);
        return tile != null && tile.isWater();
    }

    private boolean allWater(GridArena arena, List<GridPos> path) {
        for (GridPos pos : path) {
            var tile = arena.getTile(pos);
            if (tile == null || !tile.isWater()) return false;
        }
        return true;
    }
}
