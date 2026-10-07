package com.crackedgames.craftics.combat;

/**
 * One permanent characteristic of a mob, shown as a pill on the inspect panel and in the
 * bestiary: "Undead", "Immovable", "Toxic".
 *
 * <p>A trait is a LABEL for a rule the combat code already enforces, never the rule itself.
 * Nothing in the fight asks whether a mob "has" a trait; {@link MobTraits} works the list out
 * from the same flags and tables the fight reads, so a pill cannot promise something the mob
 * does not do.
 *
 * @param id          wire and registry key, lowercase with no {@code ,} {@code ;} {@code |} or
 *                    {@code =} (it travels inside the enemy sync string)
 * @param name        what the pill says
 * @param description one sentence shown when the pill is hovered
 * @param polarity    which way the trait cuts <em>for the mob carrying it</em>
 */
public record MobTrait(String id, String name, String description, Polarity polarity) {

    /** Who a trait is good for, seen from the mob's side of the grid. */
    public enum Polarity {
        /** Helps the mob: drawn green. */
        POSITIVE,
        /** Hurts the mob, which makes it an opening for the player: drawn red. */
        NEGATIVE,
        /** Describes the mob without favouring either side: drawn grey. */
        NEUTRAL
    }

    public MobTrait {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("MobTrait requires a non-blank id");
        }
        for (int i = 0; i < id.length(); i++) {
            if (",;|=".indexOf(id.charAt(i)) >= 0) {
                throw new IllegalArgumentException(
                    "MobTrait id '" + id + "' contains a sync separator");
            }
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("MobTrait " + id + " requires a name");
        }
        if (description == null) description = "";
        if (polarity == null) polarity = Polarity.NEUTRAL;
    }
}
