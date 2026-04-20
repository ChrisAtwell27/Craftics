package com.crackedgames.craftics.level;

import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class LevelGenerator {

    /**
     * Whitelist of add types each boss level is allowed to spawn at the start
     * of the fight. Each boss should only bring themed backup — e.g. the
     * Molten King spawns with basic nether grunts (zombified piglins), not the
     * full biome hostile pool with blazes + ghasts + magma cubes.
     *
     * If a biome isn't listed the normal biome pool is used.
     */
    private static final Map<String, Set<String>> BOSS_ADD_TYPES = Map.ofEntries(
        Map.entry("river",             Set.of("minecraft:drowned", "minecraft:zombie")),
        Map.entry("desert",            Set.of("minecraft:husk")),
        Map.entry("jungle",            Set.of("minecraft:spider", "minecraft:cave_spider")),
        Map.entry("cave",              Set.of("minecraft:zombie", "minecraft:skeleton")),
        Map.entry("deep_dark",         Set.of("minecraft:silverfish")),
        Map.entry("nether_wastes",     Set.of("minecraft:zombified_piglin", "minecraft:piglin")),
        Map.entry("soul_sand_valley",  Set.of("minecraft:wither_skeleton", "minecraft:skeleton")),
        Map.entry("crimson_forest",    Set.of("minecraft:piglin", "minecraft:piglin_brute", "minecraft:zombified_piglin")),
        Map.entry("warped_forest",     Set.of("minecraft:enderman", "minecraft:endermite")),
        Map.entry("basalt_deltas",     Set.of("minecraft:wither_skeleton", "minecraft:blaze")),
        Map.entry("end_city",          Set.of("minecraft:shulker")),
        Map.entry("chorus_grove",      Set.of("minecraft:enderman", "minecraft:shulker")),
        Map.entry("outer_end_islands", Set.of("minecraft:enderman", "minecraft:phantom")),
        Map.entry("dragons_nest",      Set.of("minecraft:enderman", "minecraft:phantom"))
    );

    public static LevelDefinition generate(int levelNumber) {
        return generate(levelNumber, -1);
    }

    public static LevelDefinition generate(int levelNumber, int branchChoice) {
        // Default to the global config for backwards compat callers that don't know
        // the island owner (e.g. anonymous test/arena pre-gen without player context).
        return generate(levelNumber, branchChoice,
            com.crackedgames.craftics.CrafticsMod.CONFIG.scaleHpPerLevel());
    }

    /**
     * Full signature — {@code scaleHpPerLevel} comes from the island owner's
     * {@code PlayerData.scaleHpPerLevelEnabled} so each island can independently
     * disable the per-level HP ramp within a biome.
     */
    public static LevelDefinition generate(int levelNumber, int branchChoice, boolean scaleHpPerLevel) {
        BiomeTemplate biome = BiomeRegistry.getForLevel(levelNumber);
        if (biome == null) {
            throw new IllegalStateException("No biome registered for level " + levelNumber
                + " — is the BiomeRegistry empty?");
        }
        int biomeIndex = biome.getBiomeLevelIndex(levelNumber); // 0-based within biome
        boolean isBoss = biome.isBossLevel(levelNumber);
        // Seed with nanoTime so each visit gets different mobs/positions
        Random rand = new Random(System.nanoTime() ^ (levelNumber * 31L + biome.biomeId.hashCode()));

        // +4 extra for ArenaBuilder's edge carving
        int width = biome.baseWidth + biomeIndex * biome.widthGrowth + 4;
        int height = biome.baseHeight + biomeIndex * biome.heightGrowth + 4;

        String name;
        if (isBoss) {
            name = biome.displayName + " - BOSS";
        } else if (biomeIndex < 5) {
            String[] numerals = {"I", "II", "III", "IV", "V"};
            name = biome.displayName + " " + numerals[biomeIndex];
        } else {
            name = biome.displayName + " " + (biomeIndex + 1);
        }

        GridPos playerStart = new GridPos(width / 2, 0);
        GridTile[][] tiles = generateTiles(biome, width, height, biomeIndex, rand);
        LevelDefinition.EnemySpawn[] enemies = generateEnemies(
            biome, tiles, width, height, biomeIndex, levelNumber, isBoss, branchChoice, scaleHpPerLevel, rand);
        int lootMinTypes = 1 + biomeIndex / 2;
        int lootMaxTypes = 2 + biomeIndex / 2;
        int lootMinTotal = 2 + biomeIndex;
        int lootMaxTotal = 4 + biomeIndex * 2;
        float lootMult = com.crackedgames.craftics.CrafticsMod.CONFIG.lootQuantityMultiplier();
        List<ItemStack> loot = biome.buildLootPool().roll(
            Math.min(lootMinTypes, 3), Math.min(lootMaxTypes, 3),
            (int)(Math.min(lootMinTotal, 8) * lootMult), (int)(Math.min(lootMaxTotal, 10) * lootMult)
        );

        GeneratedLevelDefinition levelDef = new GeneratedLevelDefinition(
            levelNumber, name, width, height, playerStart,
            biome.floorBlocks[0], tiles, enemies, loot,
            biome.nightLevel, biome
        );

        // Pale Garden sub-biome: at the forest midpoint, spawn creakings instead.
        // Available natively on 1.21.4+ and via the Pale Garden Backport mod on older shards.
        if ("forest".equals(biome.biomeId) && !isBoss && biomeIndex == biome.levelCount / 2
            && com.crackedgames.craftics.compat.palegardenbackport.PaleGardenBackportCompat.shouldSpawnPaleGarden()) {
            levelDef = buildPaleGardenLevel(levelNumber, biome, biomeIndex, width, height, loot, rand, scaleHpPerLevel, branchChoice);
        }

        return levelDef;
    }

    private static GeneratedLevelDefinition buildPaleGardenLevel(
            int levelNumber, BiomeTemplate biome, int biomeIndex,
            int width, int height, List<ItemStack> loot,
            Random rand, boolean scaleHpPerLevel, int branchChoice) {

        GridPos playerStart = new GridPos(width / 2, 0);
        GridTile[][] tiles = generateTiles(biome, width, height, biomeIndex, rand);

        // Compute scaling bonuses (mirrors generateEnemies logic)
        java.util.List<String> fullPath = BiomePath.getFullPath(branchChoice);
        int biomeOrdinal = Math.max(0, fullPath.indexOf(biome.biomeId));
        int hpBonus = biomeOrdinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        int atkBonus = biomeOrdinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());
        if (scaleHpPerLevel) hpBonus += biomeIndex * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerLevel();

        // Spawn 2 creakings, each paired with a creaking heart
        int creakingCount = 2;
        java.util.List<LevelDefinition.EnemySpawn> spawns = new java.util.ArrayList<>();
        java.util.List<GridPos> used = new java.util.ArrayList<>();
        used.add(playerStart);

        String creakingId = com.crackedgames.craftics.compat.palegardenbackport
            .PaleGardenBackportCompat.creakingEntityId();

        for (int i = 0; i < creakingCount; i++) {
            // Creaking mob (vanilla on 1.21.4+, palegardenbackport on older)
            GridPos creakingPos = findOpenSpawn(tiles, width, height, used, rand);
            if (creakingPos == null) continue;
            used.add(creakingPos);
            spawns.add(new LevelDefinition.EnemySpawn(creakingId, creakingPos,
                18 + hpBonus, 5 + atkBonus, 2, 1));

            // Creaking Heart — placed nearby behind the creaking (away from player)
            GridPos heartPos = findOpenSpawn(tiles, width, height, used, rand);
            if (heartPos == null) continue;
            used.add(heartPos);
            spawns.add(new LevelDefinition.EnemySpawn("craftics:creaking_heart", heartPos,
                10 + hpBonus / 2, 0, 0, 0));
        }

        // Also add 1-2 normal forest hostiles for variety
        int extraCount = 1 + (rand.nextFloat() < 0.5f ? 1 : 0);
        for (int i = 0; i < extraCount; i++) {
            if (biome.hostileMobs.length == 0) break;
            MobPoolEntry mob = biome.hostileMobs[rand.nextInt(biome.hostileMobs.length)];
            GridPos pos = findOpenSpawn(tiles, width, height, used, rand);
            if (pos == null) continue;
            used.add(pos);
            spawns.add(new LevelDefinition.EnemySpawn(mob.entityTypeId(), pos,
                mob.baseHp() + hpBonus, mob.baseAttack() + atkBonus, mob.baseDefense(), mob.range()));
        }

        LevelDefinition.EnemySpawn[] enemies = spawns.toArray(new LevelDefinition.EnemySpawn[0]);
        String name = "Pale Garden";

        GeneratedLevelDefinition def = new GeneratedLevelDefinition(
            levelNumber, name, width, height, playerStart,
            com.crackedgames.craftics.compat.palegardenbackport.PaleGardenBackportCompat.paleMossBlock(),
            tiles, enemies, loot,
            true, biome // night = true for creepy atmosphere
        );
        def.setArenaBiomeOverride("forest/pale_garden");
        return def;
    }

    private static GridPos findOpenSpawn(GridTile[][] tiles, int w, int h,
                                          java.util.List<GridPos> used, Random rand) {
        for (int attempts = 0; attempts < 40; attempts++) {
            int x = 1 + rand.nextInt(w - 2);
            int z = 2 + rand.nextInt(h - 3);
            GridPos pos = new GridPos(x, z);
            boolean tooClose = false;
            for (GridPos u : used) {
                if (Math.abs(u.x() - x) + Math.abs(u.z() - z) < 2) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose && tiles[x][z].isSafeForSpawn()) return pos;
        }
        return null;
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

        return tiles;
    }

    private static boolean isProtected(int x, int z, int w, int h) {
        if (z <= 1 && Math.abs(x - w / 2) <= 2) return true;
        if (z >= h - 2 && Math.abs(x - w / 2) <= 2) return true;
        return false;
    }


    private static LevelDefinition.EnemySpawn[] generateEnemies(BiomeTemplate biome, GridTile[][] tiles,
                                                                   int w, int h,
                                                                   int biomeIndex, int globalLevel,
                                                                   boolean isBoss, int branchChoice,
                                                                   boolean scaleHpPerLevel, Random rand) {
        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();

        // Use BiomePath position for difficulty scaling (not registry index)
        java.util.List<String> fullPath = com.crackedgames.craftics.level.BiomePath
                .getFullPath(Math.max(0, branchChoice));
        int biomeOrdinal = fullPath.indexOf(biome.biomeId);
        if (biomeOrdinal < 0) {
            biomeOrdinal = BiomeRegistry.getAllBiomes().indexOf(biome);
        }
        if (biomeOrdinal < 0) biomeOrdinal = 0;
        // Enemy count scales by biome: plains caps ~6, later biomes scale toward config max (7).
        // Per-biome cap rises from 6 (ordinal 0) by +1 per biome ordinal.
        int biomeCap = Math.min(6 + biomeOrdinal, com.crackedgames.craftics.CrafticsMod.CONFIG.maxEnemiesPerLevel());
        // Arena-size cap: keep encounters breathable by budgeting one enemy per
        // TILES_PER_ENEMY tiles of floor. A 9×9 arena gets ~4 max, a 15×15 gets ~12
        // (then clamped by config). Floor of 2 so tiny arenas still have a fight.
        final int TILES_PER_ENEMY = 18;
        int arenaCap = Math.max(2, (w * h) / TILES_PER_ENEMY);
        int hardCap = Math.min(biomeCap, arenaCap);
        int count = 2 + biomeIndex / 2 + Math.min(biomeOrdinal, 6);
        count = Math.min(count, hardCap);
        // Boss rounds: keep the add crew small and thematic. The boss itself is
        // the main threat; extra random biome trash dilutes the fight.
        if (isBoss) count = Math.min(3, com.crackedgames.craftics.CrafticsMod.CONFIG.maxBossAdds());

        float hostileRatio;
        if (biomeOrdinal == 0 && biomeIndex == 0) hostileRatio = 0.0f; // Plains I is always passive
        else if (biomeOrdinal == 0 && biomeIndex <= 1) hostileRatio = 0.4f;
        else if (biomeIndex <= 0) hostileRatio = 0.6f;
        else if (biomeIndex <= 1) hostileRatio = 0.75f;
        else hostileRatio = 1.0f;
        if (biomeOrdinal >= 9) hostileRatio = 1.0f;

        int hpBonus = biomeOrdinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        // Per-level ramp is controlled by the island owner (or the global config
        // when the caller doesn't know the owner). Island owners can flip their
        // setting with /craftics hp_per_level <on|off>.
        if (scaleHpPerLevel) {
            hpBonus += biomeIndex * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerLevel();
        }
        int atkBonus = biomeOrdinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());
        atkBonus += (biomeIndex + 1) / 2;
        int defBonus = biomeOrdinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.defPerBiome());

        GridPos playerStart = new GridPos(w / 2, 0);
        List<GridPos> validPositions = new ArrayList<>();
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                if (tiles[x][z].isSafeForSpawn() && new GridPos(x, z).manhattanDistance(playerStart) > 2) {
                    validPositions.add(new GridPos(x, z));
                }
            }
        }

        if (isBoss && biome.boss != null && !validPositions.isEmpty()) {
            GridPos bossPos = validPositions.remove(rand.nextInt(validPositions.size()));
            // Bosses are 2x2 — reserve tiles around their footprint
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

        // On boss rounds, restrict the hostile pool to the boss's themed backup
        // (e.g. Molten King brings zombified piglins, not the full nether pool).
        MobPoolEntry[] bossHostilePool = biome.hostileMobs;
        if (isBoss) {
            Set<String> allowed = BOSS_ADD_TYPES.get(biome.biomeId);
            if (allowed != null) {
                MobPoolEntry[] filtered = Arrays.stream(biome.hostileMobs)
                    .filter(m -> allowed.contains(m.entityTypeId()))
                    .toArray(MobPoolEntry[]::new);
                if (filtered.length > 0) bossHostilePool = filtered;
            }
        }

        for (int i = 0; i < count && !validPositions.isEmpty(); i++) {
            // Boss rounds spawn only themed hostile backup — no passives.
            boolean hostile = isBoss ? true : (rand.nextFloat() < hostileRatio);
            MobPoolEntry[] pool = hostile
                ? (isBoss ? bossHostilePool : biome.hostileMobs)
                : biome.passiveMobs;
            if (pool.length == 0) pool = biome.hostileMobs.length > 0 ? biome.hostileMobs : biome.passiveMobs;
            if (pool.length == 0) continue;

            MobPoolEntry mob = weightedRandom(pool, rand);
            GridPos pos = validPositions.remove(rand.nextInt(validPositions.size()));

            // Passives keep base stats; only hostiles scale with progression
            boolean isPassive = !hostile || isPassiveType(mob.entityTypeId());
            spawns.add(new LevelDefinition.EnemySpawn(
                mob.entityTypeId(), pos,
                mob.baseHp() + (isPassive ? 0 : hpBonus),
                mob.baseAttack() + (isPassive ? 0 : atkBonus),
                mob.baseDefense() + (isPassive ? 0 : defBonus),
                mob.range()
            ));
        }

        // Bee swarm: if a bee rolled, replace all passives with bees
        boolean hasBee = spawns.stream().anyMatch(s -> "minecraft:bee".equals(s.entityTypeId()));
        if (hasBee && com.crackedgames.craftics.CrafticsMod.CONFIG.beeSwarmReplacesPassives()) {
            MobPoolEntry beeEntry = null;
            for (MobPoolEntry m : biome.passiveMobs) {
                if ("minecraft:bee".equals(m.entityTypeId())) { beeEntry = m; break; }
            }
            if (beeEntry != null) {
                final MobPoolEntry bee = beeEntry;
                for (int i = 0; i < spawns.size(); i++) {
                    LevelDefinition.EnemySpawn s = spawns.get(i);
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
        return pool[0];
    }
}
