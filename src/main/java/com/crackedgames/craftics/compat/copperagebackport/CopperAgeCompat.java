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
    private static boolean registered = false;

    private CopperAgeCompat() {}

    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * Early-phase init. Flags {@link #loaded} based on the mod's presence so
     * other systems (e.g. armor-set detection) know to look for copper items,
     * but does NOT touch {@link WeaponRegistry} — item registration order
     * between Fabric mods isn't guaranteed, and copper items may not be in
     * {@link Registries#ITEM} yet. {@link #registerDeferred()} finishes the job
     * later, after all mod main entrypoints have completed.
     */
    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            CrafticsMod.LOGGER.debug("[Craftics × Copper Age] mod not loaded — skipping registration");
            return;
        }
        loaded = true;
    }

    /**
     * Late-phase registration. Safe to call from any lifecycle event that fires
     * after all {@code main} entrypoints — e.g. {@code ServerLifecycleEvents.SERVER_STARTING}
     * on the server, {@code ClientLifecycleEvents.CLIENT_STARTED} on the client, or
     * lazily from the tooltip render path. Idempotent: the first successful run
     * flips the guard and subsequent calls no-op.
     */
    public static void registerDeferred() {
        // Hot path — called from every tooltip render as a belt-and-suspenders
        // fallback. Both early-exits must stay silent or the log floods.
        if (registered || !loaded) return;
        boolean anyRegistered = registerWeapons() | registerArmorSet();
        if (anyRegistered) {
            registered = true;
            CrafticsMod.LOGGER.info("[Craftics × Copper Age] enabled — copper tier registered (tools in native lanes, armor = Marksman)");
        }
        // No retry log: if items aren't in the registry yet, the next tooltip
        // render will simply try again until they are.
    }

    /** True iff the deferred registration has completed at least once. */
    public static boolean isRegistered() {
        return registered;
    }

    /**
     * Register copper tools in their natural affinity lanes, with damage slotting
     * between stone and iron per the live config. Abilities mirror the vanilla
     * analogue so copper feels consistent with its sibling tools.
     * Pickaxes are not combat weapons (matches vanilla pickaxes which are unregistered).
     */
    private static boolean registerWeapons() {
        boolean any = false;
        // Shared handlers matching VanillaWeapons.registerSwords / registerAxes defaults.
        WeaponAbilityHandler swordHandler = Abilities.sweepAdjacent(0.10, 0.05);
        WeaponAbilityHandler axeHandler   = Abilities.armorIgnore(0.05, 0.03);

        // Damage values slot between stone and iron. Read through the live config
        // each attack so users who retune stone/iron via the config get a copper
        // tier that still sits halfway between them.
        java.util.function.IntSupplier copperSwordDmg  = () -> midpoint(CrafticsMod.CONFIG.dmgStoneSword(), CrafticsMod.CONFIG.dmgIronSword());
        java.util.function.IntSupplier copperAxeDmg    = () -> midpoint(CrafticsMod.CONFIG.dmgStoneAxe(),   CrafticsMod.CONFIG.dmgIronAxe());

        any |= registerWeapon("copper_sword",  DamageType.SLASHING, copperSwordDmg, 1, 1, false, 0.0, swordHandler);
        any |= registerWeapon("copper_axe",    DamageType.CLEAVING, copperAxeDmg,   2, 1, false, 0.0, axeHandler);
        // Shovels + hoes use fixed low damage in VanillaWeapons (no config), so pick a
        // copper value that sits between stone and iron: shovel stone=3/iron=4 → 3,
        // hoe stone=1/iron=2 → 2.
        any |= registerWeapon("copper_shovel", DamageType.PET,      () -> 3, 1, 1, false, 0.0, null);
        any |= registerWeapon("copper_hoe",    DamageType.SPECIAL,  () -> 2, 1, 1, false, 0.0, null);
        // copper_pickaxe intentionally skipped — vanilla pickaxes aren't combat weapons.
        return any;
    }

    /** Midpoint of two ints, rounded up so copper leans slightly closer to iron than stone. */
    private static int midpoint(int stone, int iron) {
        return (stone + iron + 1) / 2;
    }

    private static boolean registerWeapon(String path, DamageType type,
                                           java.util.function.IntSupplier power,
                                           int apCost, int range, boolean ranged, double breakChance,
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
    public static final double RICOCHET_CHANCE = 0.65;
    /** Fraction of the base damage dealt to the ricochet target. */
    public static final double RICOCHET_DAMAGE_MULT = 0.75;

    /** Type-affinity bonus from the copper set, matching the vanilla +2 Power baseline. */
    public static final int RANGED_POWER_BONUS = 2;

    /**
     * Register the copper armor set bonus. Identity is "Marksman":
     * <ul>
     *   <li>{@value #RANGED_POWER_BONUS} Ranged Power type affinity — matches the
     *       vanilla pattern (chainmail = +2 Slashing, iron = +2 Cleaving, etc.) so
     *       copper slots cleanly alongside the existing armor tiers as the Ranged set.</li>
     *   <li>A {@value #RICOCHET_CHANCE} chance on every ranged hit to ricochet
     *       into a nearby enemy for {@value #RICOCHET_DAMAGE_MULT} of base damage,
     *       handled in {@link com.crackedgames.craftics.combat.CombatManager}.</li>
     * </ul>
     * Set detection is wired up in {@link com.crackedgames.craftics.combat.PlayerCombatStats#getArmorSet}.
     */
    private static boolean registerArmorSet() {
        // Only register the set if at least one piece is actually present, otherwise
        // the bonus would never trigger anyway.
        if (lookupItem("copper_helmet") == null) return false;

        int chancePct = (int) Math.round(RICOCHET_CHANCE * 100);
        int dmgPct = (int) Math.round(RICOCHET_DAMAGE_MULT * 100);
        ArmorSetRegistry.register(ArmorSetEntry.builder("copper")
            .damageBonus(DamageType.RANGED, RANGED_POWER_BONUS)
            .description("\u00a76Marksman: " + chancePct + "% ricochet for " + dmgPct + "%, +"
                + RANGED_POWER_BONUS + " Ranged Power")
            .build());
        return true;
    }

    /**
     * Public getters for copper items — used by client tooltips and armor-set
     * detection. Each getter does a live registry lookup: this sidesteps the
     * chicken-and-egg between our init and the backport mod's item registration,
     * and costs one hashmap probe per tooltip render (negligible).
     */
    public static Item copperSword()     { return lookupItem("copper_sword"); }
    public static Item copperAxe()       { return lookupItem("copper_axe"); }
    public static Item copperPickaxe()   { return lookupItem("copper_pickaxe"); }
    public static Item copperShovel()    { return lookupItem("copper_shovel"); }
    public static Item copperHoe()       { return lookupItem("copper_hoe"); }
    public static Item copperHelmet()    { return lookupItem("copper_helmet"); }
    public static Item copperChestplate(){ return lookupItem("copper_chestplate"); }
    public static Item copperLeggings()  { return lookupItem("copper_leggings"); }
    public static Item copperBoots()     { return lookupItem("copper_boots"); }

    private static Item lookupItem(String path) {
        // Copper Age Backport registers its tools/armor under the minecraft: namespace
        // (verified from the JAR's `assets/minecraft/models/item/copper_*.json` paths
        // and its RegistryHelper.registerAuto, which hardcodes "minecraft").
        Identifier id = Identifier.of("minecraft", path);
        if (!Registries.ITEM.containsId(id)) return null;
        return Registries.ITEM.get(id);
    }
}
