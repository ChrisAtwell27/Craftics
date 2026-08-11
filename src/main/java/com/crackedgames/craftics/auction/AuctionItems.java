package com.crackedgames.craftics.auction;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * The bridge between {@link AuctionStore}, which holds NBT, and the live game, which holds
 * {@code ItemStack}s. Kept apart from the store on purpose: the store's rules are the part
 * worth unit-testing, and they stay testable only while nothing in it touches a registry.
 */
public final class AuctionItems {

    private AuctionItems() {}

    /**
     * Registry-aware NBT ops for this player's world.
     *
     * <p>Serialization goes through {@code ItemStack.CODEC} rather than the convenience
     * methods on ItemStack: {@code encode(WrapperLookup)} does not exist on every version
     * this mod builds for, while the codec has the same shape on all of them. One helper
     * beats a version split in two places.
     */
    private static net.minecraft.registry.RegistryOps<net.minecraft.nbt.NbtElement> ops(
            ServerPlayerEntity player) {
        RegistryWrapper.WrapperLookup lookup = player.getEntityWorld().getRegistryManager();
        return lookup.getOps(net.minecraft.nbt.NbtOps.INSTANCE);
    }

    /** Serialize a stack for storage. Returns an empty compound if it cannot be encoded,
     *  which the caller treats as "nothing to list" rather than crashing. */
    public static NbtCompound toNbt(ServerPlayerEntity player, ItemStack stack) {
        try {
            var encoded = ItemStack.CODEC.encodeStart(ops(player), stack).result();
            if (encoded.isPresent() && encoded.get() instanceof NbtCompound compound) {
                return compound;
            }
        } catch (Exception ignored) {
            // Falls through to the empty compound below.
        }
        return new NbtCompound();
    }

    /** Rebuild a stack, or an empty stack when the item no longer exists (its mod was
     *  removed since it was listed). Never throws: a listing for a vanished item must still
     *  be cancellable rather than jamming the board. */
    public static ItemStack fromNbt(ServerPlayerEntity player, NbtCompound nbt) {
        try {
            return ItemStack.CODEC.parse(ops(player), nbt).result().orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * Whether {@code stack} may be handed over as barter payment.
     *
     * <p>Plain stacks only: no enchantments, no custom name, no damage. A buyer clicking Buy
     * on a listing that asks for "1 diamond sword" must not have their Sharpness V sword
     * taken because it happened to match the item type. If their only matching items are
     * special ones, the purchase is refused and says so rather than quietly spending them.
     */
    public static boolean isPlain(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.isDamaged()) return false;
        if (stack.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME)) return false;
        if (stack.contains(net.minecraft.component.DataComponentTypes.ENCHANTMENTS)
            && !stack.getEnchantments().isEmpty()) return false;
        if (stack.contains(net.minecraft.component.DataComponentTypes.STORED_ENCHANTMENTS)) return false;
        return true;
    }

    /** How many plain items of {@code itemId} the player is holding across their inventory. */
    public static int countPlain(ServerPlayerEntity player, String itemId) {
        Item want = itemFor(itemId);
        if (want == null) return 0;
        int total = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.getItem() == want && isPlain(stack)) total += stack.getCount();
        }
        return total;
    }

    /**
     * Remove exactly {@code count} plain items of {@code itemId}. Returns false and takes
     * NOTHING when the player is short, so a partial payment can never leave someone out of
     * pocket with no item to show for it.
     */
    public static boolean takePlain(ServerPlayerEntity player, String itemId, int count) {
        if (countPlain(player, itemId) < count) return false;
        Item want = itemFor(itemId);
        int left = count;
        var inv = player.getInventory();
        for (int i = 0; i < inv.size() && left > 0; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.getItem() != want || !isPlain(stack)) continue;
            int take = Math.min(left, stack.getCount());
            stack.decrement(take);
            left -= take;
        }
        return left == 0;
    }

    /** The item for a registry id, or null when it is unknown. */
    public static Item itemFor(String itemId) {
        try {
            Identifier id = Identifier.tryParse(itemId);
            if (id == null || !Registries.ITEM.containsId(id)) return null;
            return Registries.ITEM.get(id);
        } catch (Exception e) {
            return null;
        }
    }
}
