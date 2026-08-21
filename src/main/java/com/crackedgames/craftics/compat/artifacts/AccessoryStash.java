package com.crackedgames.craftics.compat.artifacts;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent storage for a player's Accessories-mod slots.
 *
 * <p>{@link AccessoriesReflect} reads and writes the live containers; this turns the same
 * snapshot into NBT so it can sit in {@code CrafticsSavedData} across a restart. Infinite
 * Mode is the reason it exists: a run stashes the player's whole loadout and hands it back
 * when the run ends, and accessories are part of that loadout. Before this, they were the
 * one part of it the stash never touched - trinkets walked into the run and the island
 * loadout came back without them.
 *
 * <p>Every method is a no-op when the Accessories mod is absent, so the callers need no
 * mod-presence check of their own.
 */
public final class AccessoryStash {

    private AccessoryStash() {}

    /** Registry-aware NBT ops for this player's world (see {@code AuctionItems} for the why). */
    private static RegistryOps<NbtElement> ops(ServerPlayerEntity player) {
        RegistryWrapper.WrapperLookup lookup = player.getEntityWorld().getRegistryManager();
        return lookup.getOps(NbtOps.INSTANCE);
    }

    /**
     * Capture every worn accessory + cosmetic accessory as NBT. Returns an empty list when
     * Accessories isn't installed or nothing is worn.
     */
    public static NbtList save(ServerPlayerEntity player) {
        NbtList out = new NbtList();
        if (player == null) return out;
        for (AccessoriesReflect.AccessorySnapshot snap : AccessoriesReflect.saveAccessories(player)) {
            try {
                var encoded = ItemStack.CODEC.encodeStart(ops(player), snap.stack()).result();
                if (encoded.isEmpty() || !(encoded.get() instanceof NbtCompound item)) continue;
                NbtCompound entry = new NbtCompound();
                entry.putString("container", snap.container());
                entry.putInt("kind", snap.kind());
                entry.putInt("slot", snap.slot());
                entry.put("item", item);
                out.add(entry);
            } catch (Exception e) {
                CrafticsMod.LOGGER.warn("[Craftics × Accessories] could not stash {}: {}",
                    snap.stack(), e.toString());
            }
        }
        return out;
    }

    /** Empty every accessory + cosmetic slot without dropping anything. */
    public static void clear(ServerPlayerEntity player) {
        if (player == null) return;
        AccessoriesReflect.clearAccessoriesChance(player, 1.0);
    }

    /**
     * Put a {@link #save} snapshot back on the player, wiping whatever is worn first so the
     * restore is a replacement rather than a merge. A slot whose item no longer exists (its
     * mod was removed since the stash was taken) is skipped rather than throwing.
     */
    public static void restore(ServerPlayerEntity player, NbtList saved) {
        if (player == null) return;
        clear(player);
        if (saved == null || saved.isEmpty()) return;

        List<AccessoriesReflect.AccessorySnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < saved.size(); i++) {
            //? if <=1.21.4 {
            NbtCompound entry = saved.getCompound(i);
            String container = entry.getString("container");
            int kind = entry.getInt("kind");
            int slot = entry.getInt("slot");
            NbtCompound item = entry.getCompound("item");
            //?} else {
            /*NbtCompound entry = saved.getCompound(i).orElse(null);
            if (entry == null) continue;
            String container = entry.getString("container", "");
            int kind = entry.getInt("kind", 0);
            int slot = entry.getInt("slot", 0);
            NbtCompound item = entry.getCompoundOrEmpty("item");
            *///?}
            if (container.isEmpty() || item.isEmpty()) continue;
            ItemStack stack;
            try {
                stack = ItemStack.CODEC.parse(ops(player), item).result().orElse(ItemStack.EMPTY);
            } catch (Exception e) {
                stack = ItemStack.EMPTY;
            }
            if (stack.isEmpty()) continue;
            snapshots.add(new AccessoriesReflect.AccessorySnapshot(container, kind, slot, stack));
        }
        AccessoriesReflect.restoreAccessories(player, snapshots);
    }
}
