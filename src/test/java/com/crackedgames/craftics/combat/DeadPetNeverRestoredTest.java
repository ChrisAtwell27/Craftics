package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A pet that falls in battle must never turn up at the hub afterwards.
 *
 * <p>A collected pet has no copy anywhere: {@code HubPetCollector} discards the real animal
 * from the hub world when the run begins and the run carries only a snapshot. That makes the
 * restore paths the single authority on which animals still exist, and it puts a resurrection
 * exactly one missing filter away - a dead wolf handed to {@code restorePetsToHub} would walk
 * out of the hub floor alive, because the snapshot it restores from is the PRE-combat NBT with
 * a full Health tag.
 *
 * <p>Two independent barriers stop that, and these tests pin the invariant each one rests on.
 * The first is the {@code isAlive()} filter every collector shares ({@code savePets}, the bench
 * sweep, and {@code endCombat}'s safety net). The second is the {@code hp() <= 0} guard inside
 * {@code restorePetsToHub}, which reads a plain number rather than the flag. They only both
 * work if death drives BOTH - which is what is asserted here.
 */
class DeadPetNeverRestoredTest {

    private static CombatEntity pet(int maxHp) {
        return new CombatEntity(1, "minecraft:wolf", new GridPos(0, 0), maxHp, 3, 0, 1);
    }

    @Test
    void deathSetsBothTheFlagAndTheHp() {
        CombatEntity e = pet(20);
        e.takeDamage(20);
        // The isAlive() filter and the hp() <= 0 guard must agree, or one barrier passes an
        // animal the other was relied on to catch.
        assertFalse(e.isAlive(), "a pet at 0 HP must read as dead");
        assertEquals(0, e.getCurrentHp(), "death must zero the HP the restore guard reads");
    }

    @Test
    void overkillClampsToZeroRatherThanGoingNegative() {
        CombatEntity e = pet(20);
        e.takeDamage(9999);
        assertFalse(e.isAlive());
        // <= 0 rather than == 0 in the guard is deliberate, but the clamp is what makes the
        // stored HP a sane number to carry around and compare.
        assertEquals(0, e.getCurrentHp());
    }

    @Test
    void survivingADamagingHitLeavesThePetAliveAndRestorable() {
        CombatEntity e = pet(20);
        e.takeDamage(19);
        assertTrue(e.isAlive(), "a pet on its last HP is still a pet that goes home");
        assertEquals(1, e.getCurrentHp());
    }

    @Test
    void aDeadPetIsNeverMerelyAtLowHp() {
        // Walk the whole range: the flag must flip at exactly zero and nowhere else, so no
        // amount of chip damage can produce a "dead" pet the hp guard would wave through, nor
        // a living one the isAlive filter would drop.
        for (int dmg = 1; dmg <= 30; dmg++) {
            CombatEntity e = pet(30);
            e.takeDamage(dmg);
            assertEquals(dmg == 30, !e.isAlive(),
                "alive flag disagreed with HP after " + dmg + " damage");
            assertEquals(dmg == 30, e.getCurrentHp() <= 0,
                "restore guard disagreed with HP after " + dmg + " damage");
        }
    }

    @Test
    void restoreHpCannotResurrect() {
        // restoreHp clamps to a floor of 1, so it would quietly revive a corpse if a dead pet
        // ever reached the carry-over spawn path. That makes the isAlive() filter upstream
        // load-bearing rather than belt-and-braces: this asserts the sharp edge is really
        // there, so nobody "simplifies" the filter away on the assumption it is redundant.
        CombatEntity e = pet(20);
        e.takeDamage(20);
        assertFalse(e.isAlive());
        e.restoreHp(0);
        assertEquals(1, e.getCurrentHp(),
            "restoreHp floors at 1 - dead pets must be filtered out BEFORE this is reached");
    }
}
