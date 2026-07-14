package com.crackedgames.craftics.scene;

import com.crackedgames.craftics.api.registry.TraderCategoryRegistry;
import com.crackedgames.craftics.combat.TraderCategory;
import com.crackedgames.craftics.combat.VanillaTraderContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodeSceneBuilderTest {

    @BeforeEach
    void registerVanillaTraders() {
        TraderCategoryRegistry.clearAllForTest();
        VanillaTraderContent.register();
    }

    /** One booth per registered trader - the hall grows with the registry. */
    @Test
    void villageHasOneBoothPerRegisteredTrader() {
        SceneLayout layout = CodeSceneBuilder.buildLayout(0, 100, 0, "village");
        assertTrue(layout.hasSpawn(), "spawn yaw must be set");
        assertEquals(TraderCategoryRegistry.count(), layout.stands().size());
        assertEquals(8, layout.stands().size(), "the eight vanilla traders each get a stall");
    }

    @Test
    void villageBoothsAreWellFormed() {
        SceneLayout layout = CodeSceneBuilder.buildLayout(0, 100, 0, "village");
        for (StandSlot s : layout.stands()) {
            // NPC tile is inside the booth's clickable rectangle
            assertTrue(s.contains(s.npcX(), s.npcZ()), "npc tile must be inside the booth rect");
            // Player walk-up tile is two tiles in front of the NPC (one tile of gap,
            // so the counter sits between them).
            int dx = Math.abs(s.playerX() - s.npcX());
            int dz = Math.abs(s.playerZ() - s.npcZ());
            assertEquals(2, dx + dz, "player stands two tiles in front of the NPC");
        }
    }

    /** An addon trader must widen the hall, not fall off the end of it. */
    @Test
    void registeringATraderAddsABoothAndWidensTheFloor() {
        int boothsBefore = CodeSceneBuilder.boothCount("village");
        int widthBefore = CodeSceneBuilder.floorWidth("village");

        TraderCategoryRegistry.register(
            new TraderCategory("testmod:blacksmith", "Blacksmith", "#", 0),
            (pool, tier) -> { /* no stock needed for a layout test */ });

        assertEquals(boothsBefore + 1, CodeSceneBuilder.boothCount("village"));
        assertTrue(CodeSceneBuilder.floorWidth("village") > widthBefore,
            "the hall must grow to fit the new stall");

        SceneLayout layout = CodeSceneBuilder.buildLayout(0, 100, 0, "village");
        assertEquals(boothsBefore + 1, layout.stands().size());
        // The new trader takes the LAST booth: registration order is booth order.
        assertEquals("testmod:blacksmith",
            layout.stands().get(layout.stands().size() - 1).occupant());
    }

    /** Every booth must fit inside the floor the hall actually places. */
    @Test
    void everyBoothFitsWithinTheFloor() {
        SceneLayout layout = CodeSceneBuilder.buildLayout(0, 100, 0, "village");
        int width = CodeSceneBuilder.floorWidth("village");
        for (StandSlot s : layout.stands()) {
            assertTrue(s.minX() >= 0, "booth starts before the floor: " + s.minX());
            assertTrue(s.maxX() < width, "booth " + s.maxX() + " overruns floor width " + width);
            assertTrue(s.maxZ() < CodeSceneBuilder.FLOOR_DEPTH, "booth overruns floor depth");
        }
    }

    @Test
    void boothsAreDeterministicAndDoNotOverlap() {
        SceneLayout a = CodeSceneBuilder.buildLayout(0, 100, 0, "village");
        SceneLayout b = CodeSceneBuilder.buildLayout(0, 100, 0, "village");
        for (int i = 0; i < a.stands().size(); i++) {
            assertEquals(a.stands().get(i).minX(), b.stands().get(i).minX());
            assertEquals(a.stands().get(i).minZ(), b.stands().get(i).minZ());
        }
        for (int i = 1; i < a.stands().size(); i++) {
            assertTrue(a.stands().get(i).minX() > a.stands().get(i - 1).maxX(),
                "booths must not overlap in x");
        }
    }

    @Test
    void unknownSceneIsEmpty() {
        SceneLayout layout = CodeSceneBuilder.buildLayout(0, 100, 0, "nope");
        assertTrue(layout.stands().isEmpty());
        assertEquals(0, CodeSceneBuilder.boothCount("nope"));
    }

    @Test
    void villageBoothsCarryTheirRegistryOccupant() {
        SceneLayout layout = CodeSceneBuilder.buildLayout(0, 100, 0, "village");
        for (int i = 0; i < layout.stands().size(); i++) {
            assertEquals(SceneBooths.occupantFor("village", i), layout.stands().get(i).occupant());
        }
    }
}
