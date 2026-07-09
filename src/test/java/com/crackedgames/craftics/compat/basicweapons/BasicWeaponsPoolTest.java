package com.crackedgames.craftics.compat.basicweapons;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BasicWeaponsPoolTest {
    @Test
    void tiersMapToBasicWeaponsMaterials() {
        assertTrue(BasicWeaponsCompat.weaponTierIds(0).contains("wooden_dagger"));
        assertTrue(BasicWeaponsCompat.weaponTierIds(1).contains("stone_spear"));
        assertTrue(BasicWeaponsCompat.weaponTierIds(2).contains("iron_glaive"));
        assertTrue(BasicWeaponsCompat.weaponTierIds(3).contains("golden_hammer"));
        List<String> t4 = BasicWeaponsCompat.weaponTierIds(4);
        assertTrue(t4.contains("diamond_club"));
        assertTrue(t4.contains("netherite_quarterstaff"));
    }
    @Test
    void outOfRangeEmpty() {
        assertTrue(BasicWeaponsCompat.weaponTierIds(-1).isEmpty());
        assertTrue(BasicWeaponsCompat.weaponTierIds(99).isEmpty());
    }
}
