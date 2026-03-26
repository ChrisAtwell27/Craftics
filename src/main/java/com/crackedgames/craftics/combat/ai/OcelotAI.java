package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Ocelot AI: Lightning-fast hit-and-run assassin.
 * - AMBUSH: charges in at speed 4, attacks, then immediately repositions
 * - EVASION: if damaged, always flees to maximum distance before attacking again
 * - Low HP but nearly impossible to pin down
 * - Targets the flank: prefers approaching from sides/behind
 */
public class OcelotAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // EVASION: if damaged, flee to safety first
        if (self.wasDamagedSinceLastTurn()) {
            GridPos fleeTarget = AIUtils.getFleeTarget(arena, myPos, playerPos, self.getMoveSpeed());
            if (fleeTarget != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, fleeTarget, self.getMoveSpeed(), self);
                if (!path.isEmpty()) {
                    return new EnemyAction.Flee(path);
                }
            }
        }

        // Adjacent — strike
        if (dist == 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        // AMBUSH: full speed charge + attack
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
}
