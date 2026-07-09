package com.crackedgames.craftics.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IslandMigrationGeomTest {

    @Test
    void oldHubOriginPerSlot() {
        assertArrayEquals(new int[]{10000, 65, 0},    IslandMigration.oldHubOrigin(0));
        assertArrayEquals(new int[]{10000, 65, 1000}, IslandMigration.oldHubOrigin(1));
        assertArrayEquals(new int[]{10000, 65, 5000}, IslandMigration.oldHubOrigin(5));
    }

    @Test
    void migrationBoxAddsMarginAndClampsY() {
        // schem 32 wide x 48 long, hub at (10000,65,1000)
        IslandMigration.MigrationBox b = IslandMigration.migrationBox(32, 48, 10000, 65, 1000);
        // footprint half-extents: 16 in X, 24 in Z; +64 margin each side
        assertEquals(10000 - 16 - 64, b.minX());
        assertEquals(10000 + 16 + 64, b.maxX());
        assertEquals(1000 - 24 - 64, b.minZ());
        assertEquals(1000 + 24 + 64, b.maxZ());
        assertEquals(0, b.minY());
        assertEquals(160, b.maxY());
    }

    @Test
    void translateMapsHubOriginToNewOrigin() {
        // old hub (10000,65,1000) -> new hub (0,65,0)
        int[] atHub = IslandMigration.translate(10000, 65, 1000, 10000, 65, 1000, 0, 65, 0);
        assertArrayEquals(new int[]{0, 65, 0}, atHub);
        // a block 5 east, 3 up, 7 north of old hub keeps that offset at new hub
        int[] off = IslandMigration.translate(10005, 68, 993, 10000, 65, 1000, 0, 65, 0);
        assertArrayEquals(new int[]{5, 68, -7}, off);
    }
}
