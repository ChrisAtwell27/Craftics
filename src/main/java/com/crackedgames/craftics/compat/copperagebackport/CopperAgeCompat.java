package com.crackedgames.craftics.compat.copperagebackport;

import com.crackedgames.craftics.CrafticsMod;
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
 * Adds the mod's copper tier to Craftics combat:
 * <ul>
 *   <li>Copper sword/axe/pickaxe/shovel/hoe register as <b>Ranged</b> affinity weapons —
 *       copper is treated as a "marksman alloy" that benefits the same affinity tree as
 *       the bow and crossbow. Slots cleanly between stone and iron in raw damage.</li>
 *   <li>Wearing the full copper armor set ("Marksman") grants a Ranged damage bonus,
 *       extra range, and a small attack power buff that synergises with bow/crossbow
 *       builds.</li>
 * </ul>
 * <p>
 * The mod registers its tools/armor under the {@code minecraft:} namespace (verified
 * from the JAR's asset paths), so we resolve the items via the live item registry
 * after the mod has registered them. If the items aren't present (mod missing or
 * disabled) every call no-ops and the registry stays clean.
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
     * Register copper tools as Ranged-affinity weapons. Stats slot between stone
     * (3 atk sword) and iron (5 atk sword) — copper is canonically a softer tier
     * than iron, so 4 atk for the sword and similar for the rest.
     */
    private static boolean registerWeapons() {
        boolean any = false;

        // Ranged-themed sword ability: small chance to "ricochet" into an adjacent
        // enemy. Keeps with the marksman fantasy without being too strong.
        WeaponAbilityHandler ricochet = (player, target, arena, baseDamage, stats, luckPoints) -> {
            java.util.List<String> messages = new java.util.ArrayList<>();
            java.util.List<com.crackedgames.craftics.combat.CombatEntity> extras = new java.util.ArrayList<>();
            int extra = 0;
            int rangedPts = stats != null
                ? stats.getAffinityPoints(com.crackedgames.craftics.combat.PlayerProgression.Affinity.RANGED)
                : 0;
            double chance = 0.10 + rangedPts * 0.04
                + luckPoints * com.crackedgames.craftics.api.VanillaWeapons.LUCK_BONUS_PER_POINT;
            if (Math.random() < chance) {
                for (int dx = -1; dx <= 1 && extras.isEmpty(); dx++) {
                    for (int dz = -1; dz <= 1 && extras.isEmpty(); dz++) {
                        if (dx == 0 && dz == 0) continue;
                        var adj = new com.crackedgames.craftics.core.GridPos(
                            target.getGridPos().x() + dx, target.getGridPos().z() + dz);
                        var hit = arena.getOccupant(adj);
                        if (hit != null && hit.isAlive() && hit != target && !hit.isAlly()) {
                            int dmg = hit.takeDamage(Math.max(1, baseDamage / 2));
                            extra += dmg;
                            extras.add(hit);
                            messages.add("\u00a76\u27a4 Ricochet! " + hit.getDisplayName() + " takes " + dmg + " bounce damage!");
                        }
                    }
                }
            }
            return new com.crackedgames.craftics.combat.WeaponAbility.AttackResult(baseDamage + extra, messages, extras);
        };

        any |= registerWeapon("copper_sword", DamageType.RANGED, 4, 1, 1, false, 0.0, ricochet);
        any |= registerWeapon("copper_axe",    DamageType.RANGED, 5, 2, 1, false, 0.0, null);
        any |= registerWeapon("copper_pickaxe", DamageType.RANGED, 3, 1, 1, false, 0.0, null);
        any |= registerWeapon("copper_shovel", DamageType.RANGED, 3, 1, 1, false, 0.0, null);
        any |= registerWeapon("copper_hoe",    DamageType.RANGED, 2, 1, 1, false, 0.0, null);
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

    /**
     * Register the copper armor set bonus. Detection is wired up in
     * {@link com.crackedgames.craftics.combat.PlayerCombatStats#getArmorSet}.
     */
    private static boolean registerArmorSet() {
        // Only register the set if at least one piece is actually present, otherwise
        // the bonus would never trigger anyway.
        if (lookupItem("copper_helmet") == null) return false;

        ArmorSetRegistry.register(ArmorSetEntry.builder("copper")
            .damageBonus(DamageType.RANGED, 2)
            .attackBonus(1)
            .description("\u00a76Marksman: +2 Ranged dmg, +1 Attack, ranged weapons gain +1 range")
            .build());
        return true;
    }

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
