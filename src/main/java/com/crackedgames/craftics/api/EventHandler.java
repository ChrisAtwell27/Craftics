package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.EventManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.List;

/**
 * Executes the logic for a registered between-level event.
 *
 * <p>When Craftics triggers a registered {@link com.crackedgames.craftics.api.registry.EventEntry},
 * it calls {@link #execute} on the entry's handler (if non-null) with the participating
 * players, the server world, and the {@code EventManager} for dispatching rewards, UI
 * prompts, and follow-up events.
 *
 * <p>Implement this interface, typically as a lambda, and supply it when constructing an
 * {@link com.crackedgames.craftics.api.registry.EventEntry}:
 *
 * <pre>{@code
 * new EventEntry(
 *     "mymod:ancient_vault", "Ancient Vault",
 *     0.05f, 5, true,
 *     (participants, world, eventManager) -> {
 *         // grant loot, show choice screen, etc.
 *     }
 * );
 * }</pre>
 *
 * @since 0.2.0
 */
@FunctionalInterface
public interface EventHandler {

    /**
     * Run the event's logic.
     *
     * @param participants the players taking part in the current run
     * @param world        the server world the run is taking place in
     * @param eventManager the event manager for dispatching rewards and follow-up events
     */
    void execute(List<ServerPlayerEntity> participants, ServerWorld world,
                 EventManager eventManager);
}
