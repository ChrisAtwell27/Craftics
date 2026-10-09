package com.crackedgames.craftics.level;

/**
 * Where a biome's arena schematics are looked up, worked out from its id.
 *
 * <p>A built-in biome has a bare id and keeps its arenas under Craftics' own namespace:
 * {@code plains} is {@code data/craftics/arenas/plains/}. An addon biome has a namespaced id and
 * keeps them under its own: {@code mymod:cavern} is {@code data/mymod/arenas/cavern/}. That is
 * the same rule biome card art follows, and for the same reason - two addons that both call a
 * biome {@code cavern} cannot overwrite each other.
 *
 * <p>It is also the only rule that can work. The id used to be glued straight into a
 * {@code craftics:} resource path, and a colon is not legal there, so every namespaced biome
 * threw the moment its arena was built.
 *
 * <p>Pure string handling with no Minecraft types, so {@code ArenaPathsTest} can pin it down.
 */
public final class ArenaPaths {

    private ArenaPaths() {}

    /** The namespace a biome with a bare id belongs to. */
    public static final String DEFAULT_NAMESPACE = "craftics";

    /**
     * @param namespace the resource namespace the schematics live in
     * @param folder    the biome's folder under {@code arenas/}, which keeps the slash of a
     *                  sub-biome id such as {@code forest/pale_garden}
     */
    public record Location(String namespace, String folder) {

        /** True for an addon biome, whose arenas are outside Craftics' own namespace. */
        public boolean namespaced() {
            return !DEFAULT_NAMESPACE.equals(namespace);
        }

        /** Resource path of one file in the biome's folder, e.g. {@code arenas/plains/1.schem}. */
        public String file(String fileName) {
            return "arenas/" + folder + "/" + fileName;
        }

        /** Resource path of a sub-biome's single schematic, e.g. {@code arenas/forest/pale_garden.schem}. */
        public String singleFile() {
            return "arenas/" + folder + ".schem";
        }
    }

    /**
     * Resolve a biome id to where its arenas live.
     *
     * @return the location, or null when the id cannot name a file. Null rather than an
     *         exception because the caller has a perfectly good answer for "no schematic":
     *         build a generated arena.
     */
    public static Location of(String biomeId) {
        if (biomeId == null || biomeId.isBlank()) return null;

        String namespace = DEFAULT_NAMESPACE;
        String folder = biomeId;
        int colon = biomeId.indexOf(':');
        if (colon >= 0) {
            namespace = biomeId.substring(0, colon);
            folder = biomeId.substring(colon + 1);
        }
        if (!isNamespace(namespace) || !isFolder(folder)) return null;
        return new Location(namespace, folder);
    }

    private static boolean isNamespace(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!isIdChar(c)) return false;
        }
        return true;
    }

    /**
     * A folder is one or more plain segments joined by single slashes. Segments of dots are
     * refused outright: ids come from datapack JSON and end up in paths on disk, and
     * {@code ../} must not be a way out of the arena folder.
     */
    private static boolean isFolder(String s) {
        if (s.isEmpty()) return false;
        for (String segment : s.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) return false;
            for (int i = 0; i < segment.length(); i++) {
                if (!isIdChar(segment.charAt(i))) return false;
            }
        }
        return true;
    }

    private static boolean isIdChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.';
    }
}
