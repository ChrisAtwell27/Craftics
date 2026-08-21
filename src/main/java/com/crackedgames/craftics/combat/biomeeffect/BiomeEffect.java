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

    /**
     * One or two sentences on what this weather actually does to a fight, for the guide book.
     *
     * <p>The book used to print the effect's id and stop - "Sculk Sensors from level 1" tells a
     * player the name of a thing that is about to blind their party and says nothing about the
     * boots that prevent it or the pickaxe that removes it. The effect is the only place that
     * knows, so it is the place that says.
     *
     * <p>Defaults to nothing, so an addon's effect is never forced to write one - it simply
     * keeps the bare name it had before.
     */
    default String description() { return ""; }

    /** Once when combat starts on a level where this effect is active. */
    default void onFightStart(MinibossContext ctx) {}

    /** Every round boundary while active. */
    default void onRoundStart(MinibossContext ctx) {}

    /** Called every combat tick (20/sec) while active - for continuous ambience (falling rain,
     *  drifting snow, looping wind) that the turn-based onRoundStart can't produce. {@code tick}
     *  is a monotonically increasing combat tick counter; gate work with {@code tick % N == 0}. */
    default void onCombatTick(MinibossContext ctx, int tick) {}
}
