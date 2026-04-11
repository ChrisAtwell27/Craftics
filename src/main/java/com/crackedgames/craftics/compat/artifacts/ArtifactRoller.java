package com.crackedgames.craftics.compat.artifacts;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Picks a random artifact item from the Artifacts mod registry.
 * <p>
 * The pool is the same set of items wired in {@link ArtifactsScanner} —
 * see {@code docs/artifact-abilities.md}. Computed once on first use and
 * cached, since the artifact registry doesn't change after mod load.
 */
public final class ArtifactRoller {

    /** Canonical list of artifact id paths the mod is aware of. */
    private static final String[] ARTIFACT_PATHS = {
        // Head
        "night_vision_goggles", "superstitious_hat", "villager_hat", "cowboy_hat",
        "anglers_hat", "snorkel", "plastic_drinking_hat", "novelty_drinking_hat",
        // Necklace
        "cross_necklace", "flame_pendant", "thorn_pendant", "panic_necklace",
        "shock_pendant", "charm_of_sinking", "charm_of_shrinking",
        "scarf_of_invisibility", "lucky_scarf",
        // Ring
        "onion_ring", "golden_hook", "pickaxe_heater", "withered_bracelet",
        // Hand
        "digging_claws", "feral_claws", "power_glove", "fire_gauntlet",
        "pocket_piston", "vampiric_glove",
        // Belt
        "crystal_heart", "helium_flamingo", "chorus_totem", "obsidian_skull",
        "cloud_in_a_bottle", "warp_drive", "antidote_vessel", "universal_attractor",
        "everlasting_beef", "eternal_steak",
        // Misc
        "umbrella",
        // Feet
        "bunny_hoppers", "kitty_slippers", "running_shoes", "aqua_dashers",
        "rooted_boots", "snowshoes", "steadfast_spikes", "flippers",
        "strider_shoes", "whoopee_cushion",
    };

    private static List<Item> cachedPool = null;
    private static final Random RNG = new Random();

    private ArtifactRoller() {}

    /** Build (or return cached) list of all available artifact items. */
    private static List<Item> getPool() {
        if (cachedPool != null) return cachedPool;
        List<Item> pool = new ArrayList<>();
        for (String path : ARTIFACT_PATHS) {
            Identifier id = Identifier.of(ArtifactsCompat.MOD_ID, path);
            if (Registries.ITEM.containsId(id)) {
                Item item = Registries.ITEM.get(id);
                if (item != null) pool.add(item);
            }
        }
        cachedPool = pool;
        return pool;
    }

    /**
     * Roll a single random artifact as an ItemStack of size 1.
     * Returns an empty ItemStack if Artifacts isn't loaded or none of the known
     * items are present in the registry.
     */
    public static ItemStack rollOne() {
        List<Item> pool = getPool();
        if (pool.isEmpty()) return ItemStack.EMPTY;
        return new ItemStack(pool.get(RNG.nextInt(pool.size())));
    }
}
