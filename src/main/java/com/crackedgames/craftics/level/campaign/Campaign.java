package com.crackedgames.craftics.level.campaign;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Immutable authored playthrough - an ordered list of {@link CampaignRegion}s (each an
 * ordered list of {@link CampaignNode}s) plus an optional {@link CampaignBranch}.
 *
 * <p>The flattened node order is the single source of the run "ordinal" that all
 * difficulty scaling, dimension labeling, unlock gating, and completion detection read
 * from. The optional branch lets one region swap two contiguous segments of its biomes
 * (every biome always played, only order differs) driven by an int {@code branchChoice} of
 * {@code 0} or {@code 1}. The common single-biome swap is just two length-1 segments.
 *
 * <p>This is a pure-logic model with no Minecraft dependencies so it can be unit-tested
 * with plain JUnit. The flattened orders and the id&rarr;ordinal / id&rarr;region /
 * id&rarr;node lookups are computed once (eagerly, in the constructor) and stored in a
 * per-instance {@code final} cache; every derived query reads that cache rather than
 * rescanning the regions.
 *
 * <p>Validation lives in the canonical constructor and throws IllegalArgumentException.
 *
 * <p>Branch observability: branch validity is exposed as the pure, testable
 * {@link #isBranchValid()}; an invalid branch never throws and falls back to the linear
 * order. An invalid branch is also logged (warn) once when the cache is built, via a
 * dependency-free SLF4J logger.
 *
 * @since 0.2.2
 */
public final class Campaign {

    // The literal "craftics" matches CrafticsMod.MOD_ID and is used directly to avoid
    // coupling this pure model to the Minecraft-loaded CrafticsMod class.
    private static final Logger LOGGER = LoggerFactory.getLogger("craftics");

    private final String id;
    private final String displayName;
    private final List<CampaignRegion> regions;
    private final CampaignBranch branch;

    /** Eagerly computed, per-instance derived-state snapshot. */
    private final Cache cache;

    /**
     * Canonical constructor - the single validation site (used directly by JSON
     * deserialization, the builder, and tests). Defensively copies {@code regions} and
     * eagerly computes the derived cache.
     *
     * @param id          unique campaign id; must be non-blank
     * @param displayName name shown to players; defaults to {@code id} when {@code null}
     * @param regions     ordered regions (defensively copied; must be non-empty)
     * @param branch      optional 2-way swap, or {@code null} for a purely linear campaign
     * @throws IllegalArgumentException if {@code id} is blank or {@code regions} is empty
     */
    public Campaign(String id, String displayName, List<CampaignRegion> regions, CampaignBranch branch) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Campaign requires a non-blank id");
        }
        if (regions == null || regions.isEmpty()) {
            throw new IllegalArgumentException("Campaign " + id + " requires at least one region");
        }
        this.id = id;
        this.displayName = (displayName != null) ? displayName : id;
        this.regions = List.copyOf(regions);
        this.branch = branch;
        this.cache = computeCache(this.regions, this.branch);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    // --- Accessors (hand-written; same public API a record gave) ---

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public List<CampaignRegion> regions() {
        return regions;
    }

    public CampaignBranch branch() {
        return branch;
    }

    // --- Derived / computed accessors (read from the eager cache) ---

    /**
     * The flattened biome-id order across all regions (region order, then node order),
     * with the optional branch swap applied when {@code branchChoice == 1} and the branch
     * is valid. This is THE ordinal source. For {@code branchChoice == 0}, for any value
     * when there is no branch, or when the branch is invalid, it is the plain flatten.
     */
    public List<String> orderedBiomeIds(int branchChoice) {
        return (branchChoice == 1) ? cache.swappedOrder : cache.linearOrder;
    }

    /**
     * The flattened node objects in run order - the same flatten as
     * {@link #orderedBiomeIds(int)} with the identical branch swap applied, but returning
     * the {@link CampaignNode} objects (so callers can read per-ordinal data such as
     * {@code labelOverride} without an O(n) lookup). Unmodifiable.
     */
    public List<CampaignNode> orderedNodes(int branchChoice) {
        return (branchChoice == 1) ? cache.swappedNodes : cache.linearNodes;
    }

    /**
     * Whether {@link #branch()} exists and is valid: the region exists and both segments are
     * contiguous, disjoint, non-interleaving sublists of that region's node order.
     */
    public boolean isBranchValid() {
        return cache.branchValid;
    }

    /** Index of {@code biomeId} in {@link #orderedBiomeIds(int)}, or {@code -1} if absent. */
    public int ordinalOf(String biomeId, int branchChoice) {
        Map<String, Integer> map = (branchChoice == 1) ? cache.swappedOrdinal : cache.linearOrdinal;
        Integer idx = map.get(biomeId);
        return idx != null ? idx : -1;
    }

    /** Region containing a node with {@code biomeId}, or {@code null}. Branch-independent. */
    public CampaignRegion regionOf(String biomeId) {
        return cache.regionByBiome.get(biomeId);
    }

    /** Node for {@code biomeId}, or {@code null}. */
    public CampaignNode nodeOf(String biomeId) {
        return cache.nodeByBiome.get(biomeId);
    }

    /** True iff {@code biomeId} is the last entry of {@link #orderedBiomeIds(int)}. */
    public boolean isFinal(String biomeId, int branchChoice) {
        List<String> order = orderedBiomeIds(branchChoice);
        if (order.isEmpty()) {
            return false;
        }
        return order.get(order.size() - 1).equals(biomeId);
    }

    /** Total number of nodes across all regions. Branch-independent. */
    public int totalBiomeCount() {
        return cache.totalBiomeCount;
    }

    // --- Value semantics (structural over id, displayName, regions, branch) ---

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Campaign other)) {
            return false;
        }
        return id.equals(other.id)
            && displayName.equals(other.displayName)
            && regions.equals(other.regions)
            && Objects.equals(branch, other.branch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, displayName, regions, branch);
    }

    @Override
    public String toString() {
        return "Campaign[id=" + id
            + ", displayName=" + displayName
            + ", regions=" + regions
            + ", branch=" + branch + "]";
    }

    // --- Cache plumbing ---

    /** Immutable snapshot of everything derived from the region/branch structure. */
    private static final class Cache {
        final boolean branchValid;
        final List<String> linearOrder;            // branchChoice 0
        final List<String> swappedOrder;           // branchChoice 1 (== linearOrder if invalid/none)
        final List<CampaignNode> linearNodes;      // branchChoice 0 (node objects)
        final List<CampaignNode> swappedNodes;     // branchChoice 1 (== linearNodes if invalid/none)
        final Map<String, Integer> linearOrdinal;
        final Map<String, Integer> swappedOrdinal;
        final Map<String, CampaignRegion> regionByBiome;
        final Map<String, CampaignNode> nodeByBiome;
        final int totalBiomeCount;

        Cache(boolean branchValid, List<String> linearOrder, List<String> swappedOrder,
              List<CampaignNode> linearNodes, List<CampaignNode> swappedNodes,
              Map<String, Integer> linearOrdinal, Map<String, Integer> swappedOrdinal,
              Map<String, CampaignRegion> regionByBiome, Map<String, CampaignNode> nodeByBiome,
              int totalBiomeCount) {
            this.branchValid = branchValid;
            this.linearOrder = linearOrder;
            this.swappedOrder = swappedOrder;
            this.linearNodes = linearNodes;
            this.swappedNodes = swappedNodes;
            this.linearOrdinal = linearOrdinal;
            this.swappedOrdinal = swappedOrdinal;
            this.regionByBiome = regionByBiome;
            this.nodeByBiome = nodeByBiome;
            this.totalBiomeCount = totalBiomeCount;
        }
    }

    private static Cache computeCache(List<CampaignRegion> regions, CampaignBranch branch) {
        // id -> node / region (region membership is branch-independent)
        Map<String, CampaignRegion> regionByBiome = new HashMap<>();
        Map<String, CampaignNode> nodeByBiome = new HashMap<>();
        int total = 0;
        for (CampaignRegion region : regions) {
            for (CampaignNode node : region.nodes()) {
                total++;
                // first occurrence wins on duplicate ids (defensive; ids are expected unique)
                regionByBiome.putIfAbsent(node.biomeId(), region);
                nodeByBiome.putIfAbsent(node.biomeId(), node);
            }
        }

        // Linear flatten (branchChoice 0) - node objects and their ids.
        List<CampaignNode> linearNodeList = new ArrayList<>(total);
        for (CampaignRegion region : regions) {
            linearNodeList.addAll(region.nodes());
        }
        List<CampaignNode> linearNodes = List.copyOf(linearNodeList);
        List<String> linearOrder = toBiomeIds(linearNodes);

        boolean branchValid = computeBranchValid(regions, branch);

        // Swapped flatten (branchChoice 1). Falls back to linear when branch absent/invalid.
        List<CampaignNode> swappedNodes;
        List<String> swappedOrder;
        if (branchValid) {
            swappedNodes = buildSwappedNodes(regions, branch);
            swappedOrder = toBiomeIds(swappedNodes);
        } else {
            swappedNodes = linearNodes;
            swappedOrder = linearOrder;
        }

        Map<String, Integer> linearOrdinal = toOrdinalMap(linearOrder);
        Map<String, Integer> swappedOrdinal =
            (swappedOrder == linearOrder) ? linearOrdinal : toOrdinalMap(swappedOrder);

        return new Cache(branchValid, linearOrder, swappedOrder, linearNodes, swappedNodes,
            linearOrdinal, swappedOrdinal, regionByBiome, nodeByBiome, total);
    }

    private static boolean computeBranchValid(List<CampaignRegion> regions, CampaignBranch branch) {
        if (branch == null) {
            return false;
        }
        CampaignRegion region = findRegion(regions, branch.regionId());
        if (region == null) {
            LOGGER.warn("Campaign branch references unknown region '{}'; falling back to linear order",
                branch.regionId());
            return false;
        }

        List<String> regionIds = regionBiomeIds(region);
        int aStart = indexOfSublist(regionIds, branch.segmentA());
        int bStart = indexOfSublist(regionIds, branch.segmentB());
        if (aStart < 0 || bStart < 0) {
            LOGGER.warn("Campaign branch in region '{}' has segment(s) that are not a contiguous "
                    + "sublist (segmentA={} found={}, segmentB={} found={}); falling back to linear order",
                branch.regionId(), branch.segmentA(), aStart >= 0, branch.segmentB(), bStart >= 0);
            return false;
        }

        int aEnd = aStart + branch.segmentA().size() - 1; // inclusive
        int bEnd = bStart + branch.segmentB().size() - 1; // inclusive
        // Disjoint and non-interleaving: one whole segment must lie strictly before the other.
        boolean disjoint = (aEnd < bStart) || (bEnd < aStart);
        if (!disjoint) {
            LOGGER.warn("Campaign branch in region '{}' has overlapping/interleaving segments "
                    + "(segmentA at [{},{}], segmentB at [{},{}]); falling back to linear order",
                branch.regionId(), aStart, aEnd, bStart, bEnd);
            return false;
        }
        return true;
    }

    /**
     * Flatten with the two contiguous segments traded in place, returning node objects.
     * The branch is known valid here (regionId exists; both segments are contiguous, disjoint,
     * non-interleaving sublists of the region's node order).
     *
     * <p>Within the branch region, the segment occupying the earlier index range is replaced by
     * the other segment's nodes (in their own order) and vice versa; everything outside both
     * ranges - including any nodes BETWEEN the two segments (e.g. a pivot biome) and the nodes
     * surrounding them - stays in place. Segments of different lengths are supported: the
     * in-between nodes simply shift index while remaining between the two swapped blocks.
     */
    private static List<CampaignNode> buildSwappedNodes(List<CampaignRegion> regions, CampaignBranch branch) {
        List<CampaignNode> out = new ArrayList<>();
        for (CampaignRegion region : regions) {
            if (region.id().equals(branch.regionId())) {
                out.addAll(swapRegionSegments(region.nodes(), branch));
            } else {
                out.addAll(region.nodes());
            }
        }
        return List.copyOf(out);
    }

    /** Exchange the two contiguous segments within one region's node list. */
    private static List<CampaignNode> swapRegionSegments(List<CampaignNode> nodes, CampaignBranch branch) {
        List<String> ids = new ArrayList<>(nodes.size());
        for (CampaignNode node : nodes) {
            ids.add(node.biomeId());
        }
        int aStart = indexOfSublist(ids, branch.segmentA());
        int bStart = indexOfSublist(ids, branch.segmentB());
        int aEnd = aStart + branch.segmentA().size() - 1;
        int bEnd = bStart + branch.segmentB().size() - 1;

        // Normalize so [firstStart,firstEnd] precedes [secondStart,secondEnd].
        int firstStart;
        int firstEnd;
        int secondStart;
        int secondEnd;
        if (aStart <= bStart) {
            firstStart = aStart; firstEnd = aEnd; secondStart = bStart; secondEnd = bEnd;
        } else {
            firstStart = bStart; firstEnd = bEnd; secondStart = aStart; secondEnd = aEnd;
        }

        List<CampaignNode> out = new ArrayList<>(nodes.size());
        // Prefix before the first segment.
        out.addAll(nodes.subList(0, firstStart));
        // The second segment now sits where the first one was.
        out.addAll(nodes.subList(secondStart, secondEnd + 1));
        // Anything between the two segments (e.g. the pivot) stays between them.
        out.addAll(nodes.subList(firstEnd + 1, secondStart));
        // The first segment now sits where the second one was.
        out.addAll(nodes.subList(firstStart, firstEnd + 1));
        // Suffix after the second segment.
        out.addAll(nodes.subList(secondEnd + 1, nodes.size()));
        return out;
    }

    /**
     * Index of the first occurrence of {@code sub} as a contiguous sublist of {@code list},
     * or {@code -1} if {@code sub} does not appear contiguously (in order).
     */
    private static int indexOfSublist(List<String> list, List<String> sub) {
        if (sub.isEmpty() || sub.size() > list.size()) {
            return -1;
        }
        int limit = list.size() - sub.size();
        for (int i = 0; i <= limit; i++) {
            boolean match = true;
            for (int j = 0; j < sub.size(); j++) {
                if (!list.get(i + j).equals(sub.get(j))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }

    private static List<String> regionBiomeIds(CampaignRegion region) {
        List<String> ids = new ArrayList<>(region.nodes().size());
        for (CampaignNode node : region.nodes()) {
            ids.add(node.biomeId());
        }
        return ids;
    }

    private static List<String> toBiomeIds(List<CampaignNode> nodes) {
        List<String> ids = new ArrayList<>(nodes.size());
        for (CampaignNode node : nodes) {
            ids.add(node.biomeId());
        }
        return List.copyOf(ids);
    }

    private static CampaignRegion findRegion(List<CampaignRegion> regions, String regionId) {
        for (CampaignRegion region : regions) {
            if (region.id().equals(regionId)) {
                return region;
            }
        }
        return null;
    }

    private static Map<String, Integer> toOrdinalMap(List<String> order) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            // first occurrence wins (defensive against duplicate biome ids)
            map.putIfAbsent(order.get(i), i);
        }
        return map;
    }

    /** Fluent builder for {@link Campaign}. */
    public static class Builder {
        private final String id;
        private String displayName;
        private final List<CampaignRegion> regions = new ArrayList<>();
        private CampaignBranch branch;

        public Builder(String id) {
            this.id = id;
            this.displayName = id;
        }

        /** Name shown to players. Defaults to the id. */
        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        /** Append one region, in play order. */
        public Builder region(CampaignRegion region) {
            this.regions.add(region);
            return this;
        }

        /** Set the optional 2-way swap. May be {@code null} for a linear campaign. */
        public Builder branch(CampaignBranch branch) {
            this.branch = branch;
            return this;
        }

        /** Build the campaign; the canonical constructor validates (IllegalArgumentException). */
        public Campaign build() {
            return new Campaign(id, displayName, regions, branch);
        }
    }
}
