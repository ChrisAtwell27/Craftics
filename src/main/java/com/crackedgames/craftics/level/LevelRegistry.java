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

    public static int getLevelCount() {
        return BiomeRegistry.getTotalLevelCount();
    }
}
