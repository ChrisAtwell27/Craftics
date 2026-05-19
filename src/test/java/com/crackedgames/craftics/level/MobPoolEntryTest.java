package com.crackedgames.craftics.level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for {@link MobPoolEntry}'s backward-compatible constructor. */
class MobPoolEntryTest {

    @Test
    void sevenArgConstructor_defaultsAiKeyToEntityType() {
        MobPoolEntry m = new MobPoolEntry("minecraft:zombie", 5, 6, 2, 0, 1, false);
        assertEquals("minecraft:zombie", m.aiKey());
    }

    @Test
    void eightArgConstructor_keepsExplicitAiKey() {
        MobPoolEntry m = new MobPoolEntry(
            "minecraft:husk", 5, 10, 3, 1, 1, false, "minecraft:skeleton");
        assertEquals("minecraft:husk", m.entityTypeId());
        assertEquals("minecraft:skeleton", m.aiKey());
    }

    @Test
    void shorterConstructors_defaultSpeedToZero() {
        MobPoolEntry sevenArg = new MobPoolEntry("minecraft:zombie", 5, 6, 2, 0, 1, false);
        MobPoolEntry eightArg = new MobPoolEntry(
            "minecraft:husk", 5, 10, 3, 1, 1, false, "minecraft:skeleton");
        assertEquals(0, sevenArg.speed());
        assertEquals(0, eightArg.speed());
    }

    @Test
    void nineArgConstructor_storesSpeed() {
        MobPoolEntry m = new MobPoolEntry(
            "aether:moa", 5, 6, 2, 0, 1, false, "minecraft:zombie", 3);
        assertEquals(3, m.speed());
        assertEquals("minecraft:zombie", m.aiKey());
    }
}
