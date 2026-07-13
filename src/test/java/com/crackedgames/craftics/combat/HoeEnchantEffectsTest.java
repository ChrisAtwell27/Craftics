package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-math tests for the hoe (Special) enchantments. The effects need a live player and arena,
 * so they belong on the in-game checklist; what CAN be pinned here is the level -> magnitude
 * mapping, which is where an off-by-one would quietly change balance.
 */
class HoeEnchantEffectsTest {

    /** A maxed Reserving hoe is worth +15% free-AP chance on top of Special affinity's own curve. */
    @Test
    void reserving_addsFivePercentPerLevel() {
        assertEquals(0.05, HoeEnchantEffects.RESERVING_CHANCE_PER_LEVEL, 1e-9);
        assertEquals(0.15,
            CrafticsEnchantments.RESERVING.maxLevel() * HoeEnchantEffects.RESERVING_CHANCE_PER_LEVEL, 1e-9);
    }

    /** Performative is 5% per level, so a maxed hoe double-casts 15% of the time. */
    @Test
    void performative_addsFivePercentPerLevel() {
        assertEquals(0.05, HoeEnchantEffects.PERFORMATIVE_CHANCE_PER_LEVEL, 1e-9);
        assertEquals(0.15,
            CrafticsEnchantments.PERFORMATIVE.maxLevel() * HoeEnchantEffects.PERFORMATIVE_CHANCE_PER_LEVEL, 1e-9);
    }

    /** Radiant caps at 5, so a maxed hoe adds +10 damage against the undead. */
    @Test
    void radiant_addsTwoDamagePerLevel() {
        assertEquals(2, HoeEnchantEffects.RADIANT_DAMAGE_PER_LEVEL);
        assertEquals(10,
            CrafticsEnchantments.RADIANT.maxLevel() * HoeEnchantEffects.RADIANT_DAMAGE_PER_LEVEL);
    }

    /** Medic caps at 3, so a maxed hoe adds +6 HP to every Special heal. */
    @Test
    void medic_addsTwoHpPerLevel() {
        assertEquals(2, HoeEnchantEffects.MEDIC_HEAL_PER_LEVEL);
        assertEquals(6,
            CrafticsEnchantments.MEDIC.maxLevel() * HoeEnchantEffects.MEDIC_HEAL_PER_LEVEL);
    }

    /**
     * Neither proc may ever reach certainty on the enchantment alone - a guaranteed free cast or
     * a guaranteed double cast would break the AP economy outright.
     */
    @Test
    void procChances_stayWellUnderCertainty() {
        double maxReserving =
            CrafticsEnchantments.RESERVING.maxLevel() * HoeEnchantEffects.RESERVING_CHANCE_PER_LEVEL;
        double maxPerformative =
            CrafticsEnchantments.PERFORMATIVE.maxLevel() * HoeEnchantEffects.PERFORMATIVE_CHANCE_PER_LEVEL;
        assertTrue(maxReserving < 1.0, "a maxed Reserving hoe must not guarantee a free cast");
        assertTrue(maxPerformative < 1.0, "a maxed Performative hoe must not guarantee a double cast");
    }

    /** Radiant is the only hoe enchant that goes to 5; the rest cap at 3. */
    @Test
    void levelCaps_matchTheDesign() {
        assertEquals(3, CrafticsEnchantments.RESERVING.maxLevel());
        assertEquals(3, CrafticsEnchantments.PERFORMATIVE.maxLevel());
        assertEquals(5, CrafticsEnchantments.RADIANT.maxLevel());
        assertEquals(3, CrafticsEnchantments.MEDIC.maxLevel());
    }
}
