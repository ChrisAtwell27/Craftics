package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.combat.DamageType;
import net.minecraft.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class WeaponRegistry {
    private static final Map<Item, WeaponEntry> REGISTRY = new ConcurrentHashMap<>();
    /** Items whose current entry came from a JSON datapack — dropped on /reload. */
    private static final Set<Item> DATAPACK_KEYS = ConcurrentHashMap.newKeySet();
    private static final WeaponEntry DEFAULT = new WeaponEntry(
        null, DamageType.PHYSICAL, () -> com.crackedgames.craftics.CrafticsMod.CONFIG.dmgFist(),
        1, 1, false, 0.0, null
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

    public static WeaponEntry get(Item item) {
        return REGISTRY.getOrDefault(item, DEFAULT);
    }

    @Nullable
    public static WeaponEntry getOrNull(Item item) {
        return REGISTRY.get(item);
    }

    public static boolean isRegistered(Item item) {
        return REGISTRY.containsKey(item);
    }

    public static DamageType getDamageType(Item item) {
        return get(item).damageType();
    }

    public static int getAttackPower(Item item) {
        return get(item).attackPower().getAsInt();
    }

    public static int getApCost(Item item) {
        return get(item).apCost();
    }

    public static int getRange(Item item) {
        return get(item).range();
    }

    public static double getBreakChance(Item item) {
        return get(item).breakChance();
    }

    public static boolean hasAbility(Item item) {
        return get(item).ability() != null;
    }
}
