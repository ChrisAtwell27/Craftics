package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.List;

/**
 * Dark Forest Boss — "The Hexweaver" (Woodland Witch)
 * Entity: Evoker | 28HP / 5ATK / 2DEF / Range 4 / Speed 2 | Size 2×2
 *
 * Abilities:
 * - Hex Snare: telegraphed curse bolt that pulls player 2 tiles toward boss.
 * - Runic Prison: telegraphed cardinal runes around player, then erupts and cages briefly.
 * - Vex Swarm: Summons 2 Vexes (3HP/2ATK). P2: 3 Vexes, cap 6.
 * - Fang Line: 5-tile line toward player, 4 dmg each. P2: full cross burst.
 * - Cursed Fog: 3×3 debuff cloud centered on player.
 * - Hex Bolt: Ranged ATK, 1-turn Slowness.
 *
 * Phase 2 — "Arcane Fury": Teleports when adjacent, cross fangs, stronger vex swarm.
 */
public class HexweaverAI extends BossAI {
    private static final String CD_VEX = "vex_swarm";
    private static final String CD_FANG = "fang_line";
    private static final String CD_FOG = "cursed_fog";
    private static final String CD_SNARE = "hex_snare";
    private static final String CD_PRISON = "runic_prison";

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

        // Hex Snare: telegraphed pull to punish overextension.
        if (!isOnCooldown(CD_SNARE) && dist >= 2 && dist <= 5) {
            EnemyAction snare = castHexSnare(self, arena, myPos, playerPos);
            if (snare != null) {
                return snare;
            }
        }

        // Runic Prison: lock nearby lanes around the player after warning.
        if (!isOnCooldown(CD_PRISON) && dist <= 5) {
            EnemyAction prison = castRunicPrison(self, arena, playerPos);
            if (prison != null) {
                return prison;
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
                    List<GridPos> crossTiles = getCrossTiles(arena, myPos, 5);
                    EnemyAction combined = new EnemyAction.CompositeAction(List.of(
                        new EnemyAction.LineAttack(myPos, 1, 0, 5, 4),
                        new EnemyAction.LineAttack(myPos, -1, 0, 5, 4),
                        new EnemyAction.LineAttack(myPos, 0, 1, 5, 4),
                        new EnemyAction.LineAttack(myPos, 0, -1, 5, 4)
                    ));
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

    private EnemyAction castHexSnare(CombatEntity self, GridArena arena, GridPos myPos, GridPos playerPos) {
        int[] pullDir = getDirectionToward(playerPos, myPos);
        if (pullDir[0] == 0 && pullDir[1] == 0) return null;

        List<GridPos> warnTiles = new ArrayList<>();
        warnTiles.add(playerPos);
        warnTiles.addAll(getLineTiles(arena, playerPos, pullDir[0], pullDir[1], 2));

        EnemyAction resolve = new EnemyAction.CompositeAction(List.of(
            new EnemyAction.AreaAttack(playerPos, 0, 3, "hex_snare"),
            new EnemyAction.ForcedMovement(-1, pullDir[0], pullDir[1], 2)
        ));

        setCooldown(CD_SNARE, 3);
        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.DIRECTIONAL,
            warnTiles, 1, resolve, 0xFFAA55FF);
        return new EnemyAction.Idle();
    }

    private EnemyAction castRunicPrison(CombatEntity self, GridArena arena, GridPos playerPos) {
        List<GridPos> runeTiles = new ArrayList<>();
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            GridPos p = new GridPos(playerPos.x() + d[0], playerPos.z() + d[1]);
            if (arena.isInBounds(p)) {
                runeTiles.add(p);
            }
        }
        if (runeTiles.size() < 2) return null;

        List<EnemyAction> effects = new ArrayList<>();
        for (GridPos rune : runeTiles) {
            effects.add(new EnemyAction.AreaAttack(rune, 0, 3, "cursed_fog"));
        }
        effects.add(new EnemyAction.CreateTerrain(runeTiles, TileType.OBSTACLE, 2));

        setCooldown(CD_PRISON, 4);
        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
            runeTiles, 1, new EnemyAction.CompositeAction(effects), 0xFF7A2BAA);
        return new EnemyAction.Idle();
    }
}
