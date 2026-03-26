package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Warped Forest Boss — "The Void Walker" (Enderman)
 * Entity: Enderman | 50HP / 9ATK / 2DEF / Speed 3 (+ free teleports) | Size 2×2
 *
 * Abilities:
 * - Void Rift: Opens portal pair (step on one → teleported to other). 4 turns, max 2 pairs.
 * - Mirror Image: 2 clones (8HP/3ATK, take double damage). P2: 3 clones.
 * - Phase Strike: Teleport behind player + attack. Cannot be dodged.
 * - Void Pull: Pulls player 2 tiles toward boss. P2: 3 tiles.
 *
 * Phase 2 — "Reality Shatter": Permanent rifts, 3 clones, double phase strike,
 * random blink start of turn, void pull range 3.
 */
public class VoidWalkerAI extends BossAI {
    private static final String CD_RIFT = "void_rift";
    private static final String CD_MIRROR = "mirror_image";
    private static final String CD_STRIKE = "phase_strike";
    private static final String CD_PULL = "void_pull";
    private final List<GridPos[]> activeRifts = new ArrayList<>();

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        // Make existing rifts permanent
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Phase 2: Random blink at start of every turn
        if (isPhaseTwo() && getTurnCounter() > 1) {
            List<GridPos> blinkTargets = findSummonPositions(arena, 1);
            if (!blinkTargets.isEmpty()) {
                GridPos blinkTo = blinkTargets.get(0);
                // We'll teleport and then choose an ability
                EnemyAction ability = chooseOffensiveAbility(self, arena, playerPos, blinkTo);
                if (ability != null) {
                    return new EnemyAction.CompositeAction(List.of(
                        new EnemyAction.Teleport(blinkTo),
                        ability
                    ));
                }
                return new EnemyAction.Teleport(blinkTo);
            }
        }

        EnemyAction action = chooseOffensiveAbility(self, arena, playerPos, myPos);
        if (action != null) return action;

        return meleeOrApproach(self, arena, playerPos, 0);
    }

    private EnemyAction chooseOffensiveAbility(CombatEntity self, GridArena arena,
                                                GridPos playerPos, GridPos effectivePos) {
        int dist = effectivePos.manhattanDistance(playerPos);

        // Phase Strike — teleport behind and attack
        if (!isOnCooldown(CD_STRIKE) && dist >= 2) {
            setCooldown(CD_STRIKE, 2);
            GridPos behind = findTileBehindPlayer(arena, playerPos, effectivePos);
            if (behind != null) {
                if (isPhaseTwo()) {
                    // Double phase strike
                    GridPos secondPos = findTileBehindPlayer(arena, playerPos, behind);
                    if (secondPos != null) {
                        return new EnemyAction.CompositeAction(List.of(
                            new EnemyAction.TeleportAndAttack(behind, self.getAttackPower()),
                            new EnemyAction.TeleportAndAttack(secondPos, self.getAttackPower())
                        ));
                    }
                }
                return new EnemyAction.TeleportAndAttack(behind, self.getAttackPower());
            }
        }

        // Void Pull — pull player toward boss
        if (!isOnCooldown(CD_PULL) && dist >= 2 && dist <= 4) {
            setCooldown(CD_PULL, 2);
            int[] dir = getDirectionToward(playerPos, effectivePos);
            int pullDist = isPhaseTwo() ? 3 : 2;
            return new EnemyAction.ForcedMovement(-1, dir[0], dir[1], pullDist);
        }

        // Void Rift — create portal pairs
        if (!isOnCooldown(CD_RIFT) && activeRifts.size() < 2) {
            setCooldown(CD_RIFT, 3);
            List<GridPos> positions = findSummonPositions(arena, 2);
            if (positions.size() >= 2) {
                GridPos a = positions.get(0);
                GridPos b = positions.get(1);
                activeRifts.add(new GridPos[]{a, b});
                return new EnemyAction.BossAbility("void_rift",
                    new EnemyAction.Idle(), // Rifts are passive — managed by CombatManager
                    List.of(a, b));
            }
        }

        // Mirror Image — summon clones
        if (!isOnCooldown(CD_MIRROR) && getAliveMinionCount() == 0) {
            setCooldown(CD_MIRROR, 4);
            int count = isPhaseTwo() ? 3 : 2;
            List<GridPos> clonePositions = findSummonPositionsNear(arena, effectivePos, 3, count);
            if (!clonePositions.isEmpty()) {
                return new EnemyAction.SummonMinions(
                    "minecraft:enderman", clonePositions.size(), clonePositions, 8, 3, 0);
            }
        }

        // Adjacent melee
        if (dist <= 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        return null;
    }

    private GridPos findTileBehindPlayer(GridArena arena, GridPos playerPos, GridPos bossPos) {
        // "Behind" = opposite side of player from boss
        int[] dir = getDirectionToward(bossPos, playerPos);
        GridPos behind = new GridPos(playerPos.x() + dir[0], playerPos.z() + dir[1]);
        if (arena.isInBounds(behind) && !arena.isOccupied(behind)
                && arena.getTile(behind) != null && arena.getTile(behind).isWalkable()) {
            return behind;
        }
        // Try adjacent tiles if behind is blocked
        for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            GridPos alt = new GridPos(playerPos.x() + d[0], playerPos.z() + d[1]);
            if (arena.isInBounds(alt) && !arena.isOccupied(alt) && !alt.equals(bossPos)
                    && arena.getTile(alt) != null && arena.getTile(alt).isWalkable()) {
                return alt;
            }
        }
        return null;
    }

    public List<GridPos[]> getActiveRifts() { return activeRifts; }

    public void tickRifts() {
        // In Phase 2, rifts are permanent — don't remove
        // Pre-Phase 2: managed externally by turn counter
    }
}
