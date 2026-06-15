package com.crackedgames.craftics.data;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.level.campaign.Campaign;
import com.crackedgames.craftics.level.campaign.CampaignBranch;
import com.crackedgames.craftics.level.campaign.CampaignManager;
import com.crackedgames.craftics.level.campaign.CampaignNode;
import com.crackedgames.craftics.level.campaign.CampaignRegion;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads custom campaign definitions from
 * {@code data/<namespace>/craftics/campaigns/*.json} into the campaign model.
 *
 * <p>Schema (see {@link #parse} for the field-by-field handling):
 *
 * <pre>{@code
 * {
 *   "id": "myaddon:descent",          // required, non-blank
 *   "display_name": "The Descent",    // optional, defaults to id
 *   "regions": [                       // required, non-empty
 *     {
 *       "id": "surface",               // required, non-blank
 *       "display_name": "Surface",     // optional, defaults to region id
 *       "color": "§a",                 // optional, defaults to "§f"
 *       "icon": "☘",                   // optional, defaults to "?"
 *       "map_color": "FF6BBF59",       // optional ARGB hex string, defaults to white
 *       "nodes": [                      // required, non-empty
 *         { "biome": "village" },       // "biome" required
 *         { "biome": "wildwood", "label": "The Wildwood" } // "label" optional
 *       ]
 *     }
 *   ],
 *   "branch": {                         // optional; malformed branch -> treated as linear
 *     "region": "surface",
 *     "swap": ["wildwood", "marsh"]     // exactly 2 entries: two strings (single-biome swap)
 *     // or two arrays of strings for a multi-biome segment swap, e.g.
 *     // "swap": [["desert","jungle","forest"], ["snowy","mountain"]]
 *   }
 * }
 * }</pre>
 *
 * <p>One bad file never aborts the load: any reason to reject a file is logged via
 * {@link CrafticsMod#LOGGER} and {@link #parse} returns {@code null} (the base
 * {@code load} loop then skips it). Recoverable problems - a malformed
 * {@code map_color}, or a malformed {@code branch} - log a warning and fall back to a
 * sane default rather than rejecting the whole campaign.
 *
 * @since 0.2.2
 */
public final class CampaignJsonLoader extends CrafticsDataLoader<Campaign> {

    public CampaignJsonLoader() {
        super("craftics/campaigns", "campaign");
    }

    /** Exposed for unit tests - {@link #parse} is otherwise protected. */
    public Campaign parseForTest(Identifier fileId, JsonObject json) {
        return parse(fileId, json);
    }

    @Override
    protected Campaign parse(Identifier fileId, JsonObject json) {
        return parseCampaign(fileId, json);
    }

    /**
     * Parse one campaign JSON file into a {@link Campaign}, or {@code null} to skip it.
     *
     * <p>Static so it can be unit-tested with Gson + Identifier alone, without
     * bootstrapping Minecraft. The instance {@link #parse(Identifier, JsonObject)}
     * override delegates here. (A separate name is required because Java forbids a static
     * and an instance method sharing the same signature in one class.)
     *
     * @param fileId the resource id of the file, for diagnostics
     * @param json   the parsed JSON object
     * @return the parsed campaign, or {@code null} when the file is unusable
     */
    public static Campaign parseCampaign(Identifier fileId, JsonObject json) {
        try {
            if (json == null) {
                CrafticsMod.LOGGER.warn("Campaign JSON {} is empty - skipping", fileId);
                return null;
            }

            String id = optString(json, "id");
            if (id == null || id.isBlank()) {
                CrafticsMod.LOGGER.warn("Campaign JSON {} missing non-blank 'id' - skipping", fileId);
                return null;
            }

            if (!json.has("regions") || !json.get("regions").isJsonArray()) {
                CrafticsMod.LOGGER.warn("Campaign JSON {} missing 'regions' array - skipping", fileId);
                return null;
            }
            JsonArray regionsArr = json.getAsJsonArray("regions");
            if (regionsArr.isEmpty()) {
                CrafticsMod.LOGGER.warn("Campaign JSON {} has empty 'regions' - skipping", fileId);
                return null;
            }

            Campaign.Builder builder = Campaign.builder(id);
            builder.displayName(optString(json, "display_name"));

            for (JsonElement regionElement : regionsArr) {
                if (!regionElement.isJsonObject()) {
                    CrafticsMod.LOGGER.warn("Campaign {} has a non-object region - skipping campaign", fileId);
                    return null;
                }
                CampaignRegion region = parseRegion(fileId, regionElement.getAsJsonObject());
                if (region == null) {
                    // parseRegion already logged the reason.
                    return null;
                }
                builder.region(region);
            }

            CampaignBranch branch = parseBranch(fileId, json);
            builder.branch(branch);

            try {
                return builder.build();
            } catch (IllegalArgumentException e) {
                CrafticsMod.LOGGER.warn("Campaign {} failed validation: {} - skipping", fileId, e.getMessage());
                return null;
            }
        } catch (RuntimeException e) {
            // Defense-in-depth: any unexpected exception (now or after future edits) becomes a
            // logged skip rather than escaping the parser. The specific paths above still give
            // precise messages; this is just the outer safety net.
            CrafticsMod.LOGGER.warn("Campaign {} could not be parsed: {} - skipping", fileId, e.getMessage());
            return null;
        }
    }

    /** Parse one region object, or {@code null} (with a logged reason) to reject the campaign. */
    private static CampaignRegion parseRegion(Identifier fileId, JsonObject regionJson) {
        String id = optString(regionJson, "id");
        if (id == null || id.isBlank()) {
            CrafticsMod.LOGGER.warn("Campaign {} has a region missing non-blank 'id' - skipping campaign", fileId);
            return null;
        }

        if (!regionJson.has("nodes") || !regionJson.get("nodes").isJsonArray()) {
            CrafticsMod.LOGGER.warn("Campaign {} region '{}' missing 'nodes' array - skipping campaign", fileId, id);
            return null;
        }
        JsonArray nodesArr = regionJson.getAsJsonArray("nodes");
        if (nodesArr.isEmpty()) {
            CrafticsMod.LOGGER.warn("Campaign {} region '{}' has empty 'nodes' - skipping campaign", fileId, id);
            return null;
        }

        CampaignRegion.Builder region = CampaignRegion.builder(id);
        region.displayName(optString(regionJson, "display_name"));

        String color = optString(regionJson, "color");
        if (color != null) {
            region.color(color);
        }
        String icon = optString(regionJson, "icon");
        if (icon != null) {
            region.icon(icon);
        }
        region.mapColor(parseMapColor(fileId, id, optString(regionJson, "map_color")));

        for (JsonElement nodeElement : nodesArr) {
            if (!nodeElement.isJsonObject()) {
                CrafticsMod.LOGGER.warn("Campaign {} region '{}' has a non-object node - skipping campaign", fileId, id);
                return null;
            }
            JsonObject nodeJson = nodeElement.getAsJsonObject();
            String biome = optString(nodeJson, "biome");
            if (biome == null || biome.isBlank()) {
                // A node without a biome is meaningless; reject the whole campaign so the
                // author notices, rather than silently dropping a step.
                CrafticsMod.LOGGER.warn("Campaign {} region '{}' has a node missing non-blank 'biome' - skipping campaign",
                    fileId, id);
                return null;
            }
            String label = optString(nodeJson, "label");
            region.node(CampaignNode.of(biome, label));
        }

        try {
            return region.build();
        } catch (IllegalArgumentException e) {
            CrafticsMod.LOGGER.warn("Campaign {} region '{}' failed validation: {} - skipping campaign",
                fileId, id, e.getMessage());
            return null;
        }
    }

    /**
     * Parse the optional {@code branch}. A malformed branch is logged and treated as no
     * branch (linear) rather than rejecting the campaign.
     *
     * <p>The {@code swap} array must have exactly two elements and accepts two forms:
     *
     * <ul>
     *   <li><b>Single-biome swap</b> - both elements are strings:
     *       {@code "swap": ["wildwood", "marsh"]} -> segmentA={@code [wildwood]},
     *       segmentB={@code [marsh]} (each a length-1 segment). The original, backward-compatible
     *       form.</li>
     *   <li><b>Segment swap</b> - both elements are arrays of strings:
     *       {@code "swap": [["desert","jungle","forest"], ["snowy","mountain"]]} ->
     *       segmentA={@code [desert,jungle,forest]}, segmentB={@code [snowy,mountain]}. Lets two
     *       contiguous multi-biome segments exchange positions.</li>
     * </ul>
     *
     * <p>Anything else - not exactly two elements, a mix of a string and an array, a non-string
     * element inside a segment array, or an empty segment array - is malformed and falls back to
     * linear (warn).
     *
     * @return the branch, or {@code null} when absent or malformed
     */
    private static CampaignBranch parseBranch(Identifier fileId, JsonObject json) {
        if (!json.has("branch")) {
            return null;
        }
        if (!json.get("branch").isJsonObject()) {
            CrafticsMod.LOGGER.warn("Campaign {} 'branch' is not an object - ignoring branch (linear)", fileId);
            return null;
        }
        JsonObject branchJson = json.getAsJsonObject("branch");

        String region = optString(branchJson, "region");
        if (region == null || region.isBlank()) {
            CrafticsMod.LOGGER.warn("Campaign {} branch missing non-blank 'region' - ignoring branch (linear)", fileId);
            return null;
        }

        if (!branchJson.has("swap") || !branchJson.get("swap").isJsonArray()) {
            CrafticsMod.LOGGER.warn("Campaign {} branch missing 'swap' array - ignoring branch (linear)", fileId);
            return null;
        }
        JsonArray swap = branchJson.getAsJsonArray("swap");
        if (swap.size() != 2) {
            CrafticsMod.LOGGER.warn("Campaign {} branch 'swap' must have exactly 2 entries (had {}) - ignoring branch (linear)",
                fileId, swap.size());
            return null;
        }

        JsonElement first = swap.get(0);
        JsonElement second = swap.get(1);
        List<String> segmentA;
        List<String> segmentB;
        if (isJsonString(first) && isJsonString(second)) {
            // Backward-compatible single-biome swap: two strings -> two length-1 segments.
            segmentA = List.of(first.getAsString());
            segmentB = List.of(second.getAsString());
        } else if (first.isJsonArray() && second.isJsonArray()) {
            // Segment swap: two arrays of strings.
            segmentA = parseSegment(fileId, first.getAsJsonArray());
            segmentB = parseSegment(fileId, second.getAsJsonArray());
            if (segmentA == null || segmentB == null) {
                // parseSegment already logged the reason.
                return null;
            }
        } else {
            CrafticsMod.LOGGER.warn("Campaign {} branch 'swap' must be either two strings or two "
                + "arrays of strings - ignoring branch (linear)", fileId);
            return null;
        }

        try {
            return new CampaignBranch(region, segmentA, segmentB);
        } catch (RuntimeException e) {
            CrafticsMod.LOGGER.warn("Campaign {} branch is malformed: {} - ignoring branch (linear)",
                fileId, e.getMessage());
            return null;
        }
    }

    /** True iff {@code element} is a JSON primitive string (not a number/boolean/array/object). */
    private static boolean isJsonString(JsonElement element) {
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
    }

    /**
     * Parse one segment array into an ordered list of biome-id strings, or {@code null} (with a
     * logged reason) when the array is empty or holds a non-string element.
     */
    private static List<String> parseSegment(Identifier fileId, JsonArray segmentArr) {
        if (segmentArr.isEmpty()) {
            CrafticsMod.LOGGER.warn("Campaign {} branch 'swap' has an empty segment - ignoring branch (linear)", fileId);
            return null;
        }
        List<String> ids = new ArrayList<>(segmentArr.size());
        for (JsonElement element : segmentArr) {
            if (!isJsonString(element)) {
                CrafticsMod.LOGGER.warn("Campaign {} branch 'swap' segment has a non-string entry - "
                    + "ignoring branch (linear)", fileId);
                return null;
            }
            ids.add(element.getAsString());
        }
        return ids;
    }

    /**
     * Parse a {@code map_color} ARGB hex string into an int. 8-digit strings are ARGB;
     * 6-digit strings are RGB and get an opaque {@code 0xFF} alpha prepended. Anything
     * malformed (non-hex, missing, or an unexpected length) logs a warning and defaults to
     * opaque white ({@code 0xFFFFFFFF}) rather than rejecting the region.
     *
     * <p>Uses {@link Long#parseLong(String, int)} then narrows to {@code int}: values like
     * {@code FF6BBF59} overflow a signed int via {@code Integer.parseInt}.
     */
    private static int parseMapColor(Identifier fileId, String regionId, String raw) {
        if (raw == null) {
            // Absent is normal - let the region builder default to white, no warning.
            return 0xFFFFFFFF;
        }
        String hex = (raw.startsWith("#") ? raw.substring(1) : raw).trim();
        try {
            long value = Long.parseLong(hex, 16);
            if (hex.length() == 6) {
                // RGB -> opaque ARGB.
                return (int) (0xFF000000L | value);
            }
            if (hex.length() == 8) {
                return (int) value;
            }
            CrafticsMod.LOGGER.warn(
                "Campaign {} region '{}' has 'map_color' \"{}\" of unexpected length - defaulting to white",
                fileId, regionId, raw);
            return 0xFFFFFFFF;
        } catch (NumberFormatException e) {
            CrafticsMod.LOGGER.warn(
                "Campaign {} region '{}' has malformed 'map_color' \"{}\" - defaulting to white",
                fileId, regionId, raw);
            return 0xFFFFFFFF;
        }
    }

    /**
     * A type-safe string getter: returns {@code null} when the key is absent, JSON null, or
     * present but NOT a JSON primitive (i.e. an array or object). Treating composite values
     * as absent lets the caller's required-field check produce the proper "missing/blank"
     * warning instead of letting Gson's {@code getAsString()} throw on a {@code JsonObject}
     * ({@code UnsupportedOperationException}) or a multi-element/empty {@code JsonArray}
     * ({@code IllegalStateException}) and escape the parser. Numbers and booleans still
     * coerce via {@code getAsString} (e.g. {@code "id": 42} -> {@code "42"}).
     */
    private static String optString(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return null;
        }
        JsonElement value = json.get(key);
        if (!value.isJsonPrimitive()) {
            // Arrays / objects are an author typo for a scalar field - treat as absent.
            return null;
        }
        return value.getAsString();
    }

    @Override
    protected void register(Campaign parsed, RegistrationSource source) {
        CampaignManager.register(parsed, source);
    }

    @Override
    protected void clearDatapackEntries() {
        CampaignManager.clearDatapackEntries();
    }
}
