package com.crackedgames.craftics.client;

import net.minecraft.util.math.MathHelper;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Client-side allowlist of people who get cosmetic flair: a floating title
 * above their head ({@link TesterLabelRenderer}), a signature colored highlight
 * in the combat turn-order panel ({@code CombatHudOverlay}), and a credit entry
 * in the guide book's "Hall of Testers" category.
 *
 * <p>Three tiers ({@link Rank}):
 * <ul>
 *   <li>{@code CREATOR} — the fanciest treatment: an animated rainbow color and
 *       bold text. (There's just one: __Egg__.)</li>
 *   <li>{@code HELPER} — a special solid signature color.</li>
 *   <li>{@code TESTER} — a solid signature color.</li>
 * </ul>
 *
 * <p>The list is hardcoded and identical on every client, so no server sync is
 * needed — each client looks up any player it renders by username. To add
 * someone, drop a {@link #register} line in the static block. All perks are
 * purely cosmetic — nothing here touches combat balance.
 */
public final class TesterRegistry {

    /** Flair tier, in display order (Creator first). */
    public enum Rank { CREATOR, HELPER, TESTER }

    /** Shared signature color for ALL regular testers (Rank.TESTER). */
    public static final int TESTER_COLOR = 0xFF55C8FF; // sky blue

    /**
     * A person and their cosmetic profile. {@code color} is 0xAARRGGBB used for
     * {@code HELPER}/{@code TESTER}; the {@code CREATOR} color is animated (see
     * {@link #colorOf}) and only falls back to {@code color} where animation
     * isn't possible (e.g. the guide book text).
     */
    public record Tester(String name, String title, int color, Rank rank) {}

    /** Keyed by lowercased username for case-insensitive lookup; insertion order preserved. */
    private static final Map<String, Tester> TESTERS = new LinkedHashMap<>();

    static {
        // name (exact display case), floating title, signature color (0xAARRGGBB), rank
        register("__Egg__",          "✦ Creator of Craftics ✦", 0xFFE8B637, Rank.CREATOR); // rainbow + bold
        register("ChickenSizedKiwi", "✦ Pwincess ✦",        0xFF8A9A5B, Rank.HELPER);  // army green
        register("Jpkrus",           "✦ Playtester",            TESTER_COLOR, Rank.TESTER);
        register("Tintartie",        "✦ Playtester",            TESTER_COLOR, Rank.TESTER);
        register("TripleExM",        "✦ Playtester",            TESTER_COLOR, Rank.TESTER);
        
        // Add more here (all testers share TESTER_COLOR regardless of the value passed):
        // register("SomeName", "✦ Playtester", TESTER_COLOR, Rank.TESTER);
    }

    private TesterRegistry() {}

    private static void register(String name, String title, int color, Rank rank) {
        TESTERS.put(name.toLowerCase(Locale.ROOT), new Tester(name, title, color, rank));
    }

    /** True if the given username belongs to a registered person (case-insensitive). */
    public static boolean isTester(String username) {
        return username != null && TESTERS.containsKey(username.toLowerCase(Locale.ROOT));
    }

    /** The profile for a username, or {@code null} if not registered. */
    public static Tester get(String username) {
        return username == null ? null : TESTERS.get(username.toLowerCase(Locale.ROOT));
    }

    /** All registered people, in registration order (for the Hall of Testers page). */
    public static Collection<Tester> all() {
        return TESTERS.values();
    }

    /**
     * The color to draw right now: an animated rainbow for {@code CREATOR},
     * the static signature color otherwise. Called every frame by the renderers.
     */
    public static int colorOf(Tester t) {
        if (t == null) return 0xFFFFFFFF;
        return switch (t.rank()) {
            case CREATOR -> {
                float hue = (System.currentTimeMillis() % 2600L) / 2600.0f;
                yield 0xFF000000 | (MathHelper.hsvToRgb(hue, 0.6f, 1.0f) & 0x00FFFFFF);
            }
            case TESTER -> TESTER_COLOR; // all regular testers share one color
            case HELPER -> t.color();
        };
    }

    /** True if this person's flair should render bold (creator only). */
    public static boolean isBold(Tester t) {
        return t != null && t.rank() == Rank.CREATOR;
    }
}
