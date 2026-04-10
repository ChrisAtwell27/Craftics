package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.level.LevelDefinition;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Trial Chamber random event — a harder optional combat encounter
 * with trial chamber themed enemies and loot.
 */
public class TrialChamberEvent {

    /** Trial chamber mob pool with their combat stats. */
    private static final String[][] TRIAL_MOBS = {
        // {entityTypeId, hp, attack, defense, range}
        {"minecraft:zombie",      "12", "4", "1", "1"},
        {"minecraft:skeleton",    "10", "3", "0", "3"},
        {"minecraft:spider",      "10", "3", "0", "1"},
        {"minecraft:stray",       "10", "4", "0", "3"},
        {"minecraft:husk",        "12", "4", "1", "1"},
        {"minecraft:bogged",      "10", "3", "0", "3"},
        {"minecraft:silverfish",  "4",  "2", "0", "1"},
        {"minecraft:cave_spider", "8",  "3", "0", "1"},
    };

    /** Breeze is the trial chamber signature mob — appears as a mini-boss. */
    private static final String[] BREEZE = {"minecraft:breeze", "20", "5", "2", "3"};

    /** Generate a trial chamber level definition scaled to difficulty. */
    public static LevelDefinition generate(int currentBiomeOrdinal, int ngPlusLevel) {
        Random rng = new Random();

        // Scale difficulty: harder than current level
        float diffMultiplier = 1.3f + (currentBiomeOrdinal * 0.05f) + (ngPlusLevel * 0.15f);

        // Grid size: spacious arena for tactical play
        int width = 10 + rng.nextInt(3);   // 10-12
        int height = 10 + rng.nextInt(3);  // 10-12

        // Enemy count: 4-6 regular mobs + 1 breeze
        int enemyCount = 4 + rng.nextInt(3);

        List<LevelDefinition.EnemySpawn> spawnList = new ArrayList<>();

        // Place regular trial mobs
        List<GridPos> usedPositions = new ArrayList<>();
        usedPositions.add(new GridPos(1, 1)); // player start

        for (int i = 0; i < enemyCount; i++) {
            String[] mob = TRIAL_MOBS[rng.nextInt(TRIAL_MOBS.length)];
            GridPos pos = findSpawnPos(width, height, usedPositions, rng);
            if (pos == null) continue;
            usedPositions.add(pos);

            int hp  = (int)(Integer.parseInt(mob[1]) * diffMultiplier);
            int atk = (int)(Integer.parseInt(mob[2]) * diffMultiplier);
            int def = Integer.parseInt(mob[3]);
            int range = Integer.parseInt(mob[4]);

            spawnList.add(new LevelDefinition.EnemySpawn(mob[0], pos, hp, atk, def, range));
        }

        // Always spawn a Breeze as the challenge mob
        GridPos breezePos = findSpawnPos(width, height, usedPositions, rng);
        if (breezePos != null) {
            int breezeHp  = (int)(Integer.parseInt(BREEZE[1]) * diffMultiplier);
            int breezeAtk = (int)(Integer.parseInt(BREEZE[2]) * diffMultiplier);
            spawnList.add(new LevelDefinition.EnemySpawn(BREEZE[0], breezePos,
                breezeHp, breezeAtk, Integer.parseInt(BREEZE[3]), Integer.parseInt(BREEZE[4])));
        }

        // Convert to array for LevelDefinition contract
        LevelDefinition.EnemySpawn[] spawns = spawnList.toArray(new LevelDefinition.EnemySpawn[0]);

        return new TrialChamberLevelDef(width, height, spawns, rng);
    }

    private static GridPos findSpawnPos(int width, int height, List<GridPos> used, Random rng) {
        for (int attempts = 0; attempts < 30; attempts++) {
            int x = 2 + rng.nextInt(width - 4);
            int z = 2 + rng.nextInt(height - 4);
            GridPos pos = new GridPos(x, z);
            boolean tooClose = false;
            for (GridPos u : used) {
                if (Math.abs(u.x() - x) + Math.abs(u.z() - z) < 2) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) return pos;
        }
        return null;
    }

    /** Roll 1-2 trial chamber reward items. */
    public static List<ItemStack> rollRewards(Random rng) {
        List<ItemStack> rewards = new ArrayList<>();
        int count = 1 + (rng.nextFloat() < 0.4f ? 1 : 0); // 40% chance of 2 items

        for (int i = 0; i < count; i++) {
            rewards.add(rollSingleReward(rng));
        }
        return rewards;
    }

    private static ItemStack rollSingleReward(Random rng) {
        int roll = rng.nextInt(100);
        if (roll < 20) {
            // Common: consumables (20%)
            return switch (rng.nextInt(5)) {
                case 0 -> new ItemStack(Items.GOLDEN_APPLE, 1);
                case 1 -> new ItemStack(Items.ENDER_PEARL, 2);
                case 2 -> new ItemStack(Items.FIRE_CHARGE, 3);
                case 3 -> new ItemStack(Items.ARROW, 16);
                default -> new ItemStack(Items.SPLASH_POTION, 1);
            };
        } else if (roll < 45) {
            // Uncommon: useful gear (25%)
            return switch (rng.nextInt(5)) {
                case 0 -> new ItemStack(Items.DIAMOND, 2);
                case 1 -> new ItemStack(Items.EMERALD, 5);
                case 2 -> new ItemStack(Items.IRON_AXE, 1);
                case 3 -> new ItemStack(Items.CROSSBOW, 1);
                default -> new ItemStack(Items.SHIELD, 1);
            };
        } else if (roll < 70) {
            // Rare: trial chamber specials (25%)
            return switch (rng.nextInt(5)) {
                case 0 -> new ItemStack(Items.DIAMOND_SWORD, 1);
                case 1 -> new ItemStack(Items.DIAMOND_CHESTPLATE, 1);
                case 2 -> new ItemStack(Items.TRIDENT, 1);
                case 3 -> new ItemStack(Items.MACE, 1);
                default -> new ItemStack(Items.TOTEM_OF_UNDYING, 1);
            };
        } else if (roll < 90) {
            // Epic: trial exclusive items (20%)
            return switch (rng.nextInt(4)) {
                case 0 -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1);
                case 1 -> new ItemStack(Items.WIND_CHARGE, 4);
                case 2 -> new ItemStack(Items.BREEZE_ROD, 2);
                default -> new ItemStack(Items.HEAVY_CORE, 1);
            };
        } else {
            // Legendary: ominous items (10%)
            return switch (rng.nextInt(3)) {
                case 0 -> new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1);
                case 1 -> new ItemStack(Items.TRIAL_KEY, 1);
                default -> new ItemStack(Items.OMINOUS_TRIAL_KEY, 1);
            };
        }
    }

    /** Generate a small quick ambush encounter — 2-3 fast enemies, tiny arena. */
    public static LevelDefinition generateAmbush(String arenaBiomeId, int biomeOrdinal, int ngPlusLevel) {
        Random rng = new Random();

        // Pull the biome's hostile pool so ambushes match where you are —
        // zombies in Plains, magma cubes in the Nether, enderman in the End.
        com.crackedgames.craftics.level.BiomeTemplate biome = null;
        if (arenaBiomeId != null && !arenaBiomeId.isBlank()) {
            for (var b : com.crackedgames.craftics.level.BiomeRegistry.getAllBiomes()) {
                if (b.biomeId.equals(arenaBiomeId)) { biome = b; break; }
            }
        }

        // Match LevelGenerator's per-biome-ordinal stat bonuses so ambush mobs
        // don't feel like plains mobs when you're deep in the Nether.
        int hpBonus = biomeOrdinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        int atkBonus = biomeOrdinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());
        // NG+ bumps both a little on top of the biome scaling
        float ngMult = 1.0f + (ngPlusLevel * 0.08f);

        int enemyCount = 2 + (rng.nextFloat() < 0.3f ? 1 : 0); // 2-3 enemies
        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(2, 2)); // player start

        // Fallback pool for biomes with no hostile entries (shouldn't happen
        // in practice but guards against null biomes / trial chamber biomes).
        com.crackedgames.craftics.level.MobPoolEntry[] hostilePool = (biome != null && biome.hostileMobs != null && biome.hostileMobs.length > 0)
            ? biome.hostileMobs
            : new com.crackedgames.craftics.level.MobPoolEntry[] {
                new com.crackedgames.craftics.level.MobPoolEntry("minecraft:zombie", 1, 6, 2, 0, 1, false),
                new com.crackedgames.craftics.level.MobPoolEntry("minecraft:spider", 1, 6, 2, 0, 1, false),
            };

        for (int i = 0; i < enemyCount; i++) {
            com.crackedgames.craftics.level.MobPoolEntry mob = hostilePool[rng.nextInt(hostilePool.length)];
            GridPos pos = findSpawnPos(8, 8, used, rng);
            if (pos == null) continue;
            used.add(pos);
            int hp = (int)((mob.baseHp() + hpBonus) * ngMult);
            int atk = (int)((mob.baseAttack() + atkBonus) * ngMult);
            spawns.add(new LevelDefinition.EnemySpawn(mob.entityTypeId(), pos, hp, atk,
                mob.baseDefense(), mob.range()));
        }

        final String resolvedArenaBiomeId = (arenaBiomeId == null || arenaBiomeId.isBlank())
            ? "plains" : arenaBiomeId;

        return new TrialChamberLevelDef(8, 8, spawns, rng) {
            @Override public String getName() { return "Ambush!"; }
            @Override public GridPos getPlayerStart() { return new GridPos(3, 3); }
            @Override public Block getFloorBlock() { return Blocks.DIRT; }
            @Override public String getArenaBiomeId() { return resolvedArenaBiomeId; }
        };
    }

    /** Generate an ominous trial chamber — harder, with Warden + Breeze. */
    public static LevelDefinition generateOminous(int biomeOrdinal, int ngPlusLevel) {
        Random rng = new Random();
        float diffMultiplier = 1.6f + (biomeOrdinal * 0.06f) + (ngPlusLevel * 0.2f);

        int width = 12 + rng.nextInt(3);  // 12-14
        int height = 12 + rng.nextInt(3); // 12-14

        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(1, 1));

        // 5-7 regular trial mobs
        int enemyCount = 5 + rng.nextInt(3);
        for (int i = 0; i < enemyCount; i++) {
            String[] mob = TRIAL_MOBS[rng.nextInt(TRIAL_MOBS.length)];
            GridPos pos = findSpawnPos(width, height, used, rng);
            if (pos == null) continue;
            used.add(pos);
            int hp = (int)(Integer.parseInt(mob[1]) * diffMultiplier);
            int atk = (int)(Integer.parseInt(mob[2]) * diffMultiplier);
            spawns.add(new LevelDefinition.EnemySpawn(mob[0], pos, hp, atk,
                Integer.parseInt(mob[3]), Integer.parseInt(mob[4])));
        }

        // Always spawn Breeze
        GridPos breezePos = findSpawnPos(width, height, used, rng);
        if (breezePos != null) {
            used.add(breezePos);
            spawns.add(new LevelDefinition.EnemySpawn(BREEZE[0], breezePos,
                (int)(30 * diffMultiplier), (int)(6 * diffMultiplier), 3, 3));
        }

        // Spawn a Warden as the ominous boss
        GridPos wardenPos = findSpawnPos(width, height, used, rng);
        if (wardenPos != null) {
            spawns.add(new LevelDefinition.EnemySpawn("minecraft:warden", wardenPos,
                (int)(50 * diffMultiplier), (int)(8 * diffMultiplier), 4, 2));
        }

        return new TrialChamberLevelDef(width, height, spawns, rng) {
            @Override public String getName() { return "Ominous Trial Chamber"; }
            @Override public Block getFloorBlock() { return Blocks.DEEPSLATE_BRICKS; }
            @Override public List<ItemStack> rollCompletionLoot() {
                return rollOminousRewards(new Random());
            }
        };
    }

    /** Treasure Vault — no enemies, just loot on the ground. Player gets 2-4 random items scaled to biome tier. */
    public static LevelDefinition generateTreasureVault(int biomeOrdinal) {
        Random rng = new Random();
        // No enemies — the "combat" will be won immediately
        return new TrialChamberLevelDef(5, 5, new ArrayList<>(), rng) {
            @Override public String getName() { return "Treasure Vault"; }
            @Override public GridPos getPlayerStart() { return new GridPos(2, 2); }
            @Override public Block getFloorBlock() { return Blocks.GOLD_BLOCK; }
            @Override public boolean isNightLevel() { return false; }
            @Override public List<ItemStack> rollCompletionLoot() {
                List<ItemStack> loot = new ArrayList<>();
                Random r = new Random();
                int count = 2 + r.nextInt(3); // 2-4 items
                for (int i = 0; i < count; i++) {
                    loot.add(rollTieredReward(r, biomeOrdinal));
                }
                return loot;
            }
        };
    }

    /** No-arg version for backwards compatibility (defaults to late-game loot). */
    public static LevelDefinition generateTreasureVault() {
        return generateTreasureVault(10);
    }

    /** Roll a reward scaled to biome progression. Early biomes get simpler loot. */
    private static ItemStack rollTieredReward(Random rng, int biomeOrdinal) {
        // Early biomes (0-2): only common + uncommon
        // Mid biomes (3-6): common through rare
        // Late biomes (7+): full loot table including epic/legendary
        int roll = rng.nextInt(100);
        if (biomeOrdinal <= 2) {
            // Early: 50% common, 50% uncommon — no rare/epic/legendary
            if (roll < 50) {
                return switch (rng.nextInt(5)) {
                    case 0 -> new ItemStack(Items.GOLDEN_APPLE, 1);
                    case 1 -> new ItemStack(Items.ARROW, 16);
                    case 2 -> new ItemStack(Items.COOKED_BEEF, 8);
                    case 3 -> new ItemStack(Items.IRON_INGOT, 4);
                    default -> new ItemStack(Items.SHIELD, 1);
                };
            } else {
                return switch (rng.nextInt(4)) {
                    case 0 -> new ItemStack(Items.IRON_SWORD, 1);
                    case 1 -> new ItemStack(Items.IRON_AXE, 1);
                    case 2 -> new ItemStack(Items.EMERALD, 3);
                    default -> new ItemStack(Items.CROSSBOW, 1);
                };
            }
        } else if (biomeOrdinal <= 6) {
            // Mid: 30% common, 40% uncommon, 30% rare — no epic/legendary
            if (roll < 30) {
                return switch (rng.nextInt(4)) {
                    case 0 -> new ItemStack(Items.GOLDEN_APPLE, 1);
                    case 1 -> new ItemStack(Items.ENDER_PEARL, 2);
                    case 2 -> new ItemStack(Items.ARROW, 16);
                    default -> new ItemStack(Items.DIAMOND, 1);
                };
            } else if (roll < 70) {
                return switch (rng.nextInt(5)) {
                    case 0 -> new ItemStack(Items.DIAMOND, 2);
                    case 1 -> new ItemStack(Items.EMERALD, 5);
                    case 2 -> new ItemStack(Items.IRON_AXE, 1);
                    case 3 -> new ItemStack(Items.CROSSBOW, 1);
                    default -> new ItemStack(Items.SHIELD, 1);
                };
            } else {
                return switch (rng.nextInt(4)) {
                    case 0 -> new ItemStack(Items.DIAMOND_SWORD, 1);
                    case 1 -> new ItemStack(Items.DIAMOND_CHESTPLATE, 1);
                    case 2 -> new ItemStack(Items.TOTEM_OF_UNDYING, 1);
                    default -> new ItemStack(Items.DIAMOND_AXE, 1);
                };
            }
        } else {
            // Late game: full loot table
            return rollSingleReward(rng);
        }
    }

    /** Ominous trial loot — better than regular trial. */
    private static List<ItemStack> rollOminousRewards(Random rng) {
        List<ItemStack> rewards = new ArrayList<>();
        int count = 2 + (rng.nextFloat() < 0.5f ? 1 : 0); // 2-3 items
        for (int i = 0; i < count; i++) {
            int roll = rng.nextInt(100);
            if (roll < 25) {
                rewards.add(switch (rng.nextInt(3)) {
                    case 0 -> new ItemStack(Items.DIAMOND, 3);
                    case 1 -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1);
                    default -> new ItemStack(Items.TOTEM_OF_UNDYING, 1);
                });
            } else if (roll < 50) {
                rewards.add(switch (rng.nextInt(3)) {
                    case 0 -> new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1);
                    case 1 -> new ItemStack(Items.MACE, 1);
                    default -> new ItemStack(Items.TRIDENT, 1);
                });
            } else if (roll < 75) {
                rewards.add(switch (rng.nextInt(3)) {
                    case 0 -> new ItemStack(Items.HEAVY_CORE, 1);
                    case 1 -> new ItemStack(Items.OMINOUS_TRIAL_KEY, 1);
                    default -> new ItemStack(Items.WIND_CHARGE, 8);
                });
            } else {
                rewards.add(switch (rng.nextInt(3)) {
                    case 0 -> new ItemStack(Items.NETHERITE_SWORD, 1);
                    case 1 -> new ItemStack(Items.NETHERITE_CHESTPLATE, 1);
                    default -> new ItemStack(Items.DIAMOND_BLOCK, 2);
                });
            }
        }
        return rewards;
    }

    /** Inner level definition for trial chambers. */
    static class TrialChamberLevelDef extends LevelDefinition {
        private final int width, height;
        private final EnemySpawn[] spawns;
        private final Block floorBlock;
        private final int levelNumber;

        TrialChamberLevelDef(int width, int height, EnemySpawn[] spawns, Random rng) {
            this.width = width;
            this.height = height;
            this.spawns = spawns;
            // Trial chamber themed floors
            this.floorBlock = rng.nextBoolean() ? Blocks.TUFF_BRICKS : Blocks.POLISHED_TUFF;
            this.levelNumber = 9000 + rng.nextInt(900);
        }

        TrialChamberLevelDef(int width, int height, List<EnemySpawn> spawnList, Random rng) {
            this(width, height, spawnList.toArray(new EnemySpawn[0]), rng);
        }

        @Override public int getLevelNumber() { return levelNumber; }
        @Override public String getName() { return "Trial Chamber"; }
        @Override public int getWidth() { return width; }
        @Override public int getHeight() { return height; }
        @Override public GridPos getPlayerStart() { return new GridPos(1, 1); }
        @Override public Block getFloorBlock() { return floorBlock; }
        @Override public EnemySpawn[] getEnemySpawns() { return spawns; }
        @Override public boolean isNightLevel() { return true; } // dark atmosphere

        @Override
        public GridTile[][] buildTiles() {
            // Add some obstacles to make the trial chamber more interesting
            GridTile[][] tiles = super.buildTiles();
            Random tileRng = new Random();

            // Scatter a few copper/tuff obstacles (trial chamber aesthetic)
            int obstacleCount = 4 + tileRng.nextInt(5);
            for (int i = 0; i < obstacleCount; i++) {
                int ox = 1 + tileRng.nextInt(width - 2);
                int oz = 1 + tileRng.nextInt(height - 2);
                // Don't place on player start
                if (ox == 1 && oz == 1) continue;
                tiles[ox][oz] = new GridTile(
                    com.crackedgames.craftics.core.TileType.OBSTACLE,
                    Blocks.COPPER_BLOCK
                );
            }
            return tiles;
        }

        @Override
        public List<ItemStack> rollCompletionLoot() {
            return rollRewards(new Random());
        }
    }
}
