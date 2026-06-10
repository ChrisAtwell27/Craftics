package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.combat.barter.BarterCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-Java tests for the barter registries' string/registration logic.
 *
 * <p><b>BarterCategoryRegistry</b> is fully covered: it keys off plain strings
 * and never touches Minecraft, so register/get/all/exists and the
 * datapack/clear lifecycle all run in this harness.
 *
 * <p><b>BarterRegistry's entry-keyed behaviour</b> ({@code forCategory}
 * returning a registered entry, the {@code minBiomeTier} tier gate, and
 * category-id matching) is <em>not</em> exercised here because every
 * {@link com.crackedgames.craftics.combat.barter.BarterEntry} requires a
 * non-null prototype {@link net.minecraft.item.ItemStack}, and constructing
 * any {@code ItemStack} (including {@code ItemStack.EMPTY}, whose static
 * initializer runs the same path) throws
 * {@code IllegalArgumentException: Not bootstrapped} in this JUnit
 * environment, which never boots Minecraft. There is no mocking library on
 * the test classpath to stand in for the prototype either. The same
 * limitation is documented on {@code BannerEffectsTest} for its item-lookup
 * tables.
 *
 * <p>The entry-side logic is therefore verified in-game via the smoke
 * checklist (see the implementation plan): with categories registered,
 * confirm that a tier-4 reward entry is excluded from a tier-0 barter pool
 * and included once the biome tier reaches 4+, and that an entry registered
 * under a category appears only when that category is queried. The
 * {@code forCategory} method itself is trivial registration-order filtering
 * (it reads only {@code categoryId} and {@code minBiomeTier}), easy to review
 * by inspection. The query-side contract that is reachable without an entry
 * (no throw, empty result for an unknown category) <em>is</em> covered below.
 */
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
        assertTrue(BarterCategoryRegistry.exists("craftics:warmonger"));
    }

    @Test
    void unknownCategoryIsAbsent() {
        assertNull(BarterCategoryRegistry.get("craftics:ghost"));
        assertFalse(BarterCategoryRegistry.exists("craftics:ghost"));
        assertFalse(BarterCategoryRegistry.all().stream()
            .anyMatch(c -> c.id().equals("craftics:ghost")));
    }

    @Test
    void registeringSameIdReplacesAndKeepsRegistrySmall() {
        BarterCategoryRegistry.register(new BarterCategory("craftics:relic", "First", "§5", "relics", 0));
        BarterCategoryRegistry.register(new BarterCategory("craftics:relic", "Second", "§5", "relics", 3));
        assertEquals(1, BarterCategoryRegistry.all().size(), "same id overwrites, not appends");
        BarterCategory current = BarterCategoryRegistry.get("craftics:relic");
        assertNotNull(current);
        assertEquals("Second", current.displayName());
        assertEquals(3, current.minBiomeTier());
    }

    @Test
    void clearDatapackEntriesRemovesOnlyDatapackCategories() {
        BarterCategoryRegistry.register(
            new BarterCategory("craftics:code", "Code", "§a", "code", 0), RegistrationSource.CODE);
        BarterCategoryRegistry.register(
            new BarterCategory("craftics:pack", "Pack", "§b", "pack", 0), RegistrationSource.DATAPACK);

        BarterCategoryRegistry.clearDatapackEntries();

        assertTrue(BarterCategoryRegistry.exists("craftics:code"), "code-registered survives reload");
        assertFalse(BarterCategoryRegistry.exists("craftics:pack"), "datapack entry is cleared on reload");
    }

    @Test
    void clearAllForTestWipesEverything() {
        BarterCategoryRegistry.register(
            new BarterCategory("craftics:code", "Code", "§a", "code", 0), RegistrationSource.CODE);
        BarterCategoryRegistry.register(
            new BarterCategory("craftics:pack", "Pack", "§b", "pack", 0), RegistrationSource.DATAPACK);

        BarterCategoryRegistry.clearAllForTest();

        assertTrue(BarterCategoryRegistry.all().isEmpty(), "clearAllForTest wipes code and datapack alike");
    }

    @Test
    void forCategoryOnEmptyRegistryReturnsEmptyWithoutThrowing() {
        // Reachable without constructing an entry (which needs a vanilla ItemStack):
        // querying any category/tier against an empty registry yields an empty,
        // mutable, non-null list and never throws.
        var pool = assertDoesNotThrow(
            () -> BarterRegistry.forCategory("craftics:nobody", 0));
        assertNotNull(pool);
        assertTrue(pool.isEmpty());

        // Querying a category that isn't registered as a category either is also fine.
        assertTrue(BarterRegistry.forCategory("nope:ghost", 99).isEmpty());
    }
}
