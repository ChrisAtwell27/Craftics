package com.crackedgames.craftics.raid;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RaidBossLobbyPackingTest {

    private static List<UUID> joiners(int n) {
        List<UUID> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new UUID(0L, i));
        return out;
    }

    @Test
    void instanceSizeIsEight() {
        assertEquals(8, RaidBossLobbyPacking.INSTANCE_SIZE);
    }

    @Test
    void oneJoinerMakesOneInstance() {
        List<List<UUID>> packed = RaidBossLobbyPacking.pack(joiners(1), 8);
        assertEquals(1, packed.size());
        assertEquals(1, packed.get(0).size());
    }

    @Test
    void eightJoinersStayInOneInstance() {
        List<List<UUID>> packed = RaidBossLobbyPacking.pack(joiners(8), 8);
        assertEquals(1, packed.size());
        assertEquals(8, packed.get(0).size());
    }

    @Test
    void nineJoinersOpenASecondInstanceFillFirst() {
        List<List<UUID>> packed = RaidBossLobbyPacking.pack(joiners(9), 8);
        assertEquals(2, packed.size());
        assertEquals(8, packed.get(0).size());
        assertEquals(1, packed.get(1).size());
    }

    @Test
    void seventeenJoinersMakeThreeInstances() {
        List<List<UUID>> packed = RaidBossLobbyPacking.pack(joiners(17), 8);
        assertEquals(3, packed.size());
        assertEquals(8, packed.get(0).size());
        assertEquals(8, packed.get(1).size());
        assertEquals(1, packed.get(2).size());
    }

    @Test
    void packingIsCappedByMaxInstances() {
        List<List<UUID>> packed = RaidBossLobbyPacking.pack(joiners(40), 2);
        assertEquals(2, packed.size());
        assertEquals(8, packed.get(0).size());
        assertEquals(8, packed.get(1).size());
    }

    @Test
    void noJoinersMakeNoInstances() {
        assertTrue(RaidBossLobbyPacking.pack(List.of(), 8).isEmpty());
    }

    @Test
    void joinOrderIsPreserved() {
        List<UUID> in = joiners(9);
        List<List<UUID>> packed = RaidBossLobbyPacking.pack(in, 8);
        assertEquals(in.get(0), packed.get(0).get(0));
        assertEquals(in.get(7), packed.get(0).get(7));
        assertEquals(in.get(8), packed.get(1).get(0));
    }

    @Test
    void hasRoomTracksTheInstanceCap() {
        assertTrue(RaidBossLobbyPacking.hasRoom(0, 1));
        assertTrue(RaidBossLobbyPacking.hasRoom(7, 1));
        assertFalse(RaidBossLobbyPacking.hasRoom(8, 1));
        assertTrue(RaidBossLobbyPacking.hasRoom(8, 2));
        assertFalse(RaidBossLobbyPacking.hasRoom(16, 2));
    }

    @Test
    void instanceIndexForMapsJoinOrderToInstances() {
        assertEquals(0, RaidBossLobbyPacking.instanceIndexFor(0));
        assertEquals(0, RaidBossLobbyPacking.instanceIndexFor(7));
        assertEquals(1, RaidBossLobbyPacking.instanceIndexFor(8));
        assertEquals(2, RaidBossLobbyPacking.instanceIndexFor(16));
    }
}
