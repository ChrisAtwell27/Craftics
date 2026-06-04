package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure-logic tests for {@link MoreTotemsEffects}. */
class MoreTotemsEffectsTest {

    @Test
    void explosionDamage_halfMaxHpPlusOrdinal() {
        // 20 max HP, biome ordinal 3 -> 10 + 3 = 13.
        assertEquals(13, MoreTotemsEffects.explosionDamage(20, 3));
    }

    @Test
    void explosionDamage_oddMaxHpFloorsHalf() {
        // 7 max HP, ordinal 0 -> floor(3.5) + 0 = 3.
        assertEquals(3, MoreTotemsEffects.explosionDamage(7, 0));
    }

    @Test
    void explosionDamage_neverBelowOne() {
        assertEquals(1, MoreTotemsEffects.explosionDamage(1, 0));
        assertEquals(1, MoreTotemsEffects.explosionDamage(0, 0));
    }
}
