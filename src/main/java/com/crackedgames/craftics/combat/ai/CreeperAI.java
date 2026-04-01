package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Creeper AI: Suicidal bomber with charged variant.
 * - Move + Prime in the SAME turn (no wasted StartFuse turn)
 * - Explodes next turn if still adjacent (radius 2, damages ALL entities including allies)
 * - CHARGED: if hit by player's ranged attack while fuse is NOT active, becomes charged
 *   (explosion radius doubles to 4, damage doubles). Visual: lightning particles.
 * - If player escapes blast range, fuse resets and creeper chases again
 * - Explosion deals 2x (or 4x if charged) attack power in AoE
 */
public class CreeperAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Check if damaged by ranged — become charged (enraged flag = charged)
        if (self.wasDamagedSinceLastTurn() && !self.isEnraged() && self.getFuseTimer() == 0) {
            self.setEnraged(true); // charged creeper
        }

        int explosionRadius = self.isEnraged() ? 2 : 1;
        int explosionDamage = self.isEnraged()
            ? self.getAttackPower() * 2
            : self.getAttackPower() + 3;

        // Fuse active from last turn — creeper ALWAYS explodes, no movement allowed
        if (self.getFuseTimer() > 0) {
            return new EnemyAction.Explode(explosionDamage, explosionRadius);
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
            return new EnemyAction.Move(path);
        }

        return new EnemyAction.Move(path);
    }
}
