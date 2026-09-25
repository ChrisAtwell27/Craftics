package com.crackedgames.craftics.core;

import com.crackedgames.craftics.combat.CombatEntity;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

public class GridArena {
    private final int width;
    private final int height;
    private final GridTile[][] tiles;
    private final BlockPos origin;
    private final int levelNumber;
    private final GridPos playerStart;

    /** Optional polygon mask for non-rectangular arenas. {@code null} means the
     *  full {@code width × height} rectangle is playable (legacy behavior).
     *  When non-null, {@code insideMask[x][z] == true} marks a tile as inside
     *  the polygon. Every {@link #isInBounds(int, int)} caller transparently
     *  inherits the polygon - pathfinding, AI, VFX, occupancy all gate on it. */
    private final boolean[][] insideMask;

    private final Map<GridPos, CombatEntity> occupants = new HashMap<>();
    private GridPos playerGridPos;
    private final Map<GridPos, Integer> webOverlays = new HashMap<>();
    /** Dragon breath clouds by tile: {turns left, damage}. */
    private final Map<GridPos, int[]> breathClouds = new HashMap<>();

    /** Tracks tiles that were converted to OBSTACLE by VFX (mace slam debris landing).
     *  Value is the prior TileType so we can restore on cleanup. */
    private final java.util.Map<GridPos, TileType> vfxObstaclePriorType = new java.util.HashMap<>();

    /**
     * Temporary blocks standing on a tile. Tracks the item to give back when the block is
     * broken, the turns remaining before it crumbles on its own, and the starting duration
     * so the client can show progressive breaking texture as it ticks down.
     * These tiles also live in {@link #vfxObstaclePriorType} for tile-type
     * restoration; the two maps are kept in sync.
     *
     * <p>{@code item} is null for a block nobody paid for - debris thrown up by a weapon or
     * a boss and left where it landed. It crumbles on the same clock and cracks the same way
     * as a wall the player built; it just does not hand anything back when it goes.
     */
    public record PlacedWall(net.minecraft.item.Item item, int turnsRemaining, int startTurns) {
        public PlacedWall withTurns(int newTurns) { return new PlacedWall(item, newTurns, startTurns); }
    }
    private final java.util.Map<GridPos, PlacedWall> placedWalls = new java.util.HashMap<>();

    public java.util.Map<GridPos, PlacedWall> getPlacedWalls() { return placedWalls; }
    public PlacedWall getPlacedWall(GridPos pos) { return placedWalls.get(pos); }
    public boolean isPlacedWall(GridPos pos) { return placedWalls.containsKey(pos); }

    public GridArena(int width, int height, GridTile[][] tiles, BlockPos origin,
                     int levelNumber, GridPos playerStart) {
        this(width, height, tiles, origin, levelNumber, playerStart, null);
    }

    /** Polygon-aware constructor. Pass a {@code width × height} boolean array
     *  marking which tiles are inside the polygon, or {@code null} to use the
     *  legacy full-rectangle behavior. */
    public GridArena(int width, int height, GridTile[][] tiles, BlockPos origin,
                     int levelNumber, GridPos playerStart, boolean[][] insideMask) {
        this.width = width;
        this.height = height;
        this.tiles = tiles;
        this.origin = origin;
        this.levelNumber = levelNumber;
        this.playerStart = playerStart;
        this.playerGridPos = playerStart;
        this.insideMask = insideMask;
    }

    /** Whether this arena uses a non-rectangular polygon mask. */
    public boolean hasPolygonMask() { return insideMask != null; }

    /** The {@code width × height} polygon mask, or {@code null} for a rectangular
     *  arena. Used to pack the shape into {@code EnterCombatPayload} so the client
     *  can restrict the cursor to the playable polygon. */
    public boolean[][] getInsideMask() { return insideMask; }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public BlockPos getOrigin() { return origin; }
    public int getLevelNumber() { return levelNumber; }
    public GridPos getPlayerStart() { return playerStart; }

    public GridTile getTile(int x, int z) {
        if (!isInBounds(x, z)) return null;
        return tiles[x][z];
    }

    public GridTile getTile(GridPos pos) {
        return getTile(pos.x(), pos.z());
    }

    /** Set a tile at the given position. Creates a new GridTile with the given type. */
    public void setTile(GridPos pos, GridTile tile) {
        if (isInBounds(pos)) {
            tiles[pos.x()][pos.z()] = tile;
        }
    }

    public boolean isInBounds(int x, int z) {
        if (x < 0 || x >= width || z < 0 || z >= height) return false;
        // Polygon arenas: the rectangle bounds-check is necessary but not
        // sufficient - tile must also be inside the polygon mask. This single
        // gate is consulted by every pathfinding / AI / VFX / occupancy call
        // site in the codebase, so the polygon shape propagates everywhere
        // without each caller needing to know about it.
        if (insideMask != null) return insideMask[x][z];
        return true;
    }

    public boolean isInBounds(GridPos pos) {
        return isInBounds(pos.x(), pos.z());
    }

    /** The grid tile over world column {@code (blockX, blockZ)}, or null outside the grid. */
    public GridPos gridPosAtColumn(int blockX, int blockZ) {
        GridPos pos = new GridPos(blockX - origin.getX(), blockZ - origin.getZ());
        return isInBounds(pos) ? pos : null;
    }

    /**
     * Whether a player who has sunk below the floor at world column {@code (blockX, blockZ)} dies
     * for it. Only a VOID tile kills: that is the one tile the grid tells you drops you.
     *
     * <p>Everywhere else a fall means the world and the grid disagree - the block under a walkable
     * tile is gone, deep water swallowed someone the grid never moved there, a shove carried them
     * off the edge - and the player walked or was put somewhere the game promised would hold them.
     * Those are rescues, never deaths.
     */
    public boolean fallIsLethalAt(int blockX, int blockZ) {
        GridPos pos = gridPosAtColumn(blockX, blockZ);
        if (pos == null) return false;
        GridTile tile = getTile(pos);
        return tile != null && tile.getType() == TileType.VOID;
    }

    // --- Occupant tracking ---

    /**
     * True when {@code pos} can hold a placed block - a grave, a hive, a sculk sensor, any of
     * the block-backed objects a mechanic scatters at fight start.
     *
     * <p>Walkability is the test because it is exactly "this tile has a floor". A VOID tile is
     * a hole in the arena, and a block placed on one is placed in mid-air over a pit: it looks
     * like ground the player can use, the grid still says the tile kills you, and mining it
     * reverts the tile to plain floor - which turns a death pit into walkable ground with no
     * world block under it.
     */
    public boolean isPlaceableFloor(GridPos pos) {
        if (pos == null || !isInBounds(pos)) return false;
        GridTile tile = getTile(pos);
        if (tile == null || !tile.isWalkable()) return false;
        return !isOccupied(pos);
    }

    public boolean isOccupied(GridPos pos) {
        if (pos.equals(playerGridPos)) return true;
        for (GridPos p : allPlayerGridPositions) {
            if (pos.equals(p)) return true;
        }
        var occupant = occupants.get(pos);
        // Background bosses don't block movement - they're targetable but pass-through
        return occupant != null && !occupant.isBackgroundBoss();
    }

    public boolean isEnemyOccupied(GridPos pos) {
        if (pos.equals(playerGridPos)) return true;
        var occupant = occupants.get(pos);
        return occupant != null && !occupant.isBackgroundBoss();
    }

    public CombatEntity getOccupant(GridPos pos) {
        return occupants.get(pos);
    }

    /**
     * Register {@code entity}'s footprint in the occupant map. Never overwrites another
     * entity: the map is tile-keyed, so a blind put would evict whoever was standing there
     * while leaving BOTH mobs rendered on the square. Callers resolve a free spawn tile
     * first; this refuses the rest.
     *
     * @return {@code true} if the entity was placed
     */
    public boolean placeEntity(CombatEntity entity) {
        if (!canOccupy(entity, entity.getGridPos())) {
            com.crackedgames.craftics.CrafticsMod.LOGGER.warn(
                "Refused to place {} at {} - tile already occupied",
                entity.getEntityTypeId(), entity.getGridPos());
            return false;
        }
        for (GridPos tile : getOccupiedTiles(entity)) {
            occupants.put(tile, entity);
        }
        return true;
    }

    /**
     * Move {@code entity} to {@code newPos}, unless something else is already standing
     * there. Returns {@code true} when the move happened.
     *
     * <p>The occupant map is keyed by tile, so writing a second entity onto an occupied
     * tile used to silently overwrite the first: the map remembered only the newcomer
     * while BOTH mobs kept rendering on the square. Most callers check occupancy first,
     * but the ones that don't - notably the enemy teleport actions, which trust whatever
     * tile their AI picked - could stack two mobs on one tile. Refusing here is the
     * backstop: an enemy that fails to teleport is a far smaller problem than two enemies
     * merged into one square.
     */
    public boolean moveEntity(CombatEntity entity, GridPos newPos) {
        // Immovable entities (Creaking Heart and other virtual block enemies) must stay put:
        // their in-world block never moves, so relocating the grid entry would desync the
        // target and make them impossible to hit. Refuse the move.
        if (entity.isImmovable()) return false;
        // Background bosses are parked off the arena and hand-registered onto a block of
        // tiles that has nothing to do with their gridPos, which is only a sentinel. A move
        // would strip exactly one tile (the sentinel) out of that registration and then add
        // whatever tile it walked to, leaving the boss both half-registered where it belongs
        // AND standing on the floor somewhere it should never be. Nothing may relocate them.
        if (entity.isBackgroundBoss()) return false;
        if (!canOccupy(entity, newPos)) return false;
        // Remove from all old tiles
        for (GridPos tile : getOccupiedTiles(entity)) {
            occupants.remove(tile);
        }
        entity.setGridPos(newPos);
        // Place in all new tiles
        for (GridPos tile : getOccupiedTiles(entity)) {
            occupants.put(tile, entity);
        }
        return true;
    }

    /**
     * True if {@code entity}'s footprint at {@code newPos} would land only on tiles that
     * are free, or that the entity already occupies itself. Out-of-bounds tiles are
     * rejected; a background boss is pass-through and never blocks.
     */
    private boolean canOccupy(CombatEntity entity, GridPos newPos) {
        for (GridPos tile : getOccupiedTiles(newPos, entity.getSizeX(), entity.getSizeZ())) {
            if (!isInBounds(tile)) return false;
            CombatEntity other = occupants.get(tile);
            if (other != null && other != entity && !other.isBackgroundBoss()) return false;
        }
        return true;
    }

    public void removeEntity(CombatEntity entity) {
        for (GridPos tile : getOccupiedTiles(entity)) {
            occupants.remove(tile);
        }
    }

    /** All grid positions covered by an entity's (possibly rectangular) footprint. */
    public static java.util.List<GridPos> getOccupiedTiles(CombatEntity entity) {
        return getOccupiedTiles(entity.getGridPos(), entity.getSizeX(), entity.getSizeZ());
    }

    /** Returns all grid positions occupied by a square entity of the given size at the given origin. */
    public static java.util.List<GridPos> getOccupiedTiles(GridPos origin, int size) {
        return getOccupiedTiles(origin, size, size);
    }

    /** Returns all grid positions occupied by a {@code sizeX x sizeZ} footprint at the given origin. */
    public static java.util.List<GridPos> getOccupiedTiles(GridPos origin, int sizeX, int sizeZ) {
        java.util.List<GridPos> tiles = new java.util.ArrayList<>();
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                tiles.add(new GridPos(origin.x() + dx, origin.z() + dz));
            }
        }
        return tiles;
    }

    public Map<GridPos, CombatEntity> getOccupants() {
        return occupants;
    }

    /**
     * Manhattan distance from a ground blast at {@code from} to {@code e}, or
     * {@link Integer#MAX_VALUE} when nothing of it is on the ground to hit.
     *
     * <p>An ordinary combatant is measured to its tile. A background boss is not standing on its
     * grid position - that is a sentinel - so it is measured to the tiles registered for it, the
     * same surface ranged attacks aim at, and is out of reach while it has none. Measured to the
     * sentinel instead, a crystal or TNT dropped beside the parked Ender Dragon's perch corner hit
     * it while it was up in the sky and untargetable by anything else.
     */
    public int blastDistance(CombatEntity e, GridPos from) {
        if (!e.isBackgroundBoss()) return from.manhattanDistance(e.getGridPos());
        int best = Integer.MAX_VALUE;
        for (Map.Entry<GridPos, CombatEntity> entry : occupants.entrySet()) {
            if (entry.getValue() != e) continue;
            best = Math.min(best, from.manhattanDistance(entry.getKey()));
        }
        return best;
    }

    // --- Web overlay tracking (Broodmother) ---

    /** Sentinel duration meaning "this web never ticks down" - used for cobwebs
     *  baked into the arena's schematic or jungle-biome decoration. They only
     *  go away when a player walks through them. */
    public static final int PERMANENT_WEB = Integer.MAX_VALUE;

    public boolean hasWebOverlay(GridPos pos) {
        return webOverlays.containsKey(pos);
    }

    public void setWebOverlay(GridPos pos, int turns) {
        webOverlays.put(pos, turns);
    }

    public void clearWebOverlay(GridPos pos) {
        webOverlays.remove(pos);
    }

    /** Tick all web overlays. Returns positions where webs expired this tick.
     *  Webs registered with {@link #PERMANENT_WEB} are skipped - they only
     *  clear when a player walks through them. */
    public java.util.List<GridPos> tickWebOverlays() {
        java.util.List<GridPos> expired = new java.util.ArrayList<>();
        var it = webOverlays.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.getValue() == PERMANENT_WEB) continue;
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                expired.add(entry.getKey());
                it.remove();
            } else {
                entry.setValue(remaining);
            }
        }
        return expired;
    }

    public Map<GridPos, Integer> getWebOverlays() {
        return webOverlays;
    }

    public void clearAllWebOverlays() {
        webOverlays.clear();
    }

    // --- Dragon breath clouds (Ender Dragon) ---

    /**
     * Put a harming breath cloud on {@code pos} for {@code turns} rounds, biting for
     * {@code damage}. A cloud landing on one already there keeps the longer timer and the
     * harder bite of the two, so a weak cloud never softens a strong one.
     */
    public void setBreathCloud(GridPos pos, int turns, int damage) {
        if (pos == null || turns <= 0 || !isInBounds(pos)) return;
        int[] prior = breathClouds.get(pos);
        if (prior != null) {
            turns = Math.max(turns, prior[0]);
            damage = Math.max(damage, prior[1]);
        }
        breathClouds.put(pos, new int[]{turns, damage});
    }

    public boolean hasBreathCloud(GridPos pos) {
        return breathClouds.containsKey(pos);
    }

    /** What the cloud on {@code pos} bites for, or 0 when the tile is clear. */
    public int breathCloudDamage(GridPos pos) {
        int[] cloud = breathClouds.get(pos);
        return cloud != null ? cloud[1] : 0;
    }

    public java.util.Set<GridPos> getBreathCloudTiles() {
        return java.util.Collections.unmodifiableSet(breathClouds.keySet());
    }

    /** Every cloud flattened for the client's tile layer: {@code [x, z, turnsLeft, damage, ...]}. */
    public int[] breathCloudLayer() {
        int[] out = new int[breathClouds.size() * 4];
        int i = 0;
        for (Map.Entry<GridPos, int[]> e : breathClouds.entrySet()) {
            out[i++] = e.getKey().x();
            out[i++] = e.getKey().z();
            out[i++] = e.getValue()[0];
            out[i++] = e.getValue()[1];
        }
        return out;
    }

    /** Age every cloud one round. Returns the tiles whose cloud ran out. */
    public java.util.List<GridPos> tickBreathClouds() {
        java.util.List<GridPos> expired = new java.util.ArrayList<>();
        var it = breathClouds.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (--entry.getValue()[0] <= 0) {
                expired.add(entry.getKey());
                it.remove();
            }
        }
        return expired;
    }

    // --- VFX obstacle tracking (mace slam debris) ---

    /** Mark a tile as a VFX-placed obstacle. Remembers the prior tile type for cleanup. */
    /** @return true if the tile was taken, false if it was already something other than floor */
    public boolean markVfxObstacle(GridPos pos) {
        if (pos == null || !isInBounds(pos)) return false;
        GridTile t = getTile(pos);
        if (t == null) return false;
        // Don't overwrite existing obstacles or special tile types
        if (t.getType() != TileType.NORMAL) return false;
        vfxObstaclePriorType.put(pos, t.getType());
        t.setType(TileType.OBSTACLE);
        return true;
    }

    public boolean isVfxObstacle(GridPos pos) {
        return vfxObstaclePriorType.containsKey(pos);
    }

    /** Clear a single VFX obstacle - restores prior tile type and wipes the block in the world. */
    public void clearVfxObstacle(net.minecraft.server.world.ServerWorld world, GridPos pos) {
        TileType prior = vfxObstaclePriorType.remove(pos);
        placedWalls.remove(pos);
        if (prior == null) return;
        GridTile t = getTile(pos);
        if (t != null) t.setType(prior);
        // Remove the block from the world (gridToBlockPos gives the surface tile position)
        net.minecraft.util.math.BlockPos blockPos = gridToBlockPos(pos);
        world.setBlockState(blockPos, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
    }

    /**
     * Register a tile as holding a temporary block. The caller is
     * responsible for actually setting the block in the world and marking the
     * tile type via {@link #markVfxObstacle}. {@code item} is the item that
     * was consumed, so mining can refund it, or null for debris that cost nobody
     * anything and gives nothing back.
     */
    public void markPlacedWall(GridPos pos, net.minecraft.item.Item item, int turns) {
        if (pos == null || !isInBounds(pos)) return;
        placedWalls.put(pos, new PlacedWall(item, turns, turns));
    }

    /** Update the remaining-turns counter for a placed wall (no-op if absent). */
    public void setPlacedWallTurns(GridPos pos, int turns) {
        PlacedWall pw = placedWalls.get(pos);
        if (pw == null) return;
        placedWalls.put(pos, pw.withTurns(turns));
    }

    /** Clear all VFX obstacles (called on combat exit). */
    public void clearAllVfxObstacles(net.minecraft.server.world.ServerWorld world) {
        for (java.util.Map.Entry<GridPos, TileType> e :
                new java.util.ArrayList<>(vfxObstaclePriorType.entrySet())) {
            clearVfxObstacle(world, e.getKey());
        }
    }

    // --- Player position ---

    public GridPos getPlayerGridPos() { return playerGridPos; }

    public void setPlayerGridPos(GridPos pos) { this.playerGridPos = pos; }

    // All alive player grid positions (populated before enemy AI decisions for multiplayer)
    private java.util.List<GridPos> allPlayerGridPositions = new java.util.ArrayList<>();
    public java.util.List<GridPos> getAllPlayerGridPositions() { return allPlayerGridPositions; }
    public void setAllPlayerGridPositions(java.util.List<GridPos> positions) { this.allPlayerGridPositions = positions; }

    // Player held item context for AI decisions (e.g., cat + fish)
    private String playerHeldItemId = "";
    public String getPlayerHeldItemId() { return playerHeldItemId; }
    public void setPlayerHeldItemId(String id) { this.playerHeldItemId = id; }

    public BlockPos getPlayerStartBlockPos() {
        return playerStart.toBlockPos(origin, 1);
    }

    public BlockPos gridToBlockPos(GridPos pos) {
        return pos.toBlockPos(origin, 1);
    }

    /** Get entity Y position for a tile - lowered by 1 for water and low ground tiles. */
    public double getEntityY(GridPos pos) {
        return getEntityY(pos, false);
    }

    /**
     * Entity Y position aware of flight. Flyers ignore water/low-ground/snow
     * dips and float at obstacle-top + 1 instead of clipping into obstacles.
     */
    public double getEntityY(GridPos pos, boolean flying) {
        GridTile tile = getTile(pos);
        double baseY = origin.getY() + 1;
        if (tile != null) {
            TileType t = tile.getType();
            if (flying) {
                if (t == TileType.OBSTACLE || t == TileType.ELEVATED) return baseY + 1;
                if (t == TileType.STAIR) return baseY + 0.5;
                return baseY;
            }
            if (t == TileType.WATER || t == TileType.DEEP_WATER || t == TileType.LOW_GROUND
                    || t == TileType.POWDER_SNOW || t == TileType.LAVA) {
                // Lava sinks the entity by 1 the same way water does - the lava
                // block fills floor→floor+1, so an entity at baseY (floor+1)
                // would float on the surface instead of being immersed in it.
                // Knocked-back mobs in particular looked perched on top of the
                // lava with no contact, which doesn't sell the hazard.
                return baseY - 1;
            }
            // Stair = half-step landing (Y+0.5). Elevated = full upper-floor
            // landing (Y+1). The lerp in CombatManager.tickAnimation handles
            // the smooth ramp transition between floor → stair → elevated.
            if (t == TileType.STAIR) return baseY + 0.5;
            if (t == TileType.ELEVATED) return baseY + 1;
        }
        return baseY;
    }

    /** Far-away X base for the legacy / test arena origins, kept well clear of the
     *  personal-world region (the hub sits at X=10000, slot arenas at X≥11000). Without
     *  this offset the old {@code level * 1000} formula landed on the hub at level 10
     *  (X=10000) - and the arena build + its wipe would hollow out the hub island.
     *  Pushing the legacy fallback into deep negative X makes that collision impossible. */
    private static final int LEGACY_ARENA_BASE_X = -1_000_000;

    /** Get arena origin for singleplayer (Z=0 lane). Legacy - use world-slot variant. */
    public static BlockPos arenaOriginForLevel(int level) {
        return new BlockPos(LEGACY_ARENA_BASE_X - level * 1000, 100, 0);
    }

    /** Get arena origin for a specific player (unique Z lane based on UUID). Legacy - used by test range. */
    public static BlockPos arenaOriginForLevel(int level, java.util.UUID playerId) {
        int lane = Math.abs(playerId.hashCode() % 1000);
        return new BlockPos(LEGACY_ARENA_BASE_X - level * 1000, 100, lane * 1000);
    }

    /** Get arena origin within a player's world slot. Column=X (levels), Row=Z (players). */
    public static BlockPos arenaOriginForLevel(int level, int worldSlot) {
        return new BlockPos(10000 + 1000 + level * 300, 100, worldSlot * 1000);
    }
}
