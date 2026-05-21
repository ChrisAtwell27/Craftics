package com.crackedgames.craftics.combat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stacked-enemy definitions. A stack is a single {@link CombatEntity} that holds
 * an ordered queue of layers from base (killed first) to top (killed last). On
 * each "kill" the base layer is consumed and the entity transforms into the
 * next layer instead of dying. When only one layer remains, the entity behaves
 * exactly like a normal mob and dies normally (running any per-mob death side
 * effects such as the slime split).
 *
 * <p>Stack ids are referenced by spawn entries through a synthetic
 * {@code craftics:stack/<id>} entity type id. The level generator can replace
 * a vanilla spawn whose type matches a stack's {@link Layer} root with a stack
 * spawn at a small probability.
 */
public final class StackVariants {

    private StackVariants() {}

    /**
     * One layer of a stack. Display name is the *current* mob's name while
     * this layer is the front of the queue (e.g. "Zombie Stack" for the
     * base zombie while the baby is riding it, then "Baby Zombie" once the
     * baby drops).
     */
    public record Layer(
        String entityTypeId,
        String displayName,
        int hp,
        int attack,
        int defense,
        int range,
        int speed
    ) {}

    /**
     * One stack definition. {@code layers} is ordered base-first: the first
     * entry is killed first, the last entry is the surviving final mob. The
     * surviving final mob keeps all of its species' normal death behavior
     * (slime split, etc).
     *
     * <p>{@code passengerForLayer(i)} is the entity type to spawn as a visual
     * passenger on top of layer i for the cosmetic "rider sitting on mount"
     * look. Returns null when the layer has no rider above it (the top layer
     * never has a passenger).
     */
    public record StackDef(
        String id,
        List<Layer> layers,
        List<String> allowedBiomes,
        double spawnChance
    ) {
        /** Visual passenger riding the current layer, or null if this is the top of the stack. */
        public String passengerTypeFor(int currentLayerIndex) {
            if (currentLayerIndex + 1 >= layers.size()) return null;
            return layers.get(currentLayerIndex + 1).entityTypeId();
        }

        /** True if the def is allowed in the given biome (null/empty allowedBiomes = anywhere). */
        public boolean allowedIn(String biomeId) {
            if (allowedBiomes == null || allowedBiomes.isEmpty()) return true;
            return allowedBiomes.contains(biomeId);
        }
    }

    /** Synthetic entity type id prefix used in spawn entries. */
    public static final String STACK_PREFIX = "craftics:stack/";

    private static final Map<String, StackDef> REGISTRY = new LinkedHashMap<>();

    static {
        // Zombie Stack: adult zombie with a baby zombie riding. Lives in any
        // biome where adult zombies already spawn (plains, cave, forest, etc).
        register(new StackDef("zombie_stack",
            List.of(
                new Layer("minecraft:zombie", "Zombie Stack",   12, 3, 1, 1, 0),
                new Layer("minecraft:zombie", "Baby Zombie",     5, 2, 0, 1, 3)
            ),
            List.of("plains", "forest", "cave", "mountain", "snowy", "desert"),
            0.08
        ));

        // Skeleton Horseman: skeleton riding a skeleton horse. Stormy/undead
        // biomes where skeletons feel at home.
        register(new StackDef("skeleton_horseman",
            List.of(
                new Layer("minecraft:skeleton_horse", "Skeleton Horseman", 14, 3, 1, 1, 3),
                new Layer("minecraft:skeleton",       "Skeleton",            8, 3, 0, 3, 2)
            ),
            List.of("plains", "snowy", "mountain", "cave", "soul_sand_valley"),
            0.5
        ));

        // Zombie Horseman: zombie riding a zombie horse. Same overworld pools
        // as the Zombie Stack but in slightly different biomes to keep variety.
        register(new StackDef("zombie_horseman",
            List.of(
                new Layer("minecraft:zombie_horse", "Zombie Horseman", 14, 3, 1, 1, 3),
                new Layer("minecraft:zombie",       "Zombie",           8, 3, 1, 1, 1)
            ),
            List.of("plains", "desert", "forest", "mountain", "cave"),
            0.5
        ));

        // Piglin Cavalry: piglin riding a hoglin. Nether-only.
        register(new StackDef("piglin_cavalry",
            List.of(
                new Layer("minecraft:hoglin", "Piglin Cavalry", 18, 4, 1, 1, 3),
                new Layer("minecraft:piglin", "Piglin",          9, 3, 0, 1, 2)
            ),
            List.of("nether_wastes", "crimson_forest"),
            0.05
        ));

        // Slime Tower: 3 medium slimes stacked. Attack scales with how many
        // slimes remain on the tower — 8 with all 3, 6 with 2, 4 with the lone
        // medium slime. The final slime runs the usual split-on-death logic;
        // the mini slimes it splits into deal only 1 damage each (handled in
        // CombatManager.trySplitOnDeath). All layers are speed-1 beeline mobs.
        register(new StackDef("slime_tower",
            List.of(
                new Layer("minecraft:slime", "Slime Tower (3)", 10, 8, 0, 1, 1),
                new Layer("minecraft:slime", "Slime Tower (2)",  8, 6, 0, 1, 1),
                new Layer("minecraft:slime", "Slime",            6, 4, 0, 1, 1)
            ),
            List.of("river", "jungle", "forest"),
            0.05
        ));

        // Blaze Tower: 3 blazes, immobile, triple fireball. Native to nether
        // and basalt-tier biomes only.
        register(new StackDef("blaze_tower",
            List.of(
                new Layer("minecraft:blaze", "Blaze Tower (3)", 14, 3, 1, 3, 0),
                new Layer("minecraft:blaze", "Blaze Tower (2)", 10, 3, 1, 3, 0),
                new Layer("minecraft:blaze", "Blaze",            6, 3, 0, 3, 2)
            ),
            List.of("nether_wastes", "basalt_deltas", "soul_sand_valley"),
            0.05
        ));
    }

    private static void register(StackDef def) {
        REGISTRY.put(def.id(), def);
    }

    /** Get a stack definition by id. Returns null when the id is unknown. */
    public static StackDef get(String id) {
        return REGISTRY.get(id);
    }

    /** Get a stack definition by its full synthetic entity type id, e.g. {@code craftics:stack/zombie_stack}. */
    public static StackDef getByTypeId(String entityTypeId) {
        if (entityTypeId == null || !entityTypeId.startsWith(STACK_PREFIX)) return null;
        return get(entityTypeId.substring(STACK_PREFIX.length()));
    }

    /** Synthetic entity type id for a stack, used in EnemySpawn entries. */
    public static String typeIdFor(String stackId) {
        return STACK_PREFIX + stackId;
    }

    /** True if the given entityTypeId is a stack synthetic id. */
    public static boolean isStackType(String entityTypeId) {
        return entityTypeId != null && entityTypeId.startsWith(STACK_PREFIX);
    }

    public static Map<String, StackDef> all() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    /**
     * Pick a stack whose BASE layer entity type matches {@code baseEntityType}
     * and which is allowed in {@code biomeId}. Used by LevelGenerator to roll
     * a stack replacement for an eligible vanilla spawn.
     */
    public static StackDef findReplacementFor(String baseEntityType, String biomeId) {
        for (StackDef def : REGISTRY.values()) {
            if (def.layers().isEmpty()) continue;
            if (!def.layers().get(0).entityTypeId().equals(baseEntityType)) continue;
            if (!def.allowedIn(biomeId)) continue;
            return def;
        }
        return null;
    }
}
