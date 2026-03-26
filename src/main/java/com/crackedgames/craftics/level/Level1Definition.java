package com.crackedgames.craftics.level;

import com.crackedgames.craftics.combat.LootPool;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.List;

public class Level1Definition extends LevelDefinition {

    @Override
    public int getLevelNumber() { return 1; }

    @Override
    public String getName() { return "Stone Age"; }

    @Override
    public int getWidth() { return 8; }

    @Override
    public int getHeight() { return 8; }

    @Override
    public GridPos getPlayerStart() { return new GridPos(3, 0); }

    @Override
    public Block getFloorBlock() { return Blocks.GRASS_BLOCK; }

    @Override
    public GridTile[][] buildTiles() {
        int w = getWidth();
        int h = getHeight();
        GridTile[][] tiles = new GridTile[w][h];
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                Block block = (x + z) % 2 == 0 ? Blocks.GRASS_BLOCK : Blocks.MOSS_BLOCK;
                tiles[x][z] = new GridTile(TileType.NORMAL, block);
            }
        }
        return tiles;
    }

    @Override
    public EnemySpawn[] getEnemySpawns() {
        return new EnemySpawn[] {
            new EnemySpawn("minecraft:cow", new GridPos(3, 3), 4, 0, 0, 1),
        };
    }

    @Override
    public List<ItemStack> rollCompletionLoot() {
        return new LootPool()
            .add(Items.OAK_PLANKS, 10)
            .add(Items.OAK_LOG, 8)
            .add(Items.DIRT, 6)
            .add(Items.FLINT, 5)
            .add(Items.APPLE, 4)
            .add(Items.STICK, 6)
            .roll(1, 3, 2, 6);
    }
}
