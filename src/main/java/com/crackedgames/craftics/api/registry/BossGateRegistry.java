package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.BossGate;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Boss gates, keyed by the id of the biome whose boss level they guard.
 *
 * <p>See {@link BossGate} for what a gate is and what Craftics does when one refuses a party.
 *
 * @since 0.5.0
 */
public final class BossGateRegistry {

    private BossGateRegistry() {}

    private static final Map<String, BossGate> GATES = new HashMap<>();

    /** Register a gate for a biome. Re-registering a biome replaces its gate. */
    public static void register(String biomeId, BossGate gate) {
        if (biomeId == null || biomeId.isBlank() || gate == null) return;
        GATES.put(biomeId, gate);
    }

    /** True when nothing is registered, so the level transition can skip this entirely. */
    public static boolean isEmpty() {
        return GATES.isEmpty();
    }

    /** Whether this biome's boss level has a gate. */
    public static boolean has(String biomeId) {
        return biomeId != null && GATES.containsKey(biomeId);
    }

    /**
     * Ask this biome's gate about a party.
     *
     * <p>Everything that is not a clear refusal lets the party through: no gate, nobody to ask
     * about, a gate that returns nothing, and a gate that throws. A broken gate must cost its
     * own check, not the run, and a party held at a dialogue with nothing to say would be
     * exactly the soft lock a gate exists to prevent.
     *
     * @return the lines to show, blank ones dropped. Empty when the party may enter
     */
    public static List<String> check(String biomeId, List<ServerPlayerEntity> party) {
        if (biomeId == null || party == null || party.isEmpty()) return List.of();
        BossGate gate = GATES.get(biomeId);
        if (gate == null) return List.of();
        List<String> lines;
        try {
            // A copy the gate cannot edit: the caller goes on to use this list as the party.
            lines = gate.check(Collections.unmodifiableList(new ArrayList<>(party)));
        } catch (Throwable t) {
            CrafticsMod.LOGGER.error("Boss gate for '{}' threw; the party is let through", biomeId, t);
            return List.of();
        }
        if (lines == null || lines.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            if (line != null && !line.isBlank()) out.add(line);
        }
        return List.copyOf(out);
    }

    /** Clear every registration. Test hook. */
    public static void clear() {
        GATES.clear();
    }
}
