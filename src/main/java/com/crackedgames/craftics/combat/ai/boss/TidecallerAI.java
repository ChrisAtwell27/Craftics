package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.List;

/**
 * River Delta Boss — "The Tidecaller"
 * Entity: Drowned | 30HP / 5ATK / 2DEF / Speed 2 (3 on water) / Range 3 | Size 2×2
 *
 * Abilities:
 * - Tidal Wave: 2-tile-wide column floods with water, 3 turns. P2: 3-wide + permanent.
 * - Trident Storm: 3 tridents in spread, 4 dmg each. Target tiles shimmer blue.
 * - Riptide Charge: On water, charge 4 tiles, ATK+3, knockback 2.
 * - Call of the Deep: Summon 1–2 Drowned (9HP/3ATK) on water tiles. P2: 2–3 every 2 turns.
 *
 * Phase 2 — "Deluge": Half arena floods permanently, +2 ATK on water.
 */
public class TidecallerAI extends BossAI {
    private static final String CD_WAVE = "tidal_wave";
    private static final String CD_TRIDENT = "trident_storm";
    private static final String CD_RIPTIDE = "riptide_charge";
    private static final String CD_SUMMON = "call_deep";
    private boolean delugeDone = false;

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        // Flood half the arena — done once on transition
        if (!delugeDone) {
            delugeDone = true;
            // Flood the bottom half (lower z values)
            int floodRows = arena.getHeight() / 2;
            List<GridPos> floodTiles = new ArrayList<>();
            for (int z = 0; z < floodRows; z++) {
                for (int x = 0; x < arena.getWidth(); x++) {
                    GridPos pos = new GridPos(x, z);
                    if (arena.getTile(pos) != null
                            && arena.getTile(pos).getType() != TileType.OBSTACLE
                            && arena.getTile(pos).getType() != TileType.VOID) {
                        floodTiles.add(pos);
                    }
                }
            }
            // This will be executed via a CompositeAction from the warning system
        }
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        boolean onWater = arena.getTile(myPos) != null
            && arena.getTile(myPos).getType() == TileType.WATER;

        // Phase 2 first turn: flood the arena
        if (isPhaseTwo() && delugeDone && getTurnCounter() == turnCounterAtPhase2()) {
            int floodRows = arena.getHeight() / 2;
            List<GridPos> floodTiles = new ArrayList<>();
            for (int z = 0; z < floodRows; z++) {
                for (int x = 0; x < arena.getWidth(); x++) {
                    GridPos pos = new GridPos(x, z);
                    if (arena.isInBounds(pos) && arena.getTile(pos) != null
                            && arena.getTile(pos).getType() == TileType.NORMAL) {
                        floodTiles.add(pos);
                    }
                }
            }
            if (!floodTiles.isEmpty()) {
                return new EnemyAction.CreateTerrain(floodTiles, TileType.WATER, 0);
            }
        }

        // Call of the Deep
        int summonInterval = isPhaseTwo() ? 2 : 3;
        int summonCount = isPhaseTwo() ? 3 : 2;
        if (!isOnCooldown(CD_SUMMON) && getAliveMinionCount() < 4) {
            List<GridPos> waterTiles = getWaterTiles(arena);
            List<GridPos> emptyWater = new ArrayList<>();
            for (GridPos p : waterTiles) {
                if (!arena.isOccupied(p)) emptyWater.add(p);
            }
            java.util.Collections.shuffle(emptyWater);
            List<GridPos> positions = emptyWater.subList(0, Math.min(summonCount, emptyWater.size()));
            if (!positions.isEmpty()) {
                setCooldown(CD_SUMMON, summonInterval);
                EnemyAction summon = new EnemyAction.SummonMinions(
                    "minecraft:drowned", positions.size(), positions, 9, 3, 0);
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.GROUND_CRACK,
                    positions, 1, summon, 0xFF4488CC);
                return new EnemyAction.Idle();
            }
        }

        // Riptide Charge if on water and at range
        if (!isOnCooldown(CD_RIPTIDE) && onWater && dist >= 2 && dist <= 5) {
            int[] dir = getDirectionToward(myPos, playerPos);
            List<GridPos> chargePath = getChargePath(arena, myPos, dir[0], dir[1], 4);
            if (!chargePath.isEmpty()) {
                setCooldown(CD_RIPTIDE, 3);
                int chargeDmg = self.getAttackPower() + 3;
                EnemyAction riptide = new EnemyAction.CompositeAction(List.of(
                    new EnemyAction.LineAttack(myPos, dir[0], dir[1], chargePath.size(), chargeDmg),
                    new EnemyAction.ForcedMovement(-1, dir[0], dir[1], 2)
                ));
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.GATHERING_PARTICLES,
                    chargePath, 1, riptide, 0xFF2266AA);
                return new EnemyAction.Idle();
            }
        }

        // Tidal Wave
        if (!isOnCooldown(CD_WAVE)) {
            int waveWidth = isPhaseTwo() ? 3 : 2;
            int waveDuration = isPhaseTwo() ? 0 : 3;
            // Pick the column the player is on
            int targetCol = playerPos.x();
            List<GridPos> waveTiles = new ArrayList<>();
            for (int dx = 0; dx < waveWidth; dx++) {
                int col = targetCol + dx - waveWidth / 2;
                if (col >= 0 && col < arena.getWidth()) {
                    waveTiles.addAll(getColumnTiles(arena, col));
                }
            }
            setCooldown(CD_WAVE, 3);
            EnemyAction waveAction = new EnemyAction.CreateTerrain(waveTiles, TileType.WATER, waveDuration);
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                waveTiles, 1, waveAction, 0xFF3388CC);
            return new EnemyAction.Idle();
        }

        // Trident Storm at range
        if (!isOnCooldown(CD_TRIDENT) && dist >= 2 && dist <= 4) {
            setCooldown(CD_TRIDENT, 2);
            // 3 tridents at different tiles near the player
            List<GridPos> targets = new ArrayList<>();
            targets.add(playerPos);
            targets.add(new GridPos(playerPos.x() + 1, playerPos.z()));
            targets.add(new GridPos(playerPos.x() - 1, playerPos.z()));
            targets.removeIf(p -> !arena.isInBounds(p));
            EnemyAction tridentAction = new EnemyAction.AreaAttack(playerPos, 1, 4, "trident_storm");
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                targets, 1, tridentAction, 0xFF2299DD);
            return new EnemyAction.Idle();
        }

        return meleeOrApproach(self, arena, playerPos, isPhaseTwo() ? 2 : 0);
    }

    private int turnCounterAtPhase2() {
        // Return the turn counter when phase 2 was triggered (track via a field)
        return turnCounter; // approximate — executes on same turn
    }

    private List<GridPos> getWaterTiles(GridArena arena) {
        List<GridPos> tiles = new ArrayList<>();
        for (int x = 0; x < arena.getWidth(); x++) {
            for (int z = 0; z < arena.getHeight(); z++) {
                GridPos pos = new GridPos(x, z);
                if (arena.getTile(pos) != null && arena.getTile(pos).getType() == TileType.WATER) {
                    tiles.add(pos);
                }
            }
        }
        return tiles;
    }
}
