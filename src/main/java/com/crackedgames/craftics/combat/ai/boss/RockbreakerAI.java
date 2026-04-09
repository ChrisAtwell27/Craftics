package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.List;

/**
 * Stony Peaks Boss — "The Rockbreaker" (Mountain Warlord)
 * Entity: Vindicator | 30HP / 6ATK / 3DEF / Speed 2 | Size 2×2
 *
 * Aggressive melee boss that relentlessly closes distance and punishes with knockback.
 * Every attack pushes the player around the arena, creating constant positional pressure.
 *
 * Abilities:
 * - Seismic Slam: + cross pattern, 6 dmg + knockback 2 tiles away from center.
 *   Warning: GROUND_CRACK with orange fissures radiating outward.
 *   P2: range 4, knockback 3.
 *
 * - Boulder Toss: Range 4, 5 dmg + knockback 1 tile away from boss + creates obstacle.
 *   Warning: TILE_HIGHLIGHT with brown target marker.
 *   P2: 2 boulders, knockback 2.
 *
 * - Charge: Dashes in a straight line toward the player (up to 5 tiles).
 *   If the player is in the path, deals 7 dmg and knocks them back 3 tiles.
 *   Warning: DIRECTIONAL with red arrow markers along charge path.
 *   P2: charge damage 9, knockback 4.
 *
 * - Avalanche: Full-row attack, 4 dmg + knockback 1 tile downward.
 *   Warning: TILE_HIGHLIGHT with brown/tan row highlight.
 *   P2: 2 rows, knockback 2.
 *
 * - Ground Pound (melee): When adjacent, 2×2 shockwave around boss, 5 dmg + knockback 2.
 *   Instant (no telegraph). P2: knockback 3.
 *
 * Phase 2 — "Unstoppable": +1 speed, all knockback distances increased,
 *           shorter cooldowns, destroys obstacles when charging through them.
 */
public class RockbreakerAI extends BossAI {
    private static final String CD_SLAM = "seismic_slam";
    private static final String CD_BOULDER = "boulder_toss";
    private static final String CD_CHARGE = "charge";
    private static final String CD_AVALANCHE = "avalanche";

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        self.setSpeedBonus(1); // Speed 2 → 3
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        boolean p2 = isPhaseTwo();

        // Phase 2 reduces cooldowns
        int cdSlam = p2 ? 1 : 2;
        int cdBoulder = p2 ? 1 : 2;
        int cdCharge = p2 ? 2 : 3;
        int cdAvalanche = p2 ? 3 : 4;

        int extraDmg = p2 ? 2 : 0;

        // Ground Pound: instant melee AoE + knockback when adjacent
        if (dist <= 1) {
            return castGroundPound(self, arena, myPos, playerPos, p2, extraDmg);
        }

        // Charge: dash toward player for big knockback hit
        if (!isOnCooldown(CD_CHARGE) && dist >= 3 && dist <= 6) {
            EnemyAction charge = castCharge(self, arena, myPos, playerPos, cdCharge, p2, extraDmg);
            if (charge != null) return charge;
        }

        // Seismic Slam: cross pattern knockback
        int slamRange = p2 ? 4 : 3;
        if (!isOnCooldown(CD_SLAM) && dist <= slamRange) {
            return castSeismicSlam(self, arena, myPos, playerPos, slamRange, cdSlam, p2, extraDmg);
        }

        // Boulder Toss: ranged knockback + terrain denial
        if (!isOnCooldown(CD_BOULDER) && dist >= 2 && dist <= 4) {
            return castBoulderToss(self, arena, myPos, playerPos, cdBoulder, p2, extraDmg);
        }

        // Avalanche: row-wide knockback
        if (!isOnCooldown(CD_AVALANCHE) && getTurnCounter() % 3 == 0) {
            return castAvalanche(self, arena, playerPos, cdAvalanche, p2, extraDmg);
        }

        // Default: aggressively close distance and attack
        return advanceWhileCharging(self, arena, playerPos);
    }

    private EnemyAction castGroundPound(CombatEntity self, GridArena arena,
            GridPos myPos, GridPos playerPos, boolean p2, int extraDmg) {
        // Instant shockwave — no telegraph, rewards staying close
        int kbDist = p2 ? 3 : 2;
        int[] pushDir = getDirectionToward(myPos, playerPos);
        return new EnemyAction.CompositeAction(List.of(
            new EnemyAction.AreaAttack(myPos, 1, 5 + extraDmg, "ground_pound"),
            new EnemyAction.ForcedMovement(-1, pushDir[0], pushDir[1], kbDist)
        ));
    }

    private EnemyAction castCharge(CombatEntity self, GridArena arena,
            GridPos myPos, GridPos playerPos, int cooldown, boolean p2, int extraDmg) {
        int[] dir = getDirectionToward(myPos, playerPos);
        if (dir[0] == 0 && dir[1] == 0) return null;

        // Build charge path toward the player
        int maxDist = p2 ? 6 : 5;
        List<GridPos> chargePath = getChargePath(arena, myPos, dir[0], dir[1], maxDist);
        if (chargePath.size() < 2) return null;

        // Check if player is in or adjacent to the charge line
        boolean playerInPath = false;
        int stopIndex = chargePath.size();
        for (int i = 0; i < chargePath.size(); i++) {
            if (chargePath.get(i).manhattanDistance(playerPos) <= 1) {
                playerInPath = true;
                stopIndex = i + 1;
                break;
            }
        }

        if (!playerInPath) return null;

        List<GridPos> actualPath = new ArrayList<>(chargePath.subList(0, Math.min(stopIndex, chargePath.size())));
        int chargeDmg = p2 ? 9 : 7;
        int kbDist = p2 ? 4 : 3;

        EnemyAction resolve = new EnemyAction.CompositeAction(List.of(
            new EnemyAction.MoveAndAttackWithKnockback(actualPath, chargeDmg + extraDmg, kbDist)
        ));

        // Warning tiles show the full charge line
        List<GridPos> warningTiles = new ArrayList<>(chargePath.subList(0, Math.min(stopIndex, chargePath.size())));
        warningTiles.add(playerPos);

        setCooldown(CD_CHARGE, cooldown);
        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.DIRECTIONAL,
            warningTiles, 1, resolve, 0xFFFF4422);
        return advanceWhileCharging(self, arena, playerPos);
    }

    private EnemyAction castSeismicSlam(CombatEntity self, GridArena arena,
            GridPos myPos, GridPos playerPos, int slamRange, int cooldown,
            boolean p2, int extraDmg) {
        List<GridPos> crossTiles = getCrossTiles(arena, myPos, slamRange);
        setCooldown(CD_SLAM, cooldown);

        int slamDmg = 6 + extraDmg;
        int kbDist = p2 ? 3 : 2;
        int[] pushDir = getDirectionToward(myPos, playerPos);

        EnemyAction slamAction = new EnemyAction.CompositeAction(List.of(
            new EnemyAction.AreaAttack(myPos, slamRange, slamDmg, "seismic_slam"),
            new EnemyAction.ForcedMovement(-1, pushDir[0], pushDir[1], kbDist)
        ));

        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.GROUND_CRACK,
            crossTiles, 1, slamAction, 0xFFFF6644);
        return advanceWhileCharging(self, arena, playerPos);
    }

    private EnemyAction castBoulderToss(CombatEntity self, GridArena arena,
            GridPos myPos, GridPos playerPos, int cooldown, boolean p2, int extraDmg) {
        setCooldown(CD_BOULDER, cooldown);

        int boulderDmg = 5 + extraDmg;
        int kbDist = p2 ? 2 : 1;
        int[] pushDir = getDirectionToward(myPos, playerPos);

        if (p2) {
            // Two boulders: player pos + adjacent tile
            GridPos second = new GridPos(playerPos.x() + 1, playerPos.z());
            if (!arena.isInBounds(second)) second = new GridPos(playerPos.x() - 1, playerPos.z());
            List<GridPos> targetTiles = List.of(playerPos, second);
            EnemyAction boulderAction = new EnemyAction.CompositeAction(List.of(
                new EnemyAction.AreaAttack(playerPos, 0, boulderDmg, "boulder_toss"),
                new EnemyAction.ForcedMovement(-1, pushDir[0], pushDir[1], kbDist),
                new EnemyAction.CreateTerrain(List.of(playerPos), TileType.OBSTACLE, 0),
                new EnemyAction.AreaAttack(second, 0, boulderDmg, "boulder_toss"),
                new EnemyAction.CreateTerrain(List.of(second), TileType.OBSTACLE, 0)
            ));
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                targetTiles, 1, boulderAction, 0xFF887744);
        } else {
            // Single boulder
            List<GridPos> targetTiles = List.of(playerPos);
            EnemyAction boulderAction = new EnemyAction.CompositeAction(List.of(
                new EnemyAction.AreaAttack(playerPos, 0, boulderDmg, "boulder_toss"),
                new EnemyAction.ForcedMovement(-1, pushDir[0], pushDir[1], kbDist),
                new EnemyAction.CreateTerrain(List.of(playerPos), TileType.OBSTACLE, 0)
            ));
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                targetTiles, 1, boulderAction, 0xFF887744);
        }
        return new EnemyAction.Idle();
    }

    private EnemyAction castAvalanche(CombatEntity self, GridArena arena,
            GridPos playerPos, int cooldown, boolean p2, int extraDmg) {
        int targetRow = playerPos.z();
        List<GridPos> rowTiles = getRowTiles(arena, targetRow);
        setCooldown(CD_AVALANCHE, cooldown);

        int avalancheDmg = 4 + extraDmg;
        int kbDist = p2 ? 2 : 1;
        // Avalanche pushes player downward (positive z)
        int pushDz = 1;

        if (p2) {
            int secondRow = Math.min(arena.getHeight() - 1, targetRow + 1);
            List<GridPos> allTiles = new ArrayList<>(rowTiles);
            allTiles.addAll(getRowTiles(arena, secondRow));
            EnemyAction avalanche = new EnemyAction.CompositeAction(List.of(
                new EnemyAction.AreaAttack(new GridPos(0, targetRow), arena.getWidth(), avalancheDmg, "avalanche"),
                new EnemyAction.AreaAttack(new GridPos(0, secondRow), arena.getWidth(), avalancheDmg, "avalanche"),
                new EnemyAction.ForcedMovement(-1, 0, pushDz, kbDist)
            ));
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                allTiles, 1, avalanche, 0xFFAA6633);
        } else {
            EnemyAction avalanche = new EnemyAction.CompositeAction(List.of(
                new EnemyAction.AreaAttack(new GridPos(0, targetRow), arena.getWidth(), avalancheDmg, "avalanche"),
                new EnemyAction.ForcedMovement(-1, 0, pushDz, kbDist)
            ));
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                rowTiles, 1, avalanche, 0xFFAA6633);
        }
        return new EnemyAction.Idle();
    }
}
