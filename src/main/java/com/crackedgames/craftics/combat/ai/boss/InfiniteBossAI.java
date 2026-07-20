package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.List;

/**
 * INFINITE MODE boss: a visually enlarged, standard-footprint (1x1) mob wearing a generated
 * "The ____ ____" name, driven by a fixed movepool of abilities pulled from
 * the all-boss pool ({@link InfiniteAbilityPool}). The moves are used in a
 * strict cycle - move 1, move 2, ..., wrap - so the fight is learnable even
 * though the pool is random. A move that can't fire this turn (no clear lane,
 * summon cap, nothing to pull, ...) is skipped for the next in the cycle; if
 * the whole cycle whiffs the boss falls back to a plain melee/approach.
 *
 * <p>Multi-action turns (every 10 cleared biomes the boss gains an extra
 * action per enemy phase) are handled by CombatManager's turn loop via the
 * entity's {@code aiMemory}, not here - each action is a fresh
 * {@code decideAction} call, so the cycle simply advances once per action.
 */
public class InfiniteBossAI extends BossAI {

    private final List<InfiniteAbilityPool.InfiniteAbility> moves;
    private final String generatedName;
    private int cursor = 0;

    public InfiniteBossAI(List<String> abilityNames, String generatedName) {
        List<InfiniteAbilityPool.InfiniteAbility> resolved = new ArrayList<>();
        for (String name : abilityNames) {
            InfiniteAbilityPool.InfiniteAbility a = InfiniteAbilityPool.byId(name);
            if (a != null) resolved.add(a);
        }
        if (resolved.isEmpty()) {
            // Degenerate spec (bad save / renamed abilities): fight still works.
            resolved.addAll(InfiniteAbilityPool.roll(new java.util.Random(), 4));
        }
        this.moves = resolved;
        this.generatedName = generatedName;
    }

    /** Infinite bosses keep a standard 1x1 combat footprint despite their larger model. */
    @Override
    public int getGridSize() {
        return 1;
    }

    public String getGeneratedName() {
        return generatedName;
    }

    /** The movepool ids, for debug/log output. */
    public List<String> getMoveIds() {
        List<String> ids = new ArrayList<>();
        for (InfiniteAbilityPool.InfiniteAbility a : moves) ids.add(a.id());
        return ids;
    }

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        // Generic rage: regen handled by CombatManager's isEnraged hook, and the
        // pool abilities read isPhaseTwo() for their own escalation.
        self.setEnraged(true);
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        for (int attempts = 0; attempts < moves.size(); attempts++) {
            InfiniteAbilityPool.InfiniteAbility ability = moves.get(cursor % moves.size());
            cursor++;
            EnemyAction action = ability.cast(this, self, arena, playerPos);
            if (action != null && !(action instanceof EnemyAction.Idle)) {
                return action;
            }
        }
        return meleeOrApproach(self, arena, playerPos, isPhaseTwo() ? 2 : 0);
    }
}
