package com.crackedgames.craftics.scene;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CodeSceneBuilderTest {
    @Test
    void villageLayoutHasBoothsWithContainedNpcs() {
        SceneLayout layout = CodeSceneBuilder.buildLayout(0, 100, 0, "village");
        assertTrue(layout.hasSpawn(), "spawn yaw must be set");
        assertEquals(CodeSceneBuilder.VILLAGE_BOOTHS, layout.stands().size());
        for (StandSlot s : layout.stands()) {
            // NPC tile is inside the booth's clickable rectangle
            assertTrue(s.contains(s.npcX(), s.npcZ()),
                "npc tile must be inside the booth rect");
            // Player walk-up tile is adjacent to the NPC (one tile away, same y level)
            int dx = Math.abs(s.playerX() - s.npcX());
            int dz = Math.abs(s.playerZ() - s.npcZ());
            assertEquals(1, dx + dz, "player stands one tile in front of the NPC");
        }
    }

    @Test
    void barterLayoutBoothsAreDeterministicAndDistinct() {
        SceneLayout a = CodeSceneBuilder.buildLayout(0, 100, 0, "barter_station");
        SceneLayout b = CodeSceneBuilder.buildLayout(0, 100, 0, "barter_station");
        assertEquals(CodeSceneBuilder.BARTER_BOOTHS, a.stands().size());
        // Deterministic: same inputs -> identical booth rectangles in the same order
        for (int i = 0; i < a.stands().size(); i++) {
            assertEquals(a.stands().get(i).minX(), b.stands().get(i).minX());
            assertEquals(a.stands().get(i).minZ(), b.stands().get(i).minZ());
        }
        // Booths do not overlap (distinct minX columns, spaced)
        for (int i = 1; i < a.stands().size(); i++) {
            assertTrue(a.stands().get(i).minX() > a.stands().get(i - 1).maxX(),
                "booths must not overlap in x");
        }
    }

    @Test
    void unknownSceneIsEmpty() {
        SceneLayout layout = CodeSceneBuilder.buildLayout(0, 100, 0, "nope");
        assertTrue(layout.stands().isEmpty());
    }

    @Test
    void villageBoothsCarryFixedOccupants() {
        SceneLayout layout = CodeSceneBuilder.buildLayout(0, 100, 0, "village");
        for (int i = 0; i < layout.stands().size(); i++) {
            assertEquals(SceneBooths.occupantFor("village", i), layout.stands().get(i).occupant());
        }
    }
}
