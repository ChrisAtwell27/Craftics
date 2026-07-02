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
            RuntimeWorldConfig config = new RuntimeWorldConfig()
                .setDimensionType(net.minecraft.world.dimension.DimensionTypes.OVERWORLD)
                .setGenerator(new VoidChunkGenerator(biomeRegistry))
                .setDifficulty(net.minecraft.world.Difficulty.NORMAL)
                .setShouldTickTime(true);
            handle = Fantasy.get(server).getOrOpenPersistentWorld(
                Identifier.of("craftics", "island/" + owner.toString().toLowerCase()), config);
            HANDLES.put(owner, handle);
        }
        return handle.asWorld();
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
        if (w != null && !w.getPlayers().isEmpty()) return;
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
