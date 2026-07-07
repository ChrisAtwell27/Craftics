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

    // ─── The pool ──────────────────────────────────────────────────────────────

    static {
        // ═══ The Revenant (plains) ═══
        register("raise_dead", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("raise_dead") || ctx.getAliveMinionCount() >= 3) return null;
            int count = ctx.isPhaseTwo() ? 2 : 1;
            List<GridPos> positions = ctx.findSummonPositions(arena, count);
            if (positions.isEmpty()) return null;
            ctx.setCooldown("raise_dead", 3);
            return new EnemyAction.BossAbility("raise_dead",
                new EnemyAction.SummonMinions("minecraft:zombie", positions.size(), positions, 6, 2, 0),
                positions);
        });
        register("death_charge", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("death_charge")) return null;
            List<GridPos> path = chargeLane(self, arena, p, 3);
            if (path == null) return null;
            ctx.setCooldown("death_charge", 2);
            return new EnemyAction.MoveAndAttack(path, self.getAttackPower() + (ctx.isPhaseTwo() ? 4 : 2));
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
            int[] dir = dirToward(self.getGridPos(), p);
            if (dir[0] == 0 && dir[1] == 0) dir[0] = 1;
            ctx.setCooldown("shield_bash", 2);
            return new EnemyAction.ForcedMovement(-1, dir[0], dir[1], 2);
        });

        // ═══ The Hexweaver (forest) ═══
        register("fang_line", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("fang_line")) return null;
            int[] dir = dirToward(self.getGridPos(), p);
            ctx.setCooldown("fang_line", 2);
            return new EnemyAction.LineAttack(self.getGridPos(), dir[0], dir[1], 5,
                self.getAttackPower() + 1);
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
            return new EnemyAction.BossAbility("blizzard",
                new EnemyAction.AreaAttack(p, 2, self.getAttackPower(), "blizzard"), warn);
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
            ctx.setCooldown("harpoon_pull", 3);
            return new EnemyAction.ForcedMovement(-1, dir[0], dir[1], dist - 1);
        });

        // ═══ The Rockbreaker (mountain) ═══
        register("seismic_slam", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("seismic_slam")) return null;
            ctx.setCooldown("seismic_slam", 3);
            List<GridPos> warn = ctx.getCrossTiles(arena, self.getGridPos(), 3);
            return new EnemyAction.BossAbility("seismic_slam",
                new EnemyAction.AreaAttack(self.getGridPos(), 1, self.getAttackPower() + 1, "ground_pound"),
                warn);
        });
        register("boulder_toss", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("boulder_toss") || self.minDistanceTo(p) < 2) return null;
            ctx.setCooldown("boulder_toss", 2);
            List<GridPos> warn = ctx.getAreaTiles(arena, p, 1);
            return new EnemyAction.BossAbility("boulder_toss",
                new EnemyAction.AreaAttack(p, 1, self.getAttackPower() + 1, null), warn);
        });
        register("avalanche", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("avalanche")) return null;
            ctx.setCooldown("avalanche", 4);
            List<GridPos> warn = ctx.getRowTiles(arena, p.z());
            return new EnemyAction.BossAbility("avalanche",
                new EnemyAction.LineAttack(new GridPos(-1, p.z()), 1, 0, arena.getWidth(),
                    self.getAttackPower() + 1),
                warn);
        });
        register("ground_pound", (ctx, self, arena, p) -> {
            if (self.minDistanceTo(p) > 1 || ctx.isOnCooldown("ground_pound")) return null;
            ctx.setCooldown("ground_pound", 2);
            return new EnemyAction.AreaAttack(self.getGridPos(), 1, self.getAttackPower(), "ground_pound");
        });

        // ═══ The Tidecaller (river) ═══
        register("tidal_wave", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("tidal_wave")) return null;
            ctx.setCooldown("tidal_wave", 4);
            List<GridPos> warn = ctx.getColumnTiles(arena, p.x());
            return new EnemyAction.BossAbility("tidal_wave",
                new EnemyAction.LineAttack(new GridPos(p.x(), -1), 0, 1, arena.getHeight(),
                    self.getAttackPower() + 1),
                warn);
        });
        register("trident_storm", (ctx, self, arena, p) -> {
            int dist = self.minDistanceTo(p);
            if (dist < 2 || dist > 5 || ctx.isOnCooldown("trident_storm")) return null;
            ctx.setCooldown("trident_storm", 2);
            return new EnemyAction.RangedAttack(self.getAttackPower() + 1, "trident");
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
            GridPos mine = openTileAdjacent(arena, p);
            if (mine == null) return null;
            ctx.setCooldown("plant_mine", 3);
            // Registers a persistent step-trigger sand mine (special-cased in resolveAreaAttack).
            return new EnemyAction.AreaAttack(mine, 0, 0, "plant_mine");
        });
        register("sand_burial", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("sand_burial")) return null;
            ctx.setCooldown("sand_burial", 3);
            List<GridPos> warn = ctx.getAreaTiles(arena, p, 1);
            return new EnemyAction.BossAbility("sand_burial",
                new EnemyAction.AreaAttack(p, 1, self.getAttackPower(), "slowness"), warn);
        });
        register("sandstorm", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("sandstorm")) return null;
            ctx.setCooldown("sandstorm", 4);
            return new EnemyAction.AreaAttack(self.getGridPos(), 3,
                Math.max(1, self.getAttackPower() - 1), "weakness");
        });
        register("curse_of_the_sands", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("curse_of_the_sands")) return null;
            ctx.setCooldown("curse_of_the_sands", 4);
            return new EnemyAction.BossAbility("curse_of_the_sands",
                new EnemyAction.AreaAttack(p, 0, self.getAttackPower(), "wither"), List.of(p));
        });

        // ═══ The Broodmother (jungle) ═══
        register("web_spray", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("web_spray")) return null;
            List<GridPos> tiles = walkableBox(arena, p, 1, null);
            if (tiles.isEmpty()) return null;
            ctx.setCooldown("web_spray", 3);
            return new EnemyAction.PlaceWeb(tiles, 2);
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
                new EnemyAction.AreaAttack(p, 1, self.getAttackPower(), null), warn);
        });
        register("cave_in", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("cave_in")) return null;
            List<GridPos> tiles = ctx.findSummonPositionsNear(arena, p, 3, 6);
            if (tiles.size() < 3) return null;
            ctx.setCooldown("cave_in", 4);
            return new EnemyAction.BossAbility("cave_in",
                new EnemyAction.CreateTerrain(tiles, TileType.OBSTACLE, 3), tiles);
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
            return new EnemyAction.AreaAttack(self.getGridPos(), 2,
                Math.max(1, self.getAttackPower() - 1), "darkness_pulse");
        });
        register("tremor_stomp", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("tremor_stomp")) return null;
            ctx.setCooldown("tremor_stomp", 3);
            List<GridPos> warn = ctx.getCrossTiles(arena, self.getGridPos(), 2);
            return new EnemyAction.BossAbility("tremor_stomp",
                new EnemyAction.AreaAttack(self.getGridPos(), 1, self.getAttackPower() + 1, "tremor_stomp"),
                warn);
        });

        // ═══ The Molten King (nether wastes) ═══
        register("magma_eruption", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("magma_eruption")) return null;
            List<GridPos> tiles = ctx.findSummonPositionsNear(arena, p, 2, 5);
            if (tiles.size() < 3) return null;
            ctx.setCooldown("magma_eruption", 3);
            return new EnemyAction.BossAbility("magma_eruption",
                new EnemyAction.CreateTerrain(tiles, TileType.FIRE, 2), tiles);
        });
        register("lava_cage", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("lava_cage")) return null;
            List<GridPos> ring = ringTiles(arena, p, 2);
            if (ring.size() < 5) return null;
            ctx.setCooldown("lava_cage", 4);
            return new EnemyAction.BossAbility("lava_cage",
                new EnemyAction.CreateTerrain(ring, TileType.FIRE, 2), ring);
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
            List<GridPos> positions = ctx.getProjectileSpawnPositions(arena, self.getGridPos(), 1, p, 2);
            if (positions.isEmpty()) return null;
            List<int[]> directions = new ArrayList<>();
            for (GridPos pos : positions) directions.add(dirToward(pos, p));
            ctx.setCooldown("fireball_barrage", 3);
            return new EnemyAction.BossAbility("fireball_barrage",
                new EnemyAction.SpawnProjectile("minecraft:blaze", positions, directions,
                    4, self.getAttackPower(), 0, "ghast_fireball"),
                positions);
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
            List<GridPos> cross = ctx.getCrossTiles(arena, self.getGridPos(), 3);
            List<GridPos> burnable = new ArrayList<>();
            for (GridPos pos : cross) {
                var tile = arena.getTile(pos);
                if (tile != null && tile.isWalkable() && !arena.isOccupied(pos)) burnable.add(pos);
            }
            if (burnable.size() < 3) return null;
            ctx.setCooldown("ground_slam", 4);
            return new EnemyAction.BossAbility("ground_slam",
                new EnemyAction.CreateTerrain(burnable, TileType.FIRE, 1), burnable);
        });
        register("rampage", (ctx, self, arena, p) -> {
            if (self.minDistanceTo(p) > 1 || ctx.isOnCooldown("rampage")) return null;
            ctx.setCooldown("rampage", 3);
            return new EnemyAction.AreaAttack(self.getGridPos(), 1, self.getAttackPower() + 2, null);
        });

        // ═══ The Void Walker (warped forest) ═══
        register("void_beam", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("void_beam")) return null;
            int[] dir = dirToward(self.getGridPos(), p);
            ctx.setCooldown("void_beam", 2);
            return new EnemyAction.LineAttack(self.getGridPos(), dir[0], dir[1], 6,
                self.getAttackPower() + 1);
        });
        register("null_burst", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("null_burst")) return null;
            ctx.setCooldown("null_burst", 3);
            List<GridPos> warn = ctx.getAreaTiles(arena, p, 1);
            return new EnemyAction.BossAbility("null_burst",
                new EnemyAction.AreaAttack(p, 1, self.getAttackPower(), "null_burst"), warn);
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
            List<GridPos> positions = ctx.getProjectileSpawnPositions(arena, self.getGridPos(), 1, p, 2);
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
            return new EnemyAction.ForcedMovement(-1, dir[0], dir[1], 3);
        });
        register("lightning_strike", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("lightning_strike")) return null;
            ctx.setCooldown("lightning_strike", 3);
            List<GridPos> warn = ctx.getCrossTiles(arena, p, 1);
            warn.add(p);
            return new EnemyAction.BossAbility("lightning_strike",
                new EnemyAction.AreaAttack(p, 1, self.getAttackPower() + 2, null), warn);
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
            List<GridPos> positions = ctx.getProjectileSpawnPositions(arena, self.getGridPos(), 1, p, 2);
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
            List<GridPos> warn = ctx.getAreaTiles(arena, p, 1);
            return new EnemyAction.BossAbility("chorus_bomb",
                new EnemyAction.AreaAttack(p, 1, self.getAttackPower() + 1, null), warn);
        });
        register("entangle", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("entangle")) return null;
            List<GridPos> tiles = walkableBox(arena, p, 1, null);
            if (tiles.isEmpty()) return null;
            ctx.setCooldown("entangle", 4);
            return new EnemyAction.PlaceWeb(tiles, 2);
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
            return new EnemyAction.BossAbility("breath_wave",
                new EnemyAction.LineAttack(new GridPos(-1, p.z()), 1, 0, arena.getWidth(),
                    self.getAttackPower() + 2),
                warn);
        });
        register("wing_buffet", (ctx, self, arena, p) -> {
            if (self.minDistanceTo(p) > 1 || ctx.isOnCooldown("wing_buffet")) return null;
            ctx.setCooldown("wing_buffet", 2);
            return new EnemyAction.AttackWithKnockback(self.getAttackPower(), 2);
        });
        register("tail_slam", (ctx, self, arena, p) -> {
            if (ctx.isOnCooldown("tail_slam")) return null;
            ctx.setCooldown("tail_slam", 3);
            List<GridPos> warn = ctx.getCrossTiles(arena, self.getGridPos(), 2);
            return new EnemyAction.BossAbility("tail_slam",
                new EnemyAction.AreaAttack(self.getGridPos(), 1, self.getAttackPower() + 1, null),
                warn);
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
