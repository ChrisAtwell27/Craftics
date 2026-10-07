package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The marks a pet carries while it is parked away from home, and the rule that sends it back.
 *
 * <p>A party plays on its leader's island, so a guest's pets wait there between runs. Nothing used
 * to say whose they were once they stood on that island, and nothing happened when the guest left
 * the party: the animals simply stayed on somebody else's island for good.
 */
class PetTagsTest {

    private static final UUID GUEST = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID LEADER = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private static final PetHome PEN = new PetHome("craftics:island/guest", 12.5, 66.0, -7.5, 90f);

    // ─── Tags ────────────────────────────────────────────────────────────

    @Test
    void ownerSurvivesTheTag() {
        String tag = PetTags.ownerTag(GUEST);
        assertEquals("craftics_pet_owner:00000000-0000-0000-0000-00000000000a", tag);
        assertEquals(GUEST, PetTags.ownerOf(List.of("some_other_tag", tag)));
    }

    @Test
    void homeSurvivesTheTag() {
        String tag = PetTags.homeTag(PEN);
        assertEquals("craftics_pet_home:craftics:island/guest|12.5|66.0|-7.5|90.0", tag);
        assertEquals(PEN, PetTags.homeOf(List.of(tag, "some_other_tag")));
    }

    @Test
    void anUntaggedAnimalHasNoOwnerAndNoHome() {
        assertNull(PetTags.ownerOf(List.of("craftics_arena", "anything")));
        assertNull(PetTags.homeOf(List.of("craftics_arena")));
    }

    @Test
    void aMangledTagIsIgnoredRatherThanTrusted() {
        assertNull(PetTags.ownerOf(List.of("craftics_pet_owner:not-a-uuid")));
        assertNull(PetTags.homeOf(List.of("craftics_pet_home:craftics:island/guest|12.5|66.0")));
        assertNull(PetTags.homeOf(List.of("craftics_pet_home:craftics:island/guest|x|66.0|-7.5|90.0")));
        assertNull(PetTags.homeOf(List.of("craftics_pet_home:|12.5|66.0|-7.5|90.0")));
    }

    @Test
    void onlyOurOwnTagsCountAsPetTags() {
        assertTrue(PetTags.isPetTag(PetTags.ownerTag(GUEST)));
        assertTrue(PetTags.isPetTag(PetTags.homeTag(PEN)));
        assertFalse(PetTags.isPetTag("craftics_arena"));
    }

    // ─── Stranded ────────────────────────────────────────────────────────

    @Test
    void aGuestsPetWaitsOnTheLeadersIslandWhileTheyAreInTheParty() {
        // Standing on the leader's island, and the leader's island is where its owner plays.
        assertFalse(PetTags.stranded(GUEST, LEADER, LEADER));
    }

    @Test
    void itIsStrandedTheMomentItsOwnerIsNoLongerInThatParty() {
        // Left, kicked or disbanded: the owner now plays on their own island.
        assertTrue(PetTags.stranded(GUEST, LEADER, GUEST));
    }

    @Test
    void itIsStrandedWhenThePartyMovesToANewLeadersIsland() {
        // The old leader left; the party now plays on someone else's island.
        assertTrue(PetTags.stranded(GUEST, LEADER, OTHER));
    }

    @Test
    void aPetOnItsOwnersIslandIsNeverStranded() {
        assertFalse(PetTags.stranded(GUEST, GUEST, GUEST));
        // Even while its owner is off playing in somebody else's party.
        assertFalse(PetTags.stranded(GUEST, GUEST, LEADER));
    }

    @Test
    void nothingIsStrandedWithoutAnOwnerOrOutsideAnIsland() {
        assertFalse(PetTags.stranded(null, LEADER, LEADER));
        assertFalse(PetTags.stranded(GUEST, null, GUEST));
    }

    // ─── Which home to remember at pickup ────────────────────────────────

    @Test
    void pickedUpOnItsOwnIslandItsHomeIsWhereItStands() {
        PetHome here = new PetHome("craftics:island/guest", 3.5, 65.0, 4.5, 0f);
        // Even if an old tag says otherwise: the player has since moved it to a new pen.
        assertEquals(here, PetTags.homeAtPickup(true, here, PEN));
    }

    @Test
    void pickedUpWhileParkedElsewhereItKeepsTheHomeItCameFrom() {
        PetHome parkedAt = new PetHome("craftics:island/leader", 1.5, 65.0, 0.5, 0f);
        assertEquals(PEN, PetTags.homeAtPickup(false, parkedAt, PEN));
    }

    @Test
    void parkedElsewhereWithNoRememberedHomeItHasNone() {
        // Tamed mid-run and never taken home: the leader's spawn is not its home.
        PetHome parkedAt = new PetHome("craftics:island/leader", 1.5, 65.0, 0.5, 0f);
        assertNull(PetTags.homeAtPickup(false, parkedAt, null));
    }
}
