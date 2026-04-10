package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Warped Forest Boss — "The Void Walker" (Enderman)
 * Entity: Enderman | 50HP / 9ATK / 2DEF / Speed 3 (+ free teleports) | Size 2×2
 *
 * Abilities:
 * - Void Rift: Opens a portal pair (one near the player, one far away). Stepping through
 *   teleports the player between the endpoints. First traversal of a pair also grants
 *   Strength + Speed for 2 turns. Phase 1 rifts last 4 turns, Phase 2 rifts are permanent.
 *   Rift state lives in CombatManager — this AI just telegraphs the placement.
 * - Mirror Image: 2 clones (8HP/3ATK, take double damage). P2: 3 clones.
 * - Phase Strike: Teleport behind player + attack. Cannot be dodged.
 * - Void Pull: Pulls player 2 tiles toward boss. P2: 3 tiles. Can drag the player onto
 *   an active rift, triggering a forced teleport.
 * - Void Beam: Telegraphed LineAttack cast from the boss in the direction of the player.
 *   Deals ATK damage to any tile in the line. Mid-to-long range.
 * - Null Burst: Telegraphed AreaAttack on a 3×3 region around the player's current tile.
 *   Deals ATK-1 damage. Rewards the player for moving between turns.
 * - Ender Roar: Short-range AreaAttack centered on the boss itself. Deals ATK-2 damage
 *   and applies Blindness — punishes melee camping.
 *
 * Phase 2 — "Reality Shatter": Permanent rifts, 3 clones, double phase strike,
 * random blink start of turn, void pull range 3, Null Burst radius grows to 2.
 */
public class VoidWalkerAI extends BossAI {
    private static final String CD_RIFT = "void_rift";
    private static final String CD_MIRROR = "mirror_image";
    private static final String CD_STRIKE = "phase_strike";
    private static final String CD_PULL = "void_pull";
    private static final String CD_BEAM = "void_beam";
    private static final String CD_BURST = "null_burst";
    private static final String CD_ROAR = "ender_roar";

    /**
     * Mirror image clones use a separate AI instance flagged with isClone = true.
     * They retain almost the full boss kit — Phase Strike, Void Beam, Null Burst,
     * Ender Roar, Void Pull, melee — so the player can't tell them apart from the
     * real boss by behaviour. Only abilities that would duplicate boss-exclusive
     * state (summoning more clones, opening rifts, blinking at random) are gated.
     */
    private final boolean isClone;

    public VoidWalkerAI() { this(false); }
    public VoidWalkerAI(boolean isClone) { this.isClone = isClone; }

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        // Phase 2 rifts are spawned permanent by CombatManager (it reads HP on resolve).
        // Drop the rift cooldown so a fresh pair opens soon after the phase shift.
        if (!isClone) {
            setCooldown(CD_RIFT, 1);
        }
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();

        // Phase 2: Random blink at start of every turn (real boss only — clones
        // never enter phase 2 but we guard explicitly so future refactors are safe).
        if (!isClone && isPhaseTwo() && getTurnCounter() > 1) {
            List<GridPos> blinkTargets = findSummonPositions(arena, 1);
            if (!blinkTargets.isEmpty()) {
                GridPos blinkTo = blinkTargets.get(0);
                EnemyAction ability = chooseOffensiveAbility(self, arena, playerPos, blinkTo);
                if (ability != null) {
                    return new EnemyAction.CompositeAction(List.of(
                        new EnemyAction.Teleport(blinkTo),
                        ability
                    ));
                }
                return new EnemyAction.Teleport(blinkTo);
            }
        }

        EnemyAction action = chooseOffensiveAbility(self, arena, playerPos, myPos);
        if (action != null) return action;

        return meleeOrApproach(self, arena, playerPos, 0);
    }

    private EnemyAction chooseOffensiveAbility(CombatEntity self, GridArena arena,
                                                GridPos playerPos, GridPos effectivePos) {
        int dist = effectivePos.manhattanDistance(playerPos);
        int atk = self.getAttackPower();

        // Mirror Image — fires on a steady cadence so the player is regularly facing
        // a fresh set of decoys. High priority so it actually happens (was buried at
        // the bottom previously and almost never triggered).
        if (!isClone && !isOnCooldown(CD_MIRROR) && getAliveMinionCount() == 0) {
            setCooldown(CD_MIRROR, 5);
            int count = isPhaseTwo() ? 3 : 2;
            List<GridPos> clonePositions = findSummonPositionsNear(arena, effectivePos, 4, count);
            if (!clonePositions.isEmpty()) {
                return new EnemyAction.SummonMinions(
                    "minecraft:enderman", clonePositions.size(), clonePositions,
                    self.getMaxHp(), self.getAttackPower(), self.getDefense());
            }
        }

        // Ender Roar — short-range get-off-me AoE. Priority when player is right next to us.
        if (!isOnCooldown(CD_ROAR) && dist <= 2) {
            setCooldown(CD_ROAR, 5);
            int roarRadius = 2;
            java.util.List<GridPos> roarTiles = getAreaTiles(arena, effectivePos, roarRadius);
            int roarDmg = Math.max(1, atk - 2);
            EnemyAction roar = new EnemyAction.AreaAttack(effectivePos, roarRadius, roarDmg, "ender_roar");
            return new EnemyAction.BossAbility("ender_roar", roar, roarTiles);
        }

        // Void Beam — cardinal line from the boss through the player's row/column.
        // Prefer this over phase strike when player is at range, so the boss is not
        // just "teleport into melee every turn".
        if (!isOnCooldown(CD_BEAM) && dist >= 2) {
            int[] dir = getDirectionToward(effectivePos, playerPos);
            if (dir[0] != 0 || dir[1] != 0) {
                int beamLen = isPhaseTwo() ? 8 : 6;
                java.util.List<GridPos> beamTiles = getLineTiles(arena, effectivePos, dir[0], dir[1], beamLen);
                if (!beamTiles.isEmpty()) {
                    setCooldown(CD_BEAM, 4);
                    int beamDmg = atk;
                    EnemyAction beam = new EnemyAction.LineAttack(
                        effectivePos, dir[0], dir[1], beamLen, beamDmg);
                    return new EnemyAction.BossAbility("void_beam", beam, beamTiles);
                }
            }
        }

        // Null Burst — AoE centered on the player's current tile. Player has to move
        // off the telegraphed square to avoid damage, creating positional pressure.
        if (!isOnCooldown(CD_BURST) && dist >= 2 && dist <= 6) {
            setCooldown(CD_BURST, 4);
            int burstRadius = isPhaseTwo() ? 2 : 1;
            int burstDmg = Math.max(1, atk - 1);
            java.util.List<GridPos> burstTiles = getAreaTiles(arena, playerPos, burstRadius);
            EnemyAction burst = new EnemyAction.AreaAttack(playerPos, burstRadius, burstDmg, "null_burst");
            return new EnemyAction.BossAbility("null_burst", burst, burstTiles);
        }

        // Phase Strike — teleport behind and attack. Now gated so it does NOT fire every
        // cycle; the beam/burst will eat most of the open slots.
        if (!isOnCooldown(CD_STRIKE) && dist >= 2) {
            setCooldown(CD_STRIKE, 3);
            GridPos behind = findTileBehindPlayer(arena, playerPos, effectivePos);
            if (behind != null) {
                if (isPhaseTwo()) {
                    // Double phase strike
                    GridPos secondPos = findTileBehindPlayer(arena, playerPos, behind);
                    if (secondPos != null) {
                        return new EnemyAction.CompositeAction(List.of(
                            new EnemyAction.TeleportAndAttack(behind, atk),
                            new EnemyAction.TeleportAndAttack(secondPos, atk)
                        ));
                    }
                }
                return new EnemyAction.TeleportAndAttack(behind, atk);
            }
        }

        // Void Pull — pull player toward boss
        if (!isOnCooldown(CD_PULL) && dist >= 2 && dist <= 4) {
            setCooldown(CD_PULL, 2);
            int[] dir = getDirectionToward(playerPos, effectivePos);
            int pullDist = isPhaseTwo() ? 3 : 2;
            return new EnemyAction.ForcedMovement(-1, dir[0], dir[1], pullDist);
        }

        // Void Rift — place one endpoint near the player, the other far across the arena.
        // This makes the mechanic legible: the player can always see one portal right next
        // to them, and the other gives a meaningful reposition if they step through.
        // Clones cannot place rifts — that's a real-boss-only ability.
        if (!isClone && !isOnCooldown(CD_RIFT)) {
            setCooldown(CD_RIFT, 3);
            GridPos near = findRiftTileNear(arena, playerPos, effectivePos, 1, 3);
            GridPos far = findRiftTileFar(arena, playerPos, effectivePos, near, 5);
            if (near != null && far != null) {
                return new EnemyAction.BossAbility("void_rift",
                    new EnemyAction.Idle(), // Rifts are registered by CombatManager on warning resolve
                    List.of(near, far));
            }
        }

        // Adjacent melee
        if (dist <= 1) {
            return new EnemyAction.Attack(atk);
        }

        return null;
    }

    private GridPos findTileBehindPlayer(GridArena arena, GridPos playerPos, GridPos bossPos) {
        // "Behind" = opposite side of player from boss
        int[] dir = getDirectionToward(bossPos, playerPos);
        GridPos behind = new GridPos(playerPos.x() + dir[0], playerPos.z() + dir[1]);
        if (arena.isInBounds(behind) && !arena.isOccupied(behind)
                && arena.getTile(behind) != null && arena.getTile(behind).isWalkable()) {
            return behind;
        }
        // Try adjacent tiles if behind is blocked
        for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            GridPos alt = new GridPos(playerPos.x() + d[0], playerPos.z() + d[1]);
            if (arena.isInBounds(alt) && !arena.isOccupied(alt) && !alt.equals(bossPos)
                    && arena.getTile(alt) != null && arena.getTile(alt).isWalkable()) {
                return alt;
            }
        }
        return null;
    }

    /**
     * Find a walkable tile within [minDist, maxDist] manhattan of the player, biased
     * toward tiles away from the boss (so stepping on it doesn't just walk the player
     * into the boss's teeth). Returns null if nothing is available.
     */
    private GridPos findRiftTileNear(GridArena arena, GridPos playerPos, GridPos bossPos,
                                      int minDist, int maxDist) {
        List<GridPos> candidates = new ArrayList<>();
        for (int dx = -maxDist; dx <= maxDist; dx++) {
            for (int dz = -maxDist; dz <= maxDist; dz++) {
                GridPos pos = new GridPos(playerPos.x() + dx, playerPos.z() + dz);
                int d = Math.abs(dx) + Math.abs(dz);
                if (d < minDist || d > maxDist) continue;
                if (pos.equals(playerPos)) continue;
                if (!arena.isInBounds(pos) || arena.isOccupied(pos)) continue;
                var tile = arena.getTile(pos);
                if (tile == null || !tile.isWalkable()) continue;
                candidates.add(pos);
            }
        }
        if (candidates.isEmpty()) return null;
        // Prefer tiles that are NOT adjacent to the boss.
        candidates.sort((p1, p2) -> {
            int d1 = p1.manhattanDistance(bossPos);
            int d2 = p2.manhattanDistance(bossPos);
            return Integer.compare(d2, d1);
        });
        // Some randomness: pick from the top half.
        int pickRange = Math.max(1, candidates.size() / 2);
        return candidates.get((int) (Math.random() * pickRange));
    }

    /**
     * Find a walkable tile at least {@code minDistFromNear} tiles away from the near
     * rift endpoint, and as far as possible from the boss — gives the player a
     * meaningful "escape" target when they step through.
     */
    private GridPos findRiftTileFar(GridArena arena, GridPos playerPos, GridPos bossPos,
                                     GridPos near, int minDistFromNear) {
        if (near == null) return null;
        List<GridPos> candidates = new ArrayList<>();
        for (int x = 0; x < arena.getWidth(); x++) {
            for (int z = 0; z < arena.getHeight(); z++) {
                GridPos pos = new GridPos(x, z);
                if (pos.equals(near) || pos.equals(playerPos)) continue;
                if (pos.manhattanDistance(near) < minDistFromNear) continue;
                if (arena.isOccupied(pos)) continue;
                var tile = arena.getTile(pos);
                if (tile == null || !tile.isWalkable()) continue;
                candidates.add(pos);
            }
        }
        if (candidates.isEmpty()) return null;
        // Maximize distance from the near endpoint AND the boss so the rift meaningfully
        // relocates the player.
        candidates.sort((p1, p2) -> {
            int s1 = p1.manhattanDistance(near) + p1.manhattanDistance(bossPos);
            int s2 = p2.manhattanDistance(near) + p2.manhattanDistance(bossPos);
            return Integer.compare(s2, s1);
        });
        int pickRange = Math.max(1, Math.min(3, candidates.size()));
        return candidates.get((int) (Math.random() * pickRange));
    }
}
