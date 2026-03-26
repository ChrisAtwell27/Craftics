package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.List;

/**
 * Stony Peaks Boss — "The Rockbreaker" (Mountain Warlord)
 * Entity: Vindicator | 30HP / 6ATK / 3DEF / Speed 2 | Size 2×2
 *
 * Abilities:
 * - Seismic Slam: + cross (3 tiles each direction), 5 dmg. P2: 4 tiles + destroys obstacles.
 * - Boulder Toss: Range 4, 4 dmg + creates obstacle. P2: 2 boulders.
 * - Fortify: +5 DEF for 2 turns. P2: passive +3 DEF permanent (on transition).
 * - Avalanche: Full-row attack, 3 dmg. P2: 2 rows.
 */
public class RockbreakerAI extends BossAI {
    private static final String CD_SLAM = "seismic_slam";
    private static final String CD_BOULDER = "boulder_toss";
    private static final String CD_FORTIFY = "fortify";
    private static final String CD_AVALANCHE = "avalanche";

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        // Passive +3 DEF permanent — handled by CombatManager checking isEnraged for Rockbreaker
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Seismic Slam if player within cross range
        int slamRange = isPhaseTwo() ? 4 : 3;
        if (!isOnCooldown(CD_SLAM) && dist <= slamRange) {
            List<GridPos> crossTiles = getCrossTiles(arena, myPos, slamRange);
            setCooldown(CD_SLAM, 2);
            EnemyAction slamAction = new EnemyAction.AreaAttack(myPos, slamRange, 5, "seismic_slam");
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.GROUND_CRACK,
                crossTiles, 1, slamAction, 0xFFFF6644);
            return new EnemyAction.Idle();
        }

        // Boulder Toss if in range
        if (!isOnCooldown(CD_BOULDER) && dist >= 2 && dist <= 4) {
            setCooldown(CD_BOULDER, 2);
            int boulderCount = isPhaseTwo() ? 2 : 1;
            if (boulderCount == 1) {
                // Single boulder at player pos
                List<GridPos> targetTiles = List.of(playerPos);
                EnemyAction boulderAction = new EnemyAction.CompositeAction(List.of(
                    new EnemyAction.AreaAttack(playerPos, 0, 4, "boulder_toss"),
                    new EnemyAction.CreateTerrain(List.of(playerPos), TileType.OBSTACLE, 0)
                ));
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                    targetTiles, 1, boulderAction, 0xFF887744);
                return new EnemyAction.Idle();
            } else {
                // Two boulders — player pos + adjacent tile
                GridPos second = new GridPos(playerPos.x() + 1, playerPos.z());
                if (!arena.isInBounds(second)) second = new GridPos(playerPos.x() - 1, playerPos.z());
                List<GridPos> targetTiles = List.of(playerPos, second);
                EnemyAction boulderAction = new EnemyAction.CompositeAction(List.of(
                    new EnemyAction.AreaAttack(playerPos, 0, 4, "boulder_toss"),
                    new EnemyAction.CreateTerrain(List.of(playerPos), TileType.OBSTACLE, 0),
                    new EnemyAction.AreaAttack(second, 0, 4, "boulder_toss"),
                    new EnemyAction.CreateTerrain(List.of(second), TileType.OBSTACLE, 0)
                ));
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                    targetTiles, 1, boulderAction, 0xFF887744);
                return new EnemyAction.Idle();
            }
        }

        // Fortify if recently damaged and not on cooldown
        if (!isOnCooldown(CD_FORTIFY) && self.wasDamagedSinceLastTurn()) {
            setCooldown(CD_FORTIFY, 4);
            return new EnemyAction.ModifySelf("defense", 5, 2);
        }

        // Avalanche every few turns
        if (!isOnCooldown(CD_AVALANCHE) && getTurnCounter() % 4 == 0) {
            int targetRow = playerPos.z();
            List<GridPos> rowTiles = getRowTiles(arena, targetRow);
            setCooldown(CD_AVALANCHE, 4);
            if (isPhaseTwo()) {
                // Two rows
                int secondRow = Math.min(arena.getHeight() - 1, targetRow + 1);
                List<GridPos> allTiles = new java.util.ArrayList<>(rowTiles);
                allTiles.addAll(getRowTiles(arena, secondRow));
                EnemyAction avalanche = new EnemyAction.CompositeAction(List.of(
                    new EnemyAction.AreaAttack(new GridPos(0, targetRow), arena.getWidth(), 3, "avalanche"),
                    new EnemyAction.AreaAttack(new GridPos(0, secondRow), arena.getWidth(), 3, "avalanche")
                ));
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                    allTiles, 1, avalanche, 0xFFAA6633);
                return new EnemyAction.Idle();
            } else {
                EnemyAction avalanche = new EnemyAction.AreaAttack(
                    new GridPos(0, targetRow), arena.getWidth(), 3, "avalanche");
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                    rowTiles, 1, avalanche, 0xFFAA6633);
                return new EnemyAction.Idle();
            }
        }

        // Default: melee rush
        return meleeOrApproach(self, arena, playerPos, isPhaseTwo() ? 2 : 0);
    }
}
