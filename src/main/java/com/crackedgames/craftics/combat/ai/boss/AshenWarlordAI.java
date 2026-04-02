package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.List;

/**
 * Basalt Deltas Boss — "The Ashen Warlord" (Wither Skeleton)
 * Entity: Wither Skeleton | 55HP / 10ATK / 4DEF / Speed 3 | Size 2×2
 *
 * Abilities:
 * - Wither Slash: Melee ATK + Wither (max HP reduced by 3 permanently). Stacks.
 *   P2: 180° arc (3 tiles in front).
 * - Summon Blaze Guard: 2 Blazes (10HP/5ATK/Range 4). Every 4 turns, max 3.
 *   P2: Wither Skeletons (12HP/6ATK, also apply Wither) instead.
 * - Ash Brand: Marks the player's row and column, then scorches both lanes.
 * - Fire Pillar: Forward line strike. P2 adds mirrored reverse lane.
 *
 * Phase 2 — "Warlord's Command": Arc wither slash, wither skeletons, speed 4,
 * double wither stacking, X-pattern fire pillar.
 */
public class AshenWarlordAI extends BossAI {
    private static final String CD_SUMMON = "summon_guard";
    private static final String CD_BRAND = "ash_brand";
    private static final String CD_PILLAR = "fire_pillar";

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        self.setSpeedBonus(1); // Speed 3 → 4
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Ash Brand — lane-control signature move.
        if (!isOnCooldown(CD_BRAND) && dist <= 6) {
            setCooldown(CD_BRAND, isPhaseTwo() ? 2 : 3);
            List<GridPos> brandTiles = getBrandTiles(arena, playerPos);
            if (!brandTiles.isEmpty()) {
                int burnDmg = isPhaseTwo() ? 6 : 4;
                EnemyAction brand = new EnemyAction.CompositeAction(List.of(
                    new EnemyAction.AreaAttack(playerPos, 0, burnDmg, "burning"),
                    new EnemyAction.CreateTerrain(brandTiles, TileType.FIRE, 2)
                ));
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                    brandTiles, 1, brand, 0xFFCC7722);
                return new EnemyAction.Idle();
            }
        }

        // Summon Blaze Guard / Wither Skeleton Guard
        if (!isOnCooldown(CD_SUMMON) && getAliveMinionCount() < 3) {
            setCooldown(CD_SUMMON, 4);
            List<GridPos> spawnPositions = findSummonPositions(arena, 2);
            if (!spawnPositions.isEmpty()) {
                if (isPhaseTwo()) {
                    return new EnemyAction.SummonMinions(
                        "minecraft:wither_skeleton", spawnPositions.size(), spawnPositions, 12, 6, 0);
                } else {
                    return new EnemyAction.SummonMinions(
                        "minecraft:blaze", spawnPositions.size(), spawnPositions, 10, 5, 0);
                }
            }
        }

        // Fire Pillar
        if (!isOnCooldown(CD_PILLAR) && dist <= 5) {
            setCooldown(CD_PILLAR, 2);
            int[] dir = getDirectionToward(myPos, playerPos);
            List<GridPos> pillarTiles = getLineTiles(arena, myPos, dir[0], dir[1], 4);
            EnemyAction pillarAction;
            if (isPhaseTwo()) {
                List<GridPos> xTiles = new ArrayList<>(pillarTiles);
                xTiles.addAll(getLineTiles(arena, myPos, -dir[0], -dir[1], 4));
                pillarAction = new EnemyAction.CompositeAction(List.of(
                    new EnemyAction.LineAttack(myPos, dir[0], dir[1], 4, 6),
                    new EnemyAction.LineAttack(myPos, -dir[0], -dir[1], 4, 6)
                ));
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                    xTiles, 1, pillarAction, 0xFFFF6600);
            } else {
                pillarAction = new EnemyAction.LineAttack(myPos, dir[0], dir[1], 4, 6);
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                    pillarTiles, 1, pillarAction, 0xFFFF6600);
            }
            return new EnemyAction.Idle();
        }

        // Wither Slash — melee attack
        if (dist <= 1) {
            if (isPhaseTwo()) {
                // 180° arc — attack 3 tiles in front
                int[] dir = getDirectionToward(myPos, playerPos);
                List<GridPos> arcTiles = new ArrayList<>();
                arcTiles.add(new GridPos(myPos.x() + dir[0], myPos.z() + dir[1]));
                // Two perpendicular tiles
                arcTiles.add(new GridPos(myPos.x() + dir[0] + dir[1], myPos.z() + dir[1] + dir[0]));
                arcTiles.add(new GridPos(myPos.x() + dir[0] - dir[1], myPos.z() + dir[1] - dir[0]));
                arcTiles.removeIf(p -> !arena.isInBounds(p));
                int witherAmount = 4; // Phase 2: double wither
                return new EnemyAction.CompositeAction(List.of(
                    new EnemyAction.AreaAttack(myPos, 1, self.getAttackPower(), "wither_slash"),
                    new EnemyAction.BossAbility("wither_applied", new EnemyAction.Idle(), arcTiles)
                ));
            }
            // Standard wither slash: ATK damage + wither 3
            return new EnemyAction.BossAbility("wither_slash",
                new EnemyAction.Attack(self.getAttackPower()),
                List.of(playerPos));
        }

        return meleeOrApproach(self, arena, playerPos, 0);
    }

    private List<GridPos> getBrandTiles(GridArena arena, GridPos playerPos) {
        List<GridPos> tiles = new ArrayList<>();
        tiles.addAll(getRowTiles(arena, playerPos.z()));
        tiles.addAll(getColumnTiles(arena, playerPos.x()));
        tiles.removeIf(p -> !arena.isInBounds(p));
        return tiles.stream().distinct().toList();
    }
}
