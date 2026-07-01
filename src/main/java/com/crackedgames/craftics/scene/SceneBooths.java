package com.crackedgames.craftics.scene;

import com.crackedgames.craftics.combat.TraderSystem;
import com.crackedgames.craftics.combat.barter.BarterCategory;

/** Fixed per-booth merchant assignment for the code-built scenes (Stage 2c), plus occupant-id
 *  resolution to a villager TraderType or a piglin BarterCategory. Met-merchants gating (Stage 3)
 *  will replace the fixed tables; the resolvers stay. */
public final class SceneBooths {
    private SceneBooths() {}

    /** Village booth occupants by index — each MUST be a valid TraderType (lowercased). */
    public static final String[] VILLAGE_OCCUPANTS = {
        "craftics:weaponsmith", "craftics:armorer", "craftics:provisioner"
    };

    /** Bartering Station booth occupants by index — each a registered BarterCategory id. */
    public static final String[] BARTER_OCCUPANTS = {
        "craftics:warmonger", "craftics:hoarder", "craftics:flesh_dealer"
    };

    /** Occupant id for a booth by scene type + index, or "" if out of range / unknown scene. */
    public static String occupantFor(String sceneName, int boothIndex) {
        String[] table = "barter_station".equals(sceneName) ? BARTER_OCCUPANTS
            : "village".equals(sceneName) ? VILLAGE_OCCUPANTS : null;
        if (table == null || boothIndex < 0 || boothIndex >= table.length) return "";
        return table[boothIndex];
    }

    /** Resolve a village occupant id (e.g. "craftics:weaponsmith") to its TraderType, or null. */
    public static TraderSystem.TraderType traderTypeFor(String occupant) {
        if (occupant == null || occupant.isEmpty()) return null;
        int colon = occupant.indexOf(':');
        String local = colon >= 0 ? occupant.substring(colon + 1) : occupant;
        try {
            return TraderSystem.TraderType.valueOf(local.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Resolve a piglin occupant id to its BarterCategory via the registry, or null. */
    public static BarterCategory barterCategoryFor(String occupant) {
        if (occupant == null || occupant.isEmpty()) return null;
        return com.crackedgames.craftics.api.registry.BarterCategoryRegistry.get(occupant);
    }
}
