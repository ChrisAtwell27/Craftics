package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.AffinitySkin;
import com.crackedgames.craftics.combat.DamageType;
import com.crackedgames.craftics.combat.PlayerProgression;

import java.util.EnumMap;
import java.util.Map;

/**
 * Renames and re-icons for the eight affinities, and for the damage types that scale from
 * them.
 *
 * <p><b>Everything Craftics shows a player about an affinity or a damage type reads through
 * this class.</b> That is the whole contract: one registration renames the level-up screen,
 * the respec screen, the infinite-mode class picker, the damage-type panel, weapon
 * tooltips, and the chat lines for gaining a point. If a display site read the enum field
 * directly it would be the one place still showing the old name, which is worse than not
 * supporting reskins at all.
 *
 * <p>Damage types follow their affinity because the two are one-to-one and a player sees
 * both: renaming the Slashing affinity to "Physical" while weapon tooltips still say
 * "Slashing damage" would read as a bug, not a theme.
 *
 * <p>See {@link AffinitySkin} for why the eight slots are reskinnable rather than
 * extensible.
 *
 * @since 0.3.9
 */
public final class AffinitySkinRegistry {

    private AffinitySkinRegistry() {}

    private static final Map<PlayerProgression.Affinity, AffinitySkin> SKINS =
        new EnumMap<>(PlayerProgression.Affinity.class);

    // ── Registration ─────────────────────────────────────────────────────────

    /** Reskin one affinity. Registering the same affinity again replaces the skin. */
    public static void reskin(PlayerProgression.Affinity affinity, AffinitySkin skin) {
        if (affinity == null) return;
        if (skin == null) {
            SKINS.remove(affinity);
            return;
        }
        SKINS.put(affinity, skin);
    }

    /** True when anything has been reskinned. Lets display code skip the lookup. */
    public static boolean isEmpty() {
        return SKINS.isEmpty();
    }

    // ── Affinity display ─────────────────────────────────────────────────────

    /** The name to show for this affinity. */
    public static String nameOf(PlayerProgression.Affinity affinity) {
        if (affinity == null) return "";
        AffinitySkin s = SKINS.get(affinity);
        return (s != null && s.displayName() != null) ? s.displayName() : affinity.displayName;
    }

    /** The icon to show for this affinity. */
    public static String iconOf(PlayerProgression.Affinity affinity) {
        if (affinity == null) return "";
        AffinitySkin s = SKINS.get(affinity);
        return (s != null && s.icon() != null) ? s.icon() : affinity.icon;
    }

    /** The description to show for this affinity. */
    public static String descriptionOf(PlayerProgression.Affinity affinity) {
        if (affinity == null) return "";
        AffinitySkin s = SKINS.get(affinity);
        return (s != null && s.description() != null) ? s.description() : affinity.description;
    }

    // ── Damage type display, following the affinity it scales from ───────────

    /**
     * The name to show for this damage type.
     *
     * <p>Resolved through the affinity it maps to, so one reskin covers both. Falls back to
     * the damage type's own name when nothing is registered, which is every vanilla setup.
     */
    public static String nameOf(DamageType type) {
        if (type == null) return "";
        if (SKINS.isEmpty()) return type.displayName;
        PlayerProgression.Affinity a = DamageType.affinityOf(type);
        AffinitySkin s = a != null ? SKINS.get(a) : null;
        return (s != null && s.displayName() != null) ? s.displayName() : type.displayName;
    }

    /**
     * The icon to show for this damage type, taken from its affinity's skin.
     *
     * <p>Damage types have no icon of their own - the panel that draws them borrows the
     * affinity's. Returns null when there is no skinned icon, so the caller keeps whatever
     * it did before.
     */
    public static String iconOrNull(DamageType type) {
        if (type == null || SKINS.isEmpty()) return null;
        PlayerProgression.Affinity a = DamageType.affinityOf(type);
        AffinitySkin s = a != null ? SKINS.get(a) : null;
        return s != null ? s.icon() : null;
    }

    /** Clear every skin. Test hook. */
    public static void clear() {
        SKINS.clear();
    }
}
