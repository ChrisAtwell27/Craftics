package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.List;

/**
 * AI for homing projectile entities (shulker bullets).
 *
 * <p>Unlike {@link ProjectileAI}'s straight-line flight, a seeker re-aims at
 * the player before every step: up to {@code SPEED} cardinal steps per turn,
 * preferring the axis with more distance remaining and sidestepping onto the
 * other axis when the preferred tile is blocked. It never detonates on
 * terrain - a fully blocked bullet just hovers and waits - and only impacts
 * when a step lands on the player's tile.</p>
 *
 * <p>The counterplay is HP, not deflection: seekers spawn with 1 HP, so any
 * attack swats them (the ghast-fireball redirect special case deliberately
 * does not apply).</p>
 */
public class SeekingProjectileAI implements EnemyAI {
    private static final int SPEED = 2;

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        List<GridPos> path = new ArrayList<>();
        GridPos current = self.getGridPos();

        for (int i = 0; i < SPEED; i++) {
            int dx = Integer.signum(playerPos.x() - current.x());
            int dz = Integer.signum(playerPos.z() - current.z());
            if (dx == 0 && dz == 0) break; // already on the player's tile (shouldn't happen)

            // Preferred axis first (more distance remaining), other axis as the
            // sidestep. Zero-length candidates are skipped.
            int adx = Math.abs(playerPos.x() - current.x());
            int adz = Math.abs(playerPos.z() - current.z());
            int[][] candidates = adx >= adz
                ? new int[][] { { dx, 0 }, { 0, dz } }
                : new int[][] { { 0, dz }, { dx, 0 } };

            GridPos stepped = null;
            for (int[] c : candidates) {
                if (c[0] == 0 && c[1] == 0) continue;
                GridPos next = new GridPos(current.x() + c[0], current.z() + c[1]);

                // Reached the player - impact.
                if (next.equals(playerPos)) {
                    path.add(next);
                    return new EnemyAction.ProjectileMove(path, true, next);
                }

                if (!arena.isInBounds(next)) continue;
                if (arena.getTile(next) == null || !arena.getTile(next).isWalkable()) continue;
                if (arena.isOccupied(next)) continue;
                stepped = next;
                break;
            }

            if (stepped == null) break; // boxed in - hover in place this turn
            path.add(stepped);
            current = stepped;
        }

        if (path.isEmpty()) {
            return new EnemyAction.Idle();
        }
        return new EnemyAction.ProjectileMove(path, false, null);
    }

    /**
     * Danger highlight: everywhere the seeker can reach next turn. The generic
     * speed+range diamond under-reports a 2-tiles-per-turn homing bullet.
     */
    @Override
    public java.util.Set<GridPos> computeThreatTiles(CombatEntity self, GridArena arena) {
        java.util.Set<GridPos> tiles = new java.util.HashSet<>();
        GridPos myPos = self.getGridPos();
        for (int dx = -SPEED; dx <= SPEED; dx++) {
            for (int dz = -SPEED; dz <= SPEED; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > SPEED) continue;
                GridPos p = new GridPos(myPos.x() + dx, myPos.z() + dz);
                if (arena.isInBounds(p)) tiles.add(p);
            }
        }
        return tiles;
    }
}
