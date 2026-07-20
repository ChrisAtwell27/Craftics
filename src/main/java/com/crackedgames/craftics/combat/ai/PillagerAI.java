package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.List;

/**
 * Pillager AI: Tactical crossbow raider.
 * - CROSSBOW VOLLEY: fires ONLY along a clear 4-directional straight line - the target
 *   must share this tile's row or column (no diagonals), within range, with no wall/
 *   obstacle or other entity on the tiles in between. A diagonal or blocked target is
 *   never a valid shot; the pillager repositions onto a clear firing lane instead.
 * - REPOSITION: moves then shoots in same turn - never wastes a turn
 * - RETREAT AND FIRE: backs up when any threat (player or ally pet) closes
 *   to within 2 tiles, snapping off a shot only if it still has a clear straight-line shot
 * - Smart positioning - tries to keep its target near max range and stays
 *   off hazard tiles
 */
public class PillagerAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        // Honor the per-biome range from the enemy entry instead of a
        // hardcoded 4 - forest pillagers are registered at range 3.
        int range = Math.max(1, self.getRange());

        List<GridPos> threats = AIUtils.threatPositions(arena, playerPos);
        boolean clearShotNow = hasClearShot(self, arena, myPos, playerPos, range);

        // RETREAT AND FIRE: if anything is too close, back up - preferably to a tile that
        // still keeps a clear straight-line shot.
        if (AIUtils.minThreatDistance(myPos, threats) <= 2) {
            GridPos fleeTarget = AIUtils.bestRetreatTile(self, arena, threats);
            if (fleeTarget != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, fleeTarget, self.getMoveSpeed(), self);
                if (!path.isEmpty()) {
                    GridPos endPos = path.get(path.size() - 1);
                    if (hasClearShot(self, arena, endPos, playerPos, range)) {
                        return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                    }
                    return new EnemyAction.Move(path);
                }
            }
            // Pinned and can't retreat: fire only if we actually have a straight-line shot
            // from here; otherwise fall through and try to reposition onto a firing lane.
            if (clearShotNow) {
                return new EnemyAction.RangedAttack(self.getAttackPower(), "crossbow");
            }
        }

        // Clear straight-line shot from here - fire.
        if (clearShotNow) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "crossbow");
        }

        // No shot from here - move onto a clear firing lane (aligned, in range, unblocked).
        GridPos shotPos = findShotPosition(self, arena, playerPos, threats, range);
        if (shotPos != null) {
            List<GridPos> path = Pathfinding.findPath(arena, myPos, shotPos, self.getMoveSpeed(), self);
            if (!path.isEmpty()) {
                GridPos endPos = path.get(path.size() - 1);
                if (hasClearShot(self, arena, endPos, playerPos, range)) {
                    return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                }
                return new EnemyAction.Move(path);
            }
        }

        return AIUtils.seekOrWander(self, arena, playerPos);
    }

    /**
     * A pillager may only fire along a clear 4-directional straight line. The target must
     * share {@code from}'s row or column (dx==0 or dz==0 - no diagonals), be within
     * {@code range}, and have no wall/obstacle or other entity on the tiles strictly
     * between the two. Hazard tiles (pits, water, lava) do NOT block - a crossbow bolt
     * flies over them. The shooter's own tile is ignored so a candidate firing tile it is
     * about to move to isn't blocked by the tile it is vacating.
     */
    private static boolean hasClearShot(CombatEntity self, GridArena arena,
                                        GridPos from, GridPos to, int range) {
        int dx = to.x() - from.x();
        int dz = to.z() - from.z();
        if (dx != 0 && dz != 0) return false;      // not a row/column straight line
        int dist = Math.abs(dx) + Math.abs(dz);
        if (dist == 0 || dist > range) return false;
        int sx = Integer.signum(dx);
        int sz = Integer.signum(dz);
        for (int i = 1; i < dist; i++) {           // tiles strictly between from and to
            GridPos p = new GridPos(from.x() + sx * i, from.z() + sz * i);
            if (!arena.isInBounds(p)) return false;
            var tile = arena.getTile(p);
            if (tile != null && tile.getType() == TileType.OBSTACLE) return false; // wall/pillar blocks
            CombatEntity occ = arena.getOccupant(p);
            if (occ != null && occ != self) return false;                          // entity blocks the lane
        }
        return true;
    }

    private GridPos findShotPosition(CombatEntity self, GridArena arena, GridPos playerPos,
                                     List<GridPos> threats, int range) {
        GridPos myPos = self.getGridPos();
        GridPos best = null;
        int bestScore = Integer.MIN_VALUE;

        for (GridPos candidate : Pathfinding.getReachableTiles(
                arena, myPos, self.getMoveSpeed(), self)) {
            if (candidate.equals(myPos)) continue;
            // Only tiles that yield a clear 4-dir straight-line shot count as firing spots.
            if (!hasClearShot(self, arena, candidate, playerPos, range)) continue;

            int distToPlayer = candidate.manhattanDistance(playerPos);
            // Prefer firing from near max range, well clear of every threat
            int score = distToPlayer >= range - 1 ? 20 : distToPlayer * 3;
            if (AIUtils.minThreatDistance(candidate, threats) <= 1) score -= 15;
            if (AIUtils.isHazardTile(arena, candidate)) score -= 25;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }
}
