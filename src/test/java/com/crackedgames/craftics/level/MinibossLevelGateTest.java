package com.crackedgames.craftics.level;

import com.crackedgames.craftics.combat.miniboss.MinibossMechanic;
import com.crackedgames.craftics.combat.miniboss.MinibossRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link LevelGenerator#isMinibossLevel}. The gate requires a mechanic registered in
 * {@link MinibossRegistry} for the biome id, so each test that expects {@code true} must register
 * a stub mechanic first (mirrors {@code MinibossRegistryTest.stub}). The registry is static global
 * state shared across tests, so it is cleared in {@link #cleanup()} to avoid bleeding into other
 * test classes.
 */
class MinibossLevelGateTest {

    @AfterEach
    void cleanup() {
        MinibossRegistry.clear();
    }

    private static BiomeTemplate biome(String id, int levelCount) {
        return new BiomeTemplate(
            id, id, 1, levelCount,
            10, 10, 0, 0,
            (Block[]) null, (Block[]) null,
            0f, 0f,
            (MobPoolEntry[]) null, (MobPoolEntry[]) null, null,
            (Item[]) null, (int[]) null,
            (String[]) null, (int[]) null,
            false, null);
    }

    private static MinibossMechanic stub(String id) {
        return new MinibossMechanic() {
            public String biomeId() { return id; }
            public List<LevelDefinition.EnemySpawn> initialSpawns(int w, int h, int o, Random r) { return List.of(); }
            public String introTitle() { return id; }
        };
    }

    @Test
    void firesAtIndex3WhenSevenLevels() {
        MinibossRegistry.register(stub("plains"));
        assertTrue(LevelGenerator.isMinibossLevel(biome("plains", 7), 3, false));
    }

    @Test
    void notAtOtherIndices() {
        MinibossRegistry.register(stub("plains"));
        assertFalse(LevelGenerator.isMinibossLevel(biome("plains", 7), 2, false));
        assertFalse(LevelGenerator.isMinibossLevel(biome("plains", 7), 4, false));
    }

    @Test
    void neverOnBoss() {
        MinibossRegistry.register(stub("plains"));
        assertFalse(LevelGenerator.isMinibossLevel(biome("plains", 7), 3, true));
    }

    @Test
    void neverInThreeLevelBiome() {
        MinibossRegistry.register(stub("dragons_nest"));
        assertFalse(LevelGenerator.isMinibossLevel(biome("dragons_nest", 3), 3, false));
    }

    @Test
    void neverWhenNoMechanicRegistered() {
        // No stub registered for this id - the registry gate must fail closed.
        assertFalse(LevelGenerator.isMinibossLevel(biome("unregistered_biome", 7), 3, false));
    }
}
