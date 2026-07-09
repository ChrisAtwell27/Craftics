package com.crackedgames.craftics.compat.simplyswords;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.Abilities;
import com.crackedgames.craftics.api.VanillaWeapons;
import com.crackedgames.craftics.api.WeaponAbilityHandler;
import com.crackedgames.craftics.api.registry.WeaponEntry;
import com.crackedgames.craftics.api.registry.WeaponRegistry;
import com.crackedgames.craftics.combat.AoeShapes;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.DamageType;
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
 * Compatibility module for the Simply Swords mod (sweenus).
 * Registers the mod's fifteen standard weapon types (iron/gold/diamond/netherite/runic
 * tiers) into Craftics combat, mirroring the Basic Weapons compat conventions, and
 * hands the unique boss-drop weapons to {@link SimplySwordsUniques}.
 * No compile-time dependency: everything resolves by registry id and no-ops when absent.
 */
public final class SimplySwordsCompat {

    public static final String MOD_ID = "simplyswords";
    public static final String NAMESPACE = "simplyswords";

    /** Simply Swords tiers we register. Note the mod uses "gold", not "golden". */
    static final String[] TIERS = {"iron", "gold", "diamond", "netherite", "runic"};

    /** The fifteen standard weapon-type suffixes. */
    static final String[] TYPES = {
        "longsword", "twinblade", "rapier", "katana", "sai", "spear",
        "glaive", "warglaive", "cutlass", "claymore", "greataxe", "greathammer",
        "chakram", "scythe", "halberd"
    };

    // === Craftics balance tuning ===
    /** Off-hand sai second hit fraction of the main hit (dual-wield, dagger convention). */
    public static final double SAI_OFFHAND_MULT = 0.75;
    /** Twinblade back-blade follow-up fraction of the main hit (always procs). */
    public static final double TWINBLADE_SECOND_MULT = 0.50;
    /** Rapier bonus crit chance on top of the config critical roll. */
    public static final double RAPIER_CRIT_CHANCE = 0.15;
    /** Chakram ricochet fraction of base damage against the bounce target. */
    public static final double CHAKRAM_BOUNCE_MULT = 0.50;

    private static boolean loaded = false;
    private static boolean registered = false;

    private SimplySwordsCompat() {}

    public static boolean isLoaded() { return loaded; }
    public static boolean isRegistered() { return registered; }

    /**
     * Parse the weapon type from a path like "iron_longsword" -> "longsword".
     * Returns null if the path doesn't end with one of the fifteen type suffixes.
     * Pure: no registry access. Unique weapons (emberblade, ...) return null here.
     */
    public static String weaponType(String path) {
        if (path == null) return null;
        for (String type : TYPES) {
            if (path.endsWith("_" + type)) return type;
        }
        return null;
    }

    /**
     * Item PATH strings ("iron_katana", ...) for the SS standard weapons that map to a
     * Craftics gear tier: 2=iron, 3=gold, 4=diamond+netherite+runic. Wood/stone (0,1)
     * have no SS equivalent -> empty. Pure: builds paths from TIERS x TYPES, no registry.
     * Used to seed the mob modded-weapon pool (uniques are intentionally excluded).
     */
    public static java.util.List<String> standardWeaponTierIds(int craftTier) {
        java.util.List<String> ssTiers = switch (craftTier) {
            case 2 -> java.util.List.of("iron");
            case 3 -> java.util.List.of("gold");
            case 4 -> java.util.List.of("diamond", "netherite", "runic");
            default -> java.util.List.of();
        };
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (String ssTier : ssTiers) {
            for (String type : TYPES) {
                ids.add(ssTier + "_" + type);
            }
        }
        return ids;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /** Flag mod presence; do NOT touch the registry yet (mod load order is unspecified). */
    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            CrafticsMod.LOGGER.debug("[Craftics × Simply Swords] mod not loaded - skipping registration");
            return;
        }
        loaded = true;
    }

    /** Late-phase registration. Idempotent; silent on the retry path (called from the tooltip render). */
    public static void registerDeferred() {
        if (registered || !loaded) return;
        boolean any = false;
        for (String tier : TIERS) {
            // Light blades (1 AP)
            any |= registerOne(tier, "longsword", DamageType.SLASHING, 1, 1, false,
                Abilities.sweepAdjacent(0.10, 0.05));
            any |= registerOne(tier, "katana", DamageType.SLASHING, 1, 1, false, katanaAbility());
            any |= registerOne(tier, "rapier", DamageType.SLASHING, 1, 1, false, rapierAbility());
            any |= registerOne(tier, "cutlass", DamageType.SLASHING, 1, 1, false,
                Abilities.sweepAdjacent(0.20, 0.05));
            any |= registerOne(tier, "sai", DamageType.SLASHING, 1, 1, false, saiAbility());
            any |= registerOne(tier, "twinblade", DamageType.SLASHING, 1, 1, false, twinbladeAbility());
            any |= registerOne(tier, "spear", DamageType.SLASHING, 1, 2, false, null);
            any |= registerOne(tier, "warglaive", DamageType.CLEAVING, 1, 1, false, warglaiveAbility());
            // Thrown outlier (1 AP, ranged, returns to hand - no ammo)
            any |= registerOne(tier, "chakram", DamageType.RANGED, 1, 3, true, chakramAbility());
            // Mid weight (2 AP)
            any |= registerOne(tier, "halberd", DamageType.CLEAVING, 2, 2, false,
                Abilities.sweepAdjacent(0.15, 0.03));
            any |= registerOne(tier, "scythe", DamageType.CLEAVING, 2, 1, false, scytheAbility());
            // Heavy (3 AP)
            any |= registerOne(tier, "claymore", DamageType.CLEAVING, 3, 1, false, wideCleaveAbility());
            any |= registerOne(tier, "glaive", DamageType.CLEAVING, 3, 1, false, wideCleaveAbility());
            any |= registerOne(tier, "greataxe", DamageType.CLEAVING, 3, 1, false,
                Abilities.armorIgnore(0.15, 0.03));
            any |= registerOne(tier, "greathammer", DamageType.BLUNT, 3, 1, false, greathammerAbility());
        }
        // Unique boss-drop weapons - registered separately, each with its own effect + VFX.
        any |= SimplySwordsUniques.registerAll();
        if (any) {
            registered = true;
            CrafticsMod.LOGGER.info("[Craftics × Simply Swords] enabled - registered weapon types + uniques");
        }
    }

    private static boolean registerOne(String tier, String type, DamageType dt, int ap, int range,
                                       boolean ranged, WeaponAbilityHandler ability) {
        Item item = lookupItem(tier + "_" + type);
        if (item == null) return false;
        WeaponEntry.Builder b = WeaponEntry.builder(item)
            .damageType(dt).attackPower(damageFor(tier, type)).apCost(ap).range(range).ranged(ranged);
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

    /** True if the item is a registered simplyswords weapon of one of the fifteen standard types. */
    public static boolean isStandardWeapon(Item item) {
        if (item == null || !loaded) return false;
        Identifier id = Registries.ITEM.getId(item);
        return NAMESPACE.equals(id.getNamespace())
            && weaponType(id.getPath()) != null
            && WeaponRegistry.isRegistered(item);
    }

    /** True for any simplyswords item Craftics registered (standard type or unique). */
    public static boolean isSimplySword(Item item) {
        if (item == null || !loaded) return false;
        Identifier id = Registries.ITEM.getId(item);
        return NAMESPACE.equals(id.getNamespace()) && WeaponRegistry.isRegistered(item);
    }

    /** True if the item is a registered simplyswords spear (shares the Basic Weapons momentum mechanic). */
    public static boolean isSpear(Item item) {
        return isStandardWeapon(item)
            && "spear".equals(weaponType(Registries.ITEM.getId(item).getPath()));
    }

    private static boolean isSai(Item item) {
        return isStandardWeapon(item)
            && "sai".equals(weaponType(Registries.ITEM.getId(item).getPath()));
    }

    private static boolean isWarglaive(Item item) {
        return isStandardWeapon(item)
            && "warglaive".equals(weaponType(Registries.ITEM.getId(item).getPath()));
    }

    // =========================================================================
    // Damage suppliers (lazy, derived from existing config getters)
    // =========================================================================

    /**
     * Lazy per-tier damage supplier derived from existing config getters, nudged by damage class:
     * longsword = sword; katana/rapier/cutlass/twinblade/warglaive = sword-1; sai/chakram = sword-2;
     * spear = sword-3 (momentum catches up); halberd/scythe = axe; claymore/glaive/greataxe/
     * greathammer = axe+1. The runic tier reads netherite +1.
     */
    private static IntSupplier damageFor(String tier, String type) {
        return switch (type) {
            case "longsword" -> () -> swordDmg(tier);
            case "katana", "rapier", "cutlass", "twinblade", "warglaive" -> () -> Math.max(1, swordDmg(tier) - 1);
            case "sai", "chakram" -> () -> Math.max(1, swordDmg(tier) - 2);
            case "spear" -> () -> Math.max(1, swordDmg(tier) - 3);
            case "halberd", "scythe" -> () -> axeDmg(tier);
            case "claymore", "glaive", "greataxe", "greathammer" -> () -> axeDmg(tier) + 1;
            default -> () -> 1;
        };
    }

    private static int swordDmg(String tier) {
        return switch (tier) {
            case "iron" -> CrafticsMod.CONFIG.dmgIronSword();
            case "gold" -> CrafticsMod.CONFIG.dmgGoldenSword();
            case "diamond" -> CrafticsMod.CONFIG.dmgDiamondSword();
            case "netherite" -> CrafticsMod.CONFIG.dmgNetheriteSword();
            case "runic" -> CrafticsMod.CONFIG.dmgNetheriteSword() + 1;
            default -> 1;
        };
    }

    private static int axeDmg(String tier) {
        return switch (tier) {
            case "iron" -> CrafticsMod.CONFIG.dmgIronAxe();
            case "gold" -> CrafticsMod.CONFIG.dmgGoldenAxe();
            case "diamond" -> CrafticsMod.CONFIG.dmgDiamondAxe();
            case "netherite" -> CrafticsMod.CONFIG.dmgNetheriteAxe();
            case "runic" -> CrafticsMod.CONFIG.dmgNetheriteAxe() + 1;
            default -> 1;
        };
    }

    // =========================================================================
    // Ability handlers (standard types)
    // =========================================================================

    /** Katana: SLASHING-affinity-scaled chance to open a bleeding wound. */
    private static WeaponAbilityHandler katanaAbility() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int slashPts = stats != null ? stats.getAffinityPoints(PlayerProgression.Affinity.SLASHING) : 0;
            double chance = 0.25 + slashPts * 0.03 + luckPoints * 0.02;
            if (Math.random() < chance) {
                target.stackBleed(1);
                msgs.add("§c✦ Keen edge! " + target.getDisplayName() + " is bleeding.");
            }
            return new WeaponAbility.AttackResult(baseDamage, msgs, List.of());
        };
    }

    /** Rapier: precision - flat bonus crit chance for a double-damage thrust. */
    private static WeaponAbilityHandler rapierAbility() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int total = baseDamage;
            if (Math.random() < RAPIER_CRIT_CHANCE + luckPoints * 0.02) {
                int bonus = target.takeDamage(baseDamage);
                total += bonus;
                msgs.add("§e✦ Riposte! Precision thrust for +" + bonus + " damage.");
            }
            return new WeaponAbility.AttackResult(total, msgs, List.of());
        };
    }

    /** Sai: dual-wield -> a weaker second hit (SAI_OFFHAND_MULT of the main hit); else plain. */
    private static WeaponAbilityHandler saiAbility() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            Item main = player.getMainHandStack().getItem();
            Item off = player.getOffHandStack().getItem();
            if (!(isSai(main) && isSai(off))) {
                return new WeaponAbility.AttackResult(baseDamage, List.of(), List.of());
            }
            int offHit = Math.max(1, (int) Math.round(baseDamage * SAI_OFFHAND_MULT));
            int second = target.takeDamage(offHit);
            List<String> msgs = new ArrayList<>();
            msgs.add("§c✦ Twin sai strike! " + target.getDisplayName() + " hit again for " + second);
            return new WeaponAbility.AttackResult(baseDamage + second, msgs, List.of());
        };
    }

    /** Twinblade: the back blade always follows through for a half-damage second hit. */
    private static WeaponAbilityHandler twinbladeAbility() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            int offHit = Math.max(1, (int) Math.round(baseDamage * TWINBLADE_SECOND_MULT));
            int second = target.takeDamage(offHit);
            List<String> msgs = new ArrayList<>();
            msgs.add("§e✦ Twinblade spin! Second blade hits for " + second);
            return new WeaponAbility.AttackResult(baseDamage + second, msgs, List.of());
        };
    }

    /** Warglaive: dual-wield pair -> cleave both tiles adjacent to the target for half damage. */
    private static WeaponAbilityHandler warglaiveAbility() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            Item main = player.getMainHandStack().getItem();
            Item off = player.getOffHandStack().getItem();
            if (!(isWarglaive(main) && isWarglaive(off))) {
                return new WeaponAbility.AttackResult(baseDamage, List.of(), List.of());
            }
            List<String> msgs = new ArrayList<>();
            List<CombatEntity> extras = new ArrayList<>();
            int total = baseDamage;
            for (CombatEntity e : Abilities.findAdjacentEnemies(arena, target, 2)) {
                int dmg = e.takeDamage(baseDamage / 2);
                extras.add(e);
                total += dmg;
                msgs.add("§6✦ Paired glaives cleave " + e.getDisplayName() + " for " + dmg + "!");
            }
            return new WeaponAbility.AttackResult(total, msgs, extras);
        };
    }

    /** Chakram: thrown disc ricochets to the nearest other enemy within 2 tiles of the target.
     *  Package-private: the Tempest and Chomp'olotl uniques compose it with their own procs. */
    static WeaponAbilityHandler chakramAbility() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            List<CombatEntity> extras = new ArrayList<>();
            int total = baseDamage;
            CombatEntity bounce = null;
            int bestDist = Integer.MAX_VALUE;
            for (CombatEntity e : arena.getOccupants().values()) {
                if (e == target || !e.isAlive() || e.isAlly()) continue;
                int d = e.getGridPos().manhattanDistance(target.getGridPos());
                if (d <= 2 && d < bestDist) { bestDist = d; bounce = e; }
            }
            if (bounce != null) {
                int dmg = bounce.takeDamage(Math.max(1, (int) Math.round(baseDamage * CHAKRAM_BOUNCE_MULT)));
                extras.add(bounce);
                total += dmg;
                msgs.add("§b✦ Ricochet! The chakram bounces to " + bounce.getDisplayName() + " for " + dmg + "!");
                if (player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
                    com.crackedgames.craftics.combat.ProjectileSpawner.spawnProjectile(sw,
                        arena.gridToBlockPos(target.getGridPos()),
                        arena.gridToBlockPos(bounce.getGridPos()), "arrow");
                }
            }
            return new WeaponAbility.AttackResult(total, msgs, extras);
        };
    }

    /** Scythe: reaping arc - half damage across the sweep line, bleeding each enemy caught. */
    private static WeaponAbilityHandler scytheAbility() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            List<CombatEntity> extras = new ArrayList<>();
            int total = baseDamage;
            List<GridPos> tiles = AoeShapes.sweepingEdge(arena.getPlayerGridPos(), target.getGridPos(), 2);
            for (CombatEntity e : AoeShapes.enemiesOn(arena, tiles, target)) {
                int dmg = e.takeDamage(baseDamage / 2);
                e.stackBleed(1);
                extras.add(e);
                total += dmg;
                msgs.add("§6✦ Reap! " + e.getDisplayName() + " takes " + dmg + " and bleeds!");
            }
            return new WeaponAbility.AttackResult(total, msgs, extras);
        };
    }

    /** Claymore / glaive: full hit + half-damage wide cleave arc (Basic Weapons glaive convention). */
    private static WeaponAbilityHandler wideCleaveAbility() {
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

    /** Greathammer: the vanilla mace ability (3x3 AoE + knockback + blunt stun roll). */
    private static WeaponAbilityHandler greathammerAbility() {
        return VanillaWeapons::maceAbility;
    }
}
