package com.crackedgames.craftics.combat.ai.ally;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Support ally AI - a cautious bodyguard (axolotl, frog, villager, sniffer).
 * Sticks close to the player and only strikes enemies that wander into its
 * range; otherwise it repositions to the player's side - preferring the side
 * AWAY from the nearest enemy, so the squishy support isn't the first thing a
 * charge runs into. Retreats when wounded.
 *
 * @since 0.3.0
 */
public class SupportAllyAI implements AllyAI {

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, List<CombatEntity> combatants) {
        GridPos pos = self.getGridPos();

        CombatEntity threat = AllyTargeting.nearestEnemy(pos, combatants);

        // Wounded - fall back toward safety.
        if (threat != null && AllyTargeting.lowHp(self, 0.35f)) {
            EnemyAction flee = AllyTargeting.fleeFrom(self, arena, threat);
            if (flee != null) return flee;
        }

        // An enemy strayed into range - punish it without chasing.
        if (threat != null && threat.minDistanceTo(pos) <= self.getRange()) {
            return new EnemyAction.MoveAndAttackMob(
                List.of(), threat.getEntityId(), self.getAttackPower());
        }

        // Otherwise hold station next to the player, on the sheltered side.
        GridPos playerPos = arena.getPlayerGridPos();
        if (pos.manhattanDistance(playerPos) > 1) {
            GridPos beside = findShelteredSide(self, arena, playerPos, threat);
            if (beside == null) {
                beside = AIUtils.findBestAdjacentTarget(
                    arena, pos, playerPos, self.getMoveSpeed(), self.getSize());
            }
            if (beside != null && !beside.equals(pos)) {
                List<GridPos> path = AllyTargeting.pathTo(self, arena, beside);
                if (path != null && !path.isEmpty()) return new EnemyAction.Move(path);
            }
        }
        return new EnemyAction.Idle();
    }

    /** The player-adjacent tile farthest from the nearest enemy - hide behind the player. */
    private GridPos findShelteredSide(CombatEntity self, GridArena arena,
                                      GridPos playerPos, CombatEntity threat) {
        if (threat == null) return null;
        GridPos threatPos = threat.getGridPos();
        GridPos best = null;
        int bestDist = Integer.MIN_VALUE;
        for (GridPos beside : AIUtils.getAdjacentTiles(arena, playerPos)) {
            if (!AIUtils.canPlaceFootprint(arena, beside, self.getSize())) continue;
            // Only consider tiles we can actually walk to this turn.
            List<GridPos> path = AllyTargeting.pathTo(self, arena, beside);
            if (path == null || path.isEmpty()) continue;
            if (path.size() > self.getMoveSpeed()) continue;
            int d = beside.manhattanDistance(threatPos);
            if (d > bestDist) {
                bestDist = d;
                best = beside;
            }
        }
        return best;
    }
}
