package com.crackedgames.craftics.auction;

import com.crackedgames.craftics.world.CrafticsSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

/**
 * `/auction`, and its two aliases `/shop` and `/store`.
 *
 * <p>Every path here obeys the store's one rule: an item is either in an inventory or on the
 * board, never both, and it moves in a single tick. Selling removes the held stack and lists
 * it in the same statement pair; buying claims the listing before a single emerald changes
 * hands, so a buyer who loses a race pays nothing.
 */
public final class AuctionCommands {

    private AuctionCommands() {}

    /** True when the auction house is not switched off for this world. */
    private static boolean auctionOpen(ServerCommandSource src) {
        var server = src.getServer();
        if (server == null) return true;
        return com.crackedgames.craftics.world.CrafticsSavedData
            .get(server.getOverworld()).isAuctionEnabled();
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        for (String name : new String[]{"auction", "shop", "store"}) {
            dispatcher.register(CommandManager.literal(name)
                // Closed by an admin, or open to everyone. Gating the root closes every
                // subcommand with it - sell, buy, cancel, collect - rather than leaving a
                // half-shut market where listings can still be created but never browsed.
                // Admins keep access so they can inspect and unwind a market they just froze.
                .requires(src -> auctionOpen(src)
                    || com.crackedgames.craftics.command.CrafticsPermissions
                        .check(src, "command.auction.admin"))
                .executes(ctx -> browse(ctx.getSource()))
                .then(CommandManager.literal("sell")
                    .then(CommandManager.argument("price", IntegerArgumentType.integer(1, 1_000_000))
                        .executes(ctx -> sell(ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "price"), null, 0)))
                    .then(CommandManager.literal("barter")
                        .then(CommandManager.argument("item", StringArgumentType.word())
                            .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 2304))
                                .executes(ctx -> sell(ctx.getSource(), 0,
                                    StringArgumentType.getString(ctx, "item"),
                                    IntegerArgumentType.getInteger(ctx, "count")))))))
                .then(CommandManager.literal("buy")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .executes(ctx -> buy(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                .then(CommandManager.literal("cancel")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .executes(ctx -> cancel(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                .then(CommandManager.literal("mine").executes(ctx -> mine(ctx.getSource())))
                .then(CommandManager.literal("collect").executes(ctx -> collect(ctx.getSource()))));
        }
    }

    // ── Browse ───────────────────────────────────────────────────────────────

    private static int browse(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        AuctionMenus.openBrowse(source.getPlayerOrThrow(), 0);
        return 1;
    }

    private static String priceText(AuctionListing l) {
        return l.isBarter()
            ? "§b" + l.wantedCount() + "x " + l.wantedItemId().replace("minecraft:", "")
            : "§a" + l.emeraldPrice() + " emeralds";
    }

    /** The first 8 characters of the id: enough to type, enough to stay unique on a board
     *  capped at 500. Full UUIDs are unusable as a command argument. */
    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static AuctionListing findByShortId(String prefix) {
        AuctionListing match = null;
        for (AuctionListing l : AuctionStore.all()) {
            if (!shortId(l.id()).equalsIgnoreCase(prefix)) continue;
            if (match != null) return null;   // ambiguous; refuse rather than guess
            match = l;
        }
        return match;
    }

    // ── Sell ─────────────────────────────────────────────────────────────────

    private static int sell(ServerCommandSource source, int price, String wantedItem, int wantedCount)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ItemStack held = player.getMainHandStack();
        if (held.isEmpty()) {
            player.sendMessage(Text.literal("§cHold the item you want to sell."), false);
            return 0;
        }
        if (wantedItem != null && AuctionItems.itemFor(wantedItem) == null
                && AuctionItems.itemFor("minecraft:" + wantedItem) == null) {
            player.sendMessage(Text.literal("§cNo such item: " + wantedItem), false);
            return 0;
        }
        String wantedId = wantedItem == null ? null
            : (AuctionItems.itemFor(wantedItem) != null ? wantedItem : "minecraft:" + wantedItem);

        NbtCompound stackNbt = AuctionItems.toNbt(player, held);
        AuctionListing listing = new AuctionListing(
            UUID.randomUUID(), player.getUuid(), player.getName().getString(),
            stackNbt, held.getName().getString(), held.getCount(),
            price, wantedId, wantedCount, System.currentTimeMillis());

        // Check the limits BEFORE taking the item, so a refused listing cannot eat it.
        AuctionStore.Result result = AuctionStore.list(listing);
        if (result != AuctionStore.Result.OK) {
            player.sendMessage(Text.literal(switch (result) {
                case BOARD_FULL -> "§cThe auction board is full. Try again later.";
                case PLAYER_FULL -> "§cYou already have " + AuctionStore.MAX_LISTINGS_PER_PLAYER
                    + " listings up. Cancel one first.";
                default -> "§cCould not list that.";
            }), false);
            return 0;
        }
        // Listed: the item leaves the inventory in the same tick it entered the board.
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, ItemStack.EMPTY);
        markDirty(player);

        player.sendMessage(Text.literal("§aListed §f" + listing.count() + "x " + listing.displayName()
            + "§a for " + priceText(listing) + "§a. §8id " + shortId(listing.id())), false);
        return 1;
    }

    // ── Buy ──────────────────────────────────────────────────────────────────

    private static int buy(ServerCommandSource source, String shortId)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        AuctionListing preview = findByShortId(shortId);
        if (preview == null) {
            player.sendMessage(Text.literal("§cNo listing with that id."), false);
            return 0;
        }
        return buyListing(player, preview) ? 1 : 0;
    }

    /**
     * Buy {@code preview}, from a command or from the chest screen. One implementation, so
     * the claim-before-pay rule cannot be right in one place and wrong in the other.
     *
     * @return true when the item changed hands
     */
    public static boolean buyListing(ServerPlayerEntity player, AuctionListing preview) {
        if (preview.seller().equals(player.getUuid())) {
            player.sendMessage(Text.literal("§cThat is your own listing. Cancel it instead."), false);
            return false;
        }

        CrafticsSavedData data = CrafticsSavedData.get((ServerWorld) player.getEntityWorld());
        CrafticsSavedData.PlayerData buyerData = data.getPlayerData(player.getUuid());

        // Affordability is checked against the PREVIEW, before the claim, so a player who
        // cannot pay never removes the listing from under someone who can.
        if (preview.isBarter()) {
            if (AuctionItems.countPlain(player, preview.wantedItemId()) < preview.wantedCount()) {
                player.sendMessage(Text.literal("§cYou need " + preview.wantedCount() + "x "
                    + preview.wantedItemId().replace("minecraft:", "")
                    + " §7(plain ones - enchanted, renamed or damaged items are not accepted)"), false);
                return false;
            }
        } else if (buyerData.emeralds < preview.emeraldPrice()) {
            player.sendMessage(Text.literal("§cYou need " + preview.emeraldPrice()
                + " emeralds; you have " + buyerData.emeralds + "."), false);
            return false;
        }

        // THE claim. Whoever gets a non-null listing owns the item; a second buyer in the
        // same tick gets null here and is told it is gone, having paid nothing.
        AuctionListing listing = AuctionStore.claim(preview.id());
        if (listing == null) {
            player.sendMessage(Text.literal("§cSomeone else bought that first."), false);
            return false;
        }

        // Pay. If payment fails now (inventory changed between the check and here), the
        // listing goes straight back up rather than the item vanishing.
        boolean paid = listing.isBarter()
            ? AuctionItems.takePlain(player, listing.wantedItemId(), listing.wantedCount())
            : buyerData.spendEmeralds(listing.emeraldPrice());
        if (!paid) {
            AuctionStore.list(listing);
            player.sendMessage(Text.literal("§cPayment failed - the listing is still up."), false);
            return false;
        }

        // Credit the seller. Barter goes to their mailbox as items; emeralds go to the bank,
        // which works whether or not they are online.
        CrafticsSavedData.PlayerData sellerData = data.getPlayerData(listing.seller());
        if (listing.isBarter()) {
            NbtCompound paidStack = new NbtCompound();
            paidStack.putString("id", listing.wantedItemId());
            paidStack.putInt("count", listing.wantedCount());
            AuctionStore.mail(listing.seller(), paidStack);
        } else {
            sellerData.addEmeralds(listing.emeraldPrice());
        }

        deliver(player, listing.stack());
        markDirty(player);
        player.sendMessage(Text.literal("§aBought §f" + listing.count() + "x "
            + listing.displayName() + "§a."), false);

        ServerPlayerEntity seller = player.getServer().getPlayerManager().getPlayer(listing.seller());
        if (seller != null) {
            seller.sendMessage(Text.literal("§a" + player.getName().getString() + " bought your §f"
                + listing.displayName() + "§a."), false);
        }
        return true;
    }

    // ── Cancel, mine, collect ────────────────────────────────────────────────

    private static int cancel(ServerCommandSource source, String shortId)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        AuctionListing preview = findByShortId(shortId);
        if (preview == null) {
            player.sendMessage(Text.literal("§cNo listing with that id."), false);
            return 0;
        }
        return cancelListing(player, preview) ? 1 : 0;
    }

    /** Take your own listing down, from a command or from the chest screen. */
    public static boolean cancelListing(ServerPlayerEntity player, AuctionListing preview) {
        AuctionListing listing = AuctionStore.cancel(preview.id(), player.getUuid());
        if (listing == null) {
            player.sendMessage(Text.literal("§cThat is not your listing."), false);
            return false;
        }
        deliver(player, listing.stack());
        markDirty(player);
        player.sendMessage(Text.literal("§aListing cancelled; the item is back with you."), false);
        return true;
    }

    private static int mine(ServerCommandSource source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        AuctionMenus.openMine(source.getPlayerOrThrow(), 0);
        return 1;
    }

    private static int collect(ServerCommandSource source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        collectMailbox(source.getPlayerOrThrow());
        return 1;
    }

    /** Hand over everything owed, from a command or from the chest screen. */
    public static void collectMailbox(ServerPlayerEntity player) {
        List<NbtCompound> owed = AuctionStore.drainMailbox(player.getUuid());
        if (owed.isEmpty()) {
            player.sendMessage(Text.literal("§7Nothing waiting for you."), false);
            return;
        }
        for (NbtCompound stack : owed) deliver(player, stack);
        markDirty(player);
        player.sendMessage(Text.literal("§aCollected " + owed.size() + " item"
            + (owed.size() == 1 ? "" : "s") + "."), false);
    }

    /**
     * Hand an item over. Overflow goes through the same managed loot chest every other
     * reward in the mod uses, so a full inventory never drops items on the floor of a hub
     * where they would despawn.
     */
    private static void deliver(ServerPlayerEntity player, NbtCompound stackNbt) {
        ItemStack stack = AuctionItems.fromNbt(player, stackNbt);
        if (stack.isEmpty()) {
            player.sendMessage(Text.literal(
                "§cThat item's mod is no longer installed; it could not be given back."), false);
            return;
        }
        com.crackedgames.craftics.combat.LootDelivery.deliver(player, stack);
    }

    private static void markDirty(ServerPlayerEntity player) {
        CrafticsSavedData.get((ServerWorld) player.getEntityWorld()).markDirty();
    }
}
