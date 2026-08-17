package com.crackedgames.craftics.auction;

import com.crackedgames.craftics.screen.ReadOnlyMenuScreenHandler;
import com.crackedgames.craftics.world.CrafticsSavedData;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;


/**
 * The auction board as a chest screen: a page of listings you click to buy, and a confirm
 * step before anything is spent.
 *
 * <p>Built the same way the lootbox menus are - a plain chest inventory of display icons
 * wrapped in {@link ReadOnlyMenuScreenHandler}, with a click router. No custom screen, no new
 * packets, and the menu region can never be taken from or put into, so the screen itself can
 * never become a way to move an item.
 *
 * <p>The icons are copies for display only. Buying goes through {@link AuctionStore#claim},
 * exactly as the command does, so the rules live in one place regardless of how it was
 * clicked.
 */
public final class AuctionMenus {

    private AuctionMenus() {}

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    /** Top five rows hold listings; the last row is navigation. */
    private static final int PER_PAGE = 45;

    private static final int SLOT_PREV = 45;
    private static final int SLOT_PAGE = 49;
    private static final int SLOT_MINE = 51;
    private static final int SLOT_COLLECT = 52;
    private static final int SLOT_NEXT = 53;

    private static final int CONFIRM_SIZE = 27;
    private static final int CONFIRM_ITEM = 4;
    private static final int CONFIRM_BUY = 11;
    private static final int CONFIRM_BACK = 15;

    // ── Browse ───────────────────────────────────────────────────────────────

    public static void openBrowse(ServerPlayerEntity player, int page) {
        openList(player, AuctionStore.sortedByPrice(), page, false,
            Text.literal("§6Auction"));
    }

    public static void openMine(ServerPlayerEntity player, int page) {
        openList(player, AuctionStore.bySeller(player.getUuid()), page, true,
            Text.literal("§6Your listings"));
    }

    private static void openList(ServerPlayerEntity player, List<AuctionListing> listings,
                                 int page, boolean ownListings, Text title) {
        int pages = Math.max(1, (listings.size() + PER_PAGE - 1) / PER_PAGE);
        int shown = Math.max(0, Math.min(page, pages - 1));
        int from = shown * PER_PAGE;
        int to = Math.min(listings.size(), from + PER_PAGE);
        List<AuctionListing> onPage = new ArrayList<>(listings.subList(from, to));

        SimpleInventory inv = new SimpleInventory(SIZE);
        for (int i = 0; i < onPage.size(); i++) {
            AuctionListing listing = onPage.get(i);
            ItemStack icon = AuctionItems.fromNbt(player, listing.stack());
            if (icon.isEmpty()) {
                // The item's mod is gone. Show it as a placeholder rather than a hole, so the
                // seller can still find and cancel it.
                icon = new ItemStack(Items.BARRIER);
                setDisplay(icon, "§c" + listing.displayName() + " §7(mod not installed)", List.of());
                inv.setStack(i, icon);
                continue;
            }
            icon = icon.copy();
            List<String> lore = new ArrayList<>();
            lore.add(priceLine(listing));
            lore.add("§8Seller: " + listing.sellerName());
            lore.add(ownListings ? "§eClick to cancel" : "§eClick to buy");
            setDisplay(icon, "§f" + listing.count() + "x " + listing.displayName(), lore);
            inv.setStack(i, icon);
        }

        if (shown > 0) {
            setIcon(inv, SLOT_PREV, Items.ARROW, "§fPrevious page", List.of());
        }
        if (shown < pages - 1) {
            setIcon(inv, SLOT_NEXT, Items.ARROW, "§fNext page", List.of());
        }
        setIcon(inv, SLOT_PAGE, Items.PAPER, "§7Page " + (shown + 1) + " of " + pages,
            List.of("§8" + listings.size() + " listings"));
        setIcon(inv, SLOT_MINE, Items.NAME_TAG,
            ownListings ? "§fBack to the board" : "§fYour listings",
            List.of(ownListings ? "§8Everything for sale" : "§8What you have up for sale"));
        int owed = AuctionStore.mailboxSize(player.getUuid());
        setIcon(inv, SLOT_COLLECT, owed > 0 ? Items.CHEST : Items.BARREL,
            owed > 0 ? "§aCollect " + owed + " item" + (owed == 1 ? "" : "s") : "§7Nothing to collect",
            List.of("§8Items bought, cancelled or expired"));

        final int currentPage = shown;
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, viewer) -> new ReadOnlyMenuScreenHandler(
                ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inv, ROWS,
                slot -> onListClick(player, onPage, slot, currentPage, ownListings)),
            title));
    }

    private static void onListClick(ServerPlayerEntity player, List<AuctionListing> onPage,
                                    int slot, int page, boolean ownListings) {
        switch (slot) {
            case SLOT_PREV -> { reopen(player, page - 1, ownListings); return; }
            case SLOT_NEXT -> { reopen(player, page + 1, ownListings); return; }
            case SLOT_MINE -> { reopen(player, 0, !ownListings); return; }
            case SLOT_COLLECT -> { collect(player); reopen(player, page, ownListings); return; }
            case SLOT_PAGE -> { return; }
            default -> { }
        }
        if (slot < 0 || slot >= onPage.size()) return;
        AuctionListing listing = onPage.get(slot);
        if (ownListings) {
            cancel(player, listing);
            reopen(player, page, true);
            return;
        }
        openConfirm(player, listing, page);
    }

    private static void reopen(ServerPlayerEntity player, int page, boolean ownListings) {
        // Deferred a tick: closing the current screen and opening the next in the same call
        // leaves the client showing the old one.
        player.getServer().execute(() -> {
            if (ownListings) openMine(player, page); else openBrowse(player, page);
        });
    }

    // ── Confirm ──────────────────────────────────────────────────────────────

    /** Never buys on a single click. Price, balance, and what you get, before anything moves. */
    private static void openConfirm(ServerPlayerEntity player, AuctionListing listing, int page) {
        CrafticsSavedData data = CrafticsSavedData.get((ServerWorld) player.getEntityWorld());
        int balance = data.getPlayerData(player.getUuid()).emeralds;
        boolean affordable = listing.isBarter()
            ? AuctionItems.countPlain(player, listing.wantedItemId()) >= listing.wantedCount()
            : balance >= listing.emeraldPrice();

        SimpleInventory inv = new SimpleInventory(CONFIRM_SIZE);
        ItemStack icon = AuctionItems.fromNbt(player, listing.stack());
        if (icon.isEmpty()) icon = new ItemStack(Items.BARRIER);
        icon = icon.copy();
        setDisplay(icon, "§f" + listing.count() + "x " + listing.displayName(),
            List.of(priceLine(listing), "§8Seller: " + listing.sellerName()));
        inv.setStack(CONFIRM_ITEM, icon);

        if (affordable) {
            setIcon(inv, CONFIRM_BUY, Items.LIME_DYE, "§a§lBuy it",
                List.of(listing.isBarter()
                    ? "§7Hands over " + listing.wantedCount() + "x "
                        + strip(listing.wantedItemId())
                    : "§7Spends §f" + listing.emeraldPrice() + "§7 emeralds",
                    "§8You have " + balance + " emeralds"));
        } else {
            setIcon(inv, CONFIRM_BUY, Items.GRAY_DYE, "§8Cannot afford this",
                List.of(listing.isBarter()
                    ? "§7Needs " + listing.wantedCount() + "x " + strip(listing.wantedItemId())
                        + " §8(plain ones only)"
                    : "§7Needs §f" + (listing.emeraldPrice() - balance) + "§7 more emeralds"));
        }
        setIcon(inv, CONFIRM_BACK, Items.BARRIER, "§c§lBack", List.of("§7Buys nothing."));

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, viewer) -> new ReadOnlyMenuScreenHandler(
                ScreenHandlerType.GENERIC_9X3, syncId, playerInv, inv, CONFIRM_SIZE / 9,
                slot -> {
                    if (slot == CONFIRM_BUY) {
                        buy(player, listing);
                        reopen(player, page, false);
                    } else if (slot == CONFIRM_BACK) {
                        reopen(player, page, false);
                    }
                }),
            Text.literal("§6Buy this?")));
    }

    // ── Actions. All of them go through AuctionStore, same as the commands ────

    private static void buy(ServerPlayerEntity player, AuctionListing preview) {
        AuctionCommands.buyListing(player, preview);
    }

    private static void cancel(ServerPlayerEntity player, AuctionListing preview) {
        AuctionCommands.cancelListing(player, preview);
    }

    private static void collect(ServerPlayerEntity player) {
        AuctionCommands.collectMailbox(player);
    }

    // ── Icons ────────────────────────────────────────────────────────────────

    private static String priceLine(AuctionListing listing) {
        return listing.isBarter()
            ? "§bWants " + listing.wantedCount() + "x " + strip(listing.wantedItemId())
            : "§a" + listing.emeraldPrice() + " emeralds";
    }

    private static String strip(String itemId) {
        return itemId == null ? "" : itemId.replace("minecraft:", "");
    }

    private static void setDisplay(ItemStack stack, String name, List<String> lore) {
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        stack.set(DataComponentTypes.LORE,
            new LoreComponent(lore.stream().<Text>map(Text::literal).toList()));
    }

    private static void setIcon(SimpleInventory inv, int slot, net.minecraft.item.Item item,
                                String name, List<String> lore) {
        ItemStack stack = new ItemStack(item);
        stack.setCount(1);
        setDisplay(stack, name, lore);
        com.crackedgames.craftics.screen.MenuIcons.mark(stack);
        inv.setStack(slot, stack);
    }

}
