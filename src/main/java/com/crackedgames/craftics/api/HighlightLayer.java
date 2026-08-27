package com.crackedgames.craftics.api;

/**
 * The four tile overlays Craftics paints on the combat grid, addressable by an addon.
 *
 * <p>Craftics computes each of these every time the highlights refresh, from the player's own
 * move budget, weapon range and the enemies' reach. An addon whose turn is spent commanding a
 * creature rather than swinging a sword is describing a different set of tiles entirely, and had
 * no way to say so: the lists are rebuilt from scratch on every refresh, so anything an addon
 * drew on top of them lasted until the next click.
 *
 * <p>An overlay set through {@link com.crackedgames.craftics.api.CrafticsAPI#setHighlights}
 * survives that rebuild - it is re-applied on every refresh until it is cleared - and can either
 * add to Craftics' own tiles or replace them for that layer.
 *
 * @see com.crackedgames.craftics.api.CrafticsAPI#setHighlights
 * @since 0.4.5
 */
public enum HighlightLayer {

    /** Green. Where the actor can walk. Craftics fills it from the player's remaining move points. */
    MOVE,

    /** Red. What the actor can hit from where it stands. Craftics fills it from the held weapon's range. */
    ATTACK,

    /**
     * Orange. Tiles an enemy could reach on its next turn.
     *
     * <p>Craftics only fills this when the player has enemy range hints turned on; an overlay is
     * drawn either way, because an addon's danger tiles are its own signal rather than that hint.
     */
    DANGER,

    /**
     * Flashing red. An attack that is coming, on the tiles it will land on - the boss telegraph.
     *
     * <p>{@link com.crackedgames.craftics.api.CrafticsAPI#showWarning} sets this layer together
     * with the marching direction arrows, which is how a push, pull or charge says which way it
     * travels.
     */
    WARNING
}
