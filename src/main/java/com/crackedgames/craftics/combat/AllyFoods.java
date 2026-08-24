package com.crackedgames.craftics.combat;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.Map;
import java.util.Set;

/**
 * What each vanilla animal ally eats to heal, mirroring what feeds it in Minecraft.
 *
 * <h2>Why this is a table and not an {@code AllyEntry} field</h2>
 *
 * <p>{@link com.crackedgames.craftics.api.registry.AllyEntry#healItem()} binds ONE item to an
 * ally, which is right for the constructed pets it was written for - an iron golem takes an iron
 * ingot, a snow golem takes a snowball, and there is nothing else either of them would want.
 * It is wrong for animals: a wolf is fed by any meat at all, and enumerating eleven of them into
 * a single-item field is not a thing that field can do.
 *
 * <p>Which foods an animal accepts is also vanilla knowledge rather than per-registration
 * configuration - it is the same answer for every wolf anyone ever fields - so it belongs in one
 * shared table rather than repeated into each entry. Keeping it out of {@code AllyEntry} also
 * leaves that record's shape alone, which matters: it is public API, and addons construct it.
 *
 * <p>An addon ally is unaffected. It keeps using {@code healItem}, and simply has no entry here.
 */
public final class AllyFoods {

    private AllyFoods() {}

    /** HP an animal recovers from one feed. Modest on purpose: a hay bale heals half its max. */
    public static final int FEED_HEAL = 3;

    /**
     * Every meat a wolf will take, cooked or not - including rotten flesh, which a wolf eats
     * quite happily and a player cannot.
     */
    private static final Set<Item> MEATS = Set.of(
        Items.BEEF, Items.COOKED_BEEF,
        Items.PORKCHOP, Items.COOKED_PORKCHOP,
        Items.CHICKEN, Items.COOKED_CHICKEN,
        Items.MUTTON, Items.COOKED_MUTTON,
        Items.RABBIT, Items.COOKED_RABBIT,
        Items.ROTTEN_FLESH);

    private static final Set<Item> RAW_FISH = Set.of(
        Items.COD, Items.SALMON, Items.TROPICAL_FISH);

    private static final Set<Item> SEEDS = Set.of(
        Items.WHEAT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.BEETROOT_SEEDS);

    private static final Set<Item> HORSE_FEED = Set.of(
        Items.WHEAT, Items.APPLE, Items.SUGAR, Items.HAY_BLOCK,
        Items.GOLDEN_APPLE, Items.GOLDEN_CARROT);

    private static final Map<String, Set<Item>> BY_TYPE = Map.ofEntries(
        Map.entry("minecraft:wolf", MEATS),
        Map.entry("minecraft:cat", RAW_FISH),
        Map.entry("minecraft:ocelot", RAW_FISH),
        Map.entry("minecraft:parrot", SEEDS),
        Map.entry("minecraft:fox", Set.of(Items.SWEET_BERRIES, Items.GLOW_BERRIES)),
        Map.entry("minecraft:horse", HORSE_FEED),
        Map.entry("minecraft:donkey", HORSE_FEED),
        Map.entry("minecraft:mule", HORSE_FEED),
        Map.entry("minecraft:llama", Set.of(Items.WHEAT, Items.HAY_BLOCK)),
        Map.entry("minecraft:camel", Set.of(Items.CACTUS)),
        Map.entry("minecraft:goat", Set.of(Items.WHEAT)),
        Map.entry("minecraft:cow", Set.of(Items.WHEAT)),
        Map.entry("minecraft:mooshroom", Set.of(Items.WHEAT)),
        Map.entry("minecraft:sheep", Set.of(Items.WHEAT)),
        Map.entry("minecraft:pig", Set.of(Items.CARROT, Items.POTATO, Items.BEETROOT)),
        Map.entry("minecraft:chicken", SEEDS),
        Map.entry("minecraft:rabbit", Set.of(Items.CARROT, Items.GOLDEN_CARROT, Items.DANDELION)),
        Map.entry("minecraft:bee", Set.of(Items.DANDELION, Items.POPPY, Items.BLUE_ORCHID)),
        Map.entry("minecraft:turtle", Set.of(Items.SEAGRASS)),
        Map.entry("minecraft:frog", Set.of(Items.SLIME_BALL)),
        Map.entry("minecraft:sniffer", Set.of(Items.TORCHFLOWER_SEEDS)),
        Map.entry("minecraft:panda", Set.of(Items.BAMBOO)),
        Map.entry("minecraft:strider", Set.of(Items.WARPED_FUNGUS)),
        Map.entry("minecraft:hoglin", Set.of(Items.CRIMSON_FUNGUS))
    );

    /** Whether feeding {@code item} to this animal should heal it. */
    public static boolean heals(String entityTypeId, Item item) {
        if (entityTypeId == null || item == null) return false;
        Set<Item> foods = BY_TYPE.get(entityTypeId);
        return foods != null && foods.contains(item);
    }

    /** Whether this animal has any feed at all - used to decide if a click is worth routing. */
    public static boolean hasFeed(String entityTypeId) {
        return entityTypeId != null && BY_TYPE.containsKey(entityTypeId);
    }
}
