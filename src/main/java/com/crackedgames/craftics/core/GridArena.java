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
        return occupants.containsKey(pos) || pos.equals(playerGridPos);
    }

    public boolean isEnemyOccupied(GridPos pos) {
        return occupants.containsKey(pos);
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

    // --- Player position ---

    public GridPos getPlayerGridPos() { return playerGridPos; }

    public void setPlayerGridPos(GridPos pos) { this.playerGridPos = pos; }

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

    /** Get arena origin for singleplayer (Z=0 lane). */
    public static BlockPos arenaOriginForLevel(int level) {
        return new BlockPos(level * 1000, 100, 0);
    }

    /** Get arena origin for a specific player (unique Z lane based on UUID). */
    public static BlockPos arenaOriginForLevel(int level, java.util.UUID playerId) {
        // Each player gets a unique Z-lane offset so arenas never overlap
        // Hash UUID to a lane number (0-999), multiply by 1000 for Z spacing
        int lane = Math.abs(playerId.hashCode() % 1000);
        return new BlockPos(level * 1000, 100, lane * 1000);
    }
}
