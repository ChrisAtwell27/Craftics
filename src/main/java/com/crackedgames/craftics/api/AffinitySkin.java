package com.crackedgames.craftics.api;

import org.jetbrains.annotations.Nullable;

/**
 * A rename and re-icon of one of Craftics' eight affinities.
 *
 * <p>The eight affinities are fixed in number on purpose. They are the axes a player spends
 * level-up points on, and the levelling and respec screens are laid out for exactly eight;
 * a mod that added ten more would not get a bigger screen, it would get an unreadable one.
 * What a total-conversion mod actually needs is not more axes but different ones - the same
 * eight slots wearing its own names.
 *
 * <p>A skin replaces everything the player is ever shown about that affinity: its name, its
 * icon, its description, and the name of the damage type that scales from it. Nothing about
 * the mechanics changes - a reskinned Slashing affinity still boosts Slashing weapons and
 * still grants the sweep chance. Only the words change.
 *
 * <pre>{@code
 * CrafticsAPI.reskinAffinity(PlayerProgression.Affinity.SLASHING,
 *     AffinitySkin.of("Physical", "§c⚔", "+3 dmg to physical moves"));
 * }</pre>
 *
 * <p>Any field left null keeps Craftics' original, so a skin can rename without supplying a
 * new icon.
 *
 * <p><b>Register from a {@link CrafticsAddon}</b>, which runs in common initialization, so
 * both the server and the client learn the skin. Registering from a server-only entrypoint
 * would rename the chat messages and leave every screen showing the old name.
 *
 * @param displayName  new name, or null to keep the original
 * @param icon         new icon (a color code plus a glyph), or null to keep the original
 * @param description  new one-line description for the level-up and respec screens, or null
 * @since 0.3.9
 */
public record AffinitySkin(@Nullable String displayName,
                           @Nullable String icon,
                           @Nullable String description) {

    /** A skin replacing all three. */
    public static AffinitySkin of(String displayName, String icon, String description) {
        return new AffinitySkin(displayName, icon, description);
    }

    /** A skin replacing only the name. */
    public static AffinitySkin named(String displayName) {
        return new AffinitySkin(displayName, null, null);
    }
}
