package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Silverfish AI: Swarmer — fast, weak, flanks aggressively.
 * - SWARM: always tries to approach from the opposite side of other enemies
 * - SPEED 3: very fast, can cross the arena quickly
 * - WEAK BITE: low damage but attacks immediately on reaching player
 * - FLANKING: prefers to approach from behind/sides rather than head-on
 */
public class SilverfishAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Adjacent — attack immediately (swarm behavior)
        if (dist == 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        // Try to flank — approach from opposite side of other enemies
        GridPos flankTarget = findFlankPosition(arena, myPos, playerPos);
        if (flankTarget != null) {
            List<GridPos> path = Pathfinding.findPath(arena, myPos, flankTarget, self.getMoveSpeed(), self);
            if (!path.isEmpty()) {
                GridPos endPos = path.get(path.size() - 1);
                if (endPos.manhattanDistance(playerPos) == 1) {
                    return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                }
                return new EnemyAction.Move(path);
            }
        }

        // Fallback: rush directly at the player
        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, self.getMoveSpeed());
        if (target == null) target = playerPos;

        List<GridPos> path = Pathfinding.findPath(arena, myPos, target, self.getMoveSpeed(), self);
        if (path.isEmpty()) return AIUtils.seekOrWander(self, arena, playerPos);

        GridPos endPos = path.get(path.size() - 1);
        if (endPos.manhattanDistance(playerPos) == 1) {
            return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
        }
        return new EnemyAction.Move(path);
    }

    /** Find an adjacent-to-player tile that's on the opposite side from our current position. */
    private GridPos findFlankPosition(GridArena arena, GridPos myPos, GridPos playerPos) {
        GridPos[] dirs = {
            new GridPos(0, 1), new GridPos(0, -1),
            new GridPos(1, 0), new GridPos(-1, 0)
        };

        GridPos best = null;
        int bestScore = Integer.MIN_VALUE;

        // Direction from player to us (we want to go to the OPPOSITE side)
        int toUsX = myPos.x() - playerPos.x();
        int toUsZ = myPos.z() - playerPos.z();

        for (GridPos dir : dirs) {
            GridPos landing = new GridPos(playerPos.x() + dir.x(), playerPos.z() + dir.z());
            if (!arena.isInBounds(landing) || arena.isOccupied(landing)) continue;
            var tile = arena.getTile(landing);
            if (tile == null || !tile.isWalkable()) continue;

            // Score: prefer tiles on the OPPOSITE side from where we are
            // Negative dot product means opposite direction
            int score = -(dir.x() * toUsX + dir.z() * toUsZ) * 10;
            // Also prefer tiles we can actually reach
            int pathDist = myPos.manhattanDistance(landing);
            score -= pathDist; // closer is slightly better

            if (score > bestScore) {
                bestScore = score;
                best = landing;
            }
        }
        return best;
    }
}
