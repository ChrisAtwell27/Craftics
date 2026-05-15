package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.EnchantmentContext;
import com.crackedgames.craftics.api.EnchantmentEffectHandler;
import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.api.StatModifiers;
import com.crackedgames.craftics.combat.PlayerCombatStats;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class EnchantmentRegistry {
    private static final Map<String, EnchantmentEffectHandler> REGISTRY = new ConcurrentHashMap<>();
    /** Enchantment IDs whose current handler came from a JSON datapack — dropped on /reload. */
    private static final Set<String> DATAPACK_KEYS = ConcurrentHashMap.newKeySet();

    private EnchantmentRegistry() {}

    /** Register an enchantment effect from code (survives {@code /reload}). */
    public static void register(String enchantmentId, EnchantmentEffectHandler handler) {
        register(enchantmentId, handler, RegistrationSource.CODE);
    }

    /** Register an enchantment effect, tagging whether it came from code or a datapack. */
    public static void register(String enchantmentId, EnchantmentEffectHandler handler,
                                RegistrationSource source) {
        REGISTRY.put(enchantmentId, handler);
        if (source == RegistrationSource.DATAPACK) {
            DATAPACK_KEYS.add(enchantmentId);
        } else {
            DATAPACK_KEYS.remove(enchantmentId);
        }
    }

    /** Remove every enchantment effect that was loaded from a JSON datapack. */
    public static void clearDatapackEntries() {
        for (String id : DATAPACK_KEYS) {
            REGISTRY.remove(id);
        }
        DATAPACK_KEYS.clear();
    }

    public static StatModifiers applyAll(ServerPlayerEntity player) {
        StatModifiers mods = new StatModifiers();
        for (var entry : REGISTRY.entrySet()) {
            int level = findMaxEnchantLevel(player, entry.getKey());
            if (level > 0) {
                entry.getValue().apply(new EnchantmentContext(level, player, mods));
            }
        }
        return mods;
    }

    private static int findMaxEnchantLevel(ServerPlayerEntity player, String enchantId) {
        // Check weapon
        int level = PlayerCombatStats.getEnchantLevel(player.getMainHandStack(), enchantId);
        // Check all armor slots — use max level found
        for (EquipmentSlot slot : new EquipmentSlot[]{
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            level = Math.max(level, PlayerCombatStats.getEnchantLevel(player.getEquippedStack(slot), enchantId));
        }
        return level;
    }
}
