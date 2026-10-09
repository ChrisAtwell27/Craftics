package com.crackedgames.craftics.combat.shadow;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reading back what a weapon did.
 *
 * <p>A Shadow uses its copied weapon by pointing the weapon's real code at a stand-in for the
 * player. Whatever that code does to the stand-in - hurt it, burn it, throw it across the room -
 * has to come back out as something that can be done to the actual player. These are the
 * rules for that translation.
 */
class ShadowAftermathTest {

    private static CombatEntity standIn() {
        return new CombatEntity(-1, "minecraft:player", new GridPos(4, 4), 20, 0, 0, 1, 1, 3);
    }

    private static ShadowAftermath.Effect only(ShadowAftermath.Change change) {
        assertEquals(1, change.effects().size(), change.effects().toString());
        return change.effects().get(0);
    }

    @Test
    void aWeaponThatDidNothingLeavesNothingToApply() {
        CombatEntity victim = standIn();
        ShadowAftermath watch = ShadowAftermath.watch(victim);

        assertTrue(watch.read().nothing());
    }

    @Test
    void healthLostComesBackAsDamage() {
        CombatEntity victim = standIn();
        ShadowAftermath watch = ShadowAftermath.watch(victim);

        victim.takeDamage(4);
        victim.takeDamage(2);

        assertEquals(6, watch.read().damage(), "a hit and its bonus damage, added up");
    }

    @Test
    void beingThrownAcrossTheArenaComesBackAsAShove() {
        CombatEntity victim = standIn();
        ShadowAftermath watch = ShadowAftermath.watch(victim);

        victim.setGridPos(new GridPos(6, 3));

        ShadowAftermath.Change change = watch.read();
        assertEquals(2, change.movedX());
        assertEquals(-1, change.movedZ());
    }

    @Test
    void beingSetAlightComesBackAsBurning() {
        CombatEntity victim = standIn();
        ShadowAftermath watch = ShadowAftermath.watch(victim);

        victim.stackBurning(3, 1);

        ShadowAftermath.Effect burn = only(watch.read());
        assertEquals("BURNING", burn.type());
        assertEquals(3, burn.turns());
    }

    @Test
    void sharpnessBleedComesBackAsBleeding() {
        CombatEntity victim = standIn();
        ShadowAftermath watch = ShadowAftermath.watch(victim);

        victim.stackBleed(3);

        ShadowAftermath.Effect bleed = only(watch.read());
        assertEquals("BLEEDING", bleed.type());
        assertEquals(2, bleed.level(), "three stacks is Bleeding III");
    }

    @Test
    void aStunIsSoftenedToSlowness() {
        // Stunning a player is taking their turn. Slowing them is still a fight.
        CombatEntity victim = standIn();
        ShadowAftermath watch = ShadowAftermath.watch(victim);

        victim.setStunned(true);

        ShadowAftermath.Effect slow = only(watch.read());
        assertEquals("SLOWNESS", slow.type());
        assertTrue(slow.turns() > 0);
    }

    @Test
    void shatteredArmourBecomesVulnerableForAWhileNotForTheRestOfTheFight() {
        CombatEntity victim = standIn();
        ShadowAftermath watch = ShadowAftermath.watch(victim);

        victim.addPermanentDefReduction(4);

        ShadowAftermath.Effect exposed = only(watch.read());
        assertEquals("VULNERABLE", exposed.type());
        assertTrue(exposed.turns() > 0 && exposed.turns() <= 5);
    }

    @Test
    void slowingComesBackAsSlowness() {
        CombatEntity victim = standIn();
        ShadowAftermath watch = ShadowAftermath.watch(victim);

        victim.stackSlowness(2, 2);

        ShadowAftermath.Effect slow = only(watch.read());
        assertEquals("SLOWNESS", slow.type());
        assertEquals(2, slow.turns());
        assertEquals(1, slow.level(), "a penalty of two tiles is Slowness II");
    }

    @Test
    void beingFloatedComesBackAsLevitation() {
        // A copied Gravitite Sword: what it does to a monster, it does to the player.
        CombatEntity victim = standIn();
        ShadowAftermath watch = ShadowAftermath.watch(victim);

        victim.applyLevitationState(2, 0);

        ShadowAftermath.Effect lifted = only(watch.read());
        assertEquals("LEVITATION", lifted.type());
        assertEquals(2, lifted.turns());
        assertEquals(0, lifted.level());
    }

    @Test
    void whatWasAlreadyThereIsNotCountedAgain() {
        // The stand-in is built from the real victim's state as it is now. Old damage and old
        // burns are theirs already.
        CombatEntity victim = standIn();
        victim.takeDamage(5);
        victim.stackBurning(2, 0);
        ShadowAftermath watch = ShadowAftermath.watch(victim);

        victim.takeDamage(3);

        ShadowAftermath.Change change = watch.read();
        assertEquals(3, change.damage());
        assertTrue(change.effects().isEmpty());
    }

    @Test
    void aWeaponCanDoSeveralThingsAtOnce() {
        CombatEntity victim = standIn();
        ShadowAftermath watch = ShadowAftermath.watch(victim);

        victim.takeDamage(4);
        victim.stackBurning(3, 0);
        victim.stackBleed(1);
        victim.setGridPos(new GridPos(4, 6));

        ShadowAftermath.Change change = watch.read();
        assertEquals(4, change.damage());
        assertEquals(2, change.movedZ());
        assertEquals(2, change.effects().size());
        assertFalse(change.nothing());
    }
}
