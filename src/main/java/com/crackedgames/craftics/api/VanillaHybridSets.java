package com.crackedgames.craftics.api;

import com.crackedgames.craftics.api.registry.HybridSetEntry;
import com.crackedgames.craftics.api.registry.HybridSetRegistry;
import com.crackedgames.craftics.combat.HybridEffect;

/**
 * Registers Craftics' built-in hybrid armor sets — the 15 standard material pairs.
 * The 6 copper pairs are registered separately by the Copper Age compat module, so
 * they exist only when that mod is installed. Craftics dogfoods the hybrid API:
 * every pair is a normal {@link HybridSetRegistry} registration.
 *
 * @since 0.2.0
 */
public final class VanillaHybridSets {

    private VanillaHybridSets() {}

    /** Register every built-in standard hybrid. Called once from {@code CrafticsMod.onInitialize()}. */
    public static void registerAll() {
        register("leather", "chainmail", "Skirmisher", HybridEffect.SKIRMISHER,
            "If you moved before attacking, that attack deals +3 damage");
        register("leather", "iron", "Counterpuncher", HybridEffect.COUNTERPUNCHER,
            "When attacked, 50% chance to immediately attack back");
        register("leather", "gold", "Lucky Streak", HybridEffect.LUCKY_STREAK,
            "Each consecutive kill: +10% crit chance (resets when hit)");
        register("leather", "diamond", "Breaker", HybridEffect.BREAKER,
            "Your attacks ignore enemy damage-type resistances");
        register("leather", "netherite", "Rampage", HybridEffect.RAMPAGE,
            "Getting a kill refunds 1 AP");
        register("chainmail", "iron", "Sentinel", HybridEffect.SENTINEL,
            "When you dodge an attack, riposte the attacker for 3 damage");
        register("chainmail", "gold", "Cutpurse", HybridEffect.CUTPURSE,
            "Critical hits restore 2 HP");
        register("chainmail", "diamond", "Duelist", HybridEffect.DUELIST,
            "+4 damage vs a target with no enemies adjacent to it");
        register("chainmail", "netherite", "Ambush", HybridEffect.AMBUSH,
            "Your first attack each combat is a guaranteed critical");
        register("iron", "gold", "Gilded Guard", HybridEffect.GILDED_GUARD,
            "15% chance to fully negate an incoming hit");
        register("iron", "diamond", "Warlord", HybridEffect.WARLORD,
            "Your melee attacks knock the target back 1 tile");
        register("iron", "netherite", "Immovable", HybridEffect.IMMOVABLE,
            "Immune to knockback; reflect 2 damage to melee attackers");
        register("gold", "diamond", "Gladiator", HybridEffect.GLADIATOR,
            "Your critical hits deal +50% extra damage");
        register("gold", "netherite", "Berserker", HybridEffect.BERSERKER,
            "Crit chance rises as your HP drops");
        register("diamond", "netherite", "Stonewall", HybridEffect.STONEWALL,
            "Incoming damage is capped at 6 per hit");
    }

    private static void register(String matA, String matB, String className,
                                 HybridEffect effect, String description) {
        HybridSetRegistry.register(HybridSetEntry.builder(matA, matB)
            .className(className).effect(effect).description(description).build());
    }
}
