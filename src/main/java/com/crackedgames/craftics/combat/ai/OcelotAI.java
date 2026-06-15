package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Ocelot AI: Lightning-fast hit-and-run assassin.
 * - AMBUSH: charges in at speed 4, attacks, then spends leftover movement
 *   springing back out of reach (a true hit-and-run, not just a hit)
 * - EVASION: if damaged, always flees to maximum distance before attacking again
 * - Low HP but nearly impossible to pin down
 */
public class OcelotAI implements EnemyAI {
    /** Ocelots only retaliate the turn after being hit - otherwise not a threat. */
    @Override
    public boolean isHostileThreat(CombatEntity self, GridArena arena, GridPos playerPos) {
        return self.wasDamagedSinceLastTurn();
    }

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // EVASION: if damaged, flee to safety first (path-validated - finds the
        // around-the-corner escape, not just the straight line)
        if (self.wasDamagedSinceLastTurn()) {
            EnemyAction flee = AIUtils.fleeReachable(self, arena, playerPos, self.getMoveSpeed());
            if (flee != null) return flee;
        }

        // Adjacent - strike and spring away
        if (dist == 1) {
            EnemyAction combo = AIUtils.hitAndRun(self, arena, playerPos, List.of(), self.getAttackPower());
            if (combo != null) return combo;
            return new EnemyAction.Attack(self.getAttackPower());
        }

        // AMBUSH: full speed charge + attack, then retreat with anything left over
        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, self.getMoveSpeed());
        if (target == null) target = playerPos;

        List<GridPos> path = Pathfinding.findPath(arena, myPos, target, self.getMoveSpeed(), self);
        if (path.isEmpty()) return AIUtils.seekOrWander(self, arena, playerPos);

        GridPos endPos = path.get(path.size() - 1);
        if (endPos.manhattanDistance(playerPos) == 1) {
            EnemyAction combo = AIUtils.hitAndRun(self, arena, playerPos, path, self.getAttackPower());
            if (combo != null) return combo;
            return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
        }

        return new EnemyAction.Move(path);
    }
}
