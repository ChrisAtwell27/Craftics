package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Witch AI: Devious potion-lobbing caster with rotating brews.
 * - ROTATING BREW: cycles through poison, slowness, weakness, harming turn by
 *   turn (per witch - the rotation lives in the entity's AI memory)
 * - SELF-HEAL: below 40% HP, 25% chance per turn to drink a healing potion
 *   (a real heal for half her attack power, min 3) while falling back
 * - RETREAT: kites backward if any threat (player or ally pet) gets too close
 * - Prefers to throw from range 2-4 and stays off hazard tiles
 */
public class WitchAI implements EnemyAI {
    private static final String BREW_INDEX = "witch_brew";
    private static final String[] OFFENSIVE_EFFECTS = {"poison", "slowness", "weakness", "harming"};

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Rotate brew each turn (state on the entity - the AI instance is shared)
        int brewIndex = self.getAiMemory(BREW_INDEX, 0);
        String currentBrew = OFFENSIVE_EFFECTS[brewIndex % OFFENSIVE_EFFECTS.length];
        self.setAiMemory(BREW_INDEX, brewIndex + 1);

        List<GridPos> threats = AIUtils.threatPositions(arena, playerPos);

        // SELF-HEAL: when low HP, chance to drink a healing potion. This is a
        // real heal (ModifySelf), not a wasted turn - but she still gives up
        // her throw to do it, so it stays a gamble for her.
        if (self.getCurrentHp() < self.getMaxHp() * 0.4
                && ThreadLocalRandom.current().nextFloat() < 0.25f) {
            int healAmount = Math.max(3, self.getAttackPower() / 2);
            return new EnemyAction.ModifySelf("heal", healAmount, 0);
        }

        // Too close - retreat and throw
        if (AIUtils.minThreatDistance(myPos, threats) <= 1) {
            GridPos retreatTarget = AIUtils.bestRetreatTile(self, arena, threats);
            if (retreatTarget != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, retreatTarget, self.getMoveSpeed(), self);
                if (!path.isEmpty()) {
                    GridPos endPos = path.get(path.size() - 1);
                    int endDist = endPos.manhattanDistance(playerPos);
                    if (endDist >= 2 && endDist <= 4) {
                        return new EnemyAction.MoveAndAttack(path, brewDamage(self, currentBrew));
                    }
                    return new EnemyAction.Move(path);
                }
            }
            // Can't retreat - splash at point blank. Still respects the brew, so a
            // poison flask does pure poison here too rather than a full-power hit.
            return new EnemyAction.RangedAttack(brewDamage(self, currentBrew), currentBrew);
        }

        // In range (2-4) - throw the current rotating brew
        if (dist >= 2 && dist <= 4) {
            return new EnemyAction.RangedAttack(brewDamage(self, currentBrew), currentBrew);
        }

        // Out of range - move to get within throwing distance
        GridPos moveTarget = findRangeTile(self, arena, playerPos, threats);
        if (moveTarget != null) {
            List<GridPos> path = Pathfinding.findPath(arena, myPos, moveTarget, self.getMoveSpeed(), self);
            if (!path.isEmpty()) {
                GridPos endPos = path.get(path.size() - 1);
                int endDist = endPos.manhattanDistance(playerPos);
                if (endDist >= 2 && endDist <= 4) {
                    return new EnemyAction.MoveAndAttack(path, brewDamage(self, currentBrew));
                }
                return new EnemyAction.Move(path);
            }
        }

        return AIUtils.seekOrWander(self, arena, playerPos);
    }

    /**
     * Impact damage of a thrown brew. Harming is an instant-damage potion, so it
     * lands at full power. Poison is just a thrown potion - it deals NO impact
     * damage; the poison applied on hit (Witch case in applyEnemyHitEffect) is the
     * whole point. The remaining debuff brews trade for half damage.
     */
    private int brewDamage(CombatEntity self, String brew) {
        return switch (brew) {
            case "harming" -> self.getAttackPower();
            case "poison" -> 0; // pure poison - no impact damage, it's just a potion
            default -> self.getAttackPower() / 2;
        };
    }

    /** A reachable throwing position at range 2-4 from the target, clear of threats and hazards. */
    private GridPos findRangeTile(CombatEntity self, GridArena arena, GridPos playerPos,
                                  List<GridPos> threats) {
        GridPos myPos = self.getGridPos();
        GridPos best = null;
        int bestScore = Integer.MIN_VALUE;

        for (GridPos candidate : Pathfinding.getReachableTiles(
                arena, myPos, self.getMoveSpeed(), self)) {
            if (candidate.equals(myPos)) continue;

            int distToPlayer = candidate.manhattanDistance(playerPos);
            if (distToPlayer < 2 || distToPlayer > 4) continue;

            int score = AIUtils.minThreatDistance(candidate, threats) * 5
                - myPos.manhattanDistance(candidate);
            if (AIUtils.isHazardTile(arena, candidate)) score -= 25;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }
}
