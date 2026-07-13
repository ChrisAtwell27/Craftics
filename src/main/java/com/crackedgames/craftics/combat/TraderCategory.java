package com.crackedgames.craftics.combat;

/**
 * A villager trader "type" - the counterpart to {@link com.crackedgames.craftics.combat.barter.BarterCategory}
 * on the piglin side, and registry-driven for exactly the same reason: an addon cannot extend a
 * Java enum, so a hardcoded {@code TraderType} could never be added to.
 *
 * <p>{@code id} is namespaced ({@code "craftics:weaponsmith"}, {@code "mymod:blacksmith"}).
 * {@code icon} is a chat-formatted glyph. {@code minBiomeTier} gates the trader behind island
 * progression - a trader is never offered, met, or seated below its tier.
 *
 * <p>Stock is supplied separately by a {@link com.crackedgames.craftics.api.registry.TraderStockProvider}
 * registered under the same id, so the category (identity) and its wares (content) can be
 * declared independently.
 *
 * @since 0.2.10
 */
public record TraderCategory(String id, String displayName, String icon, int minBiomeTier) {

    public TraderCategory {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("TraderCategory id required");
        if (displayName == null) displayName = id;
        if (icon == null) icon = "";
        if (minBiomeTier < 0) minBiomeTier = 0;
    }

    /** The local path of a namespaced id, e.g. {@code "weaponsmith"} from {@code "craftics:weaponsmith"}. */
    public String localId() {
        int idx = id.indexOf(':');
        return idx >= 0 ? id.substring(idx + 1) : id;
    }
}
