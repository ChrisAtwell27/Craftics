package com.crackedgames.craftics.compat.springtolife;

import com.crackedgames.craftics.compat.BiomeCompatHelper;

/**
 * 1.21.5 "Spring to Life" biome override.
 * <p>
 * On 1.21.5+, cow/pig/chicken should only spawn in biomes that showcase the new
 * warm/cold variants — i.e. removed from the temperate biomes (plains, forest)
 * and added to desert, jungle (warm) and snowy (cold). The variant itself is
 * applied at spawn time by {@link com.crackedgames.craftics.combat.VariantHelper}.
 * <p>
 * On earlier versions this class is a no-op.
 */
public final class SpringToLifeCompat {

    private SpringToLifeCompat() {}

    public static void applyBiomeOverrides() {
        //? if >=1.21.5 {
        /*// Pull cow/pig/chicken out of temperate biomes so only climate-matched
        // variants are seen in combat.
        BiomeCompatHelper.removePassiveMob("plains", "minecraft:cow");
        BiomeCompatHelper.removePassiveMob("plains", "minecraft:pig");
        BiomeCompatHelper.removePassiveMob("forest", "minecraft:chicken");

        // Warm biomes get the full cow/pig/chicken set — stats mirror the
        // plains/forest entries so difficulty stays constant.
        BiomeCompatHelper.appendPassiveMob("desert", "minecraft:cow",     5, 4, 0, 0, 1);
        BiomeCompatHelper.appendPassiveMob("desert", "minecraft:pig",     4, 4, 0, 0, 1);
        BiomeCompatHelper.appendPassiveMob("desert", "minecraft:chicken", 3, 3, 0, 0, 1);
        BiomeCompatHelper.appendPassiveMob("jungle", "minecraft:cow",     5, 4, 0, 0, 1);
        BiomeCompatHelper.appendPassiveMob("jungle", "minecraft:pig",     4, 4, 0, 0, 1);
        BiomeCompatHelper.appendPassiveMob("jungle", "minecraft:chicken", 3, 3, 0, 0, 1);

        // Cold biomes (snowy, mountain).
        BiomeCompatHelper.appendPassiveMob("snowy", "minecraft:cow",     5, 4, 0, 0, 1);
        BiomeCompatHelper.appendPassiveMob("snowy", "minecraft:pig",     4, 4, 0, 0, 1);
        BiomeCompatHelper.appendPassiveMob("snowy", "minecraft:chicken", 3, 3, 0, 0, 1);
        BiomeCompatHelper.appendPassiveMob("mountain", "minecraft:cow",     5, 4, 0, 0, 1);
        BiomeCompatHelper.appendPassiveMob("mountain", "minecraft:pig",     4, 4, 0, 0, 1);
        BiomeCompatHelper.appendPassiveMob("mountain", "minecraft:chicken", 3, 3, 0, 0, 1);
        *///?}
    }
}
