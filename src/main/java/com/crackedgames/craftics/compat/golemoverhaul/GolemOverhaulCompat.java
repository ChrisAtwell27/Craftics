package com.crackedgames.craftics.compat.golemoverhaul;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.registry.AllyEntry;
import com.crackedgames.craftics.api.registry.AllyRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

/**
 * Compatibility module for the <a href="https://modrinth.com/mod/golem-overhaul">Golem
 * Overhaul</a> mod — the basis of the Craftics "pet class" expansion.
 *
 * <p>Every golem the mod adds is registered as a {@link AllyEntry.RecruitMode#BUILT}
 * combat ally: build one in the hub yard and it joins the next battle, like the
 * vanilla iron/snow golems already do. Each is healed in combat by the material it
 * is built from (a lump of coal patches a coal golem, a honey bottle a honey golem).
 *
 * <p>Like {@code CreeperOverhaulCompat}, entries are registered whether or not the
 * mod is present — a missing mod just means no hub golem ever matches, while any
 * datapack/addon referencing these ids by name still resolves. {@link #isLoaded()}
 * reports actual presence.
 *
 * <h2>Roadmap — bespoke mechanics layered on top of this foundation</h2>
 * The stats below make every golem a functioning melee ally today. The signature
 * abilities are staged as follow-up phases (each touches CombatManager / a custom
 * {@code AllyAI} / the VFX layer):
 * <ul>
 *   <li><b>Netherite golem</b> — rideable mount (speed locked to 1 + player SPD stat,
 *       additive). While ridden: player attacks as normal and the golem erupts a
 *       straight lava-line ({@code TileType.LAVA}, 3 turns) ahead of it. Mount-ability
 *       keybind (3 AP) summons 2 coal golem allies.</li>
 *   <li><b>Honey golem</b> — immobile; spawns one bee ally each round that lives 2
 *       rounds (custom {@code AllyAI} + round summon hook).</li>
 *   <li><b>Coal golem</b> — basic attacker (speed 2); flint &amp; steel ignites it into a
 *       fire-aspect attacker (heavy burn, speed 4) that dies after one attack.</li>
 *   <li><b>Slime golem</b> — splits into two small slime golems on death.</li>
 *   <li><b>Terracotta golem</b> — armored taunt-tank that forces enemy targeting.</li>
 *   <li><b>Hay golem</b> — support healer; mends the lowest-HP ally each round.</li>
 *   <li><b>Kelp golem</b> — Soaks on hit; bonus on water tiles.</li>
 *   <li><b>Candle golem</b> — ranged caster; lobs a candle-flame that ignites a tile (3-turn FIRE).</li>
 *   <li><b>Barrel golem</b> — bruiser that "barters" a loot drop on a kill.</li>
 * </ul>
 * Each ability ships with an over-the-top VFX (projectile trails via
 * {@code ProjectileSpawner.spawnProjectile/spawnSpellTrail}, summon rings via
 * {@code spawnExpandingRing/spawnConverging}, screen shake/flash via
 * {@code VfxClientPayload}).
 */
public final class GolemOverhaulCompat {

    public static final String MOD_ID = "golemoverhaul";

    private static boolean registered = false;
    private static boolean loaded = false;

    private GolemOverhaulCompat() {}

    /** {@code golemoverhaul:<path>} */
    private static String id(String path) { return MOD_ID + ":" + path; }

    /**
     * Register every golem ally. Safe to call whether the mod is loaded or not —
     * called once from {@code CrafticsMod.onInitialize()} after
     * {@code VanillaAllies.registerAll()}.
     */
    public static void init() {
        if (registered) return;
        registered = true;

        // builder(id).hp.attack.defense.speed.range — BUILT (no taming) + build-material heal.
        // Basic attacker; flint & steel "lit" transform handled in the coal-golem phase.
        golem("coal_golem",        8,  3, 0, 2, 1, Items.COAL,           4);
        // Mount. Low base speed by design — only the player's SPD stat raises it (additive).
        golem("netherite_golem",  30,  8, 5, 1, 1, Items.NETHERITE_SCRAP, 8);
        // Immobile summoner (speed 0 enforced by its phase-2 AI); spawns bees.
        golem("honey_golem",      16,  2, 2, 0, 1, Items.HONEY_BOTTLE,   5);
        // Splitter — spawns two small slime golems on death (phase).
        golem("slime_golem",      14,  3, 1, 2, 1, Items.SLIME_BALL,     5);
        // Armored taunt-tank.
        golem("terracotta_golem", 18,  3, 4, 2, 1, Items.TERRACOTTA,     5);
        // Support healer.
        golem("hay_golem",        12,  2, 1, 2, 1, Items.WHEAT,          4);
        // Soaker; bonus on water.
        golem("kelp_golem",       12,  3, 1, 2, 1, Items.KELP,           4);
        // Ranged fire caster.
        golem("candle_golem",      8,  3, 0, 2, 3, Items.CANDLE,         3);
        // Loot-bartering bruiser.
        golem("barrel_golem",     14,  3, 2, 2, 1, Items.OAK_PLANKS,     4);

        loaded = FabricLoader.getInstance().isModLoaded(MOD_ID);
        if (loaded) {
            CrafticsMod.LOGGER.info(
                "[Craftics × Golem Overhaul] enabled — 9 golem allies registered (pet-class expansion)");
        } else {
            CrafticsMod.LOGGER.debug(
                "[Craftics × Golem Overhaul] mod not loaded — golem ally entries registered for any future use");
        }
    }

    public static boolean isLoaded() { return loaded; }

    /** Register one golem as a built, gear-scaling combat ally healed by its build material. */
    private static void golem(String path, int hp, int atk, int def, int speed, int range,
                              Item healItem, int healAmount) {
        AllyRegistry.register(AllyEntry.builder(id(path))
            .hp(hp).attack(atk).defense(def).speed(speed).range(range)
            .recruitMode(AllyEntry.RecruitMode.BUILT)
            .scalesWithOwnerGear(true)
            .healItem(healItem, healAmount)
            .build());
    }
}
