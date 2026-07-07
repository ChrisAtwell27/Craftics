package com.crackedgames.craftics.level;

import java.util.List;

/**
 * Per-level parameters for an INFINITE MODE run, threaded through
 * {@code LevelRegistry.get -> LevelGenerator.generate} and carried on the
 * resulting {@link GeneratedLevelDefinition} so CombatManager can read it at
 * spawn/victory time.
 *
 * <p>Infinite runs replay the existing biomes in random order forever, so the
 * campaign ordinal (a finite index into the authored path) can't drive
 * difficulty. {@code virtualOrdinal} replaces it: the number of biomes the
 * party has already cleared this run, growing without bound. All the existing
 * per-ordinal scaling knobs (hpPerBiome, atkPerBiome, ...) apply unchanged.
 *
 * <p>Boss fields are only set on boss levels ({@code bossEntityTypeId != null}):
 * the biome's authored boss is replaced by a random standard-size (1x1) mob
 * carrying {@code abilityNames} moves pulled from the all-boss ability pool,
 * acting {@code actionsPerTurn} times per enemy phase.
 */
public record InfiniteSpec(int virtualOrdinal,
                           String bossEntityTypeId,
                           String bossName,
                           List<String> abilityNames,
                           int actionsPerTurn) {

    /** Non-boss level spec: only the difficulty ordinal matters. */
    public static InfiniteSpec forLevel(int virtualOrdinal) {
        return new InfiniteSpec(virtualOrdinal, null, null, List.of(), 1);
    }

    public boolean hasBossOverride() {
        return bossEntityTypeId != null && !bossEntityTypeId.isEmpty();
    }
}
