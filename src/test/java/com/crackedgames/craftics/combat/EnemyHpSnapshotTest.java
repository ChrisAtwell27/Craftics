package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "Did that action hurt an enemy?" - the question that decides whether a thrown item or a spell
 * gives away a player hiding in tall grass.
 *
 * <p>Weapon swings break cover the moment they are committed. Items and spells have no single
 * place that says "this one was an attack": a snowball, a splash potion and a loaf of bread all
 * go through the same call. So the fight is looked at before and after instead, and cover goes
 * only when something on the other side actually came out of it worse.
 */
class EnemyHpSnapshotTest {

    private static CombatEntity mob(int id, int hp) {
        return new CombatEntity(id, "minecraft:zombie", new GridPos(id, 0), hp, 4, 0, 1, 1, 3);
    }

    @Test
    void nothingTouchedMeansNothingHurt() {
        List<CombatEntity> enemies = List.of(mob(1, 20), mob(2, 20));
        EnemyHpSnapshot before = EnemyHpSnapshot.of(enemies);

        assertFalse(before.anyHurt());
    }

    @Test
    void anEnemyThatLostHealthWasHurt() {
        CombatEntity zombie = mob(1, 20);
        EnemyHpSnapshot before = EnemyHpSnapshot.of(List.of(zombie, mob(2, 20)));

        zombie.takeDamage(3);

        assertTrue(before.anyHurt());
    }

    @Test
    void anEnemyKilledOutrightWasHurt() {
        CombatEntity zombie = mob(1, 5);
        EnemyHpSnapshot before = EnemyHpSnapshot.of(List.of(zombie));

        zombie.takeDamage(50);

        assertFalse(zombie.isAlive());
        assertTrue(before.anyHurt());
    }

    @Test
    void aHitSoakedByAbsorptionStillCounts() {
        // The blow landed; that the mob had a shield of golden hearts does not make it quieter.
        CombatEntity zombie = mob(1, 20);
        zombie.applyAbsorption(10, 3);
        EnemyHpSnapshot before = EnemyHpSnapshot.of(List.of(zombie));

        zombie.takeDamage(4);

        assertEquals(20, zombie.getCurrentHp(), "the absorption took all of it");
        assertTrue(before.anyHurt());
    }

    @Test
    void hurtingYourOwnPetIsNotHittingAnEnemy() {
        CombatEntity pet = mob(1, 20);
        pet.setAlly(true);
        EnemyHpSnapshot before = EnemyHpSnapshot.of(List.of(pet, mob(2, 20)));

        pet.takeDamage(6);

        assertFalse(before.anyHurt());
    }

    @Test
    void healingAnEnemyIsNotHurtingIt() {
        CombatEntity zombie = mob(1, 20);
        zombie.takeDamage(10);
        EnemyHpSnapshot before = EnemyHpSnapshot.of(List.of(zombie));

        zombie.heal(5);

        assertFalse(before.anyHurt());
    }

    @Test
    void oneEnemyHealingDoesNotHideAnotherBeingHurt() {
        // Counted mob by mob, not as a total: +5 on one and -5 on the other is still a hit.
        CombatEntity healed = mob(1, 20);
        healed.takeDamage(10);
        CombatEntity struck = mob(2, 20);
        EnemyHpSnapshot before = EnemyHpSnapshot.of(List.of(healed, struck));

        healed.heal(5);
        struck.takeDamage(5);

        assertTrue(before.anyHurt());
    }

    @Test
    void reinforcementsArrivingAreNotAHit() {
        // A mob that spawned during the action was never in the picture to be compared.
        List<CombatEntity> enemies = new ArrayList<>(List.of(mob(1, 20)));
        EnemyHpSnapshot before = EnemyHpSnapshot.of(enemies);

        CombatEntity late = mob(2, 20);
        enemies.add(late);
        late.takeDamage(4);

        assertFalse(before.anyHurt());
    }

    @Test
    void anEnemyAlreadyDeadCannotBeHurtAgain() {
        CombatEntity corpse = mob(1, 5);
        corpse.takeDamage(50);
        EnemyHpSnapshot before = EnemyHpSnapshot.of(List.of(corpse));

        assertFalse(before.anyHurt());
    }

    @Test
    void anEmptyFightHasNothingToHurt() {
        assertFalse(EnemyHpSnapshot.of(List.of()).anyHurt());
        assertFalse(EnemyHpSnapshot.of(null).anyHurt());
    }
}
