package com.crackedgames.craftics.combat;

import org.jetbrains.annotations.Nullable;

import java.util.function.IntPredicate;

/**
 * Where a party pet was standing on its island when a run collected it, so it can be put back
 * there afterwards instead of beside the island spawn point. This is what lets a player keep
 * animals in pens: the wolf taken out of the kennel goes back into the kennel.
 *
 * <p>Recorded only from a real animal standing in the hub (see
 * {@code HubPetCollector.collectFollowingPets}). An animal tamed during a fight, or a summon
 * adopted as a pet, has never stood anywhere on the island and carries no home; those keep
 * arriving at the island spawn.
 *
 * <p>Held in memory alongside the pet's NBT snapshot and lost with it. The snapshot is not
 * written to disk either, and every way a run can end restores pets through the same structures.
 *
 * @param dimension the island dimension id the spot is in. Every island shares one coordinate
 *                  layout, so the coordinates mean nothing without it
 * @param yaw       the way the animal was facing
 */
public record PetHome(String dimension, double x, double y, double z, float yaw) {

    /** How far up a returning pet may be lifted to clear something built on its spot. */
    public static final int MAX_LIFT = 3;

    /** A home for a real place, or null when the position is not one. */
    @Nullable
    public static PetHome of(@Nullable String dimension, double x, double y, double z, float yaw) {
        if (dimension == null || dimension.isBlank()) return null;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw)) {
            return null;
        }
        return new PetHome(dimension, x, y, z, yaw);
    }

    /**
     * Whether a pet being restored into {@code targetDimension} can be sent to this spot.
     *
     * <p>Only on the island it was recorded on: a party guest's pet is restored onto the
     * leader's island, where the guest's pen coordinates point at somebody else's build or at
     * open air. And never inside arena territory ({@code x >= arenaStartX}): arenas share the
     * island dimension, and a slot there is wiped and rebuilt by the next fight.
     */
    public boolean usableIn(String targetDimension, int arenaStartX) {
        return dimension.equals(targetDimension) && x < arenaStartX;
    }

    /**
     * How many blocks to raise a returning pet so its body fits, or -1 to give the spot up and
     * fall back to the island spawn.
     *
     * <p>The exact spot is used whenever the body fits there, which is every time the pen has not
     * been touched - including a parrot on a fence post or a fish in a pond, which is why this
     * asks only "does it fit" and not "is there a full block to stand on". If something was
     * built on the spot meanwhile, the pet is lifted on top of it, up to {@link #MAX_LIFT}.
     *
     * @param bodyFitsAtLift whether the pet's body is clear of blocks when raised that many blocks
     * @param groundBelow    whether anything at all is under the spot. With nothing there, an
     *                       island floating in the void drops the pet out of the world
     */
    public static int liftToFit(IntPredicate bodyFitsAtLift, boolean groundBelow) {
        if (!groundBelow) return -1;
        for (int lift = 0; lift <= MAX_LIFT; lift++) {
            if (bodyFitsAtLift.test(lift)) return lift;
        }
        return -1;
    }
}
