package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.List;

/**
 * Plains Boss — "The Revenant" (Undead Knight)
 * Entity: Zombie | 20HP / 4ATK / 2DEF / Speed 2 | Size 2×2
 *
 * Abilities:
 * - Raise the Dead: Places zombie-head markers; destroying one prevents that zombie from spawning.
 * - Death Charge: Charges in a straight line from core position up to 3 tiles, ATK+2 damage. P2: ATK+4 + fire trail.
 * - Gravefire Grid: Telegraphs a giant checker-grid of magma tiles for 1 turn.
 * - Shield Bash: Adjacent knockback 2 tiles, half ATK damage.
 *
 * Phase 2 (≤50% HP) — "Undying Rage":
 * - Gains Regeneration (1 HP/turn) — handled by CombatManager checking isEnraged
 * - Raise the Dead every 2 turns instead of 3
 * - Death Charge ATK+4 + fire trail
 */
public class RevenantAI extends BossAI {
    private static final int SUMMON_CAP = 3;
    private static final String CD_RAISE = "raise_dead";
    private static final String CD_CHARGE = "death_charge";
    private static final String CD_GRAVEFIRE_GRID = "gravefire_grid";

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        int dist = self.minDistanceTo(playerPos);
        int summonInterval = isPhaseTwo() ? 2 : 3;

        // Priority 1: Resolve pending warning (handled by base class)

        // Priority 2: Death Charge if the player is in a clear lane.
        if (!isOnCooldown(CD_CHARGE)) {
            ChargePattern charge = findChargePattern(self, arena, playerPos);
            if (charge != null) {
                int chargeDmg = self.getAttackPower() + (isPhaseTwo() ? 4 : 2);
                setCooldown(CD_CHARGE, 2);

                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                    charge.warningTiles(), 1, new EnemyAction.MoveAndAttack(charge.path(), chargeDmg), 0xFFFF4444);
                return advanceWhileCharging(self, arena, playerPos);
            }
        }

        // Priority 3: Gravefire Grid (telegraphed magma checkerboard for one turn)
        if (!isOnCooldown(CD_GRAVEFIRE_GRID)) {
            List<GridPos> magmaTiles = getGravefireGridTiles(arena, playerPos);
            if (magmaTiles.size() >= 6) {
                int gridCd = isPhaseTwo() ? 3 : 4;
                setCooldown(CD_GRAVEFIRE_GRID, gridCd);
                EnemyAction magmaGrid = new EnemyAction.CreateTerrain(magmaTiles, TileType.FIRE, 1);
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.GROUND_CRACK,
                    magmaTiles, 1, magmaGrid, 0xFFFF8800);
                return advanceWhileCharging(self, arena, playerPos);
            }
        }

        // Priority 4: Raise the Dead on schedule
        if (!isOnCooldown(CD_RAISE) && getAliveMinionCount() < SUMMON_CAP) {
            int count = isPhaseTwo() ? 2 : (getAliveMinionCount() == 0 ? 2 : 1);
            List<GridPos> positions = findSummonPositions(arena, count);
            if (!positions.isEmpty()) {
                setCooldown(CD_RAISE, summonInterval);
                // Telegraph: ground cracks on summon tiles, resolves next turn
                EnemyAction summon = new EnemyAction.SummonMinions(
                    "minecraft:zombie", positions.size(), positions, 6, 2, 0);
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.GROUND_CRACK,
                    positions, 1, summon, 0xFF44FF44);
                return new EnemyAction.Idle();
            }
        }

        // Priority 5: Shield Bash if player is adjacent
        if (dist <= 1) {
            GridPos myPos = self.getGridPos();
            int kdx = Integer.signum(playerPos.x() - myPos.x());
            int kdz = Integer.signum(playerPos.z() - myPos.z());
            if (kdx == 0 && kdz == 0) kdx = 1; // fallback
            return new EnemyAction.ForcedMovement(-1, kdx, kdz, 2);
        }

        // Priority 6: Melee attack or approach
        return meleeOrApproach(self, arena, playerPos, isPhaseTwo() ? 2 : 0);
    }

    private ChargePattern findChargePattern(CombatEntity self, GridArena arena, GridPos playerPos) {
        // Single-anchor origin keeps the line attack consistent and readable.
        GridPos anchor = self.getGridPos();
        return buildChargePattern(arena, anchor, playerPos);
    }

    private List<GridPos> getGravefireGridTiles(GridArena arena, GridPos playerPos) {
        List<GridPos> tiles = new ArrayList<>();
        int parity = getTurnCounter() % 2;

        for (int x = 0; x < arena.getWidth(); x++) {
            for (int z = 0; z < arena.getHeight(); z++) {
                GridPos pos = new GridPos(x, z);
                if (((x + z) & 1) != parity) continue;
                if (pos.manhattanDistance(playerPos) <= 1) continue;
                var tile = arena.getTile(pos);
                if (tile == null || !tile.isWalkable()) continue;
                tiles.add(pos);
            }
        }

        return tiles;
    }

    private ChargePattern buildChargePattern(GridArena arena, GridPos startTile, GridPos playerPos) {
        int dx = 0;
        int dz = 0;
        if (startTile.x() == playerPos.x() && startTile.z() != playerPos.z()) {
            dz = Integer.signum(playerPos.z() - startTile.z());
        } else if (startTile.z() == playerPos.z() && startTile.x() != playerPos.x()) {
            dx = Integer.signum(playerPos.x() - startTile.x());
        } else {
            return null;
        }

        int distance = Math.abs(playerPos.x() - startTile.x()) + Math.abs(playerPos.z() - startTile.z());
        if (distance < 2) {
            return null;
        }

        int pathLength = Math.min(3, distance - 1);
        List<GridPos> path = new ArrayList<>();
        GridPos current = startTile;
        for (int i = 0; i < pathLength; i++) {
            GridPos next = new GridPos(current.x() + dx, current.z() + dz);
            if (!arena.isInBounds(next) || arena.isOccupied(next)) break;
            var tile = arena.getTile(next);
            if (tile == null || !tile.isWalkable()) break;
            path.add(next);
            current = next;
        }
        if (path.isEmpty()) {
            return null;
        }

        GridPos finalPos = path.get(path.size() - 1);
        if (finalPos.manhattanDistance(playerPos) > 1) {
            return null;
        }

        List<GridPos> warningTiles = new ArrayList<>(path);
        warningTiles.add(playerPos);
        return new ChargePattern(path.get(0), dx, dz, path, warningTiles);
    }

    private record ChargePattern(GridPos start, int dx, int dz, List<GridPos> path, List<GridPos> warningTiles) {}
}
