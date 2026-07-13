package com.crackedgames.craftics.combat.ai.ally;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;

import java.util.List;

/**
 * AI for a player-summoned seeker vex (Archer sherd) - the ally-side mirror of
 * {@code SeekingProjectileAI}, which hunts the player on the enemy's behalf.
 *
 * <p>A seeker has no self-preservation and no target scoring: it charges the
 * nearest live enemy every turn and destroys itself on attack. It re-aims each
 * turn, so killing its quarry just sends it after the next-nearest one instead
 * of leaving it stranded.
 *
 * <p>Everything else it needs already exists. {@link AllyTargeting#advance} walks
 * it toward the target and strikes in the same turn when the approach ends in
 * range, and the summon lifespan set at spawn expires it. The one thing this AI
 * does NOT do is despawn on impact - {@code CombatManager} does that, because
 * only it can remove the entity from the arena.
 *
 * @since 0.2.92
 */
public class SeekerAllyAI implements AllyAI {

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, List<CombatEntity> combatants) {
        // Re-aim every turn. A seeker whose target died last turn must pick a new
        // one rather than idle over the corpse until it expires.
        CombatEntity target = AllyTargeting.nearestEnemy(self.getGridPos(), combatants);
        if (target == null) return new EnemyAction.Idle();

        return AllyTargeting.advance(self, arena, target);
    }
}
