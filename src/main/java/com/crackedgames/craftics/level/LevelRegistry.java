package com.crackedgames.craftics.level;

/**
 * Level registry that delegates to the procedural LevelGenerator.
 * Replaces the old hardcoded Level1Definition/Level2Definition system.
 */
public class LevelRegistry {

    public static LevelDefinition get(int levelNumber) {
        return LevelGenerator.generate(levelNumber, -1);
    }

    public static LevelDefinition get(int levelNumber, int branchChoice) {
        return LevelGenerator.generate(levelNumber, branchChoice);
    }

    public static int getLevelCount() {
        return BiomeRegistry.getTotalLevelCount();
    }
}
