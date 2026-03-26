package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Breeze AI: Trial Chamber signature mob — wind-based ranged attacker.
 * - WIND CHARGE: ranged attack at range 3, deals damage + knockback concept
 * - REPOSITION: after attacking, tries to teleport/dash to a new vantage point
 * - EVASIVE: if player gets adjacent, dashes away 2-3 tiles then shoots
 * - Never stays still — constantly repositioning for optimal angles
 */
public class BreezeAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        int range = self.getRange();

        // EVASIVE: if player is adjacent or very close, dash away then shoot
        if (dist <= 1) {
            GridPos dashTarget = findDashTarget(arena, myPos, playerPos, 3);
            if (dashTarget != null) {
                List<GridPos> path = List.of(dashTarget); // instant dash (like teleport)
                if (dashTarget.manhattanDistance(playerPos) <= range) {
                    return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                }
                return new EnemyAction.Move(path);
            }
            // Cornered — melee attack
            return new EnemyAction.Attack(self.getAttackPower());
        }

        // In range with LOS — wind charge attack
        if (dist <= range && AIUtils.hasCardinalLOS(arena, myPos, playerPos, range)) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "wind_charge");
        }

        // In range but no LOS — reposition to get a clear shot
        if (dist <= range) {
            GridPos shotPos = findShotPosition(self, arena, playerPos);
            if (shotPos != null && !shotPos.equals(myPos)) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, shotPos, self.getMoveSpeed(), self);
                if (!path.isEmpty()) {
                    GridPos endPos = path.get(path.size() - 1);
                    if (endPos.manhattanDistance(playerPos) <= range
                            && AIUtils.hasCardinalLOS(arena, endPos, playerPos, range)) {
                        return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                    }
                    return new EnemyAction.Move(path);
                }
            }
        }

        // Out of range — move to a good shooting position
        GridPos shotPos = findShotPosition(self, arena, playerPos);
        if (shotPos != null) {
            List<GridPos> path = Pathfinding.findPath(arena, myPos, shotPos, self.getMoveSpeed(), self);
            if (!path.isEmpty()) {
                GridPos endPos = path.get(path.size() - 1);
                if (endPos.manhattanDistance(playerPos) <= range
                        && AIUtils.hasCardinalLOS(arena, endPos, playerPos, range)) {
                    return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                }
                return new EnemyAction.Move(path);
            }
        }

        return AIUtils.seekOrWander(self, arena, playerPos);
    }

    /** Find a tile to dash to that's far from the player but still in range. */
    private static final int BREEZE_RANGE = 3;

    private GridPos findDashTarget(GridArena arena, GridPos from, GridPos playerPos, int dashRange) {
        GridPos best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int dx = -dashRange; dx <= dashRange; dx++) {
            for (int dz = -dashRange; dz <= dashRange; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > dashRange || (dx == 0 && dz == 0)) continue;
                GridPos candidate = new GridPos(from.x() + dx, from.z() + dz);
                if (!arena.isInBounds(candidate) || arena.isOccupied(candidate)) continue;
                var tile = arena.getTile(candidate);
                if (tile == null || !tile.isWalkable()) continue;

                int distToPlayer = candidate.manhattanDistance(playerPos);
                // Prefer distance 2-3 from player (in range but not adjacent)
                int score = distToPlayer * 10;
                if (distToPlayer <= 1) score -= 50; // avoid staying adjacent
                if (distToPlayer > 4) score -= 20;  // don't go too far
                if (AIUtils.hasCardinalLOS(arena, candidate, playerPos, BREEZE_RANGE)) score += 15;

                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return best;
    }

    /** Find a position within range that has cardinal LOS to the player. */
    private GridPos findShotPosition(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        GridPos best = null;
        int bestScore = Integer.MIN_VALUE;
        int range = self.getRange();

        for (int dx = -self.getMoveSpeed(); dx <= self.getMoveSpeed(); dx++) {
            for (int dz = -self.getMoveSpeed(); dz <= self.getMoveSpeed(); dz++) {
                if (Math.abs(dx) + Math.abs(dz) > self.getMoveSpeed()) continue;
                GridPos candidate = new GridPos(myPos.x() + dx, myPos.z() + dz);
                if (!arena.isInBounds(candidate) || arena.isOccupied(candidate)) continue;
                var tile = arena.getTile(candidate);
                if (tile == null || !tile.isWalkable()) continue;

                int distToPlayer = candidate.manhattanDistance(playerPos);
                if (distToPlayer > range) continue;

                int score = 0;
                // Prefer having LOS
                if (AIUtils.hasCardinalLOS(arena, candidate, playerPos, range)) score += 30;
                // Prefer distance 2-3 (safe shooting range)
                if (distToPlayer >= 2 && distToPlayer <= 3) score += 20;
                if (distToPlayer <= 1) score -= 30;

                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return best;
    }
}
