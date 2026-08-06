package com.crackedgames.craftics.combat.ai.boss;

import java.util.ArrayList;
import java.util.List;

/**
 * INFINITE MODE boss: a visually enlarged, standard-footprint (1x1) mob wearing a
 * generated "The ____ ____" name, driven by a random movepool pulled from the
 * all-boss pool ({@link InfiniteAbilityPool}).
 *
 * <p>The cycle, the phase-two rage and the whiff fallback all live in
 * {@link MovepoolBossAI}; this class only owns the generated name and the
 * degenerate-spec recovery.
 */
public class InfiniteBossAI extends MovepoolBossAI {

    private final String generatedName;

    public InfiniteBossAI(List<String> abilityNames, String generatedName) {
        super(resolveOrReroll(abilityNames));
        this.generatedName = generatedName;
    }

    /** A bad save or renamed abilities must not produce a boss that cannot act. */
    private static List<InfiniteAbilityPool.InfiniteAbility> resolveOrReroll(List<String> abilityNames) {
        List<InfiniteAbilityPool.InfiniteAbility> resolved =
            new ArrayList<>(MovepoolBossAI.resolve(abilityNames));
        if (resolved.isEmpty()) {
            resolved.addAll(InfiniteAbilityPool.roll(new java.util.Random(), 4));
        }
        return resolved;
    }

    public String getGeneratedName() {
        return generatedName;
    }
}
