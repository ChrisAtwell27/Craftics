package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Drowned AI: Aquatic fighter with 50% trident variant.
 * - 50% of drowned spawn with tridents - rolled once per drowned, stored on
 *   the entity (the old instance field was shared by every drowned, so the
 *   first roll ever made decided the loadout for all of them, forever)
 * - TRIDENT THROW: only fires DIAGONALLY at range ≤3 for tactical variety
 * - WATER SPEED: double speed on water tiles
 * - Non-trident drowned are pure melee rushers (Husk-like stats)
 * - Falls back to melee if no trident or no diagonal LOS
 */
public class DrownedAI implements EnemyAI {
    private static final String TRIDENT_DECIDED = "drowned_trident_decided";

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        // Determine trident on first decision (50% chance per drowned)
        if (!self.hasAiMemory(TRIDENT_DECIDED)) {
            self.setAiMemory(TRIDENT_DECIDED, 1);
            // Store the flag on the combat entity so CombatManager can check it
            self.setDrownedWithTrident(java.util.concurrent.ThreadLocalRandom.current().nextDouble() < 0.5);
        }
        boolean hasTrident = self.isDrownedWithTrident();

        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Water speed boost - check if standing on water
        int effectiveSpeed = self.getMoveSpeed();
        var currentTile = arena.getTile(myPos);
        if (currentTile != null && currentTile.isWater()) {
            effectiveSpeed *= 2;
        }

        // Adjacent - melee attack
        if (dist == 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        // Trident throw only if diagonal to player and in range 3
        if (hasTrident && isDiagonal(myPos, playerPos) && dist <= 3) {
            return new EnemyAction.RangedAttack(self.getAttackPower() + 1, "trident");
        }

        // Try to move to get diagonal LOS for a trident throw
        if (hasTrident) {
            for (int dx = -effectiveSpeed; dx <= effectiveSpeed; dx++) {
                for (int dz = -effectiveSpeed; dz <= effectiveSpeed; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > effectiveSpeed) continue;
                    if (dx == 0 && dz == 0) continue;
                    GridPos candidate = new GridPos(myPos.x() + dx, myPos.z() + dz);
                    if (!arena.isInBounds(candidate) || arena.isOccupied(candidate)) continue;
                    var tile = arena.getTile(candidate);
                    if (tile == null || !tile.isWalkable()) continue;
                    // Check if this candidate position would be diagonal to player
                    if (isDiagonal(candidate, playerPos) && candidate.manhattanDistance(playerPos) <= 3) {
                        List<GridPos> path = Pathfinding.findPath(arena, myPos, candidate, effectiveSpeed, self);
                        if (!path.isEmpty()) {
                            return new EnemyAction.MoveAndAttack(path, self.getAttackPower() + 1);
                        }
                    }
                }
            }
        }

        // No diagonal trident shot available - rush melee
        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, effectiveSpeed);
        if (target == null) target = playerPos;

        List<GridPos> path = Pathfinding.findPath(arena, myPos, target, effectiveSpeed, self);
        if (path.isEmpty()) return AIUtils.seekOrWander(self, arena, playerPos);

        GridPos endPos = path.get(path.size() - 1);
        if (endPos.manhattanDistance(playerPos) <= 1) {
            return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
        }
        return new EnemyAction.Move(path);
    }

    /**
     * Checks if two positions are diagonal from each other.
     * Diagonal = both dx and dz are non-zero and equal in magnitude.
     */
    private boolean isDiagonal(GridPos from, GridPos to) {
        int dx = Math.abs(to.x() - from.x());
        int dz = Math.abs(to.z() - from.z());
        return dx > 0 && dz > 0 && dx == dz;
    }
}
