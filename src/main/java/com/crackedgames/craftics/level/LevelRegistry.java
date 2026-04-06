package com.crackedgames.craftics.level;

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
