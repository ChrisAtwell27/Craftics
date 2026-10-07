package com.crackedgames.craftics.combat;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

/**
 * The marks a party pet carries on its entity, and the rule that sends it back to its owner.
 *
 * <p>A party plays on its leader's island, so a guest's pets are parked there between runs - the
 * next run can only collect animals whose chunks are loaded, and that is where the party stands.
 * Two things were missing. Nothing said whose an animal was once it stood on somebody else's
 * island, and nothing happened when its owner stopped being in that party, so it stayed there
 * for good.
 *
 * <p>The marks are vanilla command tags. They are saved with the entity, so they survive a
 * restart and an unloaded chunk with no save format of Craftics' own, and reading them is the
 * same call on every Minecraft version.
 */
public final class PetTags {

    private static final String OWNER_PREFIX = "craftics_pet_owner:";
    private static final String HOME_PREFIX = "craftics_pet_home:";

    private PetTags() {}

    /** Whose animal this is. */
    public static String ownerTag(UUID owner) {
        return OWNER_PREFIX + owner;
    }

    /** The owner named on an entity's tags, or null when it carries none that parses. */
    @Nullable
    public static UUID ownerOf(Collection<String> tags) {
        for (String tag : tags) {
            if (!tag.startsWith(OWNER_PREFIX)) continue;
            try {
                return UUID.fromString(tag.substring(OWNER_PREFIX.length()));
            } catch (IllegalArgumentException malformed) {
                return null;
            }
        }
        return null;
    }

    /**
     * Where this animal really lives, carried while it is parked somewhere else. The dimension
     * comes first and the rest is split from the right, because a dimension id holds colons and
     * slashes of its own but never a pipe.
     */
    public static String homeTag(PetHome home) {
        return HOME_PREFIX + home.dimension() + "|" + home.x() + "|" + home.y() + "|" + home.z()
            + "|" + home.yaw();
    }

    /** The home named on an entity's tags, or null when it carries none that parses. */
    @Nullable
    public static PetHome homeOf(Collection<String> tags) {
        for (String tag : tags) {
            if (!tag.startsWith(HOME_PREFIX)) continue;
            String[] parts = tag.substring(HOME_PREFIX.length()).split("\\|");
            if (parts.length != 5) return null;
            try {
                return PetHome.of(parts[0], Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]), Float.parseFloat(parts[4]));
            } catch (NumberFormatException malformed) {
                return null;
            }
        }
        return null;
    }

    /** Whether {@code tag} is one of the marks above, so stale ones can be cleared before re-marking. */
    public static boolean isPetTag(String tag) {
        return tag.startsWith(OWNER_PREFIX) || tag.startsWith(HOME_PREFIX);
    }

    /**
     * Whether a pet standing on {@code islandOwner}'s island should be sent to its own owner's.
     *
     * <p>It belongs where it is on its owner's own island, and on the island its owner currently
     * plays on (their party leader's). Anywhere else it has been left behind: the owner left or
     * was kicked, the party disbanded, or the leader changed and the party moved islands. One
     * rule covers all four, which is why none of them is checked for by name.
     *
     * @param petOwner          whose animal it is, or null if nobody's
     * @param islandOwner       whose island it is standing on, or null when not on an island
     * @param ownersPartyIsland the island {@code petOwner} plays on right now: their party
     *                          leader's, or their own when they are in no party
     */
    public static boolean stranded(@Nullable UUID petOwner, @Nullable UUID islandOwner,
                                   @Nullable UUID ownersPartyIsland) {
        if (petOwner == null || islandOwner == null) return false;
        return !islandOwner.equals(petOwner) && !islandOwner.equals(ownersPartyIsland);
    }

    /**
     * The home to remember for a pet being collected for a run.
     *
     * <p>On its owner's own island, home is where it stands now - whatever an old mark says, the
     * player may have moved it to a new pen since. Parked on somebody else's island, where it
     * stands is not home at all: it keeps the home it was carrying, or has none.
     */
    @Nullable
    public static PetHome homeAtPickup(boolean onOwnersIsland, @Nullable PetHome here,
                                       @Nullable PetHome tagged) {
        return onOwnersIsland ? here : tagged;
    }
}
