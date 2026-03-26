package com.crackedgames.craftics.combat;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Weighted random loot pool.
 * roll(minTypes, maxTypes, minTotal, maxTotal) picks a limited number of
 * distinct item types, then distributes a total item count among them.
 */
public class LootPool {
    private static final Random RAND = new Random();

    private final List<Entry> entries = new ArrayList<>();
    private int totalWeight = 0;

    public LootPool add(Item item, int weight) {
        entries.add(new Entry(item, weight));
        totalWeight += weight;
        return this;
    }

    /**
     * Roll loot with constraints on variety and total quantity.
     * @param minTypes minimum distinct item types (e.g. 1)
     * @param maxTypes maximum distinct item types (e.g. 3)
     * @param minTotal minimum total items across all types (e.g. 2)
     * @param maxTotal maximum total items across all types (e.g. 6)
     */
    public List<ItemStack> roll(int minTypes, int maxTypes, int minTotal, int maxTotal) {
        if (entries.isEmpty()) return List.of();

        // Pick how many distinct types
        int numTypes = minTypes + RAND.nextInt(maxTypes - minTypes + 1);
        numTypes = Math.min(numTypes, entries.size());

        // Pick which types (weighted random, no duplicates)
        List<Entry> chosen = new ArrayList<>();
        List<Entry> pool = new ArrayList<>(entries);
        int poolWeight = totalWeight;

        for (int i = 0; i < numTypes && !pool.isEmpty(); i++) {
            int r = RAND.nextInt(poolWeight);
            int cumulative = 0;
            for (int j = 0; j < pool.size(); j++) {
                cumulative += pool.get(j).weight;
                if (r < cumulative) {
                    Entry picked = pool.remove(j);
                    poolWeight -= picked.weight;
                    chosen.add(picked);
                    break;
                }
            }
        }

        if (chosen.isEmpty()) return List.of();

        // Pick total item count
        int totalItems = minTotal + RAND.nextInt(maxTotal - minTotal + 1);

        // Distribute items among chosen types
        List<ItemStack> result = new ArrayList<>();
        int remaining = totalItems;

        for (int i = 0; i < chosen.size(); i++) {
            int share;
            if (i == chosen.size() - 1) {
                share = remaining; // last type gets whatever's left
            } else {
                share = 1 + RAND.nextInt(Math.max(1, remaining - (chosen.size() - i - 1)));
                share = Math.min(share, remaining);
            }
            if (share > 0) {
                result.add(new ItemStack(chosen.get(i).item, share));
                remaining -= share;
            }
        }

        return result;
    }

    private record Entry(Item item, int weight) {}
}
