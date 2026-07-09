package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.registry.WeaponRegistry;
import com.crackedgames.craftics.compat.basicweapons.BasicWeaponsCompat;
import com.crackedgames.craftics.compat.instruments.InstrumentsCompat;
import com.crackedgames.craftics.compat.simplyswords.SimplySwordsCompat;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Widens the mob weapon pool with modded items and derives their combat behaviour.
 * Pools are resolved lazily from each compat module's public tier accessors and cached.
 * The on-hit debuff table maps a weapon's id family to a short player-facing debuff (a
 * mob katana bleeds you, a poison sword poisons you); player-only procs are not mapped.
 * See docs/superpowers/specs/2026-07-09-modded-mob-weapons-design.md.
 */
public final class ModdedMobWeapons {

    private ModdedMobWeapons() {}

    /** Cap on the attack bonus a modded weapon grants a mob (netherite-ish, generous). */
    public static final int MOB_WEAPON_ATK_CAP = 6;

    /** A player-facing debuff a mob's weapon applies on hit. */
    public record Debuff(CombatEffects.EffectType type, int turns, int amplifier) {}

    // Lazy caches (built on first combat use; item registries are populated by then).
    private static List<Item>[] tierCache = null;
    private static List<Item> instrumentCache = null;

    /**
     * Registered, resolved SS-standard + Basic-Weapons items for a Craftics gear tier
     * (0-4). Empty when both mods absent. Only items present in WeaponRegistry are kept
     * so a mob never holds an id Craftics doesn't know how to fight with.
     */
    @SuppressWarnings("unchecked")
    public static List<Item> moddedWeaponsForTier(int tier) {
        if (tierCache == null) {
            tierCache = new List[5];
            for (int t = 0; t < 5; t++) {
                List<Item> bucket = new ArrayList<>();
                if (SimplySwordsCompat.isLoaded()) {
                    addResolved(bucket, SimplySwordsCompat.NAMESPACE,
                        SimplySwordsCompat.standardWeaponTierIds(t));
                }
                if (BasicWeaponsCompat.isLoaded()) {
                    addResolved(bucket, BasicWeaponsCompat.NAMESPACE,
                        BasicWeaponsCompat.weaponTierIds(t));
                }
                tierCache[t] = bucket;
            }
        }
        return (tier >= 0 && tier < 5) ? tierCache[tier] : List.of();
    }

    /** Every registered instrument item (cached). Empty when neither mod is present. */
    public static List<Item> instruments() {
        if (instrumentCache == null) {
            instrumentCache = new ArrayList<>(InstrumentsCompat.registeredInstrumentItems());
        }
        return instrumentCache;
    }

    private static void addResolved(List<Item> out, String namespace, List<String> paths) {
        for (String path : paths) {
            Identifier id = Identifier.of(namespace, path);
            if (!Registries.ITEM.containsId(id)) continue;
            Item item = Registries.ITEM.get(id);
            if (item != null && WeaponRegistry.isRegistered(item)) out.add(item);
        }
    }

    /**
     * Attack bonus a mob gets for holding {@code weapon}: registered power minus fist
     * damage, clamped to [0, MOB_WEAPON_ATK_CAP]. 0 for unregistered items.
     */
    public static int cappedAttackBonus(Item weapon) {
        if (weapon == null || !WeaponRegistry.isRegistered(weapon)) return 0;
        int bonus = WeaponRegistry.getAttackPower(weapon) - CrafticsMod.CONFIG.dmgFist();
        return Math.max(0, Math.min(bonus, MOB_WEAPON_ATK_CAP));
    }

    /**
     * The player-facing debuff a mob's weapon applies on hit, keyed by id-path family,
     * or null for weapons with no mapped on-you effect. Pure: string -> record.
     */
    public static Debuff onHitDebuff(String itemPath) {
        if (itemPath == null) return null;
        String p = itemPath.toLowerCase(java.util.Locale.ROOT);
        if (containsAny(p, "katana", "shadowsting", "ribboncleaver", "scythe"))
            return new Debuff(CombatEffects.EffectType.BLEEDING, 2, 0);
        if (containsAny(p, "toxic", "plague", "poison", "venom", "bramble"))
            return new Debuff(CombatEffects.EffectType.POISON, 2, 0);
        if (containsAny(p, "frost", "ice", "dreadtide", "livyatan", "chompolotl"))
            return new Debuff(CombatEffects.EffectType.SLOWNESS, 2, 0);
        if (containsAny(p, "ember", "molten", "brimstone", "flame", "hearthflame", "sunfire", "wick"))
            return new Debuff(CombatEffects.EffectType.BURNING, 2, 0);
        if (containsAny(p, "wither", "lich", "soul", "wraith", "grave"))
            return new Debuff(CombatEffects.EffectType.WITHER, 2, 0);
        // "stun" families: player has no stun -> WEAKNESS instead.
        if (containsAny(p, "greathammer", "hammer", "mace", "bonk", "club", "greataxe"))
            return new Debuff(CombatEffects.EffectType.WEAKNESS, 2, 0);
        return null;
    }

    private static boolean containsAny(String s, String... needles) {
        for (String n : needles) if (s.contains(n)) return true;
        return false;
    }
}
