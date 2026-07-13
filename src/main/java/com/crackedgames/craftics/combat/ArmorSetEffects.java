package com.crackedgames.craftics.combat;

/**
 * The unique per-set behaviours that the {@code ArmorSetEntry} registry cannot express.
 *
 * <p>{@code ArmorSetEntry} carries flat numbers - affinity, armor class, speed, AP. The
 * mechanics below are conditional, so they live here as a single lookup keyed by the set
 * name that {@link PlayerCombatStats#getArmorSet} returns. {@code CombatManager} calls
 * into this class at the natural site for each effect rather than scattering
 * {@code if (set.equals(...))} chains through the combat loop.
 *
 * <p>Every method takes a set name and returns a no-op value ({@code 0}, {@code false})
 * for a set that does not have that effect, so callers never have to null-check or
 * pre-filter. Sets that Craftics does not know - {@code "mixed"} included - fall through
 * to the no-op branch of every switch.
 *
 * <p>Currently the sets with unique behaviour all come from Immersive Armors; see
 * {@code ImmersiveArmorsCompat} for where they are registered.
 */
public final class ArmorSetEffects {

    private ArmorSetEffects() {}

    // === Tuning ===

    /**
     * Every point of the Luck stat shifts a proc by this much. Craftics' universal
     * convention - the same +2% per point every weapon ability uses.
     */
    public static final double LUCK_PER_POINT = 0.02;

    /** Fraction of a resisted hit that Wooden armor's padding absorbs. */
    public static final double WOODEN_TYPE_RESIST = 0.30;
    /** Chance a worn Wooden or Bone piece shatters when its wearer is hit. */
    public static final double FRAGILE_BREAK_CHANCE = 0.05;
    /** How much each point of Luck shaves off that shatter roll. */
    public static final double LUCK_BREAK_REDUCTION = 0.005;
    /** The shatter roll never falls below this - luck steadies gear, it does not bless it. */
    public static final double FRAGILE_BREAK_FLOOR = 0.01;
    /** Chance Bone armor fires an arrow without spending it. */
    public static final double BONE_AMMO_SAVE = 0.25;
    /** Chance Wither armor fires an arrow without spending it. */
    public static final double WITHER_AMMO_SAVE = 0.50;
    /** Turns of Wither inflicted on whoever strikes a Wither-armored player in melee. */
    public static final int WITHER_RETALIATION_TURNS = 2;
    /** Tiles a Slime-armored player shoves, and is shoved, on contact. */
    public static final int SLIME_KNOCKBACK_TILES = 1;
    /** Cleaving damage Warrior armor adds per full heart of missing health. */
    public static final int WARRIOR_DAMAGE_PER_MISSING_HEART = 1;
    /** AP a Robe wearer saves on every pottery-sherd spell. */
    public static final int ROBE_SHERD_AP_DISCOUNT = 1;
    /** Radius, in tiles, of Prismarine armor's per-turn discharge. */
    public static final int PRISMARINE_RADIUS = 2;
    /** Water damage each enemy in range takes from that discharge. */
    public static final int PRISMARINE_DAMAGE = 3;
    /** Turns of Soaked the discharge applies. */
    public static final int PRISMARINE_SOAK_TURNS = 2;

    // === Set keys ===
    // The armor-set names PlayerCombatStats.getArmorSet derives from Immersive Armors'
    // registry paths (immersive_armors:bone_helmet -> "bone", and so on).

    public static final String WOODEN = "wooden";
    public static final String BONE = "bone";
    public static final String ROBE = "robe";
    public static final String WITHER = "wither";
    public static final String SLIME = "slime";
    public static final String WARRIOR = "warrior";
    public static final String STEAMPUNK = "steampunk";
    public static final String DIVINE = "divine";
    public static final String PRISMARINE = "prismarine";
    public static final String HEAVY = "heavy";

    // =========================================================================
    // Defensive
    // =========================================================================

    /**
     * Fraction of an incoming typed hit this set shrugs off, {@code 0.0} for none.
     * Wooden armor's give absorbs the shock of the blunt and ranged hits - arrows,
     * blasts - that a rigid metal plate would transmit straight through.
     */
    public static double incomingResistance(String armorSet, DamageType incoming) {
        if (!WOODEN.equals(armorSet)) return 0.0;
        return (incoming == DamageType.RANGED || incoming == DamageType.BLUNT)
            ? WOODEN_TYPE_RESIST : 0.0;
    }

    /** True if a worn piece of this set can shatter outright when its wearer is hit. */
    public static boolean isFragile(String armorSet) {
        return WOODEN.equals(armorSet) || BONE.equals(armorSet);
    }

    /** True if this set ignores knockback entirely. */
    public static boolean ignoresKnockback(String armorSet) {
        return HEAVY.equals(armorSet);
    }

    /** True if this set negates the first hit of each combat outright. */
    public static boolean hasDivineDeflect(String armorSet) {
        return DIVINE.equals(armorSet);
    }

    /** Turns of Wither this set inflicts on a melee attacker, or {@code 0}. */
    public static int witherRetaliationTurns(String armorSet) {
        return WITHER.equals(armorSet) ? WITHER_RETALIATION_TURNS : 0;
    }

    /** Tiles this set shoves an attacker (and its wearer) on contact, or {@code 0}. */
    public static int contactKnockbackTiles(String armorSet) {
        return SLIME.equals(armorSet) ? SLIME_KNOCKBACK_TILES : 0;
    }

    // =========================================================================
    // Offensive
    // =========================================================================

    /**
     * Bonus Cleaving damage from the wearer's own wounds: Warrior armor turns every
     * full heart of missing health into another point of damage, so it peaks exactly
     * when the fight has gone worst.
     */
    public static int missingHealthDamage(String armorSet, float currentHp, float maxHp) {
        if (!WARRIOR.equals(armorSet)) return 0;
        int missingHearts = (int) ((maxHp - currentHp) / 2.0f);
        return Math.max(0, missingHearts * WARRIOR_DAMAGE_PER_MISSING_HEART);
    }

    /** Chance in {@code [0,1]} that this set fires an arrow without consuming it. */
    public static double ammoSaveChance(String armorSet) {
        if (BONE.equals(armorSet)) return BONE_AMMO_SAVE;
        if (WITHER.equals(armorSet)) return WITHER_AMMO_SAVE;
        return 0.0;
    }

    /**
     * As {@link #ammoSaveChance(String)}, but a lucky archer's quiver empties slower still.
     * Never reaches certainty - a set that always saved would be an infinite quiver.
     */
    public static double ammoSaveChance(String armorSet, int luckPoints) {
        double base = ammoSaveChance(armorSet);
        if (base <= 0) return 0.0;
        return Math.min(0.90, base + Math.max(0, luckPoints) * LUCK_PER_POINT);
    }

    /**
     * Chance a worn piece of a fragile set shatters on a hit. Luck steadies the wearer's
     * gear: every point shaves a little off the roll, floored so a piece is never
     * unbreakable.
     */
    public static double fragileBreakChance(String armorSet, int luckPoints) {
        if (!isFragile(armorSet)) return 0.0;
        double reduced = FRAGILE_BREAK_CHANCE - Math.max(0, luckPoints) * LUCK_BREAK_REDUCTION;
        return Math.max(FRAGILE_BREAK_FLOOR, reduced);
    }

    // =========================================================================
    // Utility
    // =========================================================================

    /** True if this set stops pottery sherds from shattering on use. */
    public static boolean sherdsNeverBreak(String armorSet) {
        return ROBE.equals(armorSet);
    }

    /** AP this set shaves off a pottery-sherd spell, or {@code 0}. */
    public static int sherdApDiscount(String armorSet) {
        return ROBE.equals(armorSet) ? ROBE_SHERD_AP_DISCOUNT : 0;
    }

    /** True if this set discharges at nearby enemies at the start of each turn. */
    public static boolean hasPrismarineDischarge(String armorSet) {
        return PRISMARINE.equals(armorSet);
    }

    /**
     * True if this set paints next-turn enemy intent onto the arena floor: the route
     * each enemy will walk, and the tiles it will strike. Read by the radar overlay
     * that {@code CombatManager} ships with the rest of the tile highlights.
     */
    public static boolean revealsEnemyIntent(String armorSet) {
        return STEAMPUNK.equals(armorSet);
    }
}
