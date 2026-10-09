package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * A mover that sets its own pace, and wants to know when each step lands.
 *
 * <p>Every move is walked a tile at a time at one fixed speed, which is right for anything
 * that walks. It is wrong for anything with weight: a thing that slides should gather speed
 * and hit what stops it, and what it hits should break when it gets there, not when it sets
 * off. An AI that implements this is asked how long each step takes, and told as each one
 * lands and when the move is over.
 *
 * <p>None of this changes what a move does. The path is the path the action asked for, cut
 * short by the same things that cut any move short.
 */
public interface PacedMover {

    /**
     * How many ticks the step onto {@code path.get(index)} takes.
     *
     * <p>Asked every tick of the step, so it must give the same answer each time and do
     * nothing else. Not asked at all when enemy animations are switched off.
     *
     * @param start where the mover stood before the first step
     * @param usual what the engine would take: return it to leave a step as it was
     */
    int stepTicks(CombatEntity self, GridPos start, List<GridPos> path, int index, int usual);

    /** The step onto {@code path.get(index)} has landed: the mover stands there now. */
    default void stepLanded(CombatEntity self, GridArena arena, GridPos start, List<GridPos> path, int index) {}

    /**
     * The move is over, however it ended. A web, a trap or a death can end one early, so
     * {@code stepsLanded} may be fewer than the path was long. Called once for every move.
     */
    default void moveEnded(CombatEntity self, GridArena arena, int stepsLanded) {}
}
