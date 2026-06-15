package com.crackedgames.craftics.combat;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.*;

/**
 * Generates tiered trader inventories based on trader type and biome tier.
 * Trader types offer themed items, biome tier determines quality.
 */
public class TraderSystem {

    public enum TraderType {
        WEAPONSMITH("Weaponsmith", "§c⚔"),
        ARMORER("Armorer", "§9⛨"),
        PROVISIONER("Provisioner", "§a⚘"),
        ALCHEMIST("Alchemist", "§d⚗"),
        SUPPLIER("Supplier", "§e⛏"),
        DECORATOR("Decorator", "§b⌂"),
        CRAFTSMAN("Craftsman", "§6⚒"),
        CURIOSITY_DEALER("Curiosity Dealer", "§5✦");

        public final String displayName;
        public final String icon;

        TraderType(String displayName, String icon) {
            this.displayName = displayName;
            this.icon = icon;
        }
    }

    public record Trade(ItemStack item, int emeraldCost, String description) {}

    /**
     * Generate trades for a random trader type at the given biome tier (1-9).
     */
    public static TraderOffer generateOffer(int biomeTier, Random random) {
        TraderType type = TraderType.values()[random.nextInt(TraderType.values().length)];
        List<Trade> trades = generateTrades(type, biomeTier, random);
        return new TraderOffer(type, trades);
    }

    public record TraderOffer(TraderType type, List<Trade> trades) {}

    private static List<Trade> generateTrades(TraderType type, int tier, Random random) {
        List<Trade> pool = new ArrayList<>();

        switch (type) {
            case WEAPONSMITH -> buildWeaponTrades(pool, tier);
            case ARMORER -> buildArmorTrades(pool, tier);
            case PROVISIONER -> buildFoodTrades(pool, tier);
            case ALCHEMIST -> buildAlchemistTrades(pool, tier);
            case SUPPLIER -> buildSupplierTrades(pool, tier);
            case DECORATOR -> buildDecoratorTrades(pool, tier);
            case CRAFTSMAN -> buildCraftsmanTrades(pool, tier);
            case CURIOSITY_DEALER -> buildCuriosityTrades(pool, tier);
        }

        // Scale prices by tier - later biomes cost significantly more
        double tierMult = 1.0 + (tier - 1) * 0.35; // tier 1=1.0x, tier 5=2.4x, tier 9=3.8x
        for (int i = 0; i < pool.size(); i++) {
            Trade t = pool.get(i);
            int scaled = Math.max(1, (int) Math.ceil(t.emeraldCost() * tierMult));
            pool.set(i, new Trade(t.item(), scaled, t.description()));
        }

        // Shuffle and pick 3-5 trades
        Collections.shuffle(pool, random);
        int count = Math.min(pool.size(), 3 + random.nextInt(3));
        return pool.subList(0, count);
    }

    // ---- WEAPONSMITH ----
    private static void buildWeaponTrades(List<Trade> pool, int tier) {
        // Current tier weapons (common)
        if (tier <= 2) {
            pool.add(trade(Items.WOODEN_SWORD, 1, 2, "Wooden Sword"));
            pool.add(trade(Items.WOODEN_AXE, 1, 2, "Wooden Axe"));
            pool.add(trade(Items.BOW, 1, 3, "Bow"));
        }
        if (tier >= 2 && tier <= 4) {
            pool.add(trade(Items.STONE_SWORD, 1, 3, "Stone Sword"));
            pool.add(trade(Items.STONE_AXE, 1, 3, "Stone Axe"));
        }
        if (tier >= 3 && tier <= 6) {
            pool.add(trade(Items.IRON_SWORD, 1, 5, "Iron Sword"));
            pool.add(trade(Items.IRON_AXE, 1, 5, "Iron Axe"));
            pool.add(trade(Items.CROSSBOW, 1, 6, "Crossbow"));
        }
        if (tier >= 6 && tier <= 8) {
            pool.add(trade(Items.DIAMOND_SWORD, 1, 10, "Diamond Sword"));
            pool.add(trade(Items.DIAMOND_AXE, 1, 10, "Diamond Axe"));
        }
        if (tier >= 8) {
            pool.add(trade(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1, 15, "Netherite Template"));
        }
        // Rare tier-up weapons (expensive)
        if (tier <= 2) pool.add(trade(Items.STONE_SWORD, 1, 6, "§eRare Stone Sword"));
        if (tier >= 3 && tier <= 5) pool.add(trade(Items.DIAMOND_SWORD, 1, 15, "§eRare Diamond Sword"));
        // Always available
        pool.add(trade(Items.ARROW, 16, 2, "Arrows (x16)"));
        pool.add(trade(Items.SHIELD, 1, 4, "Shield"));

        // Trident (water-type ranged + lightning channeling).
        if (tier >= 4) pool.add(trade(Items.TRIDENT, 1, 12, "§bTrident"));
        // Mace (AoE blunt weapon - heavy core endgame). Very rare - endgame
        // tier only and at a prohibitive emerald cost so it stays a marquee
        // find rather than a routine purchase.
        if (tier >= 8) pool.add(trade(Items.MACE, 1, 45, "§d§lMace §7(very rare)"));
        // Stick / Bamboo / Blaze Rod / Coral / Breeze Rod - special-type weapons.
        pool.add(trade(Items.STICK, 4, 1, "Sticks (x4) §7(stun)"));
        if (tier >= 2) pool.add(trade(Items.BAMBOO, 4, 2, "Bamboo (x4) §7(stun)"));
        if (tier >= 4) pool.add(trade(Items.BLAZE_ROD, 1, 4, "§6Blaze Rod §7(fire weapon)"));
        if (tier >= 6) pool.add(trade(Items.BREEZE_ROD, 1, 7, "§bBreeze Rod"));
        // Coral weapons (water-type).
        if (tier >= 3) {
            pool.add(trade(Items.TUBE_CORAL, 1, 4, "§9Tube Coral §7(soak)"));
            pool.add(trade(Items.BRAIN_CORAL, 1, 4, "§dBrain Coral §7(confuse)"));
            pool.add(trade(Items.BUBBLE_CORAL, 1, 4, "§5Bubble Coral §7(knockback)"));
            pool.add(trade(Items.FIRE_CORAL, 1, 5, "§cFire Coral §7(searing)"));
            pool.add(trade(Items.HORN_CORAL, 1, 5, "§eHorn Coral §7(pierce)"));
        }
        // Special / Pet affinity weapons (hoes / shovels).
        if (tier >= 2 && tier <= 4) {
            pool.add(trade(Items.IRON_HOE, 1, 4, "Iron Hoe §7(Special)"));
            pool.add(trade(Items.IRON_SHOVEL, 1, 4, "Iron Shovel §7(Pet)"));
        }
        if (tier >= 5) {
            pool.add(trade(Items.DIAMOND_HOE, 1, 9, "§bDiamond Hoe §7(Special)"));
            pool.add(trade(Items.DIAMOND_SHOVEL, 1, 9, "§bDiamond Shovel §7(Pet)"));
        }
        // Wind Charges (knockback + self-launch momentum).
        if (tier >= 3) pool.add(trade(Items.WIND_CHARGE, 4, 5, "Wind Charges (x4)"));
        // Tipped arrows can be picked up via Alchemist; the weaponsmith carries
        // plain arrows + Pickaxes for breaking obstacles.
        pool.add(trade(Items.WOODEN_PICKAXE, 1, 2, "Wooden Pickaxe"));
        if (tier >= 3) pool.add(trade(Items.IRON_PICKAXE, 1, 6, "Iron Pickaxe"));
        if (tier >= 6) pool.add(trade(Items.DIAMOND_PICKAXE, 1, 12, "§bDiamond Pickaxe"));

        // Basic Weapons compat: a small tier-appropriate subset of the mod's weapons.
        // No-ops when the mod is absent (forTier returns empty).
        if (com.crackedgames.craftics.compat.basicweapons.BasicWeaponsCompat.isLoaded()) {
            for (Item item : com.crackedgames.craftics.compat.basicweapons.BasicWeaponsLootRoller.forTier(tier)) {
                String type = com.crackedgames.craftics.compat.basicweapons.BasicWeaponsCompat
                    .weaponType(net.minecraft.registry.Registries.ITEM.getId(item).getPath());
                int cost = com.crackedgames.craftics.compat.basicweapons.BasicWeaponsCompat.baseTraderCost(type);
                pool.add(trade(item, 1, cost, "§e" + item.getName().getString()));
            }
        }
    }

    // ---- ARMORER ----
    private static void buildArmorTrades(List<Trade> pool, int tier) {
        if (tier <= 3) {
            pool.add(trade(Items.LEATHER_HELMET, 1, 2, "Leather Helmet"));
            pool.add(trade(Items.LEATHER_CHESTPLATE, 1, 3, "Leather Chestplate"));
            pool.add(trade(Items.LEATHER_LEGGINGS, 1, 3, "Leather Leggings"));
            pool.add(trade(Items.LEATHER_BOOTS, 1, 2, "Leather Boots"));
        }
        if (tier >= 2 && tier <= 5) {
            pool.add(trade(Items.CHAINMAIL_HELMET, 1, 3, "Chainmail Helmet"));
            pool.add(trade(Items.CHAINMAIL_CHESTPLATE, 1, 5, "Chainmail Chestplate"));
            pool.add(trade(Items.CHAINMAIL_LEGGINGS, 1, 4, "Chainmail Leggings"));
            pool.add(trade(Items.CHAINMAIL_BOOTS, 1, 3, "Chainmail Boots"));
        }
        if (tier >= 4 && tier <= 7) {
            pool.add(trade(Items.IRON_HELMET, 1, 5, "Iron Helmet"));
            pool.add(trade(Items.IRON_CHESTPLATE, 1, 8, "Iron Chestplate"));
            pool.add(trade(Items.IRON_LEGGINGS, 1, 7, "Iron Leggings"));
            pool.add(trade(Items.IRON_BOOTS, 1, 5, "Iron Boots"));
        }
        if (tier >= 7) {
            pool.add(trade(Items.DIAMOND_HELMET, 1, 10, "Diamond Helmet"));
            pool.add(trade(Items.DIAMOND_CHESTPLATE, 1, 15, "Diamond Chestplate"));
            pool.add(trade(Items.DIAMOND_LEGGINGS, 1, 13, "Diamond Leggings"));
            pool.add(trade(Items.DIAMOND_BOOTS, 1, 10, "Diamond Boots"));
        }
    }

    // ---- PROVISIONER ----
    private static void buildFoodTrades(List<Trade> pool, int tier) {
        pool.add(trade(Items.BREAD, 2, 2, "Bread (x2)"));
        pool.add(trade(Items.COOKED_BEEF, 2, 3, "Steak (x2)"));
        pool.add(trade(Items.BAKED_POTATO, 2, 2, "Baked Potato (x2)"));
        // Seeds and crops
        pool.add(trade(Items.WHEAT_SEEDS, 4, 1, "Wheat Seeds (x4)"));
        pool.add(trade(Items.BEETROOT_SEEDS, 4, 1, "Beetroot Seeds (x4)"));
        pool.add(trade(Items.CARROT, 2, 2, "Carrots (x2)"));
        pool.add(trade(Items.POTATO, 2, 2, "Potatoes (x2)"));
        if (tier >= 2) {
            pool.add(trade(Items.PUMPKIN_SEEDS, 3, 2, "Pumpkin Seeds (x3)"));
            pool.add(trade(Items.MELON_SEEDS, 3, 2, "Melon Seeds (x3)"));
            pool.add(trade(Items.SUGAR_CANE, 3, 2, "Sugar Cane (x3)"));
        }
        if (tier >= 3) {
            pool.add(trade(Items.COOKED_PORKCHOP, 2, 3, "Cooked Porkchop (x2)"));
            pool.add(trade(Items.PUMPKIN_PIE, 1, 3, "Pumpkin Pie"));
            pool.add(trade(Items.COCOA_BEANS, 3, 2, "Cocoa Beans (x3)"));
            pool.add(trade(Items.SWEET_BERRIES, 4, 2, "Sweet Berries (x4)"));
        }
        if (tier >= 4) {
            pool.add(trade(Items.GOLDEN_CARROT, 1, 5, "Golden Carrot"));
            pool.add(trade(Items.TORCHFLOWER_SEEDS, 2, 3, "Torchflower Seeds (x2)"));
        }
        if (tier >= 6) {
            pool.add(trade(Items.GOLDEN_APPLE, 1, 10, "Golden Apple"));
            pool.add(trade(Items.HONEY_BOTTLE, 2, 4, "Honey Bottles (x2)"));
            pool.add(trade(Items.RABBIT_STEW, 1, 5, "Rabbit Stew §7(best food)"));
        }
        if (tier >= 9) {
            pool.add(trade(Items.ENCHANTED_GOLDEN_APPLE, 1, 25, "§6Enchanted Golden Apple"));
        }
        pool.add(trade(Items.MILK_BUCKET, 1, 4, "Milk Bucket (clears effects)"));
        // Other restorative foods - common across tiers.
        pool.add(trade(Items.MUSHROOM_STEW, 1, 3, "Mushroom Stew"));
        pool.add(trade(Items.BEETROOT_SOUP, 1, 3, "Beetroot Soup"));
        if (tier >= 2) pool.add(trade(Items.SUSPICIOUS_STEW, 1, 4, "Suspicious Stew"));
        if (tier >= 3) {
            pool.add(trade(Items.COOKED_RABBIT, 2, 3, "Cooked Rabbit (x2)"));
            pool.add(trade(Items.COOKED_MUTTON, 2, 3, "Cooked Mutton (x2)"));
            pool.add(trade(Items.COOKED_SALMON, 2, 3, "Cooked Salmon (x2)"));
            pool.add(trade(Items.DRIED_KELP, 4, 2, "Dried Kelp (x4)"));
        }
        if (tier >= 4) {
            pool.add(trade(Items.CHORUS_FRUIT, 2, 4, "Chorus Fruit (x2)"));
            pool.add(trade(Items.CAKE, 1, 5, "Cake §7(healing tile)"));
            // Hay block - heals an ally pet.
            pool.add(trade(Items.HAY_BLOCK, 1, 4, "Hay Block §7(heal ally pet)"));
        }
    }

    // ---- ALCHEMIST ----
    private static void buildAlchemistTrades(List<Trade> pool, int tier) {
        pool.add(trade(Items.GLASS_BOTTLE, 3, 1, "Glass Bottles (x3)"));
        if (tier >= 1) {
            pool.add(potionTrade(Items.POTION, net.minecraft.potion.Potions.HEALING, 1, 3, "Potion of Healing"));
            pool.add(potionTrade(Items.POTION, net.minecraft.potion.Potions.SWIFTNESS, 1, 3, "Potion of Swiftness"));
            // Tipped arrows: poison and slowness on hit.
            pool.add(tippedArrowTrade(net.minecraft.potion.Potions.POISON, 4, 4, "Tipped Arrow: Poison (x4)"));
            pool.add(tippedArrowTrade(net.minecraft.potion.Potions.SLOWNESS, 4, 3, "Tipped Arrow: Slowness (x4)"));
        }
        if (tier >= 3) {
            pool.add(potionTrade(Items.POTION, net.minecraft.potion.Potions.STRENGTH, 1, 4, "Potion of Strength"));
            pool.add(potionTrade(Items.SPLASH_POTION, net.minecraft.potion.Potions.HARMING, 1, 5, "Splash Potion of Harming"));
            pool.add(potionTrade(Items.LINGERING_POTION, net.minecraft.potion.Potions.SLOWNESS, 1, 5, "Lingering Slowness Cloud"));
            pool.add(tippedArrowTrade(net.minecraft.potion.Potions.WEAKNESS, 4, 4, "Tipped Arrow: Weakness (x4)"));
            pool.add(tippedArrowTrade(net.minecraft.potion.Potions.HARMING, 4, 6, "Tipped Arrow: Harming (x4)"));
        }
        if (tier >= 5) {
            pool.add(potionTrade(Items.POTION, net.minecraft.potion.Potions.REGENERATION, 1, 5, "Potion of Regeneration"));
            pool.add(potionTrade(Items.POTION, net.minecraft.potion.Potions.FIRE_RESISTANCE, 1, 5, "Potion of Fire Resistance"));
            pool.add(potionTrade(Items.LINGERING_POTION, net.minecraft.potion.Potions.HARMING, 1, 8, "Lingering Harming Cloud"));
            pool.add(tippedArrowTrade(net.minecraft.potion.Potions.FIRE_RESISTANCE, 4, 5, "Tipped Arrow: Fire Resistance (x4)"));
            pool.add(tippedArrowTrade(net.minecraft.potion.Potions.HEALING, 4, 5, "Tipped Arrow: Healing (x4)"));
        }
        if (tier >= 7) {
            pool.add(potionTrade(Items.POTION, net.minecraft.potion.Potions.INVISIBILITY, 1, 7, "Potion of Invisibility"));
            pool.add(potionTrade(Items.SPLASH_POTION, net.minecraft.potion.Potions.WEAKNESS, 1, 8, "Splash Potion of Weakness"));
        }
        // Brewing ingredients + craftable alchemy reagents - also covers some
        // niche combat items (spider eye for risky food, ghast tear for regen,
        // dragon's breath for lingering potions, glistering melon for healing).
        pool.add(trade(Items.NETHER_WART, 3, 2, "Nether Wart (x3)"));
        pool.add(trade(Items.BLAZE_POWDER, 2, 3, "Blaze Powder (x2)"));
        if (tier >= 2) {
            pool.add(trade(Items.SPIDER_EYE, 2, 2, "Spider Eyes (x2)"));
            pool.add(trade(Items.FERMENTED_SPIDER_EYE, 1, 3, "Fermented Spider Eye"));
            pool.add(trade(Items.MAGMA_CREAM, 2, 3, "Magma Cream (x2)"));
        }
        if (tier >= 4) {
            pool.add(trade(Items.GHAST_TEAR, 1, 5, "Ghast Tear"));
            pool.add(trade(Items.RABBIT_FOOT, 1, 4, "Rabbit's Foot"));
            pool.add(trade(Items.GLISTERING_MELON_SLICE, 1, 4, "Glistering Melon"));
        }
        if (tier >= 6) {
            pool.add(trade(Items.DRAGON_BREATH, 1, 8, "§5Dragon's Breath"));
        }
    }

    // ---- SUPPLIER ----
    private static void buildSupplierTrades(List<Trade> pool, int tier) {
        pool.add(trade(Items.OAK_PLANKS, 3, 2, "Oak Planks (x3)"));
        pool.add(trade(Items.COBBLESTONE, 3, 3, "Cobblestone (x3)"));
        pool.add(trade(Items.STICK, 3, 1, "Sticks (x3)"));
        if (tier >= 2) {
            pool.add(trade(Items.IRON_INGOT, 2, 4, "Iron Ingots (x2)"));
            pool.add(trade(Items.COAL, 3, 2, "Coal (x3)"));
        }
        if (tier >= 3) {
            pool.add(trade(Items.IRON_INGOT, 3, 6, "Iron Ingots (x3)"));
            pool.add(trade(Items.GOLD_INGOT, 2, 5, "Gold Ingots (x2)"));
        }
        if (tier >= 5) {
            pool.add(trade(Items.REDSTONE, 3, 4, "Redstone (x3)"));
            pool.add(trade(Items.LAPIS_LAZULI, 2, 4, "Lapis Lazuli (x2)"));
        }
        if (tier >= 7) {
            pool.add(trade(Items.DIAMOND, 1, 10, "Diamond"));
        }
        if (tier >= 9) {
            pool.add(trade(Items.NETHERITE_SCRAP, 1, 18, "Netherite Scrap"));
        }
        // Mounting + pet supplies.
        pool.add(trade(Items.SADDLE, 1, 6, "Saddle §7(mount allies)"));
        if (tier >= 3) {
            pool.add(trade(Items.LEATHER_HORSE_ARMOR, 1, 4, "Leather Horse Armor"));
            pool.add(trade(Items.IRON_HORSE_ARMOR, 1, 7, "Iron Horse Armor"));
            pool.add(trade(Items.WOLF_ARMOR, 1, 8, "Wolf Armor"));
        }
        if (tier >= 6) {
            pool.add(trade(Items.GOLDEN_HORSE_ARMOR, 1, 10, "Golden Horse Armor"));
            pool.add(trade(Items.DIAMOND_HORSE_ARMOR, 1, 14, "§bDiamond Horse Armor"));
        }
        // Breeding / taming staples.
        pool.add(trade(Items.BONE, 4, 2, "Bones (x4) §7(tame wolves)"));
        pool.add(trade(Items.WHEAT, 4, 2, "Wheat (x4) §7(befriend mobs)"));
    }

    // ---- DECORATOR ----
    private static void buildDecoratorTrades(List<Trade> pool, int tier) {
        pool.add(trade(Items.PAINTING, 1, 3, "Painting"));
        pool.add(trade(Items.ITEM_FRAME, 2, 3, "Item Frames (x2)"));
        pool.add(trade(Items.FLOWER_POT, 2, 2, "Flower Pots (x2)"));
        pool.add(trade(Items.LANTERN, 2, 3, "Lanterns (x2)"));
        pool.add(trade(Items.BOOKSHELF, 1, 4, "Bookshelf"));
        pool.add(trade(Items.RED_CARPET, 3, 2, "Carpet (x3)"));
        pool.add(trade(Items.WHITE_BED, 1, 4, "Bed"));

        // Biome-themed decorations
        if (tier <= 2) {
            pool.add(trade(Items.OAK_FENCE, 3, 3, "Oak Fence (x3)"));
        }
        // Decorator's florist line - every single-block flower in the game,
        // each in a small bundle (3-7) so players can fill out a bed or stash.
        {
            int n = 3 + (tier % 5); // deterministic bundle size 3..7 per tier
            pool.add(trade(Items.DANDELION,            n, 2, "Dandelions (x" + n + ")"));
            pool.add(trade(Items.POPPY,                n, 2, "Poppies (x" + n + ")"));
            pool.add(trade(Items.BLUE_ORCHID,          n, 3, "Blue Orchids (x" + n + ")"));
            pool.add(trade(Items.ALLIUM,               n, 3, "Alliums (x" + n + ")"));
            pool.add(trade(Items.AZURE_BLUET,          n, 2, "Azure Bluets (x" + n + ")"));
            pool.add(trade(Items.RED_TULIP,            n, 2, "Red Tulips (x" + n + ")"));
            pool.add(trade(Items.ORANGE_TULIP,         n, 2, "Orange Tulips (x" + n + ")"));
            pool.add(trade(Items.WHITE_TULIP,          n, 2, "White Tulips (x" + n + ")"));
            pool.add(trade(Items.PINK_TULIP,           n, 2, "Pink Tulips (x" + n + ")"));
            pool.add(trade(Items.OXEYE_DAISY,          n, 2, "Oxeye Daisies (x" + n + ")"));
            pool.add(trade(Items.CORNFLOWER,           n, 3, "Cornflowers (x" + n + ")"));
            pool.add(trade(Items.LILY_OF_THE_VALLEY,   n, 3, "Lily of the Valley (x" + n + ")"));
            pool.add(trade(Items.WITHER_ROSE,          n, 6, "§8Wither Roses (x" + n + ")"));
            pool.add(trade(Items.TORCHFLOWER,          n, 5, "§6Torchflowers (x" + n + ")"));
            pool.add(trade(Items.FLOWERING_AZALEA,     n, 4, "Flowering Azaleas (x" + n + ")"));
        }
        if (tier >= 3 && tier <= 4) {
            pool.add(trade(Items.BLUE_ICE, 2, 4, "Blue Ice (x2)"));
            pool.add(trade(Items.SPRUCE_PLANKS, 3, 2, "Spruce Planks (x3)"));
        }
        if (tier >= 5 && tier <= 6) {
            pool.add(trade(Items.SANDSTONE, 3, 3, "Sandstone (x3)"));
            pool.add(trade(Items.TERRACOTTA, 3, 3, "Terracotta (x3)"));
        }
        if (tier >= 7) {
            pool.add(trade(Items.GLOWSTONE, 2, 4, "Glowstone (x2)"));
            pool.add(trade(Items.SEA_LANTERN, 2, 5, "Sea Lanterns (x2)"));
        }
        // Combat-useful placed/terrain items the player can deploy on the
        // arena. Each one has a tooltip-described mechanic; this pool makes
        // them findable rather than craft-only.
        if (tier >= 2) {
            pool.add(trade(Items.WATER_BUCKET, 1, 4, "Water Bucket §7(place water tile)"));
            pool.add(trade(Items.CAMPFIRE, 1, 4, "Campfire §7(heal zone)"));
            pool.add(trade(Items.LIGHTNING_ROD, 1, 5, "Lightning Rod §7(AoE strike)"));
            pool.add(trade(Items.SCAFFOLDING, 2, 3, "Scaffolding (x2) §7(+range tile)"));
            pool.add(trade(Items.CACTUS, 2, 3, "Cactus (x2) §7(prick trap)"));
        }
        if (tier >= 4) {
            pool.add(trade(Items.LAVA_BUCKET, 1, 8, "§6Lava Bucket §7(place lava)"));
            pool.add(trade(Items.HONEY_BLOCK, 1, 5, "Honey Block §7(slow trap)"));
            pool.add(trade(Items.SLIME_BLOCK, 1, 5, "Slime Block §7(bounce trap)"));
            pool.add(trade(Items.POWDER_SNOW_BUCKET, 1, 6, "§bPowder Snow §7(freeze)"));
            pool.add(trade(Items.SPONGE, 1, 4, "Sponge §7(absorb water)"));
            pool.add(trade(Items.SPORE_BLOSSOM, 1, 5, "Spore Blossom §7(AoE slow)"));
            pool.add(trade(Items.JUKEBOX, 1, 6, "Jukebox §7(+1 ally speed)"));
            pool.add(trade(Items.BELL, 1, 6, "Bell §7(stun AoE)"));
        }
        if (tier >= 6) {
            pool.add(trade(Items.ANVIL, 1, 10, "Anvil §7(drop attack)"));
        }
        // Fishing rod - always available; cheap utility.
        pool.add(trade(Items.FISHING_ROD, 1, 4, "Fishing Rod §7(cast for loot)"));
        // White banner - defense zone planter.
        pool.add(trade(Items.WHITE_BANNER, 1, 4, "Banner §7(defense zone)"));
    }

    // ---- CRAFTSMAN ----
    private static void buildCraftsmanTrades(List<Trade> pool, int tier) {
        // Basic workstations - always available
        pool.add(trade(Items.CRAFTING_TABLE, 1, 2, "Crafting Table"));
        pool.add(trade(Items.FURNACE, 1, 3, "Furnace"));
        pool.add(trade(Items.CHEST, 1, 2, "Chest"));
        pool.add(trade(Items.SMOKER, 1, 3, "Smoker"));
        pool.add(trade(Items.BLAST_FURNACE, 1, 4, "Blast Furnace"));
        pool.add(trade(Items.STONECUTTER, 1, 3, "Stonecutter"));
        pool.add(trade(Items.LOOM, 1, 3, "Loom"));

        if (tier >= 3) {
            pool.add(trade(Items.SMITHING_TABLE, 1, 6, "Smithing Table"));
            pool.add(trade(Items.ANVIL, 1, 8, "Anvil"));
            pool.add(trade(Items.GRINDSTONE, 1, 5, "Grindstone"));
            pool.add(trade(Items.CARTOGRAPHY_TABLE, 1, 4, "Cartography Table"));
        }
        if (tier >= 4) {
            pool.add(trade(Items.BREWING_STAND, 1, 8, "Brewing Stand"));
            pool.add(trade(Items.CAULDRON, 1, 5, "Cauldron"));
            pool.add(trade(Items.COMPOSTER, 1, 3, "Composter"));
        }
        if (tier >= 5) {
            pool.add(trade(Items.ENCHANTING_TABLE, 1, 12, "§bEnchanting Table"));
            pool.add(trade(Items.BOOKSHELF, 3, 6, "Bookshelves (x3)"));
        }
        if (tier >= 7) {
            pool.add(trade(Items.ENDER_CHEST, 1, 14, "§dEnder Chest"));
        }
        if (tier >= 8) {
            pool.add(trade(Items.BEACON, 1, 30, "§6Beacon"));
        }
    }

    // ---- CURIOSITY DEALER ----
    private static void buildCuriosityTrades(List<Trade> pool, int tier) {
        pool.add(trade(Items.ENDER_PEARL, 1, 5, "Ender Pearl"));
        pool.add(trade(Items.TNT, 1, 5, "TNT"));
        pool.add(trade(Items.NAME_TAG, 1, 4, "Name Tag"));
        pool.add(trade(Items.SNOWBALL, 3, 3, "Snowballs (x3)"));
        pool.add(trade(Items.EGG, 3, 3, "Eggs (x3)"));
        // Lead - commands allies in combat. Available from tier 1 because
        // it's a core utility tool and easy to miss otherwise.
        pool.add(trade(Items.LEAD, 1, 4, "Lead §7(ally command)"));
        // Cobweb - single-target stun throwable.
        pool.add(trade(Items.COBWEB, 2, 3, "Cobwebs (x2) §7(stun)"));
        // Bundle - passive loot collector. Vanilla bundle item only exists on
        // 1.21.2+, so skip the trade on shards that don't ship it.
        //? if >=1.21.2 {
        pool.add(trade(Items.BUNDLE, 1, 5, "Bundle §7(auto loot)"));
        //?}
        // Flint & Steel - fire-and-forget ignite tool.
        pool.add(trade(Items.FLINT_AND_STEEL, 1, 4, "Flint & Steel"));

        if (tier >= 3) {
            pool.add(trade(Items.SPYGLASS, 1, 5, "Spyglass"));
            pool.add(trade(Items.COMPASS, 1, 3, "Compass"));
            pool.add(trade(Items.BRUSH, 1, 4, "Brush §7(excavate)"));
            pool.add(trade(Items.GOAT_HORN, 1, 6, "§eGoat Horn §7(random variant)"));
            // Pottery sherds - random spell scrolls. Pool of common sherds.
            pool.add(trade(Items.HEART_POTTERY_SHERD, 1, 6, "§dHeart Sherd §7(heal+regen)"));
            pool.add(trade(Items.FRIEND_POTTERY_SHERD, 1, 5, "§dFriend Sherd §7(buff ally)"));
            pool.add(trade(Items.SCRAPE_POTTERY_SHERD, 1, 5, "§dScrape Sherd §7(corrode)"));
            // Heart of the Sea - water AoE.
            pool.add(trade(Items.NAUTILUS_SHELL, 2, 5, "Nautilus Shells (x2) §3(water AoE)"));
        }
        if (tier >= 5) {
            pool.add(trade(Items.FIRE_CHARGE, 4, 3, "Fire Charges (x4)"));
            pool.add(trade(Items.RECOVERY_COMPASS, 1, 12, "§6Recovery Compass §7(save inventory)"));
            pool.add(trade(Items.ECHO_SHARD, 2, 5, "Echo Shards (x2) §7(echo TP)"));
            // Rarer sherds at higher tier.
            pool.add(trade(Items.MINER_POTTERY_SHERD, 1, 7, "§dMiner Sherd §7(earth spike)"));
            pool.add(trade(Items.ARMS_UP_POTTERY_SHERD, 1, 8, "§dArms Up Sherd §7(war cry)"));
            pool.add(trade(Items.BURN_POTTERY_SHERD, 1, 8, "§dBurn Sherd §7(immolation)"));
            pool.add(trade(Items.HEART_OF_THE_SEA, 1, 12, "§3Heart of the Sea §7(water AoE)"));
        }
        if (tier >= 7) {
            pool.add(trade(Items.TOTEM_OF_UNDYING, 1, 18, "§6Totem of Undying"));
            // Top-tier sherds - finishers and rare effects.
            pool.add(trade(Items.SKULL_POTTERY_SHERD, 1, 12, "§4Skull Sherd §7(execute/wither)"));
            pool.add(trade(Items.PRIZE_POTTERY_SHERD, 1, 12, "§6Prize Sherd §7(triple dmg)"));
            pool.add(trade(Items.BLADE_POTTERY_SHERD, 1, 12, "§dBlade Sherd §7(phantom slash)"));
            // Mob skulls - equip for +1 damage type bonus.
            pool.add(trade(Items.SKELETON_SKULL, 1, 14, "§bSkeleton Skull §7(+1 Ranged)"));
            pool.add(trade(Items.ZOMBIE_HEAD, 1, 14, "§7Zombie Head §7(+1 Physical)"));
            pool.add(trade(Items.CREEPER_HEAD, 1, 14, "§2Creeper Head §7(+1 Blunt)"));
            pool.add(trade(Items.PIGLIN_HEAD, 1, 14, "§cPiglin Head §7(+1 Slashing)"));
        }
        if (tier >= 9) {
            pool.add(trade(Items.ELYTRA, 1, 25, "§5Elytra"));
            pool.add(trade(Items.WITHER_SKELETON_SKULL, 1, 22, "§8Wither Skull §7(+1 Special)"));
            pool.add(trade(Items.TRIAL_KEY, 1, 10, "§eTrial Key"));
            pool.add(trade(Items.OMINOUS_TRIAL_KEY, 1, 20, "§4Ominous Trial Key"));
        }

        // Instruments compat: a few random performance instruments at mid/high tier. These
        // are Special-affinity combat weapons whose craft materials the void hub can't
        // reliably supply, so the Curiosity Dealer is their acquisition channel. The trader
        // shuffles its whole pool and shows 3-5, so adding a few here surfaces different
        // instruments across visits; over time all are reachable. No-ops if no instrument mod.
        if (tier >= 5) {
            for (Item inst : com.crackedgames.craftics.compat.instruments.InstrumentsLootRoller.rollSome(3)) {
                pool.add(trade(inst, 1, 9, "§5" + inst.getName().getString() + " §7(performance)"));
            }
        }

        // MoreTotems compat: a random modded totem at high tier (matches vanilla Totem
        // at tier 7, priced just above it). No-ops when the mod is absent (rollOne EMPTY).
        if (com.crackedgames.craftics.compat.moretotems.MoreTotemsCompat.isLoaded() && tier >= 7) {
            net.minecraft.item.ItemStack totem =
                com.crackedgames.craftics.compat.moretotems.MoreTotemsLootRoller.rollOne();
            if (!totem.isEmpty()) {
                pool.add(new Trade(totem, 20, "§d" + totem.getName().getString()));
            }
        }
    }

    private static Trade trade(Item item, int count, int cost, String desc) {
        return new Trade(new ItemStack(item, count), cost, desc);
    }

    private static Trade potionTrade(Item potionItem, net.minecraft.registry.entry.RegistryEntry<net.minecraft.potion.Potion> potionType,
                                     int count, int cost, String desc) {
        ItemStack stack = new ItemStack(potionItem, count);
        stack.set(net.minecraft.component.DataComponentTypes.POTION_CONTENTS,
            new net.minecraft.component.type.PotionContentsComponent(potionType));
        return new Trade(stack, cost, desc);
    }

    /** Tipped arrow stack with the given potion embedded. */
    private static Trade tippedArrowTrade(net.minecraft.registry.entry.RegistryEntry<net.minecraft.potion.Potion> potionType,
                                          int count, int cost, String desc) {
        ItemStack stack = new ItemStack(Items.TIPPED_ARROW, count);
        stack.set(net.minecraft.component.DataComponentTypes.POTION_CONTENTS,
            new net.minecraft.component.type.PotionContentsComponent(potionType));
        return new Trade(stack, cost, desc);
    }
}
