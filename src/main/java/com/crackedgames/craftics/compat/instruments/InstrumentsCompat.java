package com.crackedgames.craftics.compat.instruments;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.registry.WeaponEntry;
import com.crackedgames.craftics.api.registry.WeaponRegistry;
import com.crackedgames.craftics.combat.DamageType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compat for Genshin Instruments + Even More Instruments. Registers each present
 * instrument as a Special-affinity WeaponEntry (stats/AP/tooltip); resolution runs
 * through CombatManager.handleInstrument (Task 7), not the weapon ability handler.
 * No compile-time dependency: resolves by registry id, no-ops when mods are absent.
 */
public final class InstrumentsCompat {

    public static final String GENSHIN = "genshinstrument";
    public static final String EMI = "evenmoreinstruments";

    private static boolean genshinLoaded = false;
    private static boolean emiLoaded = false;
    private static boolean registered = false;

    /** Item -> def, only for instruments whose item resolved at registration. */
    private static final Map<Item, InstrumentDef> BY_ITEM = new ConcurrentHashMap<>();

    private InstrumentsCompat() {}

    public static void init() {
        genshinLoaded = FabricLoader.getInstance().isModLoaded(GENSHIN);
        emiLoaded = FabricLoader.getInstance().isModLoaded(EMI);
        if (!genshinLoaded && !emiLoaded) {
            CrafticsMod.LOGGER.debug("[Craftics x Instruments] neither mod loaded - skipping");
        }
    }

    private static boolean modPresent(String modId) {
        return (GENSHIN.equals(modId) && genshinLoaded) || (EMI.equals(modId) && emiLoaded);
    }

    public static void registerDeferred() {
        if (registered || (!genshinLoaded && !emiLoaded)) return;
        for (InstrumentDef def : InstrumentRoster.ALL) {
            if (!modPresent(def.modId())) continue;
            Identifier id = Identifier.of(def.modId(), def.itemPath());
            if (!Registries.ITEM.containsId(id)) continue;
            Item item = Registries.ITEM.get(id);
            WeaponRegistry.register(item, WeaponEntry.builder(item)
                .damageType(DamageType.SPECIAL)
                .attackPower(def.baseDamage())   // 0 for support; tooltip shows effect instead
                .apCost(def.apCost())
                .range(1)
                .build());
            BY_ITEM.put(item, def);
        }
        registered = true;
        CrafticsMod.LOGGER.info("[Craftics x Instruments] registered {} instruments", BY_ITEM.size());
    }

    /** The def for a held item, or null if it is not a registered instrument. */
    public static InstrumentDef defFor(Item item) {
        return item == null ? null : BY_ITEM.get(item);
    }

    /** Every instrument Item that resolved at registration (drives the mob pool). */
    public static java.util.List<Item> registeredInstrumentItems() {
        return new java.util.ArrayList<>(BY_ITEM.keySet());
    }

    public static boolean isInstrument(Item item) {
        return item != null && BY_ITEM.containsKey(item);
    }

    public static boolean isInstrument(Identifier id) {
        return id != null && (GENSHIN.equals(id.getNamespace()) || EMI.equals(id.getNamespace()));
    }
}
