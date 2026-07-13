package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.combat.DamageType;
import net.minecraft.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of weapon combat stats, keyed by {@link net.minecraft.item.Item}.
 *
 * <p>Every item a player can attack with must have a registered {@link WeaponEntry}.
 * If an item is not registered, {@link #get} returns a default bare-fist entry.
 * Craftics registers all vanilla weapons at startup via {@code VanillaWeapons}; addons
 * register their own through {@code CrafticsAPI.registerWeapon}.
 *
 * @since 0.2.0
 */
public final class WeaponRegistry {
    private static final Map<Item, WeaponEntry> REGISTRY = new ConcurrentHashMap<>();
    /** Items whose current entry came from a JSON datapack - dropped on /reload. */
    private static final Set<Item> DATAPACK_KEYS = ConcurrentHashMap.newKeySet();
    private static final WeaponEntry DEFAULT = new WeaponEntry(
        null, DamageType.PHYSICAL, null,
        () -> com.crackedgames.craftics.CrafticsMod.CONFIG.dmgFist(),
        1, 1, false, 0.0, null, null
    );

    private WeaponRegistry() {}

    /** Register a weapon from code (survives {@code /reload}). */
    public static void register(Item item, WeaponEntry entry) {
        register(item, entry, RegistrationSource.CODE);
    }

    /** Register a weapon, tagging whether it came from code or a datapack. */
    public static void register(Item item, WeaponEntry entry, RegistrationSource source) {
        REGISTRY.put(item, entry);
        if (source == RegistrationSource.DATAPACK) {
            DATAPACK_KEYS.add(item);
        } else {
            DATAPACK_KEYS.remove(item);
        }
    }

    /** Remove every weapon entry that was loaded from a JSON datapack. */
    public static void clearDatapackEntries() {
        for (Item item : DATAPACK_KEYS) {
            REGISTRY.remove(item);
        }
        DATAPACK_KEYS.clear();
    }

    /**
     * The entry for {@code item}, or the default bare-fist entry if {@code item} is not
     * registered. Never {@code null}.
     */
    public static WeaponEntry get(Item item) {
        return REGISTRY.getOrDefault(item, DEFAULT);
    }

    /** The entry for {@code item}, or {@code null} if it is not registered. */
    @Nullable
    public static WeaponEntry getOrNull(Item item) {
        return REGISTRY.get(item);
    }

    /** Whether {@code item} has a registered weapon entry. */
    public static boolean isRegistered(Item item) {
        return REGISTRY.containsKey(item);
    }

    /**
     * Every currently-registered weapon item - vanilla, compat, and datapack alike.
     * Snapshot: later registrations are not reflected. Useful for building "any weapon"
     * loot pools that should automatically pick up whatever mods are installed.
     *
     * @since 0.3.0
     */
    public static java.util.Set<Item> registeredItems() {
        return java.util.Set.copyOf(REGISTRY.keySet());
    }

    /** Damage type for {@code item}, or {@code PHYSICAL} if not registered. */
    public static DamageType getDamageType(Item item) {
        return get(item).damageType();
    }

    /**
     * The weapon's second affinity, or {@code null} when it has none. A hybrid weapon
     * also scales off this type at half weight; resistances still use the primary.
     *
     * @since 0.3.0
     */
    @Nullable
    public static DamageType getSecondaryDamageType(Item item) {
        return get(item).secondaryDamageType();
    }

    /**
     * The weapon's tile-aimed action, or {@code null} when it has none. Present only on
     * weapons that do something to the ground instead of to an enemy.
     *
     * @since 0.3.0
     */
    @Nullable
    public static com.crackedgames.craftics.api.TargetlessCastHandler getTargetlessCast(Item item) {
        return get(item).targetlessCast();
    }

    /** Base attack power for {@code item}, or the configured fist damage if not registered. */
    public static int getAttackPower(Item item) {
        return get(item).attackPower().getAsInt();
    }

    /** AP cost per attack for {@code item}, or {@code 1} if not registered. */
    public static int getApCost(Item item) {
        return get(item).apCost();
    }

    /** Attack range in tiles for {@code item}, or {@code 1} if not registered. */
    public static int getRange(Item item) {
        return get(item).range();
    }

    /** Break chance per attack for {@code item}, or {@code 0.0} if not registered. */
    public static double getBreakChance(Item item) {
        return get(item).breakChance();
    }

    /** Whether {@code item} has a non-null on-hit ability registered. */
    public static boolean hasAbility(Item item) {
        return get(item).ability() != null;
    }
}
