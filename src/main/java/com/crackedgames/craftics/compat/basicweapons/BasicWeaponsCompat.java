package com.crackedgames.craftics.compat.basicweapons;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.Abilities;
import com.crackedgames.craftics.api.VanillaWeapons;
import com.crackedgames.craftics.api.WeaponAbilityHandler;
import com.crackedgames.craftics.api.registry.WeaponEntry;
import com.crackedgames.craftics.api.registry.WeaponRegistry;
import com.crackedgames.craftics.combat.AoeShapes;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.DamageType;
import com.crackedgames.craftics.combat.PlayerCombatStats;
import com.crackedgames.craftics.combat.PlayerProgression;
import com.crackedgames.craftics.combat.WeaponAbility;
import com.crackedgames.craftics.core.GridPos;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Compatibility module for the Basic Weapons mod (Khazoda).
 * Registers the mod's six weapon types into Craftics combat, gated on mod presence.
 * No compile-time dependency: everything resolves by registry id and no-ops when absent.
 */
public final class BasicWeaponsCompat {

    public static final String MOD_ID = "basicweapons";
    public static final String NAMESPACE = "basicweapons";

    /** Vanilla tiers we register. Bronze is deliberately omitted. */
    static final String[] TIERS = {"wooden", "stone", "iron", "golden", "diamond", "netherite"};

    /** The six weapon-type suffixes. */
    static final String[] TYPES = {"dagger", "spear", "quarterstaff", "club", "hammer", "glaive"};

    // === Craftics balance tuning (daggers and spears) ===
    /** Off-hand dagger second hit fraction of the main hit (1.0 main + 0.75 off = 1.75x total). */
    public static final double DAGGER_OFFHAND_MULT = 0.75;
    /** Spear bonus added per tile walked before attacking this turn. */
    public static final double SPEAR_MOVE_PER_TILE = 0.50;
    /** Hard cap on the spear movement multiplier. */
    public static final double SPEAR_MOVE_CAP = 10.0;

    private static boolean loaded = false;
    private static boolean registered = false;

    private BasicWeaponsCompat() {}

    public static boolean isLoaded() { return loaded; }
    public static boolean isRegistered() { return registered; }

    /**
     * Parse the weapon type from a path like "iron_dagger" -> "dagger".
     * Returns null if the path doesn't end with one of the six type suffixes.
     * Pure: no registry. (Bronze parses normally; bronze exclusion happens at
     * registration via the TIERS list, not here.)
     */
    public static String weaponType(String path) {
        if (path == null) return null;
        for (String type : TYPES) {
            if (path.endsWith("_" + type)) return type;
        }
        return null;
    }

    /** True for the three BLUNT types that Might affects. Pure. */
    public static boolean isBluntType(String type) {
        return "club".equals(type) || "hammer".equals(type) || "quarterstaff".equals(type);
    }

    /**
     * Base weaponsmith emerald cost by damage class (pre-tier-scaling).
     * Dagger/quarterstaff cheap (Low), spear (Medium), club/hammer/glaive dear (High). Pure.
     */
    public static int baseTraderCost(String type) {
        return switch (type) {
            case "dagger", "quarterstaff" -> 3;
            case "spear" -> 5;
            case "club", "hammer", "glaive" -> 8;
            default -> 4;
        };
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /** Flag mod presence; do NOT touch the registry yet (mod load order is unspecified). */
    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            CrafticsMod.LOGGER.debug("[Craftics × Basic Weapons] mod not loaded - skipping registration");
            return;
        }
        loaded = true;
    }

    /** Late-phase registration. Idempotent; silent on the retry path (called from the tooltip render). */
    public static void registerDeferred() {
        if (registered || !loaded) return;
        boolean any = false;
        for (String tier : TIERS) {
            any |= registerOne(tier, "dagger", DamageType.SLASHING, 1, 1, daggerAbility());
            any |= registerOne(tier, "spear", DamageType.SLASHING, 1, 2, null);
            any |= registerOne(tier, "quarterstaff", DamageType.BLUNT, 1, 2, Abilities.sweepAdjacent(0.10, 0.05));
            any |= registerOne(tier, "club", DamageType.BLUNT, 2, 1, clubAbility());
            any |= registerOne(tier, "hammer", DamageType.BLUNT, 3, 1, hammerAbility());
            any |= registerOne(tier, "glaive", DamageType.CLEAVING, 3, 1, glaiveAbility());
        }
        if (any) {
            registered = true;
            CrafticsMod.LOGGER.info("[Craftics × Basic Weapons] enabled - registered weapon types");
        }
    }

    private static boolean registerOne(String tier, String type, DamageType dt, int ap, int range,
                                       WeaponAbilityHandler ability) {
        Item item = lookupItem(tier + "_" + type);
        if (item == null) return false;
        WeaponEntry.Builder b = WeaponEntry.builder(item)
            .damageType(dt).attackPower(damageFor(tier, type)).apCost(ap).range(range);
        if (ability != null) b.ability(ability);
        WeaponRegistry.register(item, b.build());
        return true;
    }

    static Item lookupItem(String path) {
        Identifier id = Identifier.of(NAMESPACE, path);
        if (!Registries.ITEM.containsId(id)) return null;
        return Registries.ITEM.get(id);
    }

    // =========================================================================
    // Item gating helpers
    // =========================================================================

    /** True if the item is a registered basicweapons weapon of one of the six types. */
    public static boolean isBasicWeapon(Item item) {
        if (item == null || !loaded) return false;
        Identifier id = Registries.ITEM.getId(item);
        return NAMESPACE.equals(id.getNamespace())
            && weaponType(id.getPath()) != null
            && WeaponRegistry.isRegistered(item);
    }

    /** True if the item is a registered basicweapons BLUNT weapon (club/hammer/quarterstaff). */
    public static boolean isBlunt(Item item) {
        if (!isBasicWeapon(item)) return false;
        return isBluntType(weaponType(Registries.ITEM.getId(item).getPath()));
    }

    /** Both hands hold registered basicweapons daggers. */
    public static boolean isDualDagger(Item main, Item off) {
        return isDagger(main) && isDagger(off);
    }

    private static boolean isDagger(Item item) {
        return isBasicWeapon(item)
            && "dagger".equals(weaponType(Registries.ITEM.getId(item).getPath()));
    }

    /** True if the item is a registered basicweapons spear. */
    public static boolean isSpear(Item item) {
        return isBasicWeapon(item)
            && "spear".equals(weaponType(Registries.ITEM.getId(item).getPath()));
    }

    // =========================================================================
    // Damage suppliers (lazy, derived from existing config getters)
    // =========================================================================

    /**
     * Lazy per-tier damage supplier derived from existing config getters, nudged by damage class:
     * dagger/quarterstaff = max(1, sword-1); spear = max(1, sword-3); club = axe; hammer/glaive = axe+1.
     * The spear sits below the sword on its own so its damage only catches up when the player
     * closes distance (see the per-tile movement scaling in CombatManager).
     */
    private static IntSupplier damageFor(String tier, String type) {
        return switch (type) {
            case "dagger", "quarterstaff" -> () -> Math.max(1, swordDmg(tier) - 1);
            case "spear" -> () -> Math.max(1, swordDmg(tier) - 3);
            case "club" -> () -> axeDmg(tier);
            case "hammer", "glaive" -> () -> axeDmg(tier) + 1;
            default -> () -> 1;
        };
    }

    private static int swordDmg(String tier) {
        return switch (tier) {
            case "wooden" -> CrafticsMod.CONFIG.dmgWoodenSword();
            case "stone" -> CrafticsMod.CONFIG.dmgStoneSword();
            case "iron" -> CrafticsMod.CONFIG.dmgIronSword();
            case "golden" -> CrafticsMod.CONFIG.dmgGoldenSword();
            case "diamond" -> CrafticsMod.CONFIG.dmgDiamondSword();
            case "netherite" -> CrafticsMod.CONFIG.dmgNetheriteSword();
            default -> 1;
        };
    }

    private static int axeDmg(String tier) {
        return switch (tier) {
            case "wooden" -> CrafticsMod.CONFIG.dmgWoodenAxe();
            case "stone" -> CrafticsMod.CONFIG.dmgStoneAxe();
            case "iron" -> CrafticsMod.CONFIG.dmgIronAxe();
            case "golden" -> CrafticsMod.CONFIG.dmgGoldenAxe();
            case "diamond" -> CrafticsMod.CONFIG.dmgDiamondAxe();
            case "netherite" -> CrafticsMod.CONFIG.dmgNetheriteAxe();
            default -> 1;
        };
    }

    // =========================================================================
    // Ability handlers
    // =========================================================================

    private static int mightDamage(net.minecraft.server.network.ServerPlayerEntity player) {
        return PlayerCombatStats.getMight(player);
    }

    private static double mightStun(net.minecraft.server.network.ServerPlayerEntity player) {
        return PlayerCombatStats.getMight(player) * 0.05;
    }

    /** Dagger: dual-wield -> a weaker second hit (DAGGER_OFFHAND_MULT of the main hit); else plain. */
    private static WeaponAbilityHandler daggerAbility() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            Item main = player.getMainHandStack().getItem();
            Item off = player.getOffHandStack().getItem();
            if (!isDualDagger(main, off)) {
                return new WeaponAbility.AttackResult(baseDamage, List.of(), List.of());
            }
            int offHit = Math.max(1, (int) Math.round(baseDamage * DAGGER_OFFHAND_MULT));
            int second = target.takeDamage(offHit);
            List<String> msgs = new ArrayList<>();
            msgs.add("§c✦ Dual strike " + target.getDisplayName() + " hit again for " + second);
            return new WeaponAbility.AttackResult(baseDamage + second, msgs, List.of());
        };
    }

    /** Club: BLUNT-affinity-scaled chance to slow + Might bonus damage/stun. */
    private static WeaponAbilityHandler clubAbility() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int total = baseDamage;
            int might = mightDamage(player);
            if (might > 0) {
                int bonus = target.takeDamage(might);
                total += bonus;
                msgs.add("§b✦ Might! +" + bonus + " damage.");
            }
            int bluntPts = stats != null ? stats.getAffinityPoints(PlayerProgression.Affinity.BLUNT) : 0;
            double slowChance = 0.05 + (bluntPts * 0.03) + (luckPoints * 0.02) + mightStun(player);
            if (Math.random() < slowChance) {
                target.stackSlowness(2, 1);
                msgs.add("§7✦ Slowed! " + target.getDisplayName() + " is slowed for 2 turns.");
            }
            return new WeaponAbility.AttackResult(total, msgs, List.of());
        };
    }

    /** Glaive: full hit + half-damage wide cleave arc (CLEAVING). */
    private static WeaponAbilityHandler glaiveAbility() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            List<CombatEntity> extras = new ArrayList<>();
            int total = baseDamage;
            List<GridPos> tiles = AoeShapes.sweepingEdge(arena.getPlayerGridPos(), target.getGridPos(), 2);
            for (CombatEntity e : AoeShapes.enemiesOn(arena, tiles, target)) {
                int dmg = e.takeDamage(baseDamage / 2);
                extras.add(e);
                total += dmg;
                msgs.add("§6✦ Cleave! " + e.getDisplayName() + " takes " + dmg + "!");
            }
            return new WeaponAbility.AttackResult(total, msgs, extras);
        };
    }

    /** Hammer: the vanilla mace ability plus Might bonus damage (stun rides maceAbility's blunt roll). */
    private static WeaponAbilityHandler hammerAbility() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            int might = mightDamage(player);
            int effectiveBase = baseDamage;
            List<String> pre = new ArrayList<>();
            if (might > 0) {
                int bonus = target.takeDamage(might);
                effectiveBase += bonus;
                pre.add("§b✦ Might! +" + bonus + " damage.");
            }
            WeaponAbility.AttackResult r =
                VanillaWeapons.maceAbility(player, target, arena, effectiveBase, stats, luckPoints);
            if (pre.isEmpty()) return r;
            List<String> msgs = new ArrayList<>(pre);
            msgs.addAll(r.messages());
            return new WeaponAbility.AttackResult(r.totalDamage(), msgs, r.extraTargets());
        };
    }
}
