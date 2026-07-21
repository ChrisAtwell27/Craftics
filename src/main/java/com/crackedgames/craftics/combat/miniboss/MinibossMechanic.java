package com.crackedgames.craftics.combat.miniboss;

import com.crackedgames.craftics.level.LevelDefinition;
import java.util.List;
import java.util.Random;

/**
 * A per-biome level-4 miniboss: either an event mechanic (special layout + per-round hook)
 * or a literal miniboss enemy. One instance per biome, registered in MinibossRegistry.
 */
public interface MinibossMechanic {
    /** The biome this is the miniboss for. */
    String biomeId();

    /** The enemies/objects placed when the level is built (LevelGenerator side). ordinal =
     *  campaign ordinal in campaign runs, virtualOrdinal in infinite runs. */
    List<LevelDefinition.EnemySpawn> initialSpawns(int width, int height, int ordinal, Random rng);

    /** Runs once when combat starts (banners, one-time state). */
    default void onFightStart(MinibossContext ctx) {}

    /** Runs at each round boundary (waves, hazards, wind). */
    default void onRoundStart(MinibossContext ctx) {}

    /** Called every combat tick (20/sec) while active - for continuous ambience (falling rain,
     *  drifting snow, looping wind) that the turn-based onRoundStart can't produce. {@code tick}
     *  is a monotonically increasing combat tick counter; gate work with {@code tick % N == 0}. */
    default void onCombatTick(MinibossContext ctx, int tick) {}

    /** Extra win condition beyond "all enemies cleared". Default: no extra condition. */
    default boolean isComplete(MinibossContext ctx) { return true; }

    /** Banner shown at fight start, e.g. "§c§l☠ Graveyard". */
    String introTitle();
}
