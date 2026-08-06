package com.crackedgames.craftics.raid;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where each raid participant stood when they joined, so every exit path (win,
 * wipe, AFK removal, disconnect, server stop) can put them back exactly there.
 *
 * <p>Remembering is first-write-wins: a player already holding an origin is
 * mid-raid, and overwriting it with their arena position would strand them in a
 * dimension that is about to be deleted.
 *
 * <p>Deliberately not persisted. An in-flight raid does not survive a restart,
 * and the server-stop teardown returns everyone before the map is dropped.
 */
public final class RaidBossOrigins {
    private RaidBossOrigins() {}

    public record Origin(String dimensionId, double x, double y, double z, float yaw, float pitch) {}

    private static final Map<UUID, Origin> ORIGINS = new ConcurrentHashMap<>();

    public static void remember(UUID player, Origin origin) {
        if (player == null || origin == null) return;
        ORIGINS.putIfAbsent(player, origin);
    }

    public static Origin peek(UUID player) {
        return player == null ? null : ORIGINS.get(player);
    }

    public static Origin take(UUID player) {
        return player == null ? null : ORIGINS.remove(player);
    }

    public static void forget(UUID player) {
        if (player != null) ORIGINS.remove(player);
    }

    public static int size() { return ORIGINS.size(); }

    public static void clear() { ORIGINS.clear(); }
}
