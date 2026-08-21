package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.network.TeammatePingPayload;
import com.crackedgames.craftics.world.CrafticsSavedData;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of co-op pings: rate-limit, then fan out to the sender's party.
 *
 * <p>A ping is the cheapest packet in the game to send and the most annoying one to receive, so
 * the rate limit is the whole point of this class rather than an afterthought. Holding the key
 * down is a normal thing for a player to do while deciding what to pick, and a client that
 * repeats on key-repeat - or a modified one that does not bother waiting - would otherwise paint
 * the arena solid.
 *
 * <p>The limiter is split out as {@link #allow(UUID, long)} taking an explicit clock so it can be
 * tested without a server. That matters more than it looks: rate limiters are the kind of code
 * that is obviously correct and quietly off by one window.
 */
public final class PingRelay {
    private PingRelay() {}

    /** Minimum gap between two accepted pings from the same player. */
    static final long MIN_INTERVAL_MS = 400L;

    /**
     * How long a player's last-ping timestamp is worth remembering. Anything older can only ever
     * say "yes, allowed", which is the default anyway, so it is dead weight.
     */
    private static final long ENTRY_TTL_MS = 60_000L;

    /** Prune once the map is bigger than any plausible online party. Bounded, not clever. */
    private static final int PRUNE_THRESHOLD = 256;

    private static final Map<UUID, Long> LAST_PING = new HashMap<>();

    /**
     * Whether {@code player} may ping at {@code nowMs}, recording the ping if so.
     *
     * <p>Deliberately does the recording too. A check that does not record is a check somebody
     * eventually calls twice, and the second call says yes.
     */
    static synchronized boolean allow(UUID player, long nowMs) {
        if (player == null) return false;
        Long last = LAST_PING.get(player);
        if (last != null && nowMs - last < MIN_INTERVAL_MS) return false;
        LAST_PING.put(player, nowMs);
        if (LAST_PING.size() > PRUNE_THRESHOLD) {
            LAST_PING.entrySet().removeIf(e -> nowMs - e.getValue() > ENTRY_TTL_MS);
        }
        return true;
    }

    /** Drop all recorded timestamps. Test hook, and a clean slate on server shutdown. */
    static synchronized void reset() {
        LAST_PING.clear();
    }

    /**
     * Handle one ping from {@code sender}: rate-limit it, then relay to every online member of
     * their party, the sender included.
     *
     * <p>A solo player has no party, and still gets their own ping back - pinging alone is not
     * useful, but silently doing nothing when a bound key is pressed reads as a broken keybind.
     *
     * <p>There is no "are you actually in a fight" check here. The client only opens the wheel in
     * combat, and for a client that ignores that, the worst case is a marker their own party sees
     * for six seconds - already bounded by the rate limit, and not worth a second guess at what
     * counts as being in a fight.
     */
    public static void handle(ServerPlayerEntity sender, int gridX, int gridZ, int type) {
        if (sender == null) return;
        if (!allow(sender.getUuid(), System.currentTimeMillis())) return;

        TeammatePingPayload out = new TeammatePingPayload(
            sender.getUuid(), sender.getName().getString(), gridX, gridZ, type);

        ServerWorld world = (ServerWorld) sender.getEntityWorld();
        CrafticsSavedData data = CrafticsSavedData.get(world);

        boolean sentToSelf = false;
        for (UUID memberUuid : data.getPartyMemberUuids(sender.getUuid())) {
            ServerPlayerEntity member = world.getServer().getPlayerManager().getPlayer(memberUuid);
            if (member == null) continue;
            ServerPlayNetworking.send(member, out);
            if (memberUuid.equals(sender.getUuid())) sentToSelf = true;
        }
        if (!sentToSelf) ServerPlayNetworking.send(sender, out);
    }
}
