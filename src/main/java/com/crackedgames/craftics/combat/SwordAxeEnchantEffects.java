package com.crackedgames.craftics.combat;

/**
 * Pure math for the sword and axe enchantments added in the eight-enchantment batch. Minecraft
 * free so the whole ruleset is unit-testable without a bootstrap. The combat hooks in
 * CombatManager and VanillaWeapons read enchant levels and call these.
 */
public final class SwordAxeEnchantEffects {

    private SwordAxeEnchantEffects() {}

    /** Executioner: +1 damage per debuff on the target, per enchant level. */
    public static int executionerBonus(int targetDebuffCount, int level) {
        if (level <= 0 || targetDebuffCount <= 0) return 0;
        return targetDebuffCount * level;
    }

    /** Pack Bond: each pet deals +1 per OTHER living pet, per level. livingPetCount is the total
     *  alive including the acting pet, so the "others" is count - 1. */
    public static int packBondBonus(int livingPetCount, int level) {
        if (level <= 0) return 0;
        int others = Math.max(0, livingPetCount - 1);
        return others * level;
    }

    /** Serrated: Bleed stacks applied per hit equals the enchant level. */
    public static int serratedBleedStacks(int level) {
        return Math.max(0, level);
    }

    /** Reversal deals this multiplier while the wielder is at or below a quarter HP. */
    public static final double REVERSAL_MULT = 1.5;

    /** True when currentHp is at or below 25% of maxHp. */
    public static boolean isLowHp(int currentHp, int maxHp) {
        if (maxHp <= 0) return false;
        return currentHp * 4 <= maxHp;
    }

    /** Hilt cuts a hit to this fraction (a 75% reduction) on top of redirecting its affinity. */
    public static final double HILT_MULT = 0.25;
    /** Dull cuts a hit to this fraction (a 50% reduction) on top of redirecting its affinity. */
    public static final double DULL_MULT = 0.5;

    /** Hilt: hit for a quarter, never below 1 on a hit that was already positive. */
    public static int hiltDamage(int baseDamage) {
        if (baseDamage <= 0) return baseDamage;
        return Math.max(1, (int) Math.round(baseDamage * HILT_MULT));
    }

    /** Dull: hit for a half, never below 1 on a hit that was already positive. */
    public static int dullDamage(int baseDamage) {
        if (baseDamage <= 0) return baseDamage;
        return Math.max(1, (int) Math.round(baseDamage * DULL_MULT));
    }

    // ── New-wave enchantments ───────────────────────────────────────────────

    /** Matador: DEF the Exposed attacker loses, and for how many turns. */
    public static final int MATADOR_DEFENSE_PENALTY = 2;
    public static final int MATADOR_EXPOSE_TURNS = 1;

    /** Crater: extra knockback tiles, and the damage + stun for slamming into something. */
    public static final int CRATER_EXTRA_TILES = 1;
    public static final int CRATER_COLLISION_DAMAGE = 2;

    /** Momentum: AP handed to the next party member in the turn order, once per turn. */
    public static final int MOMENTUM_AP = 1;

    /** Demolisher: Speed refunded for chopping an obstacle (the chop itself costs 1 AP). */
    public static final int DEMOLISHER_SPEED_REFUND = 1;

    /** Vengeful Bond: turns the pet-killer stays Marked. */
    public static final int VENGEFUL_BOND_MARK_TURNS = 2;

    /** Phantom Edge: stealth-preserved attacks per turn equals the enchant level. */
    public static int phantomEdgeAttacksPerTurn(int level) {
        return Math.max(0, level);
    }

    // ── Second wave ─────────────────────────────────────────────────────────

    /** Hemorrhage: burst damage per Bleed stack detonated off the target. */
    public static final int HEMORRHAGE_DAMAGE_PER_STACK = 2;

    /** Ambush: ATK the frightened next-in-order enemy loses, and for how long. */
    public static final int AMBUSH_ATK_PENALTY = 2;
    public static final int AMBUSH_PENALTY_TURNS = 1;

    /** Timberfall: what the falling obstacle deals to each enemy beside it. */
    public static final int TIMBERFALL_DAMAGE = 3;

    /** Midas: emeralds a wall-slam shakes loose (1..this), once per enemy per combat. */
    public static final int MIDAS_MAX_EMERALDS = 2;

    /** Shockstep: stomp damage to each adjacent enemy on a jump landing, and the Slow turns. */
    public static final int SHOCKSTEP_DAMAGE = 2;
    public static final int SHOCKSTEP_SLOW_TURNS = 1;

    /** Ledgegrip: HP the once-per-combat ledge catch costs. */
    public static final int LEDGEGRIP_DAMAGE = 2;

    /** Longstride: widest gap a jump can clear with the leggings on (base is 2). */
    public static final int LONGSTRIDE_MAX_GAP = 3;

    /** Phalanx: AC granted per adjacent party member, to both sides of the pairing. */
    public static final int PHALANX_AC_PER_NEIGHBOR = 1;

    /** Grudgeplate: bonus damage the whole party deals to the wearer's grudge target. */
    public static final int GRUDGEPLATE_BONUS = 2;

    /** Beacon: tile radius of the walking banner aura. */
    public static final int BEACON_RADIUS = 2;
}
