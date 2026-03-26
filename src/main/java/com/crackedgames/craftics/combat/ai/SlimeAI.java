package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Slime AI: Bouncy pouncer — hops toward the player in big leaps.
 * - BOUNCE POUNCE: leaps up to 3 tiles to land adjacent and attack
 * - AGGRESSIVE: always moves toward the player, no kiting or fleeing
 * - SLOW TURN: if can't pounce, takes a short hop toward player
 * - Simple but effective in groups — fills the role of a brute charger
 */
public class SlimeAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Adjacent — slam attack
        if (dist == 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        // BOUNCE: pounce up to 3 tiles
        if (dist <= 3) {
            GridPos bounceTarget = findBounceTarget(arena, myPos, playerPos);
            if (bounceTarget != null) {
                return new EnemyAction.Pounce(bounceTarget, self.getAttackPower());
            }
        }

        // Can't pounce — hop toward player (move + attack if close enough)
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

    private GridPos findBounceTarget(GridArena arena, GridPos from, GridPos playerPos) {
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

            int bounceDist = from.manhattanDistance(landing);
            if (bounceDist <= 3 && bounceDist > 0 && bounceDist < bestDist) {
                bestDist = bounceDist;
                best = landing;
            }
        }
        return best;
    }
}
