package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.ai.boss.*;

import java.util.HashMap;
import java.util.Map;

public class AIRegistry {
    private static final Map<String, EnemyAI> STRATEGIES = new HashMap<>();
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

        // === Wander + flee mobs ===
        register("minecraft:bat", new BatAI()); // always wanders, flees when hit

        // === Aquatic mobs ===
        register("minecraft:axolotl", new AxolotlAI()); // water-only, attacks hostiles in water

        // === Territorial mobs ===
        register("minecraft:llama", new LlamaAI());         // farm animal, spits at range 2 when agro
        register("minecraft:polar_bear", new PolarBearAI()); // agro if player within 2, stays agro

        // === Swarm mobs ===
        register("minecraft:bee", new BeeAI()); // passive, all bees agro + poison when one hit

        // === Skittish mobs ===
        register("minecraft:rabbit", new RabbitAI()); // always flees within 2 blocks

        // === Aquatic mobs (water tiles only) ===
        EnemyAI fish = new CodAI();
        register("minecraft:cod", fish);
        register("minecraft:salmon", fish);

        // === Predators (hunt prey, agro if attacked) ===
        register("minecraft:wolf", new WolfAI());   // hunts sheep, chickens, skeletons
        register("minecraft:fox", new FoxAI());     // hunts sheep, chickens
        register("minecraft:cat", new CatAI());     // flees unless player holds fish

        // === Passive-aggressive mobs ===
        register("minecraft:goat", new GoatAI()); // farm animal, counterattacks with knockback when hit

        // === Basic melee hostiles ===
        register("minecraft:zombie", new ZombieAI());
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
        register("minecraft:piglin", new PillagerAI());                  // crossbow ranged, retreats
        register("minecraft:piglin_brute", new VindicatorAI());          // rook-dash charger
        register("minecraft:blaze", new BlazeAI());                      // medium-range fire attacker
        register("minecraft:wither_skeleton", new WitherSkeletonAI());   // wither-strike, patrol, skull throw

        // === Trial Chamber mobs ===
        register("minecraft:breeze", new BreezeAI());             // wind charge ranged, evasive repositioner
        register("minecraft:bogged", new BoggedAI());             // poison arrow kiter
        register("minecraft:cave_spider", new CaveSpiderAI());   // fast venomous pouncer
        register("minecraft:silverfish", new SilverfishAI());     // swarmer flanker
        register("minecraft:slime", new SlimeAI());               // bouncy pounce charger

        // === End mobs ===
        register("minecraft:shulker", new ShulkerAI());             // stationary turret, ranged projectiles
        register("minecraft:ender_dragon", new DragonAI());         // final boss, phase-shifting swoop/charge

        // === Boss AIs (keyed by biome, used via CombatEntity.aiOverrideKey) ===
        register("boss:plains", new RevenantAI());
        register("boss:forest", new HexweaverAI());
        register("boss:snowy", new FrostboundAI());
        register("boss:mountain", new RockbreakerAI());
        register("boss:river", new TidecallerAI());
        register("boss:desert", new SandstormPharaohAI());
        register("boss:jungle", new BroodmotherAI());
        register("boss:cave", new HollowKingAI());
        register("boss:deep_dark", new WardenAI());
        register("boss:nether_wastes", new MoltenKingAI());
        register("boss:soul_sand_valley", new WailingRevenantAI());
        register("boss:crimson_forest", new CrimsonRavagerAI());
        register("boss:warped_forest", new VoidWalkerAI());
        register("boss:basalt_deltas", new WitherBossAI());
        register("boss:outer_end_islands", new VoidHeraldAI());
        register("boss:end_city", new ShulkerArchitectAI());
        register("boss:chorus_grove", new ChorusMindAI());
        register("boss:dragons_nest", new DragonAI());
    }

    public static void register(String entityTypeId, EnemyAI ai) {
        STRATEGIES.put(entityTypeId, ai);
    }

    public static EnemyAI get(String entityTypeId) {
        return STRATEGIES.getOrDefault(entityTypeId, DEFAULT_AI);
    }
}
