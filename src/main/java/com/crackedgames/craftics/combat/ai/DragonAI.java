package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ai.boss.BossAI;
import com.crackedgames.craftics.combat.ai.boss.BossWarning;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.List;

/**
 * Dragon's Nest Boss — "The Ender Dragon" (Enhanced)
 * Entity: Ender Dragon | 80HP / 15ATK / 3DEF / Speed 4 | Size 3×3
 *
 * NEW Abilities:
 * - End Crystal Shield: 2 crystals (8HP each) regen Dragon 3HP/turn. Must destroy.
 *   P2: crystals respawn once.
 * - Dragon Breath Pool: After Swoop, 3×3 lingering dmg zone 3 turns (3 dmg/turn).
 *   P2: permanent.
 * - Wing Gust: Push ALL entities 2 (P2: 3) tiles away from Dragon.
 * - Summon Endermites: 3-4 (3HP/2ATK/Speed 3) every 4 turns.
 * - (Existing) Swoop (P1): Fly 4 tiles cardinal, damage in path.
 * - (Existing) Fury Charge (P2): Charge 4 tiles + AoE radius 2 + Weakness.
 *
 * Phase 2 — "Dragon's Fury":
 * - Crystals respawn once, permanent breath pools, gust 3 tiles,
 * - Swoop leaves fire tiles, every 5 turns full-width breath attack.
 */
public class DragonAI extends BossAI {
    private static final String CD_BREATH = "dragon_breath";
    private static final String CD_GUST = "wing_gust";
    private static final String CD_MITES = "summon_endermites";
    private static final String CD_FULLBREATH = "full_width_breath";
    private int crystalsAlive = 2;
    private int crystalsRespawned = 0;

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        // Respawn destroyed crystals once
        if (crystalsAlive < 2 && crystalsRespawned == 0) {
            int respawnCount = 2 - crystalsAlive;
            crystalsAlive = 2;
            crystalsRespawned = respawnCount;
        }
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Crystal healing passive
        if (crystalsAlive > 0) {
            self.heal(crystalsAlive * 3);
        }

        // Phase 2: Full-width breath every 5 turns
        if (isPhaseTwo() && !isOnCooldown(CD_FULLBREATH) && getTurnCounter() % 5 == 0) {
            setCooldown(CD_FULLBREATH, 4);
            // Perch on edge and breath across player's row or column
            boolean targetRow = Math.abs(myPos.x() - playerPos.x()) > Math.abs(myPos.z() - playerPos.z());
            List<GridPos> breathTiles;
            if (targetRow) {
                breathTiles = getRowTiles(arena, playerPos.z());
            } else {
                breathTiles = getColumnTiles(arena, playerPos.x());
            }
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.GATHERING_PARTICLES,
                breathTiles, 1,
                new EnemyAction.AreaAttack(playerPos, arena.getWidth(), self.getAttackPower(), "full_breath"),
                0xFF8800CC);
            return new EnemyAction.Idle();
        }

        // Wing Gust — push everything away when too close
        if (!isOnCooldown(CD_GUST) && dist <= 2) {
            setCooldown(CD_GUST, 2);
            int[] pushDir = getDirectionToward(myPos, playerPos);
            int pushDist = isPhaseTwo() ? 3 : 2;
            return new EnemyAction.ForcedMovement(-1, pushDir[0], pushDir[1], pushDist);
        }

        // Summon Endermites every 4 turns
        if (!isOnCooldown(CD_MITES) && getTurnCounter() % 4 == 0 && getAliveMinionCount() < 4) {
            setCooldown(CD_MITES, 3);
            int count = isPhaseTwo() ? 4 : 3;
            List<GridPos> mitePositions = findSummonPositions(arena, count);
            if (!mitePositions.isEmpty()) {
                return new EnemyAction.SummonMinions(
                    "minecraft:endermite", mitePositions.size(), mitePositions, 3, 2, 0);
            }
        }

        // Phase 2: Fury charge + AoE breath
        if (isPhaseTwo() && dist >= 2) {
            GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, 4);
            if (target == null) target = playerPos;

            List<GridPos> path = Pathfinding.findPath(arena, myPos, target, 4, self);
            if (!path.isEmpty()) {
                GridPos endPos = path.get(path.size() - 1);
                if (endPos.manhattanDistance(playerPos) <= 1) {
                    // Leave fire trail + breath pool
                    int breathDuration = 0; // permanent in P2
                    List<EnemyAction> actions = new ArrayList<>();
                    actions.add(new EnemyAction.MoveAndAttack(path, self.getAttackPower()));
                    actions.add(new EnemyAction.CreateTerrain(
                        getAreaTiles(arena, endPos, 1), TileType.FIRE, breathDuration));
                    if (isPhaseTwo()) {
                        actions.add(new EnemyAction.CreateTerrain(path, TileType.FIRE, 2));
                    }
                    return new EnemyAction.CompositeAction(actions);
                }
                return new EnemyAction.Move(path);
            }
        }

        // Phase 1: Aerial swoop
        if (!isPhaseTwo()) {
            List<GridPos> swoopPath = findSwoopPath(arena, myPos, playerPos);
            if (swoopPath != null && !swoopPath.isEmpty()) {
                // Dragon Breath Pool at landing zone
                GridPos landingPos = swoopPath.get(swoopPath.size() - 1);
                int poolDuration = 3;
                List<EnemyAction> actions = new ArrayList<>();
                actions.add(new EnemyAction.Swoop(swoopPath, self.getAttackPower()));
                actions.add(new EnemyAction.CreateTerrain(
                    getAreaTiles(arena, landingPos, 1), TileType.FIRE, poolDuration));
                return new EnemyAction.CompositeAction(actions);
            }

            // Reposition to align for swoop
            GridPos alignTarget = findAlignmentTarget(arena, myPos, playerPos);
            if (alignTarget != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, alignTarget, 3, self);
                if (!path.isEmpty()) {
                    return new EnemyAction.Move(path);
                }
            }
        }

        // Adjacent melee
        if (dist <= 1) {
            if (isPhaseTwo()) {
                return new EnemyAction.Explode(self.getAttackPower(), 2);
            }
            return new EnemyAction.Attack(self.getAttackPower());
        }

        return AIUtils.seekOrWander(self, arena, playerPos);
    }

    /** Called by CombatManager when an End Crystal is destroyed. */
    public void onCrystalDestroyed() {
        crystalsAlive = Math.max(0, crystalsAlive - 1);
    }

    public int getCrystalsAlive() { return crystalsAlive; }

    private List<GridPos> findSwoopPath(GridArena arena, GridPos from, GridPos playerPos) {
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            List<GridPos> path = new ArrayList<>();
            GridPos current = from;
            boolean hitsPlayer = false;

            for (int i = 0; i < 4; i++) {
                GridPos next = new GridPos(current.x() + dir[0], current.z() + dir[1]);
                if (!arena.isInBounds(next)) break;
                path.add(next);
                if (next.equals(playerPos)) hitsPlayer = true;
                current = next;
            }

            if (hitsPlayer && !path.isEmpty()) return path;
        }
        return null;
    }

    private GridPos findAlignmentTarget(GridArena arena, GridPos self, GridPos playerPos) {
        GridPos best = null;
        int bestScore = Integer.MAX_VALUE;

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 3) continue;
                if (dx == 0 && dz == 0) continue;
                GridPos candidate = new GridPos(self.x() + dx, self.z() + dz);
                if (!arena.isInBounds(candidate) || arena.isOccupied(candidate)) continue;
                boolean aligned = (candidate.x() == playerPos.x() || candidate.z() == playerPos.z());
                if (aligned) {
                    int dist = candidate.manhattanDistance(playerPos);
                    if (dist >= 2 && dist <= 5 && dist < bestScore) {
                        bestScore = dist;
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }
}
