package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.FieldAllyProvider;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Providers that contribute allies to a fight from outside Craftics' hub-party model.
 *
 * <p>See {@link FieldAllyProvider} for why this exists and why the allies it supplies are
 * temporary.
 *
 * <p>Every registered provider is asked, and the results are concatenated - two mods can
 * both field allies without either having to know about the other. Keys exist so a
 * provider can be replaced rather than duplicated.
 *
 * @since 0.3.9
 */
public final class FieldAllyProviderRegistry {

    private FieldAllyProviderRegistry() {}

    private static final Map<String, FieldAllyProvider> PROVIDERS = new LinkedHashMap<>();

    /** Register a provider. Re-registering a key replaces it. */
    public static void register(String key, FieldAllyProvider provider) {
        if (key == null || key.isBlank() || provider == null) return;
        PROVIDERS.put(key, provider);
    }

    /** True when nothing is registered, so the collect path can skip this entirely. */
    public static boolean isEmpty() {
        return PROVIDERS.isEmpty();
    }

    /**
     * Ask every provider what it wants fielded for this player.
     *
     * <p>A provider that throws is logged and skipped: one broken addon should cost its own
     * allies, not everyone else's, and certainly not the fight.
     *
     * @param freeSlots slots left under the player's party cap, passed through as advisory.
     *                  Results are deliberately NOT truncated to it - see
     *                  {@link FieldAllyProvider#provide}
     */
    public static List<FieldAllyProvider.FieldAlly> collect(ServerWorld world,
                                                            ServerPlayerEntity player,
                                                            int freeSlots) {
        if (PROVIDERS.isEmpty() || world == null || player == null) return List.of();
        List<FieldAllyProvider.FieldAlly> out = new ArrayList<>();
        for (Map.Entry<String, FieldAllyProvider> e : PROVIDERS.entrySet()) {
            try {
                List<FieldAllyProvider.FieldAlly> got = e.getValue().provide(world, player, freeSlots);
                if (got != null) {
                    for (FieldAllyProvider.FieldAlly a : got) {
                        if (a != null) out.add(a);
                    }
                }
            } catch (Throwable t) {
                CrafticsMod.LOGGER.error("Field ally provider '{}' threw; its allies are skipped",
                    e.getKey(), t);
            }
        }
        return out;
    }

    /**
     * Ask every provider what it wants benched for this player.
     *
     * <p>Iterates in the same registration order {@link #collect} uses, so a player's bench
     * reads in the order their mods were loaded rather than shuffling between fights.
     *
     * <p>No {@code freeSlots}: a bench has no cap to be advised about. Craftics' party cap
     * governs how many allies stand on the grid, and a benched creature stands nowhere.
     */
    public static List<FieldAllyProvider.FieldAlly> collectReserves(ServerWorld world,
                                                                    ServerPlayerEntity player) {
        if (PROVIDERS.isEmpty() || world == null || player == null) return List.of();
        List<FieldAllyProvider.FieldAlly> out = new ArrayList<>();
        for (Map.Entry<String, FieldAllyProvider> e : PROVIDERS.entrySet()) {
            try {
                List<FieldAllyProvider.FieldAlly> got = e.getValue().reserves(world, player);
                if (got != null) {
                    for (FieldAllyProvider.FieldAlly a : got) {
                        if (a != null) out.add(a);
                    }
                }
            } catch (Throwable t) {
                CrafticsMod.LOGGER.error("Field ally provider '{}' threw; its reserves are skipped",
                    e.getKey(), t);
            }
        }
        return out;
    }

    /** Clear every registration. Test hook. */
    public static void clear() {
        PROVIDERS.clear();
    }
}
