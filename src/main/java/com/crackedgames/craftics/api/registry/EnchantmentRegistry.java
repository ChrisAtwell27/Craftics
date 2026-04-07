package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.EnchantmentContext;
import com.crackedgames.craftics.api.EnchantmentEffectHandler;
import com.crackedgames.craftics.api.StatModifiers;
import com.crackedgames.craftics.combat.PlayerCombatStats;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EnchantmentRegistry {
    private static final Map<String, EnchantmentEffectHandler> REGISTRY = new ConcurrentHashMap<>();

    private EnchantmentRegistry() {}

    public static void register(String enchantmentId, EnchantmentEffectHandler handler) {
        REGISTRY.put(enchantmentId, handler);
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
