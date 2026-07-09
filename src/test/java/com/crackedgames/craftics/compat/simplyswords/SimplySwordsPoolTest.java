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
    @Test
    void outOfRangeTierEmpty() {
        assertTrue(SimplySwordsCompat.standardWeaponTierIds(-1).isEmpty());
        assertTrue(SimplySwordsCompat.standardWeaponTierIds(99).isEmpty());
    }
}
