package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.CombatTool;
import net.minecraft.item.Item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Control items pinned to the hotbar during a fight, alongside Craftics' own Move item.
 *
 * <p>See {@link CombatTool} for what these are and why a commanded-combat mod needs several.
 *
 * @since 0.4.0
 */
public final class CombatToolRegistry {

    private CombatToolRegistry() {}

    private static final Map<String, CombatTool> TOOLS = new LinkedHashMap<>();
    /** Rebuilt on registration so the slot pass and the use hook never sort or scan. */
    private static List<CombatTool> ordered = List.of();
    private static Map<Item, CombatTool> byItem = Map.of();

    /** Register a tool. Re-registering an id replaces it. */
    public static void register(CombatTool tool) {
        if (tool == null) return;
        TOOLS.put(tool.id(), tool);
        rebuild();
    }

    private static void rebuild() {
        List<CombatTool> list = new ArrayList<>(TOOLS.values());
        // Stable sort on order, so two tools claiming the same slot fall back to
        // registration order rather than swapping places between launches.
        list.sort(Comparator.comparingInt(CombatTool::order));
        ordered = Collections.unmodifiableList(list);
        Map<Item, CombatTool> m = new LinkedHashMap<>();
        for (CombatTool t : list) m.put(t.item(), t);
        byItem = Collections.unmodifiableMap(m);
    }

    /** True when nothing is registered, so the hotbar pass can return immediately. */
    public static boolean isEmpty() {
        return TOOLS.isEmpty();
    }

    /** Registered tools, sorted by their slot order. */
    public static List<CombatTool> ordered() {
        return ordered;
    }

    public static Collection<CombatTool> getAll() {
        return ordered;
    }

    /** The tool that uses this item, or null. */
    public static CombatTool byItem(Item item) {
        return item == null ? null : byItem.get(item);
    }

    /** True when this item belongs to a registered tool. */
    public static boolean isToolItem(Item item) {
        return byItem(item) != null;
    }

    /** Clear every registration. Test hook. */
    public static void clear() {
        TOOLS.clear();
        rebuild();
    }
}
