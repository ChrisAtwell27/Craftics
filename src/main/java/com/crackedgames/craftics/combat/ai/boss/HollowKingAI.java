package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.List;

/**
 * Underground Caverns Boss — "The Hollow King" (Corrupted Miner)
 * Entity: Zombie | 40HP / 7ATK / 3DEF / Speed 2 | Size 2×2
 *
 * Abilities:
 * - Demolition Cache: telegraphed TNT charges that detonate next round.
 * - Rubble Toss: throws mined rubble at the player and clears one obstacle tile.
 * - Cave-In: 3×3 AoE 5 dmg + rubble obstacles.
 * - Miner's Fury: Line charge (3 tiles) destroys obstacles, ATK+2.
 * - Swarm Call: 3-4 Silverfish from arena edges.
 * - Lights Out: Tiles beyond 3 from player go dark, enemies +2 ATK.
 *
 * Phase 2 — "Total Collapse": Auto cave-in, permanent darkness, extra TNT pressure.
 */
public class HollowKingAI extends BossAI {
    private static final String CD_TNT = "demolition_cache";
    private static final String CD_RUBBLE = "rubble_toss";
    private static final String CD_CAVEIN = "cave_in";
    private static final String CD_SWARM = "swarm_call";
    private static final String CD_LIGHTS = "lights_out";
    private static final String CD_CHARGE = "miners_fury";
    private boolean lightsOutPermanent = false;

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        lightsOutPermanent = true;
        // Permanent darkness — handled by CombatManager checking lightsOutPermanent
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Phase 2: Auto cave-in every 2 turns
        if (isPhaseTwo() && getTurnCounter() % 2 == 0 && !isOnCooldown(CD_CAVEIN)) {
            List<GridPos> caveInTiles = getAreaTiles(arena, playerPos, 1); // 3×3 at player
            setCooldown(CD_CAVEIN, 1);
            EnemyAction caveIn = new EnemyAction.CompositeAction(List.of(
                new EnemyAction.AreaAttack(playerPos, 1, 5, "cave_in"),
                new EnemyAction.CreateTerrain(caveInTiles, TileType.NORMAL, 0)
            ));
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                caveInTiles, 1, caveIn, 0xFFAA4400);
            return advanceWhileCharging(self, arena, playerPos);
        }

        // Demolition Cache: prime TNT charges that explode next round.
        if (!isOnCooldown(CD_TNT) && dist <= 5) {
            EnemyAction tnt = tryDemolitionCache(self, arena, playerPos);
            if (tnt != null) return tnt;
        }

        // Rubble Toss: consume one obstacle and hurl it at player.
        if (!isOnCooldown(CD_RUBBLE) && dist <= 5) {
            EnemyAction toss = tryRubbleToss(self, arena, playerPos);
            if (toss != null) return toss;
        }

        // Lights Out — blanket darkness
        if (!isOnCooldown(CD_LIGHTS) && !lightsOutPermanent) {
            setCooldown(CD_LIGHTS, isPhaseTwo() ? 2 : 4);
            return new EnemyAction.BossAbility("lights_out",
                new EnemyAction.AreaAttack(myPos, 0, 0, "lights_out"),
                List.of(myPos));
        }

        // Cave-In on player cluster
        if (!isOnCooldown(CD_CAVEIN) && dist >= 2) {
            List<GridPos> caveInTiles = getAreaTiles(arena, playerPos, 1);
            setCooldown(CD_CAVEIN, 3);
            EnemyAction caveIn = new EnemyAction.CompositeAction(List.of(
                new EnemyAction.AreaAttack(playerPos, 1, 5, "cave_in"),
                new EnemyAction.CreateTerrain(caveInTiles, TileType.NORMAL, 0)
            ));
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                caveInTiles, 1, caveIn, 0xFFAA4400);
            return advanceWhileCharging(self, arena, playerPos);
        }

        // Swarm Call — silverfish from edges
        if (!isOnCooldown(CD_SWARM) && getAliveMinionCount() < 4) {
            setCooldown(CD_SWARM, 3);
            int count = isPhaseTwo() ? 4 : 3;
            List<GridPos> edgePositions = findEdgePositions(arena, count);
            if (!edgePositions.isEmpty()) {
                return new EnemyAction.SummonMinions(
                    "minecraft:silverfish", edgePositions.size(), edgePositions, 2, 1, 0);
            }
        }

        // Miner's Fury — line charge that destroys obstacles
        if (!isOnCooldown(CD_CHARGE) && dist >= 2 && dist <= 4) {
            setCooldown(CD_CHARGE, 2);
            int[] dir = getDirectionToward(myPos, playerPos);
            List<GridPos> chargePath = getLineTiles(arena, myPos, dir[0], dir[1], 3);
            if (!chargePath.isEmpty()) {
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.DIRECTIONAL,
                    chargePath, 1,
                    new EnemyAction.LineAttack(myPos, dir[0], dir[1], 3, self.getAttackPower() + 2),
                    0xFFFF6600);
                return advanceWhileCharging(self, arena, playerPos);
            }
        }

        // Melee if adjacent
        if (dist <= 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        return meleeOrApproach(self, arena, playerPos, 0);
    }

    private EnemyAction tryDemolitionCache(CombatEntity self, GridArena arena, GridPos playerPos) {
        int maxCharges = isPhaseTwo() ? 3 : 2;
        List<GridPos> charges = new ArrayList<>();
        charges.add(playerPos);

        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (charges.size() >= maxCharges) break;
            GridPos p = new GridPos(playerPos.x() + d[0], playerPos.z() + d[1]);
            if (!arena.isInBounds(p)) continue;
            if (arena.isOccupied(p)) continue;
            if (arena.getTile(p) == null || !arena.getTile(p).isWalkable()) continue;
            charges.add(p);
        }

        if (charges.isEmpty()) return null;

        List<EnemyAction> primeActions = new ArrayList<>();
        for (GridPos p : charges) {
            primeActions.add(new EnemyAction.AreaAttack(p, 0, 0, "hollow_tnt_prime"));
        }

        setCooldown(CD_TNT, isPhaseTwo() ? 3 : 4);
        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
            charges, 1, new EnemyAction.CompositeAction(primeActions), 0xFFFFAA22);
        return advanceWhileCharging(self, arena, playerPos);
    }

    private EnemyAction tryRubbleToss(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos rubble = findNearestObstacle(arena, self.getGridPos());
        if (rubble == null) return null;

        setCooldown(CD_RUBBLE, 3);
        EnemyAction resolve = new EnemyAction.CompositeAction(List.of(
            new EnemyAction.CreateTerrain(List.of(rubble), TileType.NORMAL, 0),
            new EnemyAction.AreaAttack(playerPos, 0, 4, "rubble_toss")
        ));
        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.GROUND_CRACK,
            List.of(playerPos, rubble), 1, resolve, 0xFF996633);
        return advanceWhileCharging(self, arena, playerPos);
    }

    private GridPos findNearestObstacle(GridArena arena, GridPos from) {
        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int x = 0; x < arena.getWidth(); x++) {
            for (int z = 0; z < arena.getHeight(); z++) {
                GridPos p = new GridPos(x, z);
                if (arena.getTile(p) == null) continue;
                if (arena.getTile(p).getType() != TileType.OBSTACLE) continue;
                int d = Math.abs(from.x() - x) + Math.abs(from.z() - z);
                if (d < bestDist) {
                    bestDist = d;
                    best = p;
                }
            }
        }
        return best;
    }

    public boolean isLightsOutPermanent() { return lightsOutPermanent; }

    private List<GridPos> findEdgePositions(GridArena arena, int count) {
        List<GridPos> edges = new ArrayList<>();
        int w = arena.getWidth(), h = arena.getHeight();
        // Top and bottom edges
        for (int x = 0; x < w; x++) {
            edges.add(new GridPos(x, 0));
            edges.add(new GridPos(x, h - 1));
        }
        // Left and right edges
        for (int z = 1; z < h - 1; z++) {
            edges.add(new GridPos(0, z));
            edges.add(new GridPos(w - 1, z));
        }
        edges.removeIf(p -> arena.isOccupied(p) || arena.getTile(p) == null || !arena.getTile(p).isWalkable());
        java.util.Collections.shuffle(edges);
        return edges.subList(0, Math.min(count, edges.size()));
    }
}
