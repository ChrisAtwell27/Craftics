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
     * of the fight. Each boss should only bring themed backup - e.g. the
     * Molten King spawns with basic nether grunts (zombified piglins), not the
     * full biome hostile pool with blazes + ghasts + magma cubes.
     *
     * If a biome isn't listed the normal biome pool is used.
     */
    /**
     * Clamp a scaled boss attack value to {@code cap}. Boss attack scales
     * additively with biome progress and skips the per-biome enemy damage cap,
     * so without this clamp a late-campaign boss can reach an attack that near
     * one-shots a 20 HP player. Pure int math (no MC types or config lookup) so
     * it can be unit-tested; a boss already at or below the ceiling is returned
     * unchanged.
     */
    public static int clampBossAttack(int scaledAttack, int cap) {
        return Math.min(scaledAttack, cap);
    }

    /**
     * Clamp a scaled boss attack to the configured ceiling
     * ({@code maxBossAttack}). Call this at every boss spawn path so a new one
     * cannot slip past the ceiling.
     */
    public static int clampBossAttack(int scaledAttack) {
        return clampBossAttack(scaledAttack, com.crackedgames.craftics.CrafticsMod.CONFIG.maxBossAttack());
    }

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

    /**
     * Arena id for the Pale Garden sub-biome. Names a single bundled FILE
     * (arenas/forest/pale_garden.schem) rather than a directory of numbered variants.
     */
    public static final String PALE_GARDEN_ARENA_ID = "forest/pale_garden";

    public static LevelDefinition generate(int levelNumber) {
        return generate(levelNumber, -1);
    }

    public static LevelDefinition generate(int levelNumber, int branchChoice) {
        // Default to the global config for backwards compat callers that don't know
        // the island owner (e.g. anonymous test/arena pre-gen without player context).
        return generate(levelNumber, branchChoice,
            com.crackedgames.craftics.CrafticsMod.CONFIG.scaleHpPerLevel());
    }

    public static LevelDefinition generate(int levelNumber, int branchChoice, boolean scaleHpPerLevel) {
        // Default bossBeaten=false for callers without player context (the enemy count
        // then follows the normal per-biome ramp rather than the post-boss max).
        return generate(levelNumber, branchChoice, scaleHpPerLevel, false);
    }

    /**
     * Full signature - {@code scaleHpPerLevel} comes from the island owner's
     * {@code PlayerData.scaleHpPerLevelEnabled} so each island can independently
     * disable the per-level HP ramp within a biome. {@code bossBeaten} is whether the
     * island owner has already defeated this biome's boss; once true, every level in the
     * biome spawns the biome's peak enemy count instead of the ramped count.
     */
    public static LevelDefinition generate(int levelNumber, int branchChoice, boolean scaleHpPerLevel,
                                           boolean bossBeaten) {
        return generate(levelNumber, branchChoice, scaleHpPerLevel, bossBeaten, null);
    }

    /**
     * Infinite-mode variant. A non-null {@code infiniteSpec} replaces the
     * campaign-ordinal difficulty input with the run's virtual ordinal (cleared
     * biome count, unbounded) and, on boss levels, swaps the biome's authored
     * boss for the run's randomized standard-size boss. The spec is stamped on
     * the returned definition for CombatManager's spawn/victory hooks.
     */
    public static LevelDefinition generate(int levelNumber, int branchChoice, boolean scaleHpPerLevel,
                                           boolean bossBeaten, InfiniteSpec infiniteSpec) {
        BiomeTemplate biome = BiomeRegistry.getForLevel(levelNumber);
        if (biome == null) {
            throw new IllegalStateException("No biome registered for level " + levelNumber
                + " - is the BiomeRegistry empty?");
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
        if (infiniteSpec != null) {
            name = "∞ " + name; // ∞ prefix marks infinite-run levels
        }

        GridPos playerStart = new GridPos(width / 2, 0);
        GridTile[][] tiles = generateTiles(biome, width, height, biomeIndex, rand);
        LevelDefinition.EnemySpawn[] enemies = generateEnemies(
            biome, tiles, width, height, biomeIndex, levelNumber, isBoss, branchChoice, scaleHpPerLevel,
            bossBeaten, rand, infiniteSpec);
        // Completion loot scales with how many enemies this level actually has, so an
        // early (few-enemy) level pays less than a late (full) one. Boss levels keep
        // their own loot footprint and are not scaled down by their small add-crew.
        float lootMult = com.crackedgames.craftics.CrafticsMod.CONFIG.lootQuantityMultiplier();
        double enemyRewardMult = isBoss ? 1.0 : BiomeDifficulty.rewardMultiplier(enemies.length);
        int lootMinTypes = 1 + biomeIndex / 2;
        int lootMaxTypes = 2 + biomeIndex / 2;
        int lootMinTotal = (int) Math.round((2 + biomeIndex) * enemyRewardMult);
        int lootMaxTotal = (int) Math.round((4 + biomeIndex * 2) * enemyRewardMult);
        List<ItemStack> loot = biome.buildLootPool().roll(
            Math.min(lootMinTypes, 3), Math.min(lootMaxTypes, 3),
            (int)(Math.min(lootMinTotal, 8) * lootMult), (int)(Math.min(lootMaxTotal, 10) * lootMult)
        );

        GeneratedLevelDefinition levelDef = new GeneratedLevelDefinition(
            levelNumber, name, width, height, playerStart,
            biome.floorBlocks[0], tiles, enemies, loot,
            biome.nightLevel, biome
        );

        // Level-4 miniboss gate: a >=7-level biome with a registered MinibossMechanic replaces
        // its index-3 level with the mechanic's arena + spawns. Forest's Creaking encounter
        // (formerly the special-cased isPaleGardenLevel/buildPaleGardenLevel path) now flows
        // through here via ForestCreakingMechanic, registered only when a creaking entity is
        // actually available (see CrafticsMod#onInitialize) - so a shard/setup without one
        // simply has no "forest" entry in MinibossRegistry and falls through to a normal level.
        if (isMinibossLevel(biome, biomeIndex, isBoss)) {
            levelDef = buildMinibossLevel(levelNumber, biome, biomeIndex, width, height,
                tiles, loot, rand, infiniteSpec);
        }

        levelDef.setInfiniteSpec(infiniteSpec);
        return levelDef;
    }

    /** Level-4 miniboss gate: index 3 of a >=7-level biome that has a registered mechanic. */
    public static boolean isMinibossLevel(BiomeTemplate biome, int biomeIndex, boolean isBoss) {
        return !isBoss && biomeIndex == 3 && biome.levelCount >= 7
            && com.crackedgames.craftics.combat.miniboss.MinibossRegistry.has(biome.biomeId);
    }

    /**
     * Builds the level-4 miniboss level for a biome with a registered {@link
     * com.crackedgames.craftics.combat.miniboss.MinibossMechanic}. The mechanic supplies its own
     * spawns via {@code initialSpawns}; this method wires up the ordinal (infinite-mode aware),
     * wraps the result in a {@link GeneratedLevelDefinition}, and - for biomes whose miniboss
     * needs a dedicated arena - applies the arena override.
     *
     * <p>Forest's Creaking encounter (formerly {@code buildPaleGardenLevel}) needs the Pale
     * Garden arena rather than a normal forest arena; that override must also be reflected in
     * {@link #arenaBiomeOverrideFor} so the arena cache and the level this method builds agree.
     *
     * <p>INFINITE MODE: the ordinal is the run depth ({@code infiniteSpec.virtualOrdinal()}),
     * never the rolled biome's campaign position. A deep infinite run rolling e.g. plains must
     * build this level at the run's difficulty, not at plains' fixed campaign-ordinal difficulty.
     */
    private static GeneratedLevelDefinition buildMinibossLevel(
            int levelNumber, BiomeTemplate biome, int biomeIndex,
            int width, int height, GridTile[][] tiles, List<ItemStack> loot,
            Random rand, InfiniteSpec infiniteSpec) {
        var mechanic = com.crackedgames.craftics.combat.miniboss.MinibossRegistry.get(biome.biomeId);
        int ordinal = infiniteSpec != null
            ? Math.max(0, infiniteSpec.virtualOrdinal())
            : Math.max(0, com.crackedgames.craftics.level.campaign.CampaignManager
                .ordinalOf(biome.biomeId, 0));
        var spawnList = mechanic.initialSpawns(width, height, ordinal, rand);
        LevelDefinition.EnemySpawn[] enemies = spawnList.toArray(new LevelDefinition.EnemySpawn[0]);
        GridPos playerStart = new GridPos(width / 2, 0);
        boolean isForest = "forest".equals(biome.biomeId);
        // Forest keeps the old buildPaleGardenLevel's forced night (creepy Pale Garden
        // atmosphere) even though the forest biome template itself is a day biome.
        boolean nightLevel = isForest || biome.nightLevel;
        // The level's display name is the mechanic's own title (e.g. "§2§l☠ Graveyard",
        // "§b§l☠ Blizzard") - these are special levels named for their event, NOT "minibosses".
        GeneratedLevelDefinition def = new GeneratedLevelDefinition(
            levelNumber, mechanic.introTitle(), width, height, playerStart,
            biome.floorBlocks[0], tiles, enemies, loot, nightLevel, biome);
        if (isForest) {
            def.setArenaBiomeOverride(PALE_GARDEN_ARENA_ID);
        }
        return def;
    }

    /**
     * The arena biome id {@code levelNumber} builds, or null when it just uses its own biome's
     * arenas. Cheap and side-effect free: no RNG, no world access, no level generation.
     *
     * <p>The arena cache is keyed by level number and validated by a biome stamp, so it needs
     * this answer for every lookup. Generating a full {@code LevelDefinition} just to read the
     * override would be wasteful and, because {@link #generate} seeds itself from
     * {@code System.nanoTime()}, would also throw away a level nobody plays.
     *
     * <p>Must agree with {@link #buildMinibossLevel}'s override: forest's Creaking miniboss only
     * exists once {@code ForestCreakingMechanic} is registered (gated on
     * {@code PaleGardenBackportCompat.shouldSpawnPaleGarden()} at mod init), so checking
     * {@link #isMinibossLevel} here - rather than re-deriving the availability check - keeps the
     * two in lockstep by construction.
     */
    public static String arenaBiomeOverrideFor(int levelNumber) {
        BiomeTemplate biome = BiomeRegistry.getForLevel(levelNumber);
        if (biome == null) return null;
        int biomeIndex = biome.getBiomeLevelIndex(levelNumber);
        boolean isBoss = biome.isBossLevel(levelNumber);
        if ("forest".equals(biome.biomeId) && isMinibossLevel(biome, biomeIndex, isBoss)) {
            return PALE_GARDEN_ARENA_ID;
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
                                                                   boolean scaleHpPerLevel, boolean bossBeaten,
                                                                   Random rand, InfiniteSpec infiniteSpec) {
        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        // Index of the boss spawn within `spawns`, or -1 on non-boss rounds. The
        // boss must never be turned into a stacked trash mob by the replacement
        // pass below: that rewrites its type (so boss selection can no longer find
        // it) and its HP to a placeholder, which demotes the fight to a stray
        // 6-HP add masquerading as the boss.
        int bossSpawnIndex = -1;

        // Use active-campaign position for difficulty scaling (not registry index).
        // Infinite runs use the run's virtual ordinal instead - the cleared-biome
        // count, which grows without bound so the scaling never plateaus.
        int biomeOrdinal;
        if (infiniteSpec != null) {
            biomeOrdinal = infiniteSpec.virtualOrdinal();
        } else {
            biomeOrdinal = com.crackedgames.craftics.level.campaign.CampaignManager
                    .ordinalOf(biome.biomeId, Math.max(0, branchChoice));
            if (biomeOrdinal < 0) {
                biomeOrdinal = BiomeRegistry.getAllBiomes().indexOf(biome);
            }
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
        // Enemy count ramps within the biome and resets each biome: first level = 3,
        // +1 per level. Once the biome's boss is beaten, every level spawns the biome's
        // peak count. biomeOrdinal no longer drives the count (later biomes stay harder
        // via enemy stat/HP scaling instead). Still clamped by arena size + config max.
        int count = BiomeDifficulty.enemyCount(biomeIndex, biome.levelCount, bossBeaten);
        count = Math.min(count, hardCap);
        // Boss rounds: keep the add crew small and thematic. The boss itself is
        // the main threat; extra random biome trash dilutes the fight.
        if (isBoss) count = Math.min(3, com.crackedgames.craftics.CrafticsMod.CONFIG.maxBossAdds());

        // The two ends of the ramp are configurable (passiveHostileRatioEarly/Late); the rungs
        // between them interpolate off those ends rather than being hardcoded, so moving either
        // end actually moves the curve. These config fields previously did nothing at all - the
        // 0.4/1.0 defaults were copy-pasted here and the knobs left orphaned.
        float earlyRatio = com.crackedgames.craftics.CrafticsMod.CONFIG.passiveHostileRatioEarly();
        float lateRatio = com.crackedgames.craftics.CrafticsMod.CONFIG.passiveHostileRatioLate();
        float hostileRatio;
        if (biomeOrdinal == 0 && biomeIndex == 0) hostileRatio = 0.0f; // Plains I is always passive
        else if (biomeOrdinal == 0 && biomeIndex <= 1) hostileRatio = earlyRatio;
        else if (biomeIndex <= 0) hostileRatio = earlyRatio + (lateRatio - earlyRatio) * 0.33f;
        else if (biomeIndex <= 1) hostileRatio = earlyRatio + (lateRatio - earlyRatio) * 0.66f;
        else hostileRatio = lateRatio;
        if (biomeOrdinal >= 9) hostileRatio = lateRatio;

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

        // Infinite mode overrides the campaign HP math with a flat ordinal curve that
        // ignores the random biome's authored HP + bossHpMultiplier. ord = biomes cleared.
        final boolean infinite = infiniteSpec != null;
        final int infOrdinal = infinite ? infiniteSpec.virtualOrdinal() : 0;
        // Enemy HP under infinite (same for every non-passive add on this level).
        final int infEnemyHp = infinite
            ? com.crackedgames.craftics.level.InfiniteScaling.enemyHp(
                biomeIndex, infOrdinal,
                com.crackedgames.craftics.CrafticsMod.CONFIG.infiniteEnemyBaseHp(),
                com.crackedgames.craftics.CrafticsMod.CONFIG.infiniteEnemyHpPerLevel(),
                com.crackedgames.craftics.CrafticsMod.CONFIG.infiniteEnemyHpPerBiome())
            : 0;

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
            // Bosses are 2x2 - reserve tiles around their footprint
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

            int bossHp = infinite
                ? com.crackedgames.craftics.level.InfiniteScaling.bossHp(
                    infOrdinal,
                    com.crackedgames.craftics.CrafticsMod.CONFIG.infiniteBossBaseHp(),
                    com.crackedgames.craftics.CrafticsMod.CONFIG.infiniteBossHpPerBiome())
                : (int)((biome.boss.baseHp() + hpBonus)
                    * com.crackedgames.craftics.CrafticsMod.CONFIG.bossHpMultiplier());
            bossSpawnIndex = spawns.size();
            // Boss attack scales additively (baseAttack + atkBonus) and, unlike
            // regular enemies, skips the per-biome damage cap in CombatManager.
            // Clamp the scaled value once here so no boss spawn path (standard or
            // infinite override) can push a boss past the ceiling and near
            // one-shot a 20 HP player. See clampBossAttack.
            int bossAttack = clampBossAttack(biome.boss.baseAttack() + atkBonus);
            // Infinite mode: the biome's authored boss is replaced by the run's
            // randomized standard-size boss. Its stats reuse the biome boss's
            // scaled baseline; appearance/AI come from the spec (CombatManager
            // pins the InfiniteBossAI + generated name at spawn).
            if (infiniteSpec != null && infiniteSpec.hasBossOverride()) {
                spawns.add(new LevelDefinition.EnemySpawn(
                    infiniteSpec.bossEntityTypeId(), bossPos,
                    bossHp,
                    bossAttack,
                    biome.boss.baseDefense() + defBonus,
                    Math.max(1, biome.boss.range()),
                    "boss:infinite", 0
                ));
            } else {
                spawns.add(new LevelDefinition.EnemySpawn(
                    biome.boss.entityTypeId(), bossPos,
                    bossHp,
                    bossAttack,
                    biome.boss.baseDefense() + defBonus,
                    biome.boss.range(),
                    biome.boss.aiKey(), biome.boss.speed()
                ));
            }
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
            // Boss rounds spawn only themed hostile backup - no passives.
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
                // Infinite: non-passive adds use the flat enemy curve; passives keep
                // base HP (they don't scale in campaign either).
                isPassive ? mob.baseHp() : (infinite ? infEnemyHp : mob.baseHp() + hpBonus),
                mob.baseAttack() + (isPassive ? 0 : atkBonus),
                mob.baseDefense() + (isPassive ? 0 : defBonus),
                mob.range(),
                mob.aiKey(), mob.speed()
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
                            infinite ? infEnemyHp : bee.baseHp() + hpBonus + biomeIndex,
                            bee.baseAttack() + atkBonus,
                            bee.baseDefense() + defBonus,
                            bee.range(),
                            bee.aiKey(), bee.speed()
                        ));
                    }
                }
            }
        }

        // Stacked enemy replacements (Zombie Stack, Slime Tower, etc.). Each
        // eligible spawn rolls independently against its stack's spawnChance.
        // The stack mob inherits the spawn's position; CombatManager handles
        // building the actual stack visuals from its definition.
        for (int i = 0; i < spawns.size(); i++) {
            if (i == bossSpawnIndex) continue; // never restack the boss (see bossSpawnIndex note)
            LevelDefinition.EnemySpawn s = spawns.get(i);
            if (com.crackedgames.craftics.combat.StackVariants.isStackType(s.entityTypeId())) continue;
            com.crackedgames.craftics.combat.StackVariants.StackDef def =
                com.crackedgames.craftics.combat.StackVariants.findReplacementFor(s.entityTypeId(), biome.biomeId);
            if (def == null) continue;
            if (rand.nextFloat() >= def.spawnChance()) continue;
            spawns.set(i, new LevelDefinition.EnemySpawn(
                com.crackedgames.craftics.combat.StackVariants.typeIdFor(def.id()),
                s.position(),
                // HP/ATK/DEF/range/speed come from the stack's BASE layer at
                // spawn time, looked up in CombatManager. Passing 1s here is
                // intentional - these values are unused for stack types.
                1, 1, 0, 1,
                s.aiKey(), 0
            ));
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
