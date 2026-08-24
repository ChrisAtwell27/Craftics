package com.crackedgames.craftics.combat;

/**
 * How hard a trial chamber hits.
 *
 * <p>Pure arithmetic, deliberately in its own class with no Minecraft types in it. The balance
 * numbers are the part worth pinning down, and {@link TrialChamberEvent} cannot even be loaded
 * in a test - its static fields reach into the block registry, so touching the class at all
 * needs a running game. A formula nobody can execute outside Minecraft is a formula that gets
 * checked by playing, which is how a Breeze ended up swinging for eight hearts.
 *
 * <h2>Why attack scales differently from health</h2>
 *
 * <p>They used to share one multiplier. They should not: a trial mob with triple health makes
 * a fight longer, and a trial mob with triple damage makes it shorter, with the player being
 * the one it ends. Worse, trial spawns skip the per-biome damage cap that restrains every
 * campaign enemy - deliberately, so a trial can be a genuine step up - which removed the one
 * thing that would otherwise have caught the number growing out of range.
 *
 * <p>So health keeps the full surcharge and attack takes half of it.
 */
public final class TrialScaling {

    private TrialScaling() {}

    /** The fraction of the difficulty surcharge that reaches attack. */
    public static final float ATTACK_SCALE = 0.5f;

    /**
     * A trial mob's attack value.
     *
     * @param base          the mob's authored attack, before any scaling
     * @param atkBonus      the additive per-biome bonus the campaign also applies
     * @param diffMultiplier the trial's difficulty multiplier (health uses this in full)
     * @param cap           the absolute ceiling any enemy's attack may reach
     * @return base plus bonus, half-scaled, never below {@code base} and never above {@code cap}
     */
    public static int attack(int base, int atkBonus, float diffMultiplier, int cap) {
        int scaled = (int) ((base + atkBonus) * diffMultiplier * ATTACK_SCALE);
        // Floored at the mob's own base. At the first biome the halved surcharge lands UNDER
        // the base value, and a trial zombie that hit softer than an ordinary zombie would be
        // a worse bug than the one this exists to fix.
        return Math.min(cap, Math.max(base, scaled));
    }
}
