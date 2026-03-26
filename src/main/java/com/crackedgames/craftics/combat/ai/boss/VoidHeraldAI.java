package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Outer End Islands Boss — "The Void Herald" (Enderman)
 * Entity: Enderman | 55HP / 10ATK / 3DEF / Speed 3 | Size 2×2
 *
 * Abilities:
 * - Void Gale: Push ALL entities 2 tiles toward nearest void edge. P2: 3 tiles.
 * - Lightning Strike: Mark tile → + pattern 6 dmg next turn. P2: 2 tiles.
 * - Platform Collapse: Permanently remove 2×2 floor → void. P2: auto every 2 turns.
 * - Blink Assault: Teleport + attack 3 tiles (player + 2 adjacent).
 *
 * Phase 2 — "Oblivion": Auto collapse every 2 turns, gale 3 tiles, 2 lightning marks,
 * speed 4, summon 2 Endermites every 3 turns.
 */
public class VoidHeraldAI extends BossAI {
    private static final String CD_GALE = "void_gale";
    private static final String CD_LIGHTNING = "lightning_strike";
    private static final String CD_COLLAPSE = "platform_collapse";
    private static final String CD_BLINK = "blink_assault";
    private static final String CD_MITES = "summon_endermites";

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        self.setSpeedBonus(1); // Speed 3 → 4
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Phase 2: Auto platform collapse every 2 turns
        if (isPhaseTwo() && getTurnCounter() % 2 == 0 && !isOnCooldown(CD_COLLAPSE)) {
            setCooldown(CD_COLLAPSE, 1);
            List<GridPos> collapseTiles = findCollapseTiles(arena, playerPos);
            if (!collapseTiles.isEmpty()) {
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.GROUND_CRACK,
                    collapseTiles, 1,
                    new EnemyAction.CreateTerrain(collapseTiles, TileType.VOID, 0),
                    0xFF440088);
                // Continue with another action via composite
            }
        }

        // Phase 2: Summon Endermites every 3 turns
        if (isPhaseTwo() && !isOnCooldown(CD_MITES) && getTurnCounter() % 3 == 0) {
            setCooldown(CD_MITES, 2);
            List<GridPos> mitePositions = findSummonPositionsNear(arena, playerPos, 1, 2);
            if (!mitePositions.isEmpty()) {
                return new EnemyAction.SummonMinions(
                    "minecraft:endermite", mitePositions.size(), mitePositions, 2, 2, 0);
            }
        }

        // Void Gale — push everything toward void
        if (!isOnCooldown(CD_GALE)) {
            setCooldown(CD_GALE, 3);
            int[] pushDir = findVoidDirection(arena, playerPos);
            int pushDist = isPhaseTwo() ? 3 : 2;
            List<GridPos> warningTiles = new ArrayList<>();
            warningTiles.add(playerPos);
            // Show directional warning
            for (int i = 1; i <= pushDist; i++) {
                GridPos p = new GridPos(playerPos.x() + pushDir[0] * i, playerPos.z() + pushDir[1] * i);
                if (arena.isInBounds(p)) warningTiles.add(p);
            }
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.DIRECTIONAL,
                warningTiles, 1,
                new EnemyAction.ForcedMovement(-1, pushDir[0], pushDir[1], pushDist),
                0xFF8800FF);
            return new EnemyAction.Idle();
        }

        // Lightning Strike
        if (!isOnCooldown(CD_LIGHTNING)) {
            setCooldown(CD_LIGHTNING, 2);
            List<GridPos> lightningTiles = new ArrayList<>();
            lightningTiles.add(playerPos);
            lightningTiles.addAll(getCrossTiles(arena, playerPos, 1));
            if (isPhaseTwo()) {
                // Second lightning target near first
                GridPos second = new GridPos(playerPos.x() + 2, playerPos.z());
                if (arena.isInBounds(second)) {
                    lightningTiles.add(second);
                    lightningTiles.addAll(getCrossTiles(arena, second, 1));
                }
            }
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                lightningTiles, 1,
                new EnemyAction.AreaAttack(playerPos, 1, 6, "lightning_strike"),
                0xFFFFFF00);
            return new EnemyAction.Idle();
        }

        // Platform Collapse — destroy floor
        if (!isOnCooldown(CD_COLLAPSE)) {
            setCooldown(CD_COLLAPSE, 4);
            List<GridPos> collapseTiles = findCollapseTiles(arena, playerPos);
            if (!collapseTiles.isEmpty()) {
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.GROUND_CRACK,
                    collapseTiles, 1,
                    new EnemyAction.CreateTerrain(collapseTiles, TileType.VOID, 0),
                    0xFF440088);
                return new EnemyAction.Idle();
            }
        }

        // Blink Assault — teleport + triple attack
        if (!isOnCooldown(CD_BLINK) && dist >= 2) {
            setCooldown(CD_BLINK, 2);
            List<GridPos> targets = new ArrayList<>();
            targets.add(playerPos);
            for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                GridPos adj = new GridPos(playerPos.x() + d[0], playerPos.z() + d[1]);
                if (arena.isInBounds(adj) && targets.size() < 3) targets.add(adj);
            }
            GridPos landNear = findSummonPositionsNear(arena, playerPos, 1, 1).isEmpty()
                ? myPos : findSummonPositionsNear(arena, playerPos, 1, 1).get(0);
            return new EnemyAction.CompositeAction(List.of(
                new EnemyAction.Teleport(landNear),
                new EnemyAction.AreaAttack(playerPos, 1, self.getAttackPower(), "blink_assault")
            ));
        }

        if (dist <= 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        return meleeOrApproach(self, arena, playerPos, 0);
    }

    private List<GridPos> findCollapseTiles(GridArena arena, GridPos nearPlayer) {
        // Find a 2×2 section near player but not directly under them
        List<GridPos> candidates = new ArrayList<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) continue; // Don't collapse right under player
                GridPos base = new GridPos(nearPlayer.x() + dx, nearPlayer.z() + dz);
                if (arena.isInBounds(base) && arena.getTile(base) != null
                        && arena.getTile(base).isWalkable()
                        && arena.getTile(base).getType() != TileType.VOID) {
                    candidates.add(base);
                }
            }
        }
        Collections.shuffle(candidates);
        List<GridPos> result = new ArrayList<>();
        if (!candidates.isEmpty()) {
            GridPos base = candidates.get(0);
            result.add(base);
            for (int[] d : new int[][]{{1,0},{0,1},{1,1}}) {
                GridPos p = new GridPos(base.x() + d[0], base.z() + d[1]);
                if (arena.isInBounds(p)) result.add(p);
            }
        }
        return result;
    }

    private int[] findVoidDirection(GridArena arena, GridPos playerPos) {
        // Find which void edge is nearest to the player
        int w = arena.getWidth(), h = arena.getHeight();
        int dLeft = playerPos.x();
        int dRight = w - 1 - playerPos.x();
        int dTop = playerPos.z();
        int dBottom = h - 1 - playerPos.z();

        int min = Math.min(Math.min(dLeft, dRight), Math.min(dTop, dBottom));
        if (min == dLeft) return new int[]{-1, 0};
        if (min == dRight) return new int[]{1, 0};
        if (min == dTop) return new int[]{0, -1};
        return new int[]{0, 1};
    }
}
