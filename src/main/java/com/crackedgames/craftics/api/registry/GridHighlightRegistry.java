package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.HighlightLayer;
import com.crackedgames.craftics.core.GridPos;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Addon-owned tile overlays, keyed by the player they describe.
 *
 * <p>Held here rather than on the combat instance because Craftics rebuilds its highlight lists
 * from scratch on every refresh: an overlay written into those lists would survive exactly one
 * click. Everything in this registry is re-applied on every refresh until the addon clears it.
 *
 * <p>Process-local and never persisted, like every other addon registration. Cleared for each
 * participant when their fight ends, so an overlay cannot leak into the next one.
 *
 * @see HighlightLayer
 * @since 0.4.5
 */
public final class GridHighlightRegistry {

    private GridHighlightRegistry() {}

    /**
     * One layer's worth of addon tiles.
     *
     * @param tiles     the tiles to paint
     * @param exclusive true to drop Craftics' own tiles for this layer, false to add to them
     * @param dirX      arrow direction x, {@link HighlightLayer#WARNING} only. 0 for no arrows
     * @param dirZ      arrow direction z, {@link HighlightLayer#WARNING} only. 0 for no arrows
     */
    public record Layer(List<GridPos> tiles, boolean exclusive, int dirX, int dirZ) {}

    private static final Map<UUID, Map<HighlightLayer, Layer>> OVERLAYS = new ConcurrentHashMap<>();

    /** Set (or replace) one layer's overlay. An empty tile list with {@code exclusive} still hides Craftics' own. */
    public static void set(UUID player, HighlightLayer layer, Collection<GridPos> tiles,
                           boolean exclusive, int dirX, int dirZ) {
        if (player == null || layer == null) return;
        List<GridPos> copy = tiles == null ? List.of() : List.copyOf(tiles);
        OVERLAYS.computeIfAbsent(player, k -> new EnumMap<>(HighlightLayer.class))
            .put(layer, new Layer(copy, exclusive, dirX, dirZ));
    }

    /** The overlay for one layer, or null if the addon has not set one. */
    public static Layer get(UUID player, HighlightLayer layer) {
        if (player == null || layer == null) return null;
        Map<HighlightLayer, Layer> byLayer = OVERLAYS.get(player);
        return byLayer == null ? null : byLayer.get(layer);
    }

    /** Whether this player has any overlay at all, so the highlight pass can skip the lookups. */
    public static boolean hasAny(UUID player) {
        if (player == null) return false;
        Map<HighlightLayer, Layer> byLayer = OVERLAYS.get(player);
        return byLayer != null && !byLayer.isEmpty();
    }

    /** Drop one layer's overlay. Craftics' own tiles for that layer come back on the next refresh. */
    public static void clear(UUID player, HighlightLayer layer) {
        if (player == null || layer == null) return;
        Map<HighlightLayer, Layer> byLayer = OVERLAYS.get(player);
        if (byLayer == null) return;
        byLayer.remove(layer);
        if (byLayer.isEmpty()) OVERLAYS.remove(player);
    }

    /** Drop every overlay for one player. Called when their fight ends. */
    public static void clear(UUID player) {
        if (player == null) return;
        OVERLAYS.remove(player);
    }

    /** Forget every overlay for everyone. For tests. */
    public static void clearAll() {
        OVERLAYS.clear();
    }
}
