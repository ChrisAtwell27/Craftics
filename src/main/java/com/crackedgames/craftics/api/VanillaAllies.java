package com.crackedgames.craftics.api;

import com.crackedgames.craftics.api.registry.AllyEntry;
import com.crackedgames.craftics.api.registry.AllyRegistry;

/**
 * Registers Craftics' built-in combat allies. Craftics dogfoods the ally API:
 * every vanilla combat pet is a normal {@link AllyEntry} registration, exactly
 * as an addon would register its own.
 *
 * <p>The 10 genuine combat pets are registered as hub-recruitable. Farm and
 * passive animals (cow, sheep, pig, …) are also registered — they are not
 * recruited from the hub, but the game's in-combat taming mechanic needs their
 * combat stats — so they use {@link AllyEntry.RecruitMode#IN_COMBAT_ONLY}.
 *
 * @since 0.2.0
 */
public final class VanillaAllies {

    private VanillaAllies() {}

    /** Register every built-in ally. Called once from {@code CrafticsMod.onInitialize()}. */
    public static void registerAll() {
        // register(entityTypeId, hp, atk, def, speed, range)
        // Combat predators
        register("minecraft:wolf",   8, 3, 0, 3, 1);
        register("minecraft:cat",    6, 2, 0, 4, 1);
        register("minecraft:parrot", 3, 1, 0, 4, 1);

        // Ranged / special
        register("minecraft:llama", 10, 2, 1, 2, 2);

        // Mounts (decent stats when fighting on foot)
        register("minecraft:horse",          12, 2, 1, 3, 1);
        register("minecraft:donkey",         12, 2, 1, 3, 1);
        register("minecraft:mule",           12, 2, 1, 3, 1);
        register("minecraft:skeleton_horse", 12, 2, 1, 3, 1);
        register("minecraft:zombie_horse",   12, 2, 1, 3, 1);
        register("minecraft:camel",          12, 2, 1, 3, 1);

        // In-combat-tameable only — registered for combat stats (the mid-battle
        // taming mechanic needs them), but never recruited from the hub.
        // register(entityTypeId, hp, atk, def, speed, range)
        registerInCombatOnly("minecraft:ocelot",    6, 2, 0, 4, 1);
        registerInCombatOnly("minecraft:fox",       7, 3, 0, 3, 1);
        registerInCombatOnly("minecraft:goat",      8, 3, 1, 2, 1);
        registerInCombatOnly("minecraft:bee",       4, 2, 0, 4, 1);
        registerInCombatOnly("minecraft:frog",      5, 2, 0, 3, 1);
        registerInCombatOnly("minecraft:axolotl",   6, 2, 0, 3, 1);
        registerInCombatOnly("minecraft:cow",      12, 1, 2, 1, 1);
        registerInCombatOnly("minecraft:mooshroom",12, 1, 2, 1, 1);
        registerInCombatOnly("minecraft:turtle",   10, 1, 3, 1, 1);
        registerInCombatOnly("minecraft:sniffer",  10, 1, 1, 1, 1);
        registerInCombatOnly("minecraft:sheep",     8, 2, 1, 2, 1);
        registerInCombatOnly("minecraft:pig",       8, 2, 1, 2, 1);
        registerInCombatOnly("minecraft:chicken",   4, 1, 0, 3, 1);
        registerInCombatOnly("minecraft:rabbit",    4, 1, 0, 3, 1);

        // Golems — built combat allies, each healed in battle by its build material:
        // an iron ingot patches up an iron golem, a snowball repacks a snow golem.
        AllyRegistry.register(AllyEntry.builder("minecraft:iron_golem")
            .hp(20).attack(5).defense(3).speed(2).range(1)
            .recruitMode(AllyEntry.RecruitMode.BUILT)
            .scalesWithOwnerGear(true)
            .healItem(net.minecraft.item.Items.IRON_INGOT, 6)
            .build());
        AllyRegistry.register(AllyEntry.builder("minecraft:snow_golem")
            .hp(5).attack(2).defense(0).speed(2).range(3)
            .recruitMode(AllyEntry.RecruitMode.BUILT)
            .scalesWithOwnerGear(true)
            .healItem(net.minecraft.item.Items.SNOWBALL, 3)
            .build());
    }

    /** Register one tamed combat pet using the default melee AI. */
    private static void register(String entityTypeId, int hp, int atk, int def, int speed, int range) {
        AllyRegistry.register(AllyEntry.builder(entityTypeId)
            .hp(hp).attack(atk).defense(def).speed(speed).range(range)
            .recruitMode(AllyEntry.RecruitMode.TAMED)
            .scalesWithOwnerGear(true)
            .build());
    }

    /** Register one in-combat-tameable mob: combat stats defined, but never hub-recruited. */
    private static void registerInCombatOnly(String entityTypeId, int hp, int atk, int def, int speed, int range) {
        AllyRegistry.register(AllyEntry.builder(entityTypeId)
            .hp(hp).attack(atk).defense(def).speed(speed).range(range)
            .recruitMode(AllyEntry.RecruitMode.IN_COMBAT_ONLY)
            .scalesWithOwnerGear(true)
            .build());
    }
}
