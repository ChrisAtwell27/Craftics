package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.HighlightLayer;
import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for addon tile overlays.
 *
 * <p>Everything here is plain data - a UUID, a GridPos, a layer - so these run for real rather
 * than around a Minecraft type. What they pin down is the part the highlight pass depends on:
 * one overlay per layer per player, no leaking between layers or players, and a snapshot the
 * addon cannot mutate underneath a fight.
 */
class GridHighlightRegistryTest {

    private static final UUID ALICE = UUID.nameUUIDFromBytes("alice".getBytes());
    private static final UUID BOB = UUID.nameUUIDFromBytes("bob".getBytes());

    @BeforeEach
    @AfterEach
    void reset() {
        GridHighlightRegistry.clearAll();
    }

    @Test
    void nothingSet_meansCrafticsOwnHighlightsStand() {
        assertFalse(GridHighlightRegistry.hasAny(ALICE));
        assertNull(GridHighlightRegistry.get(ALICE, HighlightLayer.MOVE));
    }

    @Test
    void setThenGet_roundTripsTilesAndFlags() {
        GridHighlightRegistry.set(ALICE, HighlightLayer.ATTACK,
            List.of(new GridPos(2, 3), new GridPos(4, 5)), true, 0, 0);

        var layer = GridHighlightRegistry.get(ALICE, HighlightLayer.ATTACK);
        assertNotNull(layer);
        assertTrue(layer.exclusive());
        assertEquals(List.of(new GridPos(2, 3), new GridPos(4, 5)), layer.tiles());
    }

    @Test
    void layersAreIndependent() {
        GridHighlightRegistry.set(ALICE, HighlightLayer.MOVE, List.of(new GridPos(1, 1)), false, 0, 0);
        GridHighlightRegistry.set(ALICE, HighlightLayer.WARNING, List.of(new GridPos(9, 9)), false, 1, 0);

        GridHighlightRegistry.clear(ALICE, HighlightLayer.MOVE);

        // Taking down the move overlay must not take the telegraph with it: an addon that
        // finishes aiming still has an attack coming.
        assertNull(GridHighlightRegistry.get(ALICE, HighlightLayer.MOVE));
        assertNotNull(GridHighlightRegistry.get(ALICE, HighlightLayer.WARNING));
        assertTrue(GridHighlightRegistry.hasAny(ALICE));
    }

    @Test
    void playersAreIndependent() {
        GridHighlightRegistry.set(ALICE, HighlightLayer.MOVE, List.of(new GridPos(1, 1)), true, 0, 0);
        assertFalse(GridHighlightRegistry.hasAny(BOB));

        GridHighlightRegistry.clear(ALICE);
        assertFalse(GridHighlightRegistry.hasAny(ALICE));
    }

    @Test
    void settingALayerTwice_replacesRatherThanAccumulates() {
        GridHighlightRegistry.set(ALICE, HighlightLayer.MOVE, List.of(new GridPos(1, 1)), false, 0, 0);
        GridHighlightRegistry.set(ALICE, HighlightLayer.MOVE, List.of(new GridPos(7, 7)), false, 0, 0);

        // Aiming is a live thing: the second call is where the cursor is now, not one more tile
        // added to where it has been.
        assertEquals(List.of(new GridPos(7, 7)),
            GridHighlightRegistry.get(ALICE, HighlightLayer.MOVE).tiles());
    }

    @Test
    void anEmptyExclusiveOverlay_stillHidesCrafticsOwnTiles() {
        // "Nowhere is a legal target" has to be expressible. Treating an empty list as no
        // overlay would put the player's weapon range back on screen instead.
        GridHighlightRegistry.set(ALICE, HighlightLayer.ATTACK, List.of(), true, 0, 0);

        var layer = GridHighlightRegistry.get(ALICE, HighlightLayer.ATTACK);
        assertNotNull(layer);
        assertTrue(layer.tiles().isEmpty());
        assertTrue(layer.exclusive());
    }

    @Test
    void theOverlayIsASnapshot() {
        List<GridPos> live = new ArrayList<>(List.of(new GridPos(1, 1)));
        GridHighlightRegistry.set(ALICE, HighlightLayer.DANGER, live, false, 0, 0);
        live.add(new GridPos(2, 2));

        // The list is read on every highlight refresh, from whatever thread the fight ticks on.
        // Holding the addon's own list would let it change mid-pass.
        assertEquals(1, GridHighlightRegistry.get(ALICE, HighlightLayer.DANGER).tiles().size());
    }

    @Test
    void nullTiles_isAnEmptyOverlayRatherThanACrash() {
        GridHighlightRegistry.set(ALICE, HighlightLayer.MOVE, null, false, 0, 0);
        assertTrue(GridHighlightRegistry.get(ALICE, HighlightLayer.MOVE).tiles().isEmpty());
    }

    @Test
    void warningDirection_isCarriedWithTheTiles() {
        GridHighlightRegistry.set(ALICE, HighlightLayer.WARNING,
            List.of(new GridPos(3, 3)), false, -1, 1);

        var layer = GridHighlightRegistry.get(ALICE, HighlightLayer.WARNING);
        assertEquals(-1, layer.dirX());
        assertEquals(1, layer.dirZ());
    }
}
