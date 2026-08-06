package com.crackedgames.craftics.raid;

import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import com.crackedgames.craftics.level.LevelDefinition;
import com.crackedgames.craftics.world.CrafticsSavedData;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * The synthetic level a raid instance fights on: one boss, no adds, a grid large
 * enough for eight players, placed at the instance's own origin inside its own
 * dimension.
 *
 * <p>The procedural grid here is only a fallback. In practice the "raidboss"
 * schem set replaces it wholesale through ArenaBuilder's normal disk-then-jar
 * lookup, exactly as biome arenas do.
 */
public class RaidBossLevelDefinition extends LevelDefinition {

    /** Well clear of the trial/ambush synthetic ids, and never fed to the level*300 formula. */
    public static final int RAID_LEVEL_NUMBER = LevelDefinition.SYNTHETIC_LEVEL_BASE + 400;

    /**
     * Fallback grid when no schematic is found. Also the size of the tile array that
     * buildTiles() paints the checkerboard onto: ArenaBuilder derives the real playable
     * grid from the schematic's own corner markers, and any grid coordinate outside this
     * array falls back to a single solid block with no checker. 24 gives comfortable
     * headroom over the real raid arena's footprint so the schematic's floor never
     * outgrows the checkerboard and shows a solid outer band instead.
     */
    private static final int FALLBACK_GRID = 24;

    private final RaidBossDefinition boss;
    private final int variantIndex;
    private final BlockPos origin;

    public RaidBossLevelDefinition(RaidBossDefinition boss, int variantIndex, BlockPos origin) {
        this.boss = boss;
        this.variantIndex = variantIndex;
        this.origin = origin;
    }

    public RaidBossDefinition boss() { return boss; }

    @Override public int getLevelNumber() { return RAID_LEVEL_NUMBER; }
    @Override public String getName() { return boss.name(); }
    @Override public int getWidth() { return FALLBACK_GRID; }
    @Override public int getHeight() { return FALLBACK_GRID; }
    @Override public GridPos getPlayerStart() { return new GridPos(FALLBACK_GRID / 2, FALLBACK_GRID - 2); }
    @Override public Block getFloorBlock() { return Blocks.WHITE_CONCRETE; }

    @Override
    public GridTile[][] buildTiles() {
        int w = getWidth();
        int h = getHeight();
        GridTile[][] tiles = new GridTile[w][h];
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                Block block = (x + z) % 2 == 0 ? Blocks.WHITE_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE;
                tiles[x][z] = new GridTile(TileType.NORMAL, block);
            }
        }
        return tiles;
    }

    @Override public String getArenaBiomeId() { return RaidBossDefinition.ARENA_BIOME_ID; }
    @Override public int getArenaVariantIndex() {
        return boss.arenaVariant() >= 0 ? boss.arenaVariant() : Math.max(0, variantIndex);
    }
    @Override public String getArenaEnvironmentId() { return boss.environmentId(); }

    @Override public int getBossSpawnIndex() { return 0; }
    @Override public String getBossAiBiomeId() { return boss.aiKey(); }

    @Override public boolean hasOverrideOrigin() { return true; }
    @Override public BlockPos getOverrideOrigin(UUID worldOwner, CrafticsSavedData data) { return origin; }

    @Override
    public EnemySpawn[] getEnemySpawns() {
        // Centre of the grid, far from the player start so the fight opens at range.
        GridPos bossPos = new GridPos(FALLBACK_GRID / 2, 2);
        return new EnemySpawn[]{
            new EnemySpawn(boss.entityTypeId(), bossPos,
                boss.hp(), boss.attack(), boss.defense(), boss.range(),
                boss.aiKey(), boss.speed())
        };
    }
}
