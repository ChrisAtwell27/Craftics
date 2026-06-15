package com.crackedgames.craftics.combat.barter;

import net.minecraft.item.ItemStack;

/**
 * One reward in a barter pool, keyed to a {@link BarterCategory} id. The reward is stored as a
 * prototype {@link ItemStack}; {@code minCount}/{@code maxCount} let a single entry roll a
 * quantity range (e.g. Hoarder's 6-12 diamonds). {@code weight} biases random selection within a
 * category. {@code minBiomeTier} gates the entry to deeper Nether biomes.
 */
public record BarterEntry(String categoryId, ItemStack prototype,
                          int minCount, int maxCount, int weight, int minBiomeTier) {
    public BarterEntry {
        if (categoryId == null || categoryId.isBlank())
            throw new IllegalArgumentException("BarterEntry categoryId required");
        if (prototype == null) throw new IllegalArgumentException("BarterEntry prototype required");
        if (minCount < 1) minCount = 1;
        if (maxCount < minCount) maxCount = minCount;
        if (weight < 1) weight = 1;
        if (minBiomeTier < 0) minBiomeTier = 0;
    }

    /** Convenience for a fixed-count entry. */
    public BarterEntry(String categoryId, ItemStack prototype, int weight, int minBiomeTier) {
        this(categoryId, prototype, prototype.getCount(), prototype.getCount(), weight, minBiomeTier);
    }
}
