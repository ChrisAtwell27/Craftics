package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dense Jungle Boss — "The Broodmother" (Spider Queen)
 * Entity: Spider | 35HP / 6ATK / 2DEF / Speed 3 | Size 3×3
 *
 * Abilities:
 * - Spawn Brood: 2–3 Cave Spiders (3HP/2ATK) from egg sacs every 3 turns.
 * - Web Spray: 3×3 stun 1 turn + slow 1 turn. P2: also poison 1dmg/2turns.
 * - Venomous Bite: Melee ATK + Poison (2 dmg/turn, 3 turns).
 * - Pounce: Leap 3 tiles, 2×2 AoE landing, ATK+2. P2: 4 tiles.
 *
 * Phase 2 — "Nest Awakening": +2 Speed, 3 new egg sacs, respawning sacs.
 */
public class BroodmotherAI extends BossAI {
    private static final String CD_BROOD = "spawn_brood";
    private static final String CD_WEB = "web_spray";
    private static final String CD_POUNCE = "pounce";
    private final List<GridPos> eggSacs = new ArrayList<>();
    private boolean phase2EggsPlaced = false;

    public void initEggSacs(GridArena arena) {
        // Place 3 egg sacs at fight start in arena corners/edges
        List<GridPos> candidates = new ArrayList<>();
        int w = arena.getWidth(), h = arena.getHeight();
        candidates.add(new GridPos(1, 1));
        candidates.add(new GridPos(w - 2, 1));
        candidates.add(new GridPos(1, h - 2));
        candidates.add(new GridPos(w - 2, h - 2));
        candidates.add(new GridPos(w / 2, 1));
        candidates.add(new GridPos(w / 2, h - 2));
        Collections.shuffle(candidates);
        for (int i = 0; i < Math.min(3, candidates.size()); i++) {
            GridPos pos = candidates.get(i);
            if (arena.isInBounds(pos) && !arena.isOccupied(pos)) {
                eggSacs.add(pos);
            }
        }
    }

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        self.setSpeedBonus(2); // Speed 3 → 5
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Phase 2: Place 3 new egg sacs once
        if (isPhaseTwo() && !phase2EggsPlaced) {
            phase2EggsPlaced = true;
            List<GridPos> newSacs = findSummonPositions(arena, 3);
            eggSacs.addAll(newSacs);
        }

        // Spawn Brood from egg sacs
        if (!isOnCooldown(CD_BROOD) && !eggSacs.isEmpty()) {
            setCooldown(CD_BROOD, 3);
            int count = Math.min(eggSacs.isEmpty() ? 0 : (isPhaseTwo() ? 3 : 2), eggSacs.size());
            List<GridPos> spawnPositions = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                GridPos sacPos = eggSacs.get(i % eggSacs.size());
                // Spawn adjacent to egg sac
                List<GridPos> adj = new ArrayList<>();
                for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                    GridPos p = new GridPos(sacPos.x() + d[0], sacPos.z() + d[1]);
                    if (arena.isInBounds(p) && !arena.isOccupied(p)
                            && arena.getTile(p) != null && arena.getTile(p).isWalkable()) {
                        adj.add(p);
                    }
                }
                if (!adj.isEmpty()) {
                    spawnPositions.add(adj.get(0));
                }
            }
            if (!spawnPositions.isEmpty()) {
                EnemyAction spawn = new EnemyAction.SummonMinions(
                    "minecraft:cave_spider", spawnPositions.size(), spawnPositions, 3, 2, 0);
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.GROUND_CRACK,
                    eggSacs, 1, spawn, 0xFF44AA44);
                return new EnemyAction.Idle();
            }
        }

        // Web Spray if player in range
        if (!isOnCooldown(CD_WEB) && dist <= 4) {
            List<GridPos> webTiles = getAreaTiles(arena, playerPos, 1);
            setCooldown(CD_WEB, 3);
            String effect = isPhaseTwo() ? "web_spray_poison" : "web_spray";
            EnemyAction webAction = new EnemyAction.AreaAttack(playerPos, 1, 0, effect);
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.GATHERING_PARTICLES,
                webTiles, 1, webAction, 0xFFCCCCCC);
            return new EnemyAction.Idle();
        }

        // Pounce if at range 2–4
        int pounceRange = isPhaseTwo() ? 4 : 3;
        if (!isOnCooldown(CD_POUNCE) && dist >= 2 && dist <= pounceRange) {
            setCooldown(CD_POUNCE, 2);
            // Find landing spot adjacent to player
            GridPos landingPos = null;
            for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                GridPos p = new GridPos(playerPos.x() + d[0], playerPos.z() + d[1]);
                if (arena.isInBounds(p) && !arena.isOccupied(p)
                        && arena.getTile(p) != null && arena.getTile(p).isWalkable()) {
                    landingPos = p;
                    break;
                }
            }
            if (landingPos != null) {
                // 2×2 AoE pounce landing
                List<GridPos> landingTiles = getAreaTiles(arena, landingPos, 0);
                landingTiles.add(new GridPos(landingPos.x() + 1, landingPos.z()));
                landingTiles.add(new GridPos(landingPos.x(), landingPos.z() + 1));
                landingTiles.add(new GridPos(landingPos.x() + 1, landingPos.z() + 1));
                landingTiles.removeIf(p -> !arena.isInBounds(p));
                EnemyAction pounce = new EnemyAction.AreaAttack(landingPos, 1, self.getAttackPower() + 2, "pounce");
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                    landingTiles, 1, pounce, 0xFFFF4444);
                return new EnemyAction.Idle();
            }
        }

        // Venomous Bite if adjacent
        if (dist <= 1) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "venomous_bite");
        }

        return meleeOrApproach(self, arena, playerPos, 0);
    }

    public List<GridPos> getEggSacs() { return eggSacs; }
    public void removeEggSac(GridPos pos) { eggSacs.remove(pos); }
}
