package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Vindicator AI: Rook-movement axe berserker.
 *
 * Movement: Can only move in straight lines (cardinal directions) like a chess rook.
 * Each turn has two parts:
 * 1. DASH: Charges in a straight line at infinite speed until hitting a wall/obstacle/edge.
 *    If the player is in the dash path, the vindicator stops adjacent and attacks.
 * 2. ADJUST: After the dash, makes a small adjustment move (speed 2) to try to line up
 *    another dash toward the player for next turn.
 *
 * Rage: Becomes permanently enraged when damaged (+50% attack).
 * Also used by Piglin Brutes (registered to same AI in AIRegistry).
 */
public class VindicatorAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();

        // Rage trigger
        if (self.wasDamagedSinceLastTurn() && !self.isEnraged()) {
            self.setEnraged(true);
        }

        int damage = self.isEnraged()
            ? (int)(self.getAttackPower() * 1.5)
            : self.getAttackPower();

        // Adjacent — just attack
        if (self.minDistanceTo(playerPos) == 1) {
            return new EnemyAction.Attack(damage);
        }

        // Try all 4 cardinal directions for a dash
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        // First priority: find a dash that hits the player
        for (int[] dir : directions) {
            List<GridPos> dashPath = buildDashPath(arena, myPos, dir[0], dir[1]);
            if (dashPath.isEmpty()) continue;

            // Check if the player is in this dash line
            int playerIndex = -1;
            for (int i = 0; i < dashPath.size(); i++) {
                if (dashPath.get(i).equals(playerPos)) {
                    playerIndex = i;
                    break;
                }
            }

            if (playerIndex >= 0) {
                // Player is in the path — dash up to adjacent tile and attack
                List<GridPos> pathToPlayer = dashPath.subList(0, playerIndex);
                if (pathToPlayer.isEmpty()) {
                    // Player is directly adjacent in this direction
                    return new EnemyAction.Attack(damage);
                }
                return new EnemyAction.MoveAndAttack(new ArrayList<>(pathToPlayer), damage);
            }
        }

        // No dash hits the player — pick the best dash + adjustment combo
        // Best = whichever dash end position allows the shortest adjustment to line up with the player
        GridPos bestDashEnd = null;
        List<GridPos> bestDashPath = null;
        int bestAdjustDist = Integer.MAX_VALUE;

        for (int[] dir : directions) {
            List<GridPos> dashPath = buildDashPath(arena, myPos, dir[0], dir[1]);
            if (dashPath.isEmpty()) continue;

            GridPos dashEnd = dashPath.get(dashPath.size() - 1);

            // After dashing to dashEnd, how far is an adjustment move to line up with the player?
            // "Lined up" = same row or same column as the player
            int adjustToRow = Math.abs(dashEnd.z() - playerPos.z()); // adjust X to match
            int adjustToCol = Math.abs(dashEnd.x() - playerPos.x()); // adjust Z to match
            int minAdjust = Math.min(adjustToRow, adjustToCol);

            if (minAdjust < bestAdjustDist) {
                bestAdjustDist = minAdjust;
                bestDashEnd = dashEnd;
                bestDashPath = dashPath;
            }
        }

        if (bestDashPath != null && bestDashEnd != null) {
            // Do the dash, then try a short adjustment move
            if (bestAdjustDist <= 2 && bestAdjustDist > 0) {
                // Calculate adjustment target — move to get on same row or column as player
                GridPos adjustTarget = null;
                if (Math.abs(bestDashEnd.z() - playerPos.z()) <= 2) {
                    // Adjust X to match player's column
                    adjustTarget = new GridPos(playerPos.x(), bestDashEnd.z());
                } else if (Math.abs(bestDashEnd.x() - playerPos.x()) <= 2) {
                    // Adjust Z to match player's row
                    adjustTarget = new GridPos(bestDashEnd.x(), playerPos.z());
                }

                if (adjustTarget != null && arena.isInBounds(adjustTarget)
                        && !arena.isEnemyOccupied(adjustTarget)) {
                    // Build the adjustment path (short walk, speed 2)
                    List<GridPos> adjustPath = buildStraightPath(arena, bestDashEnd, adjustTarget);
                    if (!adjustPath.isEmpty()) {
                        // Combine dash + adjustment into one turn
                        List<GridPos> fullPath = new ArrayList<>(bestDashPath);
                        fullPath.addAll(adjustPath);
                        return new EnemyAction.Move(fullPath);
                    }
                }
            }
            // Just dash without adjustment
            return new EnemyAction.Move(bestDashPath);
        }

        // Fallback — can't dash anywhere useful
        return AIUtils.seekOrWander(self, arena, playerPos);
    }

    /**
     * Build a straight-line dash path from a position in a direction.
     * Goes until hitting a wall, obstacle, or arena edge. Does NOT include occupied tiles.
     */
    private List<GridPos> buildDashPath(GridArena arena, GridPos start, int dx, int dz) {
        List<GridPos> path = new ArrayList<>();
        GridPos current = start;
        while (true) {
            GridPos next = new GridPos(current.x() + dx, current.z() + dz);
            if (!arena.isInBounds(next)) break;
            var tile = arena.getTile(next);
            if (tile == null || !tile.isWalkable()) break;
            if (arena.isEnemyOccupied(next)) break;
            // Don't stop on player tile — we pass through (or stop adjacent for attack)
            path.add(next);
            current = next;
        }
        return path;
    }

    /**
     * Build a short straight-line path between two points (for adjustment moves).
     * Only works for cardinal movement within 2 tiles.
     */
    private List<GridPos> buildStraightPath(GridArena arena, GridPos from, GridPos to) {
        List<GridPos> path = new ArrayList<>();
        int dx = Integer.signum(to.x() - from.x());
        int dz = Integer.signum(to.z() - from.z());
        // Only one axis should be non-zero for cardinal movement
        if (dx != 0 && dz != 0) return path; // diagonal, not valid

        GridPos current = from;
        int steps = Math.max(Math.abs(to.x() - from.x()), Math.abs(to.z() - from.z()));
        for (int i = 0; i < Math.min(steps, 2); i++) { // max 2 adjustment tiles
            GridPos next = new GridPos(current.x() + dx, current.z() + dz);
            if (!arena.isInBounds(next)) break;
            var tile = arena.getTile(next);
            if (tile == null || !tile.isWalkable()) break;
            if (arena.isEnemyOccupied(next)) break;
            path.add(next);
            current = next;
        }
        return path;
    }
}
