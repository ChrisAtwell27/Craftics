package com.crackedgames.craftics.compat.golemoverhaul;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.registry.AllyEntry;
import com.crackedgames.craftics.api.registry.AllyRegistry;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.ally.AllyAbilities;
import com.crackedgames.craftics.combat.ai.ally.AllyArchetypes;
import com.crackedgames.craftics.combat.ai.ally.AllyDeathRegistry;
import com.crackedgames.craftics.combat.ai.ally.AllyKillRegistry;
import com.crackedgames.craftics.combat.ai.ally.AllyTauntRegistry;
import com.crackedgames.craftics.combat.ai.ally.IgnitableAllyRegistry;
import com.crackedgames.craftics.combat.ai.ally.RangedAllyAI;
import com.crackedgames.craftics.combat.ai.ally.SupportAllyAI;
import com.crackedgames.craftics.combat.ai.ally.TankAllyAI;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

/**
 * Compatibility module for the <a href="https://modrinth.com/mod/golem-overhaul">Golem
 * Overhaul</a> mod, the basis of the Craftics "pet class" expansion.
 *
 * <p>Every golem the mod adds is registered as a {@link AllyEntry.RecruitMode#BUILT}
 * combat ally (no taming required): build one in the hub, then add it to your battle
 * party (Shift+Right-Click it) so it joins the next battle and returns home afterward,
 * exactly like the vanilla iron/snow golems. Each is healed in combat by the material it
 * is built from (a lump of coal patches a coal golem, a honey bottle a honey golem).
 *
 * <p>Like {@code CreeperOverhaulCompat}, entries are registered whether or not the
 * mod is present: a missing mod just means no hub golem ever matches, while any
 * datapack/addon referencing these ids by name still resolves. {@link #isLoaded()}
 * reports actual presence.
 *
 * <h2>Roadmap: bespoke mechanics layered on top of this foundation</h2>
 * The stats below make every golem a functioning melee ally today. The signature
 * abilities are staged as follow-up phases (each touches CombatManager / a custom
 * {@code AllyAI} / the VFX layer):
 * <ul>
 *   <li><b>Netherite golem</b>: rideable mount (speed locked to 1 + player SPD stat,
 *       additive). While ridden: player attacks as normal and the golem erupts a
 *       straight lava-line ({@code TileType.LAVA}, 3 turns) ahead of it. Mount-ability
 *       keybind (3 AP) summons 2 coal golem allies.</li>
 *   <li><b>Honey golem</b>: immobile; spawns one bee ally each round that lives 2
 *       rounds (custom {@code AllyAI} + round summon hook).</li>
 *   <li><b>Coal golem</b>: basic attacker (speed 2); flint &amp; steel ignites it into a
 *       fire-aspect attacker (heavy burn, speed 4) that dies after one attack.</li>
 *   <li><b>Slime golem</b>: splits into two small slime golems on death.</li>
 *   <li><b>Terracotta golem</b>: armored taunt-tank that forces enemy targeting.</li>
 *   <li><b>Hay golem</b>: support healer; mends the lowest-HP ally each round.</li>
 *   <li><b>Kelp golem</b>: Soaks on hit; bonus on water tiles.</li>
 *   <li><b>Candle golem</b>: ranged caster; lobs a candle-flame that ignites a tile (3-turn FIRE).</li>
 *   <li><b>Barrel golem</b>: bruiser that "barters" a loot drop on a kill.</li>
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
     * Register every golem ally. Safe to call whether the mod is loaded or not,
     * called once from {@code CrafticsMod.onInitialize()} after
     * {@code VanillaAllies.registerAll()}.
     */
    public static void init() {
        if (registered) return;
        registered = true;

        // builder(id).hp.attack.defense.speed.range: BUILT (no taming) + build-material heal.
        // Basic attacker; flint & steel "lit" transform handled in the coal-golem phase.
        golem("coal_golem",        8,  3, 0, 2, 1, Items.COAL,           4);
        // Mount. Low base speed by design: only the player's SPD stat raises it (additive).
        // rideable(true) marks it a combat mount the player can ride (PartyMobs/CombatManager).
        AllyRegistry.register(AllyEntry.builder(id("netherite_golem"))
            .hp(30).attack(8).defense(5).speed(1).range(1)
            .recruitMode(AllyEntry.RecruitMode.BUILT)
            .scalesWithOwnerGear(true)
            .rideable(true)
            .healItem(Items.NETHERITE_SCRAP, 8)
            .build());
        // Honey: support summoner - spawns one bee ally each round (bee lives ~2 rounds).
        // Holds station via the SUPPORT archetype; the speed(0) below is declarative only
        // (ally spawning derives move speed from defaults today), full immobility is a later phase.
        AllyRegistry.register(AllyEntry.builder(id("honey_golem"))
            .hp(16).attack(2).defense(2).speed(0).range(1)
            .recruitMode(AllyEntry.RecruitMode.BUILT)
            .scalesWithOwnerGear(true)
            .healItem(Items.HONEY_BOTTLE, 5)
            .roundHook((self, ctx) -> {
                ctx.summonAlly("minecraft:bee", self, 2);
                ctx.message("§eThe honey golem releases a bee!");
            })
            .build());
        // Splitter: spawns two small slime golems on death (phase).
        golem("slime_golem",      14,  3, 1, 2, 1, Items.SLIME_BALL,     5);
        // Armored taunt-tank.
        golem("terracotta_golem", 18,  3, 4, 2, 1, Items.TERRACOTTA,     5);
        // Support healer: mends the lowest-HP injured ally each round.
        AllyRegistry.register(AllyEntry.builder(id("hay_golem"))
            .hp(12).attack(2).defense(1).speed(2).range(1)
            .recruitMode(AllyEntry.RecruitMode.BUILT)
            .scalesWithOwnerGear(true)
            .healItem(Items.WHEAT, 4)
            .roundHook((self, ctx) -> {
                CombatEntity lowest = null;
                for (CombatEntity a : ctx.allies()) {
                    if (a == self) continue;
                    if (a.getCurrentHp() >= a.getMaxHp()) continue;
                    if (lowest == null || a.getCurrentHp() < lowest.getCurrentHp()) lowest = a;
                }
                if (lowest != null) {
                    ctx.healAlly(lowest, 4);
                    ctx.message("§aThe hay golem mends " + lowest.getDisplayName() + "!");
                }
            })
            .build());
        // Soaker; bonus on water.
        golem("kelp_golem",       12,  3, 1, 2, 1, Items.KELP,           4);
        // Ranged fire caster.
        golem("candle_golem",      8,  3, 0, 2, 3, Items.CANDLE,         3);
        // Loot-bartering bruiser.
        golem("barrel_golem",     14,  3, 2, 2, 1, Items.OAK_PLANKS,     4);

        // Register a weaker small slime spawned only by the slime-golem death split,
        // never hub-recruited (IN_COMBAT_ONLY), only injected by checkAndHandleDeath.
        AllyRegistry.register(AllyEntry.builder(id("small_slime_golem"))
            .hp(5).attack(2).defense(0).speed(2).range(1)
            .recruitMode(AllyEntry.RecruitMode.IN_COMBAT_ONLY)
            .scalesWithOwnerGear(true)
            .healItem(Items.SLIME_BALL, 2)
            .build());

        registerBehaviors();

        loaded = FabricLoader.getInstance().isModLoaded(MOD_ID);
        if (loaded) {
            CrafticsMod.LOGGER.info(
                "[Craftics × Golem Overhaul] enabled - 9 hub golem allies + 1 combat-only small slime registered (pet-class expansion)");
        } else {
            CrafticsMod.LOGGER.debug(
                "[Craftics × Golem Overhaul] mod not loaded - golem ally entries registered for any future use");
        }
    }

    public static boolean isLoaded() { return loaded; }

    /**
     * Assign combat archetypes and on-hit effects for the golems. Pure-logic
     * (no Minecraft registry) so it is unit-testable; called from {@link #init()}.
     * Golems left unregistered (coal, slime, kelp-archetype, barrel, netherite)
     * fall through to the melee default. Coal's BURN is conditional (only when
     * lit) and handled by the lit-coal path, not here.
     */
    static void registerBehaviors() {
        AllyArchetypes.register(id("terracotta_golem"), new TankAllyAI());
        AllyArchetypes.register(id("hay_golem"),        new SupportAllyAI());
        AllyArchetypes.register(id("honey_golem"),      new SupportAllyAI());
        AllyArchetypes.register(id("candle_golem"),     new RangedAllyAI());

        // Honey-golem bee: engage the NEAREST enemy on its spawn round. The default
        // FlyerAllyAI dives on the globally weakest enemy, so a bee spawned at the
        // immobile honey golem's backline trots toward a distant straggler instead of
        // stinging the nearest threat. MeleeAllyAI scores nearest-first and move-and-
        // attacks in one turn (the bee's move speed 4 closes most gaps), so it aggros
        // immediately. NOTE: AllyArchetypes is a global per-type map, so this overrides
        // the AI for EVERY bee ally (including a bee the player manually party-adds), not
        // only the honey-golem summon - acceptable since nearest-first targeting suits any
        // combat bee.
        AllyArchetypes.register("minecraft:bee",
            new com.crackedgames.craftics.combat.ai.ally.MeleeAllyAI());

        AllyAbilities.register(id("kelp_golem"),   AllyAbilities.OnHitEffect.SOAK);
        AllyAbilities.register(id("candle_golem"), AllyAbilities.OnHitEffect.BURN);

        // Generic core extension hooks: golem-specific behaviour lives here, not in
        // the base combat code. Each mirrors the AllyArchetypes/on-hit pattern above.

        // Slime golem splits into two small slime golems on death. summonAlly places
        // each on a fresh free tile adjacent to the dying slime (re-checks occupancy
        // per call), so the two small slimes land on two distinct neighbours.
        AllyDeathRegistry.register(id("slime_golem"), (self, ctx) -> {
            ctx.summonAlly(id("small_slime_golem"), self, -1);
            ctx.summonAlly(id("small_slime_golem"), self, -1);
            ctx.message("§aThe slime golem splits!");
        });

        // Barrel golem barters a bonus loot roll from any kill it lands.
        AllyKillRegistry.register(id("barrel_golem"), (killer, victim) -> victim.setBonusLootRoll(true));

        // Terracotta golem is an armored taunt-tank that forces enemy targeting.
        AllyTauntRegistry.register(id("terracotta_golem"));

        // Coal golem: flint & steel ignites it into a one-shot fire-aspect attacker.
        IgnitableAllyRegistry.register(id("coal_golem"), LitCoalState.IGNITABLE);
    }

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
