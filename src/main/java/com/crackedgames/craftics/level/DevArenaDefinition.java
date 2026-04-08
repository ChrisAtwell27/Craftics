package com.crackedgames.craftics.level;

import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev/test arena using a plains biome schematic with every obstacle type
 * manually placed for testing knockback, hazards, and water mechanics.
 */
public class DevArenaDefinition extends LevelDefinition {

    private final BiomeTemplate biome;
    private final int width, height;

    public DevArenaDefinition() {
        this.biome = BiomeRegistry.getForLevel(1);
        this.width = biome != null ? biome.baseWidth + 8 : 16;
        this.height = biome != null ? biome.baseHeight + 8 : 16;
    }

    @Override public int getLevelNumber() { return 999; }
    @Override public String getName() { return "Dev Arena"; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
    @Override public GridPos getPlayerStart() { return new GridPos(width / 2, 1); }
    @Override public Block getFloorBlock() { return biome != null ? biome.floorBlocks[0] : Blocks.GRASS_BLOCK; }
    @Override public boolean isNightLevel() { return false; }
    @Override public String getArenaBiomeId() { return "plains"; }

    @Override
    public EnemySpawn[] getEnemySpawns() {
        List<EnemySpawn> spawns = new ArrayList<>();
        int midX = width / 2;
        int midZ = height / 2;
        spawns.add(new EnemySpawn("minecraft:husk", new GridPos(midX - 2, midZ + 3), 15, 3, 0, 1));
        return spawns.toArray(new EnemySpawn[0]);
    }

    /**
     * Place hazard blocks in the world using the ARENA's actual dimensions.
     * Called after ArenaBuilder.buildAt() resolves the schematic grid.
     * Also cleans up leftover entities from previous dev arena runs.
     */
    public void placeHazards(ServerWorld world, GridArena arena) {
        // Clean up leftover entities from previous dev arena at this location
        BlockPos origin = arena.getOrigin();
        net.minecraft.util.math.Box clearBox = new net.minecraft.util.math.Box(
            origin.getX() - 10, origin.getY() - 5, origin.getZ() - 10,
            origin.getX() + arena.getWidth() + 10, origin.getY() + 20, origin.getZ() + arena.getHeight() + 10);
        for (var entity : world.getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class, clearBox, e -> true)) {
            entity.discard();
        }

        int w = arena.getWidth();
        int h = arena.getHeight();
        int floorY = origin.getY();
        int sf = net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE;
        int midX = w / 2;
        int midZ = h / 2;

        // --- Fallen logs (center, 3 long sideways) ---
        placeObstacle(world, arena, midX - 1, midZ, Blocks.DARK_OAK_LOG, true);
        placeObstacle(world, arena, midX,     midZ, Blocks.DARK_OAK_LOG, true);
        placeObstacle(world, arena, midX + 1, midZ, Blocks.DARK_OAK_LOG, true);

        // --- Cobwebs (near center, offset) ---
        placeBlock(world, arena, midX - 1, midZ - 2, floorY + 1, Blocks.COBWEB);
        placeBlock(world, arena, midX + 1, midZ - 2, floorY + 1, Blocks.COBWEB);
        // Register as web overlays so the combat system detects them
        if (inBounds(midX - 1, midZ - 2, w, h)) arena.setWebOverlay(new GridPos(midX - 1, midZ - 2), 99);
        if (inBounds(midX + 1, midZ - 2, w, h)) arena.setWebOverlay(new GridPos(midX + 1, midZ - 2), 99);

        // --- Shallow water pool (left side, 2x2) ---
        placeWater(world, arena, 1, midZ - 1, false);
        placeWater(world, arena, 2, midZ - 1, false);
        placeWater(world, arena, 1, midZ,     false);
        placeWater(world, arena, 2, midZ,     false);

        // --- Deep water (below shallow, 2x1) ---
        placeWater(world, arena, 1, midZ + 1, true);
        placeWater(world, arena, 2, midZ + 1, true);

        // --- Lava pool (right side, 2x2) ---
        placeLava(world, arena, w - 2, midZ - 1);
        placeLava(world, arena, w - 3, midZ - 1);
        placeLava(world, arena, w - 2, midZ);
        placeLava(world, arena, w - 3, midZ);

        // --- Void pits (L-shape, top area — offset from lava) ---
        placePit(world, arena, midX + 1, 2);
        placePit(world, arena, midX + 2, 2);
        placePit(world, arena, midX + 2, 3);
        placePit(world, arena, midX + 2, 4);

        // --- Cacti (bottom area, spaced) ---
        placeCactus(world, arena, midX - 2, h - 3);
        placeCactus(world, arena, midX + 2, h - 3);

        // --- Powder snow patch (left side, below water) ---
        placePowderSnow(world, arena, 1, midZ + 3);
        placePowderSnow(world, arena, 2, midZ + 3);
        placePowderSnow(world, arena, 1, midZ + 4);
        placePowderSnow(world, arena, 2, midZ + 4);
    }

    // --- Helpers that place blocks AND update arena tile grid ---

    private void placeWater(ServerWorld world, GridArena arena, int x, int z, boolean deep) {
        if (!inBounds(x, z, arena.getWidth(), arena.getHeight())) return;
        BlockPos origin = arena.getOrigin();
        int sf = net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE;
        BlockPos pos = new BlockPos(origin.getX() + x, origin.getY(), origin.getZ() + z);
        world.setBlockState(pos, Blocks.WATER.getDefaultState(), sf);
        if (deep) {
            world.setBlockState(pos.down(), Blocks.WATER.getDefaultState(), sf);
            world.setBlockState(pos.down(2), Blocks.STONE.getDefaultState(), sf);
            setTile(arena, x, z, TileType.DEEP_WATER);
        } else {
            world.setBlockState(pos.down(), Blocks.STONE.getDefaultState(), sf);
            setTile(arena, x, z, TileType.WATER);
        }
    }

    private void placeLava(ServerWorld world, GridArena arena, int x, int z) {
        if (!inBounds(x, z, arena.getWidth(), arena.getHeight())) return;
        BlockPos origin = arena.getOrigin();
        int sf = net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE;
        BlockPos pos = new BlockPos(origin.getX() + x, origin.getY(), origin.getZ() + z);
        world.setBlockState(pos, Blocks.LAVA.getDefaultState(), sf);
        world.setBlockState(pos.down(), Blocks.STONE.getDefaultState(), sf);
        setTile(arena, x, z, TileType.LAVA);
    }

    private void placePit(ServerWorld world, GridArena arena, int x, int z) {
        if (!inBounds(x, z, arena.getWidth(), arena.getHeight())) return;
        BlockPos origin = arena.getOrigin();
        int sf = net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE;
        BlockPos pos = new BlockPos(origin.getX() + x, origin.getY(), origin.getZ() + z);
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), sf);
        world.setBlockState(pos.down(), Blocks.AIR.getDefaultState(), sf);
        world.setBlockState(pos.down(2), Blocks.BLACK_CONCRETE.getDefaultState(), sf);
        setTile(arena, x, z, TileType.VOID);
    }

    private void placePowderSnow(ServerWorld world, GridArena arena, int x, int z) {
        if (!inBounds(x, z, arena.getWidth(), arena.getHeight())) return;
        BlockPos origin = arena.getOrigin();
        int sf = net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE;
        BlockPos pos = new BlockPos(origin.getX() + x, origin.getY(), origin.getZ() + z);
        world.setBlockState(pos, Blocks.POWDER_SNOW.getDefaultState(), sf);
        setTile(arena, x, z, TileType.POWDER_SNOW);
    }

    private void placeCactus(ServerWorld world, GridArena arena, int x, int z) {
        if (!inBounds(x, z, arena.getWidth(), arena.getHeight())) return;
        BlockPos origin = arena.getOrigin();
        int sf = net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE;
        BlockPos floorPos = new BlockPos(origin.getX() + x, origin.getY(), origin.getZ() + z);
        BlockPos abovePos = floorPos.up();
        world.setBlockState(floorPos, Blocks.SAND.getDefaultState(), sf);
        world.setBlockState(abovePos, Blocks.CACTUS.getDefaultState(), sf);
        setTileWithBlock(arena, x, z, TileType.OBSTACLE, Blocks.CACTUS);
    }

    private void placeObstacle(ServerWorld world, GridArena arena, int x, int z, Block block, boolean sidewaysLog) {
        if (!inBounds(x, z, arena.getWidth(), arena.getHeight())) return;
        BlockPos origin = arena.getOrigin();
        int sf = net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE;
        BlockPos abovePos = new BlockPos(origin.getX() + x, origin.getY() + 1, origin.getZ() + z);
        if (sidewaysLog) {
            world.setBlockState(abovePos, block.getDefaultState()
                .with(Properties.AXIS, Direction.Axis.X), sf);
        } else {
            world.setBlockState(abovePos, block.getDefaultState(), sf);
        }
        setTile(arena, x, z, TileType.OBSTACLE);
    }

    private void placeBlock(ServerWorld world, GridArena arena, int x, int z, int y, Block block) {
        if (!inBounds(x, z, arena.getWidth(), arena.getHeight())) return;
        BlockPos origin = arena.getOrigin();
        int sf = net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE;
        world.setBlockState(new BlockPos(origin.getX() + x, y, origin.getZ() + z), block.getDefaultState(), sf);
    }

    private static void setTile(GridArena arena, int x, int z, TileType type) {
        GridTile tile = arena.getTile(x, z);
        if (tile != null) tile.setType(type);
    }

    private static void setTileWithBlock(GridArena arena, int x, int z, TileType type, Block block) {
        GridTile tile = arena.getTile(x, z);
        if (tile != null) {
            tile.setType(type);
            tile.setBlockType(block);
        }
    }

    private static boolean inBounds(int x, int z, int w, int h) {
        return x >= 0 && x < w && z >= 0 && z < h;
    }
}
