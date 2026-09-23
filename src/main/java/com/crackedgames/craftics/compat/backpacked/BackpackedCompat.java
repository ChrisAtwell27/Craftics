package com.crackedgames.craftics.compat.backpacked;

import com.crackedgames.craftics.CrafticsMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Compatibility module for Backpacked (MrCrayfish).
 *
 * <p>Craftics hands out a lot of loot in a short space of time, and a run has nowhere to put it
 * once the inventory fills - the overflow chest exists precisely because that happens. A worn
 * backpack is the missing shelf, so battle loot that will not fit anywhere else goes in there
 * before the overflow screen is put in the player's face.
 *
 * <h2>Augments</h2>
 *
 * <p>Backpacked's augments are upgrades the player deliberately fitted, and most of them are
 * written against hooks that a turn-based arena never fires: Reforge waits on an XP orb Craftics
 * never spawns, Quiverlink feeds a bow Craftics resolves itself, Immortal watches a death the
 * combat manager handles before vanilla sees it. Left alone they are not merely quiet, they are
 * dead weight the player paid for. Each one Craftics honours is re-implemented against the
 * equivalent Craftics hook, gated on the augment actually being fitted so nobody gets a benefit
 * they did not equip.
 *
 * <h2>No compile-time dependency</h2>
 *
 * <p>Reached entirely by reflection, like every other Craftics compat module, so the mod builds
 * and runs with Backpacked absent. Two things make this cheap rather than painful: the handle
 * lookup happens once, and Backpacked's own inventory type implements vanilla {@code Inventory},
 * so only <em>obtaining</em> a backpack needs reflection - filling it is ordinary Minecraft code.
 *
 * <p>Every failure path degrades to "no backpacks", which reads as the mod being absent. A compat
 * module is the last thing that should be able to break loot delivery or a death screen.
 *
 * <h2>Infinite Mode</h2>
 *
 * <p>Worn packs live in Backpacked's own player data, not in {@code PlayerInventory}, so the
 * Infinite Mode stash never saw them: a player walked into a "start from nothing" run wearing
 * their real packs, and walked out with whatever run loot they had stuffed in. {@link #saveWorn},
 * {@link #clearWorn} and {@link #restoreWorn} are how {@code RunLoadout} swaps them with the
 * rest of the loadout.
 */
public final class BackpackedCompat {

    public static final String MOD_ID = "backpacked";

    /** The mixin interface Backpacked puts on the player, holding their worn backpacks. */
    private static final String ACCESS_CLASS =
        "com.mrcrayfish.backpacked.inventory.BackpackedInventoryAccess";

    private static final String INVENTORY_CLASS =
        "com.mrcrayfish.backpacked.inventory.BackpackInventory";

    private static final String HELPER_CLASS = "com.mrcrayfish.backpacked.BackpackHelper";

    private static final String AUGMENT_PKG = "com.mrcrayfish.backpacked.common.augment.impl.";

    // Augment keys. The value is the simple class name; every one of them exposes a
    // "public static final AugmentType TYPE" field, which is the handle we actually want.
    public static final String FUNNELLING = "FunnellingAugment";
    public static final String LOOTBOUND  = "LootboundAugment";
    public static final String IMMORTAL   = "ImmortalAugment";
    public static final String GIANT      = "GiantAugment";
    public static final String REFORGE    = "ReforgeAugment";
    public static final String RECALL     = "RecallAugment";
    public static final String QUIVERLINK = "QuiverlinkAugment";

    private static boolean loaded = false;
    private static boolean resolved = false;

    private static Class<?> accessClass;
    private static Method countMethod;       // backpacked$GetBackpackInventoryCount()
    private static Method getMethod;         // backpacked$GetBackpackInventory(int)
    private static Method isAllowedItem;     // static BackpackInventory.isAllowedItem(ItemStack)
    private static Method saveItemsToStack;  // BackpackInventory.saveItemsToStack()
    private static Method getBackpackStack;  // BackpackInventory.getBackpackStack()
    private static Method findAugment;       // static BackpackHelper.findAugment(ItemStack, AugmentType)

    // Worn-pack storage, for the Infinite Mode stash (see "Stash" below). Resolved separately
    // from the handles above so a Backpacked update that moves one of these costs the stash,
    // not loot delivery.
    private static boolean stashResolved = false;
    private static Method getBackpacks;       // static BackpackHelper.getBackpacks(PlayerEntity) -> live DefaultedList
    private static Method removeAllBackpacks; // static BackpackHelper.removeAllBackpacks(PlayerEntity)
    private static Method setBackpackStack;   // static BackpackHelper.setBackpackStack(PlayerEntity, ItemStack, int)
    private static Method equipBackpack;      // static BackpackHelper.equipBackpack(PlayerEntity, ItemStack)
    private static Method getBayUnlocks;      // static BackpackHelper.getBackpackUnlockableSlots(PlayerEntity)
    private static Method setBayUnlocks;      // static BackpackHelper.setBackpackUnlockableSlots(PlayerEntity, UnlockableSlots)
    private static java.lang.reflect.Constructor<?> newBayUnlocks; // new UnlockableSlots(int maxSlots)
    private static Method maxEquipable;       // static ManagementInventory.getMaxEquipable()
    @SuppressWarnings("rawtypes")
    private static com.mojang.serialization.Codec bayUnlocksCodec; // UnlockableSlots.CODEC

    /** Augment key to its AugmentType singleton. Absent key = that augment could not be reached. */
    private static final Map<String, Object> AUGMENT_TYPES = new HashMap<>();
    /** Lazily cached accessor methods on individual augment records. */
    private static final Map<String, Method> AUGMENT_METHODS = new HashMap<>();

    private BackpackedCompat() {}

    /** Flag mod presence. Does not touch its classes - they may not be loaded yet. */
    public static void init() {
        loaded = FabricLoader.getInstance().isModLoaded(MOD_ID);
        if (!loaded) {
            CrafticsMod.LOGGER.debug("[Craftics x Backpacked] mod not loaded - skipping");
        }
    }

    public static boolean isLoaded() { return loaded; }

    /**
     * Look up the reflective handles, once.
     *
     * <p>Deferred rather than done in {@link #init}: Fabric gives no ordering guarantee that
     * Backpacked's classes are loadable during our entrypoint, and a failed lookup here would be
     * cached as "absent" for the whole session.
     */
    private static synchronized void resolve() {
        if (resolved || !loaded) return;
        resolved = true;
        try {
            accessClass = Class.forName(ACCESS_CLASS);
            countMethod = accessClass.getMethod("backpacked$GetBackpackInventoryCount");
            getMethod = accessClass.getMethod("backpacked$GetBackpackInventory", int.class);

            Class<?> inventoryClass = Class.forName(INVENTORY_CLASS);
            isAllowedItem = inventoryClass.getMethod("isAllowedItem", ItemStack.class);
            saveItemsToStack = inventoryClass.getMethod("saveItemsToStack");
            getBackpackStack = inventoryClass.getMethod("getBackpackStack");

            Class<?> augmentType = Class.forName(
                "com.mrcrayfish.backpacked.common.augment.AugmentType");
            findAugment = Class.forName(HELPER_CLASS)
                .getMethod("findAugment", ItemStack.class, augmentType);

            // Each augment is resolved independently: one of them disappearing in a Backpacked
            // update costs that single feature rather than the whole module.
            for (String key : new String[] {
                    FUNNELLING, LOOTBOUND, IMMORTAL, GIANT, REFORGE, RECALL, QUIVERLINK }) {
                try {
                    AUGMENT_TYPES.put(key,
                        Class.forName(AUGMENT_PKG + key).getField("TYPE").get(null));
                } catch (Throwable t) {
                    CrafticsMod.LOGGER.info("[Craftics x Backpacked] augment {} unavailable ({})",
                        key, t.toString());
                }
            }

            CrafticsMod.LOGGER.info(
                "[Craftics x Backpacked] enabled - {} of 7 augments wired", AUGMENT_TYPES.size());
        } catch (Throwable t) {
            accessClass = null;
            CrafticsMod.LOGGER.warn(
                "[Craftics x Backpacked] could not reach Backpacked's inventory API ({}). "
                + "Backpacks will behave as though none were worn.", t.toString());
        }
    }

    /**
     * One worn backpack: the live inventory to move items through, and the item stack that
     * carries its augments.
     *
     * @param index which bay it is worn in, needed to address a slot again later
     */
    public record Pack(int index, Inventory inventory, ItemStack stack) {}

    /** Every backpack this player is wearing, in bay order. */
    public static List<Pack> packsOf(PlayerEntity player) {
        resolve();
        if (accessClass == null || player == null || !accessClass.isInstance(player)) {
            return List.of();
        }
        try {
            int count = (int) countMethod.invoke(player);
            List<Pack> out = new ArrayList<>(Math.max(0, count));
            for (int i = 0; i < count; i++) {
                Object inv = getMethod.invoke(player, i);
                // Backpacked's inventory implements vanilla Inventory, so from here on this is
                // ordinary code. A null slot is a bay with no backpack in it.
                if (!(inv instanceof Inventory typed)) continue;
                ItemStack stack = ItemStack.EMPTY;
                try {
                    Object s = getBackpackStack.invoke(inv);
                    if (s instanceof ItemStack is) stack = is;
                } catch (Throwable ignored) {
                    // Augments become unreadable for this pack; storage still works.
                }
                out.add(new Pack(i, typed, stack));
            }
            return out;
        } catch (Throwable t) {
            CrafticsMod.LOGGER.warn("[Craftics x Backpacked] backpack lookup failed: {}", t.toString());
            return List.of();
        }
    }

    /**
     * The augment of the given kind fitted to this pack, or {@code null}.
     *
     * <p>Returned as {@code Object} on purpose - the caller cannot name the type at compile time
     * either. Anything more than a presence check goes through {@link #callAugment}.
     */
    public static Object augmentOn(Pack pack, String key) {
        resolve();
        Object type = AUGMENT_TYPES.get(key);
        if (type == null || findAugment == null || pack == null || pack.stack().isEmpty()) {
            return null;
        }
        try {
            return findAugment.invoke(null, pack.stack(), type);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Whether any worn backpack has this augment fitted. */
    public static boolean hasAugment(PlayerEntity player, String key) {
        for (Pack pack : packsOf(player)) {
            if (augmentOn(pack, key) != null) return true;
        }
        return false;
    }

    /** Invoke a no-arg or one-arg accessor on a resolved augment instance. */
    private static Object callAugment(Object augment, String key, String method, Class<?> paramType,
                                      Object param) {
        if (augment == null) return null;
        String cacheKey = key + "#" + method;
        try {
            Method m = AUGMENT_METHODS.get(cacheKey);
            if (m == null) {
                m = paramType == null
                    ? augment.getClass().getMethod(method)
                    : augment.getClass().getMethod(method, paramType);
                AUGMENT_METHODS.put(cacheKey, m);
            }
            return paramType == null ? m.invoke(augment) : m.invoke(augment, param);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Whether Backpacked will accept this item at all (it refuses to nest backpacks). */
    private static boolean accepts(ItemStack stack) {
        resolve();
        if (isAllowedItem == null) return true;
        try {
            return (boolean) isAllowedItem.invoke(null, stack);
        } catch (Throwable t) {
            return true;   // a failed check must not silently swallow loot
        }
    }

    /**
     * Write a pack's contents back into the item that holds them.
     *
     * <p>Public because several Craftics paths hand out a <em>live</em> stack from inside a
     * backpack and decrement it themselves - a totem being spent, a Mending item being repaired.
     * Those mutations are real but unsaved until this runs, and losing them means an item that
     * came back after a relog. Idempotent, so calling it defensively costs nothing.
     */
    public static void flush(PlayerEntity player) {
        if (!loaded) return;
        for (Pack pack : packsOf(player)) {
            pack.inventory().markDirty();
            if (saveItemsToStack == null) continue;
            try { saveItemsToStack.invoke(pack.inventory()); } catch (Throwable ignored) {}
        }
    }

    // -- Storage --------------------------------------------------------------

    /**
     * Push as much of {@code stack} into the player's worn backpacks as will fit.
     *
     * <p>Mutates {@code stack} down as it goes, exactly like vanilla insertion, so the caller can
     * carry on with whatever is left. Merges into part-filled stacks before taking an empty slot,
     * so a backpack does not fragment into thirty single-item piles.
     *
     * @return true if anything at all moved
     */
    public static boolean storeLoot(ServerPlayerEntity player, ItemStack stack) {
        return store(player, stack, false);
    }

    /**
     * The same, but only into packs whose Funnelling or Lootbound augment asks for this item.
     *
     * <p>Called <em>before</em> the inventory rather than after it, because that is what those
     * augments mean: Funnelling exists to put picked-up items in the pack instead of the
     * inventory, and Lootbound exists to pull mob drops straight in. Combat rewards are mob drops
     * that never got to be entities, so a player who fitted either augment has already said where
     * they want this loot to land.
     *
     * @return true if anything at all moved
     */
    public static boolean funnelLoot(ServerPlayerEntity player, ItemStack stack) {
        return store(player, stack, true);
    }

    private static boolean store(ServerPlayerEntity player, ItemStack stack, boolean filtered) {
        if (!loaded || stack == null || stack.isEmpty()) return false;
        if (!accepts(stack)) return false;

        boolean moved = false;
        for (Pack pack : packsOf(player)) {
            if (stack.isEmpty()) break;
            if (filtered && !wantsFunnelled(pack, stack)) continue;
            if (insertInto(pack.inventory(), stack)) {
                moved = true;
                pack.inventory().markDirty();
                if (saveItemsToStack != null) {
                    try { saveItemsToStack.invoke(pack.inventory()); } catch (Throwable ignored) {}
                }
            }
        }
        return moved;
    }

    /**
     * Whether this pack's augments claim this piece of loot.
     *
     * <p>Funnelling owns the filter - its {@code test} is the same allow/disallow list the player
     * edited in its settings screen. Lootbound is a companion that extends that filter to drops,
     * so it claims loot only while its "mobs" toggle is on, combat rewards being mob drops.
     */
    private static boolean wantsFunnelled(Pack pack, ItemStack stack) {
        Object funnelling = augmentOn(pack, FUNNELLING);
        Object lootbound = augmentOn(pack, LOOTBOUND);
        if (funnelling == null && lootbound == null) return false;

        boolean mobsOn = Boolean.TRUE.equals(
            callAugment(lootbound, LOOTBOUND, "mobs", null, null));
        // A filter we could not read counts as a refusal: it must not silently swallow loot into
        // a pack the player may have filtered precisely to keep it out of.
        boolean filterPass = Boolean.TRUE.equals(
            callAugment(funnelling, FUNNELLING, "test", ItemStack.class, stack));

        return AugmentRules.funnelClaims(
            funnelling != null, lootbound != null, mobsOn, filterPass);
    }

    /** Merge-then-fill, the same order vanilla uses. */
    private static boolean insertInto(Inventory backpack, ItemStack stack) {
        boolean moved = false;
        int size = backpack.size();

        for (int i = 0; i < size && !stack.isEmpty(); i++) {
            ItemStack slot = backpack.getStack(i);
            if (slot.isEmpty() || !ItemStack.areItemsAndComponentsEqual(slot, stack)) continue;
            int room = Math.min(slot.getMaxCount(), backpack.getMaxCountPerStack()) - slot.getCount();
            if (room <= 0) continue;
            int take = Math.min(room, stack.getCount());
            slot.increment(take);
            stack.decrement(take);
            moved = true;
        }

        for (int i = 0; i < size && !stack.isEmpty(); i++) {
            if (!backpack.getStack(i).isEmpty()) continue;
            if (!backpack.isValid(i, stack)) continue;   // a locked or filtered slot
            int take = Math.min(stack.getCount(),
                Math.min(stack.getMaxCount(), backpack.getMaxCountPerStack()));
            ItemStack placed = stack.copy();
            placed.setCount(take);
            backpack.setStack(i, placed);
            stack.decrement(take);
            moved = true;
        }
        return moved;
    }

    // -- Quiverlink -----------------------------------------------------------

    /**
     * Whether the player wants the backpack searched for ammunition before their inventory.
     *
     * <p>Quiverlink carries a Priority setting and it <b>defaults to BACKPACK</b>, so assuming
     * "inventory first" would be wrong for a player who never opened the settings screen as well
     * as for one who set it deliberately.
     */
    public static boolean preferBackpackArrows(ServerPlayerEntity player) {
        if (!loaded) return false;
        for (Pack pack : packsOf(player)) {
            Object quiver = augmentOn(pack, QUIVERLINK);
            if (quiver == null) continue;
            Object priority = callAugment(quiver, QUIVERLINK, "priority", null, null);
            if (priority == null) return true;   // unreadable: fall back to the mod's own default
            if (priority instanceof Enum<?> e && "BACKPACK".equals(e.name())) return true;
        }
        return false;
    }

    /**
     * An arrow from a Quiverlink backpack, or {@code null}.
     *
     * <p>Only packs with the augment are searched: Quiverlink is Backpacked's own gate on drawing
     * ammunition out of storage, and a pack without it is storage and nothing more.
     *
     * @param consume true to actually take the arrow, false to merely look
     */
    public static ItemStack drawArrow(ServerPlayerEntity player, boolean consume) {
        if (!loaded) return null;
        // Plain arrows first, for the same reason the inventory search prefers them: a shot that
        // does not use a tipped arrow's effect should not be the thing that spends it.
        ItemStack plain = drawArrow(player, consume, true);
        if (plain != null) return plain;
        return drawArrow(player, consume, false);
    }

    private static ItemStack drawArrow(ServerPlayerEntity player, boolean consume, boolean plainOnly) {
        for (Pack pack : packsOf(player)) {
            if (augmentOn(pack, QUIVERLINK) == null) continue;
            for (int i = 0; i < pack.inventory().size(); i++) {
                ItemStack slot = pack.inventory().getStack(i);
                if (slot.isEmpty() || !isArrow(slot)) continue;
                if (plainOnly && !slot.isOf(net.minecraft.item.Items.ARROW)) continue;
                if (!consume) return slot;
                ItemStack taken = slot.copy();
                taken.setCount(1);
                slot.decrement(1);
                pack.inventory().markDirty();
                if (saveItemsToStack != null) {
                    try { saveItemsToStack.invoke(pack.inventory()); } catch (Throwable ignored) {}
                }
                return taken;
            }
        }
        return null;
    }

    /** Any ammunition a bow or crossbow can fire, matching Craftics' own definition. */
    private static boolean isArrow(ItemStack stack) {
        return stack.isOf(net.minecraft.item.Items.ARROW)
            || stack.isOf(net.minecraft.item.Items.TIPPED_ARROW)
            || stack.isOf(net.minecraft.item.Items.SPECTRAL_ARROW);
    }

    // -- Immortal -------------------------------------------------------------

    /**
     * A totem stored in an Immortal backpack, or {@code null}.
     *
     * <p>The returned stack is live, so the caller decrements it exactly like a totem found in
     * the inventory - but a backpack does not persist that on its own, so callers must
     * {@link #flush} afterwards.
     *
     * @param isTotem Craftics' own definition of a totem, which includes modded ones
     */
    public static ItemStack findTotem(ServerPlayerEntity player, Predicate<ItemStack> isTotem) {
        if (!loaded) return null;
        for (Pack pack : packsOf(player)) {
            if (augmentOn(pack, IMMORTAL) == null) continue;
            for (int i = 0; i < pack.inventory().size(); i++) {
                ItemStack slot = pack.inventory().getStack(i);
                if (!slot.isEmpty() && isTotem.test(slot)) return slot;
            }
        }
        return null;
    }

    // -- Giant ----------------------------------------------------------------

    /**
     * Movement points this player's backpacks cost them, as a positive number.
     *
     * <p>Giant trades weight for space, which vanilla Backpacked expresses only as flavour text
     * ("Why does the backpack feel heavier?"). On a grid, weight has an obvious meaning, so it
     * costs one tile of movement per turn.
     *
     * <p>Never more than one tile however many Giant packs are worn, and see
     * {@code PlayerCombatStats.getSetSpeedBonus} for the floor that keeps a player able to move
     * at all.
     */
    public static int giantSpeedPenalty(ServerPlayerEntity player) {
        if (!loaded) return 0;
        return hasAugment(player, GIANT) ? 1 : 0;
    }

    // -- Reforge --------------------------------------------------------------

    /**
     * Damaged items inside Reforge backpacks, as live stacks the caller may repair.
     *
     * <p>Callers must {@link #flush} once they have finished repairing.
     */
    public static List<ItemStack> reforgeCandidates(ServerPlayerEntity player) {
        if (!loaded) return List.of();
        List<ItemStack> out = new ArrayList<>();
        for (Pack pack : packsOf(player)) {
            if (augmentOn(pack, REFORGE) == null) continue;
            for (int i = 0; i < pack.inventory().size(); i++) {
                ItemStack slot = pack.inventory().getStack(i);
                if (!slot.isEmpty() && slot.isDamaged()) out.add(slot);
            }
        }
        return out;
    }

    // -- Recall ---------------------------------------------------------------

    /**
     * Whether this pack survives the death coin-flip untouched.
     *
     * <p>Recall's promise is that the backpack comes home when you die. Craftics' defeat screen is
     * where a run's belongings are actually at risk, so that is where the promise is kept.
     *
     * <p>A linked shelf is required, matching the augment's own rule: with no shelf linked, or the
     * shelf gone, Backpacked drops the pack on death as normal, so an unlinked Recall must not
     * protect anything here either.
     */
    public static boolean isRecallProtected(Pack pack) {
        Object recall = augmentOn(pack, RECALL);
        if (recall == null) return false;
        Object key = callAugment(recall, RECALL, "shelfKey", null, null);
        return key instanceof java.util.Optional<?> opt && opt.isPresent();
    }

    /** One backpack slot exposed to the death coin-flip. */
    public record LosableSlot(int pack, int slot, ItemStack stack) {}

    /**
     * Every backpack slot the death coin-flip is allowed to roll.
     *
     * <p>Backpack contents used to sit outside the flip entirely, which quietly made a worn
     * backpack a death-proof vault - and Craftics filling it with battle loot would have turned
     * that into the obvious way to play. They are at risk like the rest of the run now, and Recall
     * is how a player buys them back out of it.
     */
    public static List<LosableSlot> losableSlots(ServerPlayerEntity player) {
        if (!loaded) return List.of();
        List<LosableSlot> out = new ArrayList<>();
        for (Pack pack : packsOf(player)) {
            if (isRecallProtected(pack)) continue;
            for (int i = 0; i < pack.inventory().size(); i++) {
                ItemStack slot = pack.inventory().getStack(i);
                if (!slot.isEmpty()) out.add(new LosableSlot(pack.index(), i, slot.copy()));
            }
        }
        return out;
    }

    // -- Stash ----------------------------------------------------------------

    /**
     * Look up the worn-pack handles the Infinite Mode stash needs, once.
     *
     * <p>Kept apart from {@link #resolve} for the same reason each augment is resolved on its
     * own: losing one of these in a Backpacked update should cost the stash, not loot delivery.
     */
    private static synchronized void resolveStash() {
        if (stashResolved || !loaded) return;
        stashResolved = true;
        try {
            Class<?> helper = Class.forName(HELPER_CLASS);
            Class<?> unlocks = Class.forName(
                "com.mrcrayfish.backpacked.common.backpack.UnlockableSlots");
            getBackpacks = helper.getMethod("getBackpacks", PlayerEntity.class);
            removeAllBackpacks = helper.getMethod("removeAllBackpacks", PlayerEntity.class);
            setBackpackStack = helper.getMethod("setBackpackStack",
                PlayerEntity.class, ItemStack.class, int.class);
            equipBackpack = helper.getMethod("equipBackpack", PlayerEntity.class, ItemStack.class);
            getBayUnlocks = helper.getMethod("getBackpackUnlockableSlots", PlayerEntity.class);
            setBayUnlocks = helper.getMethod("setBackpackUnlockableSlots", PlayerEntity.class, unlocks);
            newBayUnlocks = unlocks.getConstructor(int.class);
            maxEquipable = Class.forName("com.mrcrayfish.backpacked.inventory.ManagementInventory")
                .getMethod("getMaxEquipable");
            bayUnlocksCodec = (com.mojang.serialization.Codec) unlocks.getField("CODEC").get(null);
        } catch (Throwable t) {
            getBackpacks = null;
            CrafticsMod.LOGGER.warn("[Craftics x Backpacked] could not reach Backpacked's worn-pack "
                + "storage ({}). Worn backpacks will be left in place across Infinite Mode.",
                t.toString());
        }
    }

    /** Registry-aware NBT ops for this player's world, as {@code AccessoryStash} uses. */
    private static net.minecraft.registry.RegistryOps<net.minecraft.nbt.NbtElement> ops(
            PlayerEntity player) {
        return player.getEntityWorld().getRegistryManager().getOps(net.minecraft.nbt.NbtOps.INSTANCE);
    }

    /**
     * Snapshot every worn backpack - contents included - and the player's bay unlocks, for the
     * Infinite Mode stash.
     *
     * <p>Returns {@code null} when the packs could not be read in full: Backpacked absent, a
     * handle missing, or any one pack refusing to encode. Null is a promise to the caller, not a
     * shrug - the stash only strips a player of what it managed to save, so a partial snapshot
     * here would be real backpacks deleted on the way into a run.
     *
     * <p>Bay unlocks are best-effort: if they cannot be encoded the key is simply absent and
     * {@link #clearWorn} leaves the unlocks alone.
     */
    public static net.minecraft.nbt.NbtCompound saveWorn(PlayerEntity player) {
        if (!loaded || player == null) return null;
        resolveStash();
        if (getBackpacks == null) return null;
        try {
            // A backpack's contents live in its open inventory until the next player tick
            // writes them back to the item. Encoding the item first would miss whatever the
            // player moved in the last tick.
            flush(player);
            @SuppressWarnings("unchecked")
            List<ItemStack> worn = (List<ItemStack>) getBackpacks.invoke(null, player);
            net.minecraft.nbt.NbtList packs = new net.minecraft.nbt.NbtList();
            for (int bay = 0; bay < worn.size(); bay++) {
                ItemStack stack = worn.get(bay);
                if (stack == null || stack.isEmpty()) continue;
                var encoded = ItemStack.CODEC.encodeStart(ops(player), stack).result();
                if (encoded.isEmpty() || !(encoded.get() instanceof net.minecraft.nbt.NbtCompound item)) {
                    CrafticsMod.LOGGER.warn("[Craftics x Backpacked] could not stash worn pack {}; "
                        + "leaving every worn pack in place", stack);
                    return null;
                }
                net.minecraft.nbt.NbtCompound entry = new net.minecraft.nbt.NbtCompound();
                entry.putInt("bay", bay);
                entry.put("item", item);
                packs.add(entry);
            }
            net.minecraft.nbt.NbtCompound out = new net.minecraft.nbt.NbtCompound();
            out.put("packs", packs);
            try {
                @SuppressWarnings("unchecked")
                var bays = bayUnlocksCodec.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,
                    getBayUnlocks.invoke(null, player)).result();
                bays.ifPresent(b -> out.put("bays", (net.minecraft.nbt.NbtElement) b));
            } catch (Throwable ignored) {
                // Bays stay put; the packs themselves are still safe to move.
            }
            return out;
        } catch (Throwable t) {
            CrafticsMod.LOGGER.warn("[Craftics x Backpacked] worn-pack snapshot failed: {}", t.toString());
            return null;
        }
    }

    /**
     * Take every worn backpack off, dropping nothing, and - when {@code saved} carried the bay
     * unlocks - reset the bays to the config's starting count.
     *
     * <p>Only ever called once the packs are safe somewhere else (a stash) or are meant to go
     * (run loot, a hardcore wipe). Pass {@code null} to leave the bay unlocks alone.
     */
    public static void clearWorn(PlayerEntity player, net.minecraft.nbt.NbtCompound saved) {
        if (!loaded || player == null) return;
        resolveStash();
        if (getBackpacks == null) return;
        try {
            // An open backpack menu keeps writing into the inventory it was opened on. The
            // caller closes screens first; this is the backstop for the pack's own buffer.
            flush(player);
            removeAllBackpacks.invoke(null, player);
            if (saved != null && saved.contains("bays")) {
                int max = (int) maxEquipable.invoke(null);
                // A fresh set; Backpacked re-applies its configured starting unlocks the next
                // time anything reads the bays.
                setBayUnlocks.invoke(null, player, newBayUnlocks.newInstance(max));
            }
        } catch (Throwable t) {
            CrafticsMod.LOGGER.warn("[Craftics x Backpacked] could not clear worn packs: {}", t.toString());
        }
    }

    /**
     * Put a {@link #saveWorn} snapshot back on the player, replacing what is worn now.
     *
     * <p>Bay unlocks go back first, because Backpacked refuses a pack in a locked bay. A pack
     * that still cannot go back into its own bay tries any free bay, and failing that lands in
     * the inventory - a restore may move a pack, it never deletes one.
     */
    public static void restoreWorn(ServerPlayerEntity player, net.minecraft.nbt.NbtCompound saved) {
        if (!loaded || player == null || saved == null) return;
        resolveStash();
        if (getBackpacks == null) return;
        try {
            removeAllBackpacks.invoke(null, player);
            if (saved.contains("bays")) {
                net.minecraft.nbt.NbtElement baysNbt = saved.get("bays");
                @SuppressWarnings("unchecked")
                var bays = bayUnlocksCodec.parse(net.minecraft.nbt.NbtOps.INSTANCE, baysNbt).result();
                if (bays.isPresent()) setBayUnlocks.invoke(null, player, bays.get());
            }
        } catch (Throwable t) {
            CrafticsMod.LOGGER.warn("[Craftics x Backpacked] could not reset worn packs for restore: {}",
                t.toString());
        }

        //? if <=1.21.4 {
        net.minecraft.nbt.NbtList packs = saved.getList("packs", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
        //?} else
        /*net.minecraft.nbt.NbtList packs = saved.getListOrEmpty("packs");*/
        for (int i = 0; i < packs.size(); i++) {
            //? if <=1.21.4 {
            net.minecraft.nbt.NbtCompound entry = packs.getCompound(i);
            int bay = entry.getInt("bay");
            net.minecraft.nbt.NbtCompound item = entry.getCompound("item");
            //?} else {
            /*net.minecraft.nbt.NbtCompound entry = packs.getCompound(i).orElse(null);
            if (entry == null) continue;
            int bay = entry.getInt("bay", 0);
            net.minecraft.nbt.NbtCompound item = entry.getCompoundOrEmpty("item");
            *///?}
            ItemStack stack;
            try {
                stack = ItemStack.CODEC.parse(ops(player), item).result().orElse(ItemStack.EMPTY);
            } catch (Throwable t) {
                stack = ItemStack.EMPTY;
            }
            if (stack.isEmpty()) continue;   // its mod is gone; nothing left to hand back
            if (!placeWorn(player, stack, bay)) {
                player.getInventory().offerOrDrop(stack);
            }
        }
    }

    /** Wear {@code stack} in {@code bay}, else in any free bay. False when neither would take it. */
    private static boolean placeWorn(PlayerEntity player, ItemStack stack, int bay) {
        try {
            if ((boolean) setBackpackStack.invoke(null, player, stack, bay)) return true;
            // equipBackpack empties the stack it accepts, and leaves it untouched when it refuses.
            return (boolean) equipBackpack.invoke(null, player, stack);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Take {@code count} units out of one backpack slot, for an applied coin-flip loss. */
    public static void removeUnits(ServerPlayerEntity player, int packIndex, int slotIndex, int count) {
        if (!loaded || count <= 0) return;
        for (Pack pack : packsOf(player)) {
            if (pack.index() != packIndex) continue;
            if (slotIndex < 0 || slotIndex >= pack.inventory().size()) return;
            ItemStack slot = pack.inventory().getStack(slotIndex);
            if (slot.isEmpty()) return;
            if (count >= slot.getCount()) pack.inventory().setStack(slotIndex, ItemStack.EMPTY);
            else slot.decrement(count);
            pack.inventory().markDirty();
            if (saveItemsToStack != null) {
                try { saveItemsToStack.invoke(pack.inventory()); } catch (Throwable ignored) {}
            }
            return;
        }
    }
}
