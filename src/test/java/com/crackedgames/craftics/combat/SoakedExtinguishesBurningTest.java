package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.combat.CombatEffects.EffectType;
import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Water beats fire. Getting Soaked puts out Burning immediately, and a drenched target can't
 * be re-lit while the water lasts.
 *
 * <p>The rule has to hold in BOTH effect systems - {@link CombatEffects} for players and
 * {@link CombatEntity} for mobs - because they share no code. Testing only one would let the
 * other silently diverge.
 */
class SoakedExtinguishesBurningTest {

    /** A plain zombie - not fire-immune, so it can actually burn. */
    private static CombatEntity makeMob() {
        return new CombatEntity(1, "minecraft:zombie", new GridPos(0, 0), 20, 3, 0, 1);
    }

    // ---- Player side (CombatEffects) ----

    @Test
    void soakingAPlayerPutsOutTheirFire() {
        CombatEffects fx = new CombatEffects();
        fx.addEffect(EffectType.BURNING, 5, 0);
        assertTrue(fx.hasEffect(EffectType.BURNING));

        fx.addEffect(EffectType.SOAKED, 3, 0);

        assertFalse(fx.hasEffect(EffectType.BURNING), "water must put the fire out at once");
        assertTrue(fx.hasEffect(EffectType.SOAKED), "and leave the player drenched");
    }

    @Test
    void aDrenchedPlayerTakesNoBurnDamage() {
        CombatEffects fx = new CombatEffects();
        fx.addEffect(EffectType.BURNING, 5, 0);
        fx.addEffect(EffectType.SOAKED, 3, 0);

        assertEquals(0, fx.applyPerTurnEffects(0),
            "the fire is out, so it must deal no damage this turn");
    }

    @Test
    void aDrenchedPlayerCannotBeRelit() {
        CombatEffects fx = new CombatEffects();
        fx.addEffect(EffectType.SOAKED, 3, 0);

        fx.addEffect(EffectType.BURNING, 5, 0); // a fire proc lands while still wet

        assertFalse(fx.hasEffect(EffectType.BURNING),
            "a soaked player must not catch light - otherwise the douse lasts one proc");
    }

    /** Once the water runs out, fire works again. The immunity is not permanent. */
    @Test
    void fireWorksAgainOnceTheWaterExpires() {
        CombatEffects fx = new CombatEffects();
        fx.addEffect(EffectType.SOAKED, 1, 0);
        fx.tickTurn(); // soaked expires
        assertFalse(fx.hasEffect(EffectType.SOAKED));

        fx.addEffect(EffectType.BURNING, 3, 0);
        assertTrue(fx.hasEffect(EffectType.BURNING), "dry again, so fire catches");
    }

    /** Soaking must not disturb any other effect the player is carrying. */
    @Test
    void soakingLeavesOtherEffectsAlone() {
        CombatEffects fx = new CombatEffects();
        fx.addEffect(EffectType.BURNING, 5, 0);
        fx.addEffect(EffectType.POISON, 4, 0);
        fx.addEffect(EffectType.STRENGTH, 4, 0);

        fx.addEffect(EffectType.SOAKED, 3, 0);

        assertFalse(fx.hasEffect(EffectType.BURNING), "only the fire is doused");
        assertTrue(fx.hasEffect(EffectType.POISON));
        assertTrue(fx.hasEffect(EffectType.STRENGTH));
    }

    // ---- Enemy side (CombatEntity) ----

    @Test
    void soakingAMobPutsOutItsFire() {
        CombatEntity mob = makeMob();
        mob.stackBurning(5, 2);
        assertTrue(mob.getBurningTurns() > 0);

        mob.stackSoaked(3, 1);

        assertEquals(0, mob.getBurningTurns(), "water must put the fire out at once");
        assertEquals(0, mob.getBurningAmplifier(), "and clear the burn's level with it");
        assertTrue(mob.isSoaked(), "leaving the mob drenched");
    }

    @Test
    void aDrenchedMobCannotBeRelit() {
        CombatEntity mob = makeMob();
        mob.stackSoaked(3, 1);

        mob.stackBurning(5, 2);

        assertEquals(0, mob.getBurningTurns(),
            "a soaked mob must not catch light while the water lasts");
    }

    @Test
    void aDrenchedMobTakesNoBurnTickDamage() {
        CombatEntity mob = makeMob();
        mob.stackBurning(5, 3);
        mob.stackSoaked(3, 1);

        assertEquals(0, mob.getBurningTickDamage(), "the fire is out, so it deals nothing");
    }

    /** Soaking must not disturb the mob's other debuffs. */
    @Test
    void soakingAMobLeavesItsOtherDebuffsAlone() {
        CombatEntity mob = makeMob();
        mob.stackBurning(5, 2);
        mob.stackPoison(4, 1);
        mob.stackSlowness(3, 1);

        mob.stackSoaked(3, 1);

        assertEquals(0, mob.getBurningTurns(), "only the fire is doused");
        assertTrue(mob.getPoisonTurns() > 0);
        assertTrue(mob.getSlownessTurns() > 0);
    }

    /** Both systems must agree, or a player and a mob would follow different rules. */
    @Test
    void bothEffectSystemsAgreeThatWaterBeatsFire() {
        CombatEffects player = new CombatEffects();
        player.addEffect(EffectType.BURNING, 5, 0);
        player.addEffect(EffectType.SOAKED, 3, 0);

        CombatEntity mob = makeMob();
        mob.stackBurning(5, 2);
        mob.stackSoaked(3, 1);

        assertFalse(player.hasEffect(EffectType.BURNING));
        assertEquals(0, mob.getBurningTurns());
    }
}
