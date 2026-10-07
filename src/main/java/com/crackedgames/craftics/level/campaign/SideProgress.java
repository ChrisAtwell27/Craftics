package com.crackedgames.craftics.level.campaign;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The one string an island keeps its side-region progress in: {@code "aether=2,other=1"},
 * region id to biomes cleared.
 *
 * <p>A string rather than a map because it travels - into the save, and across to the level
 * select screen - and one field that needs no codec of its own is one less thing to keep in
 * step on four Minecraft versions. Everything that reads or writes it goes through here, so
 * the format has exactly one definition.
 *
 * <p>Region ids are namespaced ({@code "mymod:sky"}), so {@code :} is ordinary text here; the
 * separators are {@code ,} and the LAST {@code =} of an entry.
 */
public final class SideProgress {

    private SideProgress() {}

    /** Biomes cleared in {@code regionId}, or 0 for a region with no entry. */
    public static int get(String encoded, String regionId) {
        Integer cleared = parse(encoded).get(regionId);
        return cleared != null ? cleared : 0;
    }

    /** {@code encoded} with {@code regionId} set to {@code cleared}; other regions untouched. */
    public static String with(String encoded, String regionId, int cleared) {
        if (regionId == null || regionId.isEmpty()) return encoded == null ? "" : encoded;
        Map<String, Integer> all = parse(encoded);
        if (cleared <= 0) all.remove(regionId);
        else all.put(regionId, cleared);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : all.entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    /** Malformed entries are dropped rather than trusted: a bad save costs progress, not a crash. */
    private static Map<String, Integer> parse(String encoded) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) return out;
        for (String entry : encoded.split(",")) {
            int eq = entry.lastIndexOf('=');
            if (eq <= 0 || eq == entry.length() - 1) continue;
            try {
                int cleared = Integer.parseInt(entry.substring(eq + 1).trim());
                if (cleared > 0) out.put(entry.substring(0, eq).trim(), cleared);
            } catch (NumberFormatException ignored) {
                // skip this entry
            }
        }
        return out;
    }
}
