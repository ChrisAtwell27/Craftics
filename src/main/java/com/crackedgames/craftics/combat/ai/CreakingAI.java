package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Creaking AI: A wooden guardian linked to a Creaking Heart.
 * The Creaking itself is invulnerable — it can only be killed by destroying
 * its linked Creaking Heart entity. Moves toward the player and attacks.
 */
public class CreakingAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        // Gaze freeze: while the player is looking at the Creaking it cannot
        // move OR attack. CombatManager flips the frozen flag based on the
        // player's view-cone; honoring it here is what makes the gaze actually
        // matter (short-circuiting before the dist==1 attack branch below).
        if (self.isFrozen()) {
            return new EnemyAction.Idle();
        }

        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Adjacent — attack
        if (dist == 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        // Move toward player
        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, self.getMoveSpeed());
        if (target == null) target = playerPos;

        List<GridPos> path = Pathfinding.findPath(arena, myPos, target, self.getMoveSpeed(), self);
        if (path.isEmpty()) {
            return AIUtils.seekOrWander(self, arena, playerPos);
        }

        GridPos endPos = path.get(path.size() - 1);
        if (endPos.manhattanDistance(playerPos) == 1) {
            return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
        }

        return new EnemyAction.Move(path);
    }
}
