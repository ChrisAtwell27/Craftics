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
    void detectHybridPairKey_twoMaterialsAnySplit() {
        assertEquals("diamond+leather", HybridSetRegistry.detectHybridPairKey(
            new String[]{"leather", "leather", "diamond", "diamond"}));
        assertEquals("gold+iron", HybridSetRegistry.detectHybridPairKey(
            new String[]{"iron", "iron", "iron", "gold"}));
        assertEquals("copper+leather", HybridSetRegistry.detectHybridPairKey(
            new String[]{"copper", "leather", "leather", "leather"}));
    }

    @Test
    void detectHybridPairKey_rejectsNonHybrids() {
        assertNull(HybridSetRegistry.detectHybridPairKey(
            new String[]{"iron", "iron", "iron", "iron"}));
        assertNull(HybridSetRegistry.detectHybridPairKey(
            new String[]{"iron", "iron", "gold", "diamond"}));
        assertNull(HybridSetRegistry.detectHybridPairKey(
            new String[]{"iron", "iron", "diamond", null}));
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
