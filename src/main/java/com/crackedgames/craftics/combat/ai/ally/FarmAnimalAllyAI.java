package com.crackedgames.craftics.combat.ai.ally;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * The shared AI for farm animals (cow, sheep, pig, chicken, rabbit, and the
 * unsaddled mounts). Farm animals are not fighters: they trail the player for
 * safety, kick back only at an enemy right on top of them, and bolt from any
 * nearby threat. This is the one ally archetype deliberately not unique per mob.
 *
 * @since 0.3.0
 */
public class FarmAnimalAllyAI implements AllyAI {

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, List<CombatEntity> combatants) {
        GridPos pos = self.getGridPos();

        CombatEntity threat = AllyTargeting.nearestEnemy(pos, combatants);
        boolean scared = AllyTargeting.lowHp(self, 0.5f);

        if (threat != null) {
            int dist = threat.minDistanceTo(pos);
            // Cornered and not panicking - a desperate kick at the attacker.
            if (dist <= 1 && !scared) {
                return new EnemyAction.MoveAndAttackMob(
                    List.of(), threat.getEntityId(), self.getAttackPower());
            }
            // A threat is close (or we're hurt) - bolt away from it.
            if (scared || dist <= 2) {
                EnemyAction flee = AllyTargeting.fleeFrom(self, arena, threat);
                if (flee != null) return flee;
                // Boxed in with a threat adjacent - kick rather than freeze.
                if (dist <= 1) {
                    return new EnemyAction.MoveAndAttackMob(
                        List.of(), threat.getEntityId(), self.getAttackPower());
                }
            }
        }

        // No danger - trail along behind the player.
        GridPos playerPos = arena.getPlayerGridPos();
        if (pos.manhattanDistance(playerPos) > 1) {
            GridPos beside = AIUtils.findBestAdjacentTarget(
                arena, pos, playerPos, self.getMoveSpeed(), self.getSizeX(), self.getSizeZ());
            if (beside != null && !beside.equals(pos)) {
                List<GridPos> path = AllyTargeting.pathTo(self, arena, beside);
                if (path != null && !path.isEmpty()) return new EnemyAction.Move(path);
            }
        }
        return new EnemyAction.Idle();
    }
}
