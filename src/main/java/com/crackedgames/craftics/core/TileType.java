package com.crackedgames.craftics.core;

public enum TileType {
    NORMAL(true, false, 0),
    OBSTACLE(false, false, 0),
    LAVA(true, false, 3),
    FIRE(true, false, 2),
    VOID(false, false, 0),
    EXIT(true, false, 0),
    WATER(false, true, 0); // not walkable by default, requires boat

    public final boolean walkable;
    public final boolean requiresBoat; // walkable only if player has a boat
    public final int damageOnStep;

    TileType(boolean walkable, boolean requiresBoat, int damageOnStep) {
        this.walkable = walkable;
        this.requiresBoat = requiresBoat;
        this.damageOnStep = damageOnStep;
    }
}
