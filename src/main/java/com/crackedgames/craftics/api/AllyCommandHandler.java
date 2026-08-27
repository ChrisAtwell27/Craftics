package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Handles the second half of the ally-command gesture: the player has an ally selected, and has
 * now clicked somewhere on the grid to tell it what to do.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Craftics already has the whole machinery for this. Selecting an ally highlights where it can
 * walk and which enemies it can reach, and the click that follows either walks it along a
 * pathfound route or has it strike an adjacent enemy. All of it is gated on the player holding a
 * Lead, and the click arrives on Craftics' own packet with nowhere for an addon to answer it.
 *
 * <p>So a mod whose fights are commanded rather than swung had to rebuild the visible half of the
 * fight in its own screen: a Move button that cannot reach Craftics' walk, and attack buttons
 * standing in for grid targeting that already works. This handler, with
 * {@link CrafticsAPI#selectAlly}, closes that: the addon turns selection on without the item,
 * Craftics highlights the ally's real options, and the click comes back here.
 *
 * <h2>Contract</h2>
 *
 * <p>Return {@code true} if you handled the click. Craftics then does nothing further: no walk,
 * no strike, <b>no AP spent</b>, and no refusal message. Return {@code false} and the built-in
 * command runs unchanged, which is the point - decline the clicks you have no answer for and the
 * ally still walks and strikes for free.
 *
 * <p>Handlers are asked in registration order, <b>before</b> the AP check and before either
 * built-in branch, so an addon that owns its allies owns the whole gesture. A handler that throws
 * loses the click and Craftics' own behaviour runs instead.
 *
 * <p><b>Selection is left exactly as you found it.</b> Craftics clears it after its own commands,
 * because a Lead command is one order and then the ally is done; an addon issuing several orders
 * from one selection would find it deselected under them. Call
 * {@link CrafticsAPI#clearAllySelection} when your gesture is finished.
 *
 * <pre>{@code
 * CrafticsAPI.registerAllyCommandHandler((player, ally, tile, target) -> {
 *     Move move = pendingMove(player);
 *     if (move == null) return false;                 // no move picked: let Craftics walk it
 *     if (target != null) {
 *         useMoveOn(player, ally, move, target);      // grid targeting, not a screen button
 *     } else {
 *         useMoveOnTile(player, ally, move, tile);
 *     }
 *     CrafticsAPI.clearAllySelection(player);
 *     return true;
 * });
 * }</pre>
 *
 * @since 0.4.5
 */
@FunctionalInterface
public interface AllyCommandHandler {

    /**
     * Called when {@code player} clicks the grid with {@code ally} selected.
     *
     * @param player the player whose turn it is
     * @param ally   the selected ally. Alive, on the field, and this player's to command
     * @param tile   the tile they clicked, always in bounds
     * @param target the combatant standing on that tile, or null for bare ground. Usually an
     *               enemy, but it can be one of the player's own allies - a click on your own
     *               creature is how a heal or a buff picks its target, and while your
     *               selection stands every click on the grid comes here. Craftics' own strike
     *               needs an adjacent enemy; yours does not have to
     * @return true if you handled it; false to fall through to Craftics' walk-or-strike
     */
    boolean onAllyCommand(ServerPlayerEntity player, CombatEntity ally, GridPos tile,
                          CombatEntity target);
}
