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
        // Clamp against the TARGET world (not the world the player is leaving).
        int landY = CrafticsMod.hubLandingY(target, pos.getX(), pos.getZ(), pos.getY());
        int y = landY != Integer.MIN_VALUE ? landY : pos.getY();
        teleportTo(p, target, pos.getX() + 0.5, y, pos.getZ() + 0.5);
        // If we crossed out of a DIFFERENT island dim, unload it when now empty so
        // idle islands cost zero tick time. (No-op when previousWorld isn't an island
        // or is the same island we just entered.)
        if (previousWorld != target && IslandDimensions.isIslandWorld(previousWorld)) {
            java.util.UUID leftOwner = IslandDimensions.ownerOf(previousWorld);
            if (leftOwner != null) IslandDimensions.unloadIfEmpty(server, leftOwner);
        }
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
        int landY = CrafticsMod.hubLandingY(overworld, fx, fz, fy);
        int y = landY != Integer.MIN_VALUE ? landY : fy;
        teleportTo(p, overworld, fx + 0.5, y, fz + 0.5);
        if (spawn != null) p.setYaw(data.lobbySpawnYaw);
        if (previousWorld != overworld && IslandDimensions.isIslandWorld(previousWorld)) {
            java.util.UUID leftOwner = IslandDimensions.ownerOf(previousWorld);
            if (leftOwner != null) IslandDimensions.unloadIfEmpty(server, leftOwner);
        }
    }

    /** Cross-dim teleport helper mirroring the repo's stonecutter split
     *  (CrafticsMod.teleportToHub): same-dim uses requestTeleport, cross-dim uses the
     *  version-split {@code player.teleport(world, ...)} overload. Keeping the split
     *  here means the two call sites above never repeat the preprocessor dance. */
    private static void teleportTo(ServerPlayerEntity p, ServerWorld world,
                                   double x, double y, double z) {
        if (p.getServerWorld() != world) {
            //? if <=1.21.1 {
            p.teleport(world, x, y, z,
                java.util.Collections.emptySet(), p.getYaw(), p.getPitch());
            //?} else {
            /*p.teleport(world, x, y, z,
                java.util.Collections.emptySet(), p.getYaw(), p.getPitch(), true);
            *///?}
        } else {
            p.requestTeleport(x, y, z);
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
