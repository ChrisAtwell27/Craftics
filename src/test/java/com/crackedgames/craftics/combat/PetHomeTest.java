package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Where a pet stood on its island before the run took it, and when that spot may be used to put
 * it back.
 *
 * <p>Pets used to come home to the island spawn point whatever pen they had been taken from. The
 * rules pinned here are the ones that keep "back where it was" from ever being worse than that:
 * a home is only used on the island it was recorded on, never inside arena territory, never with
 * nothing under it, and lifted clear if something was built on the spot meanwhile.
 */
class PetHomeTest {

    private static final String ISLAND = "craftics:island/aaaa";
    /** Arena territory starts here in these tests; the real line comes from CrafticsSavedData. */
    private static final int ARENAS_FROM_X = 936;

    @Test
    void aRealPlaceIsRecorded() {
        PetHome home = PetHome.of(ISLAND, 12.5, 66.0, -7.5, 90f);
        assertNotNull(home);
        assertEquals(new PetHome(ISLAND, 12.5, 66.0, -7.5, 90f), home);
    }

    @Test
    void aPlaceThatIsNotOneIsNotRecorded() {
        assertNull(PetHome.of(ISLAND, Double.NaN, 66, 0, 0));
        assertNull(PetHome.of(ISLAND, 0, Double.POSITIVE_INFINITY, 0, 0));
        assertNull(PetHome.of(ISLAND, 0, 66, 0, Float.NaN));
        assertNull(PetHome.of("", 0, 66, 0, 0));
        assertNull(PetHome.of(null, 0, 66, 0, 0));
    }

    @Test
    void homeIsOnlyGoodOnTheIslandItWasRecordedOn() {
        PetHome home = PetHome.of(ISLAND, 12.5, 66.0, -7.5, 0f);
        assertTrue(home.usableIn(ISLAND, ARENAS_FROM_X));
        // Same coordinates exist on every island; a guest's pen is not a spot on the host's.
        assertFalse(home.usableIn("craftics:island/bbbb", ARENAS_FROM_X));
    }

    @Test
    void aSpotInsideArenaTerritoryIsNotAHome() {
        PetHome inArena = PetHome.of(ISLAND, 1300.5, 100.0, 4.5, 0f);
        assertFalse(inArena.usableIn(ISLAND, ARENAS_FROM_X));
        PetHome onTheLine = PetHome.of(ISLAND, 936.0, 100.0, 4.5, 0f);
        assertFalse(onTheLine.usableIn(ISLAND, ARENAS_FROM_X));
    }

    @Test
    void anOpenSpotIsUsedExactly() {
        assertEquals(0, PetHome.liftToFit(lift -> true, true));
    }

    @Test
    void aSpotSomeoneBuiltOnLiftsThePetOnTopOfIt() {
        // Blocked at the original height and one above, free two above.
        assertEquals(2, PetHome.liftToFit(lift -> lift >= 2, true));
    }

    @Test
    void aSpotBuriedTooDeepIsGivenUp() {
        assertEquals(-1, PetHome.liftToFit(lift -> lift > PetHome.MAX_LIFT, true));
    }

    @Test
    void aSpotWithNothingUnderItIsGivenUp() {
        // Free to stand in, but the floor is gone: on a void island that is a fall out of the world.
        assertEquals(-1, PetHome.liftToFit(lift -> true, false));
    }

    @Test
    void homeTravelsWithThePetBetweenLevels() {
        PetHome home = PetHome.of(ISLAND, 12.5, 66.0, -7.5, 45f);
        CombatEntity wolf = new CombatEntity(1, "minecraft:wolf", new GridPos(0, 0), 20, 3, 0, 1);
        wolf.setHubHome(home);

        assertEquals(home, HubPetCollector.PetData.fromCombatEntity(wolf, null).home());
    }

    @Test
    void anAnimalTamedMidFightHasNoHome() {
        CombatEntity tamed = new CombatEntity(2, "minecraft:wolf", new GridPos(0, 0), 20, 3, 0, 1);
        assertNull(HubPetCollector.PetData.fromCombatEntity(tamed, null).home());
    }
}
