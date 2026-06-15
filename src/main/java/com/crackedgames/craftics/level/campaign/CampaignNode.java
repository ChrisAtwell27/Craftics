package com.crackedgames.craftics.level.campaign;

/**
 * Immutable single step of a {@link CampaignRegion} - a reference to one biome by id,
 * plus an optional map label override.
 *
 * <p>{@code labelOverride} is the name to show on the campaign map for this node; when
 * {@code null}, callers fall back to the biome's own display name.
 *
 * <p>Validation lives in the canonical constructor and throws IllegalArgumentException.
 *
 * @param biomeId       biome registry id this node plays, e.g. {@code "craftics:village"}
 * @param labelOverride map label to show, or {@code null} to use the biome's display name
 * @since 0.2.2
 */
public record CampaignNode(String biomeId, String labelOverride) {
    public CampaignNode {
        if (biomeId == null || biomeId.isBlank()) {
            throw new IllegalArgumentException("CampaignNode requires a non-blank biomeId");
        }
    }

    /** Convenience factory for the common case of a node with no label override. */
    public static CampaignNode of(String biomeId) {
        return new CampaignNode(biomeId, null);
    }

    /** Convenience factory for a node with an explicit map label. */
    public static CampaignNode of(String biomeId, String labelOverride) {
        return new CampaignNode(biomeId, labelOverride);
    }
}
