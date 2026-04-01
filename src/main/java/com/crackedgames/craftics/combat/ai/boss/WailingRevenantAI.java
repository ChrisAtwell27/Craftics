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
 * Entity: Ghast | 90HP / 10ATK / 3DEF / Range 6 / Speed 0 (stationary) | Scale 2.0x
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
 * Attack rotation: Uses turn counter mod 4 to cycle through attacks, ensuring all
 * abilities get used regularly. Falls through to any available attack if the slotted
 * one is on cooldown.
 *
 * Abilities:
 * - Fireball Barrage (P1: 2-turn CD, P2: 1-turn): 3 (P2: 5) fireball projectiles at z=1.
 * - Raining Fireballs (P1: 3-turn CD, P2: 2-turn): Half the arena warned, 5 (P2: 7) dmg each.
 * - Magma Rows (P1: 3-turn CD, P2: 2-turn): 1 (P2: 3) rows turn to magma for 2 turns.
 * - Summon Wither Skeletons (P1: 4-turn CD, P2: 3-turn): 2 (P2: 3), max 4 (P2: 6).
 *
 * Phase 2 — "Requiem" (≤50% HP): Faster cooldowns, more fireballs, higher rain damage,
 * more magma rows, more skeletons.
 */
public class WailingRevenantAI extends BossAI {
    @Override public int getGridSize() { return 2; } // Overridden at runtime by CombatManager

    private static final String CD_BARRAGE = "fireball_barrage";
    private static final String CD_RAIN = "raining_fireballs";
    private static final String CD_SUMMON = "summon_skeletons";
    private static final String CD_MAGMA = "magma_rows";

    private static final int MAX_FIREBALLS_P1 = 8;
    private static final int MAX_FIREBALLS_P2 = 12;
    private static final int MAX_SKELETONS_P1 = 4;
    private static final int MAX_SKELETONS_P2 = 6;

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        int maxFireballs = isPhaseTwo() ? MAX_FIREBALLS_P2 : MAX_FIREBALLS_P1;
        int maxSkeletons = isPhaseTwo() ? MAX_SKELETONS_P2 : MAX_SKELETONS_P1;

        // Rotate through attack types so all abilities get used.
        // Try the slotted attack first, then fall through to any available.
        int slot = getTurnCounter() % 4; // 0=barrage, 1=rain, 2=magma, 3=summon

        EnemyAction action = switch (slot) {
            case 0 -> tryBarrage(self, arena, maxFireballs);
            case 1 -> tryRain(self, arena);
            case 2 -> tryMagma(self, arena);
            case 3 -> trySummon(self, arena, maxSkeletons);
            default -> null;
        };

        // If the slotted attack couldn't fire, try the others in order
        if (action == null) action = tryBarrage(self, arena, maxFireballs);
        if (action == null) action = tryRain(self, arena);
        if (action == null) action = tryMagma(self, arena);
        if (action == null) action = trySummon(self, arena, maxSkeletons);

        return action != null ? action : new EnemyAction.Idle();
    }

    private EnemyAction tryBarrage(CombatEntity self, GridArena arena, int maxFireballs) {
        if (isOnCooldown(CD_BARRAGE) || getAliveProjectileCount() >= maxFireballs) return null;
        setCooldown(CD_BARRAGE, isPhaseTwo() ? 1 : 2);
        int count = isPhaseTwo() ? 5 : 3;
        List<GridPos> spawnPositions = getFireballSpawnPositions(arena, count);
        if (spawnPositions.isEmpty()) return null;
        List<int[]> directions = new ArrayList<>();
        for (int i = 0; i < spawnPositions.size(); i++) {
            directions.add(new int[]{0, 1}); // fly +Z into the arena
        }
        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.GATHERING_PARTICLES,
            spawnPositions, 1,
            new EnemyAction.SpawnProjectile("minecraft:blaze", spawnPositions, directions, 99, 10, 0, "ghast_fireball"),
            0xFF4488FF
        );
        return new EnemyAction.Idle();
    }

    private EnemyAction tryRain(CombatEntity self, GridArena arena) {
        if (isOnCooldown(CD_RAIN)) return null;
        setCooldown(CD_RAIN, isPhaseTwo() ? 2 : 3);
        int playableArea = arena.getWidth() * (arena.getHeight() - 1);
        int tileCount = Math.max(1, playableArea / 2);
        List<GridPos> rainTargets = getRandomPlayableTiles(arena, tileCount);
        if (rainTargets.isEmpty()) return null;
        List<EnemyAction> strikes = new ArrayList<>();
        for (GridPos tile : rainTargets) {
            strikes.add(new EnemyAction.AreaAttack(tile, 0, isPhaseTwo() ? 7 : 5, "raining_fireball"));
        }
        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
            rainTargets, 1, new EnemyAction.CompositeAction(strikes), 0xFFFF4400
        );
        return new EnemyAction.Idle();
    }

    private EnemyAction tryMagma(CombatEntity self, GridArena arena) {
        if (isOnCooldown(CD_MAGMA)) return null;
        setCooldown(CD_MAGMA, isPhaseTwo() ? 2 : 3);
        int rowCount = isPhaseTwo() ? 3 : 1;
        List<GridPos> magmaTiles = getRandomRows(arena, rowCount);
        if (magmaTiles.isEmpty()) return null;
        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.GROUND_CRACK,
            magmaTiles, 1, new EnemyAction.CreateTerrain(magmaTiles, TileType.FIRE, 2), 0xFFFF6600
        );
        return new EnemyAction.Idle();
    }

    private EnemyAction trySummon(CombatEntity self, GridArena arena, int maxSkeletons) {
        if (isOnCooldown(CD_SUMMON) || getAliveMinionCount() >= maxSkeletons) return null;
        setCooldown(CD_SUMMON, isPhaseTwo() ? 3 : 4);
        int count = isPhaseTwo() ? 3 : 2;
        List<GridPos> spawnPositions = findSummonPositions(arena, count);
        if (spawnPositions.isEmpty()) return null;
        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.GROUND_CRACK,
            spawnPositions, 1,
            new EnemyAction.SummonMinions("minecraft:wither_skeleton", spawnPositions.size(), spawnPositions, 12, 6, 2),
            0xFF553300
        );
        return new EnemyAction.Idle();
    }

    /**
     * Fireball spawn positions at z=1 (row just inside the front edge), spread across width.
     */
    private List<GridPos> getFireballSpawnPositions(GridArena arena, int count) {
        int w = arena.getWidth();
        List<GridPos> candidates = new ArrayList<>();
        for (int x = 0; x < w; x++) {
            GridPos pos = new GridPos(x, 1);
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
            for (int z = 1; z < arena.getHeight(); z++) {
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
