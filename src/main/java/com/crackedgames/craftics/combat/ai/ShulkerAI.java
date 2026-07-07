package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Shulker AI: stationary artillery that NEVER walks. Its whole turn cycle is:
 * <ol>
 *   <li>fire a homing bullet,</li>
 *   <li>fire a second homing bullet,</li>
 *   <li>teleport to a random free tile and start over.</li>
 * </ol>
 *
 * <p>Bullets are real 1-HP projectile entities (rendered as vanilla shulker
 * bullets) driven by {@link SeekingProjectileAI}: they chase the player at 2
 * tiles/turn and apply Levitation on impact - shoot them down before they
 * connect. With {@link #MAX_BULLETS} of its bullets already in flight the
 * shulker holds fire (the cycle doesn't advance), and when the player is
 * point-blank it snaps off an instant bolt instead of spawning a seeker at
 * its own feet. Out of range it teleports instead of walking - the blink is
 * both its escape and its repositioning.</p>
 */
public class ShulkerAI implements EnemyAI {
    /** aiMemory key: bullets fired since the last teleport. */
    private static final String SHOTS = "shulker_shots";
    /** Shots per cycle before the shulker relocates. */
    private static final int SHOTS_PER_CYCLE = 2;
    /** Maximum homing bullets this shulker can have in flight at once. */
    private static final int MAX_BULLETS = 2;

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        int dist = self.minDistanceTo(playerPos);
        int range = self.getRange();
        int shots = self.getAiMemory(SHOTS, 0);

        // Cycle complete - blink to a random tile and start a fresh volley.
        if (shots >= SHOTS_PER_CYCLE) {
            self.setAiMemory(SHOTS, 0);
            GridPos dest = randomFreeTile(arena, playerPos);
            if (dest != null) {
                return new EnemyAction.Teleport(dest);
            }
            return new EnemyAction.Idle(); // arena packed - shell up this turn
        }

        // Count this shulker's bullets still in flight; at the cap, hold fire
        // without advancing the cycle (the sky is dangerous enough).
        int liveBullets = 0;
        for (CombatEntity e : arena.getOccupants().values()) {
            if (e.isAlive() && e.isProjectile()
                    && "shulker_bullet".equals(e.getProjectileType())
                    && e.getProjectileOwnerId() == self.getEntityId()) {
                liveBullets++;
            }
        }
        if (liveBullets >= MAX_BULLETS) {
            return new EnemyAction.Idle();
        }

        // Point blank: no room for a seeker - instant bolt, still counts as a shot.
        if (dist <= 1) {
            self.setAiMemory(SHOTS, shots + 1);
            return new EnemyAction.RangedAttack(self.getAttackPower(), "shulker_bullet");
        }

        // In range - launch a homing bullet entity (1 HP, seeks at 2/turn).
        if (dist <= range) {
            self.setAiMemory(SHOTS, shots + 1);
            GridPos spawnPos = findBulletSpawnPos(self, arena, playerPos);
            if (spawnPos != null) {
                int dx = playerPos.x() - self.getGridPos().x();
                int dz = playerPos.z() - self.getGridPos().z();
                if (Math.abs(dx) >= Math.abs(dz)) {
                    dx = Integer.signum(dx); dz = 0;
                } else {
                    dx = 0; dz = Integer.signum(dz);
                }
                return new EnemyAction.SpawnProjectile(
                    "minecraft:blaze", List.of(spawnPos), List.of(new int[]{dx, dz}),
                    1, self.getAttackPower(), 0, "shulker_bullet"
                );
            }
            // Boxed in with no free adjacent tile - instant bolt fallback.
            return new EnemyAction.RangedAttack(self.getAttackPower(), "shulker_bullet");
        }

        // Out of range - a shulker never walks: teleport to hunt a firing angle.
        GridPos dest = randomFreeTile(arena, playerPos);
        if (dest != null) {
            self.setAiMemory(SHOTS, 0);
            return new EnemyAction.Teleport(dest);
        }
        return new EnemyAction.Idle();
    }

    /**
     * A random free, walkable tile for the blink. Prefers tiles at least 2 away
     * from the player (a shulker relocating INTO melee range would be a gift);
     * falls back to any free tile when the arena is that cramped.
     */
    private GridPos randomFreeTile(GridArena arena, GridPos playerPos) {
        List<GridPos> preferred = new ArrayList<>();
        List<GridPos> fallback = new ArrayList<>();
        for (int x = 0; x < arena.getWidth(); x++) {
            for (int z = 0; z < arena.getHeight(); z++) {
                GridPos pos = new GridPos(x, z);
                if (pos.equals(playerPos) || arena.isOccupied(pos)) continue;
                if (arena.getTile(pos) == null || !arena.getTile(pos).isWalkable()) continue;
                if (pos.manhattanDistance(playerPos) >= 2) {
                    preferred.add(pos);
                } else {
                    fallback.add(pos);
                }
            }
        }
        List<GridPos> pool = !preferred.isEmpty() ? preferred : fallback;
        if (pool.isEmpty()) return null;
        return pool.get((int) (Math.random() * pool.size()));
    }

    /**
     * Free walkable tile adjacent to the shulker in the player's direction for
     * the bullet to appear on (front first, then the perpendicular corners) -
     * mirrors GhastAI's fireball spawn search.
     */
    private GridPos findBulletSpawnPos(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dx = playerPos.x() - myPos.x();
        int dz = playerPos.z() - myPos.z();
        if (Math.abs(dx) >= Math.abs(dz)) {
            dx = Integer.signum(dx); dz = 0;
        } else {
            dx = 0; dz = Integer.signum(dz);
        }

        GridPos front = new GridPos(myPos.x() + dx, myPos.z() + dz);
        if (arena.isInBounds(front) && !arena.isOccupied(front)
                && arena.getTile(front) != null && arena.getTile(front).isWalkable()) {
            return front;
        }

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
}
