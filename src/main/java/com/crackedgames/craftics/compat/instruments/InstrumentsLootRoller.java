package com.crackedgames.craftics.compat.instruments;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Supplies obtainable instrument items for the Curiosity Dealer trader. The instruments
 * are registered as combat (Special-affinity) weapons but their source-mod craft recipes
 * need varied materials the void hub can't supply, so the trader is their reliable
 * acquisition channel. No-ops when neither instrument mod is installed.
 *
 * @since 0.2.3
 */
public final class InstrumentsLootRoller {

    private InstrumentsLootRoller() {}

    /** Every instrument item whose source mod is installed and whose item id resolves. */
    public static List<Item> available() {
        List<Item> out = new ArrayList<>();
        for (InstrumentDef def : InstrumentRoster.ALL) {
            if (!FabricLoader.getInstance().isModLoaded(def.modId())) continue;
            Item item = Registries.ITEM.get(Identifier.of(def.modId(), def.itemPath()));
            if (item != null && item != Items.AIR) out.add(item);
        }
        return out;
    }

    /** Up to {@code max} random distinct available instruments, or empty when none exist. */
    public static List<Item> rollSome(int max) {
        List<Item> all = available();
        if (all.isEmpty() || max <= 0) return List.of();
        Collections.shuffle(all, new java.util.Random());
        return new ArrayList<>(all.subList(0, Math.min(max, all.size())));
    }
}
