package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.combat.TraderSystem;

import java.util.List;

/**
 * Supplies the wares one trader category can offer, before price scaling and the
 * per-visit shuffle-and-pick.
 *
 * <p>Add every trade the trader could EVER sell at this tier; {@code TraderSystem} scales the
 * prices by biome tier and then picks a random 3-5 of them for the visit, so a fat pool simply
 * means more variety between visits rather than a longer shop.
 *
 * @since 0.2.10
 */
@FunctionalInterface
public interface TraderStockProvider {

    /**
     * Append this trader's candidate trades to {@code pool}.
     *
     * @param pool the list to add to; already holds nothing for this trader
     * @param tier the island's biome tier, 1-9. Gate better wares behind higher tiers.
     */
    void stock(List<TraderSystem.Trade> pool, int tier);
}
