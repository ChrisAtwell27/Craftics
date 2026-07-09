package com.crackedgames.craftics.world;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HardcoreNbtTest {

    @Test
    void hardcoreFlagRoundTrips() {
        CrafticsSavedData.PlayerData pd = new CrafticsSavedData.PlayerData();
        pd.hardcoreIsland = true;
        CrafticsSavedData.PlayerData back = CrafticsSavedData.PlayerData.fromNbt(pd.toNbt());
        assertTrue(back.hardcoreIsland);
    }

    @Test
    void hardcoreFlagDefaultsFalse() {
        CrafticsSavedData.PlayerData back =
            CrafticsSavedData.PlayerData.fromNbt(new CrafticsSavedData.PlayerData().toNbt());
        assertFalse(back.hardcoreIsland);
    }

    @Test
    void pendingWipeConsumeIsOneShot() {
        CrafticsSavedData data = new CrafticsSavedData();
        UUID id = UUID.randomUUID();
        assertFalse(data.consumePendingHardcoreWipe(id));
        data.addPendingHardcoreWipe(id);
        assertTrue(data.consumePendingHardcoreWipe(id));
        assertFalse(data.consumePendingHardcoreWipe(id));
    }

    @Test
    void resetPlayerDataKeepsNameAndInfiniteBest() {
        CrafticsSavedData data = new CrafticsSavedData();
        UUID id = UUID.randomUUID();
        CrafticsSavedData.PlayerData pd = data.getPlayerData(id);
        pd.emeralds = 500;
        pd.hardcoreIsland = true;
        pd.worldSlot = 3;
        pd.lastKnownName = "Steve";
        pd.highestInfiniteScore = 7;
        data.resetPlayerData(id);
        CrafticsSavedData.PlayerData fresh = data.getPlayerData(id);
        assertEquals(0, fresh.emeralds);
        assertFalse(fresh.hardcoreIsland);
        assertEquals(-1, fresh.worldSlot);
        assertEquals("Steve", fresh.lastKnownName);
        assertEquals(7, fresh.highestInfiniteScore);
        assertTrue(fresh.starterGuideGranted);
    }
}
