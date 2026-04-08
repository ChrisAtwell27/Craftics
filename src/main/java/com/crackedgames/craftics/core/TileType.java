package com.crackedgames.craftics.core;

public enum TileType {
    NORMAL(true, false, 0),
    OBSTACLE(false, false, 0),
    LAVA(true, false, 10),
    FIRE(true, false, 2),
    VOID(false, false, 0),
    EXIT(true, false, 0),
    WATER(true, false, 0), // walkable without a boat (applies Soaked)
    DEEP_WATER(false, false, 0), // 2+ blocks deep — instant kill
    LOW_GROUND(true, false, 0), // 1 block lower than floor — walkable, Y-1 positioning
    POWDER_SNOW(true, false, 0); // walkable, sinks (Y-1), escalating freeze damage unless leather boots

    public final boolean walkable;
    public final boolean requiresBoat; // walkable only if player has a boat
    public final int damageOnStep;

    TileType(boolean walkable, boolean requiresBoat, int damageOnStep) {
        this.walkable = walkable;
        this.requiresBoat = requiresBoat;
        this.damageOnStep = damageOnStep;
    }
}
