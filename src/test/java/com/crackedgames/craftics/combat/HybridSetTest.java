package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.api.registry.HybridSetEntry;
import com.crackedgames.craftics.api.registry.HybridSetRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for the pure hybrid-set detection math and the registry. */
class HybridSetTest {

    @Test
    void pairKey_isOrderIndependent() {
        assertEquals(HybridSetRegistry.pairKey("iron", "diamond"),
                     HybridSetRegistry.pairKey("diamond", "iron"));
        assertEquals("diamond+iron", HybridSetRegistry.pairKey("iron", "diamond"));
    }

    @Test
    void detectHybridPairKey_requiresTwoTwoSplit() {
        // Exactly two of each material, the only valid hybrid configuration.
        assertEquals("diamond+leather", HybridSetRegistry.detectHybridPairKey(
            new String[]{"leather", "leather", "diamond", "diamond"}));
        assertEquals("diamond+leather", HybridSetRegistry.detectHybridPairKey(
            new String[]{"diamond", "leather", "diamond", "leather"}));
    }

    @Test
    void detectHybridPairKey_rejectsNonHybrids() {
        // Full set - not a hybrid.
        assertNull(HybridSetRegistry.detectHybridPairKey(
            new String[]{"iron", "iron", "iron", "iron"}));
        // Three materials - too varied.
        assertNull(HybridSetRegistry.detectHybridPairKey(
            new String[]{"iron", "iron", "gold", "diamond"}));
        // Empty slot - not a hybrid.
        assertNull(HybridSetRegistry.detectHybridPairKey(
            new String[]{"iron", "iron", "diamond", null}));
        // 3/1 split - rejected so a near-full set with one odd piece doesn't
        // accidentally trigger a hybrid bonus.
        assertNull(HybridSetRegistry.detectHybridPairKey(
            new String[]{"iron", "iron", "iron", "gold"}));
        assertNull(HybridSetRegistry.detectHybridPairKey(
            new String[]{"copper", "leather", "leather", "leather"}));
    }

    @Test
    void registerAndResolve_orderIndependent() {
        HybridSetRegistry.register(HybridSetEntry.builder("diamond", "leather")
            .className("Breaker").description("test").effect(HybridEffect.BREAKER).build());
        HybridSetEntry e = HybridSetRegistry.resolve(
            new String[]{"leather", "diamond", "diamond", "leather"});
        assertNotNull(e);
        assertEquals("Breaker", e.className());
        assertSame(e, HybridSetRegistry.get("leather", "diamond"));
        assertSame(e, HybridSetRegistry.get("diamond", "leather"));
    }

    @Test
    void resolve_unregisteredPairIsNull() {
        assertNull(HybridSetRegistry.resolve(
            new String[]{"modded_x", "modded_x", "modded_y", "modded_y"}));
    }

    @Test
    void builder_rejectsNullEffect() {
        assertThrows(NullPointerException.class, () ->
            HybridSetEntry.builder("iron", "diamond").className("X").build());
    }
}
