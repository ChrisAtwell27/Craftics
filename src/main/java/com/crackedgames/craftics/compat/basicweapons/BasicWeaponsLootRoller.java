package com.crackedgames.craftics.compat.basicweapons;

import net.minecraft.item.Item;
import java.util.ArrayList;
import java.util.List;

/**
 * Supplies a small tier-appropriate subset of basicweapons items for the weaponsmith.
 * Mirrors the lazy-cached skeleton of MoreTotemsLootRoller; the forTier(int) API is new.
 */
public final class BasicWeaponsLootRoller {

    private BasicWeaponsLootRoller() {}

    /** Pure: which material tiers are appropriate at this trader tier. */
    public static List<String> tiersForTier(int tier) {
        List<String> out = new ArrayList<>();
        if (tier <= 2) { out.add("wooden"); out.add("stone"); }
        else if (tier <= 6) { out.add("iron"); }
        else if (tier <= 8) { out.add("diamond"); }
        else { out.add("netherite"); }
        return out;
    }

    /**
     * Resolve a small set of tier-appropriate basicweapons items. Returns empty when
     * the mod is absent or nothing resolves. Deterministic (no RNG): picks two distinct
     * types per eligible material tier (dagger + club) so the pool stays small (~2-4 offers).
     */
    public static List<Item> forTier(int tier) {
        if (!BasicWeaponsCompat.isLoaded()) return List.of();
        List<Item> out = new ArrayList<>();
        String[] types = BasicWeaponsCompat.TYPES; // {dagger, spear, quarterstaff, club, hammer, glaive}
        for (String mat : tiersForTier(tier)) {
            for (int i = 0; i < types.length && out.size() < 4; i += 3) { // i=0 (dagger), i=3 (club)
                Item item = BasicWeaponsCompat.lookupItem(mat + "_" + types[i]);
                if (item != null) out.add(item);
            }
        }
        return out;
    }
}
