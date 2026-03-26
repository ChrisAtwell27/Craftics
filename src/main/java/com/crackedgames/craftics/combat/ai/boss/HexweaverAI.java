package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Dark Forest Boss — "The Hexweaver" (Woodland Witch)
 * Entity: Evoker | 28HP / 5ATK / 2DEF / Range 4 / Speed 2 | Size 2×2
 *
 * Abilities:
 * - Vex Swarm: Summons 2 Vexes (3HP/2ATK). P2: 3 Vexes, cap 6. Every 3 turns.
 * - Fang Line: 5-tile line toward player, 4 dmg each. P2: cross (both cardinal).
 * - Cursed Fog: 3×3 cloud, enemies +3 DEF, player -2 ATK. 3 turns. P2: +2 dmg/turn.
 * - Hex Bolt: Ranged ATK, 1-turn Slowness.
 *
 * Phase 2 — "Arcane Fury": Teleports when adjacent, cross fangs, stronger vex swarm.
 */
public class HexweaverAI extends BossAI {
    private static final String CD_VEX = "vex_swarm";
    private static final String CD_FANG = "fang_line";
    private static final String CD_FOG = "cursed_fog";

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        int vexCap = isPhaseTwo() ? 6 : 4;

        // Phase 2: Teleport away if player is adjacent (once per turn)
        if (isPhaseTwo() && dist <= 1) {
            GridPos fleeTarget = AIUtils.getFleeTarget(arena, myPos, playerPos, 3);
            if (fleeTarget != null) {
                return new EnemyAction.Teleport(fleeTarget);
            }
        }

        // Vex Swarm every 3 turns
        if (!isOnCooldown(CD_VEX) && getAliveMinionCount() < vexCap) {
            int count = isPhaseTwo() ? 3 : 2;
            List<GridPos> positions = findSummonPositionsNear(arena, myPos, 3, count);
            if (!positions.isEmpty()) {
                setCooldown(CD_VEX, 3);
                EnemyAction summon = new EnemyAction.SummonMinions(
                    "minecraft:vex", positions.size(), positions, 3, 2, 0);
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.GATHERING_PARTICLES,
                    List.of(myPos), 1, summon, 0xFF88FF88);
                return new EnemyAction.Idle();
            }
        }

        // Fang Line if player is within range on a cardinal axis
        if (!isOnCooldown(CD_FANG) && dist >= 2 && dist <= 5) {
            int[] dir = getDirectionToward(myPos, playerPos);
            List<GridPos> fangTiles = getLineTiles(arena, myPos, dir[0], dir[1], 5);
            if (!fangTiles.isEmpty()) {
                setCooldown(CD_FANG, 2);
                if (isPhaseTwo()) {
                    // Cross pattern: both perpendicular directions too
                    List<GridPos> crossTiles = new ArrayList<>(fangTiles);
                    crossTiles.addAll(getLineTiles(arena, myPos, -dir[0], -dir[1], 5));
                    EnemyAction fangAttack = new EnemyAction.LineAttack(myPos, dir[0], dir[1], 5, 4);
                    EnemyAction reverseFang = new EnemyAction.LineAttack(myPos, -dir[0], -dir[1], 5, 4);
                    EnemyAction combined = new EnemyAction.CompositeAction(List.of(fangAttack, reverseFang));
                    pendingWarning = new BossWarning(
                        self.getEntityId(), BossWarning.WarningType.GROUND_CRACK,
                        crossTiles, 1, combined, 0xFF664422);
                } else {
                    EnemyAction fangAttack = new EnemyAction.LineAttack(myPos, dir[0], dir[1], 5, 4);
                    pendingWarning = new BossWarning(
                        self.getEntityId(), BossWarning.WarningType.GROUND_CRACK,
                        fangTiles, 1, fangAttack, 0xFF664422);
                }
                return new EnemyAction.Idle();
            }
        }

        // Cursed Fog if player is in range
        if (!isOnCooldown(CD_FOG) && dist <= 5) {
            List<GridPos> fogTiles = getAreaTiles(arena, playerPos, 1); // 3×3 centered on player
            setCooldown(CD_FOG, 4);
            // Fog is CreateTerrain with a special effect (handled by CombatManager)
            EnemyAction fogAction = new EnemyAction.AreaAttack(playerPos, 1, 0, "cursed_fog");
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.GATHERING_PARTICLES,
                fogTiles, 1, fogAction, 0xFF442266);
            return new EnemyAction.Idle();
        }

        // Hex Bolt: ranged attack if in range 4
        if (dist >= 2 && dist <= 4) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "hex_bolt");
        }

        // Flee or kite if too close
        if (dist <= 2) {
            GridPos fleeTarget = AIUtils.getFleeTarget(arena, myPos, playerPos, self.getMoveSpeed());
            if (fleeTarget != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, fleeTarget, self.getMoveSpeed(), self);
                if (!path.isEmpty()) {
                    return new EnemyAction.Flee(path);
                }
            }
        }

        return meleeOrApproach(self, arena, playerPos, 0);
    }
}
