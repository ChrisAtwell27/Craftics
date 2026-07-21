package com.crackedgames.craftics.combat.biomeeffect;

import com.crackedgames.craftics.combat.miniboss.MinibossContext;

/**
 * A per-biome persistent weather effect (blizzard winds, jungle rain, sandstorm...). Runs on
 * every level of its biome from a JSON-configured start level through the boss, driven by the
 * same round hook as MinibossMechanic. One singleton per effect id, registered in
 * BiomeEffectRegistry and referenced from the biome JSON's "biomeEffect" block.
 */
public interface BiomeEffect {
    /** Stable id the biome JSON references, e.g. "blizzard_winds". */
    String id();

    /** Once when combat starts on a level where this effect is active. */
    default void onFightStart(MinibossContext ctx) {}

    /** Every round boundary while active. */
    default void onRoundStart(MinibossContext ctx) {}

    /** Called every combat tick (20/sec) while active - for continuous ambience (falling rain,
     *  drifting snow, looping wind) that the turn-based onRoundStart can't produce. {@code tick}
     *  is a monotonically increasing combat tick counter; gate work with {@code tick % N == 0}. */
    default void onCombatTick(MinibossContext ctx, int tick) {}
}
