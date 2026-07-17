package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Plains Boss - "The Revenant" (Undead Knight)
 * Entity: Zombie | 30HP / 1ATK / 1DEF / Speed 1 | Size 1×1
 *
 * Stats above are the authored baseline from data/craftics/craftics/biomes/plains.json.
 * LevelGenerator scales them by biome progress and CONFIG.bossHpMultiplier before the fight, so
 * the HP the player actually sees is higher; this is the number the tuning is written against.
 * Speed 1 is the zombie default (plains.json authors none) and nothing here raises it: this boss
 * is slow on purpose and pressures through the graves rather than by closing. Size is 1x1 because
 * this AI does not override BossAI.getGridSize (which returns 1) and a zombie's footprint is 1x1.
 *
 * Abilities:
 * - Raise the Dead: Every other turn, a zombie claws up from a free tile ADJACENT to a living
 *   grave. Graves are the source, so they are also the counterplay: living graves cap living
 *   zombies one each, and with no graves left there is no raise at all. A grave whose neighbours
 *   are all blocked fizzles, so body-blocking one is a legitimate stall.
 * - Death Charge: Charges in a straight line from core position up to 3 tiles, ATK+2 damage. P2: ATK+4 + fire trail.
 * - Gravefire Grid: Telegraphs a giant checker-grid of magma tiles for 1 turn.
 * - Burrow: Digs under and is UNTARGETABLE for one full player round, then erupts beside a random
 *   living grave. It cannot burrow with no graves standing, so the graves are its escape network:
 *   every one still up is a place it can reappear, and the last one broken is what pins it down.
 *   Breaking that last grave WHILE it is under drags it straight back out at the tile it dug from,
 *   retreat wasted, so cutting the network is the strongest answer to the ability rather than a
 *   stalemate. The player's dilemma each turn is to hit the boss or spend the turn on a grave
 *   while it cannot be touched.
 *
 * Graves are 50 HP cobblestone walls, three at arena build. They do not count toward room-clear,
 * so breaking them is optional: the player chooses between cutting the stream and fighting the
 * boss. Raises never escalate, so ignoring graves does not compound; it simply never improves.
 * The zombie-head markers still telegraph each raise and can still be smashed for a one-off deny.
 *
 * Phase 2 (≤50% HP) - "Undying Rage":
 * - Gains Regeneration (1 HP/turn) - handled by CombatManager checking isEnraged
 * - Tears out 2 MORE graves, so the ceiling the player thought they had lowered jumps back up.
 * - Every raise ALSO tears a Grave Skull out of a living grave: a 1-HP homing skull entity
 *   (SeekingProjectileAI, 2 tiles/turn) that bites for 3 + 2 turns of Wither. Skulls come from
 *   graves too, so destroying a grave cuts the skulls as well as the zombies, and with no graves
 *   left there is no skull. Skulls are projectiles, not minions, so they ignore the zombie cap.
 * - Death Charge ATK+4 + fire trail
 */
public class RevenantAI extends BossAI {
    private static final String CD_RAISE = "raise_dead";
    private static final String CD_CHARGE = "death_charge";
    private static final String CD_GRAVEFIRE_GRID = "gravefire_grid";
    private static final String CD_BURROW = "burrow";

    /**
     * Turns between burrows. A full untargetable round on a boss that also summons reads as
     * stalling if it comes around every other turn, so this sits at the long end of the range.
     */
    private static final int BURROW_COOLDOWN = 4;

    /** Graves torn out when the Revenant enters phase 2, on top of the three placed at build. */
    private static final int PHASE_TWO_GRAVES = 2;

    // Living graves by tile. This list IS the spawn cap: one living zombie per living grave, and
    // an empty list means no raise at all.
    private final List<GridPos> graves = new ArrayList<>();

    // Set on the phase-2 transition, consumed by the next chooseAbility. onPhaseTransition returns
    // void and runs before chooseAbility on the same turn, so it cannot emit the placement action
    // itself; mid-fight placement has to travel out as a SummonMinions for CombatManager to
    // intercept, the same route the Broodmother's mid-fight sacs take.
    private boolean pendingPhaseTwoGraves = false;

    // Burrow state. `burrowed` is the AI's view; the untargetable flag on the CombatEntity is the
    // authority for damage and targeting, and CombatManager keeps the two in step. Anything that
    // clears one MUST clear the other, or the boss is unkillable and the room never clears.
    private boolean burrowed = false;

    // The tile the boss dug in from. Kept so that losing the last grave mid-burrow can force it
    // back out exactly where it went under, rather than handing it a free reposition.
    private GridPos burrowOrigin = null;

    public boolean isBurrowed() { return burrowed; }

    public GridPos getBurrowOrigin() { return burrowOrigin; }

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        pendingPhaseTwoGraves = true;
    }

    /**
     * Pick grave tiles at arena build. Each grave needs a free neighbour to raise onto, so prefer
     * open ground: a grave jammed against a wall fizzles more often than it raises.
     *
     * <p>Reachable tiles are taken first, but unreachable ones still fill a gap. Unlike the
     * Broodmother's egg sacs this is NOT a soft-lock gate (graves do not count toward room-clear,
     * so an unreachable one cannot strand the run) but a grave the player cannot reach is a
     * mechanic they cannot engage with.
     */
    public List<GridPos> initGraves(GridArena arena) {
        int w = arena.getWidth(), h = arena.getHeight();
        java.util.Set<GridPos> reachable = com.crackedgames.craftics.combat.Pathfinding
            .getReachableTiles(arena, arena.getPlayerGridPos(), Integer.MAX_VALUE, true, false);

        List<GridPos> candidates = new ArrayList<>();
        candidates.add(new GridPos(2, 2));
        candidates.add(new GridPos(w - 3, 2));
        candidates.add(new GridPos(2, h - 3));
        candidates.add(new GridPos(w - 3, h - 3));
        candidates.add(new GridPos(w / 2, 2));
        candidates.add(new GridPos(w / 2, h - 3));
        Collections.shuffle(candidates);

        List<GridPos> chosen = new ArrayList<>();
        for (GridPos pos : candidates) {
            if (chosen.size() >= 3) break;
            if (isValidGraveTile(arena, pos) && reachable.contains(pos)) {
                chosen.add(pos);
            }
        }
        if (chosen.size() < 3) {
            for (GridPos pos : candidates) {
                if (chosen.size() >= 3) break;
                if (chosen.contains(pos)) continue;
                if (isValidGraveTile(arena, pos)) {
                    chosen.add(pos);
                }
            }
        }
        // Deliberately NOT added to `graves` here. The caller registers only the tiles the grid
        // actually accepted, or the AI would count a grave the player has no tile to attack.
        return chosen;
    }

    /** A grave tile must be in bounds, unoccupied, and safe to stand on. */
    private static boolean isValidGraveTile(GridArena arena, GridPos pos) {
        if (!arena.isInBounds(pos) || arena.isOccupied(pos)) return false;
        var tile = arena.getTile(pos);
        return tile != null && tile.isSafeForSpawn();
    }

    /** Register a grave the grid actually accepted. */
    public void registerGrave(GridPos pos) {
        if (!graves.contains(pos)) graves.add(pos);
    }

    public void onGraveDestroyed(GridPos pos) { graves.remove(pos); }

    public int getGraveCount() { return graves.size(); }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        int dist = self.minDistanceTo(playerPos);
        // The raise is every other turn in both phases. Phase 2 does not raise faster or harder:
        // it widens the cap by tearing out more graves instead, so ignoring graves never
        // compounds, it simply never improves.
        int summonInterval = 2;

        // Underground: the turn it spent under is over, so it comes up now. This runs ahead of
        // every other priority because a burrowed boss has no other legal action, and never
        // falls through to one: a Surface with a null anchor still surfaces (in place) rather
        // than leaving the boss stuck under with the untargetable flag set.
        if (burrowed) {
            return new EnemyAction.Surface(pickSurfacingAnchor(self, arena));
        }

        // Priority 0: Undying Rage just tore the ground open. Placing the extra graves is the
        // phase-2 beat, so it goes ahead of everything rather than waiting for a free turn.
        if (pendingPhaseTwoGraves) {
            EnemyAction newGraves = tryPlacePhaseTwoGraves(self, arena);
            if (newGraves != null) return newGraves;
        }

        // Priority 1: Resolve pending warning (handled by base class)

        // Priority 2: Death Charge if the player is in a clear lane.
        if (!isOnCooldown(CD_CHARGE)) {
            ChargePattern charge = findChargePattern(self, arena, playerPos);
            if (charge != null) {
                int chargeDmg = self.getAttackPower() + (isPhaseTwo() ? 4 : 2);
                setCooldown(CD_CHARGE, 2);

                // DIRECTIONAL + charge direction: the client draws marching arrows
                // along the telegraphed lane so the dash reads as "coming THIS way",
                // not just "these tiles are red".
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.DIRECTIONAL,
                    charge.warningTiles(), 1, new EnemyAction.MoveAndAttack(charge.path(), chargeDmg), 0xFFFF4444,
                    charge.dx(), charge.dz());
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

        // Priority 4: Burrow. This sits ABOVE the raise on purpose. Both want the same turn (the
        // raise is up every other turn, and dist <= 2 is exactly when the player has closed), and
        // the raise winning every time is what made this ability never fire at all. Being cornered
        // should trigger the escape, not another summon. The cooldown is what stops it stalling.
        //
        // No living graves means no burrow: with the network cut there is nowhere to reappear, so
        // the retreat is simply unavailable. CombatManager does the untargetable toggle and the
        // tile bookkeeping; the AI only decides and remembers where it went under.
        if (!isOnCooldown(CD_BURROW) && !graves.isEmpty() && dist <= 2) {
            setCooldown(CD_BURROW, BURROW_COOLDOWN);
            burrowed = true;
            burrowOrigin = self.getGridPos();
            return new EnemyAction.Burrow();
        }

        // Priority 5: Raise the Dead, every other turn, out of the graves themselves.
        // No living graves means no raise: destroying them all shuts the stream off, which is
        // the whole reward for spending turns on them.
        if (!isOnCooldown(CD_RAISE) && !graves.isEmpty()) {
            int playerCount = arena.getAllPlayerGridPositions().size();
            // Living graves cap living zombies one each, the same shape as the Broodmother's egg
            // sacs capping spiders. Grave Skulls are projectiles, not minions, so they are not in
            // getAliveMinionCount and correctly do not eat into this cap.
            int canSpawn = GraveSpawning.zombieCap(graves.size(), playerCount) - getAliveMinionCount();

            List<GridPos> summonTiles = new ArrayList<>();
            if (canSpawn > 0) {
                for (GridPos grave : graves) {
                    if (summonTiles.size() >= canSpawn) break;
                    // A grave with every neighbour blocked simply fizzles. Body-blocking a grave
                    // is legitimate counterplay, so it is NOT special-cased into a fallback tile
                    // somewhere else on the arena.
                    GridPos rise = findFreeTileAdjacentTo(arena, grave, summonTiles);
                    if (rise != null) summonTiles.add(rise);
                }
            }

            // In phase 2 the raise also tears a Grave Skull out of a living grave, so destroying
            // a grave cuts the skulls as well as the zombies.
            GridPos skullTile = isPhaseTwo() ? pickSkullGrave() : null;

            // Fizzled entirely: every grave is blocked or the cap is full, and there is no skull.
            // Fall through to a real action rather than burning the turn on nothing.
            if (!summonTiles.isEmpty() || skullTile != null) {
                setCooldown(CD_RAISE, summonInterval);
                if (!summonTiles.isEmpty()) {
                    // Telegraph: ground cracks on the rise tiles, resolves next turn. The
                    // zombie-head markers CombatManager syncs off this warning are still
                    // smashable for a one-off deny.
                    EnemyAction summon = new EnemyAction.SummonMinions(
                        "minecraft:zombie", summonTiles.size(), summonTiles, 6, 2, 0);
                    pendingWarning = new BossWarning(
                        self.getEntityId(), BossWarning.WarningType.GROUND_CRACK,
                        summonTiles, 1, summon, 0xFF44FF44);
                }
                if (skullTile != null) {
                    int sdx = Integer.signum(playerPos.x() - skullTile.x());
                    int sdz = Integer.signum(playerPos.z() - skullTile.z());
                    return new EnemyAction.SpawnProjectile(
                        "minecraft:blaze", List.of(skullTile),
                        List.of(sdx != 0 ? new int[]{sdx, 0} : new int[]{0, sdz != 0 ? sdz : 1}),
                        1, 3, 0, "grave_skull");
                }
                return new EnemyAction.Idle();
            }
        }

        // Priority 6: Melee attack or approach
        return meleeOrApproach(self, arena, playerPos, isPhaseTwo() ? 2 : 0);
    }

    /**
     * Where to erupt: beside a RANDOM living grave, not the nearest. Random keeps the escape
     * network unpredictable, so every standing grave is a threat rather than a solved distance
     * problem. Graves are tried in shuffled order and the first with a legal anchor wins.
     *
     * <p>Returns null when nothing fits anywhere, which the caller MUST still treat as a surface
     * (in place). Null means "no better tile", never "stay under".
     */
    private GridPos pickSurfacingAnchor(CombatEntity self, GridArena arena) {
        List<GridPos> shuffled = new ArrayList<>(graves);
        Collections.shuffle(shuffled);
        for (GridPos grave : shuffled) {
            GridPos anchor = firstFittingAnchor(self, arena, grave);
            if (anchor != null) return anchor;
        }
        return null;
    }

    /**
     * The first anchor around {@code grave} whose footprint the arena actually accepts, or null.
     * Validated with canPlaceFootprintIgnoringSelf so the boss's own tiles do not veto its own
     * landing, which matters while it is still registered at its pre-burrow position.
     */
    private static GridPos firstFittingAnchor(CombatEntity self, GridArena arena, GridPos grave) {
        for (GridPos anchor : surfacingAnchors(grave, self.getSizeX(), self.getSizeZ())) {
            if (com.crackedgames.craftics.combat.ai.AIUtils
                    .canPlaceFootprintIgnoringSelf(arena, anchor, self)) {
                return anchor;
            }
        }
        return null;
    }

    /**
     * The player broke the last grave while the boss was under. The escape network is gone, so
     * the retreat fails outright: it claws out at the tile it dug from with no reposition. Called
     * by CombatManager on grave death; returns the anchor to surface at, or null if it was not
     * burrowed and nothing should happen.
     *
     * <p>Clearing {@code burrowed} here is what stops the boss waiting underground for a grave
     * that is never coming back.
     */
    public GridPos forceSurfaceOnLastGraveLost(CombatEntity self, GridArena arena) {
        if (!burrowed) return null;
        burrowed = false;
        GridPos origin = burrowOrigin;
        burrowOrigin = null;
        // The origin can be taken now (a zombie or the player stepped in while it was under).
        // Fail safe to the nearest legal anchor rather than overwriting whoever is standing
        // there; a null result still surfaces in place, because a boss that cannot find a tile
        // must still become targetable.
        if (origin != null
                && com.crackedgames.craftics.combat.ai.AIUtils
                    .canPlaceFootprintIgnoringSelf(arena, origin, self)) {
            return origin;
        }
        return origin == null ? null : nearestFittingAnchor(self, arena, origin);
    }

    /** Widening ring search for a legal footprint anchor near {@code around}, or null. */
    private static GridPos nearestFittingAnchor(CombatEntity self, GridArena arena, GridPos around) {
        for (int r = 1; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    GridPos anchor = new GridPos(around.x() + dx, around.z() + dz);
                    if (com.crackedgames.craftics.combat.ai.AIUtils
                            .canPlaceFootprintIgnoringSelf(arena, anchor, self)) {
                        return anchor;
                    }
                }
            }
        }
        return null;
    }

    /** Clears burrow state. The surface handler calls this so the AI and the entity flag agree. */
    public void clearBurrowState() {
        burrowed = false;
        burrowOrigin = null;
    }

    /**
     * Anchors for a {@code sizeX x sizeZ} footprint that touches {@code grave} without covering it,
     * ordered nearest-first around the grave. Pure geometry: the caller filters these through the
     * arena to find the first one that actually fits.
     *
     * <p>The boss cannot land on the grave's own tile because the grave is a real 1x1 combatant
     * standing there, so every anchor here is offset to sit beside it. Erupting on top of the grave
     * would silently destroy the very thing the ability depends on. Size is read from the entity
     * rather than assumed: this boss is 1x1 (which reduces this to the grave's 4 orthogonal
     * neighbours), but the geometry holds for any footprint.
     *
     * <p>Extracted as static int-only logic so it is unit-testable without an arena.
     */
    static List<GridPos> surfacingAnchors(GridPos grave, int sizeX, int sizeZ) {
        List<GridPos> anchors = new ArrayList<>();
        // Every anchor whose footprint touches the grave, minus any that would cover it.
        for (int dx = -sizeX; dx <= 1; dx++) {
            for (int dz = -sizeZ; dz <= 1; dz++) {
                GridPos anchor = new GridPos(grave.x() + dx, grave.z() + dz);
                if (footprintCovers(anchor, sizeX, sizeZ, grave)) continue;
                if (!footprintTouches(anchor, sizeX, sizeZ, grave)) continue;
                anchors.add(anchor);
            }
        }
        anchors.sort(java.util.Comparator.comparingInt(a -> footprintDistanceTo(a, sizeX, sizeZ, grave)));
        return anchors;
    }

    /** True if a footprint at {@code anchor} would sit on {@code target}. */
    private static boolean footprintCovers(GridPos anchor, int sizeX, int sizeZ, GridPos target) {
        return target.x() >= anchor.x() && target.x() < anchor.x() + sizeX
            && target.z() >= anchor.z() && target.z() < anchor.z() + sizeZ;
    }

    /** True if any tile of the footprint is orthogonally adjacent to {@code target}. */
    private static boolean footprintTouches(GridPos anchor, int sizeX, int sizeZ, GridPos target) {
        return footprintDistanceTo(anchor, sizeX, sizeZ, target) == 1;
    }

    /** Manhattan distance from the closest tile of the footprint to {@code target}. */
    private static int footprintDistanceTo(GridPos anchor, int sizeX, int sizeZ, GridPos target) {
        int best = Integer.MAX_VALUE;
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                int d = Math.abs(anchor.x() + dx - target.x()) + Math.abs(anchor.z() + dz - target.z());
                if (d < best) best = d;
            }
        }
        return best;
    }

    /**
     * A free tile next to a grave for a corpse to claw up onto, or null if the grave is boxed in.
     *
     * <p>Returning null is a real outcome, not a failure to handle: a grave the player has walled
     * off with their own body raises nothing that turn.
     */
    private GridPos findFreeTileAdjacentTo(GridArena arena, GridPos grave, List<GridPos> taken) {
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            GridPos p = new GridPos(grave.x() + d[0], grave.z() + d[1]);
            if (taken.contains(p)) continue;
            if (arena.isInBounds(p) && !arena.isOccupied(p)
                    && arena.getTile(p) != null && arena.getTile(p).isWalkable()) {
                return p;
            }
        }
        return null;
    }

    /**
     * The grave a Grave Skull erupts from. The skull spawns ON the grave tile rather than beside
     * it, so unlike a zombie it never needs a free neighbour and a boxed-in grave still produces
     * one. Graves are passable for boss-spawned things, and the skull leaves the tile immediately.
     *
     * <p>Returns null when nothing lives, which is the point: no graves, no skull.
     */
    private GridPos pickSkullGrave() {
        if (graves.isEmpty()) return null;
        return graves.get(getTurnCounter() % graves.size());
    }

    /**
     * Phase 2 tears out two more graves mid-fight. Routed through SummonMinions so CombatManager
     * can intercept it and build the block-backed objects, exactly as the Broodmother's mid-fight
     * egg sacs are; the AI cannot place world blocks itself.
     */
    private EnemyAction tryPlacePhaseTwoGraves(CombatEntity self, GridArena arena) {
        pendingPhaseTwoGraves = false;
        // Search near the boss so the new graves land in the fight rather than in a far corner.
        List<GridPos> candidates = findSummonPositionsNear(
            arena, self.getGridPos(), 4, PHASE_TWO_GRAVES);
        if (candidates.isEmpty()) return null;
        return new EnemyAction.SummonMinions(
            "craftics:grave", candidates.size(), candidates, 1, 0, 0);
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
