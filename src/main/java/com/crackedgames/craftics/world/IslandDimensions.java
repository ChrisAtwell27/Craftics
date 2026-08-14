package com.crackedgames.craftics.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;
import xyz.nucleoid.fantasy.util.VoidChunkGenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One persistent runtime dimension per island owner: craftics:island/<uuid>.
 *  Created lazily, unloaded when empty so idle islands cost zero tick time.
 *  All island content (hub, arenas, trader, scenes) lives at FIXED coordinates
 *  inside the owner's dimension - the overworld keeps only the central lobby. */
public final class IslandDimensions {
    private IslandDimensions() {}

    private static final Map<UUID, RuntimeWorldHandle> HANDLES = new HashMap<>();

    public static ServerWorld getOrCreate(MinecraftServer server, UUID owner) {
        RuntimeWorldHandle handle = HANDLES.get(owner);
        if (handle == null) {
            //? if <=1.21.1 {
            var biomeRegistry = server.getRegistryManager().get(net.minecraft.registry.RegistryKeys.BIOME);
            //?} else {
            /*var biomeRegistry = server.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.BIOME);
            *///?}
            // The terrain is still generated empty (all island content is pasted at
            // fixed coords), but the BIOME is Plains rather than the_void. The void
            // biome has no spawn entries at all, so islands were permanently barren:
            // no passive animals, nothing to tame or farm. Plains gives the island
            // normal overworld spawning, grass/sky colour and ambience.
            RuntimeWorldConfig config = new RuntimeWorldConfig()
                .setDimensionType(net.minecraft.world.dimension.DimensionTypes.OVERWORLD)
                .setGenerator(new VoidChunkGenerator(
                    biomeRegistry, net.minecraft.world.biome.BiomeKeys.PLAINS))
                .setDifficulty(net.minecraft.world.Difficulty.NORMAL)
                .setShouldTickTime(true)
                // The lobby overworld turns mob spawning OFF (it's a void hub and
                // nothing should wander it), and Fantasy mirrors the overworld's
                // game rules into runtime worlds by default. Islands must opt back
                // IN explicitly or the Plains biome above would still spawn nothing.
                .setGameRule(net.minecraft.world.GameRules.DO_MOB_SPAWNING, true)
                // Set here as well as on the lobby: Fantasy copies the overworld's rules when
                // it opens a runtime world, but a persistent island keeps whatever copy it was
                // opened with, so an island created before the rule existed would never see it.
                .setGameRule(net.minecraft.world.GameRules.KEEP_INVENTORY, true);
            handle = Fantasy.get(server).getOrOpenPersistentWorld(dimensionKeyOf(owner), config);
            HANDLES.put(owner, handle);
        }
        return handle.asWorld();
    }

    /** The dimension identifier an owner's island lives under. Single source of truth for the
     *  naming scheme, so admin tooling reports exactly what Fantasy opens. */
    public static Identifier dimensionKeyOf(UUID owner) {
        return Identifier.of("craftics", "island/" + owner.toString().toLowerCase());
    }

    /** {@link #dimensionKeyOf} as a plain string, for records and command output. */
    public static String dimensionIdOf(UUID owner) {
        return dimensionKeyOf(owner).toString();
    }

    public static ServerWorld getLoaded(MinecraftServer server, UUID owner) {
        RuntimeWorldHandle handle = HANDLES.get(owner);
        return handle != null ? handle.asWorld() : null;
    }

    /** Unload the island dim when nobody (member or visitor) is inside. */
    public static boolean unloadIfEmpty(MinecraftServer server, UUID owner) {
        RuntimeWorldHandle handle = HANDLES.get(owner);
        if (handle == null) return false;
        ServerWorld w = handle.asWorld();
        if (w != null && !w.getPlayers().isEmpty()) return false;
        // Logged next to the teleport lines on purpose: an island unloading in the same breath
        // as somebody leaving it is the shape of half the ghost-lobby reports, and the two log
        // lines sitting adjacent is what makes that visible instead of theoretical.
        com.crackedgames.craftics.CrafticsMod.LOGGER.info("[dimension] unloading island {} (empty)", dimensionIdOf(owner));
        handle.unload();
        HANDLES.remove(owner);
        return true;
    }

    /** Permanently wipe an owner's island dimension: deletes the runtime world's
     *  region files from disk (Fantasy's {@code RuntimeWorldHandle.delete()}), not
     *  just an in-memory unload. Callers must evacuate any occupants BEFORE calling
     *  this - deleting out from under a player standing inside is undefined. Opens
     *  the handle first if it isn't already loaded, since {@code delete()} is an
     *  instance method on the handle. */
    public static void delete(MinecraftServer server, UUID owner) {
        RuntimeWorldHandle handle = HANDLES.get(owner);
        if (handle == null) {
            getOrCreate(server, owner);
            handle = HANDLES.get(owner);
        }
        if (handle == null) return;
        ServerWorld w = handle.asWorld();
        if (w != null && !w.getPlayers().isEmpty()) {
            com.crackedgames.craftics.CrafticsMod.LOGGER.warn("[dimension] refusing to delete island {} - {} player(s) "
                + "still inside", dimensionIdOf(owner), w.getPlayers().size());
            return;
        }
        com.crackedgames.craftics.CrafticsMod.LOGGER.info("[dimension] DELETING island {} from disk", dimensionIdOf(owner));
        handle.delete();
        HANDLES.remove(owner);
    }

    public static Map<UUID, ServerWorld> loadedIslands() {
        Map<UUID, ServerWorld> out = new HashMap<>();
        HANDLES.forEach((u, h) -> { ServerWorld w = h.asWorld(); if (w != null) out.put(u, w); });
        return out;
    }

    /** True when the given world IS someone's island dim (id under craftics:island/). */
    public static boolean isIslandWorld(World world) {
        Identifier id = world.getRegistryKey().getValue();
        return "craftics".equals(id.getNamespace()) && id.getPath().startsWith("island/");
    }

    /** Owner UUID for an island world, or null when not an island world. */
    public static UUID ownerOf(World world) {
        if (!isIslandWorld(world)) return null;
        try {
            return UUID.fromString(world.getRegistryKey().getValue().getPath().substring("island/".length()));
        } catch (IllegalArgumentException e) { return null; }
    }

    /** Clears in-memory handles on server stop. Fantasy itself unloads/saves its
     *  runtime worlds on SERVER_STOPPING; this only drops our stale UUID->handle
     *  map so it doesn't leak handles into the next singleplayer session. */
    public static void clear() {
        HANDLES.clear();
    }
}
