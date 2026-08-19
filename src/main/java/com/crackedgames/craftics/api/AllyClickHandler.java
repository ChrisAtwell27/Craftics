package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.CombatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Handles a player clicking one of their own allies on the combat grid.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Clicking an ally is never an attack, and Craftics has exactly one thing it will do with
 * that click: if the player is holding the ally's registered heal item, it heals. Anything else
 * is refused with a message and the turn is not spent.
 *
 * <p>That is a closed set, and it is the wrong shape for an addon whose allies are the whole
 * game. Clicking your own creature to open its moves, use an item on it, or give it an order is
 * the most natural gesture there is, and there was no way to reach it: grid clicks arrive over
 * Craftics' own packet and go straight into the attack path, so there is no Fabric event an
 * addon could listen to.
 *
 * <p>This is the general form of the heal-item hook that was already there.
 *
 * <h2>Contract</h2>
 *
 * <p>Return {@code true} if you handled the click. Craftics then does nothing further - no heal,
 * no "that is your ally" message. Return {@code false} and the built-in behaviour runs
 * unchanged.
 *
 * <p>Handlers are asked in registration order and get first refusal, <b>before</b> the heal-item
 * check, so an addon that owns its allies entirely can own this gesture too. Decline anything
 * you do not recognise: a handler that claims every click removes healing for everyone.
 *
 * <p><b>Nothing is spent for you.</b> Craftics does not charge AP for an ally click, because
 * until now one could not do anything worth charging for. If your action should cost something,
 * spend it yourself.
 *
 * <pre>{@code
 * CrafticsAPI.registerAllyClickHandler((player, ally, held) -> {
 *     if (!"mymod:creature".equals(ally.getEntityTypeId())) return false;
 *     if (held.isOf(MyItems.POTION)) {
 *         useItemOn(player, ally, held);
 *         return true;
 *     }
 *     openMoveMenu(player, ally);
 *     return true;
 * });
 * }</pre>
 *
 * @since 0.4.0
 */
@FunctionalInterface
public interface AllyClickHandler {

    /**
     * Called when {@code player} clicks {@code ally} on the grid.
     *
     * @param player the player whose turn it is
     * @param ally   the ally they clicked. Always on the field and on this player's side
     * @param held   what is in their main hand, never null but often empty
     * @return true if you handled it; false to fall through to Craftics' own behaviour
     */
    boolean onAllyClicked(ServerPlayerEntity player, CombatEntity ally, ItemStack held);
}
