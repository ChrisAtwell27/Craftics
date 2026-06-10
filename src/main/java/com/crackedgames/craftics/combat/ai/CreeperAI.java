package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Creeper AI: Suicidal bomber with charged variant.
 * - Move + Prime in the SAME turn (no wasted StartFuse turn)
 * - Explodes next turn if a victim (player or ally pet) is still inside the
 *   blast radius. If everyone escaped, the fuse RESETS and the creeper chases
 *   again — it no longer wastes itself on an empty tile. A creeper about to
 *   die (≤25% HP) blows regardless rather than dying for nothing.
 * - CHARGED: if hit by player's ranged attack while fuse is NOT active, becomes
 *   charged (explosion radius and damage scale up). Visual: glow.
 * - Explosion damages ALL entities in the radius, its own side included.
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

        // Fuse active from last turn — explode if it would catch a victim, or
        // if we're about to die anyway. Otherwise defuse and resume the chase:
        // a creeper hissing at an empty tile helps nobody.
        if (self.getFuseTimer() > 0) {
            boolean aboutToDie = self.getCurrentHp() * 4 <= self.getMaxHp();
            if (aboutToDie || victimInBlast(arena, myPos, playerPos, explosionRadius)) {
                return new EnemyAction.Explode(explosionDamage, explosionRadius);
            }
            self.setFuseTimer(0);
            if (self.getMobEntity() != null) {
                self.getMobEntity().setGlowing(false); // undo the fuse glow
            }
            // fall through to the chase below
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

    /** True if the blast centered on {@code center} would catch the player or an ally pet. */
    private boolean victimInBlast(GridArena arena, GridPos center, GridPos playerPos, int radius) {
        for (GridPos threat : AIUtils.threatPositions(arena, playerPos)) {
            if (center.manhattanDistance(threat) <= radius) return true;
        }
        return false;
    }
}
