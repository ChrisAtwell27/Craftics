package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.Set;

public interface EnemyAI {
    EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos);

    /**
     * Returns the set of tiles this enemy may threaten next turn, or {@code null}
     * to fall back to the generic {@code speed + range} danger diamond in
     * CombatManager's danger tile builder.
     * <p>
     * Override for enemies whose real attack reach doesn't match the generic formula -
     * e.g. the mimic whose tantrum hops 8-way for 6 steps and whose dash crosses the
     * entire arena in a straight line, neither of which is captured by
     * {@code speed + range}. Tiles returned here will be shown to the player as the
     * red "danger" highlight.
     */
    default Set<GridPos> computeThreatTiles(CombatEntity self, GridArena arena) {
        return null;
    }

    /**
     * What this mob does when something heavy runs into it ({@link EnemyAction.Ram}): the
     * blast it goes up in, or {@code null} to take the blow like anything else.
     * <p>
     * For a mob that is a bomb waiting for an excuse. It bursts where it stands, at once,
     * whether or not it had woken or lit a fuse, and whatever ran into it is left out.
     */
    default EnemyAction.Explode whenRammed(CombatEntity self) {
        return null;
    }

    /**
     * Whether this enemy is currently a threat to the player - i.e. it will try to
     * attack. Defaults to {@code true} for ordinary hostile mobs.
     * <p>
     * Passive mobs (farm animals) override this to always return {@code false}, and
     * neutral mobs (bees, wolves, etc.) return {@code false} until they are provoked.
     * Used by the anti-farming auto-end: a fight that contains only non-threatening
     * mobs and produces no kills for a few turns ends automatically so the player
     * can't farm a room full of passive animals indefinitely.
     */
    default boolean isHostileThreat(CombatEntity self, GridArena arena, GridPos playerPos) {
        return true;
    }

    /**
     * Whether this enemy should be spawned already invisible (hidden from every
     * client until the AI reveals it). Defaults to {@code false}.
     * <p>
     * Overridden by ambush mobs like the Deeper-and-Darker Shriek Worm, which
     * lurk unseen until a player wanders into range. CombatManager calls this
     * right after building the entity and, if true, sets the mob invisible so
     * there's no one-turn window where it's visible before its first
     * {@code decideAction} runs. The AI is still responsible for the reveal.
     */
    default boolean spawnsInvisible(CombatEntity self, GridArena arena) {
        return false;
    }

    /**
     * A line this enemy says as it dies, or {@code null} for none. CombatManager asks once,
     * right after it announces a boss defeated, and sends whatever comes back to the party.
     * <p>
     * An AI is not otherwise told that its creature has died, so this is the place for a
     * boss with parting words. {@code self} is already dead when this is called.
     */
    default String getLastWords(CombatEntity self) {
        return null;
    }
}
