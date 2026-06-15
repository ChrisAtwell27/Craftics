package com.crackedgames.craftics.combat.ai.ally;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Tank ally AI - a sturdy body-blocker (iron golem, turtle, goat). Charges the
 * enemy closest to the player to soak hits meant for them, and never flees no
 * matter how low its HP runs. When the threat is too far to strike this turn,
 * it INTERPOSES instead of blindly walking at it: it plants itself on the line
 * between the player and the threat, so the hit meant for the player lands on
 * armor plate.
 *
 * @since 0.3.0
 */
public class TankAllyAI implements AllyAI {

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, List<CombatEntity> combatants) {
        CombatEntity threat = AllyTargeting.nearestEnemyToPlayer(arena, combatants);
        if (threat == null) return new EnemyAction.Idle();

        GridPos pos = self.getGridPos();
        int reach = self.getMoveSpeed() + Math.max(1, self.getRange());

        // Close enough to hit (or get hit) this turn - fight.
        if (AllyTargeting.distanceToTarget(threat, arena, pos) <= reach) {
            return AllyTargeting.advance(self, arena, threat);
        }

        // Threat is far out - take up a blocking position on the player-threat
        // line rather than chasing across the arena and leaving the player open.
        GridPos playerPos = arena.getPlayerGridPos();
        GridPos threatAnchor = AllyTargeting.nearestTileOnTarget(threat, arena, playerPos);
        GridPos block = null;
        int bestScore = Integer.MIN_VALUE;
        for (GridPos candidate : Pathfinding.getReachableTiles(
                arena, pos, self.getMoveSpeed(), self.getSize(), self)) {
            int dPlayer = candidate.manhattanDistance(playerPos);
            if (dPlayer > 2) continue; // stay with the player
            // On the line = the two leg distances sum to the direct distance.
            int detour = candidate.manhattanDistance(threatAnchor) + dPlayer
                - playerPos.manhattanDistance(threatAnchor);
            int score = -detour * 10 - dPlayer;
            if (score > bestScore) {
                bestScore = score;
                block = candidate;
            }
        }
        if (block != null && !block.equals(pos)) {
            List<GridPos> path = AllyTargeting.pathTo(self, arena, block);
            if (path != null && !path.isEmpty()) return new EnemyAction.Move(path);
        }
        // Nowhere to stand guard - fall back to the straight charge.
        return AllyTargeting.advance(self, arena, threat);
    }
}
