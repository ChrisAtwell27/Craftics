package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModdedMobWeaponsTest {
    @Test
    void debuffTableMapsKnownFamilies() {
        assertEquals(CombatEffects.EffectType.BLEEDING,
            ModdedMobWeapons.onHitDebuff("diamond_katana").type());
        assertEquals(CombatEffects.EffectType.POISON,
            ModdedMobWeapons.onHitDebuff("toxic_longsword").type());
        assertEquals(CombatEffects.EffectType.SLOWNESS,
            ModdedMobWeapons.onHitDebuff("frostfall").type());
        assertEquals(CombatEffects.EffectType.BURNING,
            ModdedMobWeapons.onHitDebuff("emberblade").type());
        assertEquals(CombatEffects.EffectType.WITHER,
            ModdedMobWeapons.onHitDebuff("waking_lichblade").type());
    }
    @Test
    void stunFamilyMapsToWeaknessNotStun() {
        assertEquals(CombatEffects.EffectType.WEAKNESS,
            ModdedMobWeapons.onHitDebuff("netherite_greathammer").type());
        assertEquals(CombatEffects.EffectType.WEAKNESS,
            ModdedMobWeapons.onHitDebuff("iron_hammer").type());
    }
    @Test
    void unknownWeaponNoDebuff() {
        assertNull(ModdedMobWeapons.onHitDebuff("iron_longsword"));
        assertNull(ModdedMobWeapons.onHitDebuff("random_thing"));
    }
    @Test
    void debuffTurnsAreShort() {
        assertTrue(ModdedMobWeapons.onHitDebuff("diamond_katana").turns() >= 1);
        assertTrue(ModdedMobWeapons.onHitDebuff("diamond_katana").turns() <= 2);
    }
}
