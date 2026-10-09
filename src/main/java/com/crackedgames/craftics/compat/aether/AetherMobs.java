package com.crackedgames.craftics.compat.aether;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.CombatEffectContext;
import com.crackedgames.craftics.api.CombatEffectHandler;
import com.crackedgames.craftics.api.CombatResult;
import com.crackedgames.craftics.api.CrafticsAPI;
import com.crackedgames.craftics.api.CustomActionHandler;
import com.crackedgames.craftics.api.StatModifiers;
import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.CombatManager;
import com.crackedgames.craftics.combat.LootPool;
import com.crackedgames.craftics.combat.MobThemeTags;
import com.crackedgames.craftics.combat.MobTraits;
import com.crackedgames.craftics.combat.ai.AIRegistry;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.combat.ai.PassiveAI;
import com.crackedgames.craftics.combat.ai.ZombieAI;
import com.crackedgames.craftics.combat.animation.MobAttackAnimations;
import com.crackedgames.craftics.combat.animation.MobAttackAnimations.Style;
import com.crackedgames.craftics.compat.aether.ai.AechorPlantAI;
import com.crackedgames.craftics.compat.aether.ai.CockatriceAI;
import com.crackedgames.craftics.compat.aether.ai.MoaAI;
import com.crackedgames.craftics.compat.aether.ai.SentryAI;
import com.crackedgames.craftics.compat.aether.ai.SwetAI;
import com.crackedgames.craftics.compat.aether.ai.ValkyrieAI;
import com.crackedgames.craftics.compat.aether.ai.WhirlwindAI;
import com.crackedgames.craftics.compat.aether.ai.ZephyrAI;
import com.crackedgames.craftics.compat.artifacts.AccessoriesReflect;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compatibility for The Aether ({@code aether}): the creature half.
 *
 * <p>Every ordinary Aether mob gets a brain for the grid, the traits and tags that describe
 * it, and the drops it leaves. The three dungeon bosses are not here; they are encounters,
 * not mobs. The gear half is {@link AetherCompat}.
 *
 * <p>Nothing here links against the Aether. Creatures are named by entity type id, items by
 * registry id, and the handful of places that have to reach inside a live Aether entity do
 * it by reflection on the mod's own names.
 *
 * <h2>How the special hits are delivered</h2>
 * Damage always goes through the engine's own attack actions, never a custom one: only those
 * pick the right victim in a party, play the swing, and finish a player the hit kills. What a
 * hit <em>leaves behind</em> rides it instead. Poison is the jungle theme tag. Everything
 * else ({@link Riders}) is a combat-effect handler attached to every player, the same way
 * {@code AetherScanner} attaches its "Out of Depth" rule, because that is the one hook that
 * sees an enemy's hit land - after the dodge, after the armor - without a line of
 * {@code CombatManager} knowing these creatures exist.
 *
 * <h2>What an arena does not stop</h2>
 * Arena mobs are frozen with NoAI, and NoAI only silences goals. Whatever an entity does in
 * its own {@code tick} still runs. Several of these creatures do something there that
 * matters, and each is answered in the spawn hooks below or in the AI: a sentry would
 * detonate for real on contact, a swet or a whirlwind deletes itself in water, a moa lays
 * eggs, a whirlwind blows itself out after half a minute, and an aechor plant kills itself on
 * any tick it is not standing on a block from its own tag (see {@code rootAechorPlant}).
 *
 * <p>One hazard cannot be reached from a hook at all. A whirlwind throws every nearby entity
 * about from inside its own {@code aiStep}, every tick, on nobody's turn - which in an arena
 * would mean real players being flung off their tiles. The Aether exempts whatever is in its
 * {@code whirlwind_unaffected} entity tag, so this module ships one line adding the player to
 * it ({@code data/aether/tags/entity_type/whirlwind_unaffected.json}); the toss a player
 * actually suffers is the one {@code WhirlwindAI} asks the fight for.
 */
public final class AetherMobs {

    private AetherMobs() {}

    private static final String NS = AetherCompat.MOD_ID + ":";

    // ── Entity ids ──
    // Open Aether, hostile.
    public static final String ZEPHYR       = NS + "zephyr";
    public static final String COCKATRICE   = NS + "cockatrice";
    public static final String AECHOR_PLANT = NS + "aechor_plant";
    public static final String BLUE_SWET    = NS + "blue_swet";
    public static final String GOLDEN_SWET  = NS + "golden_swet";
    public static final String WHIRLWIND      = NS + "whirlwind";
    public static final String EVIL_WHIRLWIND = NS + "evil_whirlwind";
    // Dungeons.
    public static final String SENTRY       = NS + "sentry";
    public static final String MIMIC        = NS + "mimic";
    public static final String VALKYRIE     = NS + "valkyrie";
    public static final String FIRE_MINION  = NS + "fire_minion";
    // Neutral and passive.
    public static final String MOA          = NS + "moa";
    public static final String PHYG         = NS + "phyg";
    public static final String FLYING_COW   = NS + "flying_cow";
    public static final String SHEEPUFF     = NS + "sheepuff";
    public static final String AERBUNNY     = NS + "aerbunny";
    public static final String AERWHALE     = NS + "aerwhale";

    /** Every creature this module gives a brain to. */
    public static final List<String> ALL = List.of(
        ZEPHYR, COCKATRICE, AECHOR_PLANT, BLUE_SWET, GOLDEN_SWET,
        WHIRLWIND, EVIL_WHIRLWIND,
        SENTRY, MIMIC, VALKYRIE, FIRE_MINION,
        MOA, PHYG, FLYING_COW, SHEEPUFF, AERBUNNY, AERWHALE);

    /** The custom action a swet standing in water resolves. See {@link SwetAI}. */
    public static final String SWET_DISSOLVE = "craftics:aether_swet_dissolve";

    /** Registry path of the accessory that makes swets leave its wearer alone. */
    public static final String SWET_CAPE = "swet_cape";

    private static final String SCANNER_ID = "craftics:aether_mobs";

    private static boolean registered = false;

    /**
     * Register every creature. The brains, tags, traits and animations go in whether or not
     * the Aether is installed, so a datapack that names one of these ids resolves a real AI
     * rather than the passive fallback. The hooks that only mean anything next to a live
     * Aether entity wait for the mod.
     */
    public static void init() {
        if (registered) return;
        registered = true;

        brains().forEach(AIRegistry::register);
        CrafticsAPI.registerCustomAction(SWET_DISSOLVE, AetherMobs::dissolveSwet);

        // On-hit poison, which also makes both Toxic in the inspect panel.
        MobThemeTags.addJungleMob(COCKATRICE);
        MobThemeTags.addJungleMob(AECHOR_PLANT);

        // Each of these is a claim about what the AI does, so each names where.
        MobTraits.declare(ZEPHYR, MobTraits.FORCEFUL);                       // ZephyrAI gust
        // A flag on the live plant (rootAechorPlant); restated for the bestiary, which only
        // knows the type.
        MobTraits.declare(AECHOR_PLANT, MobTraits.IMMOVABLE);
        MobTraits.declare(WHIRLWIND, MobTraits.FORCEFUL);                    // WhirlwindAI toss
        MobTraits.declare(EVIL_WHIRLWIND, MobTraits.FORCEFUL);               // WhirlwindAI hit
        MobTraits.declare(SENTRY, MobTraits.MARTYR, MobTraits.FORCEFUL);     // SentryAI Explode
        MobTraits.declare(VALKYRIE, MobTraits.ETHEREAL, MobTraits.BERZERKER); // ValkyrieAI blink, lunge

        // A zephyr's own turn never swings (its gust is not an attack); this is for anything
        // else that makes one attack. A rooted plant should channel, not lean back to aim.
        MobAttackAnimations.register(ZEPHYR, Style.CAST);
        MobAttackAnimations.register(WHIRLWIND, Style.CAST);
        MobAttackAnimations.register(EVIL_WHIRLWIND, Style.CAST);
        MobAttackAnimations.register(AECHOR_PLANT, Style.CAST);
        MobAttackAnimations.register(COCKATRICE, Style.JAB);
        MobAttackAnimations.register(MOA, Style.JAB);
        MobAttackAnimations.register(BLUE_SWET, Style.BOUNCE);
        MobAttackAnimations.register(GOLDEN_SWET, Style.BOUNCE);
        MobAttackAnimations.register(SENTRY, Style.BOUNCE);
        MobAttackAnimations.register(MIMIC, Style.SLAM);
        MobAttackAnimations.register(FIRE_MINION, Style.SLAM);
        MobAttackAnimations.register(VALKYRIE, Style.DASH);

        if (!FabricLoader.getInstance().isModLoaded(AetherCompat.MOD_ID)) {
            CrafticsMod.LOGGER.debug("[Craftics × Aether] mod not loaded - mob AI registered for any future use");
            return;
        }
        // Held back until the mod is present on purpose. The spawn-hook registry reports
        // every deliberately keyed combatant it has no hook for once it holds any hook at
        // all, so registering these everywhere would make every install log its bosses.
        registerSpawnHooks();
        CrafticsAPI.registerEquipmentScanner(SCANNER_ID, player -> {
            StatModifiers mods = new StatModifiers();
            mods.addCombatEffect("Aether Creatures", RIDERS);
            return mods;
        });
        CrafticsMod.LOGGER.info("[Craftics × Aether] mobs registered");
    }

    /**
     * Which brain each creature runs. Stateless, all of them: anything a creature has to
     * remember between turns is kept in its own AI memory, so one shared instance is safe on
     * every spawn path, including the ones that never ask for a fresh copy.
     */
    static Map<String, EnemyAI> brains() {
        Map<String, EnemyAI> brains = new LinkedHashMap<>();
        brains.put(ZEPHYR, new ZephyrAI());             // flying, shoves from range, no damage
        brains.put(COCKATRICE, new CockatriceAI());     // poison needle, shoots then backs off
        brains.put(AECHOR_PLANT, new AechorPlantAI());  // rooted turret
        EnemyAI swet = new SwetAI();                    // slime hop, ignores Swet Cape wearers
        brains.put(BLUE_SWET, swet);
        brains.put(GOLDEN_SWET, swet);
        brains.put(WHIRLWIND, new WhirlwindAI(false));      // wanders, tosses, hunts nobody
        brains.put(EVIL_WHIRLWIND, new WhirlwindAI(true));  // hunts, and its toss is a hit
        brains.put(SENTRY, new SentryAI());             // dormant, then a walking bomb
        brains.put(VALKYRIE, new ValkyrieAI());         // neutral; lunges and blinks once hit
        brains.put(MOA, new MoaAI());                   // neutral; plain melee once hit
        // A chest with teeth and a thing made of fire both just walk at you and hit hard.
        // What makes them different is their stats, and for the minion the burn its hit leaves.
        EnemyAI melee = new ZombieAI();
        brains.put(MIMIC, melee);
        brains.put(FIRE_MINION, melee);
        EnemyAI passive = new PassiveAI();
        for (String id : List.of(PHYG, FLYING_COW, SHEEPUFF, AERBUNNY, AERWHALE)) {
            brains.put(id, passive);
        }
        return brains;
    }

    public static boolean isSwet(String entityTypeId) {
        return BLUE_SWET.equals(entityTypeId) || GOLDEN_SWET.equals(entityTypeId);
    }

    // ================================================================
    // Drops
    // ================================================================

    /** One line of a creature's drop table: an item id and its weight in the roll. */
    public record Drop(String itemId, int weight) {}

    /**
     * What a creature drops, as plain ids: the Aether's own loot, mob for mob. Null for
     * anything this module does not own; an empty list for a creature that is ours and
     * leaves nothing (the Aether gives fire minions and aerwhales no drops either).
     */
    static List<Drop> dropTable(String entityTypeId) {
        if (entityTypeId == null) return null;
        return switch (entityTypeId) {
            case ZEPHYR -> List.of(new Drop("aether:cold_aercloud", 6));
            case COCKATRICE -> List.of(new Drop("minecraft:feather", 6));
            case AECHOR_PLANT -> List.of(new Drop("aether:aechor_petal", 6));
            case BLUE_SWET -> List.of(new Drop("aether:swet_ball", 5), new Drop("aether:blue_aercloud", 2));
            case GOLDEN_SWET -> List.of(new Drop("minecraft:glowstone", 5));
            // What a whirlwind has picked up on its way: the Aether's own junk table, trimmed.
            case WHIRLWIND -> List.of(new Drop("minecraft:flint", 4), new Drop("minecraft:stick", 4),
                new Drop("minecraft:coal", 3), new Drop("minecraft:gold_ingot", 1));
            case EVIL_WHIRLWIND -> List.of(new Drop("minecraft:gunpowder", 4), new Drop("minecraft:flint", 3),
                new Drop("minecraft:coal", 3), new Drop("minecraft:gold_ingot", 1));
            // Only a sentry that was killed drops anything. One that got to explode is gone.
            case SENTRY -> List.of(new Drop("aether:carved_stone", 4), new Drop("aether:sentry_stone", 1));
            case MIMIC -> List.of(new Drop("minecraft:chest", 4), new Drop("aether:zanite_gemstone", 3));
            // The medals are what the Valkyrie Queen asks for, so this is the only source.
            case VALKYRIE -> List.of(new Drop("aether:victory_medal", 1));
            case MOA -> List.of(new Drop("minecraft:feather", 6));
            case PHYG -> List.of(new Drop("minecraft:porkchop", 6), new Drop("minecraft:feather", 2));
            case FLYING_COW -> List.of(new Drop("minecraft:beef", 5), new Drop("minecraft:leather", 4));
            case SHEEPUFF -> List.of(new Drop("minecraft:white_wool", 5), new Drop("minecraft:mutton", 4));
            case AERBUNNY -> List.of(new Drop("minecraft:string", 5));
            case FIRE_MINION, AERWHALE -> List.of();
            default -> null;
        };
    }

    /**
     * Per-kill drops for an Aether creature, or {@code null} for anything this module does
     * not own. Meant for the default branch of {@code CombatManager.mobLootPool}: the ids
     * cannot be {@code case} labels there because they only exist when the mod does.
     */
    public static LootPool mobDrops(String entityTypeId) {
        List<Drop> table = dropTable(entityTypeId);
        if (table == null) return null;
        LootPool pool = new LootPool();
        for (Drop drop : table) addItem(pool, drop.itemId(), drop.weight());
        return pool;
    }

    /**
     * How many items one kill of this creature gives, when that is not left to the roll.
     * Zero for anything that rolls as usual. A valkyrie gives one Victory Medal and no
     * more: the Valkyrie Queen counts them, so neither luck nor a doubled drop adds to it.
     */
    public static int fixedDropCount(String entityTypeId) {
        return VALKYRIE.equals(entityTypeId) ? 1 : 0;
    }

    /** Add an item to a loot pool by id, skipping ids that are not registered. */
    private static void addItem(LootPool pool, String itemId, int weight) {
        Identifier id = Identifier.tryParse(itemId);
        if (id != null && Registries.ITEM.containsId(id)) pool.add(Registries.ITEM.get(id), weight);
    }

    // ================================================================
    // Swets and the Swet Cape
    // ================================================================

    /** Whether this player has a Swet Cape in any Accessories slot. */
    public static boolean wearsSwetCape(ServerPlayerEntity player) {
        if (player == null || !AetherCompat.isLoaded()) return false;
        boolean[] worn = {false};
        AccessoriesReflect.forEachEquipped(player, stack -> {
            if (SWET_CAPE.equals(AetherCompat.pathOf(stack.getItem()))) worn[0] = true;
        });
        return worn[0];
    }

    /**
     * The party member standing on {@code tile}, or null: the tile holds a pet, or nobody, or
     * there is no live world to ask.
     */
    public static ServerPlayerEntity playerAt(CombatEntity self, GridArena arena, GridPos tile) {
        MobEntity mob = self != null ? self.getMobEntity() : null;
        if (mob == null || arena == null || tile == null) return null;
        if (!(mob.getWorld() instanceof ServerWorld world)) return null;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()) continue;
            if (tile.equals(arena.gridPosAtColumn(player.getBlockX(), player.getBlockZ()))) return player;
        }
        return null;
    }

    /**
     * Where a swet should go. That is {@code target}, unless the player standing there wears
     * a Swet Cape, in which case it is the nearest party member who does not. Null when
     * everyone does, and the swet has nobody to want.
     *
     * <p>A pet is always fair game: the cape pacifies swets toward its wearer, not toward
     * whatever its wearer brought along.
     */
    public static GridPos swetPrey(CombatEntity self, GridArena arena, GridPos target) {
        if (target == null || !wearsSwetCape(playerAt(self, arena, target))) return target;
        GridPos nearest = null;
        int nearestDist = Integer.MAX_VALUE;
        for (GridPos other : arena.getAllPlayerGridPositions()) {
            if (other.equals(target) || wearsSwetCape(playerAt(self, arena, other))) continue;
            int dist = self.minDistanceTo(other);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = other;
            }
        }
        return nearest;
    }

    /**
     * A swet that is standing in water on its turn. The entity is already dissolving on its
     * own clock; this is the grid catching up, so the kill is credited and looted like any
     * other and the tile is not left holding a creature the world has deleted.
     */
    private static void dissolveSwet(CustomActionHandler.Context ctx) {
        CombatEntity swet = ctx.self();
        BlockPos bp = ctx.arena().gridToBlockPos(swet.getGridPos());
        ctx.world().spawnParticles(ParticleTypes.SPLASH,
            bp.getX() + 0.5, bp.getY() + 0.4, bp.getZ() + 0.5, 30, 0.35, 0.3, 0.35, 0.1);
        ctx.world().playSound(null, bp, SoundEvents.ENTITY_GENERIC_SPLASH,
            SoundCategory.HOSTILE, 0.8f, 1.3f);
        ctx.message("§b" + swet.getDisplayName() + " dissolves in the water!");
        // The figure the engine itself uses when it means "certainly dead".
        ctx.damage(swet, 9999);
    }

    // ================================================================
    // What a hit leaves behind
    // ================================================================

    /** A status effect one of these creatures leaves on the player its hit lands on. */
    record Rider(CombatEffects.EffectType effect, int turns, String message) {}

    /** A swet does not hurt much. It swallows you and lifts you, and that is the problem. */
    static final Rider SWALLOWED = new Rider(CombatEffects.EffectType.LEVITATION, 1,
        "§b✦ The swet swallows you and lifts you off the ground! (Levitation, 1 turn)");
    static final Rider SET_ALIGHT = new Rider(CombatEffects.EffectType.BURNING, 2,
        "§6✦ The fire minion's touch sets you alight! (Burning, 2 turns)");

    static Rider riderFor(String entityTypeId) {
        if (isSwet(entityTypeId)) return SWALLOWED;
        if (FIRE_MINION.equals(entityTypeId)) return SET_ALIGHT;
        return null;
    }

    /** Stateless, so one instance serves every player and every rebuild of the scan. */
    private static final Riders RIDERS = new Riders();

    /**
     * Applies {@link #riderFor} to whoever one of these creatures has just hit. Called by the
     * engine once a hit has got past the dodge roll and the armor, so a blow that missed or
     * was blocked leaves nothing.
     */
    static final class Riders implements CombatEffectHandler {
        @Override
        public CombatResult onTakeDamage(CombatEffectContext ctx, CombatEntity attacker, int damage) {
            Rider rider = attacker != null && damage > 0 ? riderFor(attacker.getEntityTypeId()) : null;
            ServerPlayerEntity victim = ctx.getPlayer();
            if (rider == null || victim == null) return CombatResult.unchanged(damage);
            // The rule every other source of fire in a fight follows.
            if (rider.effect() == CombatEffects.EffectType.BURNING
                    && ctx.getPlayerEffects() != null && ctx.getPlayerEffects().hasFireResistance()) {
                return CombatResult.unchanged(damage);
            }
            if (!afflict(ctx, victim, rider)) return CombatResult.unchanged(damage);
            return CombatResult.modify(damage, rider.message());
        }

        /** False when something the victim wears turned the effect away. */
        private static boolean afflict(CombatEffectContext ctx, ServerPlayerEntity victim, Rider rider) {
            CombatManager cm = CombatManager.getActiveCombat(victim.getUuid());
            // While a hit resolves, the manager's current player IS the victim, and that is
            // who addEffectHooked writes to - through the immunity hooks, so Phoenix armor
            // still refuses the burn. Should that ever not hold, the effect goes straight
            // onto the victim rather than onto somebody else.
            if (cm != null && cm.isActive() && cm.getPlayer() == victim) {
                return cm.addEffectHooked(rider.effect(), rider.turns(), 0);
            }
            if (ctx.getPlayerEffects() == null) return false;
            ctx.getPlayerEffects().addEffect(rider.effect(), rider.turns(), 0);
            return true;
        }
    }

    // ================================================================
    // Spawn hooks: what NoAI leaves running
    // ================================================================

    /** Hitbox widths the Aether gives its two clouds, in blocks. Both are far wider than a tile. */
    private static final double ZEPHYR_WIDTH = 4.5;
    private static final double AERWHALE_WIDTH = 3.0;

    private static void registerSpawnHooks() {
        CrafticsAPI.registerSpawnCustomizer(AECHOR_PLANT, AetherMobs::rootAechorPlant);
        CrafticsAPI.registerSpawnCustomizer(SENTRY, (world, mob, entity) -> pinSentryAsleep(mob));
        CrafticsAPI.registerSpawnCustomizer(WHIRLWIND, (world, mob, entity) -> keepBlowing(mob));
        CrafticsAPI.registerSpawnCustomizer(EVIL_WHIRLWIND, (world, mob, entity) -> keepBlowing(mob));
        CrafticsAPI.registerSpawnCustomizer(ZEPHYR,
            (world, mob, entity) -> fitToFootprint(mob, entity, ZEPHYR_WIDTH));
        CrafticsAPI.registerSpawnCustomizer(AERWHALE,
            (world, mob, entity) -> fitToFootprint(mob, entity, AERWHALE_WIDTH));
        CrafticsAPI.registerSpawnCustomizer(COCKATRICE, (world, mob, entity) -> quietWings(mob));
        CrafticsAPI.registerSpawnCustomizer(MOA, (world, mob, entity) -> {
            quietWings(mob);
            stopLaying(mob);
        });
    }

    /**
     * Nothing moves an aechor plant: not a shove, not a pull, not a launch.
     *
     * <p>The check after it is a warning and nothing more, because there is nothing a spawn
     * hook can do about what it finds. The Aether kills a plant on any tick it is not
     * standing on a block from its own tag, Craftics respawns a mob it finds dead, and the
     * two will do that to each other forever, dropping the plant's loot each time. The fix
     * has to come from the arena: aether grass under the plant, or the tag widened.
     */
    private static void rootAechorPlant(ServerWorld world, MobEntity mob, CombatEntity entity) {
        entity.setImmovable(true);

        BlockPos under = mob.getBlockPos().down();
        BlockState floor = world.getBlockState(under);
        TagKey<Block> soil = TagKey.of(RegistryKeys.BLOCK,
            Identifier.of(AetherCompat.MOD_ID, "aechor_plant_spawnable_on"));
        if (floor.isIn(soil)) return;
        // Give it a block of its own soil to stand on. The plant is rooted for the whole
        // fight, so this one block is all it will ever need, and it reads as what it is: the
        // patch the thing grows out of. This hook runs again when the arena replaces a mob
        // that died outside the fight's control, so a plant that loses its footing gets it back.
        Block grass = AetherCompat.lookupBlock("aether_grass_block");
        if (grass != null) {
            world.setBlockState(under, grass.getDefaultState());
            return;
        }
        String block = Registries.BLOCK.getId(floor.getBlock()).toString();
        if (REPORTED.add("aechor floor " + block)) {
            CrafticsMod.LOGGER.warn("[Craftics × Aether] An aechor plant was placed on {}. The Aether "
                + "kills one every tick it is not on a block in #aether:aechor_plant_spawnable_on, "
                + "and Craftics will respawn it every tick in turn. Stand it on aether grass, or "
                + "add this block to that tag.", block);
        }
    }

    /**
     * Keep the real sentry asleep for good.
     *
     * <p>A sentry counts the ticks a player has been near it in {@code timeSpotted} and wakes
     * at 24. An awake sentry that is touched hits for real, sets off a real explosion that
     * breaks blocks, and removes itself - none of which the grid would know about. The arena's
     * damage guard already refuses that first hit for anyone in the fight, which stops the
     * rest; this closes it for everyone else too. Negative infinity plus one is still
     * negative infinity, so the count never arrives. {@link SentryAI} decides when the sentry
     * is awake as far as the fight is concerned.
     */
    private static void pinSentryAsleep(MobEntity mob) {
        if (!writeField(mob, "timeSpotted", Float.NEGATIVE_INFINITY)) {
            reportOnce("could not keep a sentry asleep (no timeSpotted field)");
        }
    }

    /**
     * A whirlwind counts down from about half a minute and removes itself at zero. Pushing
     * the count out of reach is all it takes to make one stay for a fight.
     */
    private static void keepBlowing(MobEntity mob) {
        try {
            mob.getClass().getMethod("setLifeLeft", int.class).invoke(mob, Integer.MAX_VALUE);
        } catch (ReflectiveOperationException | RuntimeException e) {
            reportOnce("could not keep a whirlwind from blowing out (no setLifeLeft)");
        }
    }

    /**
     * Cockatrices and moas flap whenever they are off the ground, and an arena mob never
     * touches it: a wingbeat every three quarters of a second, from each of them, for the
     * whole fight. Pushing the cooldown out of reach is all it takes to stop it.
     */
    private static void quietWings(MobEntity mob) {
        try {
            mob.getClass().getMethod("setFlapCooldown", int.class).invoke(mob, Integer.MAX_VALUE);
        } catch (ReflectiveOperationException | RuntimeException e) {
            reportOnce("could not quiet a " + mob.getType() + " (no setFlapCooldown)");
        }
    }

    /** A moa lays an egg every five to ten minutes, which a long fight would hand out for free. */
    private static void stopLaying(MobEntity mob) {
        if (!writeField(mob, "eggTime", Integer.MAX_VALUE)) {
            reportOnce("could not stop a moa laying (no eggTime field)");
        }
    }

    /**
     * Shrink a cloud until it sits inside the tiles it was given. A zephyr is four and a half
     * blocks across; left alone it would hang over every neighbour of its tile and take the
     * clicks meant for them.
     */
    private static void fitToFootprint(MobEntity mob, CombatEntity entity, double nativeWidth) {
        //? if <=1.21.1 {
        var scale = mob.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_SCALE);
        //?} else {
        /*var scale = mob.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.SCALE);
        *///?}
        if (scale != null) {
            scale.setBaseValue(fitScale(nativeWidth, Math.min(entity.getSizeX(), entity.getSizeZ())));
        }
    }

    /**
     * The scale that makes a body {@code nativeWidth} blocks wide fill nine tenths of
     * {@code tiles} tiles. Never above 1: this only ever shrinks.
     */
    static double fitScale(double nativeWidth, int tiles) {
        if (nativeWidth <= 0 || tiles <= 0) return 1.0;
        return Math.min(1.0, 0.9 * tiles / nativeWidth);
    }

    /** Write a field of a live entity by name, wherever in its class chain it is declared. */
    private static boolean writeField(Object target, String name, Object value) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return true;
            } catch (NoSuchFieldException e) {
                // Declared further up the chain, or not at all.
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }
        return false;
    }

    /** Things already said once. A spawn hook runs per mob, and none of these is worth repeating. */
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private static void reportOnce(String what) {
        if (REPORTED.add(what)) {
            CrafticsMod.LOGGER.warn("[Craftics × Aether] {}. The Aether may have renamed it.", what);
        }
    }
}
