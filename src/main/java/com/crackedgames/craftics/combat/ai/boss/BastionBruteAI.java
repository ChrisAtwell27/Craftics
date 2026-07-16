package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Crimson Forest Boss - "The Bastion Brute" (Skeleton warlord)
 * Entity: Skeleton | 65HP / 8ATK / 3DEF | Size 1×1
 *
 * Stats above are the authored baseline from data/craftics/craftics/biomes/crimson_forest.json.
 * LevelGenerator scales them by biome progress and CONFIG.bossHpMultiplier before the fight, so
 * the HP the player actually sees is higher; this is the number the tuning is written against.
 *
 * Base speed is 2, the skeleton default (crimson_forest.json authors no speed), and it is not a
 * fixed number any more: applySpeed is the single writer and adds the Banner of the March's +2
 * and the phase-2 frenzy's +1 on top, so the Brute runs at 4 with the March up and 2 once it
 * falls. Speed is what feeds Momentum, so this is the ramp's throttle rather than a stat line.
 *
 * Abilities:
 * - Gore Charge: Charges in a straight line toward the player (ignores speed), ATK+3 plus one
 *   damage per tile of the charge path. In multiplayer, chains through multiple players if
 *   possible. P2: leaves fire trail.
 * - Ground Slam: Cross-pattern FIRE terrain (magma cracks), 2 range, 3 turns.
 * - Rampage: All adjacent tiles (8), ATK damage each. P2: 2-tile radius.
 * - Summon Pack: 2 Piglins (8HP/4ATK/Range 3). Cooldown-based, repeatable. Gated on the Banner
 *   of the Horde.
 *
 * Momentum: every tile the Brute travels adds +1 to its next hit, so it inverts the usual boss
 * rule. The safest tile is the one next to it and kiting is what feeds it, while the kit already
 * punishes hugging (adjacent Rampage, Ground Slam's fire cross, a piglin pack that body-blocks
 * the retreat). Distance comes from the route Pathfinding actually built, so a Brute boxed in by
 * terrain earns only the tiles it truly covered rather than its nominal speed.
 *
 * Phase 2 - "Blood Frenzy": +4 ATK, fire trail on charge, rampage 2-tile radius,
 * speed 4.
 *
 * War banners: three, one of each, and asymmetric on purpose so breaking one is triage rather
 * than a chore. March grants +2 speed (and so feeds Momentum, which is damage per tile travelled),
 * Fury grants +3 flat ATK, Horde allows Summon Pack. Breaking the Horde stops only NEW summons;
 * piglins already on the field stay. None of them count toward room-clear.
 */
public class BastionBruteAI extends BossAI {
    private static final String CD_CHARGE = "gore_charge";
    private static final String CD_SLAM = "ground_slam";
    private static final String CD_RAMPAGE = "rampage";
    private static final String CD_SUMMON = "summon_pack";

    /**
     * The three banners are asymmetric, so which one the player breaks is a triage decision:
     * the March if they cannot escape, Fury if they are being shredded, the Horde if they are
     * boxed in. Each answers a different problem, so identity has to be tracked, not just a
     * count of what is left standing.
     *
     * <p>Each carries its own kill message, so the break is legible the instant it happens.
     */
    private enum BannerType {
        MARCH("§c✦ Banner of the March torn down! The Brute slows."),
        FURY("§c✦ Banner of Fury torn down! The Brute hits softer."),
        HORDE("§c✦ Banner of the Horde torn down! No more reinforcements.");

        private final String killMessage;

        BannerType(String killMessage) {
            this.killMessage = killMessage;
        }
    }

    /**
     * A banner to place: which tile, and the identity the caller needs to draw and announce it.
     * The enum stays private so banner identity has exactly one owner; the caller gets the block
     * to place and nothing it could get out of sync with.
     */
    public record BannerPlacement(GridPos pos, String typeName, String killMessage) {}

    // Living war banners by tile. Identity matters: onWarBannerDestroyed has only the grid
    // position to go on, so the map is what tells it which lever just went away.
    private final java.util.Map<GridPos, BannerType> warBanners = new java.util.HashMap<>();
    /** Tiles the Brute travelled on its last turn, spent on its next hit. */
    private int pendingMomentum = 0;
    private boolean speedInitialised = false;

    private boolean isBannerAlive(BannerType type) {
        return warBanners.containsValue(type);
    }

    /**
     * The single writer for the Brute's speed bonus. The March banner and the phase-2 frenzy both
     * feed it, so they must not call setSpeedBonus directly: two writers would clobber each other
     * and the last one to run would silently undo the other.
     */
    private void applySpeed(CombatEntity self) {
        int march = Momentum.marchSpeedBonus(isBannerAlive(BannerType.MARCH));
        self.setSpeedBonus(march + (isPhaseTwo() ? 1 : 0));
    }

    /**
     * Fury's attack is added at the point of use rather than written to the entity, unlike speed.
     * Both of CombatEntity's attack channels are shared: setAttackPenalty is written by player
     * debuffs (weakness, sherds, Simply Swords) and reset to 0 wholesale when they expire, and
     * setAttackBoost is the ally stat-minimum poke. A banner writing either would be clobbered by
     * an unrelated system, or would itself wipe a player's debuff. Deriving it keeps the banner
     * out of that shared state entirely, so there is only ever one writer per channel.
     */
    private int attackWithBanners(CombatEntity self) {
        return self.getAttackPower()
            + Momentum.furyAttackBonus(isBannerAlive(BannerType.FURY))
            + (isPhaseTwo() ? 4 : 0);
    }

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        applySpeed(self); // Blood Frenzy adds +1 on top of whatever banners still stand
    }

    /**
     * Pick banner tiles at arena build. Banners sit AWAY from the boss on purpose: walking over
     * to break one means leaving its face, which hands it exactly the runway Momentum wants.
     *
     * <p>Returns one placement per banner: one of each type, so all three levers are always in
     * the fight. A random draw could deal the same banner twice and silently delete a counterplay
     * option. Tile choice is still shuffled, so the player cannot memorise which corner is which.
     */
    public List<BannerPlacement> initWarBanners(GridArena arena) {
        int w = arena.getWidth(), h = arena.getHeight();
        // Prefer tiles the player can actually walk to. Unlike the Broodmother's egg sacs this is
        // NOT a soft-lock gate (banners do not count toward room-clear, so an unreachable one
        // cannot strand the run) but a banner the player cannot reach is a mechanic they cannot
        // engage with, so reachable tiles are taken first and unreachable ones only fill a gap.
        java.util.Set<GridPos> reachable = com.crackedgames.craftics.combat.Pathfinding
            .getReachableTiles(arena, arena.getPlayerGridPos(), Integer.MAX_VALUE, true, false);

        List<GridPos> candidates = new ArrayList<>();
        candidates.add(new GridPos(1, 1));
        candidates.add(new GridPos(w - 2, 1));
        candidates.add(new GridPos(1, h - 2));
        candidates.add(new GridPos(w - 2, h - 2));
        candidates.add(new GridPos(w / 2, 1));
        candidates.add(new GridPos(w / 2, h - 2));
        Collections.shuffle(candidates);

        List<GridPos> chosen = new ArrayList<>();
        for (GridPos pos : candidates) {
            if (chosen.size() >= 3) break;
            if (isValidBannerTile(arena, pos) && reachable.contains(pos)) {
                chosen.add(pos);
            }
        }
        // Second pass: an unreachable banner is worse than a reachable one but better than no
        // banner, since the speed it grants is the whole point of the object.
        if (chosen.size() < 3) {
            for (GridPos pos : candidates) {
                if (chosen.size() >= 3) break;
                if (chosen.contains(pos)) continue;
                if (isValidBannerTile(arena, pos)) {
                    chosen.add(pos);
                }
            }
        }
        // Pair each tile with a distinct banner. Fewer tiles than types is possible on a cramped
        // arena, in which case the later types simply do not appear; the AI reads each banner's
        // presence independently, so a missing one just leaves that bonus off.
        List<BannerPlacement> placements = new ArrayList<>();
        BannerType[] types = BannerType.values();
        for (int i = 0; i < chosen.size() && i < types.length; i++) {
            placements.add(new BannerPlacement(
                chosen.get(i), types[i].name().toLowerCase(java.util.Locale.ROOT),
                types[i].killMessage));
        }
        return placements;
    }

    /** A banner tile must be in bounds, unoccupied, and safe to stand on. */
    private static boolean isValidBannerTile(GridArena arena, GridPos pos) {
        if (!arena.isInBounds(pos) || arena.isOccupied(pos)) return false;
        var tile = arena.getTile(pos);
        return tile != null && tile.isSafeForSpawn();
    }

    /** Register a banner the grid actually accepted, using the placement initWarBanners issued. */
    public void registerWarBanner(BannerPlacement placement) {
        warBanners.put(placement.pos(),
            BannerType.valueOf(placement.typeName().toUpperCase(java.util.Locale.ROOT)));
    }

    /**
     * Returns the message for the banner that stood on this tile, or null if it was not a banner
     * tile. Speed is re-applied because the March may have been the one that fell; the other two
     * are read at the point of use, so nothing else needs poking here.
     */
    public String onWarBannerDestroyed(GridPos pos, CombatEntity self) {
        BannerType lost = warBanners.remove(pos);
        applySpeed(self);
        return lost != null ? lost.killMessage : null;
    }

    public int getBannerCount() { return warBanners.size(); }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        int atk = attackWithBanners(self);

        // Banners are registered after the AI is constructed, so the opening speed can only be
        // applied once the fight is actually running.
        if (!speedInitialised) {
            speedInitialised = true;
            applySpeed(self);
        }

        // Momentum carries across turns: on the normal path the Brute moves OR attacks, never
        // both in one action, so the distance it covered last turn is what lands on this turn's
        // hit. This also makes the ramp readable: the player watches it build speed and can
        // choose to close before it cashes in. Gore Charge is the exception and reads its own
        // path directly, because Swoop moves and damages in a single action.
        int momentum = pendingMomentum;
        pendingMomentum = 0;

        // Summon Pack - repeatable on cooldown, only when no minions alive.
        // The cooldown is paid only on a successful summon so a briefly-full
        // arena doesn't lock the ability out for 4 turns over nothing.
        // Gated on the Banner of the Horde: breaking it stops NEW summons only, so piglins
        // already on the field stay and the player still has to deal with them.
        if (isBannerAlive(BannerType.HORDE)
                && !isOnCooldown(CD_SUMMON) && getAliveMinionCount() == 0 && dist >= 2) {
            List<GridPos> spawnPositions = findSummonPositions(arena, 2);
            if (!spawnPositions.isEmpty()) {
                setCooldown(CD_SUMMON, 4);
                return new EnemyAction.SummonMinions(
                    "minecraft:piglin", spawnPositions.size(), spawnPositions, 8, 4, 0);
            }
        }

        // Gore Charge - charge in a straight line toward the player (ignores speed)
        // Uses Swoop to physically move the boss along the path.
        // In multiplayer, chains through multiple players.
        if (!isOnCooldown(CD_CHARGE) && dist >= 3) {
            List<GridPos> allPlayers = arena.getAllPlayerGridPositions();
            List<GridPos> chargePath;
            if (allPlayers.size() > 1) {
                chargePath = buildChainChargePath(arena, myPos, playerPos, allPlayers);
            } else {
                int[] dir = getDirectionToward(myPos, playerPos);
                chargePath = buildChargePath(arena, myPos, dir[0], dir[1], dist - 1);
            }
            if (!chargePath.isEmpty()) {
                setCooldown(CD_CHARGE, 3);
                EnemyAction chargeAction;
                // Swoop moves and damages in one action, so the charge reads its own path length
                // rather than the cross-turn pendingMomentum: a long charge hits proportionally
                // harder, which is the whole point of the ramp.
                int chargeDamage = atk + 3 + Momentum.bonusForTilesMoved(chargePath.size());
                if (isPhaseTwo()) {
                    // Gore Charge + fire trail in Phase 2
                    chargeAction = new EnemyAction.CompositeAction(List.of(
                        new EnemyAction.Swoop(chargePath, chargeDamage),
                        new EnemyAction.CreateTerrain(chargePath, TileType.FIRE, 2)
                    ));
                } else {
                    chargeAction = new EnemyAction.Swoop(chargePath, chargeDamage);
                }
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                    chargePath, 1, chargeAction, 0xFFCC0000);
                return advanceWhileCharging(self, arena, playerPos);
            }
        }

        // Rampage - if adjacent or close
        if (!isOnCooldown(CD_RAMPAGE) && dist <= (isPhaseTwo() ? 2 : 1)) {
            setCooldown(CD_RAMPAGE, 2);
            int radius = isPhaseTwo() ? 2 : 1;
            return new EnemyAction.AreaAttack(myPos, radius, atk, "rampage");
        }

        // Ground Slam - cross-pattern fire terrain around the boss
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
            return new EnemyAction.Attack(atk + Momentum.bonusForTilesMoved(momentum));
        }

        return recordMomentum(atk, meleeOrApproach(self, arena, playerPos,
            Momentum.bonusForTilesMoved(momentum)));
    }

    /**
     * Settle Momentum against the action an approach actually produced.
     *
     * <p>The path on the returned action is the real route Pathfinding built, not the Brute's
     * nominal speed, so a Brute boxed in by terrain earns only the tiles it truly covered.
     *
     * <p>seekOrWander returns MoveAndAttack when the walk ends adjacent, which is the one normal
     * action that moves and hits at once. Its damage is rebuilt from the distance it just walked
     * rather than the stale cross-turn value, and nothing is banked: momentum that lands in the
     * same action must not also be spent on the next one.
     */
    private EnemyAction recordMomentum(int atk, EnemyAction action) {
        if (action instanceof EnemyAction.Move move) {
            pendingMomentum = move.path().size();
        } else if (action instanceof EnemyAction.MoveAndAttack maa) {
            return new EnemyAction.MoveAndAttack(maa.path(),
                atk + Momentum.bonusForTilesMoved(maa.path().size()));
        }
        return action;
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
            // Stop at anything un-walkable - the old OBSTACLE/VOID-only check
            // let the charge plow into deep water and end the boss on a tile
            // it can't stand on. (FIRE/LAVA are walkable, so charging through
            // flames still works - it's hazard-immune anyway.)
            if (tile == null || !tile.isWalkable()) break;
            path.add(next);
            current = next;
        }
        return path;
    }
}
