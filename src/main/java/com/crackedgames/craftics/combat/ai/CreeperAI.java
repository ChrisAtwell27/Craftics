package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Creeper AI: Suicidal bomber. Walks up to the player and detonates.
 * - Move + Prime in the SAME turn (no wasted StartFuse turn)
 * - Explodes next turn if still adjacent (radius 2, damages ALL entities including allies)
 * - If player escapes blast range, fuse resets and creeper chases again
 * - Explosion deals 2x attack power in a radius-2 AoE
 */
public class CreeperAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Fuse active from last turn — creeper is LOCKED IN PLACE
        if (self.getFuseTimer() > 0) {
            if (dist <= 2) {
                // BOOM! Radius 2 AoE, damages player AND other mobs
                return new EnemyAction.Explode(self.getAttackPower() * 2, 2);
            } else {
                // Player ran — fuse resets, creeper can move again
                self.setFuseTimer(0);
                // Fall through to normal movement below
            }
        }

        // If fuse is primed, creeper stays still (no movement allowed while hissing)
        if (self.getFuseTimer() > 0) {
            return new EnemyAction.StartFuse();
        }

        // Adjacent — prime fuse (explodes NEXT turn)
        if (dist <= 1) {
            self.setFuseTimer(1);
            return new EnemyAction.StartFuse();
        }

        // Rush toward player — if we reach adjacency, prime immediately
        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, self.getMoveSpeed());
        if (target == null) target = playerPos;

        List<GridPos> path = Pathfinding.findPath(arena, myPos, target, self.getMoveSpeed(), self);
        if (path.isEmpty()) return AIUtils.seekOrWander(self, arena, playerPos);

        GridPos endPos = path.get(path.size() - 1);
        if (endPos.manhattanDistance(playerPos) <= 1) {
            // Arrive adjacent — move AND prime in same turn
            self.setFuseTimer(1);
            // Use Move action, CombatManager will show the fuse message next time
            return new EnemyAction.Move(path);
        }

        return new EnemyAction.Move(path);
    }
}
