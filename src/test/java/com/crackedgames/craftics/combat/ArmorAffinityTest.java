package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.api.registry.ArmorSetEntry;
import com.crackedgames.craftics.api.registry.ArmorSetRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for the pure per-piece affinity math in {@link DamageType#affinityFromCounts}. */
class ArmorAffinityTest {

    @Test
    void affinity_isPerPieceInHalfPoints() {
        ArmorSetRegistry.register(ArmorSetEntry.builder("testiron")
            .damageBonus(DamageType.CLEAVING, 1).build());
        // Every worn piece counts: each contributes 1 half-point (0.5 affinity),
        // so a full 4-piece set totals 4 half-points (2 whole affinity points).
        assertEquals(1, DamageType.affinityFromCounts(Map.of("testiron", 1), DamageType.CLEAVING));
        assertEquals(2, DamageType.affinityFromCounts(Map.of("testiron", 2), DamageType.CLEAVING));
        assertEquals(3, DamageType.affinityFromCounts(Map.of("testiron", 3), DamageType.CLEAVING));
        assertEquals(4, DamageType.affinityFromCounts(Map.of("testiron", 4), DamageType.CLEAVING));
    }

    @Test
    void affinity_sumsAcrossMaterials() {
        ArmorSetRegistry.register(ArmorSetEntry.builder("testa")
            .damageBonus(DamageType.BLUNT, 1).build());
        ArmorSetRegistry.register(ArmorSetEntry.builder("testb")
            .damageBonus(DamageType.BLUNT, 1).build());
        // 2 of each material → 2 + 2 = 4 half-points (2 whole affinity points).
        assertEquals(4, DamageType.affinityFromCounts(
            Map.of("testa", 2, "testb", 2), DamageType.BLUNT));
    }

    @Test
    void affinity_unregisteredMaterialContributesZero() {
        assertEquals(0, DamageType.affinityFromCounts(Map.of("modded_mystery", 4), DamageType.SLASHING));
    }

    @Test
    void formatAffinityHalfPoints_rendersHalves() {
        assertEquals("0",   DamageType.formatAffinityHalfPoints(0));
        assertEquals("0.5", DamageType.formatAffinityHalfPoints(1));
        assertEquals("1",   DamageType.formatAffinityHalfPoints(2));
        assertEquals("1.5", DamageType.formatAffinityHalfPoints(3));
        assertEquals("2",   DamageType.formatAffinityHalfPoints(4));
    }
}
