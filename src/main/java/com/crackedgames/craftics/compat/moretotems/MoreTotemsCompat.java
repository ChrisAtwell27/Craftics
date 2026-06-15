package com.crackedgames.craftics.compat.moretotems;

import com.crackedgames.craftics.CrafticsMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Set;

/**
 * Compatibility module for the MoreTotems mod ({@code moretotems}).
 * <p>
 * MoreTotems adds seven extra "totem of undying" items. In vanilla MoreTotems each
 * auto-triggers on lethal damage with its own potion/world effect. Craftics gives each
 * a combat-native behavior instead (see {@code CombatManager} revive path) and strips
 * their tooltips. Detection is by live registry id so there is no compile-time dependency;
 * with the mod absent every call no-ops.
 */
public final class MoreTotemsCompat {

    public static final String MOD_ID = "moretotems";
    // MoreTotems registers its items under its own namespace, which equals MOD_ID here.
    public static final String NAMESPACE = "moretotems";

    public static final String EXPLOSIVE   = "explosive_totem_of_undying";
    public static final String SKELETAL    = "skeletal_totem_of_undying";
    public static final String TELEPORTING = "teleporting_totem_of_undying";
    public static final String GHASTLY     = "ghastly_totem_of_undying";
    public static final String STINGING    = "stinging_totem_of_undying";
    public static final String TENTACLED   = "tentacled_totem_of_undying";
    public static final String ROTTING     = "rotting_totem_of_undying";

    private static final Set<String> TOTEM_PATHS = Set.of(
        EXPLOSIVE, SKELETAL, TELEPORTING, GHASTLY, STINGING, TENTACLED, ROTTING);

    private static boolean loaded = false;

    private MoreTotemsCompat() {}

    public static boolean isLoaded() {
        return loaded;
    }

    /** Early-phase init. Flags presence so the revive/tooltip paths know to look. */
    public static void init() {
        if (loaded) return;
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            CrafticsMod.LOGGER.debug("[Craftics × MoreTotems] mod not loaded - skipping");
            return;
        }
        loaded = true;
        CrafticsMod.LOGGER.info("[Craftics × MoreTotems] enabled - 7 totems mapped to combat effects");
    }

    /** True iff the item is one of the seven MoreTotems totems. */
    public static boolean isMoreTotem(Item item) {
        if (!loaded || item == null) return false;
        return totemPath(item) != null;
    }

    /**
     * The totem's registry path (e.g. {@code "explosive_totem_of_undying"}), or
     * {@code null} if the item is not a MoreTotems totem.
     *
     * <p>Unlike {@link #isMoreTotem(Item)}, this does NOT short-circuit on the
     * mod-loaded flag - it always consults the registry. Callers that want
     * load-gating should use {@link #isMoreTotem(Item)} instead.
     */
    public static String totemPath(Item item) {
        if (item == null) return null;
        Identifier id = Registries.ITEM.getId(item);
        if (id == null || !NAMESPACE.equals(id.getNamespace())) return null;
        String path = id.getPath();
        return TOTEM_PATHS.contains(path) ? path : null;
    }
}
