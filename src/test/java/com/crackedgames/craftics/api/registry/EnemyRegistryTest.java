package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.RegistrationSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for {@link EnemyRegistry} — register/get, source tagging, datapack clearing. */
class EnemyRegistryTest {

    private static EnemyEntry entry(String id) {
        return EnemyEntry.builder(id, "minecraft:husk").build();
    }

    @AfterEach
    void cleanup() {
        // EnemyRegistry is static global state. Datapack entries are dropped here;
        // each test uses unique ids so leaked code entries can't collide.
        EnemyRegistry.clearDatapackEntries();
    }

    @Test
    void register_thenGet_returnsEntry() {
        EnemyEntry e = entry("test:get_a");
        EnemyRegistry.register(e);
        assertSame(e, EnemyRegistry.getOrNull("test:get_a"));
        assertTrue(EnemyRegistry.isRegistered("test:get_a"));
    }

    @Test
    void getOrNull_unknownId_returnsNull() {
        assertNull(EnemyRegistry.getOrNull("test:does_not_exist"));
        assertFalse(EnemyRegistry.isRegistered("test:does_not_exist"));
    }

    @Test
    void clearDatapackEntries_dropsDatapackButKeepsCode() {
        EnemyRegistry.register(entry("test:code_b"), RegistrationSource.CODE);
        EnemyRegistry.register(entry("test:pack_b"), RegistrationSource.DATAPACK);

        EnemyRegistry.clearDatapackEntries();

        assertTrue(EnemyRegistry.isRegistered("test:code_b"));
        assertFalse(EnemyRegistry.isRegistered("test:pack_b"));
    }

    @Test
    void reregisterFromCode_clearsDatapackTag() {
        EnemyRegistry.register(entry("test:promote_c"), RegistrationSource.DATAPACK);
        EnemyRegistry.register(entry("test:promote_c"), RegistrationSource.CODE);

        EnemyRegistry.clearDatapackEntries();

        assertTrue(EnemyRegistry.isRegistered("test:promote_c"),
            "code re-registration should survive a datapack clear");
    }
}
