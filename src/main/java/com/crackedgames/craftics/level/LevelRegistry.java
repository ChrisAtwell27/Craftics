package com.crackedgames.craftics.level;

public class LevelRegistry {

    public static LevelDefinition get(int levelNumber) {
        return LevelGenerator.generate(levelNumber, -1);
    }

    public static LevelDefinition get(int levelNumber, int branchChoice) {
        return LevelGenerator.generate(levelNumber, branchChoice);
    }

    public static LevelDefinition get(int levelNumber, int branchChoice, boolean scaleHpPerLevel) {
        return LevelGenerator.generate(levelNumber, branchChoice, scaleHpPerLevel);
    }

    /**
     * Full signature - the caller supplies the island owner's per-level HP scaling toggle
     * and whether the owner has beaten this biome's boss. Use this whenever the island
     * owner is known; the shorter overloads fall back to global config / not-beaten.
     */
    public static LevelDefinition get(int levelNumber, int branchChoice, boolean scaleHpPerLevel,
                                      boolean bossBeaten) {
        return LevelGenerator.generate(levelNumber, branchChoice, scaleHpPerLevel, bossBeaten);
    }

    /**
     * Infinite-mode variant: {@code infiniteSpec} overrides the campaign-ordinal
     * difficulty scaling and (on boss levels) swaps the authored boss for the
     * run's randomized one. Null spec behaves exactly like the overload above.
     */
    public static LevelDefinition get(int levelNumber, int branchChoice, boolean scaleHpPerLevel,
                                      boolean bossBeaten, InfiniteSpec infiniteSpec) {
        return LevelGenerator.generate(levelNumber, branchChoice, scaleHpPerLevel, bossBeaten, infiniteSpec);
    }

    public static int getLevelCount() {
        return BiomeRegistry.getTotalLevelCount();
    }
}
