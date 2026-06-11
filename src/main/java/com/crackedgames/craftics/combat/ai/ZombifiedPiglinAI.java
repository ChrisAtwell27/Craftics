package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Zombified Piglin AI: Neutral pack mob with mob mentality.
 * - NEUTRAL: wanders peacefully until ANY zombified piglin in the arena is hit
 * - PACK AGGRO: when one is hit, ALL zombified piglins in the fight permanently
 *   aggro. Tracked with per-entity enrage flags — the old implementation used a
 *   static flag that was never reset, so one provoked piglin made every
 *   zombified piglin in every later fight on the server spawn hostile.
 * - MOB MENTALITY: +1 ATK per nearby aggro'd packmate (radius 2)
 * - Once aggro'd, relentless melee like zombie but with pack bonuses
 */
public class ZombifiedPiglinAI implements EnemyAI {

    /** Neutral until the pack is provoked. */
    @Override
    public boolean isHostileThreat(CombatEntity self, GridArena arena, GridPos playerPos) {
        return self.isEnraged();
    }

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        // One provoked piglin riles the whole pack — in THIS arena only.
        if (!self.isEnraged() && anyPackmateProvoked(arena)) {
            enragePack(arena);
        }

        GridPos myPos = self.getGridPos();

        // Not aggro — wander peacefully
        if (!self.isEnraged()) {
            return AIUtils.wander(self, arena);
        }

        // Aggro! Calculate mob mentality bonus
        int dist = self.minDistanceTo(playerPos);

        // Adjacent — attack with pack bonus
        if (dist == 1) {
            return new EnemyAction.Attack(
                self.getAttackPower() + countNearbyAggroPiglins(arena, self, myPos));
        }

        // Rush toward player
        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, self.getMoveSpeed());
        if (target == null) target = playerPos;

        List<GridPos> path = Pathfinding.findPath(arena, myPos, target, self.getMoveSpeed(), self);
        if (path.isEmpty()) return AIUtils.seekOrWander(self, arena, playerPos);

        GridPos endPos = path.get(path.size() - 1);
        if (endPos.manhattanDistance(playerPos) == 1) {
            return new EnemyAction.MoveAndAttack(path,
                self.getAttackPower() + countNearbyAggroPiglins(arena, self, endPos));
        }
        return new EnemyAction.Move(path);
    }

    /** True when any enemy-side zombified piglin has been hurt or already riled. */
    private boolean anyPackmateProvoked(GridArena arena) {
        for (CombatEntity e : arena.getOccupants().values()) {
            if (!e.isAlive() || e.isAlly()) continue;
            if (!"minecraft:zombified_piglin".equals(e.getEntityTypeId())) continue;
            if (e.isEnraged() || e.wasDamagedSinceLastTurn() || e.getCurrentHp() < e.getMaxHp()) {
                return true;
            }
        }
        return false;
    }

    private void enragePack(GridArena arena) {
        for (CombatEntity e : arena.getOccupants().values()) {
            if (!e.isAlive() || e.isAlly()) continue;
            if ("minecraft:zombified_piglin".equals(e.getEntityTypeId())) {
                e.setEnraged(true);
            }
        }
    }

    /** +1 ATK per OTHER aggro'd enemy-side packmate within 2 tiles of {@code pos}. */
    private int countNearbyAggroPiglins(GridArena arena, CombatEntity self, GridPos pos) {
        int count = 0;
        for (CombatEntity other : arena.getOccupants().values()) {
            if (!other.isAlive() || other == self || other.isAlly()) continue;
            if (!other.getEntityTypeId().equals("minecraft:zombified_piglin")) continue;
            if (!other.isEnraged()) continue;
            if (pos.manhattanDistance(other.getGridPos()) <= 2) {
                count++;
            }
        }
        return count;
    }
}
