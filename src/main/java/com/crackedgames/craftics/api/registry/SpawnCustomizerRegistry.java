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
    /** AI keys already reported as having no customizer, so each is said once, not per spawn. */
    private static final java.util.Set<String> WARNED_KEYS =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Say so when a combatant was deliberately keyed and nothing claimed it.
     *
     * <p>Only for a combatant whose AI key differs from its entity type. That difference means
     * somebody set the key on purpose - usually a mod whose one entity type stands in for many
     * creatures - so a missing customizer there is a wiring mistake worth naming. A mob whose
     * key is just its entity type is an ordinary mob nobody intended to customise, and warning
     * about those would print a line for every zombie in every fight.
     *
     * <p>Exists because the failure is otherwise invisible: an undressed combatant looks like a
     * generic one, and the only way to tell "my customizer ran and did nothing" from "my
     * customizer was never called" was to add logging on the addon side and guess.
     */
    private static void warnUnmatchedKey(CombatEntity entity) {
        String aiKey = entity.getAiKey();
        if (aiKey == null || aiKey.equals(entity.getEntityTypeId())) return;
        if (!WARNED_KEYS.add(aiKey)) return;
        CrafticsMod.LOGGER.warn(
            "No spawn customizer registered for AI key '{}' (entity type '{}'). "
            + "That combatant spawns undressed. Registered keys: {}",
            aiKey, entity.getEntityTypeId(), CUSTOMIZERS.keySet());
    }

    public static void apply(ServerWorld world, MobEntity mob, CombatEntity entity) {
        if (CUSTOMIZERS.isEmpty() || world == null || mob == null || entity == null) return;
        SpawnCustomizer c = null;
        String aiKey = entity.getAiKey();
        if (aiKey != null) c = CUSTOMIZERS.get(aiKey);
        if (c == null && entity.getEntityTypeId() != null) {
            c = CUSTOMIZERS.get(entity.getEntityTypeId());
        }
        if (c == null) {
            warnUnmatchedKey(entity);
            return;
        }
        try {
            c.onSpawn(world, mob, entity);
        } catch (Throwable t) {
            CrafticsMod.LOGGER.error("Spawn customizer for '{}' failed; the mob spawns uncustomised",
                aiKey != null ? aiKey : entity.getEntityTypeId(), t);
        }
    }

    /** Clear every registration. Test hook. */
    public static void clear() {
        WARNED_KEYS.clear();
        CUSTOMIZERS.clear();
    }
}
