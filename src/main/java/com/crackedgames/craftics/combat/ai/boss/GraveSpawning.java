package com.crackedgames.craftics.combat.ai.boss;

/**
 * The Revenant's grave-driven spawn cap.
 *
 * <p>Living graves cap living zombies, the same shape as the Broodmother's egg sacs capping
 * spiders. It is self-balancing: three graves is a smaller stream than five, and destroying one
 * permanently narrows it. Phase 2 tears out two more graves, so the ceiling jumps back up right
 * when the player thought they had cut it off.
 *
 * <p>Minecraft-free so the ruleset is unit-testable with no bootstrap.
 */
public final class GraveSpawning {

    /**
     * A safety net against runaway spawning, NOT a target. If a normal fight regularly reaches
     * this, the spawn rate is mistuned rather than the cap.
     */
    private static final int ABSOLUTE_CAP = 10;

    private GraveSpawning() {}

    /** Living zombies allowed: one per living grave, under an absolute ceiling that grows with
     *  party size. */
    public static int zombieCap(int gravesAlive, int playerCount) {
        int fromGraves = Math.max(0, gravesAlive);
        int ceiling = ABSOLUTE_CAP + Math.max(0, playerCount - 1);
        return Math.min(fromGraves, ceiling);
    }
}
