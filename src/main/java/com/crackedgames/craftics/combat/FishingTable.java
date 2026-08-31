package com.crackedgames.craftics.combat;

/**
 * What a cast pulls up, and how much the run's progress has to do with it.
 *
 * <p>Fishing used to hand out something every single time, from the same table on the first level
 * as on the last. It was the most reliable loot in the game and the least earned - cast, catch,
 * repeat, while a fight waited. Three things change that: most casts come up empty, some pull up
 * something that fights back, and the good tiers only really open up once a run has got somewhere.
 *
 * <p>Free of Minecraft so the distribution can be checked directly. The odds ARE the balance here,
 * and "roughly a third of casts catch nothing" is not something to leave to a reading of the code.
 */
public final class FishingTable {

    private FishingTable() {}

    /** Casts that come up empty. The single biggest change to how fishing feels. */
    public static final int NOTHING_CHANCE = 30;

    /** Casts that hook a drowned instead of an item. It surfaces angry. */
    public static final int DROWNED_CHANCE = 5;

    /** Treasure at ordinal 0, before progress is added. */
    private static final int BASE_TREASURE = 2;
    /** Good items at ordinal 0. */
    private static final int BASE_GOOD = 8;
    /** Useful items, flat: the middle of the table does not need to move. */
    private static final int USEFUL = 20;

    /** Ceilings, so a long run cannot turn the whole table into treasure. */
    private static final int MAX_TREASURE = 12;
    private static final int MAX_GOOD = 20;

    public enum Catch {
        /** The line comes back empty. */
        NOTHING,
        /** A drowned, scaled to how far the run has come. */
        DROWNED,
        COMMON_FISH,
        USEFUL_ITEM,
        GOOD_ITEM,
        TREASURE
    }

    /**
     * Resolve one cast.
     *
     * @param roll         0-99, uniform
     * @param biomeOrdinal how far into the campaign this fight is, 0 for the first biome
     */
    public static Catch resolve(int roll, int biomeOrdinal) {
        if (roll < NOTHING_CHANCE) return Catch.NOTHING;
        if (roll < NOTHING_CHANCE + DROWNED_CHANCE) return Catch.DROWNED;

        int progress = Math.max(0, biomeOrdinal);
        int treasure = Math.min(MAX_TREASURE, BASE_TREASURE + progress);
        int good = Math.min(MAX_GOOD, BASE_GOOD + progress);

        // Rarest first, so the bands that grow with progress eat into COMMON at the bottom
        // rather than pushing anything off the end of the table.
        int cursor = NOTHING_CHANCE + DROWNED_CHANCE;
        if (roll < (cursor += treasure)) return Catch.TREASURE;
        if (roll < (cursor += good)) return Catch.GOOD_ITEM;
        if (roll < cursor + USEFUL) return Catch.USEFUL_ITEM;
        return Catch.COMMON_FISH;
    }

    /** Health for a drowned hooked at this point in a run. */
    public static int drownedHealth(int biomeOrdinal) {
        return 18 + Math.max(0, biomeOrdinal) * 3;
    }

    /** Attack for a drowned hooked at this point in a run. */
    public static int drownedAttack(int biomeOrdinal) {
        return 3 + Math.max(0, biomeOrdinal);
    }
}
