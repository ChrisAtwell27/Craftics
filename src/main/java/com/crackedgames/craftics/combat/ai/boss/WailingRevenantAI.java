package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import java.util.ArrayList;
import java.util.List;

/**
 * Soul Sand Valley Boss — "The Wailing Revenant" (Ghast)
 * Entity: Ghast | 40HP / 8ATK / 1DEF / Range 6 / Speed 1 (teleports) | Size 2×2
 *
 * Abilities:
 * - Soul Barrage: Spawns 2 (P2: 3) fireball projectile entities that travel straight at speed 2.
 *   Fireballs have 99 HP — the player should redirect them, not kill them.
 *   Fireballs explode with 1-tile AOE on wall/player hit. Redirected fireballs damage enemies.
 * - Wail of Despair: AoE debuff: -2 ATK within 4 tiles, 2 turns.
 * - Soul Chain: Tethers player, 2 dmg/turn. Breaks at 5+ tiles from anchor.
 * - Phase Shift: Teleport to random corner/edge when player within 3 tiles.
 * - Standard attack: Single fireball projectile when no other ability fires.
 *
 * Phase 2 — "Requiem": 3 fireballs, wail + slowness, 2 chains, permanent fire, speed 2.
 */
public class WailingRevenantAI extends BossAI {
    @Override public int getGridSize() { return 2; }
    private static final String CD_BARRAGE = "soul_barrage";
    private static final String CD_WAIL = "wail_despair";
    private static final String CD_CHAIN = "soul_chain";
    private static final String CD_SHIFT = "phase_shift";
    private int activeChains = 0;

    private static final int MAX_FIREBALLS_P1 = 4;
    private static final int MAX_FIREBALLS_P2 = 6;

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        self.setSpeedBonus(1); // Speed 1 → 2
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        int maxFireballs = isPhaseTwo() ? MAX_FIREBALLS_P2 : MAX_FIREBALLS_P1;

        // Phase Shift — escape when player gets close
        if (!isOnCooldown(CD_SHIFT) && dist <= 3) {
            setCooldown(CD_SHIFT, 1);
            GridPos escapeTo = findCornerOrEdge(arena, playerPos);
            if (escapeTo != null) {
                return new EnemyAction.Teleport(escapeTo);
            }
        }

        // Soul Chain — tether the player
        int maxChains = isPhaseTwo() ? 2 : 1;
        if (!isOnCooldown(CD_CHAIN) && activeChains < maxChains && dist <= 6) {
            setCooldown(CD_CHAIN, 4);
            activeChains++;
            return new EnemyAction.BossAbility("soul_chain",
                new EnemyAction.AreaAttack(playerPos, 0, 2, "soul_chain"),
                List.of(playerPos));
        }

        // Soul Barrage — spawn fireball projectiles
        if (!isOnCooldown(CD_BARRAGE) && getAliveProjectileCount() < maxFireballs) {
            setCooldown(CD_BARRAGE, 2);
            int count = isPhaseTwo() ? 3 : 2;
            int[] dir = getDirectionToward(myPos, playerPos);
            List<GridPos> spawnPositions = getProjectileSpawnPositions(arena, myPos, getGridSize(), playerPos, count);
            if (!spawnPositions.isEmpty()) {
                List<int[]> directions = new ArrayList<>();
                for (int i = 0; i < spawnPositions.size(); i++) {
                    directions.add(new int[]{dir[0], dir[1]});
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

        // Wail of Despair — AoE debuff
        if (!isOnCooldown(CD_WAIL) && dist <= 5) {
            setCooldown(CD_WAIL, 3);
            String effect = isPhaseTwo() ? "wail_despair_slow" : "wail_despair";
            return new EnemyAction.AreaAttack(myPos, 4, 0, effect);
        }

        // Standard attack: single fireball projectile
        if (dist <= 6 && getAliveProjectileCount() < maxFireballs) {
            int[] dir = getDirectionToward(myPos, playerPos);
            List<GridPos> spawnPos = getProjectileSpawnPositions(arena, myPos, getGridSize(), playerPos, 1);
            if (!spawnPos.isEmpty()) {
                return new EnemyAction.SpawnProjectile(
                    "minecraft:blaze", spawnPos, List.of(new int[]{dir[0], dir[1]}),
                    99, 8, 0, "ghast_fireball"
                );
            }
        }

        return meleeOrApproach(self, arena, playerPos, 0);
    }

    public void onChainBroken() {
        activeChains = Math.max(0, activeChains - 1);
    }

    public int getActiveChains() { return activeChains; }

    private GridPos findCornerOrEdge(GridArena arena, GridPos awayFrom) {
        int w = arena.getWidth(), h = arena.getHeight();
        List<GridPos> corners = new ArrayList<>(List.of(
            new GridPos(1, 1),
            new GridPos(w - 2, 1),
            new GridPos(1, h - 2),
            new GridPos(w - 2, h - 2)
        ));
        // Pick the corner farthest from the player
        corners.sort((a, b) -> b.manhattanDistance(awayFrom) - a.manhattanDistance(awayFrom));
        for (GridPos c : corners) {
            if (arena.isInBounds(c) && !arena.isOccupied(c)
                    && arena.getTile(c) != null && arena.getTile(c).isWalkable()) {
                return c;
            }
        }
        return null;
    }
}
