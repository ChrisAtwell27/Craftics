package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.List;

/**
 * AI for boss-spawned projectile entities (Ghast Fireballs, Wither Skulls).
 * Moves in a straight line each turn without adjusting toward the player.
 * Speed is fixed at 2 tiles per turn.
 *
 * Collision behavior:
 * - Wall/out of bounds → impact (fireball = AOE explosion, skull = removed)
 * - Player tile → impact (damage + effects)
 * - Enemy tile (redirected fireballs only) → impact (AOE damages enemy)
 * - Enemy tile (non-redirected) → blocked, wait
 */
public class ProjectileAI implements EnemyAI {
    private static final int PROJECTILE_SPEED = 2;

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        int dx = self.getProjectileDirX();
        int dz = self.getProjectileDirZ();

        List<GridPos> path = new ArrayList<>();
        GridPos current = self.getGridPos();
        boolean impacts = false;
        GridPos impactPos = null;

        for (int i = 0; i < PROJECTILE_SPEED; i++) {
            GridPos next = new GridPos(current.x() + dx, current.z() + dz);

            // Hit wall or out of bounds
            if (!arena.isInBounds(next)
                    || (arena.getTile(next) != null && !arena.getTile(next).isWalkable())) {
                impacts = true;
                impactPos = current; // explode where the projectile is
                break;
            }

            // Hit player
            if (next.equals(playerPos)) {
                path.add(next);
                impacts = true;
                impactPos = next;
                break;
            }

            // Hit entity (including background bosses for redirected fireballs)
            CombatEntity occupant = arena.getOccupant(next);
            boolean blocked = arena.isOccupied(next);
            boolean hitBackgroundBoss = !blocked && occupant != null
                    && occupant.isBackgroundBoss() && self.isProjectileRedirected();
            if (blocked || hitBackgroundBoss) {
                if (self.isProjectileRedirected()) {
                    // Redirected fireball impacts on enemy or background boss
                    impacts = true;
                    impactPos = next;
                    break;
                } else {
                    // Non-redirected: blocked, stop without impact
                    break;
                }
            }

            path.add(next);
            current = next;
        }

        // Can't move and no impact — idle
        if (path.isEmpty() && !impacts) {
            return new EnemyAction.Idle();
        }

        return new EnemyAction.ProjectileMove(path, impacts, impactPos);
    }
}
