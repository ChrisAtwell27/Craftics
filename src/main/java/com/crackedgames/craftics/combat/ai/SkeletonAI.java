package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Skeleton AI: Tactical archer - kites the player.
 * - KITE: backs up when ANY threat (player or ally pet) is within 2 tiles,
 *   while trying to keep LOS on its target - a wolf gnawing on its ankles
 *   triggers the retreat just like the player would
 * - REPOSITION: moves to get cardinal LOS, then shoots in same turn
 * - Prefers to maintain distance 3+ for safe shooting, won't end its move
 *   on a hazard tile
 * - Speed 2 allows meaningful repositioning
 */
public class SkeletonAI implements EnemyAI {

    private static final int KITE_THRESHOLD = 2;

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int range = self.getRange();
        int speed = self.getMoveSpeed();

        // Threats include ally pets, so the skeleton kites away from whatever
        // is actually about to maul it, not just the player.
        List<GridPos> threats = AIUtils.threatPositions(arena, playerPos);

        // KITE: if anything is too close (within 2 tiles), back up - prioritize distance
        if (AIUtils.minThreatDistance(myPos, threats) <= KITE_THRESHOLD) {
            GridPos retreatPos = findRetreatPosition(self, arena, playerPos, threats);
            if (retreatPos != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, retreatPos, speed, self);
                if (!path.isEmpty()) {
                    GridPos endPos = path.get(path.size() - 1);
                    if (AIUtils.hasCardinalLOS(arena, endPos, playerPos, range)) {
                        return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                    }
                    // Couldn't line up a shot, but still back up - survival first
                    return new EnemyAction.Move(path);
                }
            }
            // Can't retreat at all - fire anyway. A cornered archer shoots rather
            // than switching to a melee swing, even without a clean cardinal line.
            return new EnemyAction.RangedAttack(self.getAttackPower(), "arrow");
        }

        int dist = self.minDistanceTo(playerPos);

        // In range with LOS - shoot from current position
        if (AIUtils.hasCardinalLOS(arena, myPos, playerPos, range)) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "arrow");
        }

        // REPOSITION: move to get LOS, shoot in same turn
        GridPos shotPos = findBestShotPosition(self, arena, playerPos, threats);
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

        // Last resort: if the player is within range, fire even without a clean
        // cardinal line rather than wandering uselessly - a skeleton with no shot
        // lined up in a cluttered arena should still threaten the player.
        if (dist <= range) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "arrow");
        }

        return AIUtils.seekOrWander(self, arena, playerPos);
    }

    /**
     * Find the best retreat tile: prioritize maximizing distance from every
     * threat (player AND ally pets), then secondarily prefer tiles that keep
     * cardinal LOS for a shot. Avoids ending the retreat on a hazard tile.
     */
    private GridPos findRetreatPosition(CombatEntity self, GridArena arena, GridPos playerPos,
                                        List<GridPos> threats) {
        GridPos myPos = self.getGridPos();
        GridPos best = null;
        int bestScore = Integer.MIN_VALUE;
        int range = self.getRange();
        int currentMin = AIUtils.minThreatDistance(myPos, threats);

        for (GridPos candidate : Pathfinding.getReachableTiles(
                arena, myPos, self.getMoveSpeed(), self)) {
            if (candidate.equals(myPos)) continue;

            int minThreatDist = AIUtils.minThreatDistance(candidate, threats);
            // Must actually increase distance - don't "retreat" closer
            if (minThreatDist <= currentMin) continue;

            // Primary: maximize distance from the nearest threat (heavily weighted)
            int score = minThreatDist * 10;
            // Secondary: bonus for having LOS to shoot after retreating
            if (AIUtils.hasCardinalLOS(arena, candidate, playerPos, range)) {
                score += 15;
            }
            // Small penalty for being on the same axis (predictable)
            if (candidate.x() == playerPos.x() || candidate.z() == playerPos.z()) {
                score -= 2;
            }
            // Don't back into lava to dodge a sword
            if (AIUtils.isHazardTile(arena, candidate)) {
                score -= 25;
            }

            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private GridPos findBestShotPosition(CombatEntity self, GridArena arena, GridPos playerPos,
                                         List<GridPos> threats) {
        GridPos myPos = self.getGridPos();
        GridPos best = null;
        int bestScore = Integer.MIN_VALUE;
        int range = self.getRange();

        for (GridPos candidate : Pathfinding.getReachableTiles(
                arena, myPos, self.getMoveSpeed(), self)) {
            if (candidate.equals(myPos)) continue;
            if (!AIUtils.hasCardinalLOS(arena, candidate, playerPos, range)) continue;

            int minThreatDist = AIUtils.minThreatDistance(candidate, threats);
            // Prefer distance 3+ (safe range), heavily penalize close positions
            int score = 20;
            score += Math.min(minThreatDist, 4) * 5;
            if (minThreatDist <= KITE_THRESHOLD) score -= 15;
            if (AIUtils.isHazardTile(arena, candidate)) score -= 25;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }
}
