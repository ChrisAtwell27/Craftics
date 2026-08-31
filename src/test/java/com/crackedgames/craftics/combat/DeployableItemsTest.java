package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What counts as placing something, for the Fortress Builder feat.
 *
 * <p>The feat wants five utility items placed in one fight. If eating and throwing counted, it
 * would fall out of any ordinary fight without the player building anything.
 */
class DeployableItemsTest {

    @Test
    @DisplayName("things that leave something standing are deployables")
    void placementsCount() {
        for (String id : new String[]{"minecraft:cobweb", "minecraft:tnt", "minecraft:end_crystal",
                "minecraft:water_bucket", "minecraft:armor_stand", "minecraft:red_bed",
                "minecraft:tall_grass", "minecraft:beehive"}) {
            assertTrue(DeployableItems.isDeployable(id), id + " should count as placed");
        }
    }

    @Test
    @DisplayName("eating, drinking, throwing and swinging are not placements")
    void ordinaryUsesDoNotCount() {
        for (String id : new String[]{"minecraft:bread", "minecraft:golden_apple",
                "minecraft:milk_bucket", "minecraft:potion", "minecraft:snowball",
                "minecraft:ender_pearl", "minecraft:egg", "minecraft:diamond_sword",
                "minecraft:fishing_rod", "minecraft:goat_horn", "minecraft:bucket"}) {
            assertFalse(DeployableItems.isDeployable(id), id + " must not count as placed");
        }
    }

    @Test
    @DisplayName("the feat is reachable")
    void fivePlacementsArePossible() {
        assertTrue(DeployableItems.count() >= 5,
            "Fortress Builder wants 5 placements but only " + DeployableItems.count()
                + " deployables exist");
    }

    @Test
    @DisplayName("an unknown or missing id is not a placement")
    void unknownIdsAreSafe() {
        assertFalse(DeployableItems.isDeployable(null));
        assertFalse(DeployableItems.isDeployable(""));
        assertFalse(DeployableItems.isDeployable("somemod:mystery_block"));
    }
}
