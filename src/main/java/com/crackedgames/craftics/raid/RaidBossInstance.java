package com.crackedgames.craftics.raid;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.combat.CombatManager;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import com.crackedgames.craftics.level.ArenaBuilder;
import com.crackedgames.craftics.level.LevelDefinition;
import com.crackedgames.craftics.network.EnterCombatPayload;
import com.crackedgames.craftics.network.ExitCombatPayload;
import com.crackedgames.craftics.world.HubTeleports;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SoulFireBlock;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * One running raid: its own dimension, its own arena, its own CombatManager, up
 * to eight players.
 *
 * <p>The roster is fixed at start. A player can only leave it, never join it, and
 * leaving by any route (AFK removal, disconnect, manual leave) forfeits their
 * reward. Players merely DOWNED are not forfeited: they spectate through
 * CombatManager's existing dead-party-member path and are rewarded on a win.
 */
public final class RaidBossInstance {

    /** Fixed arena origin inside the instance's private dimension. */
    private static final BlockPos ARENA_ORIGIN = new BlockPos(0, 64, 0);

    private static final Map<UUID, RaidBossInstance> ACTIVE = new LinkedHashMap<>();
    private static final Random VARIANT_RNG = new Random();

    private final UUID instanceId;
    private final RaidBossDefinition boss;
    private final List<UUID> roster;
    private final Set<UUID> forfeited = new HashSet<>();
    private final Map<UUID, Integer> strikes = new HashMap<>();

    private UUID leaderUuid;

    public RaidBossInstance(UUID instanceId, RaidBossDefinition boss, List<UUID> roster) {
        this.instanceId = instanceId;
        this.boss = boss;
        this.roster = new ArrayList<>(roster);
    }

    public UUID id() { return instanceId; }
    public RaidBossDefinition boss() { return boss; }
    public List<UUID> roster() { return List.copyOf(roster); }
    public UUID leaderUuid() { return leaderUuid; }

    public boolean isForfeited(UUID player) { return forfeited.contains(player); }

    public List<UUID> rewardedPlayers() {
        List<UUID> out = new ArrayList<>();
        for (UUID id : roster) if (!forfeited.contains(id)) out.add(id);
        return out;
    }

    public int strikesFor(UUID player) { return strikes.getOrDefault(player, 0); }

    public int addStrike(UUID player) {
        int next = strikesFor(player) + 1;
        strikes.put(player, next);
        return next;
    }

    // ---- lifecycle ----

    /**
     * Open the dimension, build the arena, scatter the boss's obstacles, and pull
     * the roster into combat. Mirrors RunInviteManager.beginRun: teleport the
     * leader, start combat, register members, scatter them onto free tiles, then
     * finishPartyJoin.
     */
    public boolean start(MinecraftServer server) {
        List<ServerPlayerEntity> players = new ArrayList<>();
        // Eligibility was only ever checked once, at lobby join - up to
        // raidBossJoinWindowSeconds (five minutes by default) before this runs. Re-run
        // the SAME check (RaidBossLobby.checkEligibility, shared with join() so the two
        // can never drift apart) here per player: a joiner who went idle and then
        // started a biome run, or wandered onto someone else's island, in that window
        // must not be pulled out of whatever they are now doing and have
        // setRaidBossContext(this) stamped onto the CombatManager driving it - which
        // would make that fight take the raid turn timer, AFK strikes, the no-penalty
        // game over, and the raid's bounty/loot on victory, for an ordinary fight.
        // Removed from the roster outright (not just skipped here) so they are never
        // counted as forfeited-but-owed-nothing, never rewarded, and never chased by
        // finish()'s return-home loop for a raid they were never actually in.
        java.util.Iterator<UUID> rosterIt = roster.iterator();
        while (rosterIt.hasNext()) {
            UUID id = rosterIt.next();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null) continue; // offline: start() already tolerates this below
            RaidBossLobby.JoinResult ineligible = RaidBossLobby.checkEligibility(p);
            if (ineligible != null) {
                String reason = switch (ineligible) {
                    case BUSY -> "you're mid-run or mid-combat";
                    case VISITING -> "you're visiting another island";
                    default -> "you're no longer eligible";
                };
                p.sendMessage(Text.literal("§cYou were dropped from the raid: " + reason + "."), false);
                RaidBossOrigins.forget(id);
                rosterIt.remove();
                continue;
            }
            players.add(p);
        }
        if (players.isEmpty()) {
            CrafticsMod.LOGGER.warn("Raid instance {} had no online players at start", instanceId);
            return false;
        }

        // A raid instance and a merchant scene are mutually exclusive, exactly like a
        // normal biome run (RunInviteManager.beginRun does the same eject before
        // teleporting anyone into an arena): without this, a participant who is still
        // registered as a scene member when they cross into the raid dimension stays
        // wedged in both systems at once.
        for (ServerPlayerEntity p : players) {
            com.crackedgames.craftics.scene.SceneController.ejectForRun(p);
        }

        ServerWorld world = RaidBossDimensions.open(server, instanceId);
        if (world == null) {
            CrafticsMod.LOGGER.error("Raid instance {}: could not open its dimension", instanceId);
            // open() already added the (world-less) handle to its HANDLES map before
            // returning null here, so without this the handle leaks forever. Mirrors
            // the arena-build-failure branch below.
            RaidBossDimensions.close(instanceId);
            return false;
        }

        RaidBossLevelDefinition levelDef = new RaidBossLevelDefinition(
            boss, VARIANT_RNG.nextInt(10), ARENA_ORIGIN);
        GridArena arena = ArenaBuilder.buildAt(world, levelDef, ARENA_ORIGIN);
        if (arena == null) {
            CrafticsMod.LOGGER.error("Raid instance {}: arena build failed", instanceId);
            RaidBossDimensions.close(instanceId);
            return false;
        }
        warnIfArenaTooSmall(arena);

        // Scatter this boss's authored obstacles before anyone (or the boss) occupies
        // a tile, so the free-cell sampling and the connectivity re-check both see a
        // clean floor.
        applyObstaclePlan(world, arena, levelDef);

        ServerPlayerEntity leader = players.get(0);
        this.leaderUuid = leader.getUuid();

        BlockPos startPos = arena.getPlayerStartBlockPos();
        float cameraYaw = ArenaBuilder.consumePendingCameraYaw();
        BlockPos origin = arena.getOrigin();

        // Remember exactly where (and which world, and which way) the leader was
        // standing BEFORE they cross into the raid dimension, so every exit path
        // (win, wipe, forfeit, disconnect, server stop) can put them back there.
        RaidBossOrigins.remember(leader.getUuid(), captureOrigin(leader));
        HubTeleports.teleportTo(leader, world, startPos.getX() + 0.5, startPos.getY(), startPos.getZ() + 0.5);
        ServerPlayNetworking.send(leader, new EnterCombatPayload(
            origin.getX(), origin.getY(), origin.getZ(),
            arena.getWidth(), arena.getHeight(), cameraYaw));

        CombatManager cm = CombatManager.get(leader);
        cm.setRaidBossContext(this);
        cm.startCombat(leader, arena, levelDef);
        cm.addPartyMember(leader);

        GridPos leaderGrid = arena.getPlayerGridPos();
        Set<GridPos> reserved = new HashSet<>();
        reserved.add(leaderGrid);
        int idx = 0;
        for (ServerPlayerEntity member : players) {
            if (member.getUuid().equals(leader.getUuid())) continue;
            int dx = (idx % 2 == 0) ? ((idx / 2) + 1) : -((idx / 2) + 1);
            idx++;
            int cx = Math.max(0, Math.min(arena.getWidth() - 1, leaderGrid.x() + dx));
            int cz = Math.max(0, Math.min(arena.getHeight() - 1, leaderGrid.z()));
            GridPos chosen = CombatManager.findNearestWalkableUnreserved(
                arena, new GridPos(cx, cz), reserved);
            if (chosen == null) chosen = leaderGrid;
            reserved.add(chosen);

            BlockPos bp = arena.gridToBlockPos(chosen);
            RaidBossOrigins.remember(member.getUuid(), captureOrigin(member));
            HubTeleports.teleportTo(member, world, bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
            member.changeGameMode(net.minecraft.world.GameMode.ADVENTURE);
            ServerPlayNetworking.send(member, new EnterCombatPayload(
                origin.getX(), origin.getY(), origin.getZ(),
                arena.getWidth(), arena.getHeight(), cameraYaw));
            cm.addPartyMember(member);
            com.crackedgames.craftics.item.MoveSlotManager.enforce(member);
            CombatManager.get(member.getUuid()).setRaidBossContext(this);
        }
        cm.finishPartyJoin();

        register(this);
        CrafticsMod.LOGGER.info("Raid instance {} started: boss='{}' players={}",
            instanceId, boss.id(), players.size());
        // No broadcast here: the spec announces the raid descending ONCE for the whole
        // event, not once per instance. RaidBossSchedule.startAllInstances sends the
        // single server-wide line after every instance in the pack has started.
        return true;
    }

    private void warnIfArenaTooSmall(GridArena arena) {
        int min = CrafticsMod.CONFIG.raidBossMinArenaGrid();
        if (arena.getWidth() < min || arena.getHeight() < min) {
            CrafticsMod.LOGGER.warn(
                "Raid arena for '{}' is {}x{}, smaller than raidBossMinArenaGrid={}. "
                + "Eight players plus summons will not fit comfortably; enlarge the raidboss schem.",
                boss.id(), arena.getWidth(), arena.getHeight(), min);
        }
    }

    /** Snapshot of a player's current world/position/facing, for {@link RaidBossOrigins}. */
    private static RaidBossOrigins.Origin captureOrigin(ServerPlayerEntity p) {
        ServerWorld world = (ServerWorld) p.getEntityWorld();
        return new RaidBossOrigins.Origin(
            world.getRegistryKey().getValue().toString(),
            p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch());
    }

    // ---- obstacle placement (Task 11 addendum) ----

    /**
     * Scatter this boss's authored obstacles ({@link RaidBossDefinition#obstacles()})
     * across the freshly built arena, then drop whichever OBSTACLE placements would
     * cut the player start off from the rest of the floor.
     *
     * <p>{@link RaidBossObstaclePlan} works over its own {@code Cell} record rather
     * than {@link GridPos} (so the placement maths stays off the Minecraft classpath
     * for unit testing); this method is the boundary that maps between the two.
     */
    private void applyObstaclePlan(ServerWorld world, GridArena arena, RaidBossLevelDefinition levelDef) {
        List<RaidBossObstacle> obstacles = boss.obstacles();
        if (obstacles == null || obstacles.isEmpty()) return;

        GridPos playerStart = arena.getPlayerStart();
        GridPos bossSpawn = adaptedBossSpawn(levelDef, arena);

        // Every currently-walkable tile is the universe the arena must stay connected
        // across; the same set, minus the two reserved tiles, is what obstacles may
        // actually be seeded on.
        Set<GridPos> walkable = new HashSet<>();
        for (int x = 0; x < arena.getWidth(); x++) {
            for (int z = 0; z < arena.getHeight(); z++) {
                GridPos pos = new GridPos(x, z);
                GridTile tile = arena.getTile(pos);
                if (tile != null && tile.isWalkable()) walkable.add(pos);
            }
        }

        Set<RaidBossObstaclePlan.Cell> freeCells = new HashSet<>();
        for (GridPos pos : walkable) {
            if (pos.equals(playerStart) || pos.equals(bossSpawn)) continue;
            freeCells.add(new RaidBossObstaclePlan.Cell(pos.x(), pos.z()));
        }

        List<RaidBossObstaclePlan.Placement> placements =
            RaidBossObstaclePlan.plan(obstacles, freeCells, VARIANT_RNG);
        if (placements.isEmpty()) return;

        int maxReach = arena.getWidth() * arena.getHeight();
        Set<GridPos> blockedObstacles = new HashSet<>();
        Map<String, Integer> placedByType = new LinkedHashMap<>();
        int dropped = 0;

        for (RaidBossObstaclePlan.Placement placement : placements) {
            TileType type;
            try {
                type = TileType.valueOf(placement.tileType());
            } catch (IllegalArgumentException e) {
                CrafticsMod.LOGGER.warn("Raid boss '{}': unknown obstacle tile type '{}', skipped",
                    boss.id(), placement.tileType());
                continue;
            }
            GridPos pos = new GridPos(placement.cell().x(), placement.cell().z());
            Block block = resolveObstacleBlock(placement.blockId(), type, arena);

            if (type != TileType.OBSTACLE) {
                // Only OBSTACLE tiles are unwalkable among the supported hazard types, so
                // only they can ever cut the arena in two; everything else is always safe
                // to place outright.
                applyTileType(arena, pos, type, block);
                paintWorldBlock(world, arena, pos, type, block);
                placedByType.merge(type.name(), 1, Integer::sum);
                continue;
            }

            GridTile previous = arena.getTile(pos);
            applyTileType(arena, pos, type, block);
            blockedObstacles.add(pos);

            Set<GridPos> reachable = Pathfinding.getReachableTiles(arena, playerStart, maxReach, true);
            reachable.add(playerStart);
            boolean stillConnected = true;
            for (GridPos required : walkable) {
                if (blockedObstacles.contains(required)) continue;
                if (!reachable.contains(required)) {
                    stillConnected = false;
                    break;
                }
            }

            if (stillConnected) {
                paintWorldBlock(world, arena, pos, type, block);
                placedByType.merge(type.name(), 1, Integer::sum);
            } else {
                // Revert: this one placement would isolate part of the arena from the
                // player start. Restore the tile it replaced and try the next one -
                // shipping a fight the player can't finish is worse than a lighter
                // obstacle field.
                arena.setTile(pos, previous);
                blockedObstacles.remove(pos);
                dropped++;
            }
        }

        int placedTotal = placedByType.values().stream().mapToInt(Integer::intValue).sum();
        CrafticsMod.LOGGER.info(
            "Raid boss '{}': scattered {} obstacle tile(s) {} ({} dropped for connectivity)",
            boss.id(), placedTotal, placedByType, dropped);
    }

    /** Update the logical grid tile only; the world block is written separately by
     *  {@link #paintWorldBlock} once a candidate OBSTACLE placement is confirmed safe. */
    private static void applyTileType(GridArena arena, GridPos pos, TileType type, Block block) {
        arena.setTile(pos, new GridTile(type, block));
    }

    /**
     * Write {@code block} into the world for tile {@code pos}, at the same Y-level
     * ArenaBuilder's own schematic scan reads that tile type from: OBSTACLE, the open
     * flames, and tall grass/fern all stand in the overlay slot at floor+1 with the
     * floor left intact underneath; every other supported hazard (lava, water, ice,
     * powder snow, mud) replaces the floor block itself.
     */
    private static void paintWorldBlock(ServerWorld world, GridArena arena, GridPos pos,
                                        TileType type, Block block) {
        BlockPos origin = arena.getOrigin();
        BlockPos floor = new BlockPos(origin.getX() + pos.x(), origin.getY(), origin.getZ() + pos.z());
        boolean overlay = type.isFlames() || type == TileType.OBSTACLE
            || type == TileType.TALL_GRASS || type == TileType.TALL_FERN;
        if (overlay) {
            if (type == TileType.SOUL_FIRE) ensureSoulBase(world, floor);
            world.setBlockState(floor.up(1), block.getDefaultState(), Block.NOTIFY_ALL);
        } else {
            world.setBlockState(floor, block.getDefaultState(), Block.NOTIFY_ALL);
        }
    }

    /** Soul fire is deleted by vanilla unless soul sand/soil sits under it
     *  (see {@code CombatManager.ensureSoulBase}, which this mirrors). */
    private static void ensureSoulBase(ServerWorld world, BlockPos floor) {
        if (SoulFireBlock.isSoulBase(world.getBlockState(floor))) return;
        world.setBlockState(floor, Blocks.SOUL_SOIL.getDefaultState());
    }

    /** Resolve the block for a placement: the authored blockId when given, else a
     *  sensible per-tile-type default. OBSTACLE's default reuses whatever block this
     *  arena's own walls are already built from, falling back to GridTile's generic
     *  default (stone) only when the arena has no OBSTACLE tile to sample. */
    private static Block resolveObstacleBlock(String blockId, TileType type, GridArena arena) {
        if (blockId != null && !blockId.isBlank()) {
            // tryParse, not of(): an authored blockId is untrusted JSON text, and
            // RaidBossParser only checks that it is a string, never that it is a
            // well-formed identifier. of() throws on anything malformed, which would
            // abort start() mid-build and leak the dimension it already opened.
            Identifier id = Identifier.tryParse(blockId);
            Block resolved = id != null ? Registries.BLOCK.get(id) : null;
            if (resolved != null && resolved != Blocks.AIR) return resolved;
        }
        if (type == TileType.OBSTACLE) return arenaObstacleBlock(arena);
        // GridTile's own no-arg-block constructor already carries a sane default per
        // TileType (LAVA -> lava, WATER -> water, SOUL_FIRE -> soul fire, and so on);
        // reusing it here avoids duplicating that mapping.
        return new GridTile(type).getBlockType();
    }

    /** First OBSTACLE block already standing in this arena, or plain stone if it has none. */
    private static Block arenaObstacleBlock(GridArena arena) {
        for (int x = 0; x < arena.getWidth(); x++) {
            for (int z = 0; z < arena.getHeight(); z++) {
                GridTile tile = arena.getTile(x, z);
                if (tile != null && tile.getType() == TileType.OBSTACLE) return tile.getBlockType();
            }
        }
        return new GridTile(TileType.OBSTACLE).getBlockType();
    }

    /**
     * The boss's authored spawn tile, remapped into the ACTUAL built arena's grid the
     * same way CombatManager's own (private) {@code adaptSpawnToArena} would: raid
     * levels report a fixed 16x16 fallback size from {@code getWidth()/getHeight()}
     * regardless of the real "raidboss" schem's dimensions, so the raw spawn GridPos
     * only makes sense scaled proportionally into the arena that actually got built.
     * Duplicated in miniature here rather than widening that method's visibility,
     * since this task's only sanctioned CombatManager change is the raid context field.
     */
    private static GridPos adaptedBossSpawn(RaidBossLevelDefinition levelDef, GridArena arena) {
        LevelDefinition.EnemySpawn[] spawns = levelDef.getEnemySpawns();
        int idx = levelDef.getBossSpawnIndex();
        if (spawns == null || idx < 0 || idx >= spawns.length) return null;
        GridPos raw = spawns[idx].position();
        int x = scaleGridCoordinate(raw.x(), levelDef.getWidth(), arena.getWidth());
        int z = scaleGridCoordinate(raw.z(), levelDef.getHeight(), arena.getHeight());
        return new GridPos(x, z);
    }

    private static int scaleGridCoordinate(int value, int sourceSize, int targetSize) {
        if (targetSize <= 1) return 0;
        if (sourceSize <= 1) return Math.min(targetSize - 1, targetSize / 2);
        double ratio = (double) value / (double) (sourceSize - 1);
        int mapped = (int) Math.round(ratio * (targetSize - 1));
        return Math.max(0, Math.min(targetSize - 1, mapped));
    }

    // ---- exit paths ----

    /**
     * Drop a player from the raid with no reward, and send them home. Once every
     * roster member has forfeited, nobody remains to fight, win or lose the raid,
     * so this also tears the whole instance down - see the check at the end.
     */
    public void forfeit(UUID playerId, String reason) {
        if (playerId == null || forfeited.contains(playerId)) return;
        forfeited.add(playerId);
        CrafticsMod.LOGGER.info("Raid instance {}: {} forfeited ({})", instanceId, playerId, reason);

        CombatManager cm = leaderUuid != null ? CombatManager.get(leaderUuid) : null;
        if (cm != null) cm.removePartyMember(playerId);
        // Clear the leaving player's OWN raid context - UNLESS they are the leader.
        // CombatManager.get(uuid) is a strict per-UUID singleton, and start() drives
        // the WHOLE party through CombatManager.get(leader): for the leader, "their
        // own" CombatManager IS the shared instance every other member is still
        // fighting in. Nulling it out the moment the leader forfeits (AFK, disconnect,
        // or a manual leave) would blank every raid branch CombatManager checks - the
        // wipe check, the victory check, the turn-timer override - out from under the
        // survivors mid-fight, dropping them onto ordinary (and far harsher) biome-run
        // penalties instead. The shared context is cleared by finish() instead, once
        // the raid is actually over - see the roster-empty check below, and finish().
        if (!playerId.equals(leaderUuid)) {
            CombatManager.get(playerId).setRaidBossContext(null);
        }

        MinecraftServer server = CrafticsMod.currentServer();
        if (server == null) {
            // Nobody to teleport, and the roster is fixed - this player can never
            // rejoin this instance - so the stored origin has no further use. Forget
            // it here rather than leaving it for finish() to find: an instance that is
            // forfeited-then-never-finished (e.g. every member leaves and nothing else
            // ever calls finish()) would otherwise leak this entry forever. No server
            // also means finish() below could never run anyway (it needs one), so
            // there is nothing left to do.
            RaidBossOrigins.forget(playerId);
            return;
        }
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(playerId);
        if (p == null) {
            // Player already disconnected (the realistic path: the disconnect handler
            // defers its own cleanup to next tick via server.execute, so PlayerManager
            // has already dropped them by the time forfeit() runs). Same reasoning as
            // above - forget the now-unusable origin immediately.
            RaidBossOrigins.forget(playerId);
        } else {
            p.sendMessage(Text.literal("§cYou left the raid. No reward."), false);
            ServerPlayNetworking.send(p, new ExitCombatPayload(false));
            returnHome(server, p);
        }

        // Nobody left who could still fight, win or lose this raid - tear it down now
        // rather than leaking the instance (still in ACTIVE) and its private dimension
        // forever, and leaving the shared CombatManager stuck re-triggering an AFK
        // strike every raidBossTurnSeconds against a roster that is now empty.
        if (rewardedPlayers().isEmpty()) {
            if (cm != null) cm.endCombat();
            finish(server);
        }
    }

    /** Evacuate everyone still inside and delete the dimension. Safe to call twice. */
    public void finish(MinecraftServer server) {
        if (!ACTIVE.containsKey(instanceId)) return;
        unregister(instanceId);

        // Self-sufficient: end combat and notify every still-connected, non-forfeited
        // participant here, rather than depending on the caller having already done it.
        // The three normal exits (victory, wipe, and forfeit()'s own empty-roster
        // teardown) already end combat and send an ExitCombatPayload - with the
        // correct won/lost flag - just before calling finish(); gating on isActive()
        // (rather than sending unconditionally) means those callers are not just
        // "unaffected" but never get a SECOND, contradicting ExitCombatPayload(false)
        // chasing a real ExitCombatPayload(true). An admin /raidboss cancel and the
        // SERVER_STOPPING handler, by contrast, reach finish() only through endAll()
        // and never ran either step: without this, a cancelled raid left players stuck
        // in ADVENTURE mode and the client's combat HUD, PlayerData.inCombat never
        // cleared, and this instance's CombatManager kept ticking its turn timer
        // against an arena in the dimension this method is about to delete.
        if (leaderUuid != null) {
            CombatManager leaderCm = CombatManager.get(leaderUuid);
            if (leaderCm.isActive()) {
                for (UUID id : roster) {
                    if (forfeited.contains(id)) continue;
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                    if (p != null) ServerPlayNetworking.send(p, new ExitCombatPayload(false));
                }
                leaderCm.endCombat();
            }
        }

        for (UUID id : roster) {
            // The leader's own CombatManager is the shared instance that actually
            // drives the whole raid (see forfeit()'s comment: its context is
            // deliberately left set on the leader's CM while the raid is still
            // running, even once the leader themselves forfeits, so the fight keeps
            // functioning for the rest of the party). Clear every roster UUID's own
            // context unconditionally, before the forfeited/offline checks below -
            // which only decide whether to teleport - so a forfeited OR offline member
            // can never be left pointing at this now-dead instance into their next
            // ordinary fight.
            CombatManager.get(id).setRaidBossContext(null);

            // Already sent home (and had their RaidBossOrigins entry consumed, via
            // take() or an explicit forget() on the offline/disconnected branches) by
            // forfeit(). Do NOT forget() it again here: if this player has since
            // joined a LATER raid, forgetting here would delete THAT raid's freshly
            // remembered origin instead of a stale one of ours, stranding them at that
            // raid's lobby when it ends. Re-running returnHome would have the same
            // problem in the other direction - finding no entry and falling through to
            // HubTeleports.toLobby, yanking a player who legitimately left this raid,
            // possibly long ago, to the lobby from wherever they are now.
            if (forfeited.contains(id)) continue;

            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null) {
                RaidBossOrigins.forget(id);
                continue;
            }
            if (p.isSpectator()) p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            returnHome(server, p);
        }
        RaidBossDimensions.close(instanceId);
        CrafticsMod.LOGGER.info("Raid instance {} finished", instanceId);
    }

    /** Put one player back exactly where they joined from; lobby is the fallback. */
    public static void returnHome(MinecraftServer server, ServerPlayerEntity p) {
        RaidBossOrigins.Origin origin = RaidBossOrigins.take(p.getUuid());
        if (origin == null) {
            HubTeleports.toLobby(p);
            return;
        }
        ServerWorld target = null;
        for (ServerWorld w : server.getWorlds()) {
            if (w.getRegistryKey().getValue().toString().equals(origin.dimensionId())) {
                target = w;
                break;
            }
        }
        if (target == null) {
            HubTeleports.toLobby(p);
            return;
        }
        HubTeleports.teleportTo(p, target, origin.x(), origin.y(), origin.z(), origin.yaw(), origin.pitch());
    }

    // ---- registry ----

    public static void register(RaidBossInstance instance) {
        ACTIVE.put(instance.instanceId, instance);
    }

    public static void unregister(UUID instanceId) {
        ACTIVE.remove(instanceId);
    }

    public static List<RaidBossInstance> active() {
        return new ArrayList<>(ACTIVE.values());
    }

    public static RaidBossInstance byPlayer(UUID playerId) {
        for (RaidBossInstance instance : ACTIVE.values()) {
            if (instance.roster.contains(playerId)) return instance;
        }
        return null;
    }

    /** Server stop, or an admin cancel: tear every live raid down. */
    public static void endAll(MinecraftServer server) {
        for (RaidBossInstance instance : active()) {
            instance.finish(server);
        }
        ACTIVE.clear();
        RaidBossDimensions.closeAll();
    }
}
