package com.crackedgames.craftics.level.campaign;

import com.crackedgames.craftics.api.RegistrationSource;

import java.util.List;

/**
 * Craftics' built-in {@code craftics:vanilla} campaign, authored in code.
 *
 * <p>This is the code mirror of the shipped {@code data/craftics/craftics/campaigns/vanilla.json}.
 * Both describe the same 18-biome progression (the structure previously hardcoded in
 * {@code BiomePath}); the JSON is the authoring "source of truth" and this class is the safety
 * net that seeds the same campaign from code so it always exists even without the resource -
 * mirroring how {@link com.crackedgames.craftics.api.VanillaDialogue} seeds built-in dialogue.
 *
 * <h2>Structure</h2>
 * <ul>
 *   <li><b>Overworld</b> (9): plains, desert, jungle, forest, river, snowy, mountain, cave,
 *       deep_dark.</li>
 *   <li><b>The Nether</b> (5): nether_wastes, soul_sand_valley, crimson_forest, warped_forest,
 *       basalt_deltas.</li>
 *   <li><b>The End</b> (4): outer_end_islands, end_city, chorus_grove, dragons_nest (the final
 *       boss = last node of the last region).</li>
 * </ul>
 *
 * <h2>Branch (the vanilla warm/cool swap)</h2>
 * <p>Branch choice 1 swaps the contiguous overworld segment {@code [desert, jungle, forest]} with
 * {@code [snowy, mountain]} around the {@code river} pivot. This exactly reproduces the legacy
 * {@code BiomePath.getPath(1)}: {@code [plains, snowy, mountain, river, desert, jungle, forest,
 * cave, deep_dark]}.
 *
 * <h2>Per-node vs per-region presentation</h2>
 * <p>Legacy {@code BiomePath} carried color/icon/map_color <em>per biome</em>. The campaign model
 * carries color/icon/map_color <em>per region</em>; {@link CampaignNode} only has an optional
 * {@code labelOverride}. So each biome's display name becomes its node label, and each region
 * takes a representative theme color/icon/map_color (the first biome's values per region):
 * overworld = plains' {@code §a / ☀ / AA55FF55}, nether = nether_wastes' {@code §c / ♨ / AAFF5555},
 * end = outer_end_islands' {@code §d / ✦ / AACC88FF}. The per-biome map tint is not expressible in
 * the current model and is intentionally dropped.
 *
 * <p>{@link #build()} is pure (Minecraft-free) so it is unit-testable under plain JUnit;
 * {@link #register()} is the only Minecraft-coupled call (it touches {@link CampaignManager}).
 *
 * @since 0.2.2
 */
public final class VanillaCampaign {

    private VanillaCampaign() {} // no instances

    /** Overworld theme glyph - green sun (matches BiomePath plains icon). */
    private static final String OVERWORLD_ICON = "☀";
    /** Nether theme glyph - hot springs (matches BiomePath nether_wastes icon). */
    private static final String NETHER_ICON = "♨";
    /** End theme glyph - light-purple star (matches BiomePath outer_end_islands icon). */
    private static final String END_ICON = "✦";

    /**
     * Build the built-in vanilla campaign. Pure: no Minecraft types, no static registry writes -
     * safe to call from a unit test.
     *
     * @return the {@code craftics:vanilla} campaign, structurally identical to vanilla.json
     */
    public static Campaign build() {
        CampaignRegion overworld = CampaignRegion.builder("overworld")
            .displayName("Overworld")
            .color("§a")
            .icon(OVERWORLD_ICON)
            .mapColor(0xAA55FF55)
            .node("plains", "Plains")
            .node("desert", "Scorching Desert")
            .node("jungle", "Dense Jungle")
            .node("forest", "Dark Forest")
            .node("river", "River Delta")
            .node("snowy", "Snowy Tundra")
            .node("mountain", "Stony Peaks")
            .node("cave", "Underground Caverns")
            .node("deep_dark", "The Deep Dark")
            .build();

        CampaignRegion nether = CampaignRegion.builder("nether")
            .displayName("The Nether")
            .color("§c")
            .icon(NETHER_ICON)
            .mapColor(0xAAFF5555)
            .node("nether_wastes", "Nether Wastes")
            .node("soul_sand_valley", "Soul Sand Valley")
            .node("crimson_forest", "Crimson Forest")
            .node("warped_forest", "Warped Forest")
            .node("basalt_deltas", "Basalt Deltas")
            .build();

        CampaignRegion end = CampaignRegion.builder("end")
            .displayName("The End")
            .color("§d")
            .icon(END_ICON)
            .mapColor(0xAACC88FF)
            .node("outer_end_islands", "Outer End Islands")
            .node("end_city", "End City")
            .node("chorus_grove", "Chorus Grove")
            .node("dragons_nest", "Dragon's Nest")
            .build();

        // Branch choice 1: swap [desert, jungle, forest] <-> [snowy, mountain] around the
        // river pivot. Reproduces the legacy BiomePath.getPath(1) overworld order.
        CampaignBranch branch = new CampaignBranch(
            "overworld",
            List.of("desert", "jungle", "forest"),
            List.of("snowy", "mountain"));

        return Campaign.builder(CampaignManager.VANILLA_ID)
            .displayName("Vanilla")
            .region(overworld)
            .region(nether)
            .region(end)
            .branch(branch)
            .build();
    }

    /**
     * Register the built-in vanilla campaign with the {@link CampaignManager} as a CODE entry
     * (so it survives {@code /reload}). Call once from mod init, before addons register, so any
     * addon/datapack campaign wins over this built-in one.
     */
    public static void register() {
        CampaignManager.register(build(), RegistrationSource.CODE);
    }
}
