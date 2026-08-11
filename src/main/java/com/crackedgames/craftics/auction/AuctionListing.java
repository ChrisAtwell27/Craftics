package com.crackedgames.craftics.auction;

import net.minecraft.nbt.NbtCompound;

import java.util.UUID;

/**
 * One item on the auction board.
 *
 * <p>The listed stack is held as NBT rather than as a live {@code ItemStack} on purpose. It is
 * the only form that survives a save/load round trip unchanged, it keeps {@link AuctionStore}
 * free of registry lookups (so the dupe-safety rules can be unit-tested without a Minecraft
 * bootstrap), and an item whose mod is later uninstalled stays returnable instead of crashing
 * the load.
 *
 * <p>Exactly one of the two prices is meaningful:
 * <ul>
 *   <li>{@code emeraldPrice > 0}, {@code wantedItemId == null} - an ordinary buy-now listing.
 *   <li>{@code emeraldPrice == 0}, {@code wantedItemId != null} - a barter listing, paid for
 *       with {@code wantedCount} of {@code wantedItemId}.
 * </ul>
 *
 * @param id         random per listing, so a stale click on a removed listing can never land
 *                   on a recycled slot
 * @param sellerName display only, resolved when the listing is made; names change
 */
public record AuctionListing(
    UUID id,
    UUID seller,
    String sellerName,
    NbtCompound stack,
    String displayName,
    int count,
    int emeraldPrice,
    String wantedItemId,
    int wantedCount,
    long listedAtEpochMs
) {

    /** True when this is paid for in items rather than emeralds. */
    public boolean isBarter() {
        return wantedItemId != null && !wantedItemId.isEmpty();
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        // UUIDs go in as strings rather than through putUuid/getUuid: those changed shape
        // across the versions this mod builds for, and a string needs no version split at all.
        nbt.putString("id", id.toString());
        nbt.putString("seller", seller.toString());
        nbt.putString("sellerName", sellerName == null ? "" : sellerName);
        nbt.put("stack", stack.copy());
        nbt.putString("displayName", displayName == null ? "" : displayName);
        nbt.putInt("count", count);
        nbt.putInt("price", emeraldPrice);
        if (isBarter()) {
            nbt.putString("wantedItem", wantedItemId);
            nbt.putInt("wantedCount", wantedCount);
        }
        nbt.putLong("listedAt", listedAtEpochMs);
        return nbt;
    }

    /** Rebuild from NBT, or null when the entry is malformed - a corrupt row is skipped
     *  rather than taking the whole board down with it on load. */
    public static AuctionListing fromNbt(NbtCompound nbt) {
        try {
            //? if <=1.21.4 {
            String wanted = nbt.contains("wantedItem") ? nbt.getString("wantedItem") : null;
            return new AuctionListing(
                UUID.fromString(nbt.getString("id")), UUID.fromString(nbt.getString("seller")),
                nbt.getString("sellerName"),
                nbt.getCompound("stack"), nbt.getString("displayName"), nbt.getInt("count"),
                nbt.getInt("price"), wanted, nbt.getInt("wantedCount"), nbt.getLong("listedAt"));
            //?} else {
            /*String wanted = nbt.contains("wantedItem") ? nbt.getString("wantedItem", "") : null;
            return new AuctionListing(
                UUID.fromString(nbt.getString("id", "")), UUID.fromString(nbt.getString("seller", "")),
                nbt.getString("sellerName", ""),
                nbt.getCompound("stack").orElseThrow(), nbt.getString("displayName", ""),
                nbt.getInt("count", 0), nbt.getInt("price", 0), wanted,
                nbt.getInt("wantedCount", 0), nbt.getLong("listedAt", 0L));
            *///?}
        } catch (Exception e) {
            return null;
        }
    }
}
