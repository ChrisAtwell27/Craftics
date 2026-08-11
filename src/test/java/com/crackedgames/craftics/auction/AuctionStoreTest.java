package com.crackedgames.craftics.auction;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dupe-safety rules of {@link AuctionStore}.
 *
 * <p>These are the tests that matter: an item must exist in exactly one place at every point,
 * and two buyers racing for the same listing must not both get it. The store is deliberately
 * built out of NBT and UUIDs rather than {@code ItemStack} so this can run with no Minecraft
 * bootstrap - an invariant nobody can test is an invariant that eventually breaks.
 */
class AuctionStoreTest {

    private static final UUID SELLER = UUID.nameUUIDFromBytes("seller".getBytes());
    private static final UUID BUYER = UUID.nameUUIDFromBytes("buyer".getBytes());

    @BeforeEach
    void reset() {
        AuctionStore.clear();
    }

    private static AuctionListing listing(UUID id, UUID seller, long listedAt) {
        NbtCompound stack = new NbtCompound();
        stack.putString("id", "minecraft:diamond_sword");
        return new AuctionListing(id, seller, "Seller", stack, "Diamond Sword", 1,
            64, null, 0, listedAt);
    }

    private static AuctionListing listing(UUID id) {
        return listing(id, SELLER, 0L);
    }

    @Test
    @DisplayName("a listed item is on the board exactly once")
    void listPutsItemOnBoardOnce() {
        UUID id = UUID.randomUUID();
        assertEquals(AuctionStore.Result.OK, AuctionStore.list(listing(id)));
        assertEquals(1, AuctionStore.size());
        assertNotNull(AuctionStore.peek(id));
    }

    @Test
    @DisplayName("two buyers racing the same listing: exactly one wins")
    void claimIsAtomic() {
        UUID id = UUID.randomUUID();
        AuctionStore.list(listing(id));

        AuctionListing first = AuctionStore.claim(id);
        AuctionListing second = AuctionStore.claim(id);

        assertNotNull(first, "the first claim must win");
        assertNull(second, "the second claim must lose, not hand out a second copy");
        assertEquals(0, AuctionStore.size());
    }

    @Test
    @DisplayName("a claimed listing is off the board before anything is paid")
    void claimRemovesBeforePayment() {
        UUID id = UUID.randomUUID();
        AuctionStore.list(listing(id));
        AuctionStore.claim(id);
        // Nothing has been charged or delivered yet at this point in a real buy; the listing
        // is already unreachable, which is what stops the double spend.
        assertNull(AuctionStore.peek(id));
    }

    @Test
    @DisplayName("cancel only works for the seller")
    void cancelRefusesOtherPlayers() {
        UUID id = UUID.randomUUID();
        AuctionStore.list(listing(id));

        assertNull(AuctionStore.cancel(id, BUYER), "a stranger must not cancel someone's listing");
        assertEquals(1, AuctionStore.size(), "a refused cancel must leave the listing up");

        assertNotNull(AuctionStore.cancel(id, SELLER));
        assertEquals(0, AuctionStore.size());
    }

    @Test
    @DisplayName("per-player and board limits are enforced")
    void limitsAreEnforced() {
        for (int i = 0; i < AuctionStore.MAX_LISTINGS_PER_PLAYER; i++) {
            assertEquals(AuctionStore.Result.OK, AuctionStore.list(listing(UUID.randomUUID())));
        }
        assertEquals(AuctionStore.Result.PLAYER_FULL,
            AuctionStore.list(listing(UUID.randomUUID())),
            "one player must not be able to wallpaper the board");

        // A different seller is unaffected by the first one's cap.
        assertEquals(AuctionStore.Result.OK,
            AuctionStore.list(listing(UUID.randomUUID(), BUYER, 0L)));
    }

    @Test
    @DisplayName("expiry returns the item to the seller's mailbox, never destroys it")
    void expiryMailsRatherThanDeletes() {
        UUID id = UUID.randomUUID();
        AuctionStore.list(listing(id, SELLER, 0L));

        int expired = AuctionStore.expireOld(AuctionStore.LISTING_LIFETIME_MS + 1);

        assertEquals(1, expired);
        assertEquals(0, AuctionStore.size(), "the expired listing leaves the board");
        assertEquals(1, AuctionStore.mailboxSize(SELLER), "and lands in the seller's mailbox");
    }

    @Test
    @DisplayName("a listing that has not aged out is left alone")
    void expirySparesYoungListings() {
        AuctionStore.list(listing(UUID.randomUUID(), SELLER, 0L));
        assertEquals(0, AuctionStore.expireOld(AuctionStore.LISTING_LIFETIME_MS - 1));
        assertEquals(1, AuctionStore.size());
        assertEquals(0, AuctionStore.mailboxSize(SELLER));
    }

    @Test
    @DisplayName("draining the mailbox hands over everything exactly once")
    void mailboxDrainsOnce() {
        NbtCompound stack = new NbtCompound();
        stack.putString("id", "minecraft:emerald");
        AuctionStore.mail(SELLER, stack);
        AuctionStore.mail(SELLER, stack);

        List<NbtCompound> first = AuctionStore.drainMailbox(SELLER);
        List<NbtCompound> second = AuctionStore.drainMailbox(SELLER);

        assertEquals(2, first.size());
        assertTrue(second.isEmpty(), "a second drain must not hand out the same items again");
        assertEquals(0, AuctionStore.mailboxSize(SELLER));
    }

    @Test
    @DisplayName("a full mailbox refuses rather than silently swallowing an item")
    void mailboxRefusesWhenFull() {
        NbtCompound stack = new NbtCompound();
        stack.putString("id", "minecraft:emerald");
        for (int i = 0; i < AuctionStore.MAX_MAILBOX; i++) {
            assertEquals(AuctionStore.Result.OK, AuctionStore.mail(SELLER, stack));
        }
        assertEquals(AuctionStore.Result.MAILBOX_FULL, AuctionStore.mail(SELLER, stack));
    }

    @Test
    @DisplayName("listings and mailbox survive a save and load unchanged")
    void persistenceRoundTrips() {
        UUID id = UUID.randomUUID();
        AuctionStore.list(listing(id));
        NbtCompound mailed = new NbtCompound();
        mailed.putString("id", "minecraft:emerald");
        AuctionStore.mail(BUYER, mailed);

        NbtCompound saved = AuctionStore.writeNbt();
        AuctionStore.clear();
        assertEquals(0, AuctionStore.size());

        AuctionStore.readNbt(saved);

        assertEquals(1, AuctionStore.size(), "the listing comes back");
        AuctionListing back = AuctionStore.peek(id);
        assertNotNull(back);
        assertEquals(SELLER, back.seller());
        assertEquals(64, back.emeraldPrice());
        assertEquals(1, AuctionStore.mailboxSize(BUYER), "and so does the mailbox");
    }

    @Test
    @DisplayName("a barter listing round trips with its wanted item")
    void barterRoundTrips() {
        UUID id = UUID.randomUUID();
        NbtCompound stack = new NbtCompound();
        stack.putString("id", "minecraft:diamond_sword");
        AuctionStore.list(new AuctionListing(id, SELLER, "Seller", stack, "Diamond Sword", 1,
            0, "minecraft:iron_ingot", 32, 0L));

        AuctionStore.readNbt(AuctionStore.writeNbt());

        AuctionListing back = AuctionStore.peek(id);
        assertNotNull(back);
        assertTrue(back.isBarter());
        assertEquals("minecraft:iron_ingot", back.wantedItemId());
        assertEquals(32, back.wantedCount());
    }

    @Test
    @DisplayName("a corrupt row is skipped, the rest of the board still loads")
    void corruptRowDoesNotSinkTheBoard() {
        AuctionStore.list(listing(UUID.randomUUID()));
        NbtCompound saved = AuctionStore.writeNbt();
        // Splice in a row with none of the required keys. Built fresh and put back rather
        // than mutating the stored list in place, so this reads the same on every shard
        // (NbtCompound.getList changed shape in 1.21.5).
        net.minecraft.nbt.NbtList rows = new net.minecraft.nbt.NbtList();
        for (AuctionListing l : AuctionStore.all()) rows.add(l.toNbt());
        rows.add(new NbtCompound());
        saved.put("listings", rows);

        AuctionStore.readNbt(saved);

        assertEquals(1, AuctionStore.size(), "the good listing survives the bad one");
    }

    @Test
    @DisplayName("an item is never on the board and in the mailbox at the same time")
    void neverInTwoPlacesAtOnce() {
        UUID id = UUID.randomUUID();
        AuctionStore.list(listing(id, SELLER, 0L));
        assertEquals(1, AuctionStore.size());
        assertEquals(0, AuctionStore.mailboxSize(SELLER));

        AuctionStore.expireOld(AuctionStore.LISTING_LIFETIME_MS + 1);

        // Exactly one of the two holds it, both before and after.
        assertEquals(0, AuctionStore.size());
        assertEquals(1, AuctionStore.mailboxSize(SELLER));
    }
}
