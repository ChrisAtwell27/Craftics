package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Underground Caverns Boss - "The Hollow King" (Corrupted Miner)
 * Entity: Zombie | 40HP / 7ATK / 3DEF / Speed 2 | Size 2x2
 *
 * <h2>The props are the fight</h2>
 *
 * <p>He used to have seven abilities that asked the player one question: step off the
 * highlighted tiles. Cave-In, Demolition Cache, Sandstorm-by-another-name - all the same verb,
 * all centred on wherever the player happened to be standing, and none of them had anything to
 * do with being a miner. He built walls and then ignored them.
 *
 * <p>So the terrain became the mechanic. He SHORES UP: raises three support pillars (five once
 * enraged), three blocks of timber each, and takes a point of armour from every one standing.
 * While they hold he is genuinely hard to hurt, and the room is visibly held together by
 * specific tiles. Then he knocks them out - the armour goes with them and the ceiling comes down
 * the entire row AND column of every pillar at once.
 *
 * <p>That is a different question. Not "am I standing on the orange tile" but "which lanes are
 * loaded, and where is the cell that is in none of them" - read off the room rather than off a
 * highlight under your feet.
 *
 * <p><b>The player can take the pillars too.</b> A pickaxe on an adjacent one knocks the whole
 * thing down: a point off his armour, and two fewer lanes in the collapse he is loading. But a
 * pickaxe reaches one tile, so getting to a pillar means standing in the lanes it loads, and
 * every turn spent mining is a turn not spent hitting him. That trade is the fight.
 *
 * <p>His other abilities cost him too: Shrapnel shatters one of his own pillars to fire it down
 * a lane, and Miner's Fury ploughs through obstacles, his own included. He can spend armour to
 * threaten, so the player gets to watch him choose.
 *
 * <p>Range decides his stance - see {@link #MELEE_RANGE} and its neighbours - and if he cannot
 * path to the player at all he mines through the wall rather than wandering.
 *
 * <p>Phase 2 - "Total Collapse": pillars go up in larger sets and come down harder.
 */
public class HollowKingAI extends BossAI {
    private static final String CD_TNT = "demolition_cache";
    private static final String CD_SHRAPNEL = "shrapnel";
    private static final String CD_CAVEIN = "cave_in";
    private static final String CD_SWARM = "swarm_call";
    private static final String CD_LIGHTS = "lights_out";
    private static final String CD_CHARGE = "miners_fury";
    private static final String CD_SHORE = "shore_up";

    /** Armour granted per standing pillar. */
    private static final int DEF_PER_PROP = 1;
    /** Enough pillars to be worth bringing down. Below this he shores up instead. */
    private static final int COLLAPSE_MIN_PROPS = 2;
    /** Turns the pillars must stand before he will bring them down. */
    private static final int COLLAPSE_DELAY_TURNS = 2;
    /**
     * Three bands, and they are the whole shape of how he fights.
     *
     * <ul>
     *   <li><b>Within {@value #MELEE_RANGE}</b> - too close to work. He puts the spells away and
     *       comes at you with the pickaxe.</li>
     *   <li><b>Past that, out to {@value #STATIONARY_CAST_RANGE}</b> - his working distance. He
     *       plants his feet and casts. No moving and casting in the same turn; standing here is
     *       the price of being in his range.</li>
     *   <li><b>{@value #MOVE_AND_CAST_RANGE} and beyond</b> - too far to be ignored. He closes
     *       AND casts in the same turn, at triple speed. Backing off does not buy a free
     *       turn.</li>
     * </ul>
     */
    private static final int MELEE_RANGE = 3;
    private static final int STATIONARY_CAST_RANGE = 5;
    private static final int MOVE_AND_CAST_RANGE = 6;

    /** Speed at his working distance: a miner picking his ground, not a sprinter. */
    private static final int SPEED_NEAR = 2;
    /** Speed once you are outside his range entirely. */
    private static final int SPEED_FAR = 6;

    /** Turn the current set of pillars went up, for {@link #COLLAPSE_DELAY_TURNS}. */
    private int pillarsRaisedTurn = -99;

    private boolean lightsOutPermanent = false;

    /**
     * Where he has put supports. Advisory only - a prop can be ploughed through by his own
     * charge or simply expire, so this is filtered against the arena every time it is read
     * rather than trusted. See {@link #standingProps}.
     */
    private final List<GridPos> props = new ArrayList<>();

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        lightsOutPermanent = true;
        // Permanent darkness - handled by CombatManager checking lightsOutPermanent
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        List<GridPos> standing = standingProps(arena);
        syncSpeed(self, dist);
        EnemyAction armourFix = syncArmour(self, standing.size());
        EnemyAction chosen = chooseCore(self, arena, playerPos, standing);
        // The armour correction rides along with whatever he does this turn. He only gets one
        // action, and the armour has to be re-derived EVERY turn or a pillar the player mined
        // out would still be protecting him - which is the whole reason mining one is worth a
        // turn. Nothing to fix on most turns, and then this is just the action itself.
        if (armourFix == null) return chosen;
        return new EnemyAction.CompositeAction(List.of(armourFix, chosen));
    }

    /**
     * Two gears: a working pace in range, a charge out of it.
     *
     * <p>Applied by mutating the entity rather than by emitting a {@code ModifySelf} the way the
     * armour does, and the difference matters. The movement action is BUILT this turn from
     * {@code getMoveSpeed()}, so a speed change queued as an action would arrive after the path
     * had already been planned at the old speed - he would be fast one turn late, every turn.
     * Set here, the charge is the move he makes now.
     *
     * <p>The base is re-derived from the entity each turn rather than assumed, so this cannot
     * drift and cannot fight with anything else that has touched his speed.
     */
    private void syncSpeed(CombatEntity self, int dist) {
        int want = dist >= MOVE_AND_CAST_RANGE ? SPEED_FAR : SPEED_NEAR;
        int base = self.getMoveSpeed() - self.getSpeedBonus();
        self.setSpeedBonus(want - base);
    }

    /**
     * Bring his armour back in line with the pillars actually standing.
     *
     * <p>Computed as a DELTA against what he is currently carrying, read off the entity itself
     * rather than from a counter this class keeps. Defence here is a signed accumulator, so a
     * remembered "I have granted 3" would drift the moment anything else in the fight touched
     * it, and a drifted value is armour the player cannot remove by any means. Reading the real
     * number and correcting to the target cannot drift by construction.
     *
     * @return the correction, or null when it already matches
     */
    private EnemyAction syncArmour(CombatEntity self, int pillars) {
        int want = -DEF_PER_PROP * pillars;      // a negative penalty IS bonus armour
        int have = self.getDefensePenalty();
        if (have == want) return null;
        return new EnemyAction.ModifySelf("defense", have - want, 0);
    }

    private EnemyAction chooseCore(CombatEntity self, GridArena arena, GridPos playerPos,
                                   List<GridPos> standing) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // The payoff - but not the instant the pillars are up. Held above everything else and
        // with no delay, this collapsed the fight into two moves on repeat: shore up, collapse,
        // shore up, collapse, with every other ability starved and the boss standing still
        // between them. The pillars now have to STAND for a couple of turns first, which is
        // the window the player needs to go and mine them anyway, and it leaves room for the
        // rest of the kit to fire.
        boolean pillarsSettled = getTurnCounter() - pillarsRaisedTurn >= COLLAPSE_DELAY_TURNS;
        if (standing.size() >= COLLAPSE_MIN_PROPS && pillarsSettled && !isOnCooldown(CD_CAVEIN)) {
            EnemyAction collapse = tryCollapse(self, arena, playerPos, standing);
            if (collapse != null) return collapse;
        }

        // The setup. He wants props up; without them he is a 3 DEF zombie with a pickaxe.
        if (standing.size() < propTarget() && !isOnCooldown(CD_SHORE)) {
            EnemyAction shore = tryShoreUp(self, arena, playerPos, standing);
            if (shore != null) return shore;
        }

        // Walled in? Dig. Checked before he tries to walk anywhere, because the alternative
        // when a boss cannot path is seekOrWander, and a boss wandering behind a wall the
        // player put up is a boss that has been switched off.
        EnemyAction dig = tryMineThrough(self, arena, playerPos);
        if (dig != null) return dig;

        // Inside his working distance the spells go away entirely. Standing on top of a miner
        // should mean fighting the miner, not watching him telegraph across two tiles - and it
        // gives the player a reason to want to be there.
        if (dist <= MELEE_RANGE) {
            return meleeOrApproach(self, arena, playerPos, 0);
        }

        // Demolition Cache: prime TNT charges that explode next round.
        if (!isOnCooldown(CD_TNT) && dist <= MOVE_AND_CAST_RANGE + 2) {
            EnemyAction tnt = tryDemolitionCache(self, arena, playerPos);
            if (tnt != null) return tnt;
        }

        // Shrapnel: shatter one of his own props and fire it down the lane at the player.
        if (!isOnCooldown(CD_SHRAPNEL) && !standing.isEmpty()) {
            EnemyAction spray = tryShrapnel(self, arena, playerPos, standing);
            if (spray != null) return spray;
        }

        // Lights Out - blanket darkness
        if (!isOnCooldown(CD_LIGHTS) && !lightsOutPermanent) {
            setCooldown(CD_LIGHTS, isPhaseTwo() ? 2 : 4);
            return new EnemyAction.BossAbility("lights_out",
                new EnemyAction.AreaAttack(myPos, 0, 0, "lights_out"),
                List.of(myPos));
        }

        // Swarm Call - silverfish from edges
        if (!isOnCooldown(CD_SWARM) && getAliveMinionCount() < 4) {
            setCooldown(CD_SWARM, 3);
            int count = isPhaseTwo() ? 4 : 3;
            List<GridPos> edgePositions = findEdgePositions(arena, count);
            if (!edgePositions.isEmpty()) {
                return new EnemyAction.SummonMinions(
                    "minecraft:silverfish", edgePositions.size(), edgePositions, 2, 1, 0);
            }
        }

        // Miner's Fury - line charge that ploughs through obstacles, his own props included.
        if (!isOnCooldown(CD_CHARGE) && dist >= 2 && dist <= STATIONARY_CAST_RANGE) {
            setCooldown(CD_CHARGE, 2);
            int[] dir = getDirectionToward(myPos, playerPos);
            List<GridPos> chargePath = getLineTiles(arena, myPos, dir[0], dir[1], 3);
            if (!chargePath.isEmpty()) {
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.DIRECTIONAL,
                    chargePath, 1,
                    new EnemyAction.LineAttack(myPos, dir[0], dir[1], 3, self.getAttackPower() + 2),
                    0xFFFF6600, dir[0], dir[1]);
                return castStance(self, arena, playerPos);
            }
        }

        // Melee if adjacent
        if (dist <= 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        return meleeOrApproach(self, arena, playerPos, 0);
    }

    // ── Props ────────────────────────────────────────────────────────────────

    /**
     * What he does with the turn he spends winding a spell up.
     *
     * <p>At his working distance: nothing. He plants his feet, and the telegraph turn is a turn
     * he really is standing still - which is what makes closing on him worth doing. Only from
     * {@value #MOVE_AND_CAST_RANGE} out does he walk while he charges, so distance costs the
     * player the free turn it used to buy them.
     */
    private EnemyAction castStance(CombatEntity self, GridArena arena, GridPos playerPos) {
        return self.minDistanceTo(playerPos) >= MOVE_AND_CAST_RANGE
            ? advanceWhileCharging(self, arena, playerPos)
            : new EnemyAction.Idle();
    }

    /**
     * The same rule on the turns BossAI drives the charge itself, rather than the ones this
     * class returns. Without the override he would stand still on the first telegraph turn and
     * then start walking on the next, which is the two halves of the fight disagreeing.
     */
    @Override
    public EnemyAction getChargingAdvanceAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        return castStance(self, arena, playerPos);
    }

    /** How many supports go up per cast, and therefore how many he keeps standing. */
    private int propTarget() {
        return isPhaseTwo() ? 5 : 3;
    }

    /**
     * The props that are actually still there.
     *
     * <p>Read from the arena rather than from the list, because the list is a record of
     * intentions and the arena is the truth: his own charge ploughs through obstacles, the
     * temporary-terrain timer expires them, and a player with a pickaxe can dig one out. Every
     * one of those is a prop he no longer has, and a collapse built from a remembered prop
     * would drop the ceiling along a lane nothing was holding up.
     */
    private List<GridPos> standingProps(GridArena arena) {
        List<GridPos> alive = new ArrayList<>();
        for (GridPos p : props) {
            var tile = arena.getTile(p);
            if (tile != null && tile.getType() == TileType.OBSTACLE) alive.add(p);
        }
        props.clear();
        props.addAll(alive);
        return alive;
    }

    /**
     * Raise supports and take armour from them.
     *
     * <p>Sites are chosen away from the player: a prop is scenery to be read, not another thing
     * dropped on someone's head, and the arena refuses to build on an occupied tile anyway.
     * They are spread apart so the rows and columns they load do not all overlap - four props
     * in a huddle would collapse into one lane and the whole mechanic would read as a wall.
     */
    private EnemyAction tryShoreUp(CombatEntity self, GridArena arena, GridPos playerPos,
                                   List<GridPos> standing) {
        int want = propTarget() - standing.size();
        List<GridPos> sites = findPropSites(arena, self, playerPos, standing, want);
        if (sites.isEmpty()) return null;

        setCooldown(CD_SHORE, isPhaseTwo() ? 3 : 4);
        props.addAll(sites);
        pillarsRaisedTurn = getTurnCounter();

        // No warning tiles: this builds, it does not strike, so it resolves the turn he uses it
        // rather than telegraphing for one. The armour it grants is applied by syncArmour on
        // this same turn and every turn after, off the pillars that are really there.
        EnemyAction shore = new EnemyAction.CompositeAction(List.of(
            new EnemyAction.RaisePillars(sites),
            new EnemyAction.AreaAttack(self.getGridPos(), 0, 0, "hollow_shore_up")
        ));
        return new EnemyAction.BossAbility("shore_up", shore, List.of());
    }

    /**
     * Knock the props out and bring the ceiling down along everything they were holding.
     *
     * <p>The whole lane: the entire row AND column of every pillar. A support does not hold up
     * the square it stands on, it holds up the span it stands in, so the span is what comes
     * down. It is a lot of floor - five pillars in phase two load ten lanes - but every tile of
     * it is painted a turn ahead and every tile of it visibly rains rubble when it lands, so
     * what the player is reading is the room rather than a number.
     *
     * <p>It also puts real tension on mining one. A pickaxe only reaches an ADJACENT tile, so
     * taking a pillar out means standing in the lanes it loads - and every pillar pulled is two
     * fewer lanes in the collapse and one less point of his armour. Going to get them is the
     * aggressive line, and it is supposed to feel like one.
     */
    private EnemyAction tryCollapse(CombatEntity self, GridArena arena, GridPos playerPos,
                                    List<GridPos> standing) {
        Set<GridPos> lanes = new LinkedHashSet<>();
        for (GridPos prop : standing) {
            lanes.addAll(getRowTiles(arena, prop.z()));
            lanes.addAll(getColumnTiles(arena, prop.x()));
        }
        if (lanes.isEmpty()) return null;

        setCooldown(CD_CAVEIN, isPhaseTwo() ? 3 : 4);
        List<GridPos> tiles = new ArrayList<>(lanes);
        int damage = isPhaseTwo() ? 8 : 6;

        // The props go with it. They were the armour and they were the warning; spending them
        // is the cost of the attack, and the room is left open again afterwards so the fight
        // does not silt up with permanent walls.
        List<GridPos> spent = new ArrayList<>(standing);
        props.clear();

        EnemyAction collapse = new EnemyAction.CompositeAction(List.of(
            new EnemyAction.CreateTerrain(spent, TileType.NORMAL, 0),
            new EnemyAction.TileAreaAttack(tiles, playerPos, damage, "hollow_collapse")
        ));
        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
            tiles, 1, collapse, 0xFFCC2200);
        return castStance(self, arena, playerPos);
    }

    /**
     * Shatter one prop and fire it down the lane at the player.
     *
     * <p>What Rubble Toss should have been. That one cleared an obstacle from anywhere on the
     * map for four flat damage, which fought his own cave-ins for terrain and asked the player
     * nothing. This spends a support he is drawing armour from, and it hits everything between
     * him and the target rather than only the target - so the question is whether to be in his
     * lane, and he visibly pays for asking it.
     */
    private EnemyAction tryShrapnel(CombatEntity self, GridArena arena, GridPos playerPos,
                                    List<GridPos> standing) {
        GridPos prop = nearestTo(standing, self.getGridPos());
        if (prop == null) return null;
        int[] dir = getDirectionToward(prop, playerPos);
        if (dir[0] == 0 && dir[1] == 0) return null;
        List<GridPos> lane = getLineTiles(arena, prop, dir[0], dir[1], 5);
        if (lane.isEmpty()) return null;

        setCooldown(CD_SHRAPNEL, 3);
        props.remove(prop);

        EnemyAction spray = new EnemyAction.CompositeAction(List.of(
            new EnemyAction.CreateTerrain(List.of(prop), TileType.NORMAL, 0),
            new EnemyAction.TileAreaAttack(lane, prop, self.getAttackPower(), "hollow_shrapnel")
        ));
        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.DIRECTIONAL,
            lane, 1, spray, 0xFFAA7733, dir[0], dir[1]);
        return castStance(self, arena, playerPos);
    }

    /**
     * Chew through a wall when there is no way round it.
     *
     * <p>He is a miner. Every other boss in the game treats a blocked path as a reason to
     * wander, which against a player who has learned to wall themselves in is the same as
     * switching the fight off - and this one in particular can END UP walled in by his own
     * cave-ins. So when nothing he can reach gets him closer, he takes the stone out and keeps
     * coming.
     *
     * <p>Deliberately gated on being genuinely stuck rather than on "is there a wall in the
     * direction of the player". Digging whenever a wall happens to lie on the straight line
     * would have him tunnelling through cover he could simply have walked around, which is
     * both slower for him and far less alarming to watch.
     *
     * @return the dig, or null when he has a way through already
     */
    private EnemyAction tryMineThrough(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int sizeX = self.getSizeX();
        int sizeZ = self.getSizeZ();
        int dist = self.minDistanceTo(playerPos);
        if (dist <= 1) return null;

        GridPos closest = com.crackedgames.craftics.combat.Pathfinding.findClosestReachableTo(
            arena, myPos, playerPos, self.getMoveSpeed(), self, sizeX, sizeZ);
        boolean canMakeProgress = closest != null && !closest.equals(myPos)
            && CombatEntity.minDistanceFromSizedEntity(closest, sizeX, sizeZ, playerPos) < dist;
        if (canMakeProgress) return null;

        GridPos wall = nearestBreakableWall(arena, self, playerPos);
        if (wall == null) return null;

        return new EnemyAction.BossAbility("mine_through",
            new EnemyAction.CompositeAction(List.of(
                new EnemyAction.CreateTerrain(List.of(wall), TileType.NORMAL, 0),
                new EnemyAction.AreaAttack(wall, 0, 0, "hollow_mine"))),
            List.of());
    }

    /**
     * The wall touching him that lies most nearly toward the player.
     *
     * <p>His own pillars are skipped - they are his armour, and tunnelling out through one
     * would have him stripping his own guard to reach someone. Permanent arena walls are
     * skipped too: those are the room's boundary, and a boss that can open them can leave.
     */
    private GridPos nearestBreakableWall(GridArena arena, CombatEntity self, GridPos playerPos) {
        GridPos base = self.getGridPos();
        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int dx = -1; dx <= self.getSizeX(); dx++) {
            for (int dz = -1; dz <= self.getSizeZ(); dz++) {
                GridPos p = new GridPos(base.x() + dx, base.z() + dz);
                if (!arena.isInBounds(p) || props.contains(p)) continue;
                var tile = arena.getTile(p);
                if (tile == null || tile.getType() != TileType.OBSTACLE) continue;
                if (tile.isPermanent()) continue;
                int d = p.manhattanDistance(playerPos);
                if (d < bestDist) { bestDist = d; best = p; }
            }
        }
        return best;
    }

    /** Candidate prop sites: standable, empty, clear of the player, and spread out. */
    private List<GridPos> findPropSites(GridArena arena, CombatEntity self, GridPos playerPos,
                                        List<GridPos> standing, int want) {
        List<GridPos> found = new ArrayList<>();
        if (want <= 0) return found;
        GridPos myPos = self.getGridPos();

        // Every candidate, then SHUFFLED. The old version walked x and then z from the corner
        // and took the first tiles that fitted, which is deterministic: the same arena produced
        // the same three pillars in the same corner every single cast, so the collapse loaded
        // the same lanes every time and the whole mechanic could be memorised in one fight and
        // then ignored. The rule stays identical - this only changes which of the legal
        // answers gets picked.
        List<GridPos> candidates = new ArrayList<>();
        for (int x = 1; x < arena.getWidth() - 1; x++) {
            for (int z = 1; z < arena.getHeight() - 1; z++) {
                GridPos p = new GridPos(x, z);
                var tile = arena.getTile(p);
                if (tile == null || !tile.isWalkable()) continue;
                if (arena.isOccupied(p)) continue;
                // Never underfoot: two tiles of clearance from the player, and off the boss.
                if (p.manhattanDistance(playerPos) <= 2) continue;
                if (p.manhattanDistance(myPos) <= 1) continue;
                candidates.add(p);
            }
        }
        java.util.Collections.shuffle(candidates);

        for (GridPos p : candidates) {
            if (found.size() >= want) break;
            // One pillar per row and per column. Two sharing a lane would load that lane
            // twice for no extra threat while wasting a pillar's worth of danger, and the
            // player would have no way to tell the difference by looking.
            if (sharesLane(p, standing) || sharesLane(p, found)) continue;
            found.add(p);
        }
        return found;
    }

    /** Does this tile already have a pillar in its row or its column? */
    private static boolean sharesLane(GridPos p, List<GridPos> others) {
        for (GridPos o : others) {
            if (o.x() == p.x() || o.z() == p.z()) return true;
        }
        return false;
    }

    private static GridPos nearestTo(List<GridPos> candidates, GridPos from) {
        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (GridPos p : candidates) {
            int d = p.manhattanDistance(from);
            if (d < bestDist) { bestDist = d; best = p; }
        }
        return best;
    }

    // ── Unchanged kit ────────────────────────────────────────────────────────

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
        return castStance(self, arena, playerPos);
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
