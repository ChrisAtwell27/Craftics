package com.crackedgames.craftics.combat.barter;

import com.crackedgames.craftics.api.registry.BarterCategoryRegistry;
import com.crackedgames.craftics.api.registry.BarterRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Registers the five built-in piglin barter categories and their reward pools. Called once from
 * {@code VanillaContent}. Addon curios are added to {@code craftics:relic_trader} separately by
 * compat modules so this class has no mod-detection branches.
 */
public final class VanillaBarterContent {

    public static final String WARMONGER    = "craftics:warmonger";
    public static final String HOARDER      = "craftics:hoarder";
    public static final String FLESH_DEALER = "craftics:flesh_dealer";
    public static final String RELIC_TRADER = "craftics:relic_trader";
    public static final String BEAST_TAMER  = "craftics:beast_tamer";

    private VanillaBarterContent() {}

    public static void register() {
        registerCategories();
        registerWarmonger();
        registerHoarder();
        registerFleshDealer();
        registerRelicTrader();
        registerBeastTamer();
    }

    private static void registerCategories() {
        BarterCategoryRegistry.register(new BarterCategory(WARMONGER,    "Warmonger",    "§c⚔", "weapons of war",        0));
        BarterCategoryRegistry.register(new BarterCategory(HOARDER,      "Hoarder",      "§b♦", "glittering treasures",  0));
        BarterCategoryRegistry.register(new BarterCategory(FLESH_DEALER, "Flesh Dealer", "§a⚘", "food and foul brews",  0));
        BarterCategoryRegistry.register(new BarterCategory(RELIC_TRADER, "Relic Trader", "§5✦", "rare curiosities",      0));
        BarterCategoryRegistry.register(new BarterCategory(BEAST_TAMER,  "Beast Tamer",  "§6♞", "beasts of the Nether", 0));
    }

    private static void registerWarmonger() {
        BarterRegistry.register(new BarterEntry(WARMONGER, new ItemStack(Items.GOLDEN_SWORD), 5, 0));
        BarterRegistry.register(new BarterEntry(WARMONGER, new ItemStack(Items.IRON_SWORD), 5, 0));
        BarterRegistry.register(new BarterEntry(WARMONGER, new ItemStack(Items.CROSSBOW), 4, 0));
        BarterRegistry.register(new BarterEntry(WARMONGER, new ItemStack(Items.ARROW), 8, 16, 6, 0));
        BarterRegistry.register(new BarterEntry(WARMONGER, new ItemStack(Items.SHIELD), 3, 1));
        BarterRegistry.register(new BarterEntry(WARMONGER, new ItemStack(Items.IRON_CHESTPLATE), 3, 2));
        BarterRegistry.register(new BarterEntry(WARMONGER, new ItemStack(Items.DIAMOND_SWORD), 2, 3));
        BarterRegistry.register(new BarterEntry(WARMONGER, new ItemStack(Items.DIAMOND_CHESTPLATE), 1, 4));
    }

    private static void registerHoarder() {
        // Gems, never gold. Diamonds 6-12, emeralds 16-32, iron as lower filler.
        BarterRegistry.register(new BarterEntry(HOARDER, new ItemStack(Items.IRON_INGOT), 8, 16, 6, 0));
        BarterRegistry.register(new BarterEntry(HOARDER, new ItemStack(Items.EMERALD), 16, 32, 5, 0));
        BarterRegistry.register(new BarterEntry(HOARDER, new ItemStack(Items.DIAMOND), 6, 12, 4, 1));
        BarterRegistry.register(new BarterEntry(HOARDER, new ItemStack(Items.NETHERITE_SCRAP), 1, 2, 2, 4));
    }

    private static void registerFleshDealer() {
        BarterRegistry.register(new BarterEntry(FLESH_DEALER, new ItemStack(Items.COOKED_PORKCHOP), 4, 8, 6, 0));
        BarterRegistry.register(new BarterEntry(FLESH_DEALER, new ItemStack(Items.BLAZE_POWDER), 2, 4, 5, 0));
        BarterRegistry.register(new BarterEntry(FLESH_DEALER, new ItemStack(Items.GHAST_TEAR), 1, 2, 3, 2));
        BarterRegistry.register(new BarterEntry(FLESH_DEALER, new ItemStack(Items.GOLDEN_APPLE), 2, 3));
        BarterRegistry.register(new BarterEntry(FLESH_DEALER, new ItemStack(Items.POTION), 4, 1));
    }

    private static void registerRelicTrader() {
        // Nether-themed combat consumables with real uses.
        BarterRegistry.register(new BarterEntry(RELIC_TRADER, new ItemStack(Items.FIRE_CHARGE), 2, 4, 6, 0));
        BarterRegistry.register(new BarterEntry(RELIC_TRADER, new ItemStack(Items.BLAZE_ROD), 1, 3, 5, 0));
        // Classic rarities.
        BarterRegistry.register(new BarterEntry(RELIC_TRADER, new ItemStack(Items.MUSIC_DISC_PIGSTEP), 2, 1));
        BarterRegistry.register(new BarterEntry(RELIC_TRADER, new ItemStack(Items.LODESTONE), 2, 2));
        BarterRegistry.register(new BarterEntry(RELIC_TRADER, new ItemStack(Items.ANCIENT_DEBRIS), 1, 5));
        // Addon curios are appended here by compat modules (a later task).
    }

    private static void registerBeastTamer() {
        BarterRegistry.register(new BarterEntry(BEAST_TAMER, new ItemStack(Items.STRIDER_SPAWN_EGG), 5, 0));
        BarterRegistry.register(new BarterEntry(BEAST_TAMER, new ItemStack(Items.HOGLIN_SPAWN_EGG), 4, 1));
        BarterRegistry.register(new BarterEntry(BEAST_TAMER, new ItemStack(Items.ZOGLIN_SPAWN_EGG), 2, 3));
    }
}
