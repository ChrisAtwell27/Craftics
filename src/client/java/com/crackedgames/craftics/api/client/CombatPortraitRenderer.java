package com.crackedgames.craftics.api.client;

import net.minecraft.client.gui.DrawContext;

/**
 * Draws the portrait for one combatant in Craftics' combat HUD.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Craftics picks a head icon by <b>entity type</b>. That works when a type names a creature,
 * and fails completely when one entity type stands in for hundreds - every creature in the fight
 * is the same registry id, so any icon registered for it would be right for one of them and
 * wrong for all the others. The HUD falls back to a coloured square with a letter in it, which
 * is the honest answer to "I have no idea what this is" and a poor one to look at for a whole
 * fight.
 *
 * <p>It is the icon-side version of the problem AI keys already solve on the server: the thing
 * that identifies a combatant is not always its entity type. Registering a texture cannot fix
 * it, so the hook hands over the drawing instead.
 *
 * <p>The renderer is given the <b>entity id</b>, which is what the roster is keyed by, so it can
 * find the live entity in the client world and ask whatever that entity's own mod knows about
 * it. That is usually much better than a flat image: a mod with a real model renderer can draw
 * the same portrait its own screens use.
 *
 * <h2>Contract</h2>
 *
 * <p>Return {@code true} if you drew something, {@code false} to let Craftics carry on to its
 * own head texture or coloured square. Renderers are asked in registration order and the first
 * to claim a combatant wins, so returning false for anything you do not recognise is important -
 * a renderer that claims everything blanks out the rest of the fight.
 *
 * <p>Draw inside {@code x, y} to {@code x + size, y + size}. Nothing clips you to it; drawing
 * outside lands on top of neighbouring panels.
 *
 * <pre>{@code
 * CrafticsClientAPI.registerPortraitRenderer((ctx, entityId, typeId, x, y, size, damageTint) -> {
 *     if (!typeId.equals("mymod:creature")) return false;      // not ours - let Craftics draw
 *     var world = MinecraftClient.getInstance().world;
 *     if (world == null || !(world.getEntityById(entityId) instanceof MyCreature c)) return false;
 *     MyRenderer.drawPortrait(ctx, c, x, y, size, damageTint);
 *     return true;
 * });
 * }</pre>
 *
 * @since 0.4.0
 */
@FunctionalInterface
public interface CombatPortraitRenderer {

    /**
     * Draw one combatant's portrait, or decline it.
     *
     * @param ctx        the draw context for this frame
     * @param entityId   client-world entity id of the combatant. The roster is keyed by this,
     *                   so it resolves through {@code world.getEntityById}
     * @param typeId     its entity type id, with any Craftics stat suffix already stripped.
     *                   Cheap to test before doing an entity lookup
     * @param x          left edge of the square to draw in
     * @param y          top edge of the square to draw in
     * @param size       width and height, in GUI pixels. Portraits are square and appear at
     *                   several sizes: the rosters and the inspect panel do not agree on one
     * @param damageTint how hurt the combatant is, {@code 0} untouched to {@code 1} nearly
     *                   dead. Craftics reddens its own heads by exactly this, and the column
     *                   reads health at a glance because of it - a portrait that ignores it
     *                   drops information the roster currently carries. {@code 0} at sites
     *                   that do not tint, so honouring it always matches what Craftics drew
     * @return true if you drew it; false to fall through to Craftics' own icon
     */
    boolean drawPortrait(DrawContext ctx, int entityId, String typeId,
                         int x, int y, int size, float damageTint);
}
