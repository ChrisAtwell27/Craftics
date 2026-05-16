package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.RegistrationSource;
import net.minecraft.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of items the player can use during a Craftics turn.
 *
 * <p>Craftics checks this registry first when the player uses an item: a registered
 * item runs its {@link UsableItemEntry#handler() handler}; an unregistered item falls
 * through to Craftics' built-in item handling. This lets addons add custom consumables
 * and special-effect items without altering vanilla behavior.
 *
 * @since 0.2.0
 */
public final class UsableItemRegistry {

    private static final Map<Item, UsableItemEntry> REGISTRY = new ConcurrentHashMap<>();
    /** Items whose current entry came from a JSON datapack — dropped on /reload. */
    private static final Set<Item> DATAPACK_KEYS = ConcurrentHashMap.newKeySet();

    private UsableItemRegistry() {}

    /** Register a usable item from code (survives {@code /reload}). */
    public static void register(Item item, UsableItemEntry entry) {
        register(item, entry, RegistrationSource.CODE);
    }

    /** Register a usable item, tagging whether it came from code or a datapack. */
    public static void register(Item item, UsableItemEntry entry, RegistrationSource source) {
        REGISTRY.put(item, entry);
        if (source == RegistrationSource.DATAPACK) {
            DATAPACK_KEYS.add(item);
        } else {
            DATAPACK_KEYS.remove(item);
        }
    }

    /** The entry for {@code item}, or {@code null} if it is not a registered usable item. */
    @Nullable
    public static UsableItemEntry getOrNull(Item item) {
        return REGISTRY.get(item);
    }

    /** Whether {@code item} has a registered usable-item entry. */
    public static boolean isRegistered(Item item) {
        return REGISTRY.containsKey(item);
    }

    /** Remove every usable-item entry that was loaded from a JSON datapack. */
    public static void clearDatapackEntries() {
        for (Item item : DATAPACK_KEYS) {
            REGISTRY.remove(item);
        }
        DATAPACK_KEYS.clear();
    }
}
