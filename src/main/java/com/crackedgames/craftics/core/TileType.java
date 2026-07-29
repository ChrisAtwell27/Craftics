package com.crackedgames.craftics.core;

public enum TileType {
    NORMAL(true, false, 0, false),
    OBSTACLE(false, false, 0, false),
    LAVA(true, false, 10, false),
    FIRE(true, false, 2, false),
    // Soul fire - what a burn on soul sand / soul soil becomes. Same three-stage burn as
    // FIRE, but it holds its flames a turn longer (so the front keeps advancing while the
    // ground behind it is still alight) and sets whoever stands in it to Burning III.
    SOUL_FIRE(true, false, 2, false),
    VOID(false, false, 0, false),
    WATER(true, false, 0, false), // walkable without a boat (applies Soaked)
    DEEP_WATER(false, false, 0, false), // 2+ blocks deep - instant kill
    LOW_GROUND(true, false, 0, false), // 1 block lower than floor - walkable, Y-1 positioning
    POWDER_SNOW(true, false, 0, false), // walkable, sinks (Y-1), escalating freeze damage unless leather boots
    // Miniboss hazard tiles. Placed mid-fight via GridTile.setTemporaryType(type, 3)
    // so they revert after 3 rounds like other temporary terrain.
    RUBBLE(false, false, 0, false),   // collapsed ceiling / dropped boulder - blocks like an obstacle
    SPORE(true, false, 0, false),     // walkable; the Fungal Bloom mechanic applies Poison on step
    EMBER(true, false, 2, false),     // walkable; burns (Burning applied by the mechanic on top of step dmg)
    FROST(true, false, 0, false),     // walkable; Blizzard applies Frozen risk on step
    SCULK(true, false, 0, false),     // sculk-sensor boundary paint - walkable, purely visual
    MUD(true, false, 0, false),       // rain-churned ground - walkable, but each mud tile
                                      // traveled has a 50% chance to stop movement there
                                      // (probabilistic cobweb; see the path truncation in
                                      // CombatManager). Placed by the jungle_rain biome effect.
    // Withered ground - the rot the Wither leaves behind. Walkable, permanent for the fight,
    // and it bites every time you set foot on it (damage + Wither, see the landing check in
    // CombatManager.handleMove). Unlike the aura that creates it, this does not expire: the
    // Wither poisons the arena as it moves, so the safe ground shrinks turn by turn and standing
    // still stops being an option.
    DECAY(true, false, 2, false),
    // Fungus hazard tiles: walkable, cobweb-like. Crossing one does NOT stop the player,
    // but inflicts a status effect live on their own turn (see the fungus scan in
    // CombatManager.handleMove). Scattered at fight start by the crimson/warped biome effects.
    CRIMSON_FUNGUS(true, false, 0, false), // crimson forest - inflicts Bleeding 1 on cross
    WARPED_FUNGUS(true, false, 0, false),  // warped forest - inflicts Warped 2 on cross
    // Deeper-and-Darker compat hazards (Blooming Caverns). Walkable, cobweb-like:
    // crossing does NOT stop the player but fires an on-step effect in
    // CombatManager.handleMove (same scan the fungus tiles use).
    BLOOM(true, false, 2, false),   // glowing bloom growth - 2 step dmg + Burning on cross
    GEYSER(true, false, 0, false),  // sculk geyser - step-trap: Burning II + random launch up to 3 tiles
    // Sculk jaw - step-trap that bites for damage AND drains an XP level, which it
    // holds until the jaw is destroyed. Sculk creatures are immune in the source mod,
    // and they are here too by omission: no enemy movement path reads this tile, so
    // only players can ever set it off.
    SCULK_JAW(true, false, 0, false),
    // Occupants are visually hidden (INVISIBILITY effect) and mobs can't target them
    // except from an adjacent tile. Breakable by attacking the tile for 1 AP.
    TALL_GRASS(true, false, 0, true),
    TALL_FERN(true, false, 0, true),
    // Half-step at Y+0.5 - walkable, connects a floor tile to an adjacent
    // ELEVATED tile so the player can climb a single block of height
    // smoothly instead of teleporting up. Detected from any StairsBlock at
    // the floor's Y+1 in ArenaBuilder's classification scan.
    STAIR(true, false, 0, false),
    // Full block one level above the arena floor (Y+1) that's adjacent to
    // at least one STAIR tile - treated as the upper-floor landing of a
    // stair ramp. Standing at Y+1 instead of Y. From here the player can
    // walk down to the stair (Y+0.5) or drop straight to a base floor tile.
    ELEVATED(true, false, 0, false);

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

    /**
     * True for the open-flame tiles - the ones whose block lives at Y+1 on an intact floor
     * rather than at floor level, and which set anything standing in them alight.
     *
     * <p>THE predicate for "is this tile on fire". Every site that used to compare against
     * {@code FIRE} alone reads this instead, so adding {@link #SOUL_FIRE} didn't silently
     * leave half the fire rules (AI avoidance, step damage, stealth, painting) blind to it.
     */
    public boolean isFlames() {
        return this == FIRE || this == SOUL_FIRE;
    }
}
