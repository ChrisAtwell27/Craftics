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

    private final Map<GridPos, CombatEntity> occupants = new HashMap<>();
    private GridPos playerGridPos;
    private final Map<GridPos, Integer> webOverlays = new HashMap<>();

    /** Tracks tiles that were converted to OBSTACLE by VFX (mace slam debris landing).
     *  Value is the prior TileType so we can restore on cleanup. */
    private final java.util.Map<GridPos, TileType> vfxObstaclePriorType = new java.util.HashMap<>();

    public GridArena(int width, int height, GridTile[][] tiles, BlockPos origin,
                     int levelNumber, GridPos playerStart) {
        this.width = width;
        this.height = height;
        this.tiles = tiles;
        this.origin = origin;
        this.levelNumber = levelNumber;
        this.playerStart = playerStart;
        this.playerGridPos = playerStart;
    }

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
        return x >= 0 && x < width && z >= 0 && z < height;
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
        // Background bosses don't block movement — they're targetable but pass-through
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

    public boolean hasWebOverlay(GridPos pos) {
        return webOverlays.containsKey(pos);
    }

    public void setWebOverlay(GridPos pos, int turns) {
        webOverlays.put(pos, turns);
    }

    public void clearWebOverlay(GridPos pos) {
        webOverlays.remove(pos);
    }

    /** Tick all web overlays. Returns positions where webs expired this tick. */
    public java.util.List<GridPos> tickWebOverlays() {
        java.util.List<GridPos> expired = new java.util.ArrayList<>();
        var it = webOverlays.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
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

    /** Clear a single VFX obstacle — restores prior tile type and wipes the block in the world. */
    public void clearVfxObstacle(net.minecraft.server.world.ServerWorld world, GridPos pos) {
        TileType prior = vfxObstaclePriorType.remove(pos);
        if (prior == null) return;
        GridTile t = getTile(pos);
        if (t != null) t.setType(prior);
        // Remove the block from the world (gridToBlockPos gives the surface tile position)
        net.minecraft.util.math.BlockPos blockPos = gridToBlockPos(pos);
        world.setBlockState(blockPos, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
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

    /** Get entity Y position for a tile — lowered by 1 for water and low ground tiles. */
    public double getEntityY(GridPos pos) {
        GridTile tile = getTile(pos);
        double baseY = origin.getY() + 1;
        if (tile != null) {
            TileType t = tile.getType();
            if (t == TileType.WATER || t == TileType.DEEP_WATER || t == TileType.LOW_GROUND || t == TileType.POWDER_SNOW) {
                return baseY - 1;
            }
        }
        return baseY;
    }

    /** Get arena origin for singleplayer (Z=0 lane). Legacy — use world-slot variant. */
    public static BlockPos arenaOriginForLevel(int level) {
        return new BlockPos(level * 1000, 100, 0);
    }

    /** Get arena origin for a specific player (unique Z lane based on UUID). Legacy — used by test range. */
    public static BlockPos arenaOriginForLevel(int level, java.util.UUID playerId) {
        int lane = Math.abs(playerId.hashCode() % 1000);
        return new BlockPos(level * 1000, 100, lane * 1000);
    }

    /** Get arena origin within a player's world slot. Column=X (levels), Row=Z (players). */
    public static BlockPos arenaOriginForLevel(int level, int worldSlot) {
        return new BlockPos(10000 + 1000 + level * 300, 100, worldSlot * 1000);
    }
}
