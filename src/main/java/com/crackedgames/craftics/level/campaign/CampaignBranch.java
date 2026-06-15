package com.crackedgames.craftics.level.campaign;

import java.util.Collections;
import java.util.List;

/**
 * Immutable optional 2-way swap declared by a {@link Campaign}. Both segments are always
 * played; only their order within their region differs based on the run's
 * {@code branchChoice}: when {@code branchChoice == 1}, the two contiguous segments
 * {@code segmentA} and {@code segmentB} trade positions; for {@code branchChoice == 0} the
 * linear order is kept.
 *
 * <p>A segment is an ordered, non-empty list of biome ids. The common case of swapping two
 * single biomes is a pair of length-1 segments; use {@link #of(String, String, String)} for
 * that. The general form swaps two multi-biome contiguous segments - e.g. exchanging
 * {@code [desert, jungle, forest]} with {@code [snowy, mountain]} while a pivot biome between
 * them stays put.
 *
 * <p>A branch is only honored when valid - its {@code regionId} must exist and BOTH segments
 * must appear as contiguous, disjoint, non-interleaving sublists of that region's node order
 * (see {@link Campaign#isBranchValid()}).
 *
 * <p>Validation lives in the compact constructor and throws IllegalArgumentException. Both
 * segment lists are defensively copied into unmodifiable lists.
 *
 * @param regionId id of the region whose two segments swap; must be non-blank
 * @param segmentA first contiguous segment of the swap; non-null, non-empty, blank-free ids
 * @param segmentB second contiguous segment of the swap; non-null, non-empty, blank-free ids
 * @since 0.2.2
 */
public record CampaignBranch(String regionId, List<String> segmentA, List<String> segmentB) {
    public CampaignBranch {
        if (regionId == null || regionId.isBlank()) {
            throw new IllegalArgumentException("CampaignBranch requires a non-blank regionId");
        }
        segmentA = copyValidated(segmentA, "segmentA");
        segmentB = copyValidated(segmentB, "segmentB");
    }

    /**
     * Convenience factory for the common single-biome swap: trades the positions of two
     * single biomes (each becomes a length-1 segment). Equivalent to
     * {@code new CampaignBranch(regionId, List.of(biomeA), List.of(biomeB))}.
     *
     * @param regionId id of the region whose two biomes swap
     * @param biomeA   first biome of the swap pair
     * @param biomeB   second biome of the swap pair
     * @return a branch swapping the two single biomes
     */
    public static CampaignBranch of(String regionId, String biomeA, String biomeB) {
        // Use mutable singletons (not List.of) so a null biome is reported as a clean
        // IllegalArgumentException by the compact constructor rather than List.of's NPE.
        return new CampaignBranch(regionId,
            Collections.singletonList(biomeA),
            Collections.singletonList(biomeB));
    }

    private static List<String> copyValidated(List<String> segment, String name) {
        if (segment == null || segment.isEmpty()) {
            throw new IllegalArgumentException("CampaignBranch requires a non-empty " + name);
        }
        for (String biome : segment) {
            if (biome == null || biome.isBlank()) {
                throw new IllegalArgumentException(
                    "CampaignBranch " + name + " requires non-blank biome ids");
            }
        }
        return List.copyOf(segment);
    }
}
