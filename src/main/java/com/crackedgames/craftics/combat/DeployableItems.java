package com.crackedgames.craftics.combat;

import java.util.Set;

/**
 * Which items count as "placing something" for the Fortress Builder feat.
 *
 * <p>The feat asks for five utility items placed in one fight, so it needs an answer to what
 * placing means. The rule used here is narrow on purpose: the item has to leave something standing
 * on the battlefield afterwards - cover, a hazard, a block, a trap. Eating, drinking, throwing and
 * swinging are all uses, and none of them build a fortress.
 *
 * <p>Keyed by registry id rather than {@code Item} so the list can be tested. Items cannot be
 * referenced from a unit test here: there is no Minecraft bootstrap, so touching {@code Items}
 * throws in its static initialiser.
 */
public final class DeployableItems {

    private DeployableItems() {}

    private static final Set<String> DEPLOYABLE = Set.of(
        // Cover and obstacles
        "minecraft:cobweb", "minecraft:tall_grass", "minecraft:large_fern",
        "minecraft:beehive", "minecraft:armor_stand",
        // Hazards that stay on the floor
        "minecraft:tnt", "minecraft:end_crystal", "minecraft:respawn_anchor",
        "minecraft:ice", "minecraft:packed_ice", "minecraft:blue_ice", "minecraft:frosted_ice",
        // Terrain the player pours or drops
        "minecraft:water_bucket", "minecraft:sponge", "minecraft:turtle_egg",
        // Beds, which Craftics places as either an anchor or a bomb depending on region
        "minecraft:white_bed", "minecraft:orange_bed", "minecraft:magenta_bed",
        "minecraft:light_blue_bed", "minecraft:yellow_bed", "minecraft:lime_bed",
        "minecraft:pink_bed", "minecraft:gray_bed", "minecraft:light_gray_bed",
        "minecraft:cyan_bed", "minecraft:purple_bed", "minecraft:blue_bed",
        "minecraft:brown_bed", "minecraft:green_bed", "minecraft:red_bed",
        "minecraft:black_bed"
    );

    /** Whether using this item leaves something standing on the arena floor. */
    public static boolean isDeployable(String registryId) {
        return registryId != null && DEPLOYABLE.contains(registryId);
    }

    /** How many distinct deployables exist, so a test can prove the feat is reachable. */
    public static int count() {
        return DEPLOYABLE.size();
    }
}
