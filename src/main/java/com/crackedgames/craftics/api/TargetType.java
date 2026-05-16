package com.crackedgames.craftics.api;

/**
 * Describes what a {@link com.crackedgames.craftics.api.registry.UsableItemEntry usable item}
 * targets when the player activates it during a Craftics turn.
 *
 * <p>Craftics uses this to validate the player's click and to highlight valid tiles
 * before the item's {@link UsableItemHandler} runs.
 *
 * @since 0.2.0
 */
public enum TargetType {
    /** No target — the item acts on the player only (food, a self-buff, …). */
    SELF,
    /** A single hostile combatant standing on the targeted tile. */
    SINGLE_ENEMY,
    /** A single allied combatant (pet, party member) standing on the targeted tile. */
    SINGLE_ALLY,
    /** Any tile within range, occupied or not (placing a hazard, teleporting, …). */
    ANY_TILE,
    /** An area centered on the targeted tile. */
    AOE
}
