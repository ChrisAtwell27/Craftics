package com.crackedgames.craftics.api;

/**
 * One ally sitting on a player's bench during a fight, as an addon sees it.
 *
 * <p>A read-only view, handed out so a mod can draw its own switch menu. The bench itself
 * lives in the fight and is not addressable from outside it; what an addon gets is this
 * description plus the {@link #index()} needed to name a slot when asking for a swap.
 *
 * <p>Addressed by index rather than by entity id because a benched ally has no entity. It has
 * no mob in the world, no tile on the grid, and no id to be found by - that absence is what
 * being benched IS.
 *
 * <p>Indices are only valid until the next swap, which reorders the bench. Read the bench
 * again after one rather than holding indices across it.
 *
 * @param index        position on the bench, and the handle
 *                     {@link CrafticsAPI#switchFieldAlly} takes
 * @param entityTypeId the entity type this ally is rendered as
 * @param aiKey        its AI and typing key, which is what actually distinguishes two
 *                     creatures sharing an entity type. Never null - falls back to
 *                     {@code entityTypeId}
 * @param displayName  the name to show for it
 * @param hp           current health. Equal to {@code maxHp} for an ally that has not yet
 *                     been on the field; a wounded one that was benched keeps its damage
 * @param maxHp        its maximum health
 * @since 0.4.0
 */
public record BenchedAlly(int index,
                          String entityTypeId,
                          String aiKey,
                          String displayName,
                          int hp,
                          int maxHp) {
}
