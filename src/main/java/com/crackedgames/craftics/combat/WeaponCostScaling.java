package com.crackedgames.craftics.combat;

/**
 * How much a point of affinity is worth, by the AP cost of the weapon using it.
 *
 * <p>Affinity used to be a flat +3 damage and a flat proc chance per point on every weapon.
 * A flat bonus is worth the most on the cheapest swing: a 1 AP dagger cashes it in three times
 * a turn where a 3 AP greataxe cashes it in once, so every build that invested in affinity was
 * pushed onto 1 AP weapons. Heavier swings also lose more to effects that are spent per hit
 * (Airtime, Fortune's Favor), which a per-hit flat bonus did nothing to offset.
 *
 * <p>So the per-point value now rises with the weapon's cost. Weapons costing more than 3 AP
 * are treated as 3 AP. Minecraft-free on purpose so the numbers can be pinned in unit tests.
 */
public final class WeaponCostScaling {

    private WeaponCostScaling() {}

    /** Weapon AP cost clamped into the 1 to 3 band the scaling is defined over. */
    static int tier(int apCost) {
        return Math.max(1, Math.min(3, apCost));
    }

    /** Damage per affinity point: 2 / 3 / 4 for 1 / 2 / 3 AP weapons. */
    public static int damagePerAffinityPoint(int apCost) {
        return tier(apCost) + 1;
    }

    /** Proc chance per affinity point (shatter, stun, soak, free AP...): 2% / 4% / 6%. */
    public static double procPerAffinityPoint(int apCost) {
        return 0.02 * tier(apCost);
    }

    /** Sweep chance per Slashing point: 3% / 5% / 7%. */
    public static double sweepPerAffinityPoint(int apCost) {
        return 0.01 + 0.02 * tier(apCost);
    }
}
