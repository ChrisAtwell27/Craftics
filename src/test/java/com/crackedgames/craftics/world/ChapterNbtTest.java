package com.crackedgames.craftics.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChapterNbtTest {

    @Test
    void chapterFieldsRoundTrip() {
        CrafticsSavedData.PlayerData pd = new CrafticsSavedData.PlayerData();
        pd.chapterPlacementPoints = 68;
        pd.chaptersPlaced = 4;
        pd.bestChapterPlacement = 1;
        pd.allTimeInfiniteScore = 142;
        CrafticsSavedData.PlayerData back = CrafticsSavedData.PlayerData.fromNbt(pd.toNbt());
        assertEquals(68, back.chapterPlacementPoints);
        assertEquals(4, back.chaptersPlaced);
        assertEquals(1, back.bestChapterPlacement);
        assertEquals(142, back.allTimeInfiniteScore);
    }

    @Test
    void chapterFieldsDefaultToZero() {
        // An existing save has none of these keys. Reading it must not throw and must
        // not invent a placement out of missing data.
        CrafticsSavedData.PlayerData back =
            CrafticsSavedData.PlayerData.fromNbt(new CrafticsSavedData.PlayerData().toNbt());
        assertEquals(0, back.chapterPlacementPoints);
        assertEquals(0, back.chaptersPlaced);
        assertEquals(0, back.bestChapterPlacement);
        assertEquals(0, back.allTimeInfiniteScore);
    }

    @Test
    void resetPlayerDataKeepsCareerStanding() {
        // Deleting an island wipes progress. It must NOT wipe career standing, which
        // was earned across chapters and is not island progress.
        CrafticsSavedData data = new CrafticsSavedData();
        java.util.UUID id = java.util.UUID.randomUUID();
        CrafticsSavedData.PlayerData pd = data.getPlayerData(id);
        pd.emeralds = 500;
        pd.chapterPlacementPoints = 43;
        pd.chaptersPlaced = 3;
        pd.bestChapterPlacement = 2;
        pd.allTimeInfiniteScore = 99;
        data.resetPlayerData(id);
        CrafticsSavedData.PlayerData fresh = data.getPlayerData(id);
        assertEquals(0, fresh.emeralds);
        assertEquals(43, fresh.chapterPlacementPoints);
        assertEquals(3, fresh.chaptersPlaced);
        assertEquals(2, fresh.bestChapterPlacement);
        assertEquals(99, fresh.allTimeInfiniteScore);
    }

    @Test
    void forgetIslandKeepsCareerStanding() {
        // The other island-wipe path. forgetIsland drops the whole PlayerData entry so
        // it cannot miss an island field, which also means it must put career standing
        // back deliberately or a deletion erases a permanent record.
        CrafticsSavedData data = new CrafticsSavedData();
        java.util.UUID id = java.util.UUID.randomUUID();
        CrafticsSavedData.PlayerData pd = data.getPlayerData(id);
        pd.lastKnownName = "Chapterwalker";
        pd.emeralds = 500;
        pd.worldSlot = 7;
        pd.chapterPlacementPoints = 43;
        pd.chaptersPlaced = 3;
        pd.bestChapterPlacement = 2;
        pd.allTimeInfiniteScore = 99;
        data.forgetIsland(id);
        CrafticsSavedData.PlayerData fresh = data.getPlayerData(id);
        // Island state is gone.
        assertEquals(0, fresh.emeralds);
        assertEquals(-1, fresh.worldSlot);
        assertFalse(data.hasPersonalWorld(id));
        // Career standing is not.
        assertEquals("Chapterwalker", fresh.lastKnownName);
        assertEquals(43, fresh.chapterPlacementPoints);
        assertEquals(3, fresh.chaptersPlaced);
        assertEquals(2, fresh.bestChapterPlacement);
        assertEquals(99, fresh.allTimeInfiniteScore);
    }
}
