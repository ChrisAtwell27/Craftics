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
}
