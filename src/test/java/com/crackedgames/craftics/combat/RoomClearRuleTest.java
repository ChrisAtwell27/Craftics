package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The room-clear rule that every victory check in CombatManager routes through.
 *
 * <p>Pure logic over the entity's own flags, so it is testable without an arena or a bootstrap.
 * The wiring (which objects get marked scenery) is covered by the in-game checklist.
 */
class RoomClearRuleTest {

    private static CombatEntity entity(String typeId) {
        return new CombatEntity(-1, typeId, new GridPos(1, 1), 10, 1, 0, 1);
    }

    @Test
    void aLivingHostileBlocksRoomClear() {
        assertTrue(entity("minecraft:zombie").blocksRoomClear());
    }

    @Test
    void aDeadHostileDoesNotBlock() {
        CombatEntity zombie = entity("minecraft:zombie");
        zombie.takeDamage(9999);
        assertFalse(zombie.blocksRoomClear(), "a corpse cannot hold the room open");
    }

    @Test
    void anAllyDoesNotBlock() {
        CombatEntity ally = entity("minecraft:wolf");
        ally.setAlly(true);
        assertFalse(ally.blocksRoomClear());
    }

    @Test
    void sceneryDoesNotBlockEvenWhileAliveAndHostile() {
        // The bug this rule exists to kill: graves and war banners are optional counterplay, so a
        // player who ignores them must still be able to finish the room.
        CombatEntity grave = entity("craftics:grave");
        grave.setScenery(true);
        assertFalse(grave.blocksRoomClear());
    }

    @Test
    void eggSacsStillBlock() {
        // Deliberately must-kill, which is why BroodmotherAI.initEggSacs gates on reachability.
        // If this ever flips, that reachability gate becomes dead code guarding nothing.
        CombatEntity eggSac = entity("craftics:egg_sac");
        eggSac.setInertObject(true);
        assertTrue(eggSac.blocksRoomClear(),
            "egg sacs are must-kill; inert is not the same axis as scenery");
    }

    @Test
    void inertAloneDoesNotExemptAnEntityFromRoomClear() {
        // Guards the two flags staying independent: silencing an object's turn message must never
        // silently make it optional to kill.
        CombatEntity obj = entity("craftics:egg_sac");
        obj.setInertObject(true);
        assertTrue(obj.blocksRoomClear());
        obj.setScenery(true);
        assertFalse(obj.blocksRoomClear());
    }
}
