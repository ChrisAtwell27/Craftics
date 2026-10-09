package com.crackedgames.craftics.combat.shadow;

/**
 * The numbers that keep a mirror match fair. Every tuning knob for the Shadow is in this file.
 *
 * <p>A Shadow copies a player's kit, and a player's kit is built to chew through a boss with
 * many times the player's health. Hand the copy the player's real damage and it kills its
 * original in a turn. So the Shadow's numbers come from the BOSS it stands in for:
 *
 * <ul>
 *   <li><b>Health</b> is the biome's boss health, shared out when there is one Shadow per
 *       player.</li>
 *   <li><b>Damage</b> is the boss's attack, by the AP a hit costs: each AP spent is worth the
 *       same slice of it. A 1-AP sword makes three small cuts where a 3-AP hammer makes one
 *       big one, and both add up to about the same turn.</li>
 * </ul>
 *
 * <p>Gear decides the shape of the turn, not its size. That is also what stops the oldest
 * mirror-boss trick: a player who turns up with nothing fights a Shadow that punches exactly
 * as hard as one holding their best sword would.
 *
 * <p><b>Why the figure is what lands, not what is thrown.</b> A player's armour takes a flat
 * amount off every hit. That is nothing to a boss that hits once for twelve, and it is
 * everything to one that hits four times for three: every one of those rounds down to 1.
 * The first version of the Shadow did exactly that. So {@link #hitDamage} is the damage a hit
 * is meant to LAND for, and a Shadow is marked as striking past worn armour
 * ({@code CombatEntity.setStrikesPastArmor}) so that nothing is taken off it a second time.
 * Armour still earns its keep the way it mostly does anyway: by turning hits aside entirely.
 *
 * <p>Pure arithmetic, no Minecraft types, so {@code ShadowBalanceTest} can pin it down.
 */
public final class ShadowBalance {

    private ShadowBalance() {}

    /** A lone Shadow has the whole pool. Each further one takes this much off every Shadow. */
    public static final double HP_LOSS_PER_EXTRA_CLONE = 0.15;
    /** However many Shadows there are, each keeps at least this share. */
    public static final double MIN_HP_SHARE = 0.60;

    /** Each AP a hit costs is worth this share of the boss's attack. */
    public static final double DAMAGE_PER_AP = 0.3;
    /** No single hit is worth more than the boss's whole attack, whatever it costs. */
    public static final double MAX_HIT_SHARE = 1.0;
    /** The worst weapon in a kit still hits for this fraction of what the best one does. */
    public static final double MIN_WEAPON_QUALITY = 0.5;

    /** The share of a full boss health pool each Shadow gets when there are {@code clones}. */
    public static double hpShare(int clones) {
        int extra = Math.max(0, clones - 1);
        return Math.max(MIN_HP_SHARE, 1.0 - HP_LOSS_PER_EXTRA_CLONE * extra);
    }

    /** One Shadow's health, from the boss health the biome defines. */
    public static int cloneHp(int bossHp, int clones) {
        return Math.max(1, (int) Math.round(bossHp * hpShare(clones)));
    }

    /**
     * What one hit is meant to land for, after the victim's flat armour.
     *
     * @param bossAttack  the attack stat of the boss the Shadow stands in for
     * @param apCost      what this weapon, throwable or sherd costs to use
     * @param weaponPower its strength: attack power for a weapon, a percentage for anything else
     * @param bestPower   the strongest it could be: the best weapon in the kit, or 100
     */
    public static int hitDamage(int bossAttack, int apCost, int weaponPower, int bestPower) {
        double share = Math.min(MAX_HIT_SHARE, DAMAGE_PER_AP * Math.max(1, apCost));
        double quality = bestPower <= 0 ? 1.0
            : Math.max(MIN_WEAPON_QUALITY, Math.min(1.0, weaponPower / (double) bestPower));
        return Math.max(1, (int) Math.round(Math.max(1, bossAttack) * share * quality));
    }

    /** A weapon that heals its wielder gives a Shadow back at most this share of its health per use. */
    public static final double MAX_LIFESTEAL_SHARE = 0.05;

    /**
     * What a weapon's lifesteal is worth to a Shadow.
     *
     * <p>The weapon heals in a player's numbers, out of a player's twenty health. A boss has
     * many times that, so the same points are scaled up to its pool, then capped: a Shadow
     * that hits four times a turn must not undo a whole round of the party's damage doing it.
     *
     * @param healed points the weapon restored to its wielder, on a player's scale
     * @param maxHp  the Shadow's maximum health
     */
    public static int lifesteal(int healed, int maxHp) {
        if (healed <= 0 || maxHp <= 0) return 0;
        int scaled = (int) Math.round(healed * maxHp / 20.0 / 4.0);
        int cap = Math.max(1, (int) Math.round(maxHp * MAX_LIFESTEAL_SHARE));
        return Math.max(1, Math.min(cap, scaled));
    }
}
