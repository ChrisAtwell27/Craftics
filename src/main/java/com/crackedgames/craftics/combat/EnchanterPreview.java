package com.crackedgames.craftics.combat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The enchanter's shortlist: a few things an enhancement might turn out to be, exactly one of
 * which is true.
 *
 * <p>The enchanter used to be a blind roll. Handing a weapon over and getting Dull back on it
 * feels like the game cheated, not like a gamble that went badly, because nothing ever told the
 * player a bad outcome was on the table. A shortlist fixes that without removing the risk: the
 * player sees Dull sitting among the possibilities before they commit, so the same result reads
 * as a bet they took rather than something done to them.
 *
 * <p>Two properties matter and both are the caller's to preserve:
 *
 * <ul>
 *   <li><b>The real outcome is always on the list.</b> A shortlist that can miss is worse than no
 *       shortlist, because the player learns to distrust it. The roll is therefore decided up
 *       front and the list built around it, rather than the list being a guess at a roll that has
 *       not happened yet.</li>
 *   <li><b>Its position carries no information.</b> The real entry is placed at a random index. If
 *       it were always first, or always last, the shortlist would be a spoiler rather than a
 *       warning.</li>
 * </ul>
 *
 * <p>Deliberately free of Minecraft types: the decoy selection is the part with the interesting
 * failure modes, and this way a test can reach it.
 */
public final class EnchanterPreview {

    /** How many possibilities to show. Enough to be a real doubt, few enough to read at a glance. */
    public static final int OPTION_COUNT = 3;

    private EnchanterPreview() {}

    /**
     * Build the shortlist.
     *
     * @param real  the outcome that will actually be applied, already formatted for display
     * @param pool  every other outcome that could plausibly have come up, formatted the same way;
     *              may contain {@code real} and may contain duplicates
     * @param count how many entries to aim for, including the real one
     * @param seed  fixes the decoys and the real entry's position, so repeated hovers agree
     * @return the shortlist, always containing {@code real} exactly once
     */
    public static List<String> shortlist(String real, List<String> pool, int count, long seed) {
        List<String> out = new ArrayList<>();
        if (real == null || real.isEmpty()) return out;
        if (count < 1) count = 1;

        // Distinct, and never the real entry twice - a repeat would quietly narrow the odds the
        // player is reading, and two identical lines look like a bug besides.
        Set<String> candidates = new LinkedHashSet<>();
        if (pool != null) {
            for (String p : pool) {
                if (p != null && !p.isEmpty() && !p.equals(real)) candidates.add(p);
            }
        }

        Random rng = new Random(seed);
        List<String> decoys = new ArrayList<>(candidates);
        // Shuffle then take, rather than sampling with retries: with a pool barely larger than
        // the shortlist, retry-sampling can spin for a long time on the last slot.
        java.util.Collections.shuffle(decoys, rng);
        int wanted = Math.min(count - 1, decoys.size());
        out.addAll(decoys.subList(0, Math.max(0, wanted)));

        // The real entry goes in at a random index, INCLUDING the end, hence size() + 1.
        out.add(rng.nextInt(out.size() + 1), real);
        return out;
    }

    /** {@link #shortlist} at the standard size. */
    public static List<String> shortlist(String real, List<String> pool, long seed) {
        return shortlist(real, pool, OPTION_COUNT, seed);
    }

    /**
     * An enchantment as the player should read it: {@code "Fire Aspect II"}.
     *
     * <p>Levels are Roman because that is how Minecraft writes them everywhere else, and a
     * shortlist that spelled them differently from the item tooltip would read as a different
     * kind of thing entirely.
     */
    public static String label(String enchantKey, int level) {
        if (enchantKey == null || enchantKey.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String word : enchantKey.split("_")) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        // Level I is left off single-level enchantments the same way vanilla does.
        if (level > 1) sb.append(' ').append(roman(level));
        return sb.toString();
    }

    /** A trim as the player should read it: {@code "Dune trim in Copper"}. */
    public static String trimLabel(String pattern, String material) {
        return capitalize(pattern) + " trim in " + capitalize(material);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Roman numerals for enchantment levels.
     *
     * <p>Only ever asked for 1 to 5 in practice; anything outside that comes back as digits rather
     * than a wrong numeral, since a shortlist is not worth a crash or a lie.
     */
    public static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }
}
