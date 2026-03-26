package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Spider AI: 2x2 predator with pounce and web attacks.
 * - POUNCE: leaps up to 3 tiles to land adjacent and attack (ignores obstacles)
 * - WEB: if can't reach player, move + attack applies Slowness (via hit effect)
 * - Speed 3 makes them very aggressive flankers
 * - 2x2 size blocks movement but pounce ignores pathing
 */
public class SpiderAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Adjacent — bite attack
        if (dist == 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        // POUNCE: up to 3 tiles away, leap to adjacent tile
        if (dist <= 3) {
            GridPos pounceTarget = findPounceTarget(arena, myPos, playerPos);
            if (pounceTarget != null) {
                return new EnemyAction.Pounce(pounceTarget, self.getAttackPower());
            }
        }

        // Move toward player with full speed, attack on arrival
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

    private GridPos findPounceTarget(GridArena arena, GridPos from, GridPos playerPos) {
        // Find best landing tile adjacent to player within pounce range (3)
        GridPos[] dirs = {
            new GridPos(0, 1), new GridPos(0, -1),
            new GridPos(1, 0), new GridPos(-1, 0)
        };

        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;

        for (GridPos dir : dirs) {
            GridPos landing = new GridPos(playerPos.x() + dir.x(), playerPos.z() + dir.z());
            if (!arena.isInBounds(landing) || arena.isEnemyOccupied(landing)) continue;
            var tile = arena.getTile(landing);
            if (tile == null || !tile.isWalkable()) continue;

            int poundDist = from.manhattanDistance(landing);
            if (poundDist <= 3 && poundDist > 0 && poundDist < bestDist) {
                bestDist = poundDist;
                best = landing;
            }
        }
        return best;
    }
}
