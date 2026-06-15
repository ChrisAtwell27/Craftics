package com.crackedgames.craftics.api.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for {@link AllyRegistry} - register/get/getAll. */
class AllyRegistryTest {

    private static AllyEntry entry(String id) {
        return AllyEntry.builder(id).build();
    }

    @Test
    void register_thenGet_returnsEntry() {
        AllyEntry e = entry("test:ally_a");
        AllyRegistry.register(e);
        assertSame(e, AllyRegistry.getOrNull("test:ally_a"));
        assertTrue(AllyRegistry.isRegistered("test:ally_a"));
    }

    @Test
    void getOrNull_unknownId_returnsNull() {
        assertNull(AllyRegistry.getOrNull("test:nonexistent_ally"));
        assertFalse(AllyRegistry.isRegistered("test:nonexistent_ally"));
    }

    @Test
    void getAll_containsRegisteredEntries() {
        AllyEntry e = entry("test:ally_b");
        AllyRegistry.register(e);
        assertTrue(AllyRegistry.getAll().contains(e));
    }

    @Test
    void register_replacesSameId() {
        AllyEntry first = AllyEntry.builder("test:ally_c").hp(5).build();
        AllyEntry second = AllyEntry.builder("test:ally_c").hp(99).build();
        AllyRegistry.register(first);
        AllyRegistry.register(second);
        AllyEntry result = AllyRegistry.getOrNull("test:ally_c");
        assertNotNull(result, "expected test:ally_c to be registered");
        assertEquals(99, result.hp());
    }
}
