package com.crackedgames.craftics.combat;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.List;

/**
 * Builds the cast that walks across the loading screen: one affinity per party member, in the
 * order they should file on.
 *
 * <p>Lives server-side because affinity points do. A client knows its OWN progression, but
 * nothing syncs another player's affinities to it, so the row cannot be assembled on the client
 * without inventing a packet whose only job is to feed an animation. The loading screen is
 * already a server-driven event ({@code LoadingScreenPayload}), so the cast rides along with it
 * and the client renders exactly what it is handed.
 *
 * @see com.crackedgames.craftics.client.LoadingWalkers
 */
public final class LoadingCast {

    private LoadingCast() {}

    /** What a player with no affinity points yet walks as - see {@link #strongestAffinity}. */
    private static final PlayerProgression.Affinity DEFAULT_AFFINITY =
        PlayerProgression.Affinity.PHYSICAL;

    /**
     * Encode {@code members} as a comma-separated affinity list for the wire.
     *
     * <p>Order is the caller's: whatever order the party is passed in is the order they walk on,
     * so the leader leads if the caller hands them over first. Returns an empty string for an
     * empty or null party, which the client reads as "no walkers".
     */
    public static String encode(List<ServerPlayerEntity> members) {
        if (members == null || members.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (ServerPlayerEntity member : members) {
            if (member == null) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(strongestAffinity(member).name());
        }
        return sb.toString();
    }

    /**
     * The affinity a player has invested most heavily in.
     *
     * <p>Ties break on {@link PlayerProgression.Affinity} declaration order, which is stable, so
     * the same player walks the same way twice in a row rather than flickering between two
     * equally-invested affinities on consecutive loads.
     *
     * <p>A player who has never spent a point has no strongest anything. Rather than let the
     * tie-break silently hand them whichever affinity happens to be declared first, that case is
     * named here and answered with {@link #DEFAULT_AFFINITY}.
     */
    public static PlayerProgression.Affinity strongestAffinity(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return DEFAULT_AFFINITY;
        PlayerProgression.PlayerStats stats = PlayerProgression.get(world).getStats(player);
        if (stats == null) return DEFAULT_AFFINITY;

        PlayerProgression.Affinity best = null;
        int bestPoints = 0;
        for (PlayerProgression.Affinity a : PlayerProgression.Affinity.values()) {
            int points = stats.getAffinityPoints(a);
            if (points > bestPoints) {
                bestPoints = points;
                best = a;
            }
        }
        return best != null ? best : DEFAULT_AFFINITY;
    }
}
