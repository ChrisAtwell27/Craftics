package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.Comparator;
import java.util.List;

/**
 * Crimson Forest Boss — "The Bastion Brute" (Skeleton warlord)
 * Entity: Skeleton | 45HP / 8ATK / 3DEF / Speed 3 | Size 1×1
 *
 * Abilities:
 * - Gore Charge: Charges in a straight line toward the player (ignores speed), ATK+3.
 *   In multiplayer, chains through multiple players if possible. P2: leaves fire trail.
 * - Ground Slam: Cross-pattern FIRE terrain (magma cracks), 2 range, 3 turns.
 * - Rampage: All adjacent tiles (8), ATK damage each. P2: 2-tile radius.
 * - Summon Pack: 2 Piglins (8HP/4ATK/Range 3). Cooldown-based, repeatable.
 *
 * Phase 2 — "Blood Frenzy": +4 ATK, fire trail on charge, rampage 2-tile radius,
 * speed 4.
 */
public class BastionBruteAI extends BossAI {
    private static final String CD_CHARGE = "gore_charge";
    private static final String CD_SLAM = "ground_slam";
    private static final String CD_RAMPAGE = "rampage";
    private static final String CD_SUMMON = "summon_pack";

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        self.setSpeedBonus(1); // Speed 3 → 4
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        int atk = self.getAttackPower() + (isPhaseTwo() ? 4 : 0);

        // Summon Pack — repeatable on cooldown, only when no minions alive
        if (!isOnCooldown(CD_SUMMON) && getAliveMinionCount() == 0 && dist >= 2) {
            setCooldown(CD_SUMMON, 4);
            List<GridPos> spawnPositions = findSummonPositions(arena, 2);
            if (!spawnPositions.isEmpty()) {
                return new EnemyAction.SummonMinions(
                    "minecraft:piglin", spawnPositions.size(), spawnPositions, 8, 4, 0);
            }
        }

        // Gore Charge — charge in a straight line toward the player (ignores speed)
        // Uses Swoop to physically move the boss along the path.
        // In multiplayer, chains through multiple players.
        if (!isOnCooldown(CD_CHARGE) && dist >= 3) {
            setCooldown(CD_CHARGE, 3);
            List<GridPos> allPlayers = arena.getAllPlayerGridPositions();
            List<GridPos> chargePath;
            if (allPlayers.size() > 1) {
                chargePath = buildChainChargePath(arena, myPos, playerPos, allPlayers);
            } else {
                int[] dir = getDirectionToward(myPos, playerPos);
                chargePath = buildChargePath(arena, myPos, dir[0], dir[1], dist - 1);
            }
            if (!chargePath.isEmpty()) {
                EnemyAction chargeAction;
                if (isPhaseTwo()) {
                    // Gore Charge + fire trail in Phase 2
                    chargeAction = new EnemyAction.CompositeAction(List.of(
                        new EnemyAction.Swoop(chargePath, atk + 3),
                        new EnemyAction.CreateTerrain(chargePath, TileType.FIRE, 2)
                    ));
                } else {
                    chargeAction = new EnemyAction.Swoop(chargePath, atk + 3);
                }
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                    chargePath, 1, chargeAction, 0xFFCC0000);
                return advanceWhileCharging(self, arena, playerPos);
            }
        }

        // Rampage — if adjacent or close
        if (!isOnCooldown(CD_RAMPAGE) && dist <= (isPhaseTwo() ? 2 : 1)) {
            setCooldown(CD_RAMPAGE, 2);
            int radius = isPhaseTwo() ? 2 : 1;
            List<GridPos> rampageTiles = getAreaTiles(arena, myPos, radius);
            return new EnemyAction.AreaAttack(myPos, radius, atk, "rampage");
        }

        // Ground Slam — cross-pattern fire terrain around the boss
        if (!isOnCooldown(CD_SLAM) && dist <= 3) {
            setCooldown(CD_SLAM, 3);
            int slamRange = isPhaseTwo() ? 3 : 2;
            List<GridPos> slamTiles = getCrossTiles(arena, myPos, slamRange);
            return new EnemyAction.BossAbility("ground_slam",
                new EnemyAction.CreateTerrain(slamTiles, TileType.FIRE, 3),
                slamTiles);
        }

        // Melee attack if adjacent
        if (dist <= 1) {
            return new EnemyAction.Attack(atk);
        }

        return meleeOrApproach(self, arena, playerPos, 0);
    }

    /**
     * Build a chain charge path: boss → through primary player → toward nearest other player.
     * The boss charges THROUGH the first player's position and redirects toward the next.
     * First segment uses full axial distance (charges through), last segment stops 1 before.
     */
    private List<GridPos> buildChainChargePath(GridArena arena, GridPos start, GridPos primaryTarget,
                                                List<GridPos> allPlayerPositions) {
        List<GridPos> fullPath = new java.util.ArrayList<>();

        // First segment: charge toward primary target, going THROUGH their position
        int[] dir1 = getDirectionToward(start, primaryTarget);
        int axialDist1 = dir1[0] != 0
            ? Math.abs(primaryTarget.x() - start.x())
            : Math.abs(primaryTarget.z() - start.z());
        List<GridPos> seg1 = buildChargePath(arena, start, dir1[0], dir1[1], axialDist1);
        fullPath.addAll(seg1);

        // Chain to the closest other player
        if (!seg1.isEmpty()) {
            GridPos chainFrom = seg1.get(seg1.size() - 1);
            allPlayerPositions.stream()
                .filter(p -> !p.equals(primaryTarget))
                .min(Comparator.comparingInt(p -> chainFrom.manhattanDistance(p)))
                .ifPresent(nextTarget -> {
                    int[] dir2 = getDirectionToward(chainFrom, nextTarget);
                    int axialDist2 = dir2[0] != 0
                        ? Math.abs(nextTarget.x() - chainFrom.x())
                        : Math.abs(nextTarget.z() - chainFrom.z());
                    if (axialDist2 >= 1) {
                        int maxTiles2 = Math.max(1, axialDist2 - 1);
                        List<GridPos> seg2 = buildChargePath(arena, chainFrom, dir2[0], dir2[1], maxTiles2);
                        fullPath.addAll(seg2);
                    }
                });
        }

        // Fallback: if chain produced empty path (all blocked), use single-target charge
        if (fullPath.isEmpty()) {
            int[] dir = getDirectionToward(start, primaryTarget);
            int dist = start.manhattanDistance(primaryTarget);
            return buildChargePath(arena, start, dir[0], dir[1], Math.max(1, dist - 1));
        }

        return fullPath;
    }

    /**
     * Build a charge path in a straight line, stopping at obstacles or arena edge.
     * The boss charges up to maxTiles, but stops before hitting an unwalkable tile.
     */
    private List<GridPos> buildChargePath(GridArena arena, GridPos start, int dx, int dz, int maxTiles) {
        List<GridPos> path = new java.util.ArrayList<>();
        GridPos current = start;
        for (int i = 0; i < maxTiles; i++) {
            GridPos next = new GridPos(current.x() + dx, current.z() + dz);
            if (!arena.isInBounds(next)) break;
            var tile = arena.getTile(next);
            if (tile != null && tile.getType() == TileType.OBSTACLE) break;
            if (tile != null && tile.getType() == TileType.VOID) break;
            path.add(next);
            current = next;
        }
        return path;
    }
}
