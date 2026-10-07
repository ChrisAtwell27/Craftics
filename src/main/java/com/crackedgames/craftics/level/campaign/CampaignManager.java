package com.crackedgames.craftics.level.campaign;

import com.crackedgames.craftics.api.RegistrationSource;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The single read-point for the world's <em>active</em> {@link Campaign}.
 *
 * <p>Campaigns are registered here from three sources, mirroring {@code EnemyRegistry}'s
 * CODE/DATAPACK split: Craftics' own vanilla campaign (CODE, Task 4), addons via
 * {@code CrafticsAPI.registerCampaign} (CODE), and datapacks via {@code CampaignJsonLoader}
 * (DATAPACK). Exactly one campaign is "active" per world; every gameplay query
 * (difficulty scaling, dimension labeling, completion detection) reads it through the
 * delegators here.
 *
 * <h2>Active resolution</h2>
 * <p>Rule: <strong>the most-recently-registered non-vanilla campaign wins; else
 * {@code craftics:vanilla}</strong>. Full-replace semantics - a single addon/datapack
 * campaign replaces the built-in path entirely.
 * <ul>
 *   <li>If any campaign other than {@code "craftics:vanilla"} is registered, the active one
 *       is the most-recently-registered such campaign.</li>
 *   <li>Otherwise the active one is {@code "craftics:vanilla"} if registered.</li>
 *   <li>Defensively (shouldn't happen once vanilla lands in Task 4): if neither applies but a
 *       single campaign is registered, that one is active; if the registry is empty, there is
 *       no active campaign and the delegators are null-safe.</li>
 * </ul>
 *
 * <h2>Ordering signal &amp; resolution timing</h2>
 * <p>{@link ConcurrentHashMap} does not preserve insertion order, so each registration is
 * stamped with a monotonically increasing sequence number from an {@link AtomicLong} into
 * {@link #REGISTRATION_SEQ}. {@link #active()} resolves <em>on demand</em>: it scans the
 * registry each call and returns the non-vanilla campaign with the highest sequence number
 * (or vanilla / the lone entry as the rule dictates). Resolution is therefore always current
 * - a datapack {@code register} after {@code clearDatapackEntries} is reflected on the very
 * next {@link #active()} call with no caching to invalidate and no extra mod hook needed.
 * Registration is rare (init / reload only); a full scan per query is negligible.
 *
 * <p>This class is intentionally Minecraft-free (it deals only in {@link Campaign},
 * {@link RegistrationSource}, and {@link String}) so it and its tests run under plain JUnit.
 *
 * <h2>Concurrency contract</h2>
 * <p>Registration occurs on the server thread (init / data-pack reload); reads are lock-free
 * and eventually consistent.
 *
 * @since 0.2.2
 */
public final class CampaignManager {

    /** The reserved id of Craftics' built-in campaign; loses to any other registered campaign. */
    public static final String VANILLA_ID = "craftics:vanilla";

    private static final Map<String, Campaign> REGISTRY = new ConcurrentHashMap<>();
    /** Ids whose current entry came from a JSON datapack - dropped on {@code /reload}. */
    private static final Set<String> DATAPACK_KEYS = ConcurrentHashMap.newKeySet();
    /** Registration-order stamp per id; the ordering signal for "most recently registered". */
    private static final Map<String, Long> REGISTRATION_SEQ = new ConcurrentHashMap<>();
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private CampaignManager() {}

    // === Registration ===

    /** Register a campaign from code (survives {@code /reload}). */
    public static void register(Campaign campaign) {
        register(campaign, RegistrationSource.CODE);
    }

    /** Register a campaign, tagging whether it came from code or a datapack. */
    public static void register(Campaign campaign, RegistrationSource source) {
        String id = campaign.id();
        // Stamp the sequence BEFORE publishing the entry, so a concurrent active() scan that
        // observes the new REGISTRY entry always sees a real seq (never a missing/MIN_VALUE one).
        REGISTRATION_SEQ.put(id, SEQUENCE.incrementAndGet());
        REGISTRY.put(id, campaign);
        if (source == RegistrationSource.DATAPACK) {
            DATAPACK_KEYS.add(id);
        } else {
            DATAPACK_KEYS.remove(id);
        }
    }

    /**
     * Remove every campaign that was loaded from a JSON datapack. Code-registered campaigns
     * (vanilla, addons) survive. The active campaign re-resolves automatically on the next
     * {@link #active()} call, so if the active campaign was a datapack one, resolution falls
     * back to the surviving code campaigns (vanilla).
     */
    public static void clearDatapackEntries() {
        for (String id : DATAPACK_KEYS) {
            REGISTRY.remove(id);
            REGISTRATION_SEQ.remove(id);
        }
        DATAPACK_KEYS.clear();
    }

    // === Active resolution ===

    /**
     * The resolved active campaign, or {@code null} if none is registered.
     *
     * <p>Computed on demand: the most-recently-registered non-vanilla campaign wins; else
     * {@code craftics:vanilla}; else (defensively) the lone registered campaign, or
     * {@code null} when the registry is empty.
     */
    @Nullable
    public static Campaign active() {
        // Resolution scans the tiny (1-2 entry) registry on every call and is intentionally NOT
        // cached: registration is rare and the scan is negligible, while caching would need an
        // invalidation hook on register/clear. If profiling ever shows this hot, cache a
        // `volatile Campaign` invalidated on register()/clearDatapackEntries().
        Campaign newestNonVanilla = null;
        long newestSeq = Long.MIN_VALUE;
        Campaign vanilla = null;
        Campaign any = null;

        for (Map.Entry<String, Campaign> e : REGISTRY.entrySet()) {
            String id = e.getKey();
            Campaign campaign = e.getValue();
            any = campaign;
            if (VANILLA_ID.equals(id)) {
                vanilla = campaign;
                continue;
            }
            long seq = REGISTRATION_SEQ.getOrDefault(id, Long.MIN_VALUE);
            if (newestNonVanilla == null || seq > newestSeq) {
                newestNonVanilla = campaign;
                newestSeq = seq;
            }
        }

        if (newestNonVanilla != null) {
            return newestNonVanilla;
        }
        if (vanilla != null) {
            return vanilla;
        }
        return any; // defensive: a single non-vanilla-id, non-vanilla campaign, or null when empty
    }

    /** Whether any campaign is registered (i.e. {@link #active()} is non-null). */
    public static boolean hasActive() {
        return active() != null;
    }

    // === Query delegators (all read the active campaign; null-safe) ===

    /**
     * Ordinal of {@code biomeId} in the active campaign's order, or {@code -1} if it has none.
     *
     * <p>A side-region biome has a place on this scale without being on the line: it reads as
     * the position right after its region's anchor, plus its own index. That is the campaign
     * biome which opens at the same moment, so everything that scales by ordinal (enemy stats,
     * loot tier, boss tuning) treats optional content as exactly as hard as what it opens
     * beside. It is a difficulty, not a place in the run - {@link #orderedBiomeIds} does not
     * contain it, and whether it is unlocked is {@link #isSideBiomeUnlocked}'s question, never
     * a comparison of this number against the campaign cursor.
     */
    public static int ordinalOf(String biomeId, int branchChoice) {
        Campaign active = active();
        if (active == null) return -1;
        int ordinal = active.ordinalOf(biomeId, branchChoice);
        if (ordinal >= 0) return ordinal;
        CampaignSideRegion side = sideRegionOf(biomeId);
        if (side == null) return -1;
        return active.ordinalOf(side.unlockAfterBiomeId(), branchChoice) + 1 + side.indexOf(biomeId);
    }

    /** Region containing {@code biomeId}: one of the active campaign's, or an active side region. */
    @Nullable
    public static CampaignRegion regionOf(String biomeId) {
        Campaign active = active();
        if (active == null) return null;
        CampaignRegion region = active.regionOf(biomeId);
        if (region != null) return region;
        CampaignSideRegion side = sideRegionOf(biomeId);
        return side != null ? side.region() : null;
    }

    // === Side regions (optional content beside the campaign) ===

    /** Registered side regions, in registration order. Tiny, and read far more than written. */
    private static final List<CampaignSideRegion> SIDE_REGIONS =
        new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Register an optional region that opens beside the campaign. Re-registering a region id
     * replaces the earlier definition.
     */
    public static void registerSideRegion(CampaignSideRegion side) {
        if (side == null) return;
        SIDE_REGIONS.removeIf(s -> s.region().id().equals(side.region().id()));
        SIDE_REGIONS.add(side);
    }

    /**
     * The side regions in play: those whose anchor biome is in the active campaign.
     *
     * <p>A region is also left out when it would be ambiguous - one of its biomes is already
     * on the campaign line, or it shares an id with a campaign region. A biome cannot be both
     * required and optional, and silently preferring one reading would leave the other
     * half-working.
     */
    public static List<CampaignSideRegion> sideRegions() {
        Campaign active = active();
        if (active == null || SIDE_REGIONS.isEmpty()) return List.of();
        List<CampaignSideRegion> out = new java.util.ArrayList<>();
        for (CampaignSideRegion side : SIDE_REGIONS) {
            if (active.ordinalOf(side.unlockAfterBiomeId(), 0) < 0) continue;
            boolean clash = false;
            for (CampaignRegion region : active.regions()) {
                if (region.id().equals(side.region().id())) clash = true;
            }
            for (String biomeId : side.biomeIds()) {
                if (active.ordinalOf(biomeId, 0) >= 0) clash = true;
            }
            if (!clash) out.add(side);
        }
        return out;
    }

    /** The active side region that holds {@code biomeId}, or {@code null}. */
    @Nullable
    public static CampaignSideRegion sideRegionOf(String biomeId) {
        if (biomeId == null || SIDE_REGIONS.isEmpty()) return null;
        for (CampaignSideRegion side : sideRegions()) {
            if (side.indexOf(biomeId) >= 0) return side;
        }
        return null;
    }

    /**
     * Whether a side region has opened for an island: its anchor biome is cleared.
     *
     * @param highestBiomeUnlocked the island's campaign cursor - the 1-based position of the
     *                             furthest biome it may play, so a biome at position {@code k}
     *                             is cleared exactly when the cursor is past {@code k}
     */
    public static boolean isSideRegionOpen(CampaignSideRegion side, int branchChoice,
                                           int highestBiomeUnlocked) {
        Campaign active = active();
        if (active == null || side == null) return false;
        int anchorOrdinal = active.ordinalOf(side.unlockAfterBiomeId(), Math.max(0, branchChoice));
        return anchorOrdinal >= 0 && highestBiomeUnlocked > anchorOrdinal + 1;
    }

    /**
     * Whether a side-region biome may be played: its region is open, and every biome before
     * it in that region has been cleared.
     *
     * @param clearedInRegion how many of the region's biomes the island has cleared
     */
    public static boolean isSideBiomeUnlocked(String biomeId, int branchChoice,
                                              int highestBiomeUnlocked, int clearedInRegion) {
        CampaignSideRegion side = sideRegionOf(biomeId);
        if (side == null) return false;
        return isSideRegionOpen(side, branchChoice, highestBiomeUnlocked)
            && clearedInRegion >= side.indexOf(biomeId);
    }

    /** Whether {@code biomeId} is the final biome of the active campaign ({@code false} if none). */
    public static boolean isFinalBiome(String biomeId, int branchChoice) {
        Campaign active = active();
        return active != null && active.isFinal(biomeId, branchChoice);
    }

    /** Total biome count of the active campaign ({@code 0} if none). */
    public static int totalBiomes() {
        Campaign active = active();
        return active != null ? active.totalBiomeCount() : 0;
    }

    /** Ordered biome ids of the active campaign for {@code branchChoice} (empty list if none). */
    public static List<String> orderedBiomeIds(int branchChoice) {
        Campaign active = active();
        return active != null ? active.orderedBiomeIds(branchChoice) : List.of();
    }

    /** Display name of the active campaign (empty string if none). */
    public static String activeDisplayName() {
        Campaign active = active();
        return active != null ? active.displayName() : "";
    }

    /** Ordered node objects of the active campaign for {@code branchChoice} (empty list if none). */
    public static List<CampaignNode> orderedNodes(int branchChoice) {
        Campaign active = active();
        return active != null ? active.orderedNodes(branchChoice) : List.of();
    }

    /** Node for {@code biomeId} in the active campaign, or {@code null}. */
    @Nullable
    public static CampaignNode nodeOf(String biomeId) {
        Campaign active = active();
        if (active == null) return null;
        CampaignNode node = active.nodeOf(biomeId);
        if (node != null) return node;
        CampaignSideRegion side = sideRegionOf(biomeId);
        return side != null ? side.region().nodes().get(side.indexOf(biomeId)) : null;
    }

    /** Regions of the active campaign in play order (empty list if none). */
    public static List<CampaignRegion> regions() {
        Campaign active = active();
        return active != null ? active.regions() : List.of();
    }

    /** Region of the active campaign whose {@code id()} equals {@code regionId}, or {@code null}. */
    @Nullable
    public static CampaignRegion regionById(String regionId) {
        for (CampaignRegion region : regions()) {
            if (region.id().equals(regionId)) {
                return region;
            }
        }
        for (CampaignSideRegion side : sideRegions()) {
            if (side.region().id().equals(regionId)) {
                return side.region();
            }
        }
        return null;
    }

    /** Whether the active campaign exists and has a valid branch ({@code false} if none). */
    public static boolean isBranchValid() {
        Campaign active = active();
        return active != null && active.isBranchValid();
    }

    // === Test support ===

    /**
     * Test-only full reset: drops <em>all</em> registered campaigns (code and datapack) and
     * resets the ordering sequence. Because the active-resolution rule is "most-recently
     * registered non-vanilla wins", a code entry leaked from a prior test could otherwise
     * change another test's active campaign; {@code clearDatapackEntries} alone cannot remove
     * code entries. Call this from {@code @AfterEach}. Not used by production code.
     */
    public static void clearAllForTest() {
        REGISTRY.clear();
        DATAPACK_KEYS.clear();
        REGISTRATION_SEQ.clear();
        SEQUENCE.set(0);
        SIDE_REGIONS.clear();
    }
}
