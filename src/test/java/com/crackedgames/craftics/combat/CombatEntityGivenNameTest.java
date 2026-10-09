package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The name a hover is sent.
 *
 * <p>A projectile is carried across the grid by a creature nobody is meant to see. The client
 * works a name out from the entity type when it is sent nothing better, so a Thunder Crystal
 * hovered as an Allay and a fireball as whatever flew it. The name a combatant was given is
 * what has to go, and only when it was given one.
 */
class CombatEntityGivenNameTest {

    private static CombatEntity carriedBy(String entityType) {
        return new CombatEntity(1, entityType, new GridPos(0, 0), 99, 5, 0, 1, -1, 2);
    }

    @Test
    void aProjectileIsKnownByItsOwnNameNotItsCarriers() {
        CombatEntity crystal = carriedBy("minecraft:allay");
        crystal.setProjectile(true);
        assertNull(crystal.getGivenName(), "nothing given yet: the species is all there is");
        assertEquals("Allay", crystal.getDisplayName());

        crystal.setBossDisplayName("Thunder Crystal");
        assertEquals("Thunder Crystal", crystal.getGivenName());
        assertEquals("Thunder Crystal", crystal.getDisplayName());
    }

    @Test
    void anOrdinaryMobHasNoGivenNameToSend() {
        assertNull(carriedBy("minecraft:zombie").getGivenName());
    }

    @Test
    void theOrderIsTheOneTheDisplayNameUses() {
        CombatEntity mob = carriedBy("minecraft:zombie");
        mob.setNameOverride("Bob");
        assertEquals("Bob", mob.getGivenName());
        mob.setStackDisplayName("Zombie Stack");
        assertEquals("Zombie Stack", mob.getGivenName());
        mob.setBossDisplayName("Fireball");
        assertEquals("Fireball", mob.getGivenName());
        assertEquals(mob.getDisplayName(), mob.getGivenName());
    }
}
