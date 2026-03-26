package com.crackedgames.craftics.level;

import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class Level2Definition extends LevelDefinition {

    @Override
    public int getLevelNumber() { return 2; }

    @Override
    public String getName() { return "Into the Depths"; }

    @Override
    public int getWidth() { return 10; }

    @Override
    public int getHeight() { return 10; }

    @Override
    public GridPos getPlayerStart() { return new GridPos(4, 0); }

    @Override
    public Block getFloorBlock() { return Blocks.STONE; }

    @Override
    public GridTile[][] buildTiles() {
        int w = getWidth();
        int h = getHeight();
        GridTile[][] tiles = new GridTile[w][h];

        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                // Checkerboard of stone and deepslate
                Block block = (x + z) % 2 == 0 ? Blocks.STONE : Blocks.DEEPSLATE;
                tiles[x][z] = new GridTile(TileType.NORMAL, block);
            }
        }

        return tiles;
    }

    @Override
    public EnemySpawn[] getEnemySpawns() {
        return new EnemySpawn[] {
            // 3 Zombies - melee, 6 HP, 2 atk
            new EnemySpawn("minecraft:zombie", new GridPos(2, 7), 6, 2, 0, 1),
            new EnemySpawn("minecraft:zombie", new GridPos(5, 9), 6, 2, 0, 1),
            new EnemySpawn("minecraft:zombie", new GridPos(8, 8), 6, 2, 0, 1),
            // 2 Skeletons - ranged, 4 HP, 2 atk, range 3
            new EnemySpawn("minecraft:skeleton", new GridPos(1, 5), 4, 2, 0, 3),
            new EnemySpawn("minecraft:skeleton", new GridPos(9, 4), 4, 2, 0, 3),
        };
    }

    @Override
    public boolean isNightLevel() { return true; }

    @Override
    public java.util.List<ItemStack> rollCompletionLoot() {
        return new com.crackedgames.craftics.combat.LootPool()
            .add(Items.COBBLESTONE, 10)
            .add(Items.IRON_INGOT, 8)
            .add(Items.COAL, 8)
            .add(Items.COPPER_INGOT, 6)
            .add(Items.GOLD_INGOT, 3)
            .add(Items.REDSTONE, 5)
            .add(Items.DIAMOND, 1)
            .roll(1, 3, 2, 6);
    }
}
