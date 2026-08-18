package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.SpawnCustomizer;
import com.crackedgames.craftics.combat.CombatEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-mob spawn hooks, looked up when Craftics puts an enemy or ally into an arena.
 *
 * <p>See {@link SpawnCustomizer} for what these are for and why the lookup prefers the
 * {@code aiKey}.
 *
 * @since 0.3.9
 */
public final class SpawnCustomizerRegistry {

    private SpawnCustomizerRegistry() {}

    private static final Map<String, SpawnCustomizer> CUSTOMIZERS = new HashMap<>();

    /**
     * Register a hook against an {@code aiKey} or an entity type id.
     *
     * <p>Later registrations replace earlier ones for the same key, which is what makes
     * an addon able to override a built-in: addon entrypoints run after Craftics has
     * finished registering its own content.
     */
    public static void register(String key, SpawnCustomizer customizer) {
        if (key == null || key.isEmpty() || customizer == null) return;
        CUSTOMIZERS.put(key, customizer);
    }

    /** True when anything is registered. Lets the spawn path skip the lookup entirely. */
    public static boolean isEmpty() {
        return CUSTOMIZERS.isEmpty();
    }

    /**
     * Run the hook for this entity, if there is one.
     *
     * <p>Tries the {@code aiKey} before the entity type id, so a mod that ships a single
     * entity type for many creatures can give each one its own initialisation while they
     * all share a type. Nothing happens when neither key is registered, which is the
     * normal case for every vanilla mob.
     *
     * <p>A throwing customizer is caught and logged. The mob is already spawned, placed
     * and registered by this point, so letting the exception out would abort the rest of
     * the arena build and leave a half-populated fight - strictly worse than one enemy
     * that did not get its extra initialisation.
     */
    public static void apply(ServerWorld world, MobEntity mob, CombatEntity entity) {
        if (CUSTOMIZERS.isEmpty() || world == null || mob == null || entity == null) return;
        SpawnCustomizer c = null;
        String aiKey = entity.getAiKey();
        if (aiKey != null) c = CUSTOMIZERS.get(aiKey);
        if (c == null && entity.getEntityTypeId() != null) {
            c = CUSTOMIZERS.get(entity.getEntityTypeId());
        }
        if (c == null) return;
        try {
            c.onSpawn(world, mob, entity);
        } catch (Throwable t) {
            CrafticsMod.LOGGER.error("Spawn customizer for '{}' failed; the mob spawns uncustomised",
                aiKey != null ? aiKey : entity.getEntityTypeId(), t);
        }
    }

    /** Clear every registration. Test hook. */
    public static void clear() {
        CUSTOMIZERS.clear();
    }
}
