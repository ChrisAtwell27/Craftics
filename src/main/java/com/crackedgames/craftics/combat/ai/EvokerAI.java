package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Evoker AI: Illager spellcaster with fang attacks.
 * - SUMMON VEX: summons a vex when the player first comes into range, and a
 *   second one when first wounded below 50% HP. Summon state lives in the
 *   entity's AI memory - the old instance flag was shared by every evoker,
 *   so after the first fight no evoker ever summoned again.
 * - FANG SNAP: ranged attack at 3-4 tiles (evoker fangs)
 * - RETREAT: kites backward when player closes in
 * - REPOSITION: moves then attacks in same turn
 * - Prefers to maintain range 3-4
 */
public class EvokerAI implements EnemyAI {
    private static final String VEX_SUMMONS = "evoker_vex_summons";

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // First summon when the player comes into range; a second, desperate
        // summon once wounded below half HP.
        int summons = self.getAiMemory(VEX_SUMMONS, 0);
        boolean wantsSummon = (summons == 0 && dist <= self.getRange())
            || (summons == 1 && self.getCurrentHp() * 2 < self.getMaxHp());
        if (wantsSummon) {
            self.setAiMemory(VEX_SUMMONS, summons + 1);
            // Find an open tile adjacent to the evoker for the vex
            List<GridPos> vexPositions = new java.util.ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    GridPos candidate = new GridPos(myPos.x() + dx, myPos.z() + dz);
                    if (arena.isInBounds(candidate) && !arena.isOccupied(candidate)) {
                        var tile = arena.getTile(candidate);
                        if (tile != null && tile.isWalkable()) {
                            vexPositions.add(candidate);
                        }
                    }
                }
            }
            if (!vexPositions.isEmpty()) {
                GridPos vexPos = vexPositions.get(
                    java.util.concurrent.ThreadLocalRandom.current().nextInt(vexPositions.size()));
                return new EnemyAction.SummonMinions("minecraft:vex", 1,
                    List.of(vexPos), 8, self.getAttackPower(), 0);
            }
        }

        List<GridPos> threats = AIUtils.threatPositions(arena, playerPos);

        // Too close - retreat and cast
        if (AIUtils.minThreatDistance(myPos, threats) <= 2) {
            GridPos fleeTarget = AIUtils.bestRetreatTile(self, arena, threats);
            if (fleeTarget != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, fleeTarget, self.getMoveSpeed(), self);
                if (!path.isEmpty()) {
                    GridPos endPos = path.get(path.size() - 1);
                    if (endPos.manhattanDistance(playerPos) <= self.getRange()) {
                        return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                    }
                    return new EnemyAction.Move(path);
                }
            }
            // Can't retreat - cast at point blank
            return new EnemyAction.RangedAttack(self.getAttackPower(), "fangs");
        }

        // In range - cast fangs
        if (dist <= self.getRange()) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "fangs");
        }

        // Out of range - reposition to get within casting distance
        GridPos best = null;
        int bestScore = Integer.MIN_VALUE;
        for (GridPos candidate : Pathfinding.getReachableTiles(
                arena, myPos, self.getMoveSpeed(), self.getSize(), self)) {
            if (candidate.equals(myPos)) continue;
            int distToPlayer = candidate.manhattanDistance(playerPos);
            if (distToPlayer < 3 || distToPlayer > self.getRange()) continue;
            int score = -myPos.manhattanDistance(candidate);
            if (AIUtils.isHazardTile(arena, candidate)) score -= 25;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        if (best != null) {
            List<GridPos> path = Pathfinding.findPath(arena, myPos, best, self.getMoveSpeed(), self);
            if (!path.isEmpty()) {
                GridPos endPos = path.get(path.size() - 1);
                if (endPos.manhattanDistance(playerPos) <= self.getRange()) {
                    return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                }
                return new EnemyAction.Move(path);
            }
        }

        return AIUtils.seekOrWander(self, arena, playerPos);
    }
}
