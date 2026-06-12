package com.crackedgames.craftics.scene;

/**
 * One booth in a built scene.
 *
 * <p>A booth is defined by two {@code stand_marker} corners that form a rectangle
 * plus exactly one {@code npc_marker} sitting inside that rectangle. The rectangle
 * ({@code minX..maxX} × {@code minZ..maxZ} at floor {@code y}) is the <em>clickable
 * area</em>: clicking any tile inside it counts as clicking this booth. The
 * {@code npc_marker} supplies the booth's identity: where the NPC stands
 * ({@code npcX,npcY,npcZ} / {@code npcYaw}), the player's walk-up pose
 * ({@code playerX,playerY,playerZ} / {@code playerYaw}, one tile in front of the
 * NPC facing it), the occupant id, and whether the booth is dedicated or overflow.
 */
public record StandSlot(int minX, int minZ, int maxX, int maxZ, int y,
                        int npcX, int npcY, int npcZ, float npcYaw,
                        int playerX, int playerY, int playerZ, float playerYaw,
                        String occupant, Kind kind) {

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

    /** True when world tile ({@code tx},{@code tz}) lies within this booth's clickable rectangle. */
    public boolean contains(int tx, int tz) {
        return tx >= minX && tx <= maxX && tz >= minZ && tz <= maxZ;
    }
}
