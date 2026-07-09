package com.crackedgames.craftics.compat.artifacts;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reflection shim over the Accessories mod API - the slot provider that the
 * Artifacts mod uses for its head/necklace/ring/hand/belt/feet slots on Fabric.
 * Lets Craftics clear/drop accessories on death without a compile-time dependency;
 * all calls are cheap no-ops when Accessories isn't installed.
 * <p>
 * Reflection is only needed to cross the three API hops:
 *   AccessoriesCapability.get(entity) → container.getAccessories() → ExpandedSimpleContainer
 * Once we have the container, it extends {@link net.minecraft.inventory.SimpleInventory}
 * so we can use the standard {@link Inventory} interface directly for reads/writes.
 */
public final class AccessoriesReflect {

    private static final Method CAPABILITY_GET;            // AccessoriesCapability.get(LivingEntity)
    private static final Method GET_CONTAINERS;            // AccessoriesCapability.getContainers() -> Map<String, AccessoriesContainer>
    private static final Method GET_ACCESSORIES;           // AccessoriesContainer.getAccessories() -> ExpandedSimpleContainer
    private static final Method GET_COSMETIC_ACCESSORIES;  // AccessoriesContainer.getCosmeticAccessories() -> ExpandedSimpleContainer
    private static final Method MARK_CHANGED;              // AccessoriesContainer.markChanged(boolean)
    private static final Method ATTEMPT_EQUIP;            // AccessoriesCapability.attemptToEquipAccessory(ItemStack) -> SlotReference
    private static final boolean AVAILABLE;

    static {
        Method mGet = null, mGetC = null, mGetA = null, mGetCo = null, mMark = null, mEquip = null;
        boolean ok = false;
        Throwable failure = null;
        try {
            Class<?> capCls = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
            mGet = capCls.getMethod("get", LivingEntity.class);
            mGetC = capCls.getMethod("getContainers");
            mEquip = capCls.getMethod("attemptToEquipAccessory", ItemStack.class);
            Class<?> contCls = Class.forName("io.wispforest.accessories.api.AccessoriesContainer");
            mGetA = contCls.getMethod("getAccessories");
            mGetCo = contCls.getMethod("getCosmeticAccessories");
            mMark = contCls.getMethod("markChanged", boolean.class);
            ok = true;
        } catch (Throwable t) {
            failure = t;
        }
        CAPABILITY_GET = mGet;
        GET_CONTAINERS = mGetC;
        GET_ACCESSORIES = mGetA;
        GET_COSMETIC_ACCESSORIES = mGetCo;
        MARK_CHANGED = mMark;
        ATTEMPT_EQUIP = mEquip;
        AVAILABLE = ok;
        if (ok) {
            CrafticsMod.LOGGER.info("[Craftics × Accessories] Resolved AccessoriesCapability via reflection");
        } else if (failure != null) {
            CrafticsMod.LOGGER.info("[Craftics × Accessories] Accessories API not available: {}", failure.toString());
        }
    }

    private AccessoriesReflect() {}

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Equip {@code stack} into a valid Accessories slot on {@code entity} (mob or
     * player) so the Artifacts renderer draws it WORN in the right place - boots on
     * the feet, a necklace on the neck, etc. - instead of hovering as a held item.
     * Uses the mod's own {@code attemptToEquipAccessory}, which picks the first
     * slot the item is valid for. Returns true if it equipped. No-op / false when
     * Accessories isn't installed or no valid slot exists.
     */
    public static boolean equipOnMob(LivingEntity entity, ItemStack stack) {
        if (!AVAILABLE || entity == null || stack == null || stack.isEmpty()) return false;
        if (entity.getWorld() == null || entity.getWorld().isClient) return false;
        try {
            Object capability = CAPABILITY_GET.invoke(null, entity);
            if (capability == null) return false; // entity has no accessories capability
            Object slotRef = ATTEMPT_EQUIP.invoke(capability, stack.copy());
            // attemptToEquipAccessory returns a SlotReference on success, null on failure.
            return slotRef != null;
        } catch (Throwable t) {
            CrafticsMod.LOGGER.debug("[Craftics × Accessories] equipOnMob failed", t);
            return false;
        }
    }

    /**
     * Clears every accessory + cosmetic accessory slot on the given entity and spawns
     * each removed stack as an {@link ItemEntity} at the entity's position, matching
     * vanilla inventory drop behavior on death. No-op if Accessories isn't loaded,
     * if the entity is client-side, or if any reflection hop fails.
     */
    public static void dropAllAccessories(LivingEntity entity) {
        if (!AVAILABLE || entity == null) return;
        World world = entity.getWorld();
        if (world == null || world.isClient) return;
        try {
            Object capability = CAPABILITY_GET.invoke(null, entity);
            if (capability == null) return;
            Object containersObj = GET_CONTAINERS.invoke(capability);
            if (!(containersObj instanceof Map<?, ?> containers)) return;

            for (Object container : containers.values()) {
                if (container == null) continue;
                dropContainerSlots(entity, world, container, GET_ACCESSORIES);
                dropContainerSlots(entity, world, container, GET_COSMETIC_ACCESSORIES);
                try { MARK_CHANGED.invoke(container, true); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            CrafticsMod.LOGGER.warn("[Craftics × Accessories] dropAllAccessories failed", t);
        }
    }

    private static void dropContainerSlots(LivingEntity entity, World world, Object container, Method getter) {
        try {
            Object invObj = getter.invoke(container);
            if (!(invObj instanceof Inventory inv)) return;
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (stack == null || stack.isEmpty()) continue;
                ItemStack copy = stack.copy();
                inv.setStack(i, ItemStack.EMPTY);
                ItemEntity itemEntity = new ItemEntity(world,
                    entity.getX(), entity.getY() + 0.5, entity.getZ(), copy);
                itemEntity.setToDefaultPickupDelay();
                world.spawnEntity(itemEntity);
            }
        } catch (Throwable t) {
            CrafticsMod.LOGGER.debug("[Craftics × Accessories] container drop failed", t);
        }
    }

    /**
     * Probabilistically CLEARS (destroys, does not drop) each accessory + cosmetic slot,
     * rolling {@code lossChance} per non-empty slot. Returns how many slots were cleared.
     * Used by the Craftics defeat penalty, which deletes lost gear rather than scattering it
     * across the temporary arena. No-op when Accessories isn't installed.
     */
    public static int clearAccessoriesChance(LivingEntity entity, double lossChance) {
        if (!AVAILABLE || entity == null) return 0;
        World world = entity.getWorld();
        if (world == null || world.isClient) return 0;
        int cleared = 0;
        try {
            Object capability = CAPABILITY_GET.invoke(null, entity);
            if (capability == null) return 0;
            Object containersObj = GET_CONTAINERS.invoke(capability);
            if (!(containersObj instanceof Map<?, ?> containers)) return 0;
            for (Object container : containers.values()) {
                if (container == null) continue;
                cleared += clearContainerSlotsChance(container, GET_ACCESSORIES, lossChance);
                cleared += clearContainerSlotsChance(container, GET_COSMETIC_ACCESSORIES, lossChance);
                try { MARK_CHANGED.invoke(container, true); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            CrafticsMod.LOGGER.warn("[Craftics × Accessories] clearAccessoriesChance failed", t);
        }
        return cleared;
    }

    private static int clearContainerSlotsChance(Object container, Method getter, double lossChance) {
        int cleared = 0;
        try {
            Object invObj = getter.invoke(container);
            if (!(invObj instanceof Inventory inv)) return 0;
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (stack == null || stack.isEmpty()) continue;
                if (Math.random() < lossChance) {
                    inv.setStack(i, ItemStack.EMPTY);
                    cleared++;
                }
            }
        } catch (Throwable t) {
            CrafticsMod.LOGGER.debug("[Craftics × Accessories] clearContainerSlotsChance failed", t);
        }
        return cleared;
    }

    /** A captured accessory stack together with the slot it came from. */
    public record AccessorySnapshot(String container, int kind, int slot, ItemStack stack) {}

    /**
     * Captures every non-empty accessory + cosmetic accessory stack on the entity.
     * Pure read of the live containers - needs no registry lookup. Returns an empty
     * list when Accessories isn't installed. Recovery-compass death protection uses
     * this so trinkets survive death exactly like the main inventory does.
     */
    public static List<AccessorySnapshot> saveAccessories(LivingEntity entity) {
        List<AccessorySnapshot> out = new ArrayList<>();
        if (!AVAILABLE || entity == null) return out;
        try {
            Object capability = CAPABILITY_GET.invoke(null, entity);
            if (capability == null) return out;
            Object containersObj = GET_CONTAINERS.invoke(capability);
            if (!(containersObj instanceof Map<?, ?> containers)) return out;

            for (Object keyObj : containers.keySet()) {
                if (keyObj == null) continue;
                Object container = containers.get(keyObj);
                if (container == null) continue;
                String key = keyObj.toString();
                collectSlots(out, key, 0, container, GET_ACCESSORIES);
                collectSlots(out, key, 1, container, GET_COSMETIC_ACCESSORIES);
            }
        } catch (Throwable t) {
            CrafticsMod.LOGGER.warn("[Craftics × Accessories] saveAccessories failed", t);
        }
        return out;
    }

    private static void collectSlots(List<AccessorySnapshot> out, String containerKey, int kind,
                                     Object container, Method getter) {
        try {
            Object invObj = getter.invoke(container);
            if (!(invObj instanceof Inventory inv)) return;
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (stack == null || stack.isEmpty()) continue;
                out.add(new AccessorySnapshot(containerKey, kind, i, stack.copy()));
            }
        } catch (Throwable t) {
            CrafticsMod.LOGGER.debug("[Craftics × Accessories] collectSlots failed", t);
        }
    }

    /**
     * Clears exactly one accessory (or cosmetic accessory) slot, identified by the
     * container key, kind (0 = accessories, 1 = cosmetic), and slot index captured
     * by {@link #saveAccessories}. No-op when Accessories isn't installed or the
     * slot is out of range. Used to apply a pre-decided per-item death outcome.
     */
    public static void clearAccessoryAt(LivingEntity entity, String container, int kind, int slot) {
        if (!AVAILABLE || entity == null || container == null) return;
        try {
            Object capability = CAPABILITY_GET.invoke(null, entity);
            if (capability == null) return;
            Object containersObj = GET_CONTAINERS.invoke(capability);
            if (!(containersObj instanceof java.util.Map<?, ?> containers)) return;
            Object c = containers.get(container);
            if (c == null) return;
            Object invObj = (kind == 1 ? GET_COSMETIC_ACCESSORIES : GET_ACCESSORIES).invoke(c);
            if (!(invObj instanceof net.minecraft.inventory.Inventory inv)) return;
            if (slot < 0 || slot >= inv.size()) return;
            inv.setStack(slot, net.minecraft.item.ItemStack.EMPTY);
        } catch (Throwable t) {
            CrafticsMod.LOGGER.warn("[Craftics × Accessories] clearAccessoryAt failed", t);
        }
    }

    /**
     * Re-applies accessory + cosmetic stacks previously captured by
     * {@link #saveAccessories}. No-op when Accessories isn't installed or the
     * snapshot is empty.
     */
    public static void restoreAccessories(LivingEntity entity, List<AccessorySnapshot> saved) {
        if (!AVAILABLE || entity == null || saved == null || saved.isEmpty()) return;
        try {
            Object capability = CAPABILITY_GET.invoke(null, entity);
            if (capability == null) return;
            Object containersObj = GET_CONTAINERS.invoke(capability);
            if (!(containersObj instanceof Map<?, ?> containers)) return;

            for (AccessorySnapshot snap : saved) {
                Object container = containers.get(snap.container());
                if (container == null) continue;
                Object invObj = (snap.kind() == 1 ? GET_COSMETIC_ACCESSORIES : GET_ACCESSORIES).invoke(container);
                if (!(invObj instanceof Inventory inv)) continue;
                if (snap.slot() < 0 || snap.slot() >= inv.size()) continue;
                inv.setStack(snap.slot(), snap.stack().copy());
            }

            for (Object container : containers.values()) {
                if (container == null) continue;
                try { MARK_CHANGED.invoke(container, true); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            CrafticsMod.LOGGER.warn("[Craftics × Accessories] restoreAccessories failed", t);
        }
    }
}
