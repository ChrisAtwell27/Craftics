package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Ghast AI: Long-range fireball artillery. Slow but devastating.
 * - Spawns a fireball projectile entity (speed 2, straight line, 1-tile AOE on impact)
 * - FLEE: panics if player gets within 3 tiles, retreats
 * - Player can redirect fireballs by hitting them
 * - Fireballs have high HP (99) — designed to be deflected, not killed
 * - Speed 1 = nearly stationary, but huge range compensates
 */
public class GhastAI implements EnemyAI {
    /** Maximum fireballs this ghast can have alive at once. */
    private static final int MAX_FIREBALLS = 2;

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        int range = self.getRange();

        // Count existing fireballs spawned by this ghast
        int liveFireballs = 0;
        for (CombatEntity e : arena.getOccupants().values()) {
            if (e.isAlive() && e.isProjectile()
                    && "ghast_fireball".equals(e.getProjectileType())
                    && e.getProjectileOwnerId() == self.getEntityId()) {
                liveFireballs++;
            }
        }

        // PANIC: if player too close, flee desperately
        if (dist <= 3) {
            GridPos fleeTarget = AIUtils.getFleeTarget(arena, myPos, playerPos, 1);
            if (fleeTarget != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, fleeTarget, 1, self);
                if (!path.isEmpty()) {
                    return new EnemyAction.Move(path);
                }
            }
            // Can't flee — try to fire a fireball anyway
        }

        // In range — spawn a fireball projectile
        if (dist <= range && liveFireballs < MAX_FIREBALLS) {
            GridPos spawnPos = findFireballSpawnPos(self, arena, playerPos);
            if (spawnPos != null) {
                int dx = playerPos.x() - myPos.x();
                int dz = playerPos.z() - myPos.z();
                // Normalize to cardinal direction
                if (Math.abs(dx) >= Math.abs(dz)) {
                    dx = Integer.signum(dx); dz = 0;
                } else {
                    dx = 0; dz = Integer.signum(dz);
                }
                return new EnemyAction.SpawnProjectile(
                    "minecraft:blaze", List.of(spawnPos), List.of(new int[]{dx, dz}),
                    99, self.getAttackPower(), 0, "ghast_fireball"
                );
            }
        }

        // Out of range — slowly reposition
        GridPos shotPos = findDistantShotPosition(self, arena, playerPos);
        if (shotPos != null) {
            List<GridPos> path = Pathfinding.findPath(arena, myPos, shotPos, 1, self);
            if (!path.isEmpty()) {
                return new EnemyAction.Move(path);
            }
        }

        return AIUtils.seekOrWander(self, arena, playerPos);
    }

    /**
     * Find a tile adjacent to the ghast in the direction of the player for fireball spawning.
     */
    private GridPos findFireballSpawnPos(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dx = playerPos.x() - myPos.x();
        int dz = playerPos.z() - myPos.z();
        // Normalize to cardinal direction
        if (Math.abs(dx) >= Math.abs(dz)) {
            dx = Integer.signum(dx); dz = 0;
        } else {
            dx = 0; dz = Integer.signum(dz);
        }

        // Try the tile directly in front first
        GridPos front = new GridPos(myPos.x() + dx, myPos.z() + dz);
        if (arena.isInBounds(front) && !arena.isOccupied(front)
                && arena.getTile(front) != null && arena.getTile(front).isWalkable()) {
            return front;
        }

        // Try adjacent tiles in perpendicular directions
        int perpDx = dz;
        int perpDz = -dx;
        for (int offset : new int[]{1, -1}) {
            GridPos alt = new GridPos(myPos.x() + dx + perpDx * offset, myPos.z() + dz + perpDz * offset);
            if (arena.isInBounds(alt) && !arena.isOccupied(alt)
                    && arena.getTile(alt) != null && arena.getTile(alt).isWalkable()) {
                return alt;
            }
        }
        return null;
    }

    private GridPos findDistantShotPosition(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        GridPos best = null;
        int bestScore = Integer.MIN_VALUE;
        int range = self.getRange();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 && dz != 0) continue;
                if (dx == 0 && dz == 0) continue;
                GridPos candidate = new GridPos(myPos.x() + dx, myPos.z() + dz);
                if (!arena.isInBounds(candidate) || arena.isOccupied(candidate)) continue;
                var tile = arena.getTile(candidate);
                if (tile == null || !tile.isWalkable()) continue;

                int distToPlayer = candidate.manhattanDistance(playerPos);
                // Prefer maximum distance while staying in range
                int score = distToPlayer * 3 + (distToPlayer <= range ? 20 : 0);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return best;
    }
}
