package com.crackedgames.craftics.compat.aether.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.Set;

/**
 * Aechor Plant AI: a rooted turret. It never moves, for any reason.
 * - SPIT: fires a poison needle at anything inside its range that it has a clear line to.
 *   The poison rides the plant's jungle theme tag, not this class
 * - Otherwise it waits. There is no repositioning, so the answer to one is to stay out of
 *   its range or put something solid in the way
 *
 * Range comes from the plant's stats (3 is what the compat intends). The plant is also
 * flagged immovable at spawn, so nothing can shove it off its roots either.
 */
public class AechorPlantAI implements EnemyAI {

    /** Names the shot for the combat log and any effect keyed on it. */
    public static final String NEEDLE = "poison_needle";

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        if (AetherAi.hasShot(arena, self.getGridPos(), playerPos, reach(self))) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), NEEDLE);
        }
        return new EnemyAction.Idle();
    }

    /**
     * Exactly the tiles it can reach, and no further. The engine's default adds a mob's
     * movement to its range, which for something that cannot move paints tiles that are safe.
     */
    @Override
    public Set<GridPos> computeThreatTiles(CombatEntity self, GridArena arena) {
        return AetherAi.diamond(arena, self.getGridPos(), reach(self));
    }

    private static int reach(CombatEntity self) {
        return Math.max(1, self.getRange());
    }
}
