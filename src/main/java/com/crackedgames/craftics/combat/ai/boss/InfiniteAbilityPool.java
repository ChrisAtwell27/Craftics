package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * INFINITE MODE ability pool: every boss in the mod contributes its signature
 * attacks as standalone, entity-agnostic casts. The originals live as private
 * methods inside each boss AI (coupled to per-boss state), so the pool re-implements
 * them against the same {@link EnemyAction} vocabulary CombatManager already
 * executes generically - damage keys off {@code self.getAttackPower()}, patterns
 * off the shared {@link BossAI} tile helpers, and status effects reuse the donor
 * effect-name strings so {@code applyBossAreaEffect} / the VFX category mapping
 * light up unchanged.
 *
 * <p>Design bar for every entry: the attack should CLAIM SPACE (lanes, rings,
 * multi-zone barrages, arena rows - not lone tiles), read clearly (warning tiles
 * always match the resolve footprint), and leave counterplay (a telegraph turn,
 * a safe gap, a shovable direction shown with arrows, or a killable projectile).
 *
 * <p>Rules for every entry:
 * <ul>
 *   <li>1x1-safe: nothing assumes a 2x2 footprint or an off-grid background boss.</li>
 *   <li>Self-contained: no cross-turn state beyond the {@link BossAI} cooldown map.</li>
 *   <li>Returns {@code null} when the move can't fire this turn - the
 *       {@link InfiniteBossAI} cycle then tries its next move.</li>
 *   <li>Telegraphed casts return {@link EnemyAction.BossAbility} (CombatManager
 *       owns the warning); movement casts return their action directly since the
 *       telegraph store can't drive the movement state machine.</li>
 * </ul>
 */
public final class InfiniteAbilityPool {
    private InfiniteAbilityPool() {}

    @FunctionalInterface
    public interface AbilityCast {
        EnemyAction cast(InfiniteBossAI ctx, CombatEntity self, GridArena arena, GridPos playerPos);
    }

    public record InfiniteAbility(String id, AbilityCast castFn) {
        public EnemyAction cast(InfiniteBossAI ctx, CombatEntity self, GridArena arena, GridPos playerPos) {
            try {
                return castFn.cast(ctx, self, arena, playerPos);
            } catch (Exception e) {
                // A single broken cast must never wedge the enemy turn loop.
                com.crackedgames.craftics.CrafticsMod.LOGGER.warn(
                    "Infinite ability '{}' threw; skipping this turn", id, e);
                return null;
            }
        }
    }

    private static final Map<String, InfiniteAbility> POOL = new LinkedHashMap<>();
    private static final Random RNG = new Random();

    public static InfiniteAbility byId(String id) {
        return POOL.get(id);
    }

    /** All registered ability ids, in registration order. */
    public static List<String> allIds() {
        return new ArrayList<>(POOL.keySet());
    }

    /** Roll {@code count} distinct abilities (whole pool if count exceeds it). */
    public static List<InfiniteAbility> roll(Random rng, int count) {
        List<String> ids = allIds();
        Collections.shuffle(ids, rng);
        List<InfiniteAbility> out = new ArrayList<>();
        for (String id : ids.subList(0, Math.min(count, ids.size()))) {
            out.add(POOL.get(id));
        }
        return out;
    }

    /** Roll {@code count} distinct ability ids - the serializable form used by InfiniteSpec. */
    public static List<String> rollIds(Random rng, int count) {
        List<String> ids = allIds();
        Collections.shuffle(ids, rng);
        return new ArrayList<>(ids.subList(0, Math.min(count, ids.size())));
    }

    private static void register(String id, AbilityCast cast) {
        POOL.put(id, new InfiniteAbility(id, cast));
    }

    // ─── Shared pattern helpers ────────────────────────────────────────────────

    /** Axis-aligned charge toward the player: path stops adjacent to them.
     *  Returns null when not aligned, too close, or the lane is blocked. */
    private static List<GridPos> chargeLane(CombatEntity self, GridArena arena, GridPos playerPos, int maxLen) {
        GridPos start = self.getGridPos();
        int dx = 0, dz = 0;
        if (start.x() == playerPos.x() && start.z() != playerPos.z()) {
            dz = Integer.signum(playerPos.z() - start.z());
        } else if (start.z() == playerPos.z() && start.x() != playerPos.x()) {
            dx = Integer.signum(playerPos.x() - start.x());
        } else {
            return null;
        }
        int distance = Math.abs(playerPos.x() - start.x()) + Math.abs(playerPos.z() - start.z());
        if (distance < 2) return null;
        int pathLength = Math.min(maxLen, distance - 1);
        List<GridPos> path = new ArrayList<>();
        GridPos current = start;
        for (int i = 0; i < pathLength; i++) {
            GridPos next = new GridPos(current.x() + dx, current.z() + dz);
            if (!arena.isInBounds(next) || arena.isOccupied(next)) break;
            var tile = arena.getTile(next);
            if (tile == null || !tile.isWalkable()) break;
            path.add(next);
            current = next;
        }
        if (path.isEmpty()) return null;
        GridPos finalPos = path.get(path.size() - 1);
        if (finalPos.manhattanDistance(playerPos) > 1) return null;
        return path;
    }

    /** A random walkable, unoccupied tile adjacent (8-dir) to {@code around}, or null. */
    private static GridPos openTileAdjacent(GridArena arena, GridPos around) {
        List<GridPos> candidates = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                GridPos pos = new GridPos(around.x() + dx, around.z() + dz);
                if (arena.isInBounds(pos) && !arena.isOccupied(pos)
                        && arena.getTile(pos) != null && arena.getTile(pos).isWalkable()) {
                    candidates.add(pos);
                }
            }
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(RNG.nextInt(candidates.size()));
    }

    /** Walkable tiles in the square ring at exactly {@code radius} around a center. */
    private static List<GridPos> ringTiles(GridArena arena, GridPos center, int radius) {
        List<GridPos> tiles = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                GridPos pos = new GridPos(center.x() + dx, center.z() + dz);
                if (arena.isInBounds(pos) && !arena.isOccupied(pos)
                        && arena.getTile(pos) != null && arena.getTile(pos).isWalkable()) {
                    tiles.add(pos);
                }
            }
        }
        return tiles;
    }

    /** Walkable (not necessarily unoccupied) tiles in a box around a center, player tile excluded. */
    private static List<GridPos> walkableBox(GridArena arena, GridPos center, int radius, GridPos exclude) {
        List<GridPos> tiles = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                GridPos pos = new GridPos(center.x() + dx, center.z() + dz);
                if (!arena.isInBounds(pos)) continue;
                if (exclude != null && pos.equals(exclude)) continue;
                var tile = arena.getTile(pos);
                if (tile != null && tile.isWalkable()) tiles.add(pos);
            }
        }
        return tiles;
    }

    private static int[] dirToward(GridPos from, GridPos to) {
        int dx = to.x() - from.x();
        int dz = to.z() - from.z();
        if (Math.abs(dx) >= Math.abs(dz)) return new int[]{Integer.signum(dx), 0};
        return new int[]{0, Integer.signum(dz)};
    }

    /** The four cardinal LineAttacks from {@code origin}, each {@code len} long. */
    private static List<EnemyAction> crossLines(GridPos origin, int len, int dmg) {
        List<EnemyAction> lines = new ArrayList<>(4);
        lines.add(new EnemyAction.LineAttack(origin, 1, 0, len, dmg));
        lines.add(new EnemyAction.LineAttack(origin, -1, 0, len, dmg));
        lines.add(new EnemyAction.LineAttack(origin, 0, 1, len, dmg));
        lines.add(new EnemyAction.LineAttack(origin, 0, -1, len, dmg));
        return lines;
    }

    /** In-bounds tiles of a cardinal cross around {@code origin} (origin excluded). */
    private static List<GridPos> crossWarnTiles(GridArena arena, GridPos origin, int len) {
        List<GridPos> tiles = new ArrayList<>();
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int i = 1; i <= len; i++) {
                GridPos pos = new GridPos(origin.x() + d[0] * i, origin.z() + d[1] * i);
                if (arena.isInBounds(pos)) tiles.add(pos);
            }
        }
        return tiles;
    }

    /** Straight run of in-bounds tiles from {@code from} (exclusive) along a direction. */
    private static List<GridPos> lineWarnTiles(GridArena arena, GridPos from, int dx, int dz, int len) {
        List<GridPos> tiles = new ArrayList<>();
        GridPos current = from;
        for (int i = 0; i < len; i++) {
            current = new GridPos(current.x() + dx, current.z() + dz);
            if (!arena.isInBounds(current)) break;
            tiles.add(current);
        }
        return tiles;
    }

    /** Union without duplicates, preserving order. */
    @SafeVarargs
    private static List<GridPos> union(List<GridPos>... tileLists) {
        List<GridPos> out = new ArrayList<>();
        for (List<GridPos> list : tileLists) {
            for (GridPos t : list) {
                if (!out.contains(t)) out.add(t);
            }
        }
        return out;
    }

    // ─── The pool ──────────────────────────────────────────────────────────────

    static {
        // ═══ The Revenant (plains) ═══
        register("raise_dead", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("raise_dead") || ctx.getAliveMinionCount() >= 3) return null;
            int count = ctx.isPhaseTwo() ? 2 : 1;
            // Graves crack open AROUND the player, not off in a corner - the
            // telegraph turn is the window to reposition or pre-clear a tile.
            List<GridPos> positions = ctx.findSummonPositionsNear(arena, p, 3, count);
            if (positions.isEmpty()) positions = ctx.findSummonPositions(arena, count);
            if (positions.isEmpty()) return null;
            ctx.setCooldown("raise_dead", 3);
            return new EnemyAction.BossAbility("raise_dead",
                new EnemyAction.SummonMinions("minecraft:zombie", positions.size(), positions, 6, 2, 0),
                positions);
        });
        register("death_charge", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("death_charge")) return null;
            List<GridPos> path = chargeLane(self, arena, p, 4);
            if (path == null) return null;
            ctx.setCooldown("death_charge", 2);
            int dmg = self.getAttackPower() + (ctx.isPhaseTwo() ? 4 : 2);
            if (ctx.isPhaseTwo() && path.size() > 1) {
                // Phase 2: the charge scorches its wake - re-crossing the lane costs.
                List<GridPos> trail = new ArrayList<>(path.subList(0, path.size() - 1));
                return new EnemyAction.CompositeAction(List.of(
                    new EnemyAction.MoveAndAttack(path, dmg),
                    new EnemyAction.CreateTerrain(trail, TileType.FIRE, 1)));
            }
            return new EnemyAction.MoveAndAttack(path, dmg);
        });
        register("gravefire_grid", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("gravefire_grid")) return null;
            List<GridPos> tiles = new ArrayList<>();
            int parity = ctx.getTurnCounter() % 2;
            for (int x = 0; x < arena.getWidth(); x++) {
                for (int z = 0; z < arena.getHeight(); z++) {
                    if (((x + z) & 1) != parity) continue;
                    GridPos pos = new GridPos(x, z);
                    if (pos.manhattanDistance(p) <= 1) continue;
                    var tile = arena.getTile(pos);
                    if (tile == null || !tile.isWalkable()) continue;
                    tiles.add(pos);
                }
            }
            if (tiles.size() < 6) return null;
            ctx.setCooldown("gravefire_grid", 4);
            return new EnemyAction.BossAbility("gravefire_grid",
                new EnemyAction.CreateTerrain(tiles, TileType.FIRE, 1), tiles);
        });
        register("shield_bash", (ctx, self, arena, p) -> {
            if (self.minDistanceTo(p) > 1 || ctx.isOnCooldown("shield_bash")) return null;
            ctx.setCooldown("shield_bash", 2);
            // A real hit that launches the player 3 tiles - into whatever hazard
            // the rest of the kit has been painting on the floor.
            return new EnemyAction.AttackWithKnockback(self.getAttackPower(), 3);
        });

        // ═══ The Hexweaver (forest) ═══
        register("fang_line", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("fang_line")) return null;
            GridPos origin = self.getGridPos();
            int[] dir = dirToward(origin, p);
            if (dir[0] == 0 && dir[1] == 0) return null;
            ctx.setCooldown("fang_line", 2);
            int dmg = self.getAttackPower() + 1;
            if (ctx.isPhaseTwo()) {
                // Phase 2: fangs erupt down all four lanes at once - only the
                // diagonals are safe.
                List<GridPos> warn = crossWarnTiles(arena, origin, 6);
                return new EnemyAction.BossAbility("fang_line",
                    new EnemyAction.CompositeAction(crossLines(origin, 6, dmg)), warn);
            }
            List<GridPos> warn = lineWarnTiles(arena, origin, dir[0], dir[1], 6);
            if (warn.isEmpty()) return null;
            return new EnemyAction.BossAbility("fang_line",
                new EnemyAction.LineAttack(origin, dir[0], dir[1], 6, dmg),
                warn, warn, dir[0], dir[1]);
        });
        register("cursed_fog", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("cursed_fog")) return null;
            ctx.setCooldown("cursed_fog", 3);
            List<GridPos> warn = ctx.getAreaTiles(arena, p, 2);
            return new EnemyAction.BossAbility("cursed_fog",
                new EnemyAction.AreaAttack(p, 2, Math.max(1, self.getAttackPower() - 1), "cursed_fog"),
                warn);
        });
        register("vex_swarm", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("vex_swarm") || ctx.getAliveMinionCount() >= 3) return null;
            List<GridPos> positions = ctx.findSummonPositionsNear(arena, p, 2, 2);
            if (positions.isEmpty()) return null;
            ctx.setCooldown("vex_swarm", 4);
            return new EnemyAction.BossAbility("vex_swarm",
                new EnemyAction.SummonMinions("minecraft:vex", positions.size(), positions, 4, 3, 0),
                positions);
        });
        register("hex_bolt", (ctx, self, arena, p) -> {
            int dist = self.minDistanceTo(p);
            if (dist < 2 || dist > 5) return null;
            return new EnemyAction.RangedAttack(self.getAttackPower(), "hex_bolt");
        });

        // ═══ The Frostbound Huntsman (snowy) ═══
        register("blizzard", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("blizzard")) return null;
            ctx.setCooldown("blizzard", 3);
            List<GridPos> warn = ctx.getAreaTiles(arena, p, 2);
            EnemyAction storm = new EnemyAction.AreaAttack(p, 2, self.getAttackPower(), "blizzard");
            if (ctx.isPhaseTwo()) {
                // Phase 2: the eye of the storm freezes solid - standing your
                // ground on the center tile stuns you.
                storm = new EnemyAction.CompositeAction(List.of(
                    storm, new EnemyAction.AreaAttack(p, 0, 2, "blizzard_stun")));
            }
            return new EnemyAction.BossAbility("blizzard", storm, warn);
        });
        register("ice_wall", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("ice_wall")) return null;
            // Wall perpendicular to the boss->player axis, one tile in front of the player.
            int[] dir = dirToward(p, self.getGridPos());
            List<GridPos> wall = new ArrayList<>();
            int wx = p.x() + dir[0], wz = p.z() + dir[1];
            for (int off = -1; off <= 1; off++) {
                GridPos pos = dir[0] != 0
                    ? new GridPos(wx, wz + off)
                    : new GridPos(wx + off, wz);
                if (arena.isInBounds(pos) && !arena.isOccupied(pos)
                        && arena.getTile(pos) != null && arena.getTile(pos).isWalkable()) {
                    wall.add(pos);
                }
            }
            if (wall.size() < 2) return null;
            ctx.setCooldown("ice_wall", 4);
            return new EnemyAction.CreateTerrain(wall, TileType.OBSTACLE, 3);
        });
        register("frost_arrow", (ctx, self, arena, p) -> {
            int dist = self.minDistanceTo(p);
            if (dist < 2 || dist > 6) return null;
            return new EnemyAction.RangedAttack(self.getAttackPower(), "frost_arrow");
        });
        register("harpoon_pull", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("harpoon_pull")) return null;
            int dist = self.minDistanceTo(p);
            if (dist < 3 || dist > 5) return null;
            int[] dir = dirToward(p, self.getGridPos());
            if (dir[0] == 0 && dir[1] == 0) return null;
            ctx.setCooldown("harpoon_pull", 3);
            // Telegraphed: the harpoon bites (damage on the player's tile), then
            // drags them along the arrowed path toward the boss.
            List<GridPos> dragPath = lineWarnTiles(arena, p, dir[0], dir[1], dist - 1);
            EnemyAction resolve = new EnemyAction.CompositeAction(List.of(
                new EnemyAction.AreaAttack(p, 0, 4, "frost_harpoon"),
                new EnemyAction.ForcedMovement(-1, dir[0], dir[1], dist - 1)));
            return new EnemyAction.BossAbility("harpoon_pull", resolve,
                List.of(p), union(List.of(p), dragPath), dir[0], dir[1]);
        });

        // ═══ The Rockbreaker (mountain) ═══
        register("seismic_slam", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("seismic_slam")) return null;
            GridPos origin = self.getGridPos();
            ctx.setCooldown("seismic_slam", 3);
            // Fissures actually RUN the full warned cross now (the old resolve
            // was a radius-1 puff under a range-3 warning), and the shockwave
            // hurls whoever it catches.
            int len = ctx.isPhaseTwo() ? 4 : 3;
            int dmg = self.getAttackPower() + 1;
            int[] push = dirToward(origin, p);
            if (push[0] == 0 && push[1] == 0) push = new int[]{1, 0};
            List<EnemyAction> parts = new ArrayList<>(crossLines(origin, len, dmg));
            parts.add(new EnemyAction.ForcedMovement(-1, push[0], push[1], 2));
            List<GridPos> warn = crossWarnTiles(arena, origin, len);
            return new EnemyAction.BossAbility("seismic_slam",
                new EnemyAction.CompositeAction(parts), warn);
        });
        register("boulder_toss", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("boulder_toss") || self.minDistanceTo(p) < 2) return null;
            ctx.setCooldown("boulder_toss", 2);
            int[] push = dirToward(self.getGridPos(), p);
            if (push[0] == 0 && push[1] == 0) push = new int[]{1, 0};
            List<GridPos> warn = ctx.getAreaTiles(arena, p, 1);
            // The boulder stays where it lands: instant terrain denial the player
            // can later mine away, plus a shove off the impact point.
            EnemyAction resolve = new EnemyAction.CompositeAction(List.of(
                new EnemyAction.AreaAttack(p, 1, self.getAttackPower() + 1, "boulder_toss"),
                new EnemyAction.ForcedMovement(-1, push[0], push[1], 1),
                new EnemyAction.CreateTerrain(List.of(p), TileType.OBSTACLE, 3)));
            return new EnemyAction.BossAbility("boulder_toss", resolve, warn, warn, push[0], push[1]);
        });
        register("avalanche", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("avalanche")) return null;
            ctx.setCooldown("avalanche", 4);
            int dmg = self.getAttackPower() + 1;
            List<GridPos> warn = new ArrayList<>(ctx.getRowTiles(arena, p.z()));
            List<EnemyAction> parts = new ArrayList<>();
            parts.add(new EnemyAction.LineAttack(new GridPos(-1, p.z()), 1, 0, arena.getWidth(), dmg));
            if (ctx.isPhaseTwo()) {
                int second = Math.min(arena.getHeight() - 1, p.z() + 1);
                if (second != p.z()) {
                    warn.addAll(ctx.getRowTiles(arena, second));
                    parts.add(new EnemyAction.LineAttack(new GridPos(-1, second), 1, 0, arena.getWidth(), dmg));
                }
            }
            // The rockslide carries you downhill - the arrows show which way.
            parts.add(new EnemyAction.ForcedMovement(-1, 0, 1, 1));
            return new EnemyAction.BossAbility("avalanche",
                new EnemyAction.CompositeAction(parts), warn, warn, 0, 1);
        });
        register("ground_pound", (ctx, self, arena, p) -> {
            if (self.minDistanceTo(p) > 1 || ctx.isOnCooldown("ground_pound")) return null;
            ctx.setCooldown("ground_pound", 2);
            int[] push = dirToward(self.getGridPos(), p);
            if (push[0] == 0 && push[1] == 0) push = new int[]{1, 0};
            return new EnemyAction.CompositeAction(List.of(
                new EnemyAction.AreaAttack(self.getGridPos(), 1, self.getAttackPower(), "ground_pound"),
                new EnemyAction.ForcedMovement(-1, push[0], push[1], 2)));
        });

        // ═══ The Tidecaller (river) ═══
        register("tidal_wave", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("tidal_wave")) return null;
            ctx.setCooldown("tidal_wave", 4);
            int dmg = self.getAttackPower() + 1;
            List<Integer> cols = new ArrayList<>();
            cols.add(p.x());
            if (ctx.isPhaseTwo()) {
                int second = p.x() + (p.x() + 1 < arena.getWidth() ? 1 : -1);
                if (second >= 0 && second != p.x()) cols.add(second);
            }
            List<GridPos> warn = new ArrayList<>();
            List<EnemyAction> parts = new ArrayList<>();
            List<GridPos> flooded = new ArrayList<>();
            for (int col : cols) {
                List<GridPos> colTiles = ctx.getColumnTiles(arena, col);
                warn.addAll(colTiles);
                parts.add(new EnemyAction.LineAttack(new GridPos(col, -1), 0, 1, arena.getHeight(), dmg));
                for (GridPos t : colTiles) {
                    var tile = arena.getTile(t);
                    if (tile != null && tile.getType() == TileType.NORMAL) flooded.add(t);
                }
            }
            // The wave leaves the lane underwater for a couple of rounds -
            // ground the boss's minions love and the player has to route around.
            if (!flooded.isEmpty()) {
                parts.add(new EnemyAction.CreateTerrain(flooded, TileType.WATER, 2));
            }
            return new EnemyAction.BossAbility("tidal_wave",
                new EnemyAction.CompositeAction(parts), warn);
        });
        register("trident_storm", (ctx, self, arena, p) -> {
            int dist = self.minDistanceTo(p);
            if (dist < 2 || dist > 5 || ctx.isOnCooldown("trident_storm")) return null;
            ctx.setCooldown("trident_storm", 3);
            // A true volley: three 3x3 impact zones - one on the player, one
            // ahead of them (toward the boss), one to the flank - so simply
            // side-stepping one blast can walk you into the next.
            int[] toward = dirToward(p, self.getGridPos());
            GridPos second = new GridPos(p.x() + toward[0] * 2, p.z() + toward[1] * 2);
            GridPos third = new GridPos(p.x() + toward[1] * 2, p.z() + toward[0] * 2);
            List<GridPos> centers = new ArrayList<>();
            centers.add(p);
            if (arena.isInBounds(second)) centers.add(second);
            if (arena.isInBounds(third)) centers.add(third);
            List<GridPos> warn = new ArrayList<>();
            List<EnemyAction> strikes = new ArrayList<>();
            for (GridPos c : centers) {
                warn = union(warn, ctx.getAreaTiles(arena, c, 1));
                strikes.add(new EnemyAction.AreaAttack(c, 1, self.getAttackPower() + 1, "trident_storm"));
            }
            return new EnemyAction.BossAbility("trident_storm",
                new EnemyAction.CompositeAction(strikes), warn);
        });
        register("riptide_charge", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("riptide_charge")) return null;
            List<GridPos> path = chargeLane(self, arena, p, 4);
            if (path == null) return null;
            ctx.setCooldown("riptide_charge", 3);
            return new EnemyAction.MoveAndAttackWithKnockback(path, self.getAttackPower() + 1, 1);
        });
        register("call_of_the_deep", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("call_of_the_deep") || ctx.getAliveMinionCount() >= 3) return null;
            List<GridPos> positions = ctx.findSummonPositions(arena, 2);
            if (positions.isEmpty()) return null;
            ctx.setCooldown("call_of_the_deep", 4);
            return new EnemyAction.BossAbility("call_of_the_deep",
                new EnemyAction.SummonMinions("minecraft:drowned", positions.size(), positions, 8, 2, 1),
                positions);
        });

        // ═══ The Sandstorm Pharaoh (desert) ═══
        register("plant_mine", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("plant_mine")) return null;
            // A whole minefield seeds the player's escape routes, not one lone tile.
            int mineCount = ctx.isPhaseTwo() ? 4 : 3;
            List<GridPos> mines = ctx.findSummonPositionsNear(arena, p, 2, mineCount);
            if (mines.isEmpty()) return null;
            ctx.setCooldown("plant_mine", 3);
            List<EnemyAction> plants = new ArrayList<>();
            for (GridPos m : mines) {
                // Registers a persistent step-trigger sand mine (special-cased in resolveAreaAttack).
                plants.add(new EnemyAction.AreaAttack(m, 0, 0, "plant_mine"));
            }
            return plants.size() == 1 ? plants.get(0) : new EnemyAction.CompositeAction(plants);
        });
        register("sand_burial", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("sand_burial")) return null;
            ctx.setCooldown("sand_burial", 3);
            List<GridPos> warn = ctx.getAreaTiles(arena, p, 1);
            // "sand_burial" stuns on hit (applyBossAreaEffect) - eat the hit and
            // you also lose your next action.
            return new EnemyAction.BossAbility("sand_burial",
                new EnemyAction.AreaAttack(p, 1, self.getAttackPower(), "sand_burial"), warn);
        });
        register("sandstorm", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("sandstorm")) return null;
            ctx.setCooldown("sandstorm", 4);
            // A 5x5 wall of stinging sand centered on the player - telegraphed,
            // so the storm is dodged by moving, not by luck.
            List<GridPos> warn = ctx.getAreaTiles(arena, p, 2);
            return new EnemyAction.BossAbility("sandstorm",
                new EnemyAction.AreaAttack(p, 2, Math.max(1, self.getAttackPower() - 1), "sandstorm"),
                warn);
        });
        register("curse_of_the_sands", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("curse_of_the_sands")) return null;
            ctx.setCooldown("curse_of_the_sands", 5);
            // The curse itself is CombatManager state: while cursed, every tile
            // the player moves off sprouts a live sand mine. Dodge the initial
            // blast and the curse never lands.
            List<GridPos> warn = ctx.getAreaTiles(arena, p, 1);
            return new EnemyAction.BossAbility("curse_of_the_sands",
                new EnemyAction.AreaAttack(p, 1, Math.max(1, self.getAttackPower() - 1), "curse_of_sands"),
                warn);
        });

        // ═══ The Broodmother (jungle) ═══
        register("web_spray", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("web_spray")) return null;
            List<GridPos> tiles = walkableBox(arena, p, 1, null);
            if (tiles.isEmpty()) return null;
            ctx.setCooldown("web_spray", 3);
            // Webs the whole 3x3 AND stuns anyone still standing in it when the
            // spray lands ("web_spray" effect). Telegraphed - move or pay.
            EnemyAction resolve = new EnemyAction.CompositeAction(List.of(
                new EnemyAction.PlaceWeb(tiles, 2),
                new EnemyAction.AreaAttack(p, 1, 0, ctx.isPhaseTwo() ? "web_spray_poison" : "web_spray")));
            return new EnemyAction.BossAbility("web_spray", resolve, tiles);
        });
        register("venomous_bite", (ctx, self, arena, p) -> {
            if (self.minDistanceTo(p) > 1 || ctx.isOnCooldown("venomous_bite")) return null;
            ctx.setCooldown("venomous_bite", 2);
            return new EnemyAction.AreaAttack(p, 0, self.getAttackPower() + 1, "venom");
        });
        register("spawn_brood", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("spawn_brood") || ctx.getAliveMinionCount() >= 3) return null;
            List<GridPos> positions = ctx.findSummonPositionsNear(arena, self.getGridPos(), 2, 2);
            if (positions.isEmpty()) return null;
            ctx.setCooldown("spawn_brood", 4);
            return new EnemyAction.BossAbility("spawn_brood",
                new EnemyAction.SummonMinions("minecraft:cave_spider", positions.size(), positions, 5, 2, 0),
                positions);
        });
        register("pounce_strike", (ctx, self, arena, p) -> {
            int dist = self.minDistanceTo(p);
            if (dist < 2 || dist > 3 || ctx.isOnCooldown("pounce_strike")) return null;
            GridPos landing = openTileAdjacent(arena, p);
            if (landing == null) return null;
            ctx.setCooldown("pounce_strike", 2);
            return new EnemyAction.Pounce(landing, self.getAttackPower() + 1);
        });

        // ═══ The Hollow King (cave) ═══
        register("rubble_toss", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("rubble_toss") || self.minDistanceTo(p) < 2) return null;
            ctx.setCooldown("rubble_toss", 2);
            List<GridPos> warn = ctx.getAreaTiles(arena, p, 1);
            return new EnemyAction.BossAbility("rubble_toss",
                new EnemyAction.AreaAttack(p, 1, self.getAttackPower(), "rubble_toss"), warn);
        });
        register("cave_in", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("cave_in")) return null;
            List<GridPos> rubble = ctx.findSummonPositionsNear(arena, p, 3, 6);
            if (rubble.size() < 3) return null;
            ctx.setCooldown("cave_in", 4);
            // The ceiling comes down ON the player too: a 3x3 slam plus rubble
            // obstacles that reshape the arena for a few rounds.
            List<GridPos> warn = union(ctx.getAreaTiles(arena, p, 1), rubble);
            EnemyAction resolve = new EnemyAction.CompositeAction(List.of(
                new EnemyAction.AreaAttack(p, 1, self.getAttackPower(), "cave_in"),
                new EnemyAction.CreateTerrain(rubble, TileType.OBSTACLE, 3)));
            return new EnemyAction.BossAbility("cave_in", resolve, warn);
        });
        register("swarm_call", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("swarm_call") || ctx.getAliveMinionCount() >= 4) return null;
            List<GridPos> positions = ctx.findSummonPositions(arena, 3);
            if (positions.isEmpty()) return null;
            ctx.setCooldown("swarm_call", 4);
            return new EnemyAction.BossAbility("swarm_call",
                new EnemyAction.SummonMinions("minecraft:silverfish", positions.size(), positions, 3, 2, 0),
                positions);
        });
        register("miners_fury", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("miners_fury")) return null;
            List<GridPos> path = chargeLane(self, arena, p, 4);
            if (path == null) return null;
            ctx.setCooldown("miners_fury", 3);
            return new EnemyAction.MoveAndAttack(path, self.getAttackPower() + 3);
        });

        // ═══ The Warden (deep dark) ═══
        register("sonic_boom", (ctx, self, arena, p) -> {
            int dist = self.minDistanceTo(p);
            if (dist < 2 || dist > 6 || ctx.isOnCooldown("sonic_boom")) return null;
            ctx.setCooldown("sonic_boom", 3);
            return new EnemyAction.RangedAttack(self.getAttackPower() + 2, "sonic_boom");
        });
        register("darkness_pulse", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("darkness_pulse")) return null;
            ctx.setCooldown("darkness_pulse", 3);
            int radius = ctx.isPhaseTwo() ? 3 : 2;
            List<GridPos> warn = ctx.getAreaTiles(arena, self.getGridPos(), radius);
            return new EnemyAction.BossAbility("darkness_pulse",
                new EnemyAction.AreaAttack(self.getGridPos(), radius,
                    Math.max(1, self.getAttackPower() - 1), "darkness_pulse"),
                warn);
        });
        register("tremor_stomp", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("tremor_stomp")) return null;
            GridPos origin = self.getGridPos();
            ctx.setCooldown("tremor_stomp", 3);
            // The tremor now runs the FULL warned cross (resolve used to cover a
            // third of its own telegraph) with a close-range slam at the center.
            List<EnemyAction> parts = new ArrayList<>();
            parts.add(new EnemyAction.AreaAttack(origin, 1, self.getAttackPower() + 1, "tremor_stomp"));
            parts.addAll(crossLines(origin, 3, Math.max(1, self.getAttackPower() - 1)));
            List<GridPos> warn = union(ctx.getAreaTiles(arena, origin, 1), crossWarnTiles(arena, origin, 3));
            return new EnemyAction.BossAbility("tremor_stomp",
                new EnemyAction.CompositeAction(parts), warn);
        });

        // ═══ The Molten King (nether wastes) ═══
        register("magma_eruption", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("magma_eruption")) return null;
            List<GridPos> vents = ctx.findSummonPositionsNear(arena, p, 2, 5);
            if (vents.size() < 3) return null;
            ctx.setCooldown("magma_eruption", 3);
            // The eruption BLASTS the player zone as the vents open ("magma_eruption"
            // burns on hit), then the vents keep burning as terrain.
            List<GridPos> warn = union(ctx.getAreaTiles(arena, p, 1), vents);
            EnemyAction resolve = new EnemyAction.CompositeAction(List.of(
                new EnemyAction.AreaAttack(p, 1, self.getAttackPower(), "magma_eruption"),
                new EnemyAction.CreateTerrain(vents, TileType.FIRE, 2)));
            return new EnemyAction.BossAbility("magma_eruption", resolve, warn);
        });
        register("lava_cage", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("lava_cage")) return null;
            List<GridPos> ring = ringTiles(arena, p, 2);
            if (ring.size() < 5) return null;
            ctx.setCooldown("lava_cage", 4);
            // Leave exactly one gap on the side facing away from the boss: the
            // cage is escapable, but only through the door the boss ISN'T at.
            GridPos gap = null;
            int best = Integer.MIN_VALUE;
            for (GridPos t : ring) {
                int score = t.manhattanDistance(self.getGridPos());
                if (score > best) { best = score; gap = t; }
            }
            List<GridPos> cage = new ArrayList<>(ring);
            if (gap != null) cage.remove(gap);
            if (cage.size() < 4) return null;
            return new EnemyAction.BossAbility("lava_cage",
                new EnemyAction.CreateTerrain(cage, TileType.FIRE, 2), cage);
        });
        register("absorb_heat", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("absorb_heat")) return null;
            if (self.getCurrentHp() > self.getMaxHp() * 2 / 3) return null;
            ctx.setCooldown("absorb_heat", 5);
            return new EnemyAction.ModifySelf("heal", 4 + self.getMaxHp() / 20, 0);
        });

        // ═══ The Wailing Revenant (soul sand valley) ═══
        register("fireball_barrage", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("fireball_barrage")) return null;
            int count = ctx.isPhaseTwo() ? 4 : 3;
            List<GridPos> positions = ctx.getProjectileSpawnPositions(arena, self.getGridPos(), 1, p, count);
            if (positions.isEmpty()) return null;
            List<int[]> directions = new ArrayList<>();
            for (GridPos pos : positions) directions.add(dirToward(pos, p));
            ctx.setCooldown("fireball_barrage", 3);
            return new EnemyAction.BossAbility("fireball_barrage",
                new EnemyAction.SpawnProjectile("minecraft:blaze", positions, directions,
                    4, self.getAttackPower(), 0, "ghast_fireball"),
                positions);
        });
        register("fireball_rain", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("fireball_rain")) return null;
            // Carpet-bomb a third of the arena: every strike telegraphed, the
            // gaps between them are the dodge.
            List<GridPos> candidates = walkableBox(arena,
                new GridPos(arena.getWidth() / 2, arena.getHeight() / 2),
                Math.max(arena.getWidth(), arena.getHeight()), null);
            if (candidates.size() < 6) return null;
            Collections.shuffle(candidates, RNG);
            int strikesWanted = Math.min(12, Math.max(4, candidates.size() / 3));
            List<GridPos> targets = new ArrayList<>(candidates.subList(0, strikesWanted));
            // Always include the player's tile so the rain can't whiff entirely.
            if (!targets.contains(p)) targets.set(0, p);
            ctx.setCooldown("fireball_rain", 4);
            List<EnemyAction> strikes = new ArrayList<>();
            for (GridPos t : targets) {
                strikes.add(new EnemyAction.AreaAttack(t, 0, self.getAttackPower(), "raining_fireball"));
            }
            return new EnemyAction.BossAbility("fireball_rain",
                new EnemyAction.CompositeAction(strikes), targets);
        });
        register("summon_wither_skeletons", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("summon_wither_skeletons") || ctx.getAliveMinionCount() >= 3) return null;
            List<GridPos> positions = ctx.findSummonPositions(arena, 2);
            if (positions.isEmpty()) return null;
            ctx.setCooldown("summon_wither_skeletons", 5);
            return new EnemyAction.BossAbility("summon_wither_skeletons",
                new EnemyAction.SummonMinions("minecraft:wither_skeleton", positions.size(), positions, 8, 3, 1),
                positions);
        });

        // ═══ The Bastion Brute (crimson forest) ═══
        register("gore_charge", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("gore_charge")) return null;
            List<GridPos> path = chargeLane(self, arena, p, 4);
            if (path == null) return null;
            ctx.setCooldown("gore_charge", 3);
            return new EnemyAction.MoveAndAttackWithKnockback(path, self.getAttackPower() + 2, 2);
        });
        register("ground_slam", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("ground_slam")) return null;
            GridPos origin = self.getGridPos();
            List<GridPos> cross = ctx.getCrossTiles(arena, origin, 3);
            List<GridPos> burnable = new ArrayList<>();
            for (GridPos pos : cross) {
                var tile = arena.getTile(pos);
                if (tile != null && tile.isWalkable() && !arena.isOccupied(pos)) burnable.add(pos);
            }
            if (burnable.size() < 3) return null;
            ctx.setCooldown("ground_slam", 4);
            // Magma cracks hit as they open, then burn as terrain - the warned
            // cross is dangerous NOW and stays dangerous.
            List<EnemyAction> parts = new ArrayList<>(crossLines(origin, 3, Math.max(1, self.getAttackPower() - 1)));
            parts.add(new EnemyAction.CreateTerrain(burnable, TileType.FIRE, 2));
            return new EnemyAction.BossAbility("ground_slam",
                new EnemyAction.CompositeAction(parts), crossWarnTiles(arena, origin, 3));
        });
        register("rampage", (ctx, self, arena, p) -> {
            if (self.minDistanceTo(p) > 1 || ctx.isOnCooldown("rampage")) return null;
            ctx.setCooldown("rampage", 3);
            int[] push = dirToward(self.getGridPos(), p);
            if (push[0] == 0 && push[1] == 0) push = new int[]{1, 0};
            return new EnemyAction.CompositeAction(List.of(
                new EnemyAction.AreaAttack(self.getGridPos(), 1, self.getAttackPower() + 2, "rampage"),
                new EnemyAction.ForcedMovement(-1, push[0], push[1], 1)));
        });

        // ═══ The Void Walker (warped forest) ═══
        register("void_beam", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("void_beam")) return null;
            GridPos origin = self.getGridPos();
            int[] dir = dirToward(origin, p);
            if (dir[0] == 0 && dir[1] == 0) return null;
            ctx.setCooldown("void_beam", 2);
            int len = ctx.isPhaseTwo() ? 8 : 6;
            List<GridPos> warn = lineWarnTiles(arena, origin, dir[0], dir[1], len);
            if (warn.isEmpty()) return null;
            return new EnemyAction.BossAbility("void_beam",
                new EnemyAction.LineAttack(origin, dir[0], dir[1], len, self.getAttackPower() + 1),
                warn, warn, dir[0], dir[1]);
        });
        register("null_burst", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("null_burst")) return null;
            ctx.setCooldown("null_burst", 3);
            int radius = ctx.isPhaseTwo() ? 2 : 1;
            List<GridPos> warn = ctx.getAreaTiles(arena, p, radius);
            return new EnemyAction.BossAbility("null_burst",
                new EnemyAction.AreaAttack(p, radius, self.getAttackPower(), "null_burst"), warn);
        });
        register("phase_strike", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("phase_strike") || self.minDistanceTo(p) < 2) return null;
            GridPos target = openTileAdjacent(arena, p);
            if (target == null) return null;
            ctx.setCooldown("phase_strike", 3);
            return new EnemyAction.TeleportAndAttack(target, self.getAttackPower() + 2);
        });
        register("void_pull", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("void_pull")) return null;
            int dist = self.minDistanceTo(p);
            if (dist < 3) return null;
            int[] dir = dirToward(p, self.getGridPos());
            ctx.setCooldown("void_pull", 3);
            return new EnemyAction.ForcedMovement(-1, dir[0], dir[1], Math.min(3, dist - 1));
        });
        register("ender_roar", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("ender_roar")) return null;
            ctx.setCooldown("ender_roar", 4);
            return new EnemyAction.AreaAttack(self.getGridPos(), 2,
                Math.max(1, self.getAttackPower() - 1), "ender_roar");
        });

        // ═══ The Wither (basalt deltas) ═══
        register("skull_barrage", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("skull_barrage")) return null;
            int count = ctx.isPhaseTwo() ? 4 : 3;
            List<GridPos> positions = ctx.getProjectileSpawnPositions(arena, self.getGridPos(), 1, p, count);
            if (positions.isEmpty()) return null;
            List<int[]> directions = new ArrayList<>();
            for (GridPos pos : positions) directions.add(dirToward(pos, p));
            ctx.setCooldown("skull_barrage", 3);
            return new EnemyAction.BossAbility("skull_barrage",
                new EnemyAction.SpawnProjectile("minecraft:wither_skull", positions, directions,
                    6, self.getAttackPower(), 0, "wither_skull"),
                positions);
        });
        register("decay_aura", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("decay_aura")) return null;
            ctx.setCooldown("decay_aura", 3);
            return new EnemyAction.AreaAttack(self.getGridPos(), 2,
                Math.max(1, self.getAttackPower() - 2), "wither_decay");
        });

        // ═══ The Void Herald (outer end islands) ═══
        register("void_gale", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("void_gale")) return null;
            int[] dir = dirToward(self.getGridPos(), p);
            if (dir[0] == 0 && dir[1] == 0) return null;
            ctx.setCooldown("void_gale", 3);
            // Telegraphed shove: arrows sweep the throw path so the player can
            // brace against a wall or sidestep the lane.
            List<GridPos> throwPath = lineWarnTiles(arena, p, dir[0], dir[1], 3);
            return new EnemyAction.BossAbility("void_gale",
                new EnemyAction.ForcedMovement(-1, dir[0], dir[1], 3),
                List.of(p), union(List.of(p), throwPath), dir[0], dir[1]);
        });
        register("lightning_strike", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("lightning_strike")) return null;
            ctx.setCooldown("lightning_strike", 3);
            List<GridPos> warn = ctx.getAreaTiles(arena, p, 1);
            List<EnemyAction> strikes = new ArrayList<>();
            strikes.add(new EnemyAction.AreaAttack(p, 1, self.getAttackPower() + 2, "lightning_strike"));
            if (ctx.isPhaseTwo()) {
                // Second bolt lands offset from the first - splitting the dodge space.
                GridPos second = new GridPos(p.x() + (RNG.nextBoolean() ? 2 : -2),
                    p.z() + (RNG.nextBoolean() ? 2 : -2));
                if (arena.isInBounds(second)) {
                    warn = union(warn, ctx.getAreaTiles(arena, second, 1));
                    strikes.add(new EnemyAction.AreaAttack(second, 1, self.getAttackPower() + 2, "lightning_strike"));
                }
            }
            return new EnemyAction.BossAbility("lightning_strike",
                strikes.size() == 1 ? strikes.get(0) : new EnemyAction.CompositeAction(strikes),
                warn);
        });
        register("summon_endermites", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("summon_endermites") || ctx.getAliveMinionCount() >= 4) return null;
            List<GridPos> positions = ctx.findSummonPositionsNear(arena, p, 2, 3);
            if (positions.isEmpty()) return null;
            ctx.setCooldown("summon_endermites", 4);
            return new EnemyAction.BossAbility("summon_endermites",
                new EnemyAction.SummonMinions("minecraft:endermite", positions.size(), positions, 3, 2, 0),
                positions);
        });

        // ═══ The Shulker Architect (end city) ═══
        register("bullet_storm", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("bullet_storm")) return null;
            int count = ctx.isPhaseTwo() ? 4 : 3;
            List<GridPos> positions = ctx.getProjectileSpawnPositions(arena, self.getGridPos(), 1, p, count);
            if (positions.isEmpty()) return null;
            List<int[]> directions = new ArrayList<>();
            for (GridPos pos : positions) directions.add(dirToward(pos, p));
            ctx.setCooldown("bullet_storm", 2);
            return new EnemyAction.SpawnProjectile("minecraft:blaze", positions, directions,
                1, self.getAttackPower(), 0, "shulker_bullet");
        });
        register("fortify_shell", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("fortify_shell")) return null;
            ctx.setCooldown("fortify_shell", 5);
            return new EnemyAction.ModifySelf("defense", 3, 3);
        });

        // ═══ The Chorus Mind (chorus grove) ═══
        register("chorus_bomb", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("chorus_bomb")) return null;
            ctx.setCooldown("chorus_bomb", 3);
            int radius = ctx.isPhaseTwo() ? 2 : 1;
            List<GridPos> warn = ctx.getAreaTiles(arena, p, radius);
            return new EnemyAction.BossAbility("chorus_bomb",
                new EnemyAction.AreaAttack(p, radius, self.getAttackPower() + 1, "chorus_bomb"), warn);
        });
        register("entangle", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("entangle")) return null;
            List<GridPos> tiles = walkableBox(arena, p, 1, null);
            if (tiles.isEmpty()) return null;
            ctx.setCooldown("entangle", 4);
            // Roots web the ground AND grip anyone caught in the blast
            // ("entangle" effect roots for a turn).
            EnemyAction resolve = new EnemyAction.CompositeAction(List.of(
                new EnemyAction.PlaceWeb(tiles, 2),
                new EnemyAction.AreaAttack(p, 1, 2, "entangle")));
            return new EnemyAction.BossAbility("entangle", resolve, tiles);
        });
        register("blink_step", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("blink_step") || self.minDistanceTo(p) <= 2) return null;
            GridPos target = openTileAdjacent(arena, p);
            if (target == null) return null;
            ctx.setCooldown("blink_step", 2);
            return new EnemyAction.Teleport(target);
        });

        // ═══ The Ender Dragon (dragons nest) ═══
        register("breath_wave", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("breath_wave")) return null;
            ctx.setCooldown("breath_wave", 4);
            List<GridPos> warn = ctx.getRowTiles(arena, p.z());
            List<EnemyAction> parts = new ArrayList<>();
            parts.add(new EnemyAction.LineAttack(new GridPos(-1, p.z()), 1, 0, arena.getWidth(),
                self.getAttackPower() + 2));
            if (ctx.isPhaseTwo()) {
                // Phase 2: the breath lingers - the row burns for a round.
                List<GridPos> burnable = new ArrayList<>();
                for (GridPos t : warn) {
                    var tile = arena.getTile(t);
                    if (tile != null && tile.getType() == TileType.NORMAL) burnable.add(t);
                }
                if (!burnable.isEmpty()) {
                    parts.add(new EnemyAction.CreateTerrain(burnable, TileType.FIRE, 1));
                }
            }
            return new EnemyAction.BossAbility("breath_wave",
                parts.size() == 1 ? parts.get(0) : new EnemyAction.CompositeAction(parts), warn);
        });
        register("wing_buffet", (ctx, self, arena, p) -> {
            if (self.minDistanceTo(p) > 1 || ctx.isOnCooldown("wing_buffet")) return null;
            ctx.setCooldown("wing_buffet", 2);
            return new EnemyAction.AttackWithKnockback(self.getAttackPower(), 2);
        });
        register("tail_slam", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("tail_slam")) return null;
            GridPos origin = self.getGridPos();
            ctx.setCooldown("tail_slam", 3);
            // The tail sweeps the whole warned cross and bats the player away.
            int[] push = dirToward(origin, p);
            if (push[0] == 0 && push[1] == 0) push = new int[]{1, 0};
            List<EnemyAction> parts = new ArrayList<>();
            parts.add(new EnemyAction.AreaAttack(origin, 1, self.getAttackPower() + 1, "ground_pound"));
            parts.addAll(crossLines(origin, 2, self.getAttackPower()));
            parts.add(new EnemyAction.ForcedMovement(-1, push[0], push[1], 2));
            List<GridPos> warn = union(ctx.getAreaTiles(arena, origin, 1), crossWarnTiles(arena, origin, 2));
            return new EnemyAction.BossAbility("tail_slam",
                new EnemyAction.CompositeAction(parts), warn);
        });

        // ═══ The Ashen Warlord (unreleased boss - its moves live on here) ═══
        register("wither_slash", (ctx, self, arena, p) -> {
            if (self.minDistanceTo(p) > 1 || ctx.isOnCooldown("wither_slash")) return null;
            ctx.setCooldown("wither_slash", 2);
            return new EnemyAction.AreaAttack(p, 0, self.getAttackPower() + 1, "wither_slash");
        });
        register("fire_pillar", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("fire_pillar")) return null;
            List<GridPos> tiles = walkableBox(arena, p, 1, null);
            if (tiles.isEmpty()) return null;
            ctx.setCooldown("fire_pillar", 3);
            return new EnemyAction.BossAbility("fire_pillar",
                new EnemyAction.CreateTerrain(tiles, TileType.FIRE, 2), tiles);
        });
        register("ash_brand", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("ash_brand")) return null;
            ctx.setCooldown("ash_brand", 4);
            // Brand the player's full row AND column: both lanes scorch to fire.
            // Standing at the intersection when it lands also burns you directly.
            List<GridPos> lanes = union(ctx.getRowTiles(arena, p.z()), ctx.getColumnTiles(arena, p.x()));
            List<GridPos> burnable = new ArrayList<>();
            for (GridPos t : lanes) {
                var tile = arena.getTile(t);
                if (tile != null && tile.isWalkable()) burnable.add(t);
            }
            if (burnable.size() < 4) return null;
            EnemyAction resolve = new EnemyAction.CompositeAction(List.of(
                new EnemyAction.AreaAttack(p, 0, 4, "burning"),
                new EnemyAction.CreateTerrain(burnable, TileType.FIRE, 2)));
            return new EnemyAction.BossAbility("ash_brand", resolve, lanes);
        });
        register("summon_blaze_guard", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("summon_blaze_guard") || ctx.getAliveMinionCount() >= 2) return null;
            List<GridPos> positions = ctx.findSummonPositionsNear(arena, self.getGridPos(), 2, 1);
            if (positions.isEmpty()) return null;
            ctx.setCooldown("summon_blaze_guard", 5);
            return new EnemyAction.BossAbility("summon_blaze_guard",
                new EnemyAction.SummonMinions("minecraft:blaze", positions.size(), positions, 7, 3, 0),
                positions);
        });
    }
}
