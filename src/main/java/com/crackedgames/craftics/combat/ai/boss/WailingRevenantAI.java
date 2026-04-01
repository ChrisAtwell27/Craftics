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
 * Soul Sand Valley Boss — "The Wailing Revenant" (Ghast)
 * Entity: Ghast | 60HP / 8ATK / 2DEF / Range 6 / Speed 0 (stationary) | Scale 2.0x
 *
 * UNIQUE BOSS MECHANIC: The ghast does NOT stand on the arena. It hovers outside the
 * arena's low-Z edge (back-right in the isometric SW camera view), scaled up to 2x size.
 * It never moves — it is a stationary artillery boss that rains attacks onto the arena.
 *
 * TARGETING: The entire front row (z=0) is registered as the ghast's hitbox. The player
 * can attack any tile along that row to damage the boss. These tiles remain walkable
 * (backgroundBoss flag) — the ghast doesn't block movement.
 *
 * SPAWNING: No regular ghasts spawn during this fight. Only wither skeletons appear
 * as minions alongside the boss.
 *
 * Abilities:
 * - Fireball Barrage (2-turn CD): Spawns 3 (P2: 5) fireball projectiles at z=1,
 *   flying +Z across the arena. Fireballs have 99HP (designed to be redirected, not killed).
 *   8 damage + burning on player contact. 1-tile AoE explosion on wall/player hit.
 * - Raining Fireballs (3-turn CD): Warns half the playable arena tiles with red overlay,
 *   then rains down fireballs next turn. 5 damage per tile, no AoE spread, applies burning.
 * - Magma Rows (3-turn CD): Warns 1 (P2: 2) random rows with ground-crack particles,
 *   then turns them to magma (fire tiles) for 2 turns.
 * - Summon Wither Skeletons (4-turn CD): Spawns 2 (P2: 3) wither skeletons (10HP/5ATK/1DEF).
 *   Max 4 alive at once.
 *
 * Phase 2 — "Requiem" (≤50% HP):
 * - Fireball Barrage: 5 fireballs (up from 3), max 10 alive (up from 6)
 * - Raining Fireballs: still half the arena
 * - Magma Rows: 2 rows (up from 1)
 * - Summon: 3 wither skeletons (up from 2)
 */
public class WailingRevenantAI extends BossAI {
    @Override public int getGridSize() { return 2; } // Overridden at runtime by CombatManager

    private static final String CD_BARRAGE = "fireball_barrage";
    private static final String CD_RAIN = "raining_fireballs";
    private static final String CD_SUMMON = "summon_skeletons";
    private static final String CD_MAGMA = "magma_rows";

    private static final int MAX_FIREBALLS_P1 = 6;
    private static final int MAX_FIREBALLS_P2 = 10;
    private static final int MAX_SKELETONS = 4;

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        int maxFireballs = isPhaseTwo() ? MAX_FIREBALLS_P2 : MAX_FIREBALLS_P1;

        // Priority 1: Fireball Barrage (every 2 turns)
        if (!isOnCooldown(CD_BARRAGE) && getAliveProjectileCount() < maxFireballs) {
            setCooldown(CD_BARRAGE, 2);
            int count = isPhaseTwo() ? 5 : 3;
            List<GridPos> spawnPositions = getFireballSpawnPositions(arena, count);
            if (!spawnPositions.isEmpty()) {
                List<int[]> directions = new ArrayList<>();
                for (int i = 0; i < spawnPositions.size(); i++) {
                    directions.add(new int[]{0, 1}); // Fireballs fly +Z (away from ghast, into arena)
                }
                EnemyAction spawnFireballs = new EnemyAction.SpawnProjectile(
                    "minecraft:blaze", spawnPositions, directions,
                    99, 8, 0, "ghast_fireball"
                );
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.GATHERING_PARTICLES,
                    spawnPositions, 1, spawnFireballs, 0xFF4488FF
                );
                return new EnemyAction.Idle();
            }
        }

        // Priority 2: Raining Fireballs (every 3 turns)
        if (!isOnCooldown(CD_RAIN)) {
            setCooldown(CD_RAIN, 3);
            int playableArea = arena.getWidth() * (arena.getHeight() - 1); // exclude boss row
            int tileCount = Math.max(1, playableArea / 2);
            List<GridPos> rainTargets = getRandomPlayableTiles(arena, tileCount);
            if (!rainTargets.isEmpty()) {
                List<EnemyAction> strikes = new ArrayList<>();
                for (GridPos tile : rainTargets) {
                    strikes.add(new EnemyAction.AreaAttack(tile, 0, 5, "raining_fireball"));
                }
                EnemyAction rainAction = new EnemyAction.CompositeAction(strikes);
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                    rainTargets, 1, rainAction, 0xFFFF4400
                );
                return new EnemyAction.Idle();
            }
        }

        // Priority 3: Magma Rows (every 3 turns)
        if (!isOnCooldown(CD_MAGMA)) {
            setCooldown(CD_MAGMA, 3);
            int rowCount = isPhaseTwo() ? 2 : 1;
            List<GridPos> magmaTiles = getRandomRows(arena, rowCount);
            if (!magmaTiles.isEmpty()) {
                EnemyAction magmaAction = new EnemyAction.CreateTerrain(magmaTiles, TileType.FIRE, 2);
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.GROUND_CRACK,
                    magmaTiles, 1, magmaAction, 0xFFFF6600
                );
                return new EnemyAction.Idle();
            }
        }

        // Priority 4: Summon Wither Skeletons (every 4 turns)
        if (!isOnCooldown(CD_SUMMON) && getAliveMinionCount() < MAX_SKELETONS) {
            setCooldown(CD_SUMMON, 4);
            int count = isPhaseTwo() ? 3 : 2;
            List<GridPos> spawnPositions = findSummonPositions(arena, count);
            if (!spawnPositions.isEmpty()) {
                EnemyAction summon = new EnemyAction.SummonMinions(
                    "minecraft:wither_skeleton", spawnPositions.size(), spawnPositions,
                    10, 5, 1
                );
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.GROUND_CRACK,
                    spawnPositions, 1, summon, 0xFF553300
                );
                return new EnemyAction.Idle();
            }
        }

        return new EnemyAction.Idle();
    }

    /**
     * Fireball spawn positions at z=1 (row just inside the front edge), spread across width.
     */
    private List<GridPos> getFireballSpawnPositions(GridArena arena, int count) {
        int w = arena.getWidth();
        int spawnZ = 1; // Row just inside the boss edge

        List<GridPos> candidates = new ArrayList<>();
        for (int x = 0; x < w; x++) {
            GridPos pos = new GridPos(x, spawnZ);
            if (arena.isInBounds(pos) && !arena.isOccupied(pos)
                    && arena.getTile(pos) != null && arena.getTile(pos).isWalkable()) {
                candidates.add(pos);
            }
        }
        if (candidates.isEmpty()) return candidates;

        Collections.shuffle(candidates);
        return candidates.subList(0, Math.min(count, candidates.size()));
    }

    /**
     * Random walkable tiles excluding the front row (boss row z=0).
     */
    private List<GridPos> getRandomPlayableTiles(GridArena arena, int count) {
        List<GridPos> candidates = new ArrayList<>();
        for (int x = 0; x < arena.getWidth(); x++) {
            for (int z = 1; z < arena.getHeight(); z++) { // skip z=0 boss row
                GridPos pos = new GridPos(x, z);
                if (arena.isInBounds(pos) && arena.getTile(pos) != null && arena.getTile(pos).isWalkable()) {
                    candidates.add(pos);
                }
            }
        }
        Collections.shuffle(candidates);
        return candidates.subList(0, Math.min(count, candidates.size()));
    }

    /**
     * Random full rows excluding row 0 (boss) and the last row.
     */
    private List<GridPos> getRandomRows(GridArena arena, int rowCount) {
        List<Integer> rowCandidates = new ArrayList<>();
        for (int z = 1; z < arena.getHeight() - 1; z++) {
            rowCandidates.add(z);
        }
        Collections.shuffle(rowCandidates);

        List<GridPos> tiles = new ArrayList<>();
        int picked = 0;
        for (int row : rowCandidates) {
            if (picked >= rowCount) break;
            tiles.addAll(getRowTiles(arena, row));
            picked++;
        }
        return tiles;
    }
}
