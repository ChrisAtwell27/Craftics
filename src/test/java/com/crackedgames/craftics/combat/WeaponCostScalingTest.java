package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeaponCostScalingTest {

    @Test
    void damagePerPointRisesWithCost() {
        assertEquals(2, WeaponCostScaling.damagePerAffinityPoint(1));
        assertEquals(3, WeaponCostScaling.damagePerAffinityPoint(2));
        assertEquals(4, WeaponCostScaling.damagePerAffinityPoint(3));
    }

    @Test
    void procPerPointRisesWithCost() {
        assertEquals(0.02, WeaponCostScaling.procPerAffinityPoint(1), 1e-9);
        assertEquals(0.04, WeaponCostScaling.procPerAffinityPoint(2), 1e-9);
        assertEquals(0.06, WeaponCostScaling.procPerAffinityPoint(3), 1e-9);
    }

    @Test
    void sweepPerPointRisesWithCost() {
        assertEquals(0.03, WeaponCostScaling.sweepPerAffinityPoint(1), 1e-9);
        assertEquals(0.05, WeaponCostScaling.sweepPerAffinityPoint(2), 1e-9);
        assertEquals(0.07, WeaponCostScaling.sweepPerAffinityPoint(3), 1e-9);
    }

    @Test
    void costsOutsideTheBandClamp() {
        // A free (0 AP) swing must not scale below a 1 AP one, and a 5 AP one not past 3 AP.
        assertEquals(2, WeaponCostScaling.damagePerAffinityPoint(0));
        assertEquals(4, WeaponCostScaling.damagePerAffinityPoint(5));
        assertEquals(0.06, WeaponCostScaling.procPerAffinityPoint(9), 1e-9);
    }

    @Test
    void heavierWeaponsGetMorePerSwingButLessPerAp() {
        // The intent: a 3 AP swing earns more per point than a 1 AP swing, but three 1 AP
        // swings still out-earn it per turn, so light weapons stay viable.
        int light = WeaponCostScaling.damagePerAffinityPoint(1) * 3;
        int heavy = WeaponCostScaling.damagePerAffinityPoint(3);
        assertTrue(heavy > WeaponCostScaling.damagePerAffinityPoint(1));
        assertTrue(light > heavy);
    }
}
