package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

/**
 * Enderman AI: Hit-and-run teleporter — the most unpredictable enemy.
 * - TELEPORT STRIKE: teleports behind player, attacks, then teleports away
 * - REACTIVE BLINK: if damaged, instantly teleports to safety
 * - Speed 5 teleport range makes it impossible to corner
 * - Alternates between attacking and repositioning
 */
public class EndermanAI implements EnemyAI {

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // REACTIVE BLINK: if damaged, teleport away to safety
        if (self.wasDamagedSinceLastTurn()) {
            GridPos escapeTile = findTeleportAway(arena, myPos, playerPos, 4);
            if (escapeTile != null) {
                return new EnemyAction.Teleport(escapeTile);
            }
        }

        // Adjacent — attack
        if (dist == 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        // TELEPORT STRIKE: blink to adjacent tile and attack
        GridPos teleportTarget = findTeleportBehind(arena, myPos, playerPos);
        if (teleportTarget != null) {
            return new EnemyAction.TeleportAndAttack(teleportTarget, self.getAttackPower());
        }

        // Fallback: teleport closer if can't reach adjacent
        GridPos closerTile = findTeleportCloser(arena, myPos, playerPos, self.getMoveSpeed());
        if (closerTile != null) {
            return new EnemyAction.Teleport(closerTile);
        }

        return AIUtils.seekOrWander(self, arena, playerPos);
    }

    private GridPos findTeleportBehind(GridArena arena, GridPos self, GridPos playerPos) {
        int dx = Integer.signum(playerPos.x() - self.x());
        int dz = Integer.signum(playerPos.z() - self.z());

        // Try behind, flanks, then front
        GridPos[] candidates = {
            new GridPos(playerPos.x() + dx, playerPos.z() + dz),
            new GridPos(playerPos.x() + dz, playerPos.z() - dx),
            new GridPos(playerPos.x() - dz, playerPos.z() + dx),
            new GridPos(playerPos.x() - dx, playerPos.z() - dz),
        };

        for (GridPos c : candidates) {
            if (arena.isInBounds(c) && !arena.isOccupied(c)) {
                var tile = arena.getTile(c);
                if (tile != null && tile.isWalkable() && self.manhattanDistance(c) <= 5) {
                    return c;
                }
            }
        }
        return null;
    }

    private GridPos findTeleportAway(GridArena arena, GridPos self, GridPos threat, int range) {
        GridPos best = null;
        int bestDist = 0;

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > range) continue;
                GridPos candidate = new GridPos(self.x() + dx, self.z() + dz);
                if (!arena.isInBounds(candidate) || arena.isOccupied(candidate)) continue;
                var tile = arena.getTile(candidate);
                if (tile == null || !tile.isWalkable()) continue;

                int distFromThreat = candidate.manhattanDistance(threat);
                if (distFromThreat > bestDist) {
                    bestDist = distFromThreat;
                    best = candidate;
                }
            }
        }
        return best;
    }

    private GridPos findTeleportCloser(GridArena arena, GridPos self, GridPos playerPos, int range) {
        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > range) continue;
                GridPos candidate = new GridPos(self.x() + dx, self.z() + dz);
                if (!arena.isInBounds(candidate) || arena.isOccupied(candidate)) continue;
                var tile = arena.getTile(candidate);
                if (tile == null || !tile.isWalkable()) continue;

                int dist = candidate.manhattanDistance(playerPos);
                if (dist < bestDist && dist >= 1) {
                    bestDist = dist;
                    best = candidate;
                }
            }
        }
        return best;
    }
}
