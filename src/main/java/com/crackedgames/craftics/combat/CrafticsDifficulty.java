package com.crackedgames.craftics.combat;

/**
 * How hard the fights are, as one setting the player actually feels.
 *
 * <p>The mod already had a dozen scaling knobs - per-biome HP, per-level HP, attack divisors,
 * a global enemy multiplier - but every one of them is a number in a config screen that means
 * nothing until you have played enough to have an opinion about it. This is the single choice
 * in front of that: enemies are tougher, and everything that swings at you hits harder.
 *
 * <p>Deliberately only two levers. A difficulty that changes ten things at once cannot be
 * reasoned about by the person picking it, and cannot be balanced by the person building it.
 *
 * <ul>
 *   <li><b>Easy</b> - exactly what the game was before this setting existed, and the default
 *       for every island and every server. The baseline is genuinely unchanged, so nobody's
 *       existing save gets harder for updating.</li>
 *   <li><b>Medium</b> - enemies carry half again their health, and every attack lands for one
 *       more.</li>
 *   <li><b>Hard</b> - double health, and two more on every attack.</li>
 * </ul>
 *
 * <p>The flat damage addition is deliberately flat rather than a multiplier. A multiplier makes
 * the big hits enormous and leaves the small ones untouched, which sharpens exactly the spikes
 * that already kill people; a flat addition raises the floor, so chip damage stops being free
 * and the ceiling stays somewhere a 20 HP player can live with.
 */
public enum CrafticsDifficulty {

    EASY(1.0f, 0),
    MEDIUM(1.5f, 1),
    HARD(2.0f, 2);

    /** Multiplied ON TOP of the existing enemy HP scaling, not instead of it. */
    public final float hpMultiplier;
    /** Added to every enemy attack that lands on a player. */
    public final int damageBonus;

    CrafticsDifficulty(float hpMultiplier, int damageBonus) {
        this.hpMultiplier = hpMultiplier;
        this.damageBonus = damageBonus;
    }

    /** Parse a stored or typed name, falling back to {@code fallback} for anything unknown. */
    public static CrafticsDifficulty parse(String name, CrafticsDifficulty fallback) {
        if (name == null || name.isBlank()) return fallback;
        for (CrafticsDifficulty d : values()) {
            if (d.name().equalsIgnoreCase(name)) return d;
        }
        return fallback;
    }

    /**
     * The difficulty in force for a given island, by its owner.
     *
     * <p>Per island, not per server. On a shared server one player wanting a harder campaign
     * should not drag everyone else's runs up with them - and in a party fight the island being
     * played is the leader's, so the leader's setting is the one that counts. Every caller
     * passes the same owner id the HP-scaling preference already uses, so the two cannot
     * disagree about whose island it is.
     *
     * <p><b>Easy until somebody says otherwise</b>, deliberately ignoring the world's own
     * difficulty. Deriving it from the world would mean a server that happens to run on Hard
     * silently doubles every islander's enemy health, and an existing save would get harder for
     * updating the mod - neither of which anybody asked for. The only thing that changes this
     * is a player choosing to change it.
     */
    public static CrafticsDifficulty of(net.minecraft.server.world.ServerWorld world,
                                        java.util.UUID islandOwner) {
        if (world == null || islandOwner == null) return EASY;
        try {
            String stored = com.crackedgames.craftics.world.CrafticsSavedData.get(world)
                .getPlayerData(islandOwner).difficulty;
            return parse(stored, EASY);
        } catch (Throwable t) {
            return EASY;
        }
    }

    /**
     * The enemy HP multiplier to actually use: the configured one, scaled by difficulty.
     *
     * <p>Every spawn path calls this rather than reading the config directly, so the two can
     * never disagree about how much health a mob should have - a mob spawned through one path
     * with difficulty applied and another without would be the same enemy at two different
     * sizes depending on where it came from.
     */
    public static double enemyHpMultiplier(CrafticsDifficulty difficulty) {
        return com.crackedgames.craftics.CrafticsMod.CONFIG.enemyHpMultiplier()
            * (difficulty == null ? EASY : difficulty).hpMultiplier;
    }

    /** Flat damage added to every enemy attack. */
    public static int enemyDamageBonus(CrafticsDifficulty difficulty) {
        return (difficulty == null ? EASY : difficulty).damageBonus;
    }

    /** Title-case label for chat and screens. */
    public String label() {
        return name().charAt(0) + name().substring(1).toLowerCase(java.util.Locale.ROOT);
    }
}
