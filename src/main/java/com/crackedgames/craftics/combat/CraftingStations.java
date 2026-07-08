package com.crackedgames.craftics.combat;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.CartographyTableScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.GrindstoneScreenHandler;
import net.minecraft.screen.LoomScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.screen.StonecutterScreenHandler;
import net.minecraft.text.Text;

/**
 * Crafting-station blocks that can be opened mid-battle. Holding one of these
 * and taking a USE_ITEM action spends {@link #AP_COST} AP and opens that
 * station's vanilla UI for the acting player (no block is placed, the item is
 * not consumed).
 *
 * <p>Deliberately excludes furnaces and every other smelter (furnace, blast
 * furnace, smoker) - those process over time and don't fit a single-action
 * open. Anvils and brewing stands are excluded too.
 *
 * <p>Single source of truth: the server ({@code CombatManager.handleUseItem})
 * opens the screen, the client input handler ({@code CombatInputHandler}) uses
 * it to pick the USE_ITEM action mode, and the tooltip layer describes it.
 */
public final class CraftingStations {

    private CraftingStations() {}

    /** AP a station open costs. */
    public static final int AP_COST = 1;

    public enum Station {
        CRAFTING(Items.CRAFTING_TABLE, "Crafting Table", "container.crafting"),
        SMITHING(Items.SMITHING_TABLE, "Smithing Table", "container.upgrade"),
        LOOM(Items.LOOM, "Loom", "container.loom"),
        STONECUTTER(Items.STONECUTTER, "Stonecutter", "container.stonecutter"),
        GRINDSTONE(Items.GRINDSTONE, "Grindstone", "container.grindstone_title"),
        CARTOGRAPHY(Items.CARTOGRAPHY_TABLE, "Cartography Table", "container.cartography_table"),
        ENCHANTING(Items.ENCHANTING_TABLE, "Enchanting Table", "container.enchant");

        private final Item item;
        private final String label;
        private final String titleKey;

        Station(Item item, String label, String titleKey) {
            this.item = item;
            this.label = label;
            this.titleKey = titleKey;
        }

        public Item item() { return item; }
        public String label() { return label; }

        /**
         * Build the vanilla screen factory for this station, bound to {@code ctx}
         * (the acting player's position). The context drives on-close item return:
         * anything left in the station's working slots drops at the player's tile,
         * where they immediately pick it back up - so nothing is lost.
         */
        public NamedScreenHandlerFactory factory(ScreenHandlerContext ctx) {
            return new SimpleNamedScreenHandlerFactory((syncId, inv, viewer) -> switch (this) {
                case CRAFTING    -> new CraftingScreenHandler(syncId, inv, ctx);
                case SMITHING    -> new SmithingScreenHandler(syncId, inv, ctx);
                case LOOM        -> new LoomScreenHandler(syncId, inv, ctx);
                case STONECUTTER -> new StonecutterScreenHandler(syncId, inv, ctx);
                case GRINDSTONE  -> new GrindstoneScreenHandler(syncId, inv, ctx);
                case CARTOGRAPHY -> new CartographyTableScreenHandler(syncId, inv, ctx);
                case ENCHANTING  -> new EnchantmentScreenHandler(syncId, inv, ctx);
            }, Text.translatable(titleKey));
        }
    }

    /** The station backed by {@code item}, or {@code null} if it isn't one. */
    public static Station of(Item item) {
        if (item == null) return null;
        for (Station s : Station.values()) {
            if (s.item == item) return s;
        }
        return null;
    }

    public static boolean isStation(Item item) {
        return of(item) != null;
    }
}
