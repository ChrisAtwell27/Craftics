package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.compat.palegardenbackport.PaleGardenBackportCompat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Creaking has to be recognised by its registry id, never by its Java class.
 *
 * <p>The Pale Garden backport implements it as an ANIMAL - a defensible choice for a mob that
 * stands motionless until you look away, and one that quietly defeated every class-based check
 * in this mod. It survived the arena's "remove naturally-spawned hostiles" sweep, because that
 * sweep asked {@code instanceof HostileEntity}, so Dark Forest fights ran with live Creakings
 * standing in them killing people outside the turn system.
 *
 * <p>These tests cannot construct an entity - that needs a bootstrapped registry - so they pin
 * the thing that actually went wrong: the ID LISTS. Whenever a Creaking is identified by name
 * rather than by class, both flavours have to be named, and forgetting one is the bug.
 */
class CreakingIsNotAnAnimalTest {

    /** Vanilla has it from 1.21.4; the backport supplies it on 1.21.1 to 1.21.3. */
    private static final String VANILLA = "minecraft:creaking";
    private static final String BACKPORT = "palegardenbackport:creaking";

    @Test
    @DisplayName("both flavours of Creaking are recognised")
    void bothFlavoursAreRecognised() {
        assertTrue(PaleGardenBackportCompat.isCreakingEntity(VANILLA));
        assertTrue(PaleGardenBackportCompat.isCreakingEntity(BACKPORT),
            "the backport's id is the one that exists on the shards most people play");
    }

    @Test
    @DisplayName("nothing else is mistaken for one")
    void nothingElseMatches() {
        assertFalse(PaleGardenBackportCompat.isCreakingEntity("minecraft:creaking_heart"),
            "the heart is a block, and matching it here would confuse the two");
        assertFalse(PaleGardenBackportCompat.isCreakingEntity("minecraft:cow"));
        assertFalse(PaleGardenBackportCompat.isCreakingEntity(""));
        assertFalse(PaleGardenBackportCompat.isCreakingEntity(null));
    }

    @Test
    @DisplayName("neither flavour can be walked into a battle party")
    void neitherCanJoinAParty() {
        // It reads as a passive animal to any class-based check, so the only thing keeping it
        // out of a party is being named here. The vanilla id was listed and the backport's was
        // not, which on 1.21.1 is the one that actually exists.
        assertTrue(PartyEligibility.ALWAYS_HOSTILE.contains(VANILLA));
        assertTrue(PartyEligibility.ALWAYS_HOSTILE.contains(BACKPORT));
    }

    @Test
    @DisplayName("the id the mod spawns is one it also recognises")
    void theSpawnedIdIsRecognised() {
        // Craftics picks an id per shard to spawn its own Pale Garden creaking. If that ever
        // stopped matching isCreakingEntity, the mod's OWN creaking would fall through every
        // check that identifies one - including the party exclusion.
        String spawned = PaleGardenBackportCompat.creakingEntityId();
        assertTrue(PaleGardenBackportCompat.isCreakingEntity(spawned),
            "spawns " + spawned + " but does not recognise it");
        assertTrue(PartyEligibility.ALWAYS_HOSTILE.contains(spawned),
            "spawns " + spawned + " but would let a player add it to their party");
    }
}
