package com.crackedgames.craftics.combat.miniboss;

import com.crackedgames.craftics.level.LevelDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MinibossRegistryTest {
    static MinibossMechanic stub(String id) {
        return new MinibossMechanic() {
            public String biomeId() { return id; }
            public List<LevelDefinition.EnemySpawn> initialSpawns(int w, int h, int o, Random r) { return List.of(); }
            public String introTitle() { return id; }
        };
    }

    @Test
    void registerAndGet() {
        MinibossRegistry.register(stub("test_biome_x"));
        assertTrue(MinibossRegistry.has("test_biome_x"));
        assertEquals("test_biome_x", MinibossRegistry.get("test_biome_x").biomeId());
    }

    @Test
    void unknownBiomeNull() {
        assertNull(MinibossRegistry.get("no_such_biome_zzz"));
    }
}
