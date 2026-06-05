package com.crackedgames.craftics.compat.moretotems;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Picks a random MoreTotems totem for rare-drop loot. The pool is the seven totem ids,
 * resolved from the live registry once on first use and cached (the registry doesn't change
 * after mod load). Returns {@link ItemStack#EMPTY} if MoreTotems isn't loaded or none of the
 * totems are present, so every caller degrades gracefully to its original loot.
 */
public final class MoreTotemsLootRoller {

    private static final String[] TOTEM_PATHS = {
        MoreTotemsCompat.EXPLOSIVE, MoreTotemsCompat.SKELETAL, MoreTotemsCompat.TELEPORTING,
        MoreTotemsCompat.GHASTLY, MoreTotemsCompat.STINGING, MoreTotemsCompat.TENTACLED,
        MoreTotemsCompat.ROTTING,
    };

    private static List<Item> cachedPool = null;
    private static final Random RNG = new Random();

    private MoreTotemsLootRoller() {}

    private static List<Item> getPool() {
        if (cachedPool != null) return cachedPool;
        List<Item> pool = new ArrayList<>();
        if (MoreTotemsCompat.isLoaded()) {
            for (String path : TOTEM_PATHS) {
                Identifier id = Identifier.of(MoreTotemsCompat.NAMESPACE, path);
                if (Registries.ITEM.containsId(id)) {
                    Item item = Registries.ITEM.get(id);
                    if (item != null) pool.add(item);
                }
            }
        }
        cachedPool = pool;
        return pool;
    }

    /** A size-1 stack of a random totem, or {@link ItemStack#EMPTY} if none are available. */
    public static ItemStack rollOne() {
        List<Item> pool = getPool();
        if (pool.isEmpty()) return ItemStack.EMPTY;
        return new ItemStack(pool.get(RNG.nextInt(pool.size())));
    }
}
