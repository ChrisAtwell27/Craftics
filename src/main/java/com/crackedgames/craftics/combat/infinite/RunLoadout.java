package com.crackedgames.craftics.combat.infinite;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.compat.artifacts.AccessoryStash;
import com.crackedgames.craftics.compat.backpacked.BackpackedCompat;
import com.crackedgames.craftics.world.CrafticsSavedData;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything a player carries across the Infinite Mode boundary, defined in one place.
 *
 * <p>Infinite Mode is "start from nothing": entering a run stashes the player's real loadout and
 * hands them an empty one, and leaving swaps it back so the run's loot evaporates. The swap used
 * to be written out by hand four times - start, park, resume, end - and each copy listed the
 * storage it knew about: the inventory, and later the Accessories slots. Anything not on those
 * lists simply was not swapped. A worn Backpacked pack carried the player's real gear into the
 * run and carried run loot back out; the ender chest and vanilla XP did the same, and so did
 * whatever was sitting on the cursor or in the 2x2 crafting grid at the moment of the swap.
 *
 * <p>So the list lives here now, once. Every transition captures, clears and applies through
 * this class, and a new kind of player-attached storage is added by writing one {@link Part} -
 * which every transition then swaps, because none of them name storage themselves any more.
 *
 * <h2>The one rule</h2>
 *
 * <p>A part is only ever cleared or replaced if the REAL side captured it. A part the real
 * stash does not hold - its mod was missing, a snapshot failed to encode, or the run began on a
 * build that predates the part - is left exactly where it is, on both sides. Moving it would mean
 * guessing which of the two copies is the real one, and a wrong guess deletes a player's items.
 * That is why {@link #capture(ServerPlayerEntity, Snapshot)} takes a mask, and why an uncaptured
 * part reads as absent rather than empty.
 */
public final class RunLoadout {

    private RunLoadout() {}

    /**
     * One captured loadout. The inventory is always a part; the rest are present only when they
     * were actually captured. Accessories keep their own fields because they were stashed before
     * this class existed and saves already carry them in that shape.
     */
    public static final class Snapshot {
        public NbtList inventory = new NbtList();
        public int selectedSlot;
        public NbtList accessories = new NbtList();
        public boolean accessoriesCaptured;
        /** One entry per captured {@link Part}, keyed by {@link Part#key}, plus {@link #LOOSE}. */
        public NbtCompound extras = new NbtCompound();

        public boolean has(String partKey) {
            return extras.contains(partKey);
        }
    }

    /**
     * A kind of player-attached storage that is not {@code PlayerInventory}.
     *
     * <p>Implementations must never drop anything into the world and must treat
     * {@link #capture} failing as "not captured" (return null) rather than "empty".
     */
    interface Part {
        /** NBT key inside {@link Snapshot#extras}. Never change one: saves carry it. */
        String key();

        /** The part's contents, or {@code null} when they could not be read in full. */
        NbtElement capture(ServerPlayerEntity player);

        /** Empty the part. {@code owner} is what the real side captured for it. */
        void clear(ServerPlayerEntity player, NbtElement owner);

        /** Replace the part's contents with {@code saved}. */
        void apply(ServerPlayerEntity player, NbtElement saved);
    }

    /**
     * Loose stacks that would not fit back into the inventory when they were folded into it at
     * capture time. Not a {@link Part}: it rides along with the inventory, which is always swapped.
     */
    static final String LOOSE = "looseOverflow";

    /** Every non-inventory part, in swap order. Add new player-attached storage HERE. */
    static final List<Part> PARTS = List.of(new Experience(), new EnderChest(), new Backpacks());

    // ─── Transitions ───────────────────────────────────────────────────────────

    /** Capture every part the player has. */
    public static Snapshot capture(ServerPlayerEntity player) {
        return capture(player, null);
    }

    /**
     * Capture the player's loadout, limited to the parts {@code mask} holds ({@code null} = all).
     *
     * <p>Loose stacks - the cursor, the crafting grid, whatever an open container hands back when
     * it closes - are folded into the inventory first. They belong to whichever side is being
     * captured; leaving them where they are is how they used to ride across a swap.
     */
    public static Snapshot capture(ServerPlayerEntity player, Snapshot mask) {
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack loose : harvestLoose(player)) {
            inventory.insertStack(loose);
            if (!loose.isEmpty()) overflow.add(loose);
        }

        Snapshot snap = new Snapshot();
        snap.inventory = inventory.writeNbt(new NbtList());
        //? if <=1.21.4 {
        snap.selectedSlot = inventory.selectedSlot;
        //?} else
        /*snap.selectedSlot = inventory.getSelectedSlot();*/
        if (mask == null || mask.accessoriesCaptured) {
            snap.accessories = AccessoryStash.save(player);
            snap.accessoriesCaptured = true;
        }
        for (Part part : PARTS) {
            if (mask != null && !mask.has(part.key())) continue;
            NbtElement data;
            try {
                data = part.capture(player);
            } catch (Throwable t) {
                CrafticsMod.LOGGER.warn("[Infinite] could not capture {} for {}: {}",
                    part.key(), player.getName().getString(), t.toString());
                data = null;
            }
            if (data != null) snap.extras.put(part.key(), data);
        }
        if (!overflow.isEmpty()) snap.extras.put(LOOSE, encodeStacks(player, overflow));
        return snap;
    }

    /**
     * Strip the player of every part {@code owner} captured, dropping nothing. Loose stacks are
     * discarded: whatever they are, the side they belong to is being thrown away or is already
     * safe in a snapshot.
     */
    public static void clear(ServerPlayerEntity player, Snapshot owner) {
        harvestLoose(player);
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.markDirty();
        if (owner.accessoriesCaptured) AccessoryStash.clear(player);
        for (Part part : PARTS) {
            if (!owner.has(part.key())) continue;
            try {
                part.clear(player, owner.extras.get(part.key()));
            } catch (Throwable t) {
                CrafticsMod.LOGGER.warn("[Infinite] could not clear {} for {}: {}",
                    part.key(), player.getName().getString(), t.toString());
            }
        }
    }

    /**
     * Replace the player's loadout with {@code snap}. Only parts present in both {@code snap} and
     * {@code mask} are touched; anything else stays as it is.
     */
    public static void apply(ServerPlayerEntity player, Snapshot snap, Snapshot mask) {
        harvestLoose(player);
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.readNbt(snap.inventory);
        //? if <=1.21.4 {
        inventory.selectedSlot = Math.max(0, Math.min(snap.selectedSlot, 8));
        //?} else
        /*inventory.setSelectedSlot(Math.max(0, Math.min(snap.selectedSlot, 8)));*/
        inventory.markDirty();
        if (snap.accessoriesCaptured && mask.accessoriesCaptured) {
            AccessoryStash.restore(player, snap.accessories);
        }
        for (Part part : PARTS) {
            if (!snap.has(part.key()) || !mask.has(part.key())) continue;
            try {
                part.apply(player, snap.extras.get(part.key()));
            } catch (Throwable t) {
                CrafticsMod.LOGGER.warn("[Infinite] could not restore {} for {}: {}",
                    part.key(), player.getName().getString(), t.toString());
            }
        }
        if (snap.extras.get(LOOSE) instanceof NbtList loose) {
            for (ItemStack stack : decodeStacks(player, loose)) inventory.offerOrDrop(stack);
        }
    }

    /**
     * A hardcore wipe: everything the player wears or carries goes, with nothing captured. The
     * ender chest is deliberately NOT carried gear - it is storage, like the island's chests.
     * Vanilla XP is left to the caller, which already zeroes it.
     */
    public static void wipeCarried(ServerPlayerEntity player) {
        harvestLoose(player);
        player.getInventory().clear();
        player.getInventory().markDirty();
        AccessoryStash.clear(player);
        BackpackedCompat.clearWorn(player, null);
    }

    // ─── PlayerData bridge ─────────────────────────────────────────────────────

    /** The pre-run loadout stashed on {@code pd}. */
    public static Snapshot fromStash(CrafticsSavedData.PlayerData pd) {
        Snapshot s = new Snapshot();
        s.inventory = pd.infiniteStashInventory;
        s.selectedSlot = pd.infiniteStashSelectedSlot;
        s.accessories = pd.infiniteStashAccessories;
        s.accessoriesCaptured = pd.infiniteStashAccessoriesCaptured;
        s.extras = pd.infiniteStashExtras;
        return s;
    }

    public static void toStash(CrafticsSavedData.PlayerData pd, Snapshot s) {
        pd.infiniteStashInventory = s.inventory;
        pd.infiniteStashSelectedSlot = s.selectedSlot;
        pd.infiniteStashAccessories = s.accessories;
        pd.infiniteStashAccessoriesCaptured = s.accessoriesCaptured;
        pd.infiniteStashExtras = s.extras;
    }

    public static void clearStash(CrafticsSavedData.PlayerData pd) {
        toStash(pd, new Snapshot());
    }

    /** A parked run's loadout, saved on its host's {@code pd}. */
    public static Snapshot fromParked(CrafticsSavedData.PlayerData pd) {
        Snapshot s = new Snapshot();
        s.inventory = pd.infiniteParkedInventory;
        s.selectedSlot = pd.infiniteParkedSelectedSlot;
        s.accessories = pd.infiniteParkedAccessories;
        s.accessoriesCaptured = pd.infiniteParkedAccessoriesCaptured;
        s.extras = pd.infiniteParkedExtras;
        return s;
    }

    public static void toParked(CrafticsSavedData.PlayerData pd, Snapshot s) {
        pd.infiniteParkedInventory = s.inventory;
        pd.infiniteParkedSelectedSlot = s.selectedSlot;
        pd.infiniteParkedAccessories = s.accessories;
        pd.infiniteParkedAccessoriesCaptured = s.accessoriesCaptured;
        pd.infiniteParkedExtras = s.extras;
    }

    public static void clearParked(CrafticsSavedData.PlayerData pd) {
        toParked(pd, new Snapshot());
    }

    // ─── Loose stacks ──────────────────────────────────────────────────────────

    /**
     * Pull every stack the player holds outside their inventory slots and return it.
     *
     * <p>Vanilla only hands these back on a real close, and none of the swaps caused one: a
     * Craftics popup replaces the inventory screen on the client without telling the server, and
     * the arena and hub share a dimension, so even the teleport never flushes them. Whatever sat
     * on the cursor or in the crafting grid at a swap came out on the other side.
     *
     * <p>A container the server still has open is closed for real, so its own {@code onClosed}
     * returns its cursor and input slots. That goes through {@code offerOrDrop}, so the inventory
     * is parked empty for the duration: everything lands in a slot to be collected here, never on
     * the floor. The player's own 2x2 grid is emptied directly - closing it would send a close
     * packet and dismiss whatever Craftics screen is up.
     */
    static List<ItemStack> harvestLoose(ServerPlayerEntity player) {
        List<ItemStack> loose = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        if (player.currentScreenHandler != player.playerScreenHandler) {
            NbtList held = inventory.writeNbt(new NbtList());
            inventory.clear();
            player.closeHandledScreen();
            for (int i = 0; i < inventory.size(); i++) {
                ItemStack stack = inventory.removeStack(i);
                if (!stack.isEmpty()) loose.add(stack);
            }
            inventory.readNbt(held);
        }
        PlayerScreenHandler own = player.playerScreenHandler;
        var grid = own.getCraftingInput();
        for (int i = 0; i < grid.size(); i++) {
            ItemStack stack = grid.removeStack(i);
            if (!stack.isEmpty()) loose.add(stack);
        }
        ItemStack cursor = own.getCursorStack();
        if (!cursor.isEmpty()) {
            loose.add(cursor);
            own.setCursorStack(ItemStack.EMPTY);
        }
        own.sendContentUpdates();
        return loose;
    }

    private static RegistryOps<NbtElement> ops(ServerPlayerEntity player) {
        return player.getEntityWorld().getRegistryManager().getOps(NbtOps.INSTANCE);
    }

    private static NbtList encodeStacks(ServerPlayerEntity player, List<ItemStack> stacks) {
        NbtList out = new NbtList();
        for (ItemStack stack : stacks) {
            ItemStack.CODEC.encodeStart(ops(player), stack).result().ifPresent(out::add);
        }
        return out;
    }

    private static List<ItemStack> decodeStacks(ServerPlayerEntity player, NbtList list) {
        List<ItemStack> out = new ArrayList<>();
        for (NbtElement element : list) {
            ItemStack.CODEC.parse(ops(player), element).result()
                .filter(s -> !s.isEmpty()).ifPresent(out::add);
        }
        return out;
    }

    // ─── Parts ─────────────────────────────────────────────────────────────────

    /**
     * Vanilla XP. Run kills pay XP, and real XP buys things inside a run (respecs, the combat
     * enchanting table, Backpacked bay unlocks), so a shared pool leaked both ways.
     */
    static final class Experience implements Part {
        @Override public String key() { return "xp"; }

        @Override
        public NbtElement capture(ServerPlayerEntity player) {
            NbtCompound xp = new NbtCompound();
            xp.putInt("level", player.experienceLevel);
            xp.putFloat("progress", player.experienceProgress);
            xp.putInt("total", player.totalExperience);
            return xp;
        }

        @Override
        public void clear(ServerPlayerEntity player, NbtElement owner) {
            set(player, 0, 0f, 0);
        }

        @Override
        public void apply(ServerPlayerEntity player, NbtElement saved) {
            if (!(saved instanceof NbtCompound xp)) return;
            //? if <=1.21.4 {
            set(player, xp.getInt("level"), xp.getFloat("progress"), xp.getInt("total"));
            //?} else
            /*set(player, xp.getInt("level", 0), xp.getFloat("progress", 0f), xp.getInt("total", 0));*/
        }

        private static void set(ServerPlayerEntity player, int level, float progress, int total) {
            // setExperienceLevel also marks the client's XP bar stale, so it resyncs next tick
            // with all three values below.
            player.setExperienceLevel(Math.max(0, level));
            player.experienceProgress = Math.max(0f, Math.min(progress, 0.9999f));
            player.totalExperience = Math.max(0, total);
        }
    }

    /**
     * The vanilla ender chest. Not reachable in a normal run, but an event room is survival mode
     * and an ender chest can be bought or crafted, so an unswapped one was a door between the
     * real save and the run.
     */
    static final class EnderChest implements Part {
        @Override public String key() { return "enderChest"; }

        @Override
        public NbtElement capture(ServerPlayerEntity player) {
            return player.getEnderChestInventory().toNbtList(player.getEntityWorld().getRegistryManager());
        }

        @Override
        public void clear(ServerPlayerEntity player, NbtElement owner) {
            player.getEnderChestInventory().clear();
        }

        @Override
        public void apply(ServerPlayerEntity player, NbtElement saved) {
            if (!(saved instanceof NbtList list)) return;
            // readNbtList empties every slot before it fills any, so this is a replace.
            player.getEnderChestInventory().readNbtList(list, player.getEntityWorld().getRegistryManager());
        }
    }

    /** Worn Backpacked packs (contents included) and the player's bay unlocks. */
    static final class Backpacks implements Part {
        @Override public String key() { return "backpacks"; }

        @Override
        public NbtElement capture(ServerPlayerEntity player) {
            return BackpackedCompat.saveWorn(player);
        }

        @Override
        public void clear(ServerPlayerEntity player, NbtElement owner) {
            BackpackedCompat.clearWorn(player, owner instanceof NbtCompound c ? c : null);
        }

        @Override
        public void apply(ServerPlayerEntity player, NbtElement saved) {
            if (saved instanceof NbtCompound c) BackpackedCompat.restoreWorn(player, c);
        }
    }
}
