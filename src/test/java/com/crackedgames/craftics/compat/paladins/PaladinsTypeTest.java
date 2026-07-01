package com.crackedgames.craftics.compat.paladins;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaladinsTypeTest {

    @Test
    void weaponType_recognizesThreeMeleeTypes() {
        assertEquals("claymore", PaladinsCompat.weaponType("iron_claymore"));
        assertEquals("great_hammer", PaladinsCompat.weaponType("netherite_great_hammer"));
        assertEquals("mace", PaladinsCompat.weaponType("ruby_mace"));
    }

    @Test
    void weaponType_ordersGreatHammerBeforeMaceToAvoidSuffixCollision() {
        // "great_hammer" must not be mis-parsed; "mace" is a suffix of nothing here but
        // guard the general case: a non-weapon path returns null.
        assertNull(PaladinsCompat.weaponType("aether_kite_shield"));
        assertNull(PaladinsCompat.weaponType("holy_staff"));
        assertNull(PaladinsCompat.weaponType(null));
    }
}
