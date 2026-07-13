package com.crackedgames.craftics.scene;

import com.crackedgames.craftics.api.registry.BarterCategoryRegistry;
import com.crackedgames.craftics.api.registry.TraderCategoryRegistry;
import com.crackedgames.craftics.combat.TraderCategory;
import com.crackedgames.craftics.combat.barter.BarterCategory;

import java.util.List;

/**
 * Booth occupant ids for the code-built scenes, and resolution of an occupant id to the merchant
 * it names.
 *
 * <p>A booth's occupant id is only its DEFAULT identity, taken from the registry in registration
 * order (booth 0 is the first registered trader, and so on). Who actually stands there is decided
 * per visit by {@code SceneController} from the island's met-merchants set - an unmet merchant
 * leaves its booth empty. The ids here exist so a layout can still name a booth, which a
 * hand-built schematic uses to pin a stall to a specific merchant.
 */
public final class SceneBooths {
    private SceneBooths() {}

    /**
     * Occupant id for a booth by scene type + index, or {@code ""} when the index is past the end
     * of the registry (a schematic with more stalls than there are merchants).
     */
    public static String occupantFor(String sceneName, int boothIndex) {
        if (boothIndex < 0) return "";
        if ("barter_station".equals(sceneName)) {
            List<BarterCategory> all = BarterCategoryRegistry.all();
            return boothIndex < all.size() ? all.get(boothIndex).id() : "";
        }
        if ("village".equals(sceneName)) {
            List<TraderCategory> all = TraderCategoryRegistry.all();
            return boothIndex < all.size() ? all.get(boothIndex).id() : "";
        }
        return "";
    }

    /**
     * Resolve a village occupant id to its trader, or {@code null}.
     *
     * <p>Accepts the modern namespaced id ({@code "craftics:weaponsmith"}) and the legacy bare enum
     * name ({@code "WEAPONSMITH"}) that pre-0.2.10 saves used. Returns null for an id that is not
     * registered - e.g. an addon trader whose mod has since been removed - so the caller leaves the
     * booth empty rather than crashing the hall.
     */
    public static TraderCategory traderTypeFor(String occupant) {
        if (occupant == null || occupant.isEmpty()) return null;
        return TraderCategoryRegistry.resolveLegacy(occupant);
    }

    /** Resolve a piglin occupant id to its BarterCategory via the registry, or null. */
    public static BarterCategory barterCategoryFor(String occupant) {
        if (occupant == null || occupant.isEmpty()) return null;
        return BarterCategoryRegistry.get(occupant);
    }
}
