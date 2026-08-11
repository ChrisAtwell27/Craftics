package com.crackedgames.craftics.world;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Single home for "send this player somewhere safe" teleports. Every hub/lobby
 *  return must go through here so the landing-Y clamp is never skipped again
 *  (a raw stored Y under the island surface drops the player into the void).
 *
 *  <p>De-laned (Task 6): the hub lives at fixed coordinates inside the OWNER'S
 *  island dimension (craftics:island/&lt;uuid&gt;), and the lobby is the single
 *  overworld spawn. Both teleports are therefore cross-dimensional and use the
 *  repo's established stonecutter-split {@code player.teleport(world, ...)}
 *  pattern (see CrafticsMod.teleportToHub / CombatManager counterpart). */
public final class HubTeleports {
    private HubTeleports() {}

    /** Send a player to their island hub (owner's hub when in a party). Resolves
     *  the owner's island dim (re-opening it if Fantasy unloaded it), clamps the
     *  landing Y against THAT world, teleports cross-dim, then unloads the island
     *  the player just left if it is now empty. */
    public static void toHub(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        // Any self-driven exit from a visited island drops visitor tracking, whichever
        // way the player leaves (home here, or the lobby path below).
        VisitManager.clearVisit(p.getUuid());
        ServerWorld previousWorld = (ServerWorld) p.getEntityWorld();
        CrafticsSavedData data = CrafticsSavedData.get(previousWorld);
        java.util.UUID owner = data.getEffectiveWorldOwner(p.getUuid());
        // Guard: no personal world for the effective owner means there is no hub to
        // resolve. Without this, getOrCreate would open a brand-new empty island dim
        // just to drop the player at its unbuilt origin (void). Send them to the
        // lobby instead, same as if they'd never called /home.
        if (!data.hasPersonalWorld(owner)) {
            toLobby(p);
            return;
        }
        // Re-open over getLoaded: a stale HANDLES entry (Fantasy unloaded the world
        // externally) would otherwise hand back a null asWorld() as the target.
        ServerWorld island = IslandDimensions.getOrCreate(server, owner);
        // Old-save migration: copy an overworld-lane base into this dim (or build a
        // fresh hub) before the personalHubBuilt guard below - otherwise an old save
        // (personalHubBuilt=true) skips the rebuild and voids the player.
        IslandMigration.ensureMigrated(server, owner);
        // Self-heal: /craftics island reset wipes the built hub (personalHubBuilt=false)
        // but keeps worldSlot as the has-island marker, so neither /new nor the JOIN
        // repair path ever rebuilds it. Without this, the player lands at the stored
        // (now stale/unbuilt) hub position - a void drop. Mirrors the same
        // build+flag+spawn-pos bookkeeping the JOIN and /craftics world create call
        // sites perform right after HubRoomBuilder.build.
        CrafticsSavedData.PlayerData ownerPd = data.getPlayerData(owner);
        if (!ownerPd.personalHubBuilt) {
            BlockPos hubCenter = data.getHubOrigin(owner);
            BlockPos spawnPos = HubRoomBuilder.build(island, hubCenter);
            ownerPd.personalHubBuilt = true;
            ownerPd.personalHubVersion = HubRoomBuilder.HUB_VERSION;
            ownerPd.hubSpawnX = spawnPos.getX();
            ownerPd.hubSpawnY = spawnPos.getY();
            ownerPd.hubSpawnZ = spawnPos.getZ();
            data.markDirty();
        }
        BlockPos hub = data.getHubTeleportPos(p.getUuid());
        // Last resort. personalHubBuilt only tracks whether the game ever BUILT a hub, not
        // whether one is still standing, so a player who mined theirs out (or moved their
        // base and cleared the original site) keeps the flag and skips the self-heal above
        // while having nowhere to land. If there is no ground anywhere near the hub
        // coordinate, lay ONE block under the spawn point and put them on it.
        //
        // Deliberately a single block and not a rebuilt hub room: the island is theirs, and
        // an empty site is a decision as often as it is an accident - somebody clearing space
        // to build should not come home to the starter room stamped back over their plot. All
        // this owes them is a foothold on their own island; what they do from there is theirs.
        if (CrafticsMod.findLandingSpot(island, hub.getX(), hub.getZ(), hub.getY()) == null) {
            CrafticsMod.LOGGER.warn("Island of {} has no ground within {} blocks of its hub; "
                + "placing a single rescue block under the spawn at {}.",
                owner, CrafticsMod.LANDING_SEARCH_RADIUS, hub);
            island.setBlockState(hub.down(),
                net.minecraft.block.Blocks.SMOOTH_STONE.getDefaultState());
            p.sendMessage(net.minecraft.text.Text.literal(
                "§eThere was nothing to stand on, so a block was placed at your spawn point."), false);
        }
        crossDimMove(server, p, previousWorld, island, hub);
    }

    /** Send a visitor into the OWNER's island hub - unlike {@link #toHub}, the target
     *  dimension and hub spawn are resolved from the explicit {@code owner} UUID, never
     *  from the visitor's own effective owner (party leader/self). Used exclusively by
     *  {@link VisitManager}, which has already validated the visit (party fast-path or
     *  accepted invite) before calling this. Shares the same self-heal + clamp +
     *  cross-dim move + previous-island unload tail as {@link #toHub}. */
    public static void visitHub(ServerPlayerEntity visitor, java.util.UUID owner) {
        MinecraftServer server = visitor.getServer();
        if (server == null) return;
        ServerWorld previousWorld = (ServerWorld) visitor.getEntityWorld();
        CrafticsSavedData data = CrafticsSavedData.get(previousWorld);
        // Re-open over getLoaded: a stale HANDLES entry (Fantasy unloaded the world
        // externally) would otherwise hand back a null asWorld() as the target.
        ServerWorld island = IslandDimensions.getOrCreate(server, owner);
        // Old-save migration: same as toHub - migrate the owner's overworld-lane base
        // into this dim before the guard, so a guest visiting an unmigrated owner lands
        // in the copied base rather than a void.
        IslandMigration.ensureMigrated(server, owner);
        // Self-heal: mirrors toHub's hub-rebuild guard so a visit into an island whose
        // hub was wiped by /craftics island reset doesn't drop the visitor into the void.
        CrafticsSavedData.PlayerData ownerPd = data.getPlayerData(owner);
        if (!ownerPd.personalHubBuilt) {
            BlockPos hubCenter = data.getHubOrigin(owner);
            BlockPos spawnPos = HubRoomBuilder.build(island, hubCenter);
            ownerPd.personalHubBuilt = true;
            ownerPd.personalHubVersion = HubRoomBuilder.HUB_VERSION;
            ownerPd.hubSpawnX = spawnPos.getX();
            ownerPd.hubSpawnY = spawnPos.getY();
            ownerPd.hubSpawnZ = spawnPos.getZ();
            data.markDirty();
        }
        // getHubSpawnPos(owner) directly - NOT getHubTeleportPos(visitor), which would
        // resolve the visitor's own effective owner instead of the explicit target.
        BlockPos hub = data.getHubSpawnPos(owner);
        crossDimMove(server, visitor, previousWorld, island, hub);
    }

    /** Admin variant of {@link #toHub}: teleports {@code p} into an already-resolved
     *  dimension/position pair instead of resolving the player's own effective owner
     *  and hub spawn. Used by {@code /craftics island tp} to drop an operator into a
     *  target player's island regardless of the operator's own party/ownership state.
     *  Shares the same clamp + cross-dim move + previous-island unload tail as
     *  {@link #toHub} so admin teleports never skip the void-landing safety net. */
    public static void adminTeleport(ServerPlayerEntity p, ServerWorld dim, BlockPos pos) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        ServerWorld previousWorld = (ServerWorld) p.getEntityWorld();
        crossDimMove(server, p, previousWorld, dim, pos);
    }

    /** Shared tail for {@link #toHub} and {@link #adminTeleport}: dismount, clamp the
     *  landing Y against the TARGET world, cross-dim teleport, then unload the island
     *  just left behind (if it was a different, now-empty island dim). */
    private static void crossDimMove(MinecraftServer server, ServerPlayerEntity p,
                                      ServerWorld previousWorld, ServerWorld target, BlockPos pos) {
        dismountForTeleport(p);
        // Clamp against the TARGET world (not the world the player is leaving). Searching
        // outward rather than probing the one stored column is what keeps a rebuilt island
        // reachable: the stored hub coordinate stops being ground the moment somebody digs
        // it out, and the old single-column check answered that by handing back the raw
        // stored Y - a teleport into open air above their own island.
        BlockPos landing = CrafticsMod.findLandingSpot(target, pos.getX(), pos.getZ(), pos.getY());
        BlockPos dest = landing != null ? landing : pos;
        teleportTo(p, target, dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5);
        // Same reason as the lobby path: arriving at a hub still flagged as a downed
        // spectator is a state nobody can get out of on their own.
        clearSpectator(p);
        // If we crossed out of a DIFFERENT island dim, unload it when now empty so idle
        // islands cost zero tick time - but a tick later, never in the same tick as the
        // teleport. See unloadLeftIslandNextTick.
        unloadLeftIslandNextTick(server, previousWorld, target);
    }

    /** Send a player to the central lobby spawn in the overworld, honoring a custom
     *  stored lobby spawn set via {@code /craftics lobby setspawn} when one is present.
     *  Cross-dim from any island; unloads the island left behind when it empties. */
    public static void toLobby(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        VisitManager.clearVisit(p.getUuid());
        ServerWorld previousWorld = (ServerWorld) p.getEntityWorld();
        ServerWorld overworld = server.getOverworld();
        CrafticsSavedData data = CrafticsSavedData.get(overworld);
        BlockPos spawn = data.getLobbySpawn();
        int fx = spawn != null ? spawn.getX() : 0;
        int fz = spawn != null ? spawn.getZ() : 0;
        int fy = spawn != null ? spawn.getY() : 65;
        dismountForTeleport(p);
        BlockPos lobbyLanding = CrafticsMod.findLandingSpot(overworld, fx, fz, fy);
        BlockPos lobbyDest = lobbyLanding != null ? lobbyLanding : new BlockPos(fx, fy, fz);
        teleportTo(p, overworld, lobbyDest.getX() + 0.5, lobbyDest.getY(), lobbyDest.getZ() + 0.5);
        if (spawn != null) p.setYaw(data.lobbySpawnYaw);
        clearSpectator(p);
        unloadLeftIslandNextTick(server, previousWorld, overworld);
    }

    /**
     * Unload the island a player just left, ONE TICK LATER.
     *
     * <p>The delay is the whole point. Tearing the world down in the same tick as the
     * teleport pulls it out from under a dimension change that is still in flight: the
     * server ends up believing the player is at their destination - they can hear the people
     * standing around them - while the transition that would have sent them chunks never
     * finishes. What the player sees is a void with no terrain, invisible to everyone else,
     * and no command fixes it because nothing is wrong with their game mode or permissions.
     *
     * <p>The disconnect path in {@code CrafticsMod} already learned this and defers for the
     * same reason; the two teleport paths did not, which is the difference between logging
     * out of an island cleanly and walking out of one into a ghost lobby.
     */
    private static void unloadLeftIslandNextTick(MinecraftServer server,
                                                 ServerWorld previousWorld, ServerWorld target) {
        if (previousWorld == target || !IslandDimensions.isIslandWorld(previousWorld)) return;
        java.util.UUID leftOwner = IslandDimensions.ownerOf(previousWorld);
        if (leftOwner == null) return;
        server.execute(() -> IslandDimensions.unloadIfEmpty(server, leftOwner));
    }

    /**
     * Take a player out of spectator on arrival.
     *
     * <p>A downed party member is put into SPECTATOR while the rest of their party fights on
     * (see {@code CombatManager.handlePlayerDowned}). Most exits from a run restore the mode
     * on the way out, but not all of them do, and the ones that miss it leave a player who
     * reaches the lobby still a spectator: floating in the void, able to hear the people
     * around them, invisible to every one of them. That reads as a broken lobby rather than
     * as a game mode, and it is unrecoverable without an operator.
     *
     * <p>Only SPECTATOR is corrected. An operator in creative teleporting to the lobby stays
     * in creative - this fixes the state the game put someone in, it does not police modes.
     */
    private static void clearSpectator(ServerPlayerEntity p) {
        if (p.interactionManager.getGameMode() == net.minecraft.world.GameMode.SPECTATOR) {
            p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        }
    }

    /** Cross-dim teleport helper mirroring the repo's stonecutter split
     *  (CrafticsMod.teleportToHub): same-dim uses requestTeleport, cross-dim uses the
     *  version-split {@code player.teleport(world, ...)} overload. Keeping the split
     *  here means call sites across the mod never repeat the preprocessor dance.
     *  Public: this is also the single teleport path for the daily raid boss instance
     *  runtime ({@code RaidBossInstance}), which crosses into and back out of its own
     *  private dimension. Keeps the player's CURRENT facing; see the yaw/pitch overload
     *  below when an exact facing must be restored instead. */
    public static void teleportTo(ServerPlayerEntity p, ServerWorld world,
                                  double x, double y, double z) {
        teleportTo(p, world, x, y, z, p.getYaw(), p.getPitch());
    }

    /** As {@link #teleportTo(ServerPlayerEntity, ServerWorld, double, double, double)},
     *  but with an explicit facing instead of the player's current one. Needed when
     *  restoring a player to a remembered origin - e.g. RaidBossInstance putting a
     *  raider back exactly where and which way they were facing before the raid
     *  started, which their CURRENT (in-arena) yaw/pitch would not capture. */
    public static void teleportTo(ServerPlayerEntity p, ServerWorld world,
                                  double x, double y, double z, float yaw, float pitch) {
        if (p.getServerWorld() != world) {
            //? if <=1.21.1 {
            p.teleport(world, x, y, z,
                java.util.Collections.emptySet(), yaw, pitch);
            //?} else {
            /*p.teleport(world, x, y, z,
                java.util.Collections.emptySet(), yaw, pitch, true);
            *///?}
        } else {
            p.requestTeleport(x, y, z);
            p.setYaw(yaw);
            p.setPitch(pitch);
        }
    }

    /** While a passenger of a combat mount/boat, requestTeleport silently keeps
     *  the player bound to the vehicle and snaps them back. A frozen combat
     *  mount also keeps the CLIENT rider link after stopRiding, so discard it. */
    private static void dismountForTeleport(ServerPlayerEntity p) {
        if (!p.hasVehicle()) return;
        net.minecraft.entity.Entity vehicle = p.getVehicle();
        p.stopRiding();
        if (vehicle != null && vehicle.getCommandTags().contains("craftics_arena_mount")) {
            vehicle.discard();
        }
    }
}
