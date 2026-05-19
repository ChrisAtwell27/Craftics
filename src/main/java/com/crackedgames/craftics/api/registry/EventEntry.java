package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.EventHandler;

/**
 * Immutable definition of a between-level event that Craftics can trigger during a run.
 *
 * <p>After each level Craftics rolls a random float and walks the registered event list,
 * triggering the first event whose cumulative probability covers the roll. Built-in
 * events (ambush, shrine, trader, …) are registered by {@code VanillaContent}; addons
 * register their own through {@code CrafticsAPI.registerEvent}:
 *
 * <pre>{@code
 * CrafticsAPI.registerEvent(new EventEntry(
 *     "mymod:ancient_vault", "Ancient Vault",
 *     0.05f, 5, true,
 *     (participants, world, eventManager) -> { ... }
 * ));
 * }</pre>
 *
 * @param id             unique event id, e.g. {@code "mymod:ancient_vault"}
 * @param displayName    name shown to players when the event triggers
 * @param probability    base probability per level (e.g. {@code 0.10f} for 10 percent);
 *                       total registered probability above {@code 0.90} is automatically
 *                       scaled down to prevent overflow
 * @param minBiomeOrdinal the minimum biome ordinal at which this event can trigger;
 *                        {@code 0} means the event is available from the first biome
 * @param isChoiceEvent  whether this event presents a player choice rather than
 *                       triggering automatically
 * @param handler        logic executed when the event fires; may be {@code null} for
 *                       built-in events handled directly by {@code CombatManager}
 * @since 0.2.0
 */
public record EventEntry(
    String id,
    String displayName,
    float probability,
    int minBiomeOrdinal,
    boolean isChoiceEvent,
    EventHandler handler
) {}
