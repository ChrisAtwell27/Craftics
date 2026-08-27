package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.TurnOrderProvider;
import com.crackedgames.craftics.combat.CombatEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Providers that decide what order the creatures act in.
 *
 * <p>Asked in registration order; the first to answer with a list wins and the rest are not
 * asked. Two mods with opinions about initiative cannot both have one.
 *
 * @see TurnOrderProvider
 * @since 0.4.5
 */
public final class TurnOrderRegistry {

    private TurnOrderRegistry() {}

    private static final List<TurnOrderProvider> PROVIDERS = new ArrayList<>();

    /** Register a provider. Registering the same instance twice is a no-op. */
    public static void register(TurnOrderProvider provider) {
        if (provider == null || PROVIDERS.contains(provider)) return;
        PROVIDERS.add(provider);
    }

    /** Whether anything is listening, so the round can skip the ordering pass entirely. */
    public static boolean isEmpty() {
        return PROVIDERS.isEmpty();
    }

    /**
     * Ask for an order, and return one that is safe to act on.
     *
     * <p>The answer is sanitised rather than trusted, because the list it produces is what the
     * round iterates: a provider that dropped a creature would have it silently skip the round,
     * and one that returned a creature twice would have it act twice. So the result is built
     * from {@code actors} - every actor exactly once, the named ones first in the order given,
     * everything else after in the order it already had.
     *
     * @return the order to act in, or null to leave Craftics' own order alone
     */
    public static List<CombatEntity> order(ServerPlayerEntity player, List<CombatEntity> actors,
                                           int round) {
        if (PROVIDERS.isEmpty() || actors == null || actors.isEmpty()) return null;
        for (TurnOrderProvider provider : PROVIDERS) {
            List<CombatEntity> answer;
            try {
                answer = provider.orderTurns(player, List.copyOf(actors), round);
            } catch (Throwable t) {
                CrafticsMod.LOGGER.error("Turn order provider threw; the round keeps its own order", t);
                continue;
            }
            if (answer == null || answer.isEmpty()) continue;
            return reconcile(actors, answer);
        }
        return null;
    }

    /** Every actor exactly once: the named ones in the order given, then the rest as they were. */
    private static List<CombatEntity> reconcile(List<CombatEntity> actors, List<CombatEntity> answer) {
        Map<CombatEntity, Boolean> acting = new IdentityHashMap<>();
        for (CombatEntity e : actors) acting.put(e, Boolean.FALSE);

        List<CombatEntity> ordered = new ArrayList<>(actors.size());
        for (CombatEntity e : answer) {
            if (e == null) continue;
            Boolean placed = acting.get(e);
            if (placed == null || placed) continue;   // not in this round, or already placed
            acting.put(e, Boolean.TRUE);
            ordered.add(e);
        }
        for (CombatEntity e : actors) {
            if (!acting.get(e)) ordered.add(e);
        }
        return ordered;
    }

    /** Forget every provider. For tests. */
    public static void clear() {
        PROVIDERS.clear();
    }
}
