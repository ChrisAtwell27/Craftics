package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Boiling Bracer lifts "water beats fire" on enemies: with {@link
 * CombatEntity#fireAndWaterCoexist} set, Soaked and Burning tick side by side. Without it, the
 * old rule must hold exactly as {@link SoakedExtinguishesBurningTest} pins it.
 */
class BoilingBracerTest {

    /** A plain zombie - not fire-immune, so it can actually burn. */
    private static CombatEntity boilingMob() {
        CombatEntity mob = new CombatEntity(1, "minecraft:zombie", new GridPos(0, 0), 20, 3, 0, 1);
        mob.setFireAndWaterCoexist(true);
        return mob;
    }

    @Test
    void soakingNoLongerPutsTheFireOut() {
        CombatEntity mob = boilingMob();
        mob.stackBurning(4, 1);
        mob.stackSoaked(3, 0);

        assertTrue(mob.getBurningTurns() > 0, "the bracer keeps the fire lit through the soak");
        assertEquals(1, mob.getBurningAmplifier(), "at its full level");
        assertTrue(mob.isSoaked());
        assertTrue(mob.getBurningTickDamage() > 0, "and the burn still deals damage");
    }

    @Test
    void aSoakedMobCanStillCatchFire() {
        CombatEntity mob = boilingMob();
        mob.stackSoaked(3, 0);
        mob.stackBurning(4, 0);

        assertTrue(mob.getBurningTurns() > 0, "Water Fang first, then Fire Fang, must still ignite");
        assertTrue(mob.isSoaked());
    }

    @Test
    void soulFireLandsOnASoakedMobToo() {
        CombatEntity mob = boilingMob();
        mob.stackSoaked(3, 0);
        mob.stackSoulBurning(3, 0);

        assertTrue(mob.getSoulBurningTurns() > 0);
    }

    /** Fire Fang then Water Fang - the exact order ShovelEnchantEffects applies them in. */
    @Test
    void fireFangThenWaterFang_bothStick() {
        CombatEntity mob = boilingMob();
        mob.stackBurning(2, 0); // Fire Fang I
        mob.stackSoaked(2, 0);  // Water Fang I

        assertTrue(mob.getBurningTurns() > 0);
        assertTrue(mob.isSoaked());
    }

    // (Fire immunity is not covered: isFireImmune reads the live MobEntity, which is null without
    // a Minecraft bootstrap. The bracer only touches the drench checks, not the immunity one.)

    @Test
    void offByDefault_waterStillBeatsFire() {
        CombatEntity mob = new CombatEntity(3, "minecraft:zombie", new GridPos(0, 0), 20, 3, 0, 1);
        assertFalse(mob.fireAndWaterCoexist());
        mob.stackBurning(4, 1);
        mob.stackSoaked(3, 0);
        assertEquals(0, mob.getBurningTurns());
    }

    /** Taking the bracer off mid-fight restores the rule for the next soak. */
    @Test
    void turningItOffRestoresTheDouse() {
        CombatEntity mob = boilingMob();
        mob.stackBurning(4, 1);
        mob.setFireAndWaterCoexist(false);
        mob.stackSoaked(3, 0);
        assertEquals(0, mob.getBurningTurns());
    }
}
