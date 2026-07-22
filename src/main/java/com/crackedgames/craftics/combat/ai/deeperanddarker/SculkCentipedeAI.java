package com.crackedgames.craftics.combat.ai.deeperanddarker;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Deeper-and-Darker Sculk Centipede (Deeplands).
 *
 * <p>A speed-2 hit-and-run skirmisher: it darts in, bites (applying Poison via
 * its {@code jungle} theme tag in {@link com.crackedgames.craftics.combat.MobThemeTags}),
 * then peels back out of reach on the same turn so the player can't easily
 * retaliate. When it can't reach the player it just closes distance.
 */
public class SculkCentipedeAI implements EnemyAI {

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int speed = self.getMoveSpeed();

        // Already adjacent: bite, then skitter back to a tile out of the player's
        // melee reach using whatever movement is left this turn.
        if (self.minDistanceTo(playerPos) == 1) {
            GridPos retreat = findRetreatTile(arena, myPos, playerPos, speed);
            if (retreat != null && !retreat.equals(myPos)) {
                List<GridPos> back = Pathfinding.findPath(arena, myPos, retreat, speed, self);
                if (!back.isEmpty()) {
                    return new EnemyAction.MoveAttackMove(List.of(), self.getAttackPower(), back);
                }
            }
            return new EnemyAction.Attack(self.getAttackPower());
        }

        // Dash in to an adjacent tile and bite; if we reach, peel back out.
        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, speed);
        if (target != null) {
            List<GridPos> approach = Pathfinding.findPath(arena, myPos, target, speed, self);
            if (!approach.isEmpty()
                    && approach.get(approach.size() - 1).manhattanDistance(playerPos) == 1) {
                // Spend the approach reaching the player; a fresh retreat next turn.
                return new EnemyAction.MoveAndAttack(approach, self.getAttackPower());
            }
            if (!approach.isEmpty()) return new EnemyAction.Move(approach);
        }

        return AIUtils.seekOrWander(self, arena, playerPos);
    }

    /** A reachable tile at Chebyshev distance >= 2 from the player, farthest from them. */
    private GridPos findRetreatTile(GridArena arena, GridPos myPos, GridPos playerPos, int speed) {
        GridPos best = null;
        int bestDist = -1;
        for (int dx = -speed; dx <= speed; dx++) {
            for (int dz = -speed; dz <= speed; dz++) {
                GridPos p = new GridPos(myPos.x() + dx, myPos.z() + dz);
                if (!arena.isInBounds(p) || arena.isOccupied(p)) continue;
                var tile = arena.getTile(p);
                if (tile == null || !tile.isWalkable()) continue;
                int cheb = Math.max(Math.abs(p.x() - playerPos.x()), Math.abs(p.z() - playerPos.z()));
                if (cheb < 2) continue; // still in melee reach - not a real retreat
                if (cheb > bestDist) {
                    bestDist = cheb;
                    best = p;
                }
            }
        }
        return best;
    }
}
