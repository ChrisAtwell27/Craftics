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
 * Soul Sand Valley Boss - "The Wailing Revenant" (Ghast)
 * Entity: Ghast | 90HP / 10ATK / 3DEF / Range 6 / Speed 0 (stationary) | Scale 2.0x
 *
 * UNIQUE BOSS MECHANIC: The ghast does NOT stand on the arena. It hovers outside the
 * arena's low-Z edge (back-right in the isometric SW camera view), scaled up to 2x size.
 * It never moves - it is a stationary artillery boss that rains attacks onto the arena.
 *
 * TARGETING: The entire front row (z=0) is registered as the ghast's hitbox. The player
 * can attack any tile along that row to damage the boss. These tiles remain walkable
 * (backgroundBoss flag) - the ghast doesn't block movement.
 *
 * SPAWNING: No regular ghasts spawn during this fight. Only wither skeletons appear
 * as minions alongside the boss.
 *
 * Attack rotation: Strict round-robin over available attacks. The boss remembers
 * the next attack slot and advances after each successful queue, preventing
 * priority bias toward early checks.
 *
 * Abilities:
 * - Fireball Barrage (P1: 2-turn CD, P2: 1-turn): 3 (P2: 5) fireball projectiles at z=1.
 * - Raining Fireballs (P1: 3-turn CD, P2: 2-turn): Half the arena warned, 5 (P2: 7) dmg each.
 * - Soul Ember (P1: 3-turn CD, P2: 2-turn): drops 1 (P2: 3) burning embers on random tiles.
 *   Each is a SINGLE tile that catches and is then left to the arena - soul sand and soul
 *   soil are both fuel AND a vanilla soul base, so an ember lit here comes up blue on its own
 *   and the burn cycle carries it outward a ring per turn. The valley floor also restores
 *   after burning instead of scarring to ash, so the fire sweeps the arena and the arena
 *   grows back, which is what makes a seed-and-spread attack fair to drop here.
 * - Summon Wither Skeletons (P1: 4-turn CD, P2: 3-turn): 2 (P2: 3), max 4 (P2: 6).
 *
 * Phase 2 - "Requiem" (≤50% HP): Faster cooldowns, more fireballs, higher rain damage,
 * more magma rows, more skeletons.
 */
public class WailingRevenantAI extends BossAI {
    @Override public int getGridSize() { return 2; } // Overridden at runtime by CombatManager

    /**
     * The ghast never moves, not even on a telegraph turn.
     *
     * <p>From the fourth biome onward CombatManager stops letting a boss idle through the
     * turn it spends charging a warning, and asks it for a movement action to run alongside
     * the telegraph. The inherited answer is "walk toward the player", which for this boss
     * is catastrophic: it hovers OUTSIDE the arena and is hand-registered onto the whole
     * front row, with a sentinel gridPos of (0, 0) that is not where it is. Walking it moved
     * the mob onto the arena floor in the corner, so from its second turn on the stationary
     * artillery boss was standing on the stage next to you, meleeable, with its reflected
     * fireball counterplay reduced to a formality. Soul sand valley is well past that
     * ordinal, so this fired every single fight.
     */
    @Override
    public EnemyAction getChargingAdvanceAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        return new EnemyAction.Idle();
    }

    private static final int ATTACK_COUNT = 4;
    private int nextAttackIndex = 0;

    private static final String CD_BARRAGE = "fireball_barrage";
    private static final String CD_RAIN = "raining_fireballs";
    private static final String CD_SUMMON = "summon_skeletons";
    private static final String CD_MAGMA = "soul_ember";

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

        // Round-robin from the next slot; take the first attack that is currently usable.
        for (int i = 0; i < ATTACK_COUNT; i++) {
            int slot = (nextAttackIndex + i) % ATTACK_COUNT;
            EnemyAction action = switch (slot) {
                case 0 -> tryBarrage(self, arena, maxFireballs);
                case 1 -> tryRain(self, arena);
                case 2 -> tryMagma(self, arena);
                case 3 -> trySummon(self, arena, maxSkeletons);
                default -> null;
            };

            if (action != null) {
                nextAttackIndex = (slot + 1) % ATTACK_COUNT;
                return action;
            }
        }

        // Every slot is cooling or capped - spit a plain fireball at the player
        // rather than wailing into the void. An artillery boss never just idles.
        return new EnemyAction.RangedAttack(Math.max(3, self.getAttackPower() / 2), "fire");
    }

    private EnemyAction tryBarrage(CombatEntity self, GridArena arena, int maxFireballs) {
        if (isOnCooldown(CD_BARRAGE) || getAliveProjectileCount() >= maxFireballs) return null;
        int count = isPhaseTwo() ? 5 : 3;
        List<GridPos> spawnPositions = getFireballSpawnPositions(arena, count);
        if (spawnPositions.isEmpty()) return null;
        setCooldown(CD_BARRAGE, isPhaseTwo() ? 1 : 2); // pay only on success
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
        int playableArea = arena.getWidth() * (arena.getHeight() - 1);
        int tileCount = Math.max(1, playableArea / 2);
        List<GridPos> rainTargets = getRandomPlayableTiles(arena, tileCount);
        if (rainTargets.isEmpty()) return null;
        setCooldown(CD_RAIN, isPhaseTwo() ? 2 : 3);
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

    /**
     * Drop burning embers on single tiles and let the valley do the rest.
     *
     * <p>This used to paint whole rows of {@link TileType#FIRE} terrain that timed out after
     * two turns. Two things were wrong with that. Painted flame terrain never joins the burn
     * cycle, so it re-seeded its neighbours every turn it stayed up, never collapsed to magma
     * and never left the cooldown that stops ground relighting. And it came out ORANGE while
     * everything it spread to came out blue - soul sand is fuel, so the spread caught, and
     * {@code burnsSoulFire} then flipped each spread tile to soul fire. The seed was the only
     * tile in the whole burn that wasn't soul fire.
     *
     * <p>An ember fixes both. One tile, lit through the burn cycle, blue from the start,
     * spreading a ring per turn on its own and burning itself out. The ghast lobs a coal, not
     * a carpet.
     */
    private EnemyAction tryMagma(CombatEntity self, GridArena arena) {
        if (isOnCooldown(CD_MAGMA)) return null;
        int emberCount = isPhaseTwo() ? 3 : 1;
        List<GridPos> emberTiles = getRandomPlayableTiles(arena, emberCount);
        if (emberTiles.isEmpty()) return null;
        setCooldown(CD_MAGMA, isPhaseTwo() ? 2 : 3);
        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.GROUND_CRACK,
            emberTiles, 1, new EnemyAction.IgniteTiles(emberTiles, true, "soul_ember"), 0xFF3AB0FF
        );
        return new EnemyAction.Idle();
    }

    private EnemyAction trySummon(CombatEntity self, GridArena arena, int maxSkeletons) {
        if (isOnCooldown(CD_SUMMON) || getAliveMinionCount() >= maxSkeletons) return null;
        int count = isPhaseTwo() ? 3 : 2;
        List<GridPos> spawnPositions = findSummonPositions(arena, count);
        if (spawnPositions.isEmpty()) return null;
        setCooldown(CD_SUMMON, isPhaseTwo() ? 3 : 4);
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
}
