package com.crackedgames.craftics.auction;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The auction board: every live listing, plus the mailbox of items owed to players.
 *
 * <p><b>The one rule.</b> An item is either in a player's inventory or in here, never both.
 * Every duplication bug in every server economy is the same bug - an item exists in two places
 * for a moment and something interrupts before the second copy is removed. So there is no
 * "pending listing" state and no "reserved" flag: a pending state is a state a crash can
 * strand. Callers take the stack out of the inventory and hand it to {@link #list} in the same
 * tick, or take it out of here with {@link #claim} and put it in an inventory in the same tick.
 *
 * <p><b>Claim before pay.</b> {@link #claim} removes the listing and returns it. A second buyer
 * racing the first gets null and is told the listing is gone. Nothing charges anyone until the
 * claim has succeeded, so a lost race costs nothing. This is the whole of the double-spend
 * defence and it is why buying is not a "read then remove" pair.
 *
 * <p>Deliberately free of {@code ItemStack}, {@code Registries} and any world access: it holds
 * NBT and UUIDs. That keeps the rules above unit-testable without a Minecraft bootstrap, which
 * is the only way to have real tests for an invariant this important.
 */
public final class AuctionStore {

    private AuctionStore() {}

    /** Listings a single player may have up at once. */
    public static final int MAX_LISTINGS_PER_PLAYER = 10;
    /** Hard ceiling on the whole board, bounding both the save file and the GUI paging. */
    public static final int MAX_LISTINGS_TOTAL = 500;
    /** Items a player may have waiting for collection before they must clear some. */
    public static final int MAX_MAILBOX = 100;
    /** How long a listing stays up before it is returned to the seller's mailbox. */
    public static final long LISTING_LIFETIME_MS = 14L * 24 * 60 * 60 * 1000;

    /** Why an attempted mutation was refused. {@link #OK} is the only success. */
    public enum Result { OK, BOARD_FULL, PLAYER_FULL, MAILBOX_FULL, GONE, NOT_YOURS }

    /** Insertion-ordered so the board reads oldest-first without a sort on every open. */
    private static final Map<UUID, AuctionListing> LISTINGS = new LinkedHashMap<>();
    private static final Map<UUID, List<NbtCompound>> MAILBOX = new LinkedHashMap<>();

    // ── Listings ─────────────────────────────────────────────────────────────

    /**
     * Put an item on the board. The caller MUST have already removed it from the seller's
     * inventory this tick.
     */
    public static Result list(AuctionListing listing) {
        if (LISTINGS.size() >= MAX_LISTINGS_TOTAL) return Result.BOARD_FULL;
        if (countFor(listing.seller()) >= MAX_LISTINGS_PER_PLAYER) return Result.PLAYER_FULL;
        LISTINGS.put(listing.id(), listing);
        return Result.OK;
    }

    /**
     * Take a listing off the board, atomically. Returns the listing to whoever won, or null if
     * it is already gone - which is exactly what a second buyer in the same tick sees.
     *
     * <p>Callers must treat a non-null return as "you now own this item and nobody else can
     * get it": if delivery then fails, it is theirs to mailbox, not to drop.
     */
    public static AuctionListing claim(UUID listingId) {
        return LISTINGS.remove(listingId);
    }

    /** Cancel your own listing. Fails rather than removing someone else's. */
    public static AuctionListing cancel(UUID listingId, UUID requester) {
        AuctionListing listing = LISTINGS.get(listingId);
        if (listing == null) return null;
        if (!listing.seller().equals(requester)) return null;
        return LISTINGS.remove(listingId);
    }

    public static AuctionListing peek(UUID listingId) {
        return LISTINGS.get(listingId);
    }

    /** Every live listing, oldest first. */
    public static List<AuctionListing> all() {
        return new ArrayList<>(LISTINGS.values());
    }

    /** One player's live listings, oldest first. */
    public static List<AuctionListing> bySeller(UUID seller) {
        List<AuctionListing> out = new ArrayList<>();
        for (AuctionListing l : LISTINGS.values()) {
            if (l.seller().equals(seller)) out.add(l);
        }
        return out;
    }

    public static int countFor(UUID seller) {
        int n = 0;
        for (AuctionListing l : LISTINGS.values()) {
            if (l.seller().equals(seller)) n++;
        }
        return n;
    }

    public static int size() {
        return LISTINGS.size();
    }

    /**
     * Return everything older than {@link #LISTING_LIFETIME_MS} to its seller's mailbox.
     *
     * <p>Expiry never destroys an item. A seller who never comes back has a full mailbox, not
     * a hole where their diamonds were.
     *
     * @param nowMs current wall clock, passed in so this is testable without a real clock
     * @return how many listings expired
     */
    public static int expireOld(long nowMs) {
        List<AuctionListing> expired = new ArrayList<>();
        for (AuctionListing l : LISTINGS.values()) {
            if (nowMs - l.listedAtEpochMs() >= LISTING_LIFETIME_MS) expired.add(l);
        }
        for (AuctionListing l : expired) {
            // claim() first: the item leaves the board before it enters the mailbox, so it is
            // never in both, exactly like a sale.
            AuctionListing claimed = claim(l.id());
            if (claimed != null) mail(claimed.seller(), claimed.stack());
        }
        return expired.size();
    }

    // ── Mailbox ──────────────────────────────────────────────────────────────

    /** Owe {@code owner} an item. Refused when their mailbox is full, so the caller must
     *  check the return and keep the item rather than assuming it landed. */
    public static Result mail(UUID owner, NbtCompound stack) {
        List<NbtCompound> box = MAILBOX.computeIfAbsent(owner, k -> new ArrayList<>());
        if (box.size() >= MAX_MAILBOX) return Result.MAILBOX_FULL;
        box.add(stack.copy());
        return Result.OK;
    }

    /** Everything owed to {@code owner}, removed from the mailbox in one go. The caller owns
     *  the items the moment this returns and must deliver or re-mail them. */
    public static List<NbtCompound> drainMailbox(UUID owner) {
        List<NbtCompound> box = MAILBOX.remove(owner);
        return box == null ? List.of() : box;
    }

    public static int mailboxSize(UUID owner) {
        List<NbtCompound> box = MAILBOX.get(owner);
        return box == null ? 0 : box.size();
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    public static void clear() {
        LISTINGS.clear();
        MAILBOX.clear();
    }

    public static NbtCompound writeNbt() {
        NbtCompound root = new NbtCompound();
        NbtList listings = new NbtList();
        for (AuctionListing l : LISTINGS.values()) listings.add(l.toNbt());
        root.put("listings", listings);

        NbtList mail = new NbtList();
        for (var entry : MAILBOX.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            NbtCompound owner = new NbtCompound();
            owner.putString("owner", entry.getKey().toString());
            NbtList items = new NbtList();
            for (NbtCompound stack : entry.getValue()) items.add(stack.copy());
            owner.put("items", items);
            mail.add(owner);
        }
        root.put("mail", mail);
        return root;
    }

    public static void readNbt(NbtCompound root) {
        clear();
        if (root == null) return;
        NbtList listings = listOf(root, "listings");
        for (int i = 0; i < listings.size(); i++) {
            AuctionListing l = AuctionListing.fromNbt(compoundAt(listings, i));
            // A corrupt row is skipped rather than taking the whole board down on load.
            if (l != null) LISTINGS.put(l.id(), l);
        }
        NbtList mail = listOf(root, "mail");
        for (int i = 0; i < mail.size(); i++) {
            NbtCompound owner = compoundAt(mail, i);
            try {
                UUID id = UUID.fromString(ownerId(owner));
                NbtList items = listOf(owner, "items");
                List<NbtCompound> box = new ArrayList<>();
                for (int j = 0; j < items.size(); j++) box.add(compoundAt(items, j));
                if (!box.isEmpty()) MAILBOX.put(id, box);
            } catch (Exception ignored) {
                // Same rule as listings: lose the malformed entry, keep the rest.
            }
        }
    }

    // NbtCompound.getList / NbtList.getCompound changed shape in 1.21.5 (Optional returns,
    // no type argument). Both are wrapped once here so readNbt reads the same on every shard.
    private static NbtList listOf(NbtCompound c, String key) {
        //? if <=1.21.4 {
        return c.getList(key, NbtElement.COMPOUND_TYPE);
        //?} else {
        /*return c.getListOrEmpty(key);
        *///?}
    }

    private static NbtCompound compoundAt(NbtList list, int index) {
        //? if <=1.21.4 {
        return list.getCompound(index);
        //?} else {
        /*return list.getCompound(index).orElseGet(NbtCompound::new);
        *///?}
    }

    /** The owner UUID string from a mailbox row, across the NBT API shapes this mod builds
     *  for. Strings rather than putUuid/getUuid, for the reason given in AuctionListing. */
    private static String ownerId(NbtCompound row) {
        //? if <=1.21.4 {
        return row.getString("owner");
        //?} else {
        /*return row.getString("owner", "");
        *///?}
    }

    /** Listings sorted cheapest first, for the browse screen's default order. */
    public static List<AuctionListing> sortedByPrice() {
        List<AuctionListing> out = all();
        out.sort(Comparator.comparingInt(AuctionListing::emeraldPrice));
        return out;
    }
}
