package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.CombatEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides what order the creatures act in each round.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Craftics acts in spawn order. The first mob the level put on the board is the first one to
 * move, every round, for the whole fight, and nothing a creature is can change that. For a mod
 * whose creatures have a Speed stat, that is the one number the fight refuses to read: a mod can
 * describe a fast creature perfectly and it will still wait its turn behind a slow one.
 *
 * <p>Ordering was the missing half of a pick-then-resolve round. The other half already works:
 * {@link CrafticsAPI#commandAlly} leaves an order that is obeyed on the creature's own turn
 * rather than the instant the player clicks, carrying its attack type and accuracy with it. So
 * the player picks a move during their turn, the enemy's AI picks one when its turn comes, and
 * this is what decides which of them goes first.
 *
 * <pre>{@code
 * // The whole of a speed-based round, for a mod that has a Speed stat.
 * CrafticsAPI.registerTurnOrderProvider((player, actors, round) -> actors.stream()
 *     .sorted(Comparator.comparingInt(this::speedOf).reversed())
 *     .toList());
 * }</pre>
 *
 * <h2>What it does and does not decide</h2>
 *
 * <p>It orders the <b>creatures</b>: every enemy and every ally that acts this round, in one
 * list, so a fast enemy can act before the player's own creature. The player's own turn still
 * comes first - they are the one choosing, and the choosing is what their turn is for. For a
 * fight where the creature is the fighter, that is a pick-then-resolve round exactly.
 *
 * <p>Craftics keeps everything else: a creature's turn resolves through the same movement,
 * damage, resistance, typing and accuracy handling whatever order it comes in.
 *
 * <h2>Contract</h2>
 *
 * <p>Return the actors in the order you want them, or null to leave the round alone - the first
 * provider to answer with a list wins. You cannot add a combatant to the round or remove one
 * from it: anything you return that is not acting this round is ignored, and anything you leave
 * out still acts, after the ones you named, in the order it already had. A provider that throws
 * is skipped and the round runs in Craftics' own order.
 *
 * <p>Asked once at the top of every round, so a creature slowed or hasted mid-fight is ordered
 * on what it is now rather than what it was when the fight started.
 *
 * @since 0.4.5
 */
@FunctionalInterface
public interface TurnOrderProvider {

    /**
     * Order the creatures acting this round.
     *
     * @param player the player whose fight this is
     * @param actors every creature that will act this round - enemies and allies together - in
     *               the order Craftics would use. Never empty
     * @param round  the round number, counting from 1
     * @return the order to act in, or null to leave Craftics' order alone
     */
    List<CombatEntity> orderTurns(ServerPlayerEntity player, List<CombatEntity> actors, int round);

    /**
     * The obvious one: fastest first, by each creature's move speed.
     *
     * <p>A convenience for a mod whose Speed already drives movement. A mod whose Speed is its
     * own stat should sort on that instead - this only knows what Craftics knows.
     *
     * <p>Ties keep their existing order rather than being broken at random. A coin flip between
     * two equally fast creatures is a design decision, and one a provider can make for itself.
     */
    static TurnOrderProvider byMoveSpeed() {
        return (player, actors, round) -> {
            List<CombatEntity> sorted = new ArrayList<>(actors);
            sorted.sort((a, b) -> Integer.compare(b.getMoveSpeed(), a.getMoveSpeed()));
            return sorted;
        };
    }
}
