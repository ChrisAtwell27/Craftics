package com.crackedgames.craftics.core;

public enum TileType {
    NORMAL(true, false, 0, false),
    OBSTACLE(false, false, 0, false),
    LAVA(true, false, 10, false),
    FIRE(true, false, 2, false),
    VOID(false, false, 0, false),
    EXIT(true, false, 0, false),
    WATER(true, false, 0, false), // walkable without a boat (applies Soaked)
    DEEP_WATER(false, false, 0, false), // 2+ blocks deep — instant kill
    LOW_GROUND(true, false, 0, false), // 1 block lower than floor — walkable, Y-1 positioning
    POWDER_SNOW(true, false, 0, false), // walkable, sinks (Y-1), escalating freeze damage unless leather boots
    // Occupants are visually hidden (INVISIBILITY effect) and mobs can't target them
    // except from an adjacent tile. Breakable by attacking the tile for 1 AP.
    TALL_GRASS(true, false, 0, true),
    TALL_FERN(true, false, 0, true);

    public final boolean walkable;
    public final boolean requiresBoat; // walkable only if player has a boat
    public final int damageOnStep;
    public final boolean providesStealth;

    TileType(boolean walkable, boolean requiresBoat, int damageOnStep, boolean providesStealth) {
        this.walkable = walkable;
        this.requiresBoat = requiresBoat;
        this.damageOnStep = damageOnStep;
        this.providesStealth = providesStealth;
    }
}
