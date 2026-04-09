package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
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
 *    Charge damage scales with distance traveled (+1 per tile beyond 2).
 * 2. ADJUST: After the dash, makes a small adjustment move (speed 2, or 3 when enraged)
 *    to try to line up another dash toward the player for next turn.
 *
 * Rage: Becomes permanently enraged when damaged (+50% attack, wider adjustment range).
 * Also used by Piglin Brutes (registered to same AI in AIRegistry).
 */
public class VindicatorAI implements EnemyAI {
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();

        // Rage trigger
        if (self.wasDamagedSinceLastTurn() && !self.isEnraged()) {
            self.setEnraged(true);
        }

        boolean enraged = self.isEnraged();
        int baseDamage = enraged ? (int)(self.getAttackPower() * 1.5) : self.getAttackPower();
        int adjustRange = enraged ? 3 : 2;

        // Adjacent — just attack (enraged gets knockback)
        if (self.minDistanceTo(playerPos) == 1) {
            if (enraged) {
                return new EnemyAction.AttackWithKnockback(baseDamage, 1);
            }
            return new EnemyAction.Attack(baseDamage);
        }

        // Try all 4 cardinal directions for a dash that hits the player
        EnemyAction bestDashAttack = null;
        int bestDashDist = Integer.MAX_VALUE;

        for (int[] dir : DIRECTIONS) {
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
                int dashDist = pathToPlayer.size();
                // Charge bonus: +1 damage per tile traveled beyond 2
                int chargeDamage = baseDamage + Math.max(0, dashDist - 2);

                // Prefer shorter dashes (more reliable positioning)
                if (dashDist < bestDashDist) {
                    bestDashDist = dashDist;
                    if (pathToPlayer.isEmpty()) {
                        bestDashAttack = enraged
                            ? new EnemyAction.AttackWithKnockback(chargeDamage, 1)
                            : new EnemyAction.Attack(chargeDamage);
                    } else {
                        bestDashAttack = enraged
                            ? new EnemyAction.MoveAndAttackWithKnockback(new ArrayList<>(pathToPlayer), chargeDamage, 1)
                            : new EnemyAction.MoveAndAttack(new ArrayList<>(pathToPlayer), chargeDamage);
                    }
                }
            }
        }

        if (bestDashAttack != null) {
            return bestDashAttack;
        }

        // No dash hits the player — pick the best dash + adjustment combo
        // Score each option by how close it gets to being lined up with the player
        GridPos bestDashEnd = null;
        List<GridPos> bestDashPath = null;
        int bestScore = Integer.MAX_VALUE;

        for (int[] dir : DIRECTIONS) {
            List<GridPos> dashPath = buildDashPath(arena, myPos, dir[0], dir[1]);
            if (dashPath.isEmpty()) continue;

            GridPos dashEnd = dashPath.get(dashPath.size() - 1);

            // Check both axes — which is closer to aligning with the player?
            int rowDiff = Math.abs(dashEnd.z() - playerPos.z());
            int colDiff = Math.abs(dashEnd.x() - playerPos.x());
            int minAlign = Math.min(rowDiff, colDiff);

            // Penalize options that are far from the player overall
            int totalDist = dashEnd.manhattanDistance(playerPos);
            int score = minAlign * 10 + totalDist;

            if (score < bestScore) {
                bestScore = score;
                bestDashEnd = dashEnd;
                bestDashPath = dashPath;
            }
        }

        if (bestDashPath != null && bestDashEnd != null) {
            // Try adjustment move to get lined up
            GridPos adjustTarget = findBestAdjustTarget(arena, bestDashEnd, playerPos, adjustRange);

            if (adjustTarget != null) {
                List<GridPos> adjustPath = buildStraightPath(arena, bestDashEnd, adjustTarget, adjustRange);
                if (!adjustPath.isEmpty()) {
                    // Combine dash + adjustment
                    List<GridPos> fullPath = new ArrayList<>(bestDashPath);
                    fullPath.addAll(adjustPath);

                    // Check if after adjusting we're adjacent — attack too
                    GridPos finalPos = fullPath.get(fullPath.size() - 1);
                    if (finalPos.manhattanDistance(playerPos) == 1) {
                        return enraged
                            ? new EnemyAction.MoveAndAttackWithKnockback(fullPath, baseDamage, 1)
                            : new EnemyAction.MoveAndAttack(fullPath, baseDamage);
                    }
                    return new EnemyAction.Move(fullPath);
                }
            }

            // Just dash without adjustment — still better than nothing
            return new EnemyAction.Move(bestDashPath);
        }

        // Fallback — use pathfinding to close distance aggressively
        return AIUtils.seekOrWander(self, arena, playerPos);
    }

    /**
     * Find the best adjustment target to line up with the player.
     * Prefers getting on the same row or column as the player, within adjustRange.
     */
    private GridPos findBestAdjustTarget(GridArena arena, GridPos from, GridPos playerPos, int maxSteps) {
        // Try to get on the same row (z) as the player
        GridPos rowAlign = new GridPos(from.x(), playerPos.z());
        int rowDist = Math.abs(from.z() - playerPos.z());
        // Try to get on the same column (x) as the player
        GridPos colAlign = new GridPos(playerPos.x(), from.z());
        int colDist = Math.abs(from.x() - playerPos.x());

        // Pick whichever alignment is reachable and closer
        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;

        if (rowDist <= maxSteps && rowDist > 0 && arena.isInBounds(rowAlign)
                && !arena.isEnemyOccupied(rowAlign)) {
            best = rowAlign;
            bestDist = rowDist;
        }
        if (colDist <= maxSteps && colDist > 0 && colDist < bestDist
                && arena.isInBounds(colAlign) && !arena.isEnemyOccupied(colAlign)) {
            best = colAlign;
        }

        return best;
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
            path.add(next);
            current = next;
        }
        return path;
    }

    /**
     * Build a short straight-line path between two points (for adjustment moves).
     * Only works for cardinal movement within maxSteps tiles.
     */
    private List<GridPos> buildStraightPath(GridArena arena, GridPos from, GridPos to, int maxSteps) {
        List<GridPos> path = new ArrayList<>();
        int dx = Integer.signum(to.x() - from.x());
        int dz = Integer.signum(to.z() - from.z());
        // Only one axis should be non-zero for cardinal movement
        if (dx != 0 && dz != 0) return path;

        GridPos current = from;
        int steps = Math.max(Math.abs(to.x() - from.x()), Math.abs(to.z() - from.z()));
        for (int i = 0; i < Math.min(steps, maxSteps); i++) {
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
