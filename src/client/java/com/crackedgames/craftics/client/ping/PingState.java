package com.crackedgames.craftics.client.ping;

import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.PingType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The pings currently standing on the battlefield, client side.
 *
 * <p>Keyed by sender, one live ping each. That is the whole concurrency policy and it is doing
 * real work: a list would let one player stack six pillars on six tiles in the time it takes to
 * tap a key six times, and the rate limit alone does not stop that - it only slows it down. One
 * per player means the arena can never hold more markers than there are people in it, and
 * pinging again simply moves your own marker, which is what a player means by it anyway.
 *
 * <p>Written from the network thread and read from the render thread, hence the concurrent map.
 */
public final class PingState {
    private PingState() {}

    /** How long a ping stands before it disappears. */
    public static final long LIFETIME_MS = 2_000L;

    /** One standing ping. {@code startMs} is client wall-clock at the moment it arrived. */
    public record Ping(UUID sender, String senderName, GridPos pos, PingType type, long startMs) {

        /** 0 at the moment it landed, 1 when it is due to vanish. */
        public float progress(long nowMs) {
            return Math.min(1f, Math.max(0f, (nowMs - startMs) / (float) LIFETIME_MS));
        }

        public boolean expired(long nowMs) {
            return nowMs - startMs >= LIFETIME_MS;
        }
    }

    private static final Map<UUID, Ping> ACTIVE = new ConcurrentHashMap<>();

    /** Record an incoming ping, replacing whatever that player had standing. */
    public static void put(UUID sender, String senderName, int gridX, int gridZ, PingType type) {
        if (sender == null || type == null) return;
        ACTIVE.put(sender, new Ping(sender, senderName == null ? "" : senderName,
            new GridPos(gridX, gridZ), type, System.currentTimeMillis()));
    }

    /**
     * Live pings, expired ones dropped on the way out.
     *
     * <p>Pruning on read rather than on a tick keeps this correct while the client is paused or
     * the world is loading, when no tick runs but the render pass still does.
     */
    public static List<Ping> active() {
        long now = System.currentTimeMillis();
        List<Ping> out = new ArrayList<>(ACTIVE.size());
        for (Ping p : ACTIVE.values()) {
            if (p.expired(now)) ACTIVE.remove(p.sender());
            else out.add(p);
        }
        return out;
    }

    /** True when nothing is standing - lets the renderer bail before touching the world. */
    public static boolean isEmpty() {
        return ACTIVE.isEmpty();
    }

    /** Forget everything. Called on combat exit and on disconnect. */
    public static void clear() {
        ACTIVE.clear();
    }
}
