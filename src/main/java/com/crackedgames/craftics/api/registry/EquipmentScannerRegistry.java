package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.EquipmentScanner;
import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.api.StatModifiers;
import com.crackedgames.craftics.combat.TrimEffects;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class EquipmentScannerRegistry {
    private static final Map<String, EquipmentScanner> SCANNERS = new LinkedHashMap<>();
    /** Scanner IDs whose current entry came from a JSON datapack — dropped on /reload. */
    private static final Set<String> DATAPACK_KEYS = new HashSet<>();

    private EquipmentScannerRegistry() {}

    /** Register an equipment scanner from code (survives {@code /reload}). */
    public static void register(String id, EquipmentScanner scanner) {
        register(id, scanner, RegistrationSource.CODE);
    }

    /** Register an equipment scanner, tagging whether it came from code or a datapack. */
    public static void register(String id, EquipmentScanner scanner, RegistrationSource source) {
        SCANNERS.put(id, scanner);
        if (source == RegistrationSource.DATAPACK) {
            DATAPACK_KEYS.add(id);
        } else {
            DATAPACK_KEYS.remove(id);
        }
    }

    /** Remove every equipment scanner that was loaded from a JSON datapack. */
    public static void clearDatapackEntries() {
        for (String id : DATAPACK_KEYS) {
            SCANNERS.remove(id);
        }
        DATAPACK_KEYS.clear();
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
