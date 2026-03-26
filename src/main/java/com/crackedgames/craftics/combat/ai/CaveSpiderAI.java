package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Cave Spider AI: Fast venomous attacker — small, aggressive, poisonous.
 * - POUNCE: like SpiderAI, leaps up to 2 tiles to attack (smaller than regular spider)
 * - VENOMOUS BITE: melee attack applies poison effect
 * - HIT AND RUN: after attacking, tries to scurry away 1-2 tiles
 * - Speed 3 makes them zippy flankers that are hard to pin down
 */
public class CaveSpiderAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Adjacent — venomous bite
        if (dist == 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        // POUNCE: leap up to 2 tiles to land adjacent
        if (dist <= 2) {
            GridPos pounceTarget = findPounceTarget(arena, myPos, playerPos, 2);
            if (pounceTarget != null) {
                return new EnemyAction.Pounce(pounceTarget, self.getAttackPower());
            }
        }

        // Rush toward player — move + attack if possible
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

    private GridPos findPounceTarget(GridArena arena, GridPos from, GridPos playerPos, int pounceRange) {
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

            int pDist = from.manhattanDistance(landing);
            if (pDist <= pounceRange && pDist > 0 && pDist < bestDist) {
                bestDist = pDist;
                best = landing;
            }
        }
        return best;
    }
}
