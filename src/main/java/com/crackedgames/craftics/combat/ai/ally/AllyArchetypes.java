package com.crackedgames.craftics.combat.ai.ally;

import java.util.Map;
import java.util.Set;

/**
 * Maps an entity type to its combat-ally behavior archetype. Every mob added to
 * a player's battle party fights with one of six behaviors — five distinct
 * combat archetypes plus a single shared {@link FarmAnimalAllyAI} for farm
 * animals and the unsaddled mounts.
 *
 * <p>Used by {@code CombatManager} when a party mob is spawned into the arena;
 * the resolved {@link AllyAI} is stored on its {@code CombatEntity}.
 *
 * @since 0.3.0
 */
public final class AllyArchetypes {

    private AllyArchetypes() {}

    // Shares the one melee instance AllyEntry already exposes as its default.
    private static final AllyAI MELEE   = com.crackedgames.craftics.api.registry.AllyEntry.DEFAULT_AI;
    private static final AllyAI RANGED  = new RangedAllyAI();
    private static final AllyAI FLYER   = new FlyerAllyAI();
    private static final AllyAI TANK    = new TankAllyAI();
    private static final AllyAI SUPPORT = new SupportAllyAI();
    private static final AllyAI FARM    = new FarmAnimalAllyAI();

    /**
     * Farm animals and the unsaddled livestock mounts. These share one AI by
     * design — the feature spec calls for unique AI "except for farm animals".
     */
    private static final Set<String> FARM_ANIMALS = Set.of(
        "minecraft:cow", "minecraft:mooshroom", "minecraft:sheep", "minecraft:pig",
        "minecraft:chicken", "minecraft:rabbit", "minecraft:horse", "minecraft:donkey",
        "minecraft:mule", "minecraft:skeleton_horse", "minecraft:zombie_horse",
        "minecraft:camel", "minecraft:strider"
    );

    /** Per-type archetype assignment. Farm animals are handled separately. */
    private static final Map<String, AllyAI> BY_TYPE = Map.ofEntries(
        // Melee predators — chase, score targets, retreat when badly hurt.
        Map.entry("minecraft:wolf", MELEE),
        Map.entry("minecraft:fox", MELEE),
        Map.entry("minecraft:ocelot", MELEE),
        Map.entry("minecraft:cat", MELEE),
        Map.entry("minecraft:polar_bear", MELEE),
        Map.entry("minecraft:panda", MELEE),
        Map.entry("minecraft:dolphin", MELEE),
        Map.entry("minecraft:hoglin", MELEE),
        Map.entry("minecraft:zombified_piglin", MELEE),
        Map.entry("minecraft:piglin", MELEE),
        Map.entry("minecraft:enderman", MELEE),
        Map.entry("minecraft:spider", MELEE),
        Map.entry("minecraft:cave_spider", MELEE),
        // Ranged kiters — fire from distance, back away from melee.
        Map.entry("minecraft:llama", RANGED),
        Map.entry("minecraft:trader_llama", RANGED),
        Map.entry("minecraft:snow_golem", RANGED),
        // Flyers — fast, fearless, dive on the weakest enemy.
        Map.entry("minecraft:parrot", FLYER),
        Map.entry("minecraft:bee", FLYER),
        Map.entry("minecraft:bat", FLYER),
        Map.entry("minecraft:allay", FLYER),
        // Tanks — body-block the enemy nearest the player, never flee.
        Map.entry("minecraft:iron_golem", TANK),
        Map.entry("minecraft:turtle", TANK),
        Map.entry("minecraft:goat", TANK),
        Map.entry("minecraft:armadillo", TANK),
        // Support — hold station by the player, punish enemies that close in.
        Map.entry("minecraft:axolotl", SUPPORT),
        Map.entry("minecraft:frog", SUPPORT),
        Map.entry("minecraft:villager", SUPPORT),
        Map.entry("minecraft:wandering_trader", SUPPORT),
        Map.entry("minecraft:sniffer", SUPPORT)
    );

    /**
     * Resolve the combat AI for a party mob of the given entity type. Farm
     * animals share {@link FarmAnimalAllyAI}; unknown/exotic mobs default to the
     * melee fighter so any mob still pulls its weight in a fight.
     */
    public static AllyAI aiFor(String entityTypeId) {
        if (entityTypeId == null) return MELEE;
        if (FARM_ANIMALS.contains(entityTypeId)) return FARM;
        AllyAI ai = BY_TYPE.get(entityTypeId);
        return ai != null ? ai : MELEE;
    }

    /** Whether this entity type uses the shared farm-animal AI. */
    public static boolean isFarmAnimal(String entityTypeId) {
        return entityTypeId != null && FARM_ANIMALS.contains(entityTypeId);
    }
}
