package com.crackedgames.craftics.level.campaign;

import java.util.ArrayList;
import java.util.List;

/**
 * A region that sits beside the campaign rather than inside it: optional content that opens
 * when one of the campaign's biomes is cleared and is never required to finish.
 *
 * <p>A {@link Campaign} is one line - every biome has a position, the last one is the final
 * boss, and a single cursor says how far an island has got. A side region is deliberately
 * not on that line. Its biomes do not count toward the campaign's length, do not move the
 * final biome, and are cleared against a counter of their own, so an island can finish the
 * campaign without ever setting foot in one, or wander into one and come back.
 *
 * <p>What it borrows from the line is difficulty. Its first biome plays at the position of
 * whichever campaign biome opens at the same moment - the one after {@code unlockAfterBiomeId}
 * - and each biome after that one step further, so content that opens alongside a region is
 * as hard as that region (see {@link CampaignManager#ordinalOf}).
 *
 * <p>Registered with {@link CampaignManager#registerSideRegion}, and in play only while its
 * anchor biome is part of the active campaign: a side region written for vanilla's Nether
 * simply does not appear under a custom campaign that has no such biome.
 *
 * @param region             the region itself: id, name, colours and its biomes in order
 * @param unlockAfterBiomeId the campaign biome whose clear opens this region
 * @since 0.4.9
 */
public record CampaignSideRegion(CampaignRegion region, String unlockAfterBiomeId) {

    public CampaignSideRegion {
        if (region == null) {
            throw new IllegalArgumentException("CampaignSideRegion requires a region");
        }
        if (region.nodes().isEmpty()) {
            throw new IllegalArgumentException(
                "CampaignSideRegion " + region.id() + " requires at least one biome");
        }
        if (unlockAfterBiomeId == null || unlockAfterBiomeId.isBlank()) {
            throw new IllegalArgumentException(
                "CampaignSideRegion " + region.id() + " requires the biome it unlocks after");
        }
    }

    /** Position of {@code biomeId} within this region, or {@code -1} if it is not one of its biomes. */
    public int indexOf(String biomeId) {
        List<CampaignNode> nodes = region.nodes();
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).biomeId().equals(biomeId)) return i;
        }
        return -1;
    }

    /** This region's biome ids, in order. */
    public List<String> biomeIds() {
        List<String> ids = new ArrayList<>(region.nodes().size());
        for (CampaignNode node : region.nodes()) ids.add(node.biomeId());
        return ids;
    }

    /** How many biomes the region holds. */
    public int size() {
        return region.nodes().size();
    }
}
