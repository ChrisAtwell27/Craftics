package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Shared brain for the undead horde family: relentless advance, attack on
 * arrival, never retreat — with a +1 attack HORDE BONUS per adjacent undead
 * packmate. {@link ZombieAI}, {@link ZombieVillagerAI} and {@link HuskAI}
 * subclass this and tune damage/speed through the hooks instead of each
 * carrying its own copy of the march-and-bite loop.
 */
public class UndeadHordeAI implements EnemyAI {

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        int speed = effectiveSpeed(self);

        // Adjacent — attack with horde bonus
        if (dist <= 1) {
            return new EnemyAction.Attack(damageAt(self, arena, myPos));
        }

        // Move toward the target and attack if we reach adjacency
        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, speed);
        if (target == null) target = playerPos;

        List<GridPos> path = Pathfinding.findPath(arena, myPos, target, speed, self);
        if (path.isEmpty()) {
            return AIUtils.seekOrWander(self, arena, playerPos);
        }

        GridPos endPos = path.get(path.size() - 1);
        if (endPos.manhattanDistance(playerPos) <= 1) {
            // Recalculate horde at the destination
            return new EnemyAction.MoveAndAttack(path, damageAt(self, arena, endPos));
        }
        return new EnemyAction.Move(path);
    }

    /** Attack power from a given tile: base + per-mob bonus + horde bonus. */
    private int damageAt(CombatEntity self, GridArena arena, GridPos pos) {
        return self.getAttackPower() + bonusDamage(self) + countAdjacentUndead(arena, self, pos);
    }

    /** Flat extra damage for the variant (husk hunger bite). */
    protected int bonusDamage(CombatEntity self) {
        return 0;
    }

    /** Movement budget for the turn (husk desperation, villager frenzy). */
    protected int effectiveSpeed(CombatEntity self) {
        return self.getMoveSpeed();
    }

    /** +1 attack per adjacent undead packmate (not counting ourselves). */
    private int countAdjacentUndead(GridArena arena, CombatEntity self, GridPos pos) {
        int count = 0;
        java.util.Set<CombatEntity> seen = new java.util.HashSet<>();
        for (CombatEntity other : arena.getOccupants().values()) {
            if (!other.isAlive() || other == self || other.isAlly()) continue;
            if (!seen.add(other)) continue;
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
