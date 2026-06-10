package com.crackedgames.craftics.scene;

/**
 * One booth in a built scene: where the player lerp-walks to ({@code x,y,z}),
 * which way they face when there ({@code standYaw}), the occupant id, and whether
 * it is a dedicated single-merchant booth or the addon overflow booth.
 */
public record StandSlot(int x, int y, int z, float standYaw, String occupant, Kind kind) {

    public enum Kind {
        /** Hosts exactly one merchant, identified by {@code occupant}. */
        DEDICATED,
        /** Hosts all met merchants of a type not claimed by a dedicated booth. */
        OVERFLOW
    }

    /** True when {@code occupant} is one of the addon overflow wildcards. */
    public static boolean isOverflowWildcard(String occupant) {
        return "villager:addon".equals(occupant) || "piglin:addon".equals(occupant);
    }
}
