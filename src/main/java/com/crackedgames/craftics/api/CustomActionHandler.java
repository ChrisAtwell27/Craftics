package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;

import java.util.List;

/**
 * Resolves an addon-defined enemy action.
 *
 * <p>Craftics' {@code EnemyAction} is a sealed set of about forty shapes - move, attack,
 * pounce, teleport, explode and so on - and being sealed is load-bearing: the enemy turn
 * machine dispatches on it with pattern-matching switches, and the compiler checks those
 * are exhaustive. Unsealing it to let addons add shapes would silently give that checking
 * up across dozens of sites.
 *
 * <p>So addons get one more member of the set instead. {@code EnemyAction.CustomAction}
 * carries an id and a bag of parameters; the turn machine recognises exactly that one
 * shape and hands resolution straight back to whoever registered the id. The other thirty
 * nine keep their exhaustiveness checks.
 *
 * <h2>What a handler is responsible for</h2>
 *
 * <p>Everything the action does: damage, movement, effects, particles, messages. Craftics
 * has already advanced the turn to this enemy and will move on when you return - the
 * handler is the action.
 *
 * <p>What Craftics still does around it: the turn rotation itself, death checks on
 * anything the handler damaged, and the telegraph if the action was wrapped in a
 * {@code BossAbility}. Wrapping is the normal way to get a charge-up: a custom action
 * inside a {@code BossAbility} gets warning tiles and the full windup treatment for free,
 * with the handler firing a turn later when it resolves.
 *
 * <pre>{@code
 * CrafticsAPI.registerCustomAction("mymod:flamethrower", (ctx) -> {
 *     for (GridPos tile : ctx.tiles()) {
 *         CombatEntity victim = ctx.arena().getOccupant(tile);
 *         if (victim != null) ctx.damage(victim, 6);
 *     }
 *     ctx.message("§cThe flames wash over the lane!");
 * });
 * }</pre>
 *
 * @since 0.3.9
 */
@FunctionalInterface
public interface CustomActionHandler {

    /**
     * Carry out the action.
     *
     * @param ctx everything the handler needs and is allowed to touch
     */
    void resolve(Context ctx);

    /**
     * What a custom action handler is handed when it fires.
     *
     * <p>Deliberately an interface rather than a record: it is the seam between an addon
     * and the combat internals, and the internals move. Adding a capability here is a new
     * default method rather than a break in every addon that ever implemented it.
     */
    interface Context {

        /** The enemy performing the action. */
        CombatEntity self();

        /** The arena it is being performed in. */
        GridArena arena();

        /** The arena world, for particles and sounds. */
        ServerWorld world();

        /** Where the player the AI aimed at is standing. */
        GridPos playerPos();

        /** The tiles this action names, as the AI decided them. Empty when it named none. */
        List<GridPos> tiles();

        /** The parameters the AI attached, or an empty compound. */
        NbtCompound params();

        /** The damage figure the AI attached, or {@code 0}. */
        int damage();

        /**
         * Deal damage to a combatant through Craftics' own pipeline, so resistances,
         * attack typings, shields, effects and death handling all apply exactly as they
         * would for a built-in action. Prefer this over touching HP directly.
         */
        void damage(CombatEntity target, int amount);

        /**
         * Move this enemy to a tile, respecting the grid the way built-in movement does:
         * refuses solid tiles, teleports the world entity to match, and drops it into a
         * pit if it lands on one.
         *
         * @return true if the move happened
         */
        boolean moveSelfTo(GridPos dest);

        /** Send a line to everyone in the fight. */
        void message(String text);
    }
}
