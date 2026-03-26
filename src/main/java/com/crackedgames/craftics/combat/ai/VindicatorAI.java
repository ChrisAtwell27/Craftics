package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Vindicator AI: Axe-wielding berserker.
 * - RAGE: becomes permanently enraged when damaged, gaining +50% attack
 * - CHARGE: full speed rush + attack in same turn
 * - Speed 3 makes them a devastating melee threat
 */
public class VindicatorAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Check if damaged — trigger rage
        if (self.wasDamagedSinceLastTurn() && !self.isEnraged()) {
            self.setEnraged(true);
        }

        // Enraged vindicators deal 50% more damage
        int damage = self.isEnraged()
            ? (int)(self.getAttackPower() * 1.5)
            : self.getAttackPower();

        if (dist == 1) {
            return new EnemyAction.Attack(damage);
        }

        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, self.getMoveSpeed());
        if (target == null) target = playerPos;

        List<GridPos> path = Pathfinding.findPath(arena, myPos, target, self.getMoveSpeed(), self);
        if (path.isEmpty()) return AIUtils.seekOrWander(self, arena, playerPos);

        GridPos endPos = path.get(path.size() - 1);
        if (endPos.manhattanDistance(playerPos) == 1) {
            return new EnemyAction.MoveAndAttack(path, damage);
        }
        return new EnemyAction.Move(path);
    }
}
