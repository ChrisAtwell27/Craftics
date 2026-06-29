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

    /** Tracks tiles that were converted to OBSTACLE by VFX (mace slam debris landing).
     *  Value is the prior TileType so we can restore on cleanup. */
    private final java.util.Map<GridPos, TileType> vfxObstaclePriorType = new java.util.HashMap<>();

    /**
     * Player-placed wall blocks. Tracks the item the player consumed, the
     * turns remaining before silent expiry, and the starting duration so the
     * client can show progressive breaking texture as the wall ticks down.
     * These tiles also live in {@link #vfxObstaclePriorType} for tile-type
     * restoration; the two maps are kept in sync.
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

    // --- Occupant tracking ---

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

    public void placeEntity(CombatEntity entity) {
        for (GridPos tile : getOccupiedTiles(entity.getGridPos(), entity.getSize())) {
            occupants.put(tile, entity);
        }
    }

    public void moveEntity(CombatEntity entity, GridPos newPos) {
        // Immovable entities (Creaking Heart and other virtual block enemies) must stay put:
        // their in-world block never moves, so relocating the grid entry would desync the
        // target and make them impossible to hit. Refuse the move.
        if (entity.isImmovable()) return;
        // Remove from all old tiles
        for (GridPos tile : getOccupiedTiles(entity.getGridPos(), entity.getSize())) {
            occupants.remove(tile);
        }
        entity.setGridPos(newPos);
        // Place in all new tiles
        for (GridPos tile : getOccupiedTiles(newPos, entity.getSize())) {
            occupants.put(tile, entity);
        }
    }

    public void removeEntity(CombatEntity entity) {
        for (GridPos tile : getOccupiedTiles(entity.getGridPos(), entity.getSize())) {
            occupants.remove(tile);
        }
    }

    /** Returns all grid positions occupied by an entity of the given size at the given origin. */
    public static java.util.List<GridPos> getOccupiedTiles(GridPos origin, int size) {
        java.util.List<GridPos> tiles = new java.util.ArrayList<>();
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                tiles.add(new GridPos(origin.x() + dx, origin.z() + dz));
            }
        }
        return tiles;
    }

    public Map<GridPos, CombatEntity> getOccupants() {
        return occupants;
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

    // --- VFX obstacle tracking (mace slam debris) ---

    /** Mark a tile as a VFX-placed obstacle. Remembers the prior tile type for cleanup. */
    public void markVfxObstacle(GridPos pos) {
        if (pos == null || !isInBounds(pos)) return;
        GridTile t = getTile(pos);
        if (t == null) return;
        // Don't overwrite existing obstacles or special tile types
        if (t.getType() != TileType.NORMAL) return;
        vfxObstaclePriorType.put(pos, t.getType());
        t.setType(TileType.OBSTACLE);
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
     * Register a tile as a temporary player-placed wall. The caller is
     * responsible for actually setting the block in the world and marking the
     * tile type via {@link #markVfxObstacle}. {@code item} is the item that
     * was consumed, so mining can refund it.
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
