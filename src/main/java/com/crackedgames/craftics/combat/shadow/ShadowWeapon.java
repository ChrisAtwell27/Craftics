package com.crackedgames.craftics.combat.shadow;

/**
 * One weapon out of the player's inventory, as the Shadow's planner sees it: what it costs,
 * how far it reaches and how strong it is.
 *
 * <p>The numbers are the ones the player sees on the tooltip, straight from the weapon
 * registry. What the weapon DOES when it hits is not described here at all: the Shadow runs
 * the weapon's own ability code for that (see {@code ShadowStrike}).
 *
 * @param slot   where the stack sits in the copied kit, or -1 for bare fists
 * @param itemId registry id, for logs and messages
 * @param power  attack power in the player's hands
 * @param apCost AP to use it once
 * @param range  reach in tiles
 * @param ranged true for bows, thrown weapons and anything else that needs a clear line
 */
public record ShadowWeapon(int slot, String itemId, int power, int apCost, int range, boolean ranged) {

    /** Whether this weapon can hit something {@code distance} tiles away right now. */
    public boolean reaches(int distance, boolean lineOfSight) {
        return distance <= range && (!ranged || lineOfSight);
    }
}
