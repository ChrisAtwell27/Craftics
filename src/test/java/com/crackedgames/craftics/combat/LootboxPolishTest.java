package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that a lootbox only rolls the enchant/trim polish pass on the types that <em>say</em>
 * they do.
 *
 * <p>This is a disclosure rule, not a balance one. The odds screen and the {@code /craftics
 * lootbox odds} output both publish the polish chances for weapons and armor and for nothing
 * else, while the roll itself used to run on every type - so a Material Crate handed out
 * enchanted materials that its own published odds said nothing about.
 *
 * <p>A loot box whose stated odds are incomplete is worse than one with a bad drop table, which
 * is why this is pinned rather than left to be noticed again.
 */
class LootboxPolishTest {

    /** The types whose odds displays disclose an enchant (or trim) roll. */
    private static final Set<LootboxManager.Type> DISCLOSED =
        EnumSet.of(LootboxManager.Type.WEAPONS, LootboxManager.Type.ARMOR);

    @Test
    void onlyWeaponsAndArmorRollThePolishPass() {
        for (LootboxManager.Type type : LootboxManager.Type.values()) {
            assertEquals(DISCLOSED.contains(type), LootboxManager.polishes(type),
                type + " polish behaviour must match what its odds display promises");
        }
    }

    @Test
    void materialsNeverRollAnEnchant() {
        // The reported bug, named. A material crate is bought for bulk resources; an enchanted
        // one is not a bonus, it is an undocumented outcome.
        assertFalse(LootboxManager.polishes(LootboxManager.Type.MATERIALS));
    }

    @Test
    void weaponsAndArmorStillDo() {
        // The other half of the rule: fixing the leak must not silently remove the feature the
        // two crates are actually sold on.
        assertTrue(LootboxManager.polishes(LootboxManager.Type.WEAPONS));
        assertTrue(LootboxManager.polishes(LootboxManager.Type.ARMOR));
    }

    @Test
    void everyTypeHasAnAnswer() {
        // Guards the enum growing without this being revisited: a new type defaults to "no
        // polish", which is the safe direction, but it must at least be a decision that was
        // made rather than an exception thrown mid-roll.
        for (LootboxManager.Type type : LootboxManager.Type.values()) {
            assertDoesNotThrow(() -> LootboxManager.polishes(type), type.name());
        }
    }
}
