package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.ai.boss.*;

import java.util.HashMap;
import java.util.Map;

public class AIRegistry {
    private static final Map<String, EnemyAI> STRATEGIES = new HashMap<>();
    /**
     * Factories for AIs that carry per-fight mutable state (all the boss AIs:
     * phase flag, turn counter, cooldowns, pending warnings, minion lists).
     * {@link #get} hands back ONE shared instance per key, which is fine for
     * the stateless mob AIs but poison for bosses — a boss killed in phase two
     * would leave the next boss of its kind starting in phase two with stale
     * cooldowns. CombatManager calls {@link #createFresh} at boss spawn and
     * pins the result on the entity via {@code setAiInstance}.
     */
    private static final Map<String, java.util.function.Supplier<EnemyAI>> BOSS_FACTORIES = new HashMap<>();
    private static final EnemyAI DEFAULT_AI = new PassiveAI();

    static {
        // === Passive mobs ===
        EnemyAI passive = new PassiveAI();
        register("minecraft:cow", passive);
        register("minecraft:pig", passive);
        register("minecraft:sheep", passive);
        register("minecraft:chicken", passive);
        register("minecraft:parrot", passive);   // farm animal behavior
        register("minecraft:panda", passive);    // basic farm animal
        register("minecraft:horse", passive);    // rare plains passive, farm animal
        register("minecraft:donkey", passive);
        register("minecraft:mule", passive);

        // === Territorial mobs ===
        register("minecraft:llama", new LlamaAI());         // farm animal, spits at range 2 when agro
        register("minecraft:polar_bear", new PolarBearAI()); // agro if player within 2, stays agro

        // === Swarm mobs ===
        register("minecraft:bee", new BeeAI()); // passive, all bees agro + poison when one hit

        // === Skittish mobs ===
        register("minecraft:rabbit", new RabbitAI()); // always flees within 2 blocks
        register("minecraft:bat", new BatAI()); // always wanders, flees when hit

        // === Aquatic mobs (water tiles only) ===
        EnemyAI fish = new CodAI();
        register("minecraft:cod", fish);
        register("minecraft:salmon", fish);
        register("minecraft:axolotl", new AxolotlAI()); // water-only, attacks hostiles in water

        // === Predators (hunt prey, agro if attacked) ===
        register("minecraft:wolf", new WolfAI());   // hunts sheep, chickens, skeletons
        register("minecraft:fox", new FoxAI());     // hunts sheep, chickens
        register("minecraft:cat", new CatAI());     // flees unless player holds fish

        // === Passive-aggressive mobs ===
        register("minecraft:goat", new GoatAI()); // farm animal, counterattacks with knockback when hit

        // === Basic melee hostiles ===
        register("minecraft:zombie", new ZombieAI());
        register("minecraft:zombie_villager", new ZombieVillagerAI());
        register("minecraft:husk", new HuskAI());        // desert zombie, extra damage
        register("minecraft:drowned", new DrownedAI());   // aquatic zombie + trident throw

        // === Ranged hostiles ===
        register("minecraft:skeleton", new SkeletonAI());
        register("minecraft:stray", new StrayAI());       // ice skeleton, kites + slows
        register("minecraft:pillager", new PillagerAI()); // crossbow, range 4, retreats

        // === Rush melee hostiles ===
        register("minecraft:vindicator", new VindicatorAI()); // 2-tile move, axe berserker
        register("minecraft:spider", new SpiderAI());          // 2x2, pounce attack
        register("minecraft:creeper", new CreeperAI());        // fuse + AoE explosion

        // === Special hostiles ===
        register("minecraft:witch", new WitchAI());           // potion throwing, keeps distance
        register("minecraft:enderman", new EndermanAI());     // teleport behind player + attack
        register("minecraft:phantom", new PhantomAI());       // swooping dive attacks
        register("minecraft:ocelot", new OcelotAI());         // fast hit-and-run
        register("minecraft:ravager", new RavagerAI());       // mounted melee, charges + stomps
        // === Spellcasters ===
        register("minecraft:evoker", new EvokerAI());    // fang attacks, summons 1 vex, keeps distance
        register("minecraft:vex", new ZombieAI());        // fast aggressive melee (summoned by evoker)

        // === Boss mobs (vanilla AI fallback when not boss-flagged) ===
        register("minecraft:warden", new WardenAI());  // Deep Dark boss, phase-shifting
        register("minecraft:guardian", new StrayAI()); // reuses stray pattern — ranged kiter

        // === Mounted mobs (rider on mount, extra speed) ===
        register("minecraft:camel", new MountedAI());  // husk/parched riding camel

        // === Nether mobs ===
        register("minecraft:zombified_piglin", new ZombifiedPiglinAI()); // neutral pack aggro, mob mentality
        register("minecraft:magma_cube", new MagmaCubeAI());             // bounce over obstacles, fire trail, split
        register("minecraft:ghast", new GhastAI());                      // long-range fireball kiter
        register("minecraft:hoglin", new HoglinAI());                    // bull rush charge + knockback
        register("minecraft:piglin", new PiglinAI());                    // melee or ranged based on weapon
        register("minecraft:piglin_brute", new VindicatorAI());          // rook-dash charger
        register("minecraft:blaze", new BlazeAI());                      // medium-range fire attacker
        register("minecraft:wither_skeleton", new WitherSkeletonAI());   // wither-strike, patrol, skull throw

        // === Trial Chamber mobs ===
        register("minecraft:breeze", new BreezeAI());             // wind charge ranged, evasive repositioner
        register("minecraft:bogged", new BoggedAI());             // poison arrow kiter
        register("minecraft:cave_spider", new CaveSpiderAI());   // fast venomous pouncer
        register("minecraft:silverfish", new SilverfishAI());     // swarmer flanker
        register("minecraft:slime", new SlimeAI());               // bouncy pounce charger

        // === Pale Garden mobs (1.21.4+ vanilla, or palegardenbackport on older) ===
        // Heart is a craftics-virtual entity, so its AI is registered on every version.
        register("craftics:creaking_heart", new CreakingHeartAI()); // stationary target, kill to destroy creaking
        //? if >=1.21.4 {
        register("minecraft:creaking", new CreakingAI());           // invulnerable guardian, linked to heart
        //?}
        // palegardenbackport:creaking AI is registered by PaleGardenBackportCompat.init().

        // === End pests ===
        register("minecraft:endermite", new EndermiteAI()); // blink-swarmer, short-range teleport + attack
        register("minecraft:end_crystal", new EndCrystalAI()); // stationary hazard, explodes when damaged

        // === Projectile entities (boss-spawned fireballs, wither skulls) ===
        register("projectile", new ProjectileAI());

        // === End mobs ===
        register("minecraft:shulker", new ShulkerAI());             // stationary turret, ranged projectiles
        register("minecraft:ender_dragon", new DragonAI());         // final boss, phase-shifting swoop/charge

        // === Boss AIs (keyed by biome, used via CombatEntity.aiOverrideKey) ===
        // Registered via factories: the shared instance backs stateless queries
        // (getGridSize), while every spawned boss gets a fresh copy through
        // createFresh() so its phase/cooldown/warning state is per-fight.
        registerBoss("boss:plains", RevenantAI::new);
        registerBoss("boss:forest", HexweaverAI::new);
        registerBoss("boss:snowy", FrostboundAI::new);
        registerBoss("boss:mountain", RockbreakerAI::new);
        registerBoss("boss:river", TidecallerAI::new);
        registerBoss("boss:desert", SandstormPharaohAI::new);
        registerBoss("boss:jungle", BroodmotherAI::new);
        registerBoss("boss:cave", HollowKingAI::new);
        registerBoss("boss:deep_dark", WardenAI::new);
        registerBoss("boss:nether_wastes", () -> new MoltenKingAI(0));
        registerBoss("boss:nether_wastes_g1", () -> new MoltenKingAI(1));
        registerBoss("boss:soul_sand_valley", WailingRevenantAI::new);
        registerBoss("boss:crimson_forest", BastionBruteAI::new);
        registerBoss("boss:warped_forest", VoidWalkerAI::new);
        registerBoss("boss:basalt_deltas", WitherBossAI::new);
        registerBoss("boss:outer_end_islands", VoidHeraldAI::new);
        registerBoss("boss:end_city", ShulkerArchitectAI::new);
        registerBoss("boss:chorus_grove", ChorusMindAI::new);
        registerBoss("boss:dragons_nest", DragonAI::new);

        // === Structures (non-acting entities) ===
        register("craftics:egg_sac", passive);
    }

    public static void register(String entityTypeId, EnemyAI ai) {
        STRATEGIES.put(entityTypeId, ai);
    }

    /** Register a stateful AI: a shared lookup instance plus a per-fight factory. */
    public static void registerBoss(String key, java.util.function.Supplier<EnemyAI> factory) {
        BOSS_FACTORIES.put(key, factory);
        STRATEGIES.put(key, factory.get());
    }

    public static EnemyAI get(String entityTypeId) {
        return STRATEGIES.getOrDefault(entityTypeId, DEFAULT_AI);
    }

    /**
     * A fresh per-fight AI instance for a stateful (boss) key, or {@code null}
     * when the key has no factory and no shared BossAI to copy. Compat/addon
     * bosses registered through plain {@link #register} are covered by the
     * reflective fallback as long as they expose a no-arg constructor.
     */
    public static EnemyAI createFresh(String key) {
        var factory = BOSS_FACTORIES.get(key);
        if (factory != null) return factory.get();
        EnemyAI shared = STRATEGIES.get(key);
        if (shared instanceof BossAI) {
            try {
                return shared.getClass().getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException ignored) {
                // fall through — caller keeps the shared instance
            }
        }
        return null;
    }
}
