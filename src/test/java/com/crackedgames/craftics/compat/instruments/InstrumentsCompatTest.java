package com.crackedgames.craftics.compat.instruments;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Null-guard tests for InstrumentsCompat. Vanilla {@code Items.*} are avoided
 * because accessing them requires Minecraft's bootstrap, which does not run in
 * this JUnit environment (see BannerEffectsTest). The null-guarded overloads
 * need no registry, so they verify the safe no-op behavior in isolation.
 */
class InstrumentsCompatTest {

    @Test
    void defForNullIsNullAndIsInstrumentNullIsFalse() {
        // Without the mods loaded in the test env, nothing is registered;
        // the null-guarded paths must return null / false with no registry.
        assertNull(InstrumentsCompat.defFor(null));
        assertFalse(InstrumentsCompat.isInstrument((net.minecraft.item.Item) null));
    }
}
