package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A boss driven by a fixed list of abilities from {@link InfiniteAbilityPool},
 * used in a strict cycle so the fight is learnable even when the pool is data
 * driven. A move that cannot fire this turn (no clear lane, summon cap, nothing
 * to pull) is skipped for the next in the cycle; if the whole cycle whiffs the
 * boss falls back to a plain melee or approach.
 *
 * <p>Shared by INFINITE MODE bosses ({@link InfiniteBossAI}, random movepool,
 * generated name) and daily raid bosses ({@link RaidBossAI}, authored movepool,
 * authored name). Multi-action turns are handled by CombatManager's turn loop via
 * the entity's {@code aiMemory}, not here: each action is a fresh
 * {@code decideAction} call, so the cycle simply advances once per action.
 */
public abstract class MovepoolBossAI extends BossAI {

    private final List<InfiniteAbilityPool.InfiniteAbility> moves;
    private int cursor = 0;

    protected MovepoolBossAI(List<InfiniteAbilityPool.InfiniteAbility> moves) {
        this.moves = moves != null ? new ArrayList<>(moves) : new ArrayList<>();
    }

    /** Filter requested ids down to the ones that exist, preserving order, no duplicates. */
    public static List<String> resolveMoveIds(List<String> requested, Set<String> known) {
        Set<String> ordered = new LinkedHashSet<>();
        if (requested == null || known == null) return new ArrayList<>();
        for (String id : requested) {
            if (id != null && known.contains(id)) ordered.add(id);
        }
        return new ArrayList<>(ordered);
    }

    /** Resolve ids straight to abilities through the live pool. */
    public static List<InfiniteAbilityPool.InfiniteAbility> resolve(List<String> ids) {
        List<InfiniteAbilityPool.InfiniteAbility> out = new ArrayList<>();
        if (ids == null) return out;
        for (String id : ids) {
            InfiniteAbilityPool.InfiniteAbility a = InfiniteAbilityPool.byId(id);
            if (a != null) out.add(a);
        }
        return out;
    }

    /** These bosses keep a standard 1x1 combat footprint despite a larger model. */
    @Override
    public int getGridSize() {
        return 1;
    }

    /** The movepool ids, for debug and log output. */
    public List<String> getMoveIds() {
        List<String> ids = new ArrayList<>();
        for (InfiniteAbilityPool.InfiniteAbility a : moves) ids.add(a.id());
        return ids;
    }

    protected boolean hasMoves() {
        return !moves.isEmpty();
    }

    /**
     * Called once per action before the cycle picks a move. Subclasses use it to
     * re-assert per-turn state (a raid boss's permanent buff). Default does nothing.
     */
    protected void beforeAction(CombatEntity self) {
    }

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        // Generic rage: regen is handled by CombatManager's isEnraged hook, and the
        // pool abilities read isPhaseTwo() for their own escalation.
        self.setEnraged(true);
    }

    @Override
    protected final EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        beforeAction(self);
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
