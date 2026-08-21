package com.crackedgames.craftics.core;

/**
 * The things a player can say by pinging a tile in co-op.
 *
 * <p>Six of them, deliberately. A ping wheel is read by muscle memory, not by reading labels, and
 * every option past about six turns a flick of the wrist into a decision. These cover the two
 * kinds of thing worth pointing at (a threat, a reward) and the two kinds of thing worth
 * promising (I have this one, you take that one), plus a neutral "look here" and a warning.
 *
 * <p>The glyphs are drawn from the set Craftics already renders elsewhere in the HUD. Picking
 * prettier symbols is a real risk: a codepoint the game's font does not cover renders as a
 * tofu box, and a ping wheel of tofu is worse than no wheel.
 *
 * <p>Ordinals cross the wire, which is safe only because both sides ship in the same jar.
 * {@link #byId} still refuses to trust the number, because a malformed or stale packet should
 * land on a harmless "look here" rather than throw inside a network receiver.
 */
public enum PingType {

    /** Neutral attention marker. What a quick tap of the ping key sends. */
    LOOK("⬤", "Look Here", 0xFFE8E8E8),

    /** "There is something dangerous here." */
    ENEMY("⚔", "Enemy", 0xFFFF4C4C),

    /** "There is something worth picking up here." */
    LOOT("✨", "Loot", 0xFFFFC83C),

    /** "I'll take care of this." */
    ON_IT("✊", "On It", 0xFF4CE07A),

    /** "Take this one." */
    YOURS("➳", "You Take It", 0xFF5AAEFF),

    /** "Careful" / "don't go in yet". */
    CAREFUL("✖", "Careful", 0xFFFF9A3C);

    /** Single-character symbol shown on the wheel, in the log line and over the tile. */
    public final String glyph;

    /** Human-readable label for the wheel and the log. */
    public final String label;

    /** Packed ARGB used for the wheel chip, the log line and the world marker. */
    public final int color;

    PingType(String glyph, String label, int color) {
        this.glyph = glyph;
        this.label = label;
        this.color = color;
    }

    /** Red channel as 0..1, for the tile renderer (which works in floats). */
    public float red() { return ((color >> 16) & 0xFF) / 255f; }

    /** Green channel as 0..1. */
    public float green() { return ((color >> 8) & 0xFF) / 255f; }

    /** Blue channel as 0..1. */
    public float blue() { return (color & 0xFF) / 255f; }

    /** The Minecraft formatting code that most nearly matches {@link #color}, for chat/log text. */
    public String chatColor() {
        return switch (this) {
            case LOOK -> "§f";
            case ENEMY -> "§c";
            case LOOT -> "§6";
            case ON_IT -> "§a";
            case YOURS -> "§b";
            case CAREFUL -> "§e";
        };
    }

    /**
     * Which option a radial wheel selects for a cursor offset from its centre, or null when the
     * cursor is still inside {@code deadZone} pixels of the middle.
     *
     * <p>Options sit clockwise from straight up, one per equal slice. Screen Y grows downward,
     * so the angle is {@code atan2(dx, -dy)}: that puts zero at twelve o'clock and increases
     * clockwise, matching how the chips are drawn.
     *
     * <p>This lives here rather than in the wheel widget so it can be tested. The mapping from
     * "where the player flicked" to "what they said" is the one piece of the ping system that
     * can be silently wrong - every option still highlights, just the wrong one - and it is pure
     * arithmetic over two integers, which is exactly the kind of thing worth pinning down.
     *
     * @param deadZone radius, in the same units as dx/dy, inside which no option is selected
     */
    public static PingType fromOffset(int dx, int dy, int deadZone) {
        if (dx * dx + dy * dy < deadZone * deadZone) return null;
        PingType[] all = values();
        double angle = Math.atan2(dx, -dy);
        if (angle < 0) angle += Math.PI * 2;
        double slice = Math.PI * 2 / all.length;
        // The half-slice offset centres each option on its chip rather than on a boundary,
        // so flicking straight at a chip picks that chip.
        int index = (int) Math.floor((angle + slice / 2) / slice) % all.length;
        return all[index];
    }

    /**
     * Decode a wire ordinal. Anything out of range becomes {@link #LOOK} rather than throwing:
     * this runs in a packet handler, where an exception is a disconnect and a wrong-but-harmless
     * marker is a shrug.
     */
    public static PingType byId(int id) {
        PingType[] all = values();
        return (id < 0 || id >= all.length) ? LOOK : all[id];
    }
}
