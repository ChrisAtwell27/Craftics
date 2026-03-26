package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Skeleton AI: Tactical archer — kites the player.
 * - KITE: if player is within 2 tiles, backs up (priority) while trying to keep LOS
 * - REPOSITION: moves to get cardinal LOS, then shoots in same turn
 * - Prefers to maintain distance 3+ for safe shooting
 * - Speed 2 allows meaningful repositioning
 */
public class SkeletonAI implements EnemyAI {

    private static final int KITE_THRESHOLD = 2;

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        int range = self.getRange();
        int speed = self.getMoveSpeed();

        // KITE: if player is too close (within 2 tiles), back up — prioritize distance
        if (dist <= KITE_THRESHOLD) {
            GridPos retreatPos = findRetreatPosition(self, arena, playerPos);
            if (retreatPos != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, retreatPos, speed, self);
                if (!path.isEmpty()) {
                    GridPos endPos = path.get(path.size() - 1);
                    if (AIUtils.hasCardinalLOS(arena, endPos, playerPos, range)) {
                        return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                    }
                    // Couldn't line up a shot, but still back up — survival first
                    return new EnemyAction.Move(path);
                }
            }
            // Can't retreat at all — shoot at close range if possible
            if (AIUtils.hasCardinalLOS(arena, myPos, playerPos, range)) {
                return new EnemyAction.RangedAttack(self.getAttackPower(), "arrow");
            }
            return new EnemyAction.Attack(self.getAttackPower());
        }

        // In range with LOS — shoot from current position
        if (AIUtils.hasCardinalLOS(arena, myPos, playerPos, range)) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "arrow");
        }

        // REPOSITION: move to get LOS, shoot in same turn
        GridPos shotPos = findBestShotPosition(self, arena, playerPos);
        if (shotPos != null) {
            List<GridPos> path = Pathfinding.findPath(arena, myPos, shotPos, speed, self);
            if (!path.isEmpty()) {
                GridPos endPos = path.get(path.size() - 1);
                if (AIUtils.hasCardinalLOS(arena, endPos, playerPos, range)) {
                    return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                }
                return new EnemyAction.Move(path);
            }
        }

        return AIUtils.seekOrWander(self, arena, playerPos);
    }

    /**
     * Find the best retreat tile: prioritize maximizing distance from player,
     * then secondarily prefer tiles that maintain cardinal LOS for a shot.
     */
    private GridPos findRetreatPosition(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        GridPos best = null;
        int bestScore = Integer.MIN_VALUE;
        int range = self.getRange();
        int speed = self.getMoveSpeed();

        for (int dx = -speed; dx <= speed; dx++) {
            for (int dz = -speed; dz <= speed; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > speed) continue;
                if (dx == 0 && dz == 0) continue;
                GridPos candidate = new GridPos(myPos.x() + dx, myPos.z() + dz);
                if (!arena.isInBounds(candidate) || arena.isOccupied(candidate)) continue;
                var tile = arena.getTile(candidate);
                if (tile == null || !tile.isWalkable()) continue;

                int distToPlayer = candidate.manhattanDistance(playerPos);
                // Must actually increase distance — don't "retreat" closer
                if (distToPlayer <= myPos.manhattanDistance(playerPos)) continue;

                // Primary: maximize distance from player (heavily weighted)
                int score = distToPlayer * 10;
                // Secondary: bonus for having LOS to shoot after retreating
                if (AIUtils.hasCardinalLOS(arena, candidate, playerPos, range)) {
                    score += 15;
                }
                // Small penalty for being on the same axis (predictable)
                if (candidate.x() == playerPos.x() || candidate.z() == playerPos.z()) {
                    score -= 2;
                }

                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return best;
    }

    private GridPos findBestShotPosition(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        GridPos best = null;
        int bestScore = Integer.MIN_VALUE;
        int range = self.getRange();
        int speed = self.getMoveSpeed();

        for (int dx = -speed; dx <= speed; dx++) {
            for (int dz = -speed; dz <= speed; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > speed) continue;
                if (dx == 0 && dz == 0) continue;
                GridPos candidate = new GridPos(myPos.x() + dx, myPos.z() + dz);
                if (!arena.isInBounds(candidate) || arena.isOccupied(candidate)) continue;
                var tile = arena.getTile(candidate);
                if (tile == null || !tile.isWalkable()) continue;

                if (!AIUtils.hasCardinalLOS(arena, candidate, playerPos, range)) continue;

                int distToPlayer = candidate.manhattanDistance(playerPos);
                // Prefer distance 3+ (safe range), heavily penalize close positions
                int score = 20;
                score += Math.min(distToPlayer, 4) * 5;
                if (distToPlayer <= KITE_THRESHOLD) score -= 15;
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return best;
    }
}
