package com.crackedgames.craftics.combat.shadow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What a Shadow makes of the things a player throws and the sherds a player casts.
 *
 * <p>The real items are written for a player hitting a monster and cannot be pointed the other
 * way, so each one is described here a second time, as plainly as it can be: how far it
 * reaches, how wide it lands, and the one thing it does besides damage. What it costs and how
 * far it reaches come from the real item every time, so an inscribed sherd keeps its
 * inscription.
 */
class ShadowSpellsTest {

    private static ShadowItem sherd(String name) {
        return ShadowSpells.forSherd("minecraft:" + name + "_pottery_sherd", 7, 4, 3);
    }

    @Test
    void aSherdKeepsTheCostAndReachOfTheRealOne() {
        ShadowItem immolation = ShadowSpells.forSherd("minecraft:burn_pottery_sherd", 7, 2, 6);

        assertEquals(7, immolation.slot());
        assertEquals(2, immolation.apCost(), "a Swift inscription made it cheaper for the player too");
        assertEquals(6, immolation.range(), "and a Farsighted one made it reach further");
    }

    @Test
    void immolationBurnsTheTargetAndThoseBesideIt() {
        ShadowItem immolation = sherd("burn");

        assertEquals(ShadowItem.Use.SPELL, immolation.use());
        assertEquals("immolation", immolation.label());
        assertEquals(1, immolation.radius());
        assertEquals("BURNING", immolation.effect());
        assertTrue(immolation.turns() > 0);
    }

    @Test
    void deathMarkWithersButNeverExecutes() {
        // In a player's hands it kills anything under a health threshold outright. Pointed at
        // a player that would be a coin flip on losing the run, so the Shadow's only withers.
        ShadowItem deathMark = sherd("skull");

        assertEquals(ShadowItem.Use.SPELL, deathMark.use());
        assertEquals("WITHER", deathMark.effect());
    }

    @Test
    void entangleSlowsButNeverStuns() {
        // Stunning a player is taking their turn away. Slowing them is a fight.
        assertEquals("SLOWNESS", sherd("sheaf").effect());
    }

    @Test
    void tectonicChargeThrowsItsTargetBack() {
        assertEquals(3, sherd("snort").knockback());
    }

    @Test
    void riptideHookDragsItsTargetIn() {
        assertTrue(sherd("angler").knockback() < 0, "a pull is a knockback the other way");
    }

    @Test
    void tidalSurgeGoesOffAroundTheShadowItself() {
        ShadowItem surge = sherd("flow");

        assertTrue(surge.onSelf());
        assertEquals(2, surge.radius());
        assertEquals(2, surge.range(), "it can only be cast once the target is inside it");
        assertTrue(surge.knockback() > 0);
    }

    @Test
    void soulDrainHealsItsCaster() {
        assertTrue(sherd("mourner").lifesteal());
        assertFalse(sherd("miner").lifesteal());
    }

    @Test
    void healingSherdsHeal() {
        assertEquals(ShadowItem.Use.HEAL, sherd("heart").use());
        assertEquals(ShadowItem.Use.HEAL, sherd("plenty").use());
        assertTrue(sherd("heart").amount() > sherd("plenty").amount());
    }

    @Test
    void buffingSherdsBuff() {
        assertEquals("STRENGTH", sherd("arms_up").effect());
        assertEquals(ShadowItem.Use.BUFF, sherd("arms_up").use());
        assertEquals("RESISTANCE", sherd("shelter").effect());
        assertEquals(ShadowItem.Use.BUFF, sherd("brewer").use());
        assertEquals(ShadowItem.Use.BUFF, sherd("prize").use());
    }

    @Test
    void phaseStepIsABlink() {
        ShadowItem phaseStep = ShadowSpells.forSherd("minecraft:explorer_pottery_sherd", 7, 3, 4);

        assertEquals(ShadowItem.Use.BLINK, phaseStep.use());
        assertEquals(4, phaseStep.range());
    }

    @Test
    void seekerVexesIsASummon() {
        ShadowItem vexes = sherd("archer");

        assertEquals(ShadowItem.Use.SUMMON, vexes.use());
        assertEquals(2, vexes.amount(), "two of them");
    }

    @Test
    void everyAttackingSherdIsASpellTheShadowCanCast() {
        for (String name : new String[]{"scrape", "angler", "heartbreak", "sheaf", "miner", "blade",
                "burn", "snort", "flow", "mourner", "guster", "skull"}) {
            ShadowItem item = sherd(name);
            assertNotNull(item, name);
            assertEquals(ShadowItem.Use.SPELL, item.use(), name);
            assertEquals(100, item.potency(), name + " is worth its whole AP cost");
            assertEquals(1, item.charges(), name + " is cast once");
        }
    }

    @Test
    void sherdsThatOnlyMakeSenseForAPlayerAreLeftAlone() {
        // Guardian Spirit and Petsplosion work through the caster's own pets, and Hex Trap
        // waits for an enemy to walk onto it. None of them has a mirror image.
        assertNull(sherd("friend"));
        assertNull(sherd("howl"));
        assertNull(sherd("danger"));
    }

    @Test
    void anUnknownSherdIsNotGuessedAt() {
        assertNull(ShadowSpells.forSherd("somemod:odd_pottery_sherd", 7, 4, 3));
    }

    @Test
    void aSnowballStingsAndShoves() {
        ShadowItem snowball = ShadowSpells.forThrowable("minecraft:snowball", 3, 1, 16);

        assertEquals(ShadowItem.Use.SPELL, snowball.use());
        assertEquals(1, snowball.knockback());
        assertEquals(0, snowball.radius(), "one tile");
        assertTrue(snowball.potency() < 100, "a snowball is not a sword");
        assertEquals(4, snowball.range());
    }

    @Test
    void throwablesComeInThreesAtMost() {
        // A stack of sixteen snowballs is not sixteen turns of snowballs.
        assertEquals(3, ShadowSpells.forThrowable("minecraft:snowball", 3, 1, 16).charges());
        assertEquals(2, ShadowSpells.forThrowable("minecraft:snowball", 3, 1, 2).charges());
        assertEquals(1, ShadowSpells.forThrowable("minecraft:snowball", 3, 1, 0).charges());
    }

    @Test
    void aFireChargeBurns() {
        assertEquals("BURNING", ShadowSpells.forThrowable("minecraft:fire_charge", 3, 1, 4).effect());
    }

    @Test
    void aWindChargeThrowsItsTargetFurtherThanASnowball() {
        assertEquals(3, ShadowSpells.forThrowable("minecraft:wind_charge", 3, 1, 4).knockback());
    }

    @Test
    void aBrickHitsHarderThanASnowball() {
        assertTrue(ShadowSpells.forThrowable("minecraft:brick", 3, 1, 4).potency()
            > ShadowSpells.forThrowable("minecraft:snowball", 3, 1, 4).potency());
    }

    @Test
    void anEnderPearlIsABlinkWithNoRealLimit() {
        ShadowItem pearl = ShadowSpells.forThrowable("minecraft:ender_pearl", 3, 1, 4);

        assertEquals(ShadowItem.Use.BLINK, pearl.use());
        assertTrue(pearl.range() >= 32);
    }

    @Test
    void tntLandsOnTheTargetAndEveryoneBesideIt() {
        ShadowItem tnt = ShadowSpells.forThrowable("minecraft:tnt", 3, 2, 8);

        assertEquals(ShadowItem.Use.SPELL, tnt.use());
        assertEquals(1, tnt.radius());
        assertEquals(100, tnt.potency());
    }

    @Test
    void anythingElseIsNotAThrowable() {
        assertNull(ShadowSpells.forThrowable("minecraft:cobblestone", 3, 1, 64));
    }
}
