package com.crackedgames.craftics.trade;

import com.crackedgames.craftics.screen.ReadOnlyMenuScreenHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The trade window, and the one tick in which a trade actually happens.
 *
 * <p>Built like the auction board: a chest of display icons behind
 * {@link ReadOnlyMenuScreenHandler}, so the menu region can never be taken from or put into and
 * the screen itself can never be a way to move an item. What it shows is both sides' offers,
 * live, with every change to either one clearing both confirmations.
 *
 * <p>The swap is the part that matters. See {@link TradeSession} for why nothing is held in
 * escrow; the consequence here is that {@link #execute} must re-check that both sides still
 * physically hold everything they offered, because an offer is only ever a promise about an
 * inventory the player has been free to keep using. It verifies both sides completely BEFORE
 * removing anything, so a trade either happens in full or does not happen at all.
 */
public final class TradeMenus {

    private TradeMenus() {}

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;

    /** Rows 0-1: what you are giving. Rows 2-3: what they are giving. */
    private static final int YOURS_FROM = 0;
    private static final int THEIRS_FROM = 18;
    /** Row 4 mirrors your hotbar: click a slot to put that stack up. */
    private static final int HOTBAR_FROM = 36;
    private static final int HOTBAR_SIZE = 9;

    private static final int SLOT_CONFIRM = 45;
    private static final int SLOT_STATUS = 49;
    private static final int SLOT_CANCEL = 53;

    // ── Opening ──────────────────────────────────────────────────────────────

    public static void open(ServerPlayerEntity player) {
        TradeSession session = TradeSession.of(player.getUuid());
        if (session == null) return;
        UUID me = player.getUuid();
        UUID them = session.other(me);

        SimpleInventory inv = new SimpleInventory(SIZE);
        List<ItemStack> mine = session.offerOf(me);
        List<ItemStack> theirs = session.offerOf(them);

        for (int i = 0; i < mine.size() && i < TradeSession.MAX_OFFER; i++) {
            ItemStack icon = mine.get(i).copy();
            decorate(icon, "§aYou give: §f" + label(icon), List.of("§eClick to take it back"));
            inv.setStack(YOURS_FROM + i, icon);
        }
        for (int i = 0; i < theirs.size() && i < TradeSession.MAX_OFFER; i++) {
            ItemStack icon = theirs.get(i).copy();
            decorate(icon, "§b" + session.nameOf(them) + " gives: §f" + label(icon), List.of());
            inv.setStack(THEIRS_FROM + i, icon);
        }

        // The hotbar mirror. Offering from a menu you cannot move items in needs some way to
        // name a stack, and the hotbar is the row a player already thinks of as "what I have
        // out". Clicking one of these copies its description into the offer; the real stack
        // stays exactly where it is.
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            ItemStack held = player.getInventory().getStack(i);
            if (held.isEmpty()) continue;
            ItemStack icon = held.copy();
            decorate(icon, "§7Hotbar " + (i + 1) + ": §f" + label(icon),
                List.of("§eClick to offer this stack"));
            inv.setStack(HOTBAR_FROM + i, icon);
        }

        boolean iConfirmed = session.confirmed(me);
        boolean theyConfirmed = session.confirmed(them);
        setIcon(inv, SLOT_CONFIRM,
            iConfirmed ? Items.LIME_CONCRETE : Items.GRAY_CONCRETE,
            iConfirmed ? "§a✔ You have accepted" : "§eClick to accept this trade",
            List.of("§8Changing either offer clears both accepts"));
        setIcon(inv, SLOT_STATUS,
            theyConfirmed ? Items.LIME_DYE : Items.GRAY_DYE,
            theyConfirmed
                ? "§a" + session.nameOf(them) + " has accepted"
                : "§7" + session.nameOf(them) + " has not accepted yet",
            List.of("§8Trading with " + session.nameOf(them)));
        setIcon(inv, SLOT_CANCEL, Items.BARRIER, "§cCancel the trade",
            List.of("§8Nothing has moved; nothing will"));

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, viewer) -> new ReadOnlyMenuScreenHandler(
                ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inv, ROWS,
                slot -> onClick(player, slot)),
            Text.literal("§6Trade with " + session.nameOf(them))));
    }

    /** Re-draw both sides. Deferred a tick: closing and re-opening in the same call leaves the
     *  client showing the old screen. */
    private static void refresh(ServerPlayerEntity player, TradeSession session) {
        var server = player.getServer();
        if (server == null) return;
        UUID them = session.other(player.getUuid());
        server.execute(() -> {
            if (session.isFinished()) return;
            open(player);
            ServerPlayerEntity partner = server.getPlayerManager().getPlayer(them);
            if (partner != null) open(partner);
        });
    }

    // ── Clicks ───────────────────────────────────────────────────────────────

    private static void onClick(ServerPlayerEntity player, int slot) {
        TradeSession session = TradeSession.of(player.getUuid());
        if (session == null) { player.closeHandledScreen(); return; }
        UUID me = player.getUuid();

        if (slot == SLOT_CANCEL) {
            cancel(player, session, "§c" + player.getName().getString() + " cancelled the trade.");
            return;
        }
        if (slot == SLOT_CONFIRM) {
            session.toggleConfirm(me);
            if (session.bothConfirmed()) {
                execute(player, session);
                return;
            }
            refresh(player, session);
            return;
        }
        if (slot >= YOURS_FROM && slot < YOURS_FROM + TradeSession.MAX_OFFER) {
            session.removeOffer(me, slot - YOURS_FROM);
            refresh(player, session);
            return;
        }
        if (slot >= HOTBAR_FROM && slot < HOTBAR_FROM + HOTBAR_SIZE) {
            ItemStack held = player.getInventory().getStack(slot - HOTBAR_FROM);
            if (held.isEmpty()) return;
            if (!session.addOffer(me, held)) {
                player.sendMessage(Text.literal("§cYour side of the trade is full."), false);
                return;
            }
            refresh(player, session);
        }
    }

    // ── The swap ─────────────────────────────────────────────────────────────

    /**
     * Perform the trade, or refuse it outright.
     *
     * <p>Both sides are verified in full before a single item is removed. An offer is a promise
     * about an inventory its owner has been free to keep using the whole time the window was
     * open - they could have eaten the bread, dropped the sword, or handed it to someone else -
     * so the promise is checked against reality at the last possible moment. If either side has
     * come up short the trade is cancelled with nothing moved, which is the only outcome that
     * cannot invent or destroy an item.
     */
    private static void execute(ServerPlayerEntity clicker, TradeSession session) {
        var server = clicker.getServer();
        if (server == null) return;
        UUID meId = clicker.getUuid();
        UUID themId = session.other(meId);
        ServerPlayerEntity partner = server.getPlayerManager().getPlayer(themId);
        if (partner == null) {
            cancel(clicker, session, "§cThe other trader is no longer online. Nothing was traded.");
            return;
        }

        List<ItemStack> fromMe = session.offerOf(meId);
        List<ItemStack> fromThem = session.offerOf(themId);

        if (!holdsAll(clicker, fromMe)) {
            cancelBoth(session, clicker, partner,
                "§cTrade cancelled: " + clicker.getName().getString()
                + " no longer has everything they offered. Nothing was traded.");
            return;
        }
        if (!holdsAll(partner, fromThem)) {
            cancelBoth(session, clicker, partner,
                "§cTrade cancelled: " + partner.getName().getString()
                + " no longer has everything they offered. Nothing was traded.");
            return;
        }

        // Verified. From here it is one uninterrupted sequence: take, then give.
        List<ItemStack> toPartner = new ArrayList<>();
        for (ItemStack s : fromMe) toPartner.add(s.copy());
        List<ItemStack> toClicker = new ArrayList<>();
        for (ItemStack s : fromThem) toClicker.add(s.copy());

        removeAll(clicker, fromMe);
        removeAll(partner, fromThem);
        for (ItemStack s : toPartner) give(partner, s);
        for (ItemStack s : toClicker) give(clicker, s);

        session.end();
        clicker.closeHandledScreen();
        partner.closeHandledScreen();
        String done = "§a✔ Trade complete with ";
        clicker.sendMessage(Text.literal(done + partner.getName().getString() + "."), false);
        partner.sendMessage(Text.literal(done + clicker.getName().getString() + "."), false);
    }

    /**
     * Does this player physically hold everything in {@code offers}?
     *
     * <p>Counts across the whole inventory rather than per slot, because a promised stack of 64
     * may well be sitting as two stacks of 32 by now, and matches on item AND components so an
     * offered Sharpness V sword cannot be settled with a plain one of the same type. That
     * component match is the same rule the auction uses for barter payment, for the same
     * reason: what is handed over has to be the thing that was agreed to.
     */
    private static boolean holdsAll(ServerPlayerEntity player, List<ItemStack> offers) {
        List<ItemStack> need = new ArrayList<>();
        for (ItemStack s : offers) need.add(s.copy());
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack have = inv.getStack(i);
            if (have.isEmpty()) continue;
            int available = have.getCount();
            for (ItemStack want : need) {
                if (available <= 0) break;
                if (want.isEmpty()) continue;
                if (!ItemStack.areItemsAndComponentsEqual(have, want)) continue;
                int take = Math.min(want.getCount(), available);
                want.decrement(take);
                available -= take;
            }
        }
        for (ItemStack want : need) {
            if (!want.isEmpty()) return false;
        }
        return true;
    }

    /** Take the offered items out. Only ever called immediately after {@link #holdsAll}
     *  returned true for the same list in the same tick, so it cannot come up short. */
    private static void removeAll(ServerPlayerEntity player, List<ItemStack> offers) {
        var inv = player.getInventory();
        for (ItemStack offer : offers) {
            int left = offer.getCount();
            for (int i = 0; i < inv.size() && left > 0; i++) {
                ItemStack have = inv.getStack(i);
                if (have.isEmpty() || !ItemStack.areItemsAndComponentsEqual(have, offer)) continue;
                int take = Math.min(left, have.getCount());
                have.decrement(take);
                left -= take;
            }
        }
    }

    /** Hand a stack over, dropping at the player's feet if their inventory is full. Never
     *  silently destroys anything: an item that will not fit is on the floor, not gone. */
    private static void give(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().insertStack(stack) || !stack.isEmpty()) {
            player.dropItem(stack, false);
        }
    }

    // ── Cancelling ───────────────────────────────────────────────────────────

    public static void cancel(ServerPlayerEntity player, TradeSession session, String message) {
        var server = player.getServer();
        ServerPlayerEntity partner = server == null ? null
            : server.getPlayerManager().getPlayer(session.other(player.getUuid()));
        cancelBoth(session, player, partner, message);
    }

    private static void cancelBoth(TradeSession session, ServerPlayerEntity a,
                                   ServerPlayerEntity b, String message) {
        session.end();
        if (a != null) { a.closeHandledScreen(); a.sendMessage(Text.literal(message), false); }
        if (b != null) { b.closeHandledScreen(); b.sendMessage(Text.literal(message), false); }
    }

    // ── Icons ────────────────────────────────────────────────────────────────

    private static String label(ItemStack stack) {
        return stack.getCount() + "x " + stack.getName().getString();
    }

    private static void setIcon(SimpleInventory inv, int slot, net.minecraft.item.Item item,
                                String name, List<String> lore) {
        ItemStack stack = new ItemStack(item);
        decorate(stack, name, lore);
        inv.setStack(slot, stack);
    }

    private static void decorate(ItemStack stack, String name, List<String> lore) {
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        if (!lore.isEmpty()) {
            List<Text> lines = new ArrayList<>();
            for (String line : lore) lines.add(Text.literal(line));
            stack.set(DataComponentTypes.LORE, new LoreComponent(lines));
        }
    }
}
