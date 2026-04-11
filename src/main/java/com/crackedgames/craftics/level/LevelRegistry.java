package com.crackedgames.craftics.level;

public class LevelRegistry {

    public static LevelDefinition get(int levelNumber) {
        return LevelGenerator.generate(levelNumber, -1);
    }

    public static LevelDefinition get(int levelNumber, int branchChoice) {
        return LevelGenerator.generate(levelNumber, branchChoice);
    }

    /**
     * Full signature — the caller supplies the island owner's per-level HP
     * scaling toggle. Use this whenever the island owner is known; the
     * 2-arg overload falls back to the global config.
     */
    public static LevelDefinition get(int levelNumber, int branchChoice, boolean scaleHpPerLevel) {
        return LevelGenerator.generate(levelNumber, branchChoice, scaleHpPerLevel);
    }

    public static int getLevelCount() {
        return BiomeRegistry.getTotalLevelCount();
    }
}
