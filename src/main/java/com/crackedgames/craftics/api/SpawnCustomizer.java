package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.CombatEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * A hook that runs once on a freshly spawned arena mob, after Craftics has created it
 * and before it takes its first turn.
 *
 * <p>Craftics spawns every enemy the same way: look the entity type up in the registry
 * and create it. That is enough for a zombie, whose identity IS its entity type, and not
 * enough for a mod whose identity lives somewhere else. The motivating case is a mod that
 * ships one entity type for hundreds of creatures and stores which creature it is in a
 * component or in NBT: created bare, every one of them spawns blank and identical.
 *
 * <p>Two ways to fix that, and they cover different ground:
 *
 * <ul>
 *   <li>{@code spawnNbt} on the enemy entry, which a DATAPACK can author with no Java at
 *       all. Right for anything expressible as entity NBT.</li>
 *   <li>This hook, for what NBT cannot say - a mod whose entity has to be initialised
 *       through its own API rather than by writing tags onto it.</li>
 * </ul>
 *
 * <p>Both run if both are present, NBT first, so a customizer sees the tagged entity and
 * can correct or extend it.
 *
 * <h2>Which key it is looked up by</h2>
 *
 * <p>Registered against a key that is matched first against the enemy's {@code aiKey} and
 * then against its entity type id. That order matters for exactly the case above: a mod
 * with one entity type distinguishes its creatures by {@code aiKey}, so keying on the AI
 * lets each one initialise itself while they all share an entity type.
 *
 * <pre>{@code
 * // One entity type, many creatures: key on the aiKey, which differs per creature.
 * SpawnCustomizerRegistry.register("mymod:charizard", (world, mob, entity) -> {
 *     MyModApi.setSpecies(mob, "charizard");
 * });
 * }</pre>
 *
 * <p>Exceptions are caught and logged by the caller: a failing customizer leaves an
 * ordinary mob standing in the arena rather than aborting the spawn, because an arena
 * that is one enemy short is recoverable and a half-built fight is not.
 *
 * @since 0.3.9
 */
@FunctionalInterface
public interface SpawnCustomizer {

    /**
     * Initialise a newly spawned arena mob.
     *
     * @param world  the arena world
     * @param mob    the live entity, already positioned and tagged {@code craftics_arena}
     * @param entity the Craftics combat entity wrapping it, carrying its stats, grid
     *               position and {@code aiKey}. Its footprint and stats are already set.
     */
    void onSpawn(ServerWorld world, MobEntity mob, CombatEntity entity);
}
