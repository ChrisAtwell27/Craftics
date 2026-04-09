package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Enderman AI: Aggressive phase-shifting teleporter.
 *
 * Hunts in cycles — teleports in for 2-3 strikes, then blinks out. Below 50% HP,
 * enters a frenzy and never retreats. Varied attack patterns keep the player guessing.
 *
 * Behavior:
 * - ASSAULT PHASE (strikesRemaining > 0): teleport adjacent → attack → repeat.
 *   Each cycle is 2–3 strikes before retreating.
 * - RETREAT: blinks away after exhausting strikes, resets for next assault.
 * - STALK: when far away, teleports to within 2 tiles (visible threat) before striking.
 * - FRENZY (≤50% HP): never retreats. Teleport-strike every turn. Attacks with +50% damage.
 * - REACTIVE DODGE: 60% chance to blink 1 tile sideways when hit (not full escape).
 */
public class EndermanAI implements EnemyAI {
    private int strikesRemaining = 0;
    private boolean frenzy = false;

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        int atk = self.getAttackPower();

        // Frenzy check: below 50% HP, never retreat
        if (!frenzy && self.getCurrentHp() <= self.getMaxHp() / 2) {
            frenzy = true;
            self.setEnraged(true);
        }
        int damage = frenzy ? (int)(atk * 1.5) : atk;

        // REACTIVE DODGE: when hit, short blink sideways (not full escape)
        if (self.wasDamagedSinceLastTurn() && !frenzy) {
            GridPos dodge = findDodgeTile(arena, myPos, playerPos);
            if (dodge != null && Math.random() < 0.6) {
                return new EnemyAction.Teleport(dodge);
            }
        }

        // FRENZY MODE: relentless teleport-strike every turn
        if (frenzy) {
            if (dist == 1) {
                return new EnemyAction.Attack(damage);
            }
            GridPos target = findTeleportAdjacent(arena, myPos, playerPos);
            if (target != null) {
                return new EnemyAction.TeleportAndAttack(target, damage);
            }
            GridPos closer = findTeleportCloser(arena, myPos, playerPos, 5);
            if (closer != null) {
                return new EnemyAction.Teleport(closer);
            }
            return AIUtils.seekOrWander(self, arena, playerPos);
        }

        // ASSAULT PHASE: chain strikes
        if (strikesRemaining > 0) {
            strikesRemaining--;
            if (dist == 1) {
                return new EnemyAction.Attack(damage);
            }
            GridPos target = findTeleportAdjacent(arena, myPos, playerPos);
            if (target != null) {
                return new EnemyAction.TeleportAndAttack(target, damage);
            }
        }

        // RETREAT after assault — blink away, reset strikes for next cycle
        if (strikesRemaining == 0 && dist <= 2) {
            strikesRemaining = 2 + (Math.random() < 0.5 ? 1 : 0); // 2-3 next cycle
            GridPos escapeTile = findTeleportAway(arena, myPos, playerPos, 4);
            if (escapeTile != null) {
                return new EnemyAction.Teleport(escapeTile);
            }
        }

        // STALK: approach to within 2 tiles before starting an assault
        if (strikesRemaining == 0) {
            strikesRemaining = 2 + (Math.random() < 0.5 ? 1 : 0);
        }
        if (dist > 2) {
            // Teleport to a menacing position 2 tiles from the player
            GridPos stalkPos = findStalkPosition(arena, myPos, playerPos);
            if (stalkPos != null) {
                return new EnemyAction.Teleport(stalkPos);
            }
        }

        // Adjacent — attack directly
        if (dist == 1) {
            return new EnemyAction.Attack(damage);
        }

        // Teleport strike if in range
        GridPos target = findTeleportAdjacent(arena, myPos, playerPos);
        if (target != null) {
            strikesRemaining = Math.max(0, strikesRemaining - 1);
            return new EnemyAction.TeleportAndAttack(target, damage);
        }

        return AIUtils.seekOrWander(self, arena, playerPos);
    }

    /** Find any tile adjacent to the player that we can teleport to (within range 5). */
    private GridPos findTeleportAdjacent(GridArena arena, GridPos self, GridPos playerPos) {
        int dx = Integer.signum(playerPos.x() - self.x());
        int dz = Integer.signum(playerPos.z() - self.z());

        // Try behind, flanks, then front — varied approach angles
        GridPos[] candidates = {
            new GridPos(playerPos.x() + dx, playerPos.z() + dz),   // behind
            new GridPos(playerPos.x() + dz, playerPos.z() - dx),   // flank left
            new GridPos(playerPos.x() - dz, playerPos.z() + dx),   // flank right
            new GridPos(playerPos.x() - dx, playerPos.z() - dz),   // front
        };

        // Shuffle flanks for unpredictability
        if (Math.random() < 0.5) {
            GridPos temp = candidates[1];
            candidates[1] = candidates[2];
            candidates[2] = temp;
        }

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

    /** Short dodge: blink 1-2 tiles perpendicular to the threat. */
    private GridPos findDodgeTile(GridArena arena, GridPos self, GridPos threat) {
        int dx = self.x() - threat.x();
        int dz = self.z() - threat.z();
        // Perpendicular directions
        int pdx = -Integer.signum(dz);
        int pdz = Integer.signum(dx);
        if (pdx == 0 && pdz == 0) { pdx = 1; }

        GridPos[] candidates = {
            new GridPos(self.x() + pdx, self.z() + pdz),
            new GridPos(self.x() - pdx, self.z() - pdz),
            new GridPos(self.x() + pdx * 2, self.z() + pdz * 2),
            new GridPos(self.x() - pdx * 2, self.z() - pdz * 2),
        };

        for (GridPos c : candidates) {
            if (arena.isInBounds(c) && !arena.isOccupied(c)) {
                var tile = arena.getTile(c);
                if (tile != null && tile.isWalkable()) return c;
            }
        }
        return null;
    }

    /** Find a tile 2 tiles from the player for stalking/threatening. */
    private GridPos findStalkPosition(GridArena arena, GridPos self, GridPos playerPos) {
        List<GridPos> candidates = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) != 2) continue;
                GridPos c = new GridPos(playerPos.x() + dx, playerPos.z() + dz);
                if (!arena.isInBounds(c) || arena.isOccupied(c)) continue;
                var tile = arena.getTile(c);
                if (tile == null || !tile.isWalkable()) continue;
                if (self.manhattanDistance(c) <= 5) {
                    candidates.add(c);
                }
            }
        }
        if (candidates.isEmpty()) return null;
        Collections.shuffle(candidates);
        return candidates.get(0);
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
