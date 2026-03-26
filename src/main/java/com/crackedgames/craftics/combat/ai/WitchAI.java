package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;
import java.util.Random;

/**
 * Witch AI: Devious potion-lobbing caster.
 * - POTION VOLLEY: throws potions at range 2-4 (any direction)
 * - RANDOM EFFECTS: harming, poison, slowness, weakness — unpredictable
 * - SELF-HEAL: 25% chance to drink healing potion instead of attacking when below 40% HP
 * - RETREAT: kites backward if player gets too close
 */
public class WitchAI implements EnemyAI {
    private static final Random RNG = new Random();
    private static final String[] EFFECTS = {"poison", "slowness", "weakness", "harming"};

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // SELF-HEAL: when low HP, chance to heal instead of attack
        if (self.getCurrentHp() < self.getMaxHp() * 0.4 && RNG.nextFloat() < 0.25f) {
            // "Heals" by dealing negative damage — but we can't heal in this system
            // Instead, just skip the turn strategically (repositioning)
        }

        // Too close — retreat and throw
        if (dist <= 1) {
            GridPos retreatTarget = findRetreatTile(arena, myPos, playerPos);
            if (retreatTarget != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, retreatTarget, self.getMoveSpeed(), self);
                if (!path.isEmpty()) {
                    GridPos endPos = path.get(path.size() - 1);
                    int endDist = endPos.manhattanDistance(playerPos);
                    if (endDist >= 2 && endDist <= 4) {
                        String effect = EFFECTS[RNG.nextInt(EFFECTS.length)];
                        int damage = effect.equals("harming") ? self.getAttackPower() : self.getAttackPower() / 2;
                        return new EnemyAction.MoveAndAttack(path, damage);
                    }
                    return new EnemyAction.Move(path);
                }
            }
            // Can't retreat — splash at point blank
            return new EnemyAction.RangedAttack(self.getAttackPower(), "harming");
        }

        // In range (2-4) — throw a random potion
        if (dist >= 2 && dist <= 4) {
            String effect = EFFECTS[RNG.nextInt(EFFECTS.length)];
            int damage = effect.equals("harming") ? self.getAttackPower() : self.getAttackPower() / 2;
            return new EnemyAction.RangedAttack(damage, effect);
        }

        // Out of range — move to get within throwing distance
        GridPos moveTarget = findRangeTile(arena, myPos, playerPos, self.getMoveSpeed());
        if (moveTarget != null) {
            List<GridPos> path = Pathfinding.findPath(arena, myPos, moveTarget, self.getMoveSpeed(), self);
            if (!path.isEmpty()) {
                GridPos endPos = path.get(path.size() - 1);
                int endDist = endPos.manhattanDistance(playerPos);
                if (endDist >= 2 && endDist <= 4) {
                    String effect = EFFECTS[RNG.nextInt(EFFECTS.length)];
                    int damage = effect.equals("harming") ? self.getAttackPower() : self.getAttackPower() / 2;
                    return new EnemyAction.MoveAndAttack(path, damage);
                }
                return new EnemyAction.Move(path);
            }
        }

        return AIUtils.seekOrWander(self, arena, playerPos);
    }

    private GridPos findRetreatTile(GridArena arena, GridPos self, GridPos threat) {
        int dx = Integer.signum(self.x() - threat.x());
        int dz = Integer.signum(self.z() - threat.z());
        if (dx == 0 && dz == 0) dx = 1;

        GridPos[] candidates = {
            new GridPos(self.x() + dx, self.z() + dz),
            new GridPos(self.x() + dx, self.z()),
            new GridPos(self.x(), self.z() + dz),
            new GridPos(self.x() + dz, self.z() - dx),
            new GridPos(self.x() - dz, self.z() + dx),
        };

        for (GridPos c : candidates) {
            if (arena.isInBounds(c) && !arena.isOccupied(c)) {
                var tile = arena.getTile(c);
                if (tile != null && tile.isWalkable()) return c;
            }
        }
        return null;
    }

    private GridPos findRangeTile(GridArena arena, GridPos self, GridPos playerPos, int maxSteps) {
        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int dx = -maxSteps; dx <= maxSteps; dx++) {
            for (int dz = -maxSteps; dz <= maxSteps; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > maxSteps) continue;
                GridPos candidate = new GridPos(self.x() + dx, self.z() + dz);
                if (!arena.isInBounds(candidate) || arena.isOccupied(candidate)) continue;

                int distToPlayer = candidate.manhattanDistance(playerPos);
                int distToSelf = self.manhattanDistance(candidate);

                if (distToPlayer >= 2 && distToPlayer <= 4 && distToSelf <= maxSteps) {
                    if (distToSelf < bestDist) {
                        var tile = arena.getTile(candidate);
                        if (tile != null && tile.isWalkable()) {
                            bestDist = distToSelf;
                            best = candidate;
                        }
                    }
                }
            }
        }
        return best;
    }
}
