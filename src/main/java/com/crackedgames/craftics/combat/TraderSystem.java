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
        ARMORER("Armorer", "§9🛡"),
        PROVISIONER("Provisioner", "§a🍖"),
        ALCHEMIST("Alchemist", "§d⚗"),
        SUPPLIER("Supplier", "§e📦"),
        DECORATOR("Decorator", "§b🏠"),
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

        // Scale prices by tier — later biomes cost significantly more
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
        }
        if (tier >= 9) {
            pool.add(trade(Items.ENCHANTED_GOLDEN_APPLE, 1, 25, "§6Enchanted Golden Apple"));
        }
        pool.add(trade(Items.MILK_BUCKET, 1, 4, "Milk Bucket (clears effects)"));
    }

    // ---- ALCHEMIST ----
    private static void buildAlchemistTrades(List<Trade> pool, int tier) {
        pool.add(trade(Items.GLASS_BOTTLE, 3, 1, "Glass Bottles (x3)"));
        if (tier >= 1) {
            pool.add(potionTrade(Items.POTION, net.minecraft.potion.Potions.HEALING, 1, 3, "Potion of Healing"));
            pool.add(potionTrade(Items.POTION, net.minecraft.potion.Potions.SWIFTNESS, 1, 3, "Potion of Swiftness"));
        }
        if (tier >= 3) {
            pool.add(potionTrade(Items.POTION, net.minecraft.potion.Potions.STRENGTH, 1, 4, "Potion of Strength"));
            pool.add(potionTrade(Items.SPLASH_POTION, net.minecraft.potion.Potions.HARMING, 1, 5, "Splash Potion of Harming"));
        }
        if (tier >= 5) {
            pool.add(potionTrade(Items.POTION, net.minecraft.potion.Potions.REGENERATION, 1, 5, "Potion of Regeneration"));
            pool.add(potionTrade(Items.POTION, net.minecraft.potion.Potions.FIRE_RESISTANCE, 1, 5, "Potion of Fire Resistance"));
        }
        if (tier >= 7) {
            pool.add(potionTrade(Items.POTION, net.minecraft.potion.Potions.INVISIBILITY, 1, 7, "Potion of Invisibility"));
            pool.add(potionTrade(Items.SPLASH_POTION, net.minecraft.potion.Potions.WEAKNESS, 1, 8, "Splash Potion of Weakness"));
        }
        // Brewing ingredients
        pool.add(trade(Items.NETHER_WART, 3, 2, "Nether Wart (x3)"));
        pool.add(trade(Items.BLAZE_POWDER, 2, 3, "Blaze Powder (x2)"));
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
            pool.add(trade(Items.POPPY, 2, 2, "Flowers (x2)"));
            pool.add(trade(Items.OAK_FENCE, 3, 3, "Oak Fence (x3)"));
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
    }

    // ---- CRAFTSMAN ----
    private static void buildCraftsmanTrades(List<Trade> pool, int tier) {
        // Basic workstations — always available
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

        if (tier >= 3) {
            pool.add(trade(Items.SPYGLASS, 1, 5, "Spyglass"));
            pool.add(trade(Items.COMPASS, 1, 3, "Compass"));
        }
        if (tier >= 5) {
            pool.add(trade(Items.TRIDENT, 1, 12, "§bTrident"));
            pool.add(trade(Items.FIRE_CHARGE, 4, 3, "Fire Charges (x4)"));
        }
        if (tier >= 7) {
            pool.add(trade(Items.TOTEM_OF_UNDYING, 1, 18, "§6Totem of Undying"));
        }
        if (tier >= 9) {
            pool.add(trade(Items.ELYTRA, 1, 25, "§5Elytra"));
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
}
