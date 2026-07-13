package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-math tests for the shovel (Pet) enchantments. The effects themselves need a live
 * player/arena, so they belong on the in-game checklist; what CAN be pinned here is the
 * level -> magnitude mapping, which is where an off-by-one would quietly change balance.
 */
class ShovelEnchantEffectsTest {

    /** Thunder Fang's shock reaches one tile per level, and nothing at all without the enchant. */
    @Test
    void thunderRadius_scalesOneTilePerLevel() {
        assertEquals(0, ShovelEnchantEffects.thunderRadius(0), "no enchant means no shock");
        assertEquals(1, ShovelEnchantEffects.thunderRadius(1));
        assertEquals(2, ShovelEnchantEffects.thunderRadius(2));
        assertEquals(3, ShovelEnchantEffects.thunderRadius(3));
    }

    /** A negative level can only come from a bug, but it must never invert into a radius. */
    @Test
    void thunderRadius_neverNegative() {
        assertEquals(0, ShovelEnchantEffects.thunderRadius(-1));
    }

    /** Honed is flat +1 damage per level, so its cap of 5 is worth +5 to every pet. */
    @Test
    void honedDamage_isOnePerLevel() {
        assertEquals(1, ShovelEnchantEffects.HONED_DAMAGE_PER_LEVEL);
        assertEquals(5, CrafticsEnchantments.HONED.maxLevel() * ShovelEnchantEffects.HONED_DAMAGE_PER_LEVEL,
            "a maxed Honed shovel should give every pet +5 damage");
    }

    /** The three Fangs all cap at 3, which the level->duration/radius math above assumes. */
    @Test
    void fangs_allCapAtThree() {
        assertEquals(3, CrafticsEnchantments.FIRE_FANG.maxLevel());
        assertEquals(3, CrafticsEnchantments.THUNDER_FANG.maxLevel());
        assertEquals(3, CrafticsEnchantments.WATER_FANG.maxLevel());
        // Thunder's radius is driven straight off the level, so the cap IS the max radius.
        assertEquals(3, ShovelEnchantEffects.thunderRadius(CrafticsEnchantments.THUNDER_FANG.maxLevel()));
    }
}
