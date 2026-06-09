package com.crackedgames.craftics.combat.barter;

import com.crackedgames.craftics.api.registry.BarterRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import java.util.List;
import java.util.Random;

/**
 * Pure mechanics for piglin bartering: the secret success threshold and the offer-scaled
 * success/overpay odds. No Minecraft dependencies in these methods so they stay unit-testable.
 * Reward selection (which touches ItemStack/registries) lives in later methods added in the
 * reward-roll task.
 */
public final class PiglinBarterSystem {

    /** Inclusive minimum of the secret threshold roll. */
    public static final int MIN_THRESHOLD = 16;
    /** Inclusive maximum of the secret threshold roll (also the max offer). */
    public static final int MAX_THRESHOLD = 64;

    /** Per-surplus-gold bonus probability, before the cap. */
    private static final double OVERPAY_PER_GOLD = 0.03;

    private PiglinBarterSystem() {}

    /**
     * Secret offer (in gold ingots) that guarantees a successful barter, rolled uniformly in
     * {@code [MIN_THRESHOLD, MAX_THRESHOLD]} per player per event.
     */
    public static int rollThreshold(Random random) {
        return MIN_THRESHOLD + random.nextInt(MAX_THRESHOLD - MIN_THRESHOLD + 1);
    }

    /**
     * Linear success chance for an {@code offer} against the secret {@code threshold}:
     * {@code clamp(offer / threshold, 0, 1)}. A non-positive threshold is treated as a
     * guaranteed success (defensive; the real roll is always >= 16).
     */
    public static double successChance(int offer, int threshold) {
        if (threshold <= 0) return 1.0;
        double c = (double) offer / (double) threshold;
        if (c < 0.0) return 0.0;
        if (c > 1.0) return 1.0;
        return c;
    }

    /**
     * Chance at a second bonus item when the offer exceeded the secret threshold. Scales linearly
     * with the surplus gold and is capped at 1.0. Zero when there is no surplus.
     */
    public static double overpayBonusChance(int surplus) {
        if (surplus <= 0) return 0.0;
        double c = surplus * OVERPAY_PER_GOLD;
        return c > 1.0 ? 1.0 : c;
    }

    /**
     * The "bland" junk a failed barter returns, mirroring vanilla piglin bartering rejects.
     * A failed offer still consumes the gold. Item-backed, so this list and the methods that
     * read it are exercised in-game; the count is exposed item-free for unit tests.
     *
     * <p>Held in a lazy nested class so that merely class-loading {@code PiglinBarterSystem}
     * (as the no-bootstrap JUnit harness does) does NOT trigger {@code Items.<clinit>}, which
     * would throw "Not bootstrapped". The {@code Items.*} references resolve only when this list
     * is first touched at runtime in-game.
     */
    private static final class Junk {
        static final List<Item> ITEMS = List.of(
            Items.GRAVEL, Items.SOUL_SAND, Items.CRYING_OBSIDIAN, Items.NETHERRACK,
            Items.NETHER_BRICK, Items.SOUL_SOIL, Items.BONE, Items.LEATHER, Items.STRING);
    }

    /**
     * Number of distinct junk options. A plain literal (not {@code Junk.ITEMS.size()}) so it stays
     * item-free and readable by unit tests that cannot touch {@code Items}. Kept in sync with
     * {@link Junk#ITEMS} by {@link #junkItems()} on first in-game access.
     */
    public static final int JUNK_ITEM_COUNT = 9;

    /**
     * The junk reward pool, resolved lazily on first call (in-game only). Verified in-game.
     */
    public static List<Item> junkItems() {
        return Junk.ITEMS;
    }

    /**
     * Pure weighted selection: given per-bucket {@code weights} and a {@code roll} in
     * {@code [0, sum(weights))}, return the chosen index. A roll at or beyond the total clamps to
     * the last index (defensive). Item-free so it is unit-testable in the no-bootstrap harness.
     */
    public static int pickWeightedIndex(int[] weights, int roll) {
        int acc = 0;
        for (int i = 0; i < weights.length; i++) {
            acc += weights[i];
            if (roll < acc) return i;
        }
        return weights.length - 1;
    }

    /** Pure inclusive-range count roll, never below 1. Item-free, unit-testable. */
    public static int rollCount(int min, int max, Random random) {
        int lo = Math.max(1, min);
        int hi = Math.max(lo, max);
        int span = hi - lo;
        return lo + (span <= 0 ? 0 : random.nextInt(span + 1));
    }

    /**
     * Roll one good reward from {@code categoryId}'s eligible entries (tier-filtered, weighted).
     * Returns {@link ItemStack#EMPTY} when the category has no eligible entry. Verified in-game;
     * selection math is covered by {@link #pickWeightedIndex}/{@link #rollCount} unit tests.
     */
    public static ItemStack rollGoodReward(String categoryId, int tier, Random random) {
        List<BarterEntry> pool = BarterRegistry.forCategory(categoryId, tier);
        if (pool.isEmpty()) return ItemStack.EMPTY;

        int[] weights = new int[pool.size()];
        int total = 0;
        for (int i = 0; i < pool.size(); i++) { weights[i] = pool.get(i).weight(); total += weights[i]; }
        BarterEntry chosen = pool.get(pickWeightedIndex(weights, random.nextInt(total)));

        ItemStack stack = chosen.prototype().copy();
        stack.setCount(rollCount(chosen.minCount(), chosen.maxCount(), random));
        return stack;
    }

    /** Roll a small (1-4) stack of a random junk item for a failed barter. Verified in-game. */
    public static ItemStack rollJunk(Random random) {
        List<Item> items = junkItems();
        Item item = items.get(random.nextInt(items.size()));
        return new ItemStack(item, 1 + random.nextInt(4));
    }
}
