package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Zombified Piglin AI: Neutral pack mob with mob mentality.
 * - NEUTRAL: wanders peacefully until ANY zombified piglin is hit
 * - PACK AGGRO: when one is hit, ALL zombified piglins permanently aggro
 * - MOB MENTALITY: +1 ATK per nearby aggro'd zombified piglin (radius 2)
 * - Once aggro'd, relentless melee like zombie but with pack bonuses
 */
public class ZombifiedPiglinAI implements EnemyAI {
    /** Shared across all instances — once true, all piglins are hostile. */
    private static boolean packAggro = false;

    /** Called when any zombified piglin takes damage. */
    public static void triggerPackAggro() { packAggro = true; }
    public static void resetPackAggro() { packAggro = false; }
    public static boolean isPackAggro() { return packAggro; }

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        // Check if this piglin was hit — trigger pack aggro
        if (self.wasDamagedSinceLastTurn()) {
            triggerPackAggro();
        }

        GridPos myPos = self.getGridPos();

        // Not aggro — wander peacefully
        if (!packAggro) {
            return AIUtils.wander(self, arena);
        }

        // Aggro! Calculate mob mentality bonus
        int bonus = countNearbyAggroPiglins(arena, myPos);
        int damage = self.getAttackPower() + bonus;
        int dist = self.minDistanceTo(playerPos);

        // Adjacent — attack with pack bonus
        if (dist == 1) {
            return new EnemyAction.Attack(damage);
        }

        // Rush toward player
        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, self.getMoveSpeed());
        if (target == null) target = playerPos;

        List<GridPos> path = Pathfinding.findPath(arena, myPos, target, self.getMoveSpeed(), self);
        if (path.isEmpty()) return AIUtils.seekOrWander(self, arena, playerPos);

        GridPos endPos = path.get(path.size() - 1);
        if (endPos.manhattanDistance(playerPos) == 1) {
            int destBonus = countNearbyAggroPiglins(arena, endPos);
            return new EnemyAction.MoveAndAttack(path, self.getAttackPower() + destBonus);
        }
        return new EnemyAction.Move(path);
    }

    private int countNearbyAggroPiglins(GridArena arena, GridPos pos) {
        int count = 0;
        for (var entry : arena.getOccupants().entrySet()) {
            CombatEntity other = entry.getValue();
            if (!other.isAlive()) continue;
            if (!other.getEntityTypeId().equals("minecraft:zombified_piglin")) continue;
            if (pos.manhattanDistance(other.getGridPos()) <= 2 && !pos.equals(other.getGridPos())) {
                count++;
            }
        }
        return count;
    }
}
