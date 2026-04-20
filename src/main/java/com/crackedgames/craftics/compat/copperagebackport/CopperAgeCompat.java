package com.crackedgames.craftics.compat.copperagebackport;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.Abilities;
import com.crackedgames.craftics.api.WeaponAbilityHandler;
import com.crackedgames.craftics.api.registry.ArmorSetEntry;
import com.crackedgames.craftics.api.registry.ArmorSetRegistry;
import com.crackedgames.craftics.api.registry.WeaponEntry;
import com.crackedgames.craftics.api.registry.WeaponRegistry;
import com.crackedgames.craftics.combat.DamageType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Compatibility module for the Copper Age Backport mod (Smallinger).
 * <p>
 * Slots the mod's copper tier into Craftics combat:
 * <ul>
 *   <li>Copper sword/axe/shovel/hoe keep their natural affinity lanes
 *       (Slashing/Cleaving/Pet/Special) — copper is just a new metal tier,
 *       not a weapon-type rework. Stats slot between stone and iron.</li>
 *   <li>Copper armor is the Ranged-focused piece: wearing the full set
 *       grants <b>Marksman</b> — +2 Ranged damage and +1 Attack, making
 *       copper the canonical set for bow/crossbow builds.</li>
 * </ul>
 * <p>
 * The mod registers its items under the {@code minecraft:} namespace (verified
 * from the JAR's asset paths), so we resolve them from the live registry after
 * the mod has loaded. Missing items (mod absent) cause every call to no-op.
 */
public final class CopperAgeCompat {

    public static final String MOD_ID = "copperagebackport";

    private static boolean loaded = false;

    private CopperAgeCompat() {}

    public static boolean isLoaded() {
        return loaded;
    }

    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            CrafticsMod.LOGGER.debug("[Craftics × Copper Age] mod not loaded — skipping registration");
            return;
        }

        boolean anyRegistered = registerWeapons() | registerArmorSet();
        if (anyRegistered) {
            loaded = true;
            CrafticsMod.LOGGER.info("[Craftics × Copper Age] enabled — copper tier registered as Ranged affinity");
        } else {
            CrafticsMod.LOGGER.warn(
                "[Craftics × Copper Age] mod present but no copper items found in registry — skipping");
        }
    }

    /**
     * Register copper tools in their natural affinity lanes. Stats slot between
     * stone and iron — copper is canonically a softer tier than iron. Abilities
     * match the vanilla analogue so copper feels consistent with its sibling tools.
     * Pickaxes are not combat weapons (matches vanilla pickaxes which are unregistered).
     */
    private static boolean registerWeapons() {
        boolean any = false;
        // Shared handlers matching VanillaWeapons.registerSwords / registerAxes defaults.
        WeaponAbilityHandler swordHandler = Abilities.sweepAdjacent(0.10, 0.05);
        WeaponAbilityHandler axeHandler   = Abilities.armorIgnore(0.05, 0.03);

        // copper_sword — SLASHING, between stone (3) and iron (5)
        any |= registerWeapon("copper_sword",  DamageType.SLASHING, 4, 1, 1, false, 0.0, swordHandler);
        // copper_axe — CLEAVING, between stone (4) and iron (6)
        any |= registerWeapon("copper_axe",    DamageType.CLEAVING, 5, 2, 1, false, 0.0, axeHandler);
        // copper_shovel — PET, between stone (3) and iron (4)
        any |= registerWeapon("copper_shovel", DamageType.PET,       3, 1, 1, false, 0.0, null);
        // copper_hoe — SPECIAL, between stone (1) and iron (2)
        any |= registerWeapon("copper_hoe",    DamageType.SPECIAL,   2, 1, 1, false, 0.0, null);
        // copper_pickaxe intentionally skipped — vanilla pickaxes aren't combat weapons.
        return any;
    }

    private static boolean registerWeapon(String path, DamageType type, int power, int apCost,
                                           int range, boolean ranged, double breakChance,
                                           WeaponAbilityHandler ability) {
        Item item = lookupItem(path);
        if (item == null) return false;
        WeaponEntry.Builder b = WeaponEntry.builder(item)
            .damageType(type).attackPower(power)
            .apCost(apCost).range(range).ranged(ranged).breakChance(breakChance);
        if (ability != null) b.ability(ability);
        WeaponRegistry.register(item, b.build());
        return true;
    }

    /** Chance for a ranged hit to ricochet when the full copper set is worn. */
    public static final double RICOCHET_CHANCE = 0.40;
    /** Fraction of the base damage dealt to the ricochet target. */
    public static final double RICOCHET_DAMAGE_MULT = 0.50;

    /**
     * Register the copper armor set bonus. The set has no flat damage or attack
     * bonuses — its identity is the Marksman ricochet chance on ranged hits,
     * handled in {@link com.crackedgames.craftics.combat.CombatManager}. Detection
     * is wired up in {@link com.crackedgames.craftics.combat.PlayerCombatStats#getArmorSet}.
     */
    private static boolean registerArmorSet() {
        // Only register the set if at least one piece is actually present, otherwise
        // the bonus would never trigger anyway.
        if (lookupItem("copper_helmet") == null) return false;

        int chancePct = (int) Math.round(RICOCHET_CHANCE * 100);
        int dmgPct = (int) Math.round(RICOCHET_DAMAGE_MULT * 100);
        ArmorSetRegistry.register(ArmorSetEntry.builder("copper")
            .description("\u00a76Marksman: " + chancePct + "% ranged hits ricochet to a nearby enemy for " + dmgPct + "%")
            .build());
        return true;
    }

    /** Public getters for copper items — used by client tooltips and set detection. */
    public static Item copperSword()     { return loaded ? lookupItem("copper_sword")     : null; }
    public static Item copperAxe()       { return loaded ? lookupItem("copper_axe")       : null; }
    public static Item copperPickaxe()   { return loaded ? lookupItem("copper_pickaxe")   : null; }
    public static Item copperShovel()    { return loaded ? lookupItem("copper_shovel")    : null; }
    public static Item copperHoe()       { return loaded ? lookupItem("copper_hoe")       : null; }

    private static Item lookupItem(String path) {
        // Copper Age Backport registers its tools/armor under the minecraft: namespace
        // (verified from the JAR's `assets/minecraft/models/item/copper_*.json` paths).
        Identifier id = Identifier.of("minecraft", path);
        if (!Registries.ITEM.containsId(id)) return null;
        return Registries.ITEM.get(id);
    }

    /**
     * Returns the copper helmet item if the mod is loaded and the item is registered,
     * otherwise null. Used by {@link com.crackedgames.craftics.combat.PlayerCombatStats}
     * to detect the full set without holding a hard reference to the modded item.
     */
    public static Item copperHelmet()    { return loaded ? lookupItem("copper_helmet")    : null; }
    public static Item copperChestplate(){ return loaded ? lookupItem("copper_chestplate"): null; }
    public static Item copperLeggings()  { return loaded ? lookupItem("copper_leggings")  : null; }
    public static Item copperBoots()     { return loaded ? lookupItem("copper_boots")     : null; }
}
