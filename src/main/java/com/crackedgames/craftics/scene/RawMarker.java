package com.crackedgames.craftics.scene;

/**
 * A single marker block hit found while scanning a scene schematic, reduced to
 * plain data so {@link SceneLayoutResolver} (and its unit tests) never touch
 * Minecraft world/block types. Coordinates are absolute world coordinates.
 */
public record RawMarker(Kind kind, int x, int y, int z, float yaw, String occupant) {

    public enum Kind {
        /** The scene spawn / camera vantage marker. {@code occupant} is unused. */
        SPAWN,
        /** A booth marker. {@code occupant} is the dedicated merchant id or an overflow wildcard. */
        STAND
    }
}
