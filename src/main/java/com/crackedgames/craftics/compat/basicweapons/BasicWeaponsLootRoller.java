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

    /** Pure: which material tiers are appropriate at this trader tier. Golden sits in the
     *  mid band so the golden material is reachable; without it the gold tier was never
     *  sold at all. */
    public static List<String> tiersForTier(int tier) {
        List<String> out = new ArrayList<>();
        if (tier <= 2) { out.add("wooden"); out.add("stone"); }
        else if (tier <= 6) { out.add("iron"); out.add("golden"); }
        else if (tier <= 8) { out.add("diamond"); }
        else { out.add("netherite"); }
        return out;
    }

    /**
     * Resolve the tier-appropriate basicweapons items. Returns empty when the mod is
     * absent or nothing resolves. Covers ALL six weapon types (dagger, spear, quarterstaff,
     * club, hammer, glaive) for each eligible material tier so every type/tier is reachable
     * - the weaponsmith later shuffles its full pool and shows 3-5, so a large pool here
     * just means more variety over visits, not a flooded screen. (The old loop stepped i+=3
     * and so only ever offered dagger + club, and never the golden tier.)
     */
    public static List<Item> forTier(int tier) {
        if (!BasicWeaponsCompat.isLoaded()) return List.of();
        List<Item> out = new ArrayList<>();
        String[] types = BasicWeaponsCompat.TYPES; // {dagger, spear, quarterstaff, club, hammer, glaive}
        for (String mat : tiersForTier(tier)) {
            for (String type : types) {
                Item item = BasicWeaponsCompat.lookupItem(mat + "_" + type);
                if (item != null) out.add(item);
            }
        }
        return out;
    }
}
