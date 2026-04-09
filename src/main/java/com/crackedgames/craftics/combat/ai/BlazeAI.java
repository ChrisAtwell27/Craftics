package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Blaze AI: Hovering fire artillery with a 3-shot fireball barrage.
 *
 * Each barrage cycle takes 5 turns:
 *   Turn 1: CHARGE — set fire ticks visually, idle/reposition
 *   Turn 2: SHOT 1 — fires a weaker fireball
 *   Turn 3: CHARGE — set fire ticks again, idle
 *   Turn 4: SHOT 2 — fires another fireball
 *   Turn 5: SHOT 3 — final fireball, then cooldown
 *
 * Each blaze gets its own AI instance (via {@code setAiInstance}) so state is per-entity.
 */
public class BlazeAI implements EnemyAI {

    /**
     * Phase machine for the barrage. Each entry maps to "what happens this turn".
     * SHOT phases fire a ranged attack; CHARGE phases idle and apply visual fire ticks
     * to telegraph the next shot.
     */
    private enum Phase { CHARGE_1, SHOT_1, CHARGE_2, SHOT_2, SHOT_3 }

    private Phase phase = Phase.CHARGE_1;

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        int range = self.getRange();
        // Each shot in the barrage hits for half the blaze's normal attack power
        int barrageDamage = Math.max(1, self.getAttackPower() / 2);

        // CHARGE phases: don't shoot, set the mob on fire so the player sees it telegraphing.
        if (phase == Phase.CHARGE_1 || phase == Phase.CHARGE_2) {
            applyFireTelegraph(self);
            // While charging, try to maintain a good range — back off if too close.
            EnemyAction repositionAction = repositionForBarrage(self, arena, playerPos, range);
            // Advance phase regardless of whether we moved
            phase = (phase == Phase.CHARGE_1) ? Phase.SHOT_1 : Phase.SHOT_2;
            return repositionAction != null ? repositionAction : new EnemyAction.Idle();
        }

        // SHOT phases: fire if in range, otherwise reposition + fire if possible.
        // If we can't shoot at all this turn, drop to a normal Move and stay in this phase.
        if (dist <= range) {
            EnemyAction shot = new EnemyAction.RangedAttack(barrageDamage, "fire");
            advanceShotPhase();
            return shot;
        }

        // Out of range — try to MoveAndAttack into range
        GridPos shotPos = findShotPosition(self, arena, playerPos);
        if (shotPos != null) {
            List<GridPos> path = Pathfinding.findPath(arena, myPos, shotPos, self.getMoveSpeed(), self);
            if (!path.isEmpty()) {
                GridPos endPos = path.get(path.size() - 1);
                if (endPos.manhattanDistance(playerPos) <= range) {
                    advanceShotPhase();
                    return new EnemyAction.MoveAndAttack(path, barrageDamage);
                }
                // Can't reach firing range this turn — close the gap and stay in this phase
                return new EnemyAction.Move(path);
            }
        }
        return AIUtils.seekOrWander(self, arena, playerPos);
    }

    /** Advance the phase counter after a successful shot. SHOT_3 wraps back to CHARGE_1. */
    private void advanceShotPhase() {
        phase = switch (phase) {
            case SHOT_1 -> Phase.CHARGE_2;
            case SHOT_2 -> Phase.SHOT_3;
            case SHOT_3 -> Phase.CHARGE_1;
            default -> phase;
        };
    }

    /**
     * Set the mob on fire (visually) for the duration of the upcoming shot turn.
     * 80 ticks ≈ 1 turn at standard combat speed.
     */
    private void applyFireTelegraph(CombatEntity self) {
        if (self.getMobEntity() != null) {
            self.getMobEntity().setFireTicks(160);
        }
    }

    /**
     * During CHARGE turns, try to keep distance from the player so the next shot has range.
     * Returns null if no movement is needed/possible.
     */
    private EnemyAction repositionForBarrage(CombatEntity self, GridArena arena, GridPos playerPos, int range) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        if (dist > 2 && dist <= range) return null; // already in a good firing window
        GridPos fleeTarget = AIUtils.getFleeTarget(arena, myPos, playerPos, self.getMoveSpeed());
        if (fleeTarget != null) {
            List<GridPos> path = Pathfinding.findPath(arena, myPos, fleeTarget, self.getMoveSpeed(), self);
            if (!path.isEmpty()) return new EnemyAction.Move(path);
        }
        return null;
    }

    private GridPos findShotPosition(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        GridPos best = null;
        int bestScore = Integer.MIN_VALUE;
        int range = self.getRange();

        for (int dx = -self.getMoveSpeed(); dx <= self.getMoveSpeed(); dx++) {
            for (int dz = -self.getMoveSpeed(); dz <= self.getMoveSpeed(); dz++) {
                if (Math.abs(dx) + Math.abs(dz) > self.getMoveSpeed()) continue;
                if (dx == 0 && dz == 0) continue;
                GridPos candidate = new GridPos(myPos.x() + dx, myPos.z() + dz);
                if (!arena.isInBounds(candidate) || arena.isOccupied(candidate)) continue;
                var tile = arena.getTile(candidate);
                if (tile == null || !tile.isWalkable()) continue;

                int distToPlayer = candidate.manhattanDistance(playerPos);
                if (distToPlayer > range) continue;
                int score = Math.min(distToPlayer, 4) * 3;
                if (distToPlayer <= 1) score -= 10;
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return best;
    }
}
