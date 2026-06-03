package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.level.LevelDefinition;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;

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

    /** Items that can appear as the ominous-trial "hero" weapon. Real-material
     *  weapons use the netherite tier; bow/crossbow/mace stay as their only tier. */
    private static final Item[] OMINOUS_WEAPONS = {
        Items.NETHERITE_SWORD, Items.NETHERITE_AXE,
        Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE,
        Items.BOW, Items.CROSSBOW, Items.MACE
    };

    /** Any-tier armor piece, four slots × six materials. */
    private static final Item[] OMINOUS_ARMOR = {
        Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS,
        Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS,
        Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
        Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS,
        Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
        Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
    };

    /** Map an armor item to its equipment slot for the enchant pool lookup. */
    private static EquipmentSlot armorSlotFor(Item item) {
        String id = net.minecraft.registry.Registries.ITEM.getId(item).getPath();
        if (id.endsWith("_helmet"))     return EquipmentSlot.HEAD;
        if (id.endsWith("_chestplate")) return EquipmentSlot.CHEST;
        if (id.endsWith("_leggings"))   return EquipmentSlot.LEGS;
        if (id.endsWith("_boots"))      return EquipmentSlot.FEET;
        return EquipmentSlot.CHEST;
    }

    /** Roll a single heavily enchanted armor piece — any material tier, any slot. */
    private static ItemStack rollHeavyArmor(ServerWorld world, Random rng) {
        Item item = OMINOUS_ARMOR[rng.nextInt(OMINOUS_ARMOR.length)];
        ItemStack stack = new ItemStack(item, 1);
        EquipmentSlot slot = armorSlotFor(item);
        return CombatManager.heavilyEnchant(world, stack,
            CombatManager.getValidArmorEnchants(slot), rng);
    }

    /** Roll a single heavily enchanted weapon — netherite tier (where applicable)
     *  for swords/axes/shovels/hoes, plus bow/crossbow/mace. */
    private static ItemStack rollHeavyWeapon(ServerWorld world, Random rng) {
        Item item = OMINOUS_WEAPONS[rng.nextInt(OMINOUS_WEAPONS.length)];
        ItemStack stack = new ItemStack(item, 1);
        return CombatManager.heavilyEnchant(world, stack,
            CombatManager.getValidWeaponEnchants(stack), rng);
    }

    /** Roll one supply item — high-tier consumables that complement the hero piece. */
    private static ItemStack rollSupplyItem(Random rng) {
        int roll = rng.nextInt(100);
        if (roll < 15) {
            // Rare top-shelf consumable
            return new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1);
        } else if (roll < 40) {
            return new ItemStack(Items.GOLDEN_APPLE, 1 + rng.nextInt(2));
        } else if (roll < 70) {
            return new ItemStack(Items.GOLDEN_CARROT, 3 + rng.nextInt(3));
        } else if (roll < 85) {
            return new ItemStack(Items.CAKE, 1);
        }
        return new ItemStack(Items.HONEY_BOTTLE, 2);
    }

    /** Per-player ominous trial reward: one heavily-enchanted gear piece (weapon
     *  or armor, 50/50) plus 2-3 supply consumables. Called once per recipient
     *  in CombatManager's loot loop. */
    private static List<ItemStack> rollOminousLoot(ServerWorld world, Random rng) {
        List<ItemStack> rewards = new ArrayList<>();
        rewards.add(rng.nextBoolean() ? rollHeavyWeapon(world, rng) : rollHeavyArmor(world, rng));
        int supplies = 2 + rng.nextInt(2); // 2 or 3
        for (int i = 0; i < supplies; i++) {
            rewards.add(rollSupplyItem(rng));
        }
        return rewards;
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
            @Override public List<ItemStack> rollCompletionLoot(ServerWorld world) {
                // Per-player: 1 heavily-enchanted gear piece (weapon or armor, 50/50)
                // plus 2-3 supply consumables. CombatManager's loot loop calls this
                // once per recipient so each member gets their own fresh roll.
                return rollOminousLoot(world, new Random());
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
        public net.minecraft.util.math.BlockPos getOverrideOrigin(
                java.util.UUID worldOwner,
                com.crackedgames.craftics.world.CrafticsSavedData data) {
            if (worldOwner == null || data == null) return null;
            return data.getTrialChamberOrigin(worldOwner);
        }

        @Override
        public GridTile[][] buildTiles() {
            // Trial chambers are now schematic-driven (preserveSchematicGround).
            // The previous random copper-obstacle scatter set OBSTACLE tile
            // types that never matched a real world block — those phantom
            // obstacles blocked pathfinding line-of-sight (a stray's arrow
            // would report "blocked by an obstacle" even on a visually clear
            // shot) and pinned the player out of perfectly walkable tiles.
            // The post-scan in ArenaBuilder.buildAt promotes NORMAL → OBSTACLE
            // wherever the placed schematic actually has wall blocks, so the
            // dev's design is the single source of truth.
            return super.buildTiles();
        }

        @Override
        public List<ItemStack> rollCompletionLoot() {
            return rollRewards(new Random());
        }
    }
}
