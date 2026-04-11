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
     * Override for enemies whose real attack reach doesn't match the generic formula —
     * e.g. the mimic whose tantrum hops 8-way for 6 steps and whose dash crosses the
     * entire arena in a straight line, neither of which is captured by
     * {@code speed + range}. Tiles returned here will be shown to the player as the
     * red "danger" highlight.
     */
    default Set<GridPos> computeThreatTiles(CombatEntity self, GridArena arena) {
        return null;
    }
}
