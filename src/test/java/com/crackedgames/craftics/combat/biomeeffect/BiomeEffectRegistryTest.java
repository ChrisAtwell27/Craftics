package com.crackedgames.craftics.combat.biomeeffect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BiomeEffectRegistryTest {
    static BiomeEffect stub(String id) {
        return new BiomeEffect() {
            public String id() { return id; }
        };
    }

    @Test
    void registerAndGet() {
        BiomeEffectRegistry.clear();
        BiomeEffectRegistry.register(stub("test_effect_x"));
        assertTrue(BiomeEffectRegistry.has("test_effect_x"));
        assertEquals("test_effect_x", BiomeEffectRegistry.get("test_effect_x").id());
    }

    @Test
    void unknownIdNull() {
        BiomeEffectRegistry.clear();
        assertNull(BiomeEffectRegistry.get("no_such_effect_zzz"));
    }
}
