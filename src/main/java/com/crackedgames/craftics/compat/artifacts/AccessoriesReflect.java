package com.crackedgames.craftics.compat.artifacts;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Reflection shim over the Accessories mod API — the slot provider that the
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
    private static final boolean AVAILABLE;

    static {
        Method mGet = null, mGetC = null, mGetA = null, mGetCo = null, mMark = null;
        boolean ok = false;
        Throwable failure = null;
        try {
            Class<?> capCls = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
            mGet = capCls.getMethod("get", LivingEntity.class);
            mGetC = capCls.getMethod("getContainers");
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
}
