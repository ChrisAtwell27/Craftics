package com.crackedgames.craftics.compat.simplybows;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.registry.WeaponRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Compatibility module for the Simply Bows mod (sweenus).
 *
 * <p>Simply Bows adds seven unique bows, each with a signature trick. Craftics gives each
 * one an affinity, a range, and a turn-based reading of that trick - see
 * {@link SimplyBowsUniques} for the abilities themselves.
 *
 * <p>Two details of the mod matter here. Its item ids carry a folder in the path
 * ({@code simplybows:vine_bow/vine_bow}, not {@code simplybows:vine_bow}), so a naive
 * lookup silently finds nothing. And every one of its bows is a real bow, so Craftics
 * routes them through the bow path - arrows, bow enchants, ranged accuracy - rather than
 * treating them as generic ranged weapons.
 *
 * <p>No compile-time dependency: everything resolves by registry id, and the module
 * silently does nothing when the mod is absent.
 */
public final class SimplyBowsCompat {

    public static final String MOD_ID = "simplybows";
    public static final String NAMESPACE = "simplybows";

    private static boolean loaded = false;
    private static boolean registered = false;

    private SimplyBowsCompat() {}

    public static boolean isLoaded() { return loaded; }
    public static boolean isRegistered() { return registered; }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /** Flag mod presence; do NOT touch the registry yet (mod load order is unspecified). */
    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            CrafticsMod.LOGGER.debug("[Craftics × Simply Bows] mod not loaded - skipping registration");
            return;
        }
        loaded = true;
    }

    /** Late-phase registration. Idempotent; silent on the retry path (called from the tooltip render). */
    public static void registerDeferred() {
        if (registered || !loaded) return;
        if (!SimplyBowsUniques.registerAll()) return;
        registered = true;
        CrafticsMod.LOGGER.info("[Craftics × Simply Bows] enabled - registered {} unique bows",
            SimplyBowsUniques.registeredPaths().size());
    }

    // =========================================================================
    // Item lookup + gating
    // =========================================================================

    /**
     * Look up one of the mod's items. {@code path} must be the full registry path
     * including its folder, e.g. {@code "vine_bow/vine_bow"}.
     */
    static Item lookupItem(String path) {
        Identifier id = Identifier.of(NAMESPACE, path);
        if (!Registries.ITEM.containsId(id)) return null;
        return Registries.ITEM.get(id);
    }

    /** True if the item is one of the Simply Bows uniques Craftics registered. */
    public static boolean isSimplyBow(Item item) {
        if (item == null || !loaded) return false;
        Identifier id = Registries.ITEM.getId(item);
        return NAMESPACE.equals(id.getNamespace())
            && SimplyBowsUniques.registeredPaths().contains(id.getPath())
            && WeaponRegistry.isRegistered(item);
    }

    /** Every registered unique bow item, for the boss loot pool. Empty when the mod is absent. */
    public static List<Item> registeredBows() {
        if (!registered) return List.of();
        List<Item> out = new ArrayList<>();
        for (String path : SimplyBowsUniques.registeredPaths()) {
            Item item = lookupItem(path);
            if (item != null) out.add(item);
        }
        return List.copyOf(out);
    }
}
