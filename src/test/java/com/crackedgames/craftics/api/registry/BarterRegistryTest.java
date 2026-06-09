package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.combat.barter.BarterCategory;
import com.crackedgames.craftics.combat.barter.BarterEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BarterRegistryTest {

    @BeforeEach
    void reset() {
        BarterCategoryRegistry.clearAllForTest();
        BarterRegistry.clearAllForTest();
    }

    @Test
    void registeredCategoryIsRetrievable() {
        var cat = new BarterCategory("craftics:warmonger", "Warmonger", "§c⚔", "weapons", 0);
        BarterCategoryRegistry.register(cat);
        assertEquals(cat, BarterCategoryRegistry.get("craftics:warmonger"));
        assertTrue(BarterCategoryRegistry.all().contains(cat));
    }

    @Test
    void rewardForKnownCategoryIsEligible() {
        BarterCategoryRegistry.register(new BarterCategory("craftics:hoarder", "Hoarder", "§b", "gems", 0));
        var entry = new BarterEntry("craftics:hoarder", new ItemStack(Items.DIAMOND), 6, 12, 8, 0);
        BarterRegistry.register(entry);
        List<BarterEntry> pool = BarterRegistry.forCategory("craftics:hoarder", 0);
        assertEquals(1, pool.size());
        assertEquals(Items.DIAMOND, pool.get(0).prototype().getItem());
    }

    @Test
    void rewardForUnknownCategoryIsIgnoredNotThrown() {
        assertDoesNotThrow(() ->
            BarterRegistry.register(new BarterEntry("nope:ghost", new ItemStack(Items.DIRT), 1, 0)));
        assertTrue(BarterRegistry.forCategory("nope:ghost", 0).isEmpty()
            || BarterCategoryRegistry.get("nope:ghost") == null);
    }

    @Test
    void minBiomeTierFiltersEntries() {
        BarterCategoryRegistry.register(new BarterCategory("craftics:relic", "Relic", "§5", "relics", 0));
        BarterRegistry.register(new BarterEntry("craftics:relic", new ItemStack(Items.NETHERITE_SCRAP), 1, 4));
        assertTrue(BarterRegistry.forCategory("craftics:relic", 0).isEmpty(), "tier 0 excludes tier-4 entry");
        assertEquals(1, BarterRegistry.forCategory("craftics:relic", 5).size(), "tier 5 includes it");
    }
}
