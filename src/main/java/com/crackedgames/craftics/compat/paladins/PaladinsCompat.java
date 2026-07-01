package com.crackedgames.craftics.compat.paladins;

import com.crackedgames.craftics.CrafticsMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Compatibility module for the Paladins and Priests mod (RPG Series, mod id "paladins").
 * Registers the mod's melee weapons, kite shields, and staff/wand spell casters into
 * Craftics combat; armor sets and spell books/scrolls are vanity. Gated on mod presence,
 * no compile-time dependency: everything resolves by registry id and no-ops when absent.
 */
public final class PaladinsCompat {

    public static final String MOD_ID = "paladins";
    public static final String NAMESPACE = "paladins";

    /** The three melee weapon type suffixes, longest first so "great_hammer" is matched
     *  before any shorter suffix could collide. */
    static final String[] TYPES = {"great_hammer", "claymore", "mace"};

    private static boolean loaded = false;
    private static boolean registered = false;

    private PaladinsCompat() {}

    public static boolean isLoaded() { return loaded; }

    /** Parse the weapon type from a path like "iron_claymore" -> "claymore". Null if none. Pure. */
    public static String weaponType(String path) {
        if (path == null) return null;
        for (String type : TYPES) {
            if (path.endsWith("_" + type)) return type;
        }
        return null;
    }

    /** Look up a Paladins item by path, or null if it is not registered. */
    static Item lookupItem(String path) {
        Identifier id = Identifier.of(NAMESPACE, path);
        if (!Registries.ITEM.containsId(id)) return null;
        return Registries.ITEM.get(id);
    }

    /** Flag mod presence during main init; do NOT touch the registry yet (load order). */
    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            CrafticsMod.LOGGER.debug("[Craftics x Paladins] mod not loaded - skipping registration");
            return;
        }
        loaded = true;
    }

    /** Late-phase registration on SERVER_STARTING. Idempotent. */
    public static void registerDeferred() {
        if (registered || !loaded) return;
        // Filled in by later tasks:
        PaladinsWeapons.register();      // Task 4
        PaladinsSpells.register();       // Task 5
        registered = true;
        CrafticsMod.LOGGER.info("[Craftics x Paladins] enabled");
    }
}
