package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The legendary weapon pool's merge step.
 *
 * <p>This list used to be a hand-written array of ids and it had drifted twenty-two weapons behind
 * the uniques Craftics actually registers, which is why the Simply Swords half is now derived
 * rather than listed. The derivation is one small function, and the two ways it could be wrong -
 * dropping the namespace, or letting the same weapon in twice - are worth holding down, because
 * neither would fail loudly: a bad id resolves to nothing and silently shrinks the pool.
 */
class LegendaryPoolTest {

    private static final String[] BOWS = {
        "simplybows:bee_bow/bee_bow", "simplybows:vine_bow/vine_bow"};

    @Test
    @DisplayName("sword paths are namespaced")
    void swordPathsGetTheirNamespace() {
        String[] out = LootboxManager.mergeLegendaryIds(BOWS, List.of("emberblade", "bloodwake"));
        List<String> ids = Arrays.asList(out);
        assertTrue(ids.contains("simplyswords:emberblade"));
        assertTrue(ids.contains("simplyswords:bloodwake"));
    }

    @Test
    @DisplayName("hand-listed bows survive the merge")
    void bowsAreKept() {
        List<String> ids = Arrays.asList(LootboxManager.mergeLegendaryIds(BOWS, List.of("enigma")));
        assertTrue(ids.contains("simplybows:bee_bow/bee_bow"));
        assertTrue(ids.contains("simplybows:vine_bow/vine_bow"));
        assertEquals(3, ids.size());
    }

    @Test
    @DisplayName("no duplicates, however the two halves overlap")
    void noDuplicates() {
        String[] out = LootboxManager.mergeLegendaryIds(
            new String[] {"simplyswords:emberblade", "simplybows:bee_bow/bee_bow"},
            List.of("emberblade", "emberblade", "enigma"));
        assertEquals(out.length, new HashSet<>(Arrays.asList(out)).size());
        assertEquals(3, out.length);
    }

    @Test
    @DisplayName("Simply Swords absent leaves the bows alone")
    void noSwordsStillYieldsBows() {
        // What a plain install without Simply Swords looks like: registeredPaths() is empty.
        assertEquals(BOWS.length, LootboxManager.mergeLegendaryIds(BOWS, List.of()).length);
    }

    @Test
    @DisplayName("neither mod present yields an empty pool, not a broken one")
    void bothAbsent() {
        assertEquals(0, LootboxManager.mergeLegendaryIds(new String[0], List.of()).length);
        assertEquals(0, LootboxManager.mergeLegendaryIds(null, null).length);
    }

    @Test
    @DisplayName("blank entries are dropped rather than becoming a bare namespace")
    void blanksAreDropped() {
        // "simplyswords:" on its own resolves to nothing and would quietly shrink the pool.
        List<String> ids = Arrays.asList(
            LootboxManager.mergeLegendaryIds(new String[] {"", null}, Arrays.asList("", null, "enigma")));
        assertEquals(List.of("simplyswords:enigma"), ids);
    }
}
