package com.crackedgames.craftics.api;

import com.crackedgames.craftics.api.registry.EnvironmentRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

/**
 * Registers Craftics' 13 built-in arena environments into {@link EnvironmentRegistry}.
 * Called once from {@code CrafticsMod.onInitialize()}.
 *
 * @since 0.2.0
 */
public final class VanillaEnvironments {

    private VanillaEnvironments() {}

    /** Register every built-in environment. */
    public static void registerAll() {
        reg("plains",         Blocks.GRASS_BLOCK,    Blocks.OAK_FENCE,            Blocks.LANTERN);
        reg("forest",         Blocks.PODZOL,         Blocks.DARK_OAK_FENCE,       Blocks.LANTERN);
        reg("snowy",          Blocks.SNOW_BLOCK,     Blocks.SPRUCE_FENCE,         Blocks.SOUL_LANTERN);
        reg("mountain",       Blocks.STONE,          Blocks.COBBLESTONE_WALL,     Blocks.LANTERN);
        reg("river",          Blocks.GRASS_BLOCK,    Blocks.PRISMARINE_WALL,      Blocks.SEA_LANTERN);
        reg("desert",         Blocks.SAND,           Blocks.SANDSTONE_WALL,       Blocks.LANTERN);
        reg("jungle",         Blocks.MOSS_BLOCK,     Blocks.JUNGLE_FENCE,         Blocks.SHROOMLIGHT);
        reg("cave",           Blocks.STONE,          Blocks.COBBLESTONE_WALL,     Blocks.LANTERN);
        reg("deep_dark",      Blocks.SCULK,          Blocks.DEEPSLATE_BRICK_WALL, Blocks.SOUL_LANTERN);
        reg("nether",         Blocks.NETHERRACK,     Blocks.NETHER_BRICK_FENCE,   Blocks.SOUL_LANTERN);
        reg("crimson_forest", Blocks.CRIMSON_NYLIUM, Blocks.CRIMSON_FENCE,        Blocks.SHROOMLIGHT);
        reg("warped_forest",  Blocks.WARPED_NYLIUM,  Blocks.WARPED_FENCE,         Blocks.SOUL_LANTERN);
        reg("end",            Blocks.END_STONE,      Blocks.PURPUR_PILLAR,        Blocks.END_ROD);
    }

    private static void reg(String id, Block floor, Block post, Block light) {
        EnvironmentRegistry.register(EnvironmentDef.builder(id)
            .floorBlock(floor).postBlock(post).lightBlock(light).build());
    }
}
