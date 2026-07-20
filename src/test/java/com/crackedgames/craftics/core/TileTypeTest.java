package com.crackedgames.craftics.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TileTypeTest {
    @Test void rubbleBlocksMovement() { assertFalse(TileType.RUBBLE.walkable); }
    @Test void emberBurnsOnStep()     { assertTrue(TileType.EMBER.walkable); assertEquals(2, TileType.EMBER.damageOnStep); }
    @Test void sporeWalkable()        { assertTrue(TileType.SPORE.walkable); }
    @Test void frostWalkable()        { assertTrue(TileType.FROST.walkable); }
}
