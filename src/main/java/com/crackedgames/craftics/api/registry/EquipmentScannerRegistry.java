package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.EquipmentScanner;
import com.crackedgames.craftics.api.StatModifiers;
import com.crackedgames.craftics.combat.TrimEffects;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EquipmentScannerRegistry {
    private static final Map<String, EquipmentScanner> SCANNERS = new LinkedHashMap<>();

    private EquipmentScannerRegistry() {}

    public static void register(String id, EquipmentScanner scanner) {
        SCANNERS.put(id, scanner);
    }

    public static StatModifiers scanAll(ServerPlayerEntity player) {
        StatModifiers combined = new StatModifiers();
        for (EquipmentScanner scanner : SCANNERS.values()) {
            StatModifiers result = scanner.scan(player);
            for (var entry : result.getAll().entrySet()) {
                combined.add(entry.getKey(), entry.getValue());
            }
            if (result.getSetBonus() != TrimEffects.SetBonus.NONE) {
                combined.addSetBonus(result.getSetBonus(), result.getSetBonusName());
            }
            // Propagate combat effect handlers contributed by scanners.
            for (var effect : result.getCombatEffects()) {
                combined.addCombatEffect(effect.name(), effect.handler());
            }
        }
        return combined;
    }
}
