package com.crackedgames.craftics.world;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which island a player is sent to.
 *
 * <p>A party plays on its leader's island, so every island lookup resolved the leader and used
 * their record. Nothing checked whether the leader actually had an island. A player in a party
 * led by someone islandless was therefore treated as islandless themselves: {@code /home} put
 * them in the lobby at the bare fallback coordinate, {@code /new} still refused (their own
 * record had a world slot), and a friend was told they had no island to visit.
 */
class IslandOwnerResolutionTest {

    private static UUID withIsland(CrafticsSavedData data) {
        UUID id = UUID.randomUUID();
        data.getPlayerData(id).worldSlot = 1;
        return id;
    }

    private static UUID withoutIsland(CrafticsSavedData data) {
        UUID id = UUID.randomUUID();
        data.getPlayerData(id);
        return id;
    }

    /** {@code leader} leads a party {@code member} has joined. */
    private static void party(CrafticsSavedData data, UUID leader, UUID member) {
        Party p = data.createParty(leader);
        data.addPartyInvite(p.getPartyId(), member);
        assertTrue(data.joinParty(member, p.getPartyId()));
    }

    @Test
    void soloPlayerResolvesToThemselves() {
        CrafticsSavedData data = new CrafticsSavedData();
        UUID solo = withIsland(data);
        assertEquals(solo, data.getIslandOwnerFor(solo));
    }

    @Test
    void partyResolvesToTheLeaderWhoHasAnIsland() {
        CrafticsSavedData data = new CrafticsSavedData();
        UUID leader = withIsland(data);
        UUID member = withIsland(data);
        party(data, leader, member);
        // The party plays on the leader's island even though the member has one of their own.
        assertEquals(leader, data.getIslandOwnerFor(member));
    }

    @Test
    void islandlessLeaderFallsBackToTheMembersOwnIsland() {
        CrafticsSavedData data = new CrafticsSavedData();
        UUID leader = withoutIsland(data);
        UUID member = withIsland(data);
        party(data, leader, member);
        assertEquals(member, data.getIslandOwnerFor(member),
            "a leader with no island must not make their party islandless");
        assertTrue(data.hasPersonalWorld(data.getIslandOwnerFor(member)));
    }

    @Test
    void nobodyWithAnIslandStillResolvesToTheLeader() {
        // The genuine "no island yet" case: callers handle it, and the answer must stay the
        // leader so /new builds the party's island rather than a second one per member.
        CrafticsSavedData data = new CrafticsSavedData();
        UUID leader = withoutIsland(data);
        UUID member = withoutIsland(data);
        party(data, leader, member);
        assertEquals(leader, data.getIslandOwnerFor(member));
        assertFalse(data.hasPersonalWorld(data.getIslandOwnerFor(member)));
    }

    @Test
    void hubTeleportPosFollowsTheResolvedOwner() {
        CrafticsSavedData data = new CrafticsSavedData();
        UUID leader = withoutIsland(data);
        UUID member = withIsland(data);
        CrafticsSavedData.PlayerData memberPd = data.getPlayerData(member);
        // Island coordinates are non-negative; getHubSpawnPos treats a negative as "unset".
        memberPd.hubSpawnX = 12;
        memberPd.hubSpawnY = 101;
        memberPd.hubSpawnZ = 7;
        party(data, leader, member);
        assertEquals(new net.minecraft.util.math.BlockPos(12, 101, 7),
            data.getHubTeleportPos(member),
            "an islandless leader must not send the member to a hub position that is not theirs");
    }
}
