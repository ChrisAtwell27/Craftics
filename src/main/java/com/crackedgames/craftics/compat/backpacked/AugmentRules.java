package com.crackedgames.craftics.compat.backpacked;

/**
 * The decisions behind the Backpacked augment integrations, with no Minecraft in them.
 *
 * <p>Split out of {@link BackpackedCompat} because that class cannot be tested: everything in it
 * needs a live player, a reflectively-resolved backpack and a bootstrapped registry, none of which
 * a JUnit run has. The rules themselves are small pieces of logic with real branches - "does this
 * pack want this item", "may this penalty apply" - and those branches are worth pinning down
 * somewhere a test can reach.
 */
public final class AugmentRules {

    private AugmentRules() {}

    /**
     * Whether a pack with these augments claims a piece of combat loot.
     *
     * <p>Funnelling owns the filter; Lootbound is a companion that extends the same filter to
     * drops and carries its own toggle for whether mob drops count. Combat rewards are mob drops
     * that never got to be entities, so a player who turned that toggle off has said no to
     * exactly this loot, whatever the filter would have allowed.
     *
     * @param hasFunnelling Funnelling is fitted to this pack
     * @param hasLootbound  Lootbound is fitted to this pack
     * @param mobsOn        Lootbound's "mobs" toggle, meaningless when Lootbound is absent
     * @param filterPass    Funnelling's filter accepted the item, meaningless without Funnelling
     */
    public static boolean funnelClaims(boolean hasFunnelling, boolean hasLootbound,
                                       boolean mobsOn, boolean filterPass) {
        if (!hasFunnelling && !hasLootbound) return false;
        // Lootbound present and refusing mob drops overrides an otherwise permissive filter.
        if (hasLootbound && !mobsOn) return false;
        // Lootbound alone has no filter to consult; its toggle is the whole gate.
        if (!hasFunnelling) return true;
        return filterPass;
    }

    /**
     * The movement penalty a Giant backpack may actually charge.
     *
     * <p>Zero when charging it in full would leave the player unable to move at all. That floor is
     * not politeness: at zero movement a player cannot reach an enemy, step out of a hazard or
     * finish the level, so on a low base SPEED the trade-off would become a dead run rather than a
     * heavier pack.
     *
     * @param base     the player's effective SPEED stat
     * @param setBonus flat movement from their armor set, which may itself be negative
     * @param penalty  what Giant would like to charge
     * @return the penalty to apply, never more than {@code penalty} and never below zero
     */
    public static int giantPenalty(int base, int setBonus, int penalty) {
        if (penalty <= 0) return 0;
        return (base + setBonus - penalty) >= 1 ? penalty : 0;
    }
}
