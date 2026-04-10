package com.crackedgames.craftics.combat;

import java.util.HashMap;
import java.util.Map;

/**
 * Movement-pattern tag attached to each enemy type so the client can render
 * an accurate hover preview without running the actual server-side AI.
 *
 * Each style maps to a deterministic visualization in
 * {@code CombatState.getHoveredEnemyMoveTiles()}. When adding a new mob, register
 * its style here — defaults to {@link #WALK} if missing, which produces a normal
 * BFS using the mob's speed stat.
 */
public enum MoveStyle {
    /** Standard pathfinding BFS (zombies, skeletons, most ground mobs). */
    WALK,
    /** Cardinal-only walk (ghasts, drowned in some configs). Same as WALK in BFS terms. */
    CARDINAL_WALK,
    /** Rook dash: cardinal lines until obstacle + small adjust ring (vindicator, piglin brute). */
    ROOK_DASH,
    /** Bull-rush charge up to N cardinal tiles, plus walk fallback (hoglin, ravager). */
    CHARGE,
    /** Free hop ignoring obstacles within a manhattan radius (magma cube, slime, phantom). */
    BOUNCE_FREE,
    /** Short-range teleport into any walkable tile within blink range (endermite). */
    BLINK,
    /** Long-range teleport — can appear anywhere unoccupied/walkable in the arena (enderman). */
    TELEPORT,
    /** Pounce: lands adjacent to the player (spider, cave spider). */
    POUNCE,
    /** Stationary — does not move (shulker, end crystal, egg sac, ghast turret-mode). */
    STATIONARY;

    private static final Map<String, MoveStyle> BY_TYPE = new HashMap<>();

    static {
        // ─── Passive / farm animals ───
        BY_TYPE.put("minecraft:cow", WALK);
        BY_TYPE.put("minecraft:pig", WALK);
        BY_TYPE.put("minecraft:sheep", WALK);
        BY_TYPE.put("minecraft:chicken", WALK);
        BY_TYPE.put("minecraft:parrot", WALK);
        BY_TYPE.put("minecraft:panda", WALK);
        BY_TYPE.put("minecraft:horse", WALK);
        BY_TYPE.put("minecraft:donkey", WALK);
        BY_TYPE.put("minecraft:mule", WALK);

        // ─── Territorial / aggro-on-hit ───
        BY_TYPE.put("minecraft:llama", WALK);
        BY_TYPE.put("minecraft:polar_bear", WALK);
        BY_TYPE.put("minecraft:bee", WALK);
        BY_TYPE.put("minecraft:goat", WALK);

        // ─── Skittish ───
        BY_TYPE.put("minecraft:rabbit", WALK);
        BY_TYPE.put("minecraft:bat", WALK);

        // ─── Aquatic ───
        BY_TYPE.put("minecraft:cod", WALK);
        BY_TYPE.put("minecraft:salmon", WALK);
        BY_TYPE.put("minecraft:axolotl", WALK);

        // ─── Predators ───
        BY_TYPE.put("minecraft:wolf", WALK);
        BY_TYPE.put("minecraft:fox", WALK);
        BY_TYPE.put("minecraft:cat", WALK);
        BY_TYPE.put("minecraft:ocelot", WALK);

        // ─── Basic melee hostiles ───
        BY_TYPE.put("minecraft:zombie", WALK);
        BY_TYPE.put("minecraft:zombie_villager", WALK);
        BY_TYPE.put("minecraft:husk", WALK);
        BY_TYPE.put("minecraft:drowned", WALK);

        // ─── Ranged hostiles (still walk between shots) ───
        BY_TYPE.put("minecraft:skeleton", WALK);
        BY_TYPE.put("minecraft:stray", WALK);
        BY_TYPE.put("minecraft:pillager", WALK);
        BY_TYPE.put("minecraft:bogged", WALK);
        BY_TYPE.put("minecraft:witch", WALK);
        BY_TYPE.put("minecraft:evoker", WALK);
        BY_TYPE.put("minecraft:wither_skeleton", WALK);
        BY_TYPE.put("minecraft:guardian", WALK);

        // ─── Custom-movement hostiles ───
        BY_TYPE.put("minecraft:vindicator", ROOK_DASH);
        BY_TYPE.put("minecraft:piglin_brute", ROOK_DASH);
        BY_TYPE.put("minecraft:hoglin", CHARGE);
        BY_TYPE.put("minecraft:ravager", CHARGE);
        BY_TYPE.put("minecraft:spider", POUNCE);
        BY_TYPE.put("minecraft:cave_spider", POUNCE);
        BY_TYPE.put("minecraft:phantom", BOUNCE_FREE);
        BY_TYPE.put("minecraft:magma_cube", BOUNCE_FREE);
        BY_TYPE.put("minecraft:slime", BOUNCE_FREE);
        BY_TYPE.put("minecraft:enderman", TELEPORT);
        BY_TYPE.put("minecraft:endermite", BLINK);
        BY_TYPE.put("minecraft:breeze", BLINK);
        BY_TYPE.put("minecraft:silverfish", WALK);
        BY_TYPE.put("minecraft:creeper", WALK);
        BY_TYPE.put("minecraft:blaze", WALK);
        BY_TYPE.put("minecraft:ghast", CARDINAL_WALK);
        BY_TYPE.put("minecraft:zombified_piglin", WALK);
        BY_TYPE.put("minecraft:piglin", WALK); // weapon-based AI uses walk pathing for both modes

        // ─── Stationary ───
        BY_TYPE.put("minecraft:shulker", STATIONARY);
        BY_TYPE.put("minecraft:end_crystal", STATIONARY);
        BY_TYPE.put("craftics:egg_sac", STATIONARY);

        // ─── Mounted ───
        BY_TYPE.put("minecraft:camel", WALK);

        // ─── Bosses (vanilla AI fallback when not boss-flagged) ───
        BY_TYPE.put("minecraft:warden", WALK);
        BY_TYPE.put("minecraft:ender_dragon", BOUNCE_FREE);
    }

    /** Lookup the style for a vanilla entity type id. Defaults to {@link #WALK}. */
    public static MoveStyle forEntityType(String entityTypeId) {
        if (entityTypeId == null) return WALK;
        return BY_TYPE.getOrDefault(entityTypeId, WALK);
    }

    /** Short tag string written into sync metadata (e.g. {@code mv=ROOK_DASH}). */
    public String tag() {
        return name();
    }

    /** Parse a style from its tag, returning {@link #WALK} on unknown values. */
    public static MoveStyle fromTag(String tag) {
        if (tag == null) return WALK;
        try {
            return MoveStyle.valueOf(tag);
        } catch (IllegalArgumentException e) {
            return WALK;
        }
    }
}
