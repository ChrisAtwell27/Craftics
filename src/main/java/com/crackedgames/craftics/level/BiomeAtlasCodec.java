package com.crackedgames.craftics.level;

import java.util.ArrayList;
import java.util.List;

/**
 * Wire format for the biome atlas: what the guide book shows about each biome.
 *
 * <p>A flat delimited string rather than a structured packet codec. That matches how Craftics
 * already ships list-shaped data to the client ({@code discoveredBiomes}, the guide book's own
 * unlock set, the enemy type map), and it dodges the real cost of the alternative: collection
 * and nested-record codecs moved between 1.21.1 and 1.21.5, so a structured codec would need
 * version branches for something that is, in the end, a list of item ids and numbers.
 *
 * <p>The upside of the trade is that encode and decode are pure functions over strings, so the
 * format is testable end to end without a server, a client, or a registry. Which matters,
 * because delimited formats fail in exactly one way - a field containing a delimiter - and that
 * is a case you want a test for rather than a bug report.
 *
 * <p>Delimiters, outermost first: {@code ;} between biomes, {@code |} between a biome's fields,
 * {@code ,} between list items, {@code *} between an item id and its weight. The per-mob drop
 * field nests one level deeper again: {@code ~} between mobs and {@code =} between a mob and its
 * drop list. An id can contain a colon, which is why no separator is one. Free text is scrubbed
 * of all six on the way out - see {@link #clean} - so a datapack cannot corrupt the stream by
 * naming a biome "Fire; Ice".
 *
 * <p><b>Adding a field</b> means appending it to {@link Entry}, to {@link #encode}, and to
 * {@link #decode}'s tail, and raising {@link #FIELD_COUNT}. Append only: {@code decode} accepts
 * anything with at least the expected number of fields, so a field added in the middle silently
 * shifts every field after it.
 */
public final class BiomeAtlasCodec {
    private BiomeAtlasCodec() {}

    private static final String BIOME_SEP = ";";
    private static final String FIELD_SEP = "|";
    private static final String LIST_SEP = ",";
    private static final String WEIGHT_SEP = "*";
    private static final String MOB_SEP = "~";
    private static final String MOB_KV = "=";

    /** Fields per biome. A chunk with fewer is malformed and gets dropped. */
    private static final int FIELD_COUNT = 13;

    /** One weighted drop: an item or enchantment id and its share of the pool. */
    public record Drop(String id, int weight) {}

    /** What one enemy type drops when it dies here. */
    public record MobDrops(String mobId, List<Drop> drops) {}

    /**
     * Everything the atlas knows about one biome.
     *
     * <p>Every field is read straight off the live {@link BiomeTemplate} (and, for
     * {@code mobDrops}, off the live per-mob drop tables) at send time. Nothing here is authored
     * per biome, which is the point: retuning a loot weight, swapping a mob pool or adding a
     * whole biome changes the guide book with it, and there is no second copy of the content to
     * forget to update.
     *
     * @param loot the biome's own pool, rolled once when a level is cleared
     * @param mobDrops per-enemy tables, rolled once per kill. A completely separate source from
     *                 {@code loot}: their weights are not comparable and must not be merged into
     *                 one percentage, because they answer different questions.
     * @param discovered whether this island has been there. Undiscovered biomes are still sent,
     *                   with their contents emptied out - the client needs to know the biome
     *                   exists in order to show it as an unexplored page, and must not be told
     *                   what is in it, since a client that holds the answer can display it.
     * @param effectStartLevel 1-based level within the biome at which {@code effectId} kicks in,
     *                   or 0 for a biome with no environmental effect.
     */
    public record Entry(String biomeId, String displayName, int levelCount, boolean discovered,
                        boolean nightLevel, String bossId, List<String> hostileIds,
                        List<String> passiveIds, List<Drop> loot, List<Drop> enchants,
                        String effectId, int effectStartLevel, List<MobDrops> mobDrops) {}

    /** Strip every delimiter from free text. Cheaper than escaping and impossible to get wrong. */
    static String clean(String raw) {
        if (raw == null) return "";
        return raw.replace(BIOME_SEP, " ").replace(FIELD_SEP, " ")
                  .replace(LIST_SEP, " ").replace(WEIGHT_SEP, " ")
                  .replace(MOB_SEP, " ").replace(MOB_KV, " ").trim();
    }

    public static String encode(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            if (sb.length() > 0) sb.append(BIOME_SEP);
            sb.append(clean(e.biomeId())).append(FIELD_SEP)
              .append(clean(e.displayName())).append(FIELD_SEP)
              .append(e.levelCount()).append(FIELD_SEP)
              .append(e.discovered() ? 1 : 0).append(FIELD_SEP)
              .append(e.nightLevel() ? 1 : 0).append(FIELD_SEP)
              .append(clean(e.bossId())).append(FIELD_SEP)
              .append(joinIds(e.hostileIds())).append(FIELD_SEP)
              .append(joinIds(e.passiveIds())).append(FIELD_SEP)
              .append(joinDrops(e.loot())).append(FIELD_SEP)
              .append(joinDrops(e.enchants())).append(FIELD_SEP)
              .append(clean(e.effectId())).append(FIELD_SEP)
              .append(e.effectStartLevel()).append(FIELD_SEP)
              .append(joinMobDrops(e.mobDrops()));
        }
        return sb.toString();
    }

    /**
     * Parse an encoded atlas. Malformed input yields fewer entries, never an exception: this is
     * decoded inside a packet handler, where a throw is a disconnect.
     */
    public static List<Entry> decode(String encoded) {
        List<Entry> out = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return out;
        for (String chunk : encoded.split(java.util.regex.Pattern.quote(BIOME_SEP))) {
            if (chunk.isEmpty()) continue;
            String[] f = chunk.split(java.util.regex.Pattern.quote(FIELD_SEP), -1);
            if (f.length < FIELD_COUNT) continue;
            if (f[0].isEmpty()) continue;
            out.add(new Entry(
                f[0], f[1], parseInt(f[2]), "1".equals(f[3]), "1".equals(f[4]),
                f[5], splitIds(f[6]), splitIds(f[7]),
                splitDrops(f[8]), splitDrops(f[9]), f[10], parseInt(f[11]),
                splitMobDrops(f[12])));
        }
        return out;
    }

    private static String joinIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            String c = clean(id);
            if (c.isEmpty()) continue;
            if (sb.length() > 0) sb.append(LIST_SEP);
            sb.append(c);
        }
        return sb.toString();
    }

    private static String joinDrops(List<Drop> drops) {
        if (drops == null || drops.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Drop d : drops) {
            String c = clean(d.id());
            if (c.isEmpty()) continue;
            if (sb.length() > 0) sb.append(LIST_SEP);
            sb.append(c).append(WEIGHT_SEP).append(d.weight());
        }
        return sb.toString();
    }

    private static String joinMobDrops(List<MobDrops> mobs) {
        if (mobs == null || mobs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (MobDrops m : mobs) {
            String id = clean(m.mobId());
            if (id.isEmpty()) continue;
            String drops = joinDrops(m.drops());
            if (drops.isEmpty()) continue; // a mob that drops nothing is not worth a row
            if (sb.length() > 0) sb.append(MOB_SEP);
            sb.append(id).append(MOB_KV).append(drops);
        }
        return sb.toString();
    }

    private static List<String> splitIds(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String s : raw.split(java.util.regex.Pattern.quote(LIST_SEP))) {
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private static List<Drop> splitDrops(String raw) {
        List<Drop> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String s : raw.split(java.util.regex.Pattern.quote(LIST_SEP))) {
            if (s.isEmpty()) continue;
            int at = s.lastIndexOf(WEIGHT_SEP);
            if (at <= 0) continue;
            out.add(new Drop(s.substring(0, at), parseInt(s.substring(at + 1))));
        }
        return out;
    }

    private static List<MobDrops> splitMobDrops(String raw) {
        List<MobDrops> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String s : raw.split(java.util.regex.Pattern.quote(MOB_SEP))) {
            if (s.isEmpty()) continue;
            int at = s.indexOf(MOB_KV);
            if (at <= 0) continue;
            List<Drop> drops = splitDrops(s.substring(at + 1));
            if (drops.isEmpty()) continue;
            out.add(new MobDrops(s.substring(0, at), drops));
        }
        return out;
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }
}
