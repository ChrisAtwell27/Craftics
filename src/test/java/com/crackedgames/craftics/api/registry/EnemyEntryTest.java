package com.crackedgames.craftics.api.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for the pure {@link EnemyEntry} builder — defaults, AI fallback, validation. */
class EnemyEntryTest {

    @Test
    void builder_appliesStatDefaults() {
        EnemyEntry e = EnemyEntry.builder("craftics:husk", "minecraft:husk").build();
        assertEquals(6, e.hp());
        assertEquals(2, e.attack());
        assertEquals(0, e.defense());
        assertEquals(1, e.range());
    }

    @Test
    void builder_aiKeyDefaultsToEntityType() {
        EnemyEntry e = EnemyEntry.builder("craftics:husk", "minecraft:husk").build();
        assertEquals("minecraft:husk", e.aiKey());
    }

    @Test
    void builder_aiKeyCanDivergeFromEntityType() {
        EnemyEntry e = EnemyEntry.builder("craftics:parched_husk", "minecraft:husk")
            .ai("minecraft:skeleton")
            .build();
        assertEquals("minecraft:husk", e.entityTypeId());
        assertEquals("minecraft:skeleton", e.aiKey());
    }

    @Test
    void builder_storesAllStats() {
        EnemyEntry e = EnemyEntry.builder("craftics:brute", "minecraft:zombie")
            .hp(20).attack(5).defense(3).range(2)
            .build();
        assertEquals("craftics:brute", e.id());
        assertEquals(20, e.hp());
        assertEquals(5, e.attack());
        assertEquals(3, e.defense());
        assertEquals(2, e.range());
        assertEquals("minecraft:zombie", e.entityTypeId());
        assertEquals("minecraft:zombie", e.aiKey());
    }

    @Test
    void build_rejectsNullId() {
        assertThrows(IllegalStateException.class,
            () -> EnemyEntry.builder(null, "minecraft:husk").build());
    }

    @Test
    void build_rejectsNullEntityType() {
        assertThrows(IllegalStateException.class,
            () -> EnemyEntry.builder("craftics:husk", null).build());
    }

    @Test
    void build_rejectsBlankId() {
        assertThrows(IllegalStateException.class,
            () -> EnemyEntry.builder("  ", "minecraft:husk").build());
    }

    @Test
    void build_rejectsBlankEntityType() {
        assertThrows(IllegalStateException.class,
            () -> EnemyEntry.builder("craftics:husk", "").build());
    }
}
