package com.crackedgames.craftics.combat.ai.ally;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Support ally AI — a cautious bodyguard (axolotl, frog, villager, sniffer).
 * Sticks close to the player and only strikes enemies that wander into its
 * range; otherwise it repositions to the player's side. Retreats when wounded.
 *
 * @since 0.3.0
 */
public class SupportAllyAI implements AllyAI {

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, List<CombatEntity> combatants) {
        GridPos pos = self.getGridPos();
        int speed = self.getMoveSpeed();

        CombatEntity threat = AllyTargeting.nearestEnemy(pos, combatants);

        // Wounded — fall back toward safety.
        if (threat != null && AllyTargeting.lowHp(self, 0.35f)) {
            EnemyAction flee = AllyTargeting.fleeFrom(self, arena, threat);
            if (flee != null) return flee;
        }

        // An enemy strayed into range — punish it without chasing.
        if (threat != null && threat.minDistanceTo(pos) <= self.getRange()) {
            return new EnemyAction.MoveAndAttackMob(
                List.of(), threat.getEntityId(), self.getAttackPower());
        }

        // Otherwise hold station next to the player.
        GridPos playerPos = arena.getPlayerGridPos();
        if (pos.manhattanDistance(playerPos) > 1) {
            GridPos beside = AIUtils.findBestAdjacentTarget(arena, pos, playerPos, speed);
            if (beside != null && !beside.equals(pos)) {
                List<GridPos> path = Pathfinding.findPath(arena, pos, beside, speed, false);
                if (path != null && !path.isEmpty()) return new EnemyAction.Move(path);
            }
        }
        return new EnemyAction.Idle();
    }
}
