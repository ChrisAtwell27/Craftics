package com.crackedgames.craftics.level;

import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Procedurally generates level definitions from biome templates.
 * Uses seeded random for reproducible layouts per level number.
 */
public class LevelGenerator {

    public static LevelDefinition generate(int levelNumber) {
        return generate(levelNumber, -1);
    }

    public static LevelDefinition generate(int levelNumber, int branchChoice) {
        BiomeTemplate biome = BiomeRegistry.getForLevel(levelNumber);
        int biomeIndex = biome.getBiomeLevelIndex(levelNumber); // 0-4 within biome
        boolean isBoss = biome.isBossLevel(levelNumber);
        // Use current time as part of seed so each visit generates different mobs/positions
        Random rand = new Random(System.nanoTime() ^ (levelNumber * 31L + biome.biomeId.hashCode()));

        // Grid size scales within biome — +4 extra to allow edge carving
        int width = biome.baseWidth + biomeIndex * biome.widthGrowth + 4;
        int height = biome.baseHeight + biomeIndex * biome.heightGrowth + 4;

        // Build display name
        String[] numerals = {"I", "II", "III", "IV", "V"};
        String name = biome.displayName + " " + numerals[biomeIndex];
        if (isBoss) name = biome.displayName + " - BOSS";

        // Player start: bottom center
        GridPos playerStart = new GridPos(width / 2, 0);

        // Build tile grid
        GridTile[][] tiles = generateTiles(biome, width, height, biomeIndex, rand);

        // Generate enemy spawns (pass tiles so spawns avoid carved terrain)
        LevelDefinition.EnemySpawn[] enemies = generateEnemies(biome, tiles, width, height, biomeIndex, levelNumber, isBoss, branchChoice, rand);

        // Roll loot
        int lootMinTypes = 1 + biomeIndex / 2;
        int lootMaxTypes = 2 + biomeIndex / 2;
        int lootMinTotal = 2 + biomeIndex;
        int lootMaxTotal = 4 + biomeIndex * 2;
        float lootMult = com.crackedgames.craftics.CrafticsMod.CONFIG.lootQuantityMultiplier();
        List<ItemStack> loot = biome.buildLootPool().roll(
            Math.min(lootMinTypes, 3), Math.min(lootMaxTypes, 3),
            (int)(Math.min(lootMinTotal, 8) * lootMult), (int)(Math.min(lootMaxTotal, 10) * lootMult)
        );

        return new GeneratedLevelDefinition(
            levelNumber, name, width, height, playerStart,
            biome.floorBlocks[0], tiles, enemies, loot,
            biome.nightLevel, biome
        );
    }

    private static GridTile[][] generateTiles(BiomeTemplate biome, int w, int h,
                                                int biomeIndex, Random rand) {
        GridTile[][] tiles = new GridTile[w][h];

        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                Block floor = biome.floorBlocks[(x + z) % biome.floorBlocks.length];
                tiles[x][z] = new GridTile(TileType.NORMAL, floor);
            }
        }

        // No carving here — the ArenaBuilder environment will bleed into the edges
        // and sync tiles to match, creating organic shapes from the actual surroundings

        return tiles;
    }

    /** Check if a tile is near player start or boss spawn and should be protected. */
    private static boolean isProtected(int x, int z, int w, int h) {
        if (z <= 1 && Math.abs(x - w / 2) <= 2) return true;
        if (z >= h - 2 && Math.abs(x - w / 2) <= 2) return true;
        return false;
    }


    private static LevelDefinition.EnemySpawn[] generateEnemies(BiomeTemplate biome, GridTile[][] tiles,
                                                                   int w, int h,
                                                                   int biomeIndex, int globalLevel,
                                                                   boolean isBoss, int branchChoice, Random rand) {
        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();

        // Enemy count: starts low, ramps gradually
        // Plains I = 1, Plains II = 1, Plains III = 2, Plains IV = 2, Plains Boss = 2 adds
        // Later biomes add more but cap at 6
        // Use the player's BiomePath position (not registry index) so difficulty matches actual progression
        java.util.List<String> fullPath = com.crackedgames.craftics.level.BiomePath
                .getFullPath(Math.max(0, branchChoice));
        int biomeOrdinal = fullPath.indexOf(biome.biomeId);
        if (biomeOrdinal < 0) {
            // Fallback for biomes not in the path (shouldn't happen normally)
            biomeOrdinal = BiomeRegistry.getAllBiomes().indexOf(biome);
        }
        if (biomeOrdinal < 0) biomeOrdinal = 0;
        int count = 1 + biomeIndex / 2 + Math.min(biomeOrdinal, 4);
        count = Math.min(count, com.crackedgames.craftics.CrafticsMod.CONFIG.maxEnemiesPerLevel());
        if (isBoss) count = Math.min(Math.max(1, count - 1), com.crackedgames.craftics.CrafticsMod.CONFIG.maxBossAdds());

        // Passive vs hostile ratio — early levels have some passive mobs
        float hostileRatio;
        if (biomeOrdinal == 0 && biomeIndex == 0) hostileRatio = 0.0f; // Plains level 1: always passive animals
        else if (biomeOrdinal == 0 && biomeIndex <= 1) hostileRatio = 0.4f;
        else if (biomeIndex <= 0) hostileRatio = 0.6f;
        else if (biomeIndex <= 1) hostileRatio = 0.75f;
        else hostileRatio = 1.0f;
        if (biomeOrdinal >= 9) hostileRatio = 1.0f;

        // Global difficulty scaling — configurable via craftics-config
        int hpBonus = biomeOrdinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        if (com.crackedgames.craftics.CrafticsMod.CONFIG.scaleHpPerLevel()) {
            hpBonus += biomeIndex * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerLevel();
        }
        int atkBonus = biomeOrdinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());
        // Per-level attack scaling within a biome (so later levels in the same biome hit harder)
        atkBonus += (biomeIndex + 1) / 2;
        int defBonus = biomeOrdinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.defPerBiome());

        // Collect valid spawn positions — only walkable tiles, 2+ tiles from player start
        GridPos playerStart = new GridPos(w / 2, 0);
        List<GridPos> validPositions = new ArrayList<>();
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                if (tiles[x][z].isWalkable() && new GridPos(x, z).manhattanDistance(playerStart) > 2) {
                    validPositions.add(new GridPos(x, z));
                }
            }
        }

        // Add boss FIRST on boss levels — random position from valid tiles
        if (isBoss && biome.boss != null && !validPositions.isEmpty()) {
            GridPos bossPos = validPositions.remove(rand.nextInt(validPositions.size()));
            // Reserve tiles around ALL boss-occupied squares (bosses are 2x2)
            int bossSize = 2;
            validPositions.removeIf(p -> {
                for (int dx = 0; dx < bossSize; dx++) {
                    for (int dz = 0; dz < bossSize; dz++) {
                        GridPos bossTile = new GridPos(bossPos.x() + dx, bossPos.z() + dz);
                        if (p.manhattanDistance(bossTile) < 2) return true;
                    }
                }
                return false;
            });

            int bossHp = (int)((biome.boss.baseHp() + hpBonus) * com.crackedgames.craftics.CrafticsMod.CONFIG.bossHpMultiplier());
            spawns.add(new LevelDefinition.EnemySpawn(
                biome.boss.entityTypeId(), bossPos,
                bossHp,
                biome.boss.baseAttack() + atkBonus,
                biome.boss.baseDefense() + defBonus,
                biome.boss.range()
            ));
        }

        // Spawn regular enemies
        for (int i = 0; i < count && !validPositions.isEmpty(); i++) {
            boolean hostile = rand.nextFloat() < hostileRatio;
            MobPoolEntry[] pool = hostile ? biome.hostileMobs : biome.passiveMobs;
            if (pool.length == 0) pool = biome.hostileMobs.length > 0 ? biome.hostileMobs : biome.passiveMobs;
            if (pool.length == 0) continue;

            MobPoolEntry mob = weightedRandom(pool, rand);
            GridPos pos = validPositions.remove(rand.nextInt(validPositions.size()));

            // Passive mobs keep their base stats — only hostile enemies scale with progression
            boolean isPassive = !hostile || isPassiveType(mob.entityTypeId());
            spawns.add(new LevelDefinition.EnemySpawn(
                mob.entityTypeId(), pos,
                mob.baseHp() + (isPassive ? 0 : hpBonus),
                mob.baseAttack() + (isPassive ? 0 : atkBonus),
                mob.baseDefense() + (isPassive ? 0 : defBonus),
                mob.range()
            ));
        }

        // Bee swarm rule: if any bee spawned, replace all other passives with bees (configurable)
        boolean hasBee = spawns.stream().anyMatch(s -> "minecraft:bee".equals(s.entityTypeId()));
        if (hasBee && com.crackedgames.craftics.CrafticsMod.CONFIG.beeSwarmReplacesPassives()) {
            // Find the bee entry in passive pool for stats reference
            MobPoolEntry beeEntry = null;
            for (MobPoolEntry m : biome.passiveMobs) {
                if ("minecraft:bee".equals(m.entityTypeId())) { beeEntry = m; break; }
            }
            if (beeEntry != null) {
                final MobPoolEntry bee = beeEntry;
                for (int i = 0; i < spawns.size(); i++) {
                    LevelDefinition.EnemySpawn s = spawns.get(i);
                    // Replace non-bee passives (check if it's a passive mob type)
                    if (!s.entityTypeId().equals("minecraft:bee") && isPassiveType(s.entityTypeId())) {
                        spawns.set(i, new LevelDefinition.EnemySpawn(
                            "minecraft:bee", s.position(),
                            bee.baseHp() + hpBonus + biomeIndex,
                            bee.baseAttack() + atkBonus,
                            bee.baseDefense() + defBonus,
                            bee.range()
                        ));
                    }
                }
            }
        }

        return spawns.toArray(new LevelDefinition.EnemySpawn[0]);
    }

    private static final java.util.Set<String> PASSIVE_TYPES = java.util.Set.of(
        "minecraft:cow", "minecraft:pig", "minecraft:sheep", "minecraft:chicken",
        "minecraft:rabbit", "minecraft:parrot", "minecraft:bat", "minecraft:goat",
        "minecraft:horse", "minecraft:donkey", "minecraft:mule", "minecraft:cat",
        "minecraft:fox", "minecraft:wolf", "minecraft:llama", "minecraft:panda",
        "minecraft:axolotl", "minecraft:cod", "minecraft:salmon", "minecraft:polar_bear"
    );

    private static boolean isPassiveType(String entityTypeId) {
        return PASSIVE_TYPES.contains(entityTypeId);
    }

    private static MobPoolEntry weightedRandom(MobPoolEntry[] pool, Random rand) {
        int totalWeight = 0;
        for (MobPoolEntry e : pool) totalWeight += e.weight();
        int r = rand.nextInt(totalWeight);
        int cumulative = 0;
        for (MobPoolEntry e : pool) {
            cumulative += e.weight();
            if (r < cumulative) return e;
        }
        return pool[0]; // fallback
    }
}
