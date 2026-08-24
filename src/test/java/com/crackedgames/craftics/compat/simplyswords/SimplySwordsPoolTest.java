package com.crackedgames.craftics.compat.simplyswords;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SimplySwordsPoolTest {
    @Test
    void tier4HasNetheriteAndRunicAndDiamondPaths() {
        List<String> ids = SimplySwordsCompat.standardWeaponTierIds(4);
        assertTrue(ids.contains("diamond_katana"));
        assertTrue(ids.contains("netherite_longsword"));
        assertTrue(ids.contains("runic_greataxe"));
        assertFalse(ids.contains("iron_katana"));
    }
    @Test
    void tier2IsIron() {
        List<String> ids = SimplySwordsCompat.standardWeaponTierIds(2);
        assertTrue(ids.contains("iron_katana"));
        assertTrue(ids.contains("iron_chakram"));
        assertFalse(ids.contains("gold_katana"));
    }
    @Test
    void tier3IsGold() {
        assertTrue(SimplySwordsCompat.standardWeaponTierIds(3).contains("gold_rapier"));
    }
    @Test
    void woodStoneTiersEmpty() {
        assertTrue(SimplySwordsCompat.standardWeaponTierIds(0).isEmpty());
        assertTrue(SimplySwordsCompat.standardWeaponTierIds(1).isEmpty());
    }
    /** Every unique added in Simply Swords 1.70, by registry path. */
    private static final List<String> NEW_UNIQUES = List.of(
        "bloodwake", "wraithmaw", "soulstalker", "dreadwhisper", "gloampiercer",
        "riftmane", "stormscale", "ionbound_stormscale", "dawnquiver", "the_devourer");

    @Test
    void uniquesAreNeverMistakenForStandardWeapons() {
        // weaponType() decides whether a path is one of the fifteen tiered standards. A unique
        // that happened to end in a type suffix would be claimed by both halves of the compat -
        // registered once as a unique with its own ability, and again as a plain tiered weapon.
        // Simply Swords is free to name a future unique "something_katana", so this is worth
        // holding rather than assuming.
        for (String path : NEW_UNIQUES) {
            assertNull(SimplySwordsCompat.weaponType(path),
                path + " collides with a standard weapon type suffix");
        }
    }

    @Test
    void uniquesAreNotInTheStandardMobPools() {
        // The mob weapon pool is built from tier x type. A unique leaking into it would put a
        // boss-drop weapon in the hands of ordinary spawns.
        for (int tier = 0; tier <= 4; tier++) {
            List<String> ids = SimplySwordsCompat.standardWeaponTierIds(tier);
            for (String path : NEW_UNIQUES) {
                assertFalse(ids.contains(path), path + " leaked into the tier " + tier + " pool");
            }
        }
    }

    @Test
    void standardTypeSuffixesStillResolve() {
        // The other half of the same rule: the tiered weapons must keep being recognised.
        assertEquals("katana", SimplySwordsCompat.weaponType("iron_katana"));
        assertEquals("greathammer", SimplySwordsCompat.weaponType("runic_greathammer"));
        assertNull(SimplySwordsCompat.weaponType("katana"));
        assertNull(SimplySwordsCompat.weaponType(null));
    }

    @Test
    void outOfRangeTierEmpty() {
        assertTrue(SimplySwordsCompat.standardWeaponTierIds(-1).isEmpty());
        assertTrue(SimplySwordsCompat.standardWeaponTierIds(99).isEmpty());
    }
}
