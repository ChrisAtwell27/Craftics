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
 * - Raise the Dead: Summons 1–2 Zombies (6HP/2ATK) every 3 turns (2 in P2). Cap 3 alive.
 * - Death Charge: Straight line charge up to 3 tiles, ATK+2 damage. P2: ATK+4 + fire trail.
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

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        int summonInterval = isPhaseTwo() ? 2 : 3;

        // Priority 1: Resolve pending warning (handled by base class)

        // Priority 2: Death Charge if the player is in a clear lane.
        if (!isOnCooldown(CD_CHARGE)) {
            ChargePattern charge = findChargePattern(self, arena, playerPos);
            if (charge != null) {
                int chargeDmg = self.getAttackPower() + (isPhaseTwo() ? 4 : 2);
                setCooldown(CD_CHARGE, 2);

                List<EnemyAction> actions = new ArrayList<>();
                actions.add(new EnemyAction.LineAttack(
                    charge.start(), charge.dx(), charge.dz(), charge.tiles().size(), chargeDmg));
                if (isPhaseTwo()) {
                    actions.add(new EnemyAction.CreateTerrain(charge.tiles(), TileType.FIRE, 3));
                }
                EnemyAction resolved = actions.size() == 1 ? actions.get(0)
                    : new EnemyAction.CompositeAction(actions);

                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                    charge.tiles(), 1, resolved, 0xFFFF4444);
                return new EnemyAction.Idle();
            }
        }

        // Priority 3: Raise the Dead on schedule
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

        // Priority 4: Shield Bash if player is adjacent
        if (dist <= 1) {
            // Determine knockback direction: away from boss
            int kdx = Integer.signum(playerPos.x() - myPos.x());
            int kdz = Integer.signum(playerPos.z() - myPos.z());
            if (kdx == 0 && kdz == 0) kdx = 1; // fallback
            return new EnemyAction.ForcedMovement(-1, kdx, kdz, 2);
        }

        // Priority 5: Melee attack or approach
        return meleeOrApproach(self, arena, playerPos, isPhaseTwo() ? 2 : 0);
    }

    private ChargePattern findChargePattern(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        ChargePattern best = null;

        // Check charge from each occupied tile (1 for 1x1, 4 for 2x2)
        for (GridPos occupiedTile : GridArena.getOccupiedTiles(myPos, self.getSize())) {
            ChargePattern candidate = buildChargePattern(arena, occupiedTile, playerPos);
            if (candidate == null) continue;
            if (best == null || candidate.tiles().size() > best.tiles().size()) {
                best = candidate;
            }
        }

        // For 1x1 bosses: also try charging after a 1-tile sidestep to align
        // This compensates for not having multiple occupied tiles to check from
        if (best == null && self.getSize() == 1) {
            GridPos[] sidesteps = {
                new GridPos(myPos.x() + 1, myPos.z()),
                new GridPos(myPos.x() - 1, myPos.z()),
                new GridPos(myPos.x(), myPos.z() + 1),
                new GridPos(myPos.x(), myPos.z() - 1)
            };
            for (GridPos step : sidesteps) {
                if (!arena.isInBounds(step) || arena.isOccupied(step)) continue;
                var tile = arena.getTile(step);
                if (tile == null || !tile.isWalkable()) continue;
                ChargePattern candidate = buildChargePattern(arena, step, playerPos);
                if (candidate != null && (best == null || candidate.tiles().size() > best.tiles().size())) {
                    best = candidate;
                    // Include the sidestep move by prepending it to the charge
                    List<GridPos> fullPath = new java.util.ArrayList<>();
                    fullPath.add(step);
                    fullPath.addAll(candidate.tiles());
                    best = new ChargePattern(step, candidate.dx(), candidate.dz(), fullPath);
                }
            }
        }

        return best;
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

        List<GridPos> path = getChargePath(arena, startTile, dx, dz, 3);
        if (path.isEmpty() || !path.contains(playerPos)) {
            return null;
        }
        return new ChargePattern(path.get(0), dx, dz, path);
    }

    private record ChargePattern(GridPos start, int dx, int dz, List<GridPos> tiles) {}
}
