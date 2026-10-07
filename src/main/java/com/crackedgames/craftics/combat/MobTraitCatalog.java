package com.crackedgames.craftics.combat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wire format for the bestiary's trait table: bestiary entry name to the traits that mob
 * carries.
 *
 * <p>{@code "Zombie=undead|The Revenant=infallible,indomitable,undead"}. Entries are joined
 * with {@code |} and split from their trait list by the last {@code =}, the same two
 * separators the guide book sync already reserves. A mob with no traits is simply absent, so
 * a missing name and an empty list mean the same thing to the reader.
 *
 * <p>Pure string work, kept apart from the code that gathers the mobs so it can be tested
 * without a registry.
 */
public final class MobTraitCatalog {

    private MobTraitCatalog() {}

    public static String encode(Map<String, List<MobTrait>> byName) {
        StringBuilder sb = new StringBuilder();
        if (byName == null) return "";
        for (Map.Entry<String, List<MobTrait>> e : byName.entrySet()) {
            String name = e.getKey();
            String ids = MobTraits.encode(e.getValue());
            if (name == null || name.isEmpty() || ids.isEmpty()) continue;
            // A name carrying a separator would corrupt every entry after it. No bestiary
            // name does; one that did is dropped rather than trusted.
            if (name.indexOf('|') >= 0 || name.indexOf('=') >= 0) continue;
            if (sb.length() > 0) sb.append('|');
            sb.append(name).append('=').append(ids);
        }
        return sb.toString();
    }

    public static Map<String, List<MobTrait>> decode(String encoded) {
        Map<String, List<MobTrait>> out = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) return out;
        for (String entry : encoded.split("\\|")) {
            int eq = entry.lastIndexOf('=');
            if (eq <= 0) continue;
            List<MobTrait> traits = MobTraits.decode(entry.substring(eq + 1));
            if (!traits.isEmpty()) out.put(entry.substring(0, eq), traits);
        }
        return out;
    }

    /** {@code a} and {@code b} combined, without duplicates, in display order. */
    public static List<MobTrait> union(Collection<MobTrait> a, Collection<MobTrait> b) {
        List<MobTrait> out = new ArrayList<>();
        for (MobTrait t : MobTraits.all()) {
            if ((a != null && a.contains(t)) || (b != null && b.contains(t))) out.add(t);
        }
        return out;
    }
}
