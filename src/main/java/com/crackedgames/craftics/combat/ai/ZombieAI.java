package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Zombie AI: Relentless horde fighter. Always moves + attacks in the same turn.
 * - HORDE BONUS: +1 attack power for each adjacent allied zombie/husk/drowned
 * - Beelines toward player with full speed, attacks on arrival
 * - Never retreats, never wastes a turn — always advancing
 */
public class ZombieAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Horde bonus: count adjacent undead allies
        int hordePower = self.getAttackPower() + countAdjacentUndead(arena, myPos);

        // Adjacent — attack with horde bonus
        if (dist == 1) {
            return new EnemyAction.Attack(hordePower);
        }

        // Move toward player and attack if we reach adjacency
        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, self.getMoveSpeed());
        if (target == null) target = playerPos;

        List<GridPos> path = Pathfinding.findPath(arena, myPos, target, self.getMoveSpeed(), self);
        if (path.isEmpty()) {
            return AIUtils.seekOrWander(self, arena, playerPos);
        }

        GridPos endPos = path.get(path.size() - 1);
        if (endPos.manhattanDistance(playerPos) == 1) {
            // Recalculate horde at destination
            int destHorde = self.getAttackPower() + countAdjacentUndead(arena, endPos);
            return new EnemyAction.MoveAndAttack(path, destHorde);
        }

        return new EnemyAction.Move(path);
    }

    private int countAdjacentUndead(GridArena arena, GridPos pos) {
        int count = 0;
        for (var entry : arena.getOccupants().entrySet()) {
            CombatEntity other = entry.getValue();
            if (!other.isAlive()) continue;
            String type = other.getEntityTypeId();
            boolean isUndead = type.equals("minecraft:zombie") || type.equals("minecraft:husk")
                || type.equals("minecraft:drowned") || type.equals("minecraft:zombie_villager");
            if (isUndead && pos.manhattanDistance(other.getGridPos()) == 1) {
                count++;
            }
        }
        return count;
    }
}
