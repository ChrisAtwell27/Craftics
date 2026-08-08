package com.crackedgames.craftics.raid;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;
import xyz.nucleoid.fantasy.util.VoidChunkGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One TEMPORARY runtime dimension per raid instance: craftics:raid/&lt;uuid&gt;.
 *
 * <p>Temporary rather than persistent (unlike islands): a raid dimension holds
 * nothing worth surviving a restart, and leaving persistent worlds behind would
 * leak a region folder per raid, forever. Fantasy's {@code openTemporaryWorld}
 * also registers a JVM {@code forceDeleteOnExit} hook for the world folder at
 * creation time, so a graceful shutdown cleans up even if {@link #close} is
 * never reached. Only a hard kill (crash, power loss) skips that hook and can
 * leave a folder behind; see {@link #deleteOrphans} for why that residue can't
 * be swept up from here.
 */
public final class RaidBossDimensions {
    private RaidBossDimensions() {}

    private static final String PATH_PREFIX = "raid/";

    private static final Map<UUID, RuntimeWorldHandle> HANDLES = new HashMap<>();

    /** Instance ids for which {@link #close} has been called but Fantasy has
     *  not yet actually removed the world. {@code RuntimeWorldHandle.delete()}
     *  only ENQUEUES deletion: Fantasy's own tick loop evacuates any remaining
     *  players and waits for the last chunk to unload before dropping the world
     *  from {@code server.getWorlds()}, which can take several further ticks.
     *  {@link #isTracked} treats ids in this set as expected/draining rather
     *  than anomalous. Entries are dropped once the corresponding world is no
     *  longer among the loaded raid worlds; see {@link #reconcileClosing}. */
    private static final Set<UUID> CLOSING = new HashSet<>();

    public static ServerWorld open(MinecraftServer server, UUID instanceId) {
        RuntimeWorldHandle handle = HANDLES.get(instanceId);
        if (handle == null) {
            //? if <=1.21.1 {
            var biomeRegistry = server.getRegistryManager().get(net.minecraft.registry.RegistryKeys.BIOME);
            //?} else {
            /*var biomeRegistry = server.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.BIOME);
            *///?}
            RuntimeWorldConfig config = new RuntimeWorldConfig()
                .setDimensionType(net.minecraft.world.dimension.DimensionTypes.OVERWORLD)
                .setGenerator(new VoidChunkGenerator(
                    biomeRegistry, net.minecraft.world.biome.BiomeKeys.PLAINS))
                .setDifficulty(net.minecraft.world.Difficulty.NORMAL)
                .setShouldTickTime(false)
                // A raid arena is a stage. Nothing should wander into it.
                .setGameRule(net.minecraft.world.GameRules.DO_MOB_SPAWNING, false);
            handle = Fantasy.get(server).openTemporaryWorld(
                Identifier.of("craftics", PATH_PREFIX + instanceId.toString().toLowerCase()), config);
            HANDLES.put(instanceId, handle);
            CrafticsMod.LOGGER.info("Opened raid dimension for instance {}", instanceId);
        }
        return handle.asWorld();
    }

    public static ServerWorld getLoaded(UUID instanceId) {
        RuntimeWorldHandle handle = HANDLES.get(instanceId);
        return handle != null ? handle.asWorld() : null;
    }

    /** Tear a raid dimension down. Callers MUST have evacuated it first.
     *  This only REQUESTS deletion: Fantasy enqueues the removal and finishes
     *  it over the next few ticks (see {@link #CLOSING}), it does not happen
     *  synchronously before this method returns. */
    public static void close(UUID instanceId) {
        RuntimeWorldHandle handle = HANDLES.remove(instanceId);
        if (handle == null) return;
        ServerWorld w = handle.asWorld();
        if (w != null && !w.getPlayers().isEmpty()) {
            // Callers are supposed to have evacuated already, so reaching here means a
            // return-home path failed or was skipped. This used to only WARN and delete
            // anyway, which is the worst possible outcome for the players left behind: a
            // raid arena is a VoidChunkGenerator world, so whatever Fantasy does with them
            // as it tears the world down, they end up standing on nothing in a world that
            // is disappearing - an ordinary out-of-combat void death, dropping their whole
            // (own, non-run) inventory into a dimension that no longer exists. Put them on
            // real ground first; the lobby is the one floor guaranteed to still be there.
            CrafticsMod.LOGGER.warn(
                "Closing raid dimension {} with {} player(s) still inside - evacuating them to the lobby",
                instanceId, w.getPlayers().size());
            for (net.minecraft.server.network.ServerPlayerEntity stranded
                    : new ArrayList<>(w.getPlayers())) {
                try {
                    if (stranded.isSpectator()) {
                        stranded.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
                    }
                    com.crackedgames.craftics.world.HubTeleports.toLobby(stranded);
                } catch (Exception e) {
                    CrafticsMod.LOGGER.error("Failed to evacuate {} from raid dimension {}: {}",
                        stranded.getName().getString(), instanceId, e.getMessage());
                }
            }
        }
        CLOSING.add(instanceId);
        handle.delete();
        CrafticsMod.LOGGER.info(
            "Requested deletion of raid dimension for instance {} (Fantasy finishes it over the next few ticks)",
            instanceId);
    }

    public static void closeAll() {
        for (UUID id : new ArrayList<>(HANDLES.keySet())) close(id);
    }

    /** True when the given world IS a raid arena dim. */
    public static boolean isRaidWorld(World world) {
        Identifier id = world.getRegistryKey().getValue();
        return "craftics".equals(id.getNamespace()) && id.getPath().startsWith(PATH_PREFIX);
    }

    /**
     * Diagnostic sweep, not a disk cleaner: logs any raid world that is
     * currently loaded in {@code server.getWorlds()} but neither open in
     * {@link #HANDLES} nor draining after a recent {@link #close} (see
     * {@link #CLOSING}), then leaves it alone.
     *
     * <p>This deliberately does NOT delete anything. A raid dimension folder
     * abandoned by a hard crash is never registered back into
     * {@code server.getWorlds()} on the next boot: Fantasy worlds are
     * constructed and added to the server's world map entirely in memory (see
     * {@code RuntimeWorldManager.add}), and nothing in Fantasy or vanilla
     * rescans the level's dimension folders at startup and re-adds unreferenced
     * ones. So a true crash orphan is invisible to this method by construction;
     * looping over {@code server.getWorlds()} can only ever find a raid world
     * that is already loaded THIS session.
     *
     * <p>Under normal operation, a loaded raid world is in exactly one of two
     * expected states: open (tracked in {@link #HANDLES}) or mid-teardown
     * (its instance id is in {@link #CLOSING}, because {@code delete()} only
     * enqueues removal and Fantasy takes further ticks to actually drop it).
     * Both are routine, not an anomaly, so this method does not warn about them.
     * Finding a loaded raid world in neither state is the actual anomaly (e.g.
     * a stray reference kept alive by something outside this class), so the
     * safe move is to report it rather than guess at deleting a world this
     * class did not open and does not hold a handle for.
     *
     * <p>Actually reclaiming a hard-crash orphan's files would require walking
     * the level storage session's raid/ subdirectory on disk directly, outside
     * any Fantasy API and outside any live {@link ServerWorld}, which risks
     * deleting a folder a concurrent process still expects to exist. That is
     * out of scope here; this method only ever reports what it finds.
     */
    public static void deleteOrphans(MinecraftServer server) {
        List<ServerWorld> raidWorlds = new ArrayList<>();
        for (ServerWorld w : server.getWorlds()) {
            if (isRaidWorld(w)) raidWorlds.add(w);
        }
        reconcileClosing(raidWorlds);
        for (ServerWorld w : raidWorlds) {
            if (!isTracked(w)) {
                CrafticsMod.LOGGER.warn(
                    "Raid dimension {} is loaded but not tracked by RaidBossDimensions; "
                        + "leaving it alone (this class only deletes worlds it opened itself)",
                    w.getRegistryKey().getValue());
            }
        }
    }

    /** Drops ids from {@link #CLOSING} once their world is no longer among the
     *  currently loaded raid worlds, i.e. once Fantasy has actually finished
     *  removing it. There is no completion callback from Fantasy for this, so
     *  this reconciliation runs lazily whenever {@link #deleteOrphans} does. */
    private static void reconcileClosing(List<ServerWorld> raidWorlds) {
        if (CLOSING.isEmpty()) return;
        CLOSING.removeIf(id -> {
            for (ServerWorld w : raidWorlds) {
                if (id.equals(instanceIdOf(w))) return false;
            }
            return true;
        });
    }

    /** True when this loaded raid world is either open in {@link #HANDLES} or
     *  draining after a recent {@link #close} (its id is in {@link #CLOSING}).
     *  RuntimeWorldHandle exposes no equals(), so the HANDLES check compares by
     *  the underlying world reference instead. */
    private static boolean isTracked(ServerWorld w) {
        for (RuntimeWorldHandle handle : HANDLES.values()) {
            if (handle.asWorld() == w) return true;
        }
        return CLOSING.contains(instanceIdOf(w));
    }

    /** Parses the instance id encoded in a raid world's identifier path
     *  (craftics:raid/&lt;uuid&gt;), or null if this isn't a raid world or the
     *  path isn't a valid UUID. */
    private static UUID instanceIdOf(World world) {
        if (!isRaidWorld(world)) return null;
        String path = world.getRegistryKey().getValue().getPath();
        try {
            return UUID.fromString(path.substring(PATH_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
