package com.crackedgames.craftics.compat.deeperanddarker;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.Abilities;
import com.crackedgames.craftics.api.WeaponAbilityHandler;
import com.crackedgames.craftics.api.registry.ArmorSetEntry;
import com.crackedgames.craftics.api.registry.ArmorSetRegistry;
import com.crackedgames.craftics.api.registry.WeaponEntry;
import com.crackedgames.craftics.api.registry.WeaponRegistry;
import com.crackedgames.craftics.combat.DamageType;
import com.crackedgames.craftics.combat.MobThemeTags;
import com.crackedgames.craftics.combat.ai.AIRegistry;
import com.crackedgames.craftics.combat.ai.SilverfishAI;
import com.crackedgames.craftics.combat.ai.SlimeAI;
import com.crackedgames.craftics.combat.ai.ZombieAI;
import com.crackedgames.craftics.combat.ai.deeperanddarker.AnglerFishAI;
import com.crackedgames.craftics.combat.ai.deeperanddarker.SculkCentipedeAI;
import com.crackedgames.craftics.combat.ai.deeperanddarker.ShriekWormAI;
import com.crackedgames.craftics.combat.ai.deeperanddarker.StalkerAI;
import com.crackedgames.craftics.combat.CombatManager;
import com.crackedgames.craftics.combat.miniboss.MinibossContext;
import com.crackedgames.craftics.compat.BiomeCompatHelper;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;
import com.crackedgames.craftics.level.MobPoolEntry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Compatibility module for the <b>Deeper and Darker</b> mod (1.21.1).
 *
 * <p>When the mod is installed, the Deep Dark biome is fully overhauled: the
 * hostile pool is replaced wholesale with the mod's sculk creatures, a level-4
 * Stalker miniboss replaces the vanilla Swarm (see {@code StalkerMinibossMechanic}
 * registration in {@code CrafticsMod}), and the Blooming Caverns bloom/geyser
 * blocks become hazard tiles. Without the mod, nothing here fires - every
 * mutation is guarded by {@link BiomeCompatHelper#entityExists} / {@code isLoaded}.
 *
 * <p>Mirrors the {@code CreeperOverhaulCompat} shape: {@link #init} always
 * registers AI + theme tags (so datapacks referencing these ids never crash),
 * then sets {@code loaded} only if the mod is present; {@link #applyBiomeOverrides}
 * does the biome mutation and is called from both of CrafticsMod's
 * SERVER_STARTED / END_DATA_PACK_RELOAD hooks, AFTER the other compat modules so
 * this full replacement supersedes their per-mob swaps.
 */
public final class DeeperAndDarkerCompat {

    public static final String MOD_ID = "deeperdarker";
    private static final String NS = MOD_ID + ":";

    // Entity ids. Guarded by entityExists() everywhere, so a wrong/renamed id is
    // a safe no-op rather than a crash.
    public static final String STALKER        = NS + "stalker";
    public static final String SCULK_CENTIPEDE = NS + "sculk_centipede";
    public static final String SCULK_LEECH    = NS + "sculk_leech";
    public static final String SCULK_SNAPPER  = NS + "sculk_snapper";
    public static final String SHATTERED      = NS + "shattered";
    public static final String SHRIEK_WORM    = NS + "shriek_worm";
    public static final String ANGLER         = NS + "angler_fish";
    public static final String SLUDGE         = NS + "sludge";

    private static boolean loaded = false;
    private static boolean aiRegistered = false;
    private static boolean gearRegistered = false;

    private DeeperAndDarkerCompat() {}

    /**
     * Register AI + on-hit theme tags for every D&D creature. Always runs so any
     * datapack/custom biome referencing these ids resolves a real AI; the mod
     * gate only controls the biome overhaul.
     */
    public static void init() {
        if (aiRegistered) return;
        aiRegistered = true;

        // Custom behaviors.
        AIRegistry.register(STALKER, new StalkerAI());               // 2-mode invisible miniboss
        AIRegistry.register(ANGLER, new AnglerFishAI());             // water-locked ambusher
        AIRegistry.register(SCULK_CENTIPEDE, new SculkCentipedeAI()); // speed-2 hit-and-run
        AIRegistry.register(SHRIEK_WORM, new ShriekWormAI());        // immobile range-3 turret

        // Reused archetypes.
        AIRegistry.register(SHATTERED, new ZombieAI());      // basic melee, common
        AIRegistry.register(SCULK_SNAPPER, new ZombieAI());  // slow melee (speed via stats) + root tag
        AIRegistry.register(SCULK_LEECH, new SilverfishAI()); // fast swarmer + lifesteal tag
        AIRegistry.register(SLUDGE, new SlimeAI());          // beeline slime + soaked tag

        // On-hit effects (data-driven via MobThemeTags, applied in damagePlayer).
        MobThemeTags.addJungleMob(SCULK_CENTIPEDE);  // Poison on bite
        MobThemeTags.addWaterMob(SLUDGE);            // Soaked on hit
        MobThemeTags.addRootMob(SCULK_SNAPPER);      // locks the player in place
        MobThemeTags.addRootMob(SHRIEK_WORM);        // heavy hit + lock
        MobThemeTags.addLifestealMob(SCULK_LEECH);   // drains life on hit

        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            CrafticsMod.LOGGER.debug(
                "[Craftics × Deeper and Darker] mod not loaded - AI/tags registered for any future use");
            return;
        }
        loaded = true;
        CrafticsMod.LOGGER.info("[Craftics × Deeper and Darker] enabled - Deep Dark overhaul wired");
    }

    public static boolean isLoaded() {
        return loaded;
    }

    // ================================================================
    // Gear: Warden + Resonarium tiers
    // ================================================================

    /**
     * Late-phase gear registration, mirroring {@code CopperAgeCompat.registerDeferred}.
     * {@link WeaponRegistry} needs the real {@link Item} instances and Fabric does not
     * guarantee Deeper and Darker's main entrypoint has run by the time ours does, so
     * this runs on SERVER_STARTING / CLIENT_STARTED instead of in {@link #init}.
     * Idempotent: the first successful run flips the guard.
     *
     * <p>Until this runs, Warden and Resonarium gear resolve to the registry's
     * bare-fist DEFAULT entry - i.e. a Warden sword swings for fist damage.
     */
    public static void registerDeferred() {
        // Called from the tooltip render path as a fallback, so both early-exits
        // must stay silent or the log floods.
        if (gearRegistered || !loaded) return;
        boolean any = registerWeapons() | registerArmorSets();
        if (any) {
            gearRegistered = true;
            CrafticsMod.LOGGER.info(
                "[Craftics × Deeper and Darker] gear registered - Warden tier (Echo set) + Resonarium tier");
        }
    }

    /** True iff the deferred gear registration has completed at least once. */
    public static boolean isGearRegistered() {
        return gearRegistered;
    }

    /**
     * Register both tiers' tools in their natural affinity lanes (sword Slashing,
     * axe Cleaving, shovel Pet, hoe Special), with damage read through the live
     * config on every attack so retuning vanilla retunes these with it.
     *
     * <p>Tiers come from the mod's own smithing recipes, not guesswork:
     * <ul>
     *   <li><b>Resonarium</b> = {@code iron_sword + resonarium_plate}, so it sits
     *       between iron and diamond.</li>
     *   <li><b>Warden</b> = {@code netherite_sword + reinforced_echo_shard +
     *       warden_upgrade_smithing_template}, the mod's endgame tier, so it sits
     *       ABOVE netherite by one netherite-over-diamond step.</li>
     * </ul>
     * Pickaxes are skipped, matching vanilla pickaxes and the Copper Age precedent.
     */
    private static boolean registerWeapons() {
        boolean any = false;

        // Warden beats netherite by the same margin netherite beats diamond, so the
        // gap stays proportional if those two are retuned.
        java.util.function.IntSupplier wardenSword = () ->
            CrafticsMod.CONFIG.dmgNetheriteSword()
                + step(CrafticsMod.CONFIG.dmgDiamondSword(), CrafticsMod.CONFIG.dmgNetheriteSword());
        java.util.function.IntSupplier wardenAxe = () ->
            CrafticsMod.CONFIG.dmgNetheriteAxe()
                + step(CrafticsMod.CONFIG.dmgDiamondAxe(), CrafticsMod.CONFIG.dmgNetheriteAxe());
        java.util.function.IntSupplier resoSword = () ->
            midpoint(CrafticsMod.CONFIG.dmgIronSword(), CrafticsMod.CONFIG.dmgDiamondSword());
        java.util.function.IntSupplier resoAxe = () ->
            midpoint(CrafticsMod.CONFIG.dmgIronAxe(), CrafticsMod.CONFIG.dmgDiamondAxe());

        // Warden gear cannot inherit the netherite sword's Execute (that is keyed to
        // the vanilla item by identity), so its edge is a much stronger sweep and a
        // shatter that scales harder with Cleaving.
        any |= registerWeapon("warden_sword",  DamageType.SLASHING, wardenSword, 1, 1, Abilities.sweepAdjacent(0.25, 0.06));
        any |= registerWeapon("warden_axe",    DamageType.CLEAVING, wardenAxe,   2, 1, Abilities.armorIgnore(0.15, 0.04));
        any |= registerWeapon("warden_shovel", DamageType.PET,      () -> 7,     1, 1, null);
        any |= registerWeapon("warden_hoe",    DamageType.SPECIAL,  () -> 4,     1, 1, null);

        any |= registerWeapon("resonarium_sword",  DamageType.SLASHING, resoSword, 1, 1, Abilities.sweepAdjacent(0.15, 0.05));
        any |= registerWeapon("resonarium_axe",    DamageType.CLEAVING, resoAxe,   2, 1, Abilities.armorIgnore(0.08, 0.035));
        // Shovels/hoes use fixed low damage in VanillaWeapons (no config entry):
        // shovel iron=4/diamond=5/netherite=6, hoe iron=2/diamond=3.
        any |= registerWeapon("resonarium_shovel", DamageType.PET,     () -> 5,   1, 1, null);
        any |= registerWeapon("resonarium_hoe",    DamageType.SPECIAL, () -> 3,   1, 1, null);
        // *_pickaxe intentionally skipped: vanilla pickaxes aren't combat weapons.

        // The Sonorous Staff is the mod's only real weapon artifact: a ranged Special
        // attack that fires a line, so it lands in the Special lane the Resonarium set
        // boosts. 2 AP because it hits a whole row.
        any |= registerRangedWeapon("sonorous_staff", DamageType.SPECIAL,
            () -> SONOROUS_STAFF_DAMAGE, 2, SONOROUS_STAFF_RANGE, sonicBoom());
        return any;
    }

    /** Base damage of the Sonorous Staff's boom, before falloff. */
    public static final int SONOROUS_STAFF_DAMAGE = 14;
    /** Tiles the staff can target from. */
    public static final int SONOROUS_STAFF_RANGE = 5;

    /** Midpoint of two damage values, rounded up so the tier leans toward the higher one. */
    private static int midpoint(int lower, int upper) {
        return (lower + upper + 1) / 2;
    }

    /** One tier step, floored at 1 so a flattened config still leaves Warden ahead. */
    private static int step(int lower, int upper) {
        return Math.max(1, upper - lower);
    }

    private static boolean registerWeapon(String path, DamageType type,
                                          java.util.function.IntSupplier power,
                                          int apCost, int range, WeaponAbilityHandler ability) {
        return register(path, type, power, apCost, range, false, ability);
    }

    private static boolean registerRangedWeapon(String path, DamageType type,
                                                java.util.function.IntSupplier power,
                                                int apCost, int range, WeaponAbilityHandler ability) {
        return register(path, type, power, apCost, range, true, ability);
    }

    private static boolean register(String path, DamageType type,
                                    java.util.function.IntSupplier power,
                                    int apCost, int range, boolean ranged,
                                    WeaponAbilityHandler ability) {
        Item item = lookupItem(path);
        if (item == null) return false;
        WeaponEntry.Builder b = WeaponEntry.builder(item)
            .damageType(type).attackPower(power)
            .apCost(apCost).range(range).ranged(ranged).breakChance(0.0);
        if (ability != null) b.ability(ability);
        WeaponRegistry.register(item, b.build());
        return true;
    }

    /** AC for the Warden set - one above netherite (7), the game's new ceiling. */
    public static final int WARDEN_ARMOR_CLASS = 8;
    /** AC for the Resonarium set - between iron (4) and diamond (6). */
    public static final int RESONARIUM_ARMOR_CLASS = 5;

    /** Per-piece affinity (half-points) both sets grant; full 4-piece set = 2 affinity. */
    private static final int PIECE_AFFINITY = 1;

    /**
     * Register both armor sets.
     * <ul>
     *   <li><b>Warden - "Echo"</b>: Physical per-piece affinity plus the dynamic set
     *       bonus in {@link WardenSetEffects} (permanent Darkness, +2 to the damage
     *       type you carry most). Physical is deliberately the generic lane so the
     *       static half doesn't pre-commit the build the dynamic half will pick.</li>
     *   <li><b>Resonarium - "Resonant"</b>: Special affinity, the sound/echo lane,
     *       with no gimmick. It is the honest mid-tier upgrade over iron.</li>
     * </ul>
     * Set detection needs no code: {@code ArmorClassTable.armorSetKeyOf} derives
     * {@code "warden"} / {@code "resonarium"} from the item paths, and
     * {@code PlayerCombatStats.getArmorSet} already falls through to any registered
     * key whose four pieces match.
     */
    private static boolean registerArmorSets() {
        boolean any = false;
        if (lookupItem("warden_helmet") != null) {
            ArmorSetRegistry.register(ArmorSetEntry.builder(WardenSetEffects.SET_KEY)
                .damageBonus(DamageType.PHYSICAL, PIECE_AFFINITY)
                .armorClass(WARDEN_ARMOR_CLASS)
                .description("§3Echo: §7always Darkness, +"
                    + WardenSetEffects.DOMINANT_AFFINITY_BONUS
                    + " to your most-carried affinity (live)")
                .build());
            any = true;
        }
        if (lookupItem("resonarium_helmet") != null) {
            ArmorSetRegistry.register(ArmorSetEntry.builder("resonarium")
                .damageBonus(DamageType.SPECIAL, PIECE_AFFINITY)
                .armorClass(RESONARIUM_ARMOR_CLASS)
                .description("§3Resonant: §7+" + (PIECE_AFFINITY * 2) + " Special Power")
                .build());
            any = true;
        }
        return any;
    }

    /**
     * Per-kill drops for the mod's creatures, or {@code null} for anything this
     * module doesn't own. Called from {@code CombatManager.getMobDrops}'s default
     * branch: the ids can't be {@code case} labels there because they only exist
     * when the mod does.
     *
     * <p>Drops are the mod's own crafting materials, so killing sculk creatures
     * feeds the Resonarium and Warden tiers rather than dead-ending. Every entry
     * goes through {@link #addItem}, which skips ids that aren't registered.
     */
    public static com.crackedgames.craftics.combat.LootPool mobDrops(String entityTypeId) {
        if (!loaded || entityTypeId == null || !entityTypeId.startsWith(NS)) return null;
        com.crackedgames.craftics.combat.LootPool pool =
            new com.crackedgames.craftics.combat.LootPool();
        switch (entityTypeId) {
            // The Shattered inherited the Stalker's role as the reliable source of the
            // reinforced shard. That shard gates the Warden gear tier, and the Stalker was
            // the only mob that dropped it dependably - withdrawing the Stalker without
            // rehoming this would have quietly walled off a whole tier of progression.
            case SHATTERED -> {
                addItem(pool, "sculk_bone", 5);
                addItem(pool, "grime_ball", 3);
                addItem(pool, "reinforced_echo_shard", 2);
            }
            case SCULK_CENTIPEDE -> { addItem(pool, "sculk_bone", 4); addItem(pool, "soul_dust", 3); }
            case SCULK_LEECH -> { addItem(pool, "soul_dust", 5); addItem(pool, "grime_ball", 2); }
            case SCULK_SNAPPER -> { addItem(pool, "sculk_bone", 5); addItem(pool, "resonarium", 1); }
            case SHRIEK_WORM -> { addItem(pool, "soul_crystal", 2); addItem(pool, "sculk_bone", 4); }
            case ANGLER -> { addItem(pool, "soul_dust", 4); addItem(pool, "grime_ball", 3); }
            case SLUDGE -> { addItem(pool, "grime_ball", 6); addItem(pool, "soul_dust", 2); }
            // Unreachable now that nothing spawns a Stalker, and kept anyway: an admin
            // summoning one by command, or a future decision to bring it back, should not
            // find it dropping nothing. Its shard has moved to the Shattered above.
            case STALKER -> {
                addItem(pool, "reinforced_echo_shard", 2);
                addItem(pool, "soul_crystal", 3);
                addItem(pool, "warden_carapace", 1);
            }
            default -> { return null; }
        }
        return pool;
    }

    /** Add a {@code deeperdarker:} item to a loot pool, skipping ids that aren't registered. */
    private static void addItem(com.crackedgames.craftics.combat.LootPool pool, String path, int weight) {
        Item item = lookupItem(path);
        if (item != null) pool.add(item, weight);
    }

    // ================================================================
    // Arena props: infested sculk, ancient vase, sculk jaw
    // ================================================================

    /** Chance per fight that any D&D props are scattered at all. */
    private static final float PROP_CHANCE = 0.5f;
    /** Chance a placed prop is an ancient vase rather than infested sculk. */
    private static final float VASE_SHARE = 0.4f;
    /** Chance the vase bursts instead of paying out. Mirrors the mod's own gamble. */
    private static final float VASE_AMBUSH_CHANCE = 0.35f;

    /**
     * Scatter Deep Dark props into the arena at fight start. Called from
     * {@code SculkSensorEffect}, which owns the deep_dark biome-effect slot - a biome
     * can only name one effect in JSON, so the props ride along with the sensors
     * rather than needing a slot of their own. No-ops without the mod.
     */
    public static void placeArenaProps(MinibossContext ctx) {
        if (!loaded || ctx == null) return;
        java.util.Random rng = ctx.rng();
        if (rng.nextFloat() > PROP_CHANCE) return;

        net.minecraft.block.Block infestedBlock = lookupBlock("infested_sculk");
        net.minecraft.block.Block vaseBlock = lookupBlock("ancient_vase");

        GridArena arena = ctx.arena();
        int width = arena.getWidth();
        int height = arena.getHeight();
        java.util.List<GridPos> used = new java.util.ArrayList<>();
        used.add(new GridPos(width / 2, 0)); // player start - never place here

        // Jaws below don't need either block, so a missing prop block only skips props.
        int count = (infestedBlock == null && vaseBlock == null) ? 0 : 1 + rng.nextInt(2);
        boolean placedAny = false;
        for (int i = 0; i < count; i++) {
            // Props are blocks, so they need real floor - the bare roll would hang a vase
            // over a VOID pit.
            GridPos pos = com.crackedgames.craftics.combat.miniboss.MinibossSpawns
                .findOpen(width, height, used, rng, ctx.arena()::isPlaceableFloor);
            if (pos == null) continue;
            used.add(pos);
            boolean asVase = vaseBlock != null && (infestedBlock == null || rng.nextFloat() < VASE_SHARE);
            if (asVase) {
                ctx.spawnBlockObject(CombatManager.ANCIENT_VASE_ID, pos,
                    CombatManager.ANCIENT_VASE_HP, vaseBlock);
            } else {
                ctx.spawnBlockObject(CombatManager.INFESTED_SCULK_ID, pos,
                    CombatManager.INFESTED_SCULK_HP, infestedBlock);
            }
            placedAny = true;
        }
        // Sculk jaws are NOT generated. The tile is backed by a real Deeper and Darker
        // block, and that block keeps running its own logic every tick inside the arena -
        // biting whoever stands on it over and over in real time, on nobody's turn, on top
        // of the single once-per-step bite the grid rule applies. A hazard the turn system
        // cannot see or bound is not a hazard, it is a death sentence with no counterplay,
        // so the floor here stays hostile through the growths and the geysers instead.
        int jawsPlaced = 0;

        if (placedAny || jawsPlaced > 0) {
            ctx.message("§8Infested growths, old pottery and waiting jaws litter the dark.");
        }
    }

    /**
     * Infested sculk broken without Silk Touch: leeches and a shriek worm boil out and
     * the breaker is thrown back. Stats match the biome pool so an ambush spawn is no
     * weaker than one that started the fight.
     */
    public static void eruptInfestedSculk(CombatManager cm, GridPos pos) {
        if (cm == null || pos == null) return;
        int leeches = 1 + (int) (Math.random() * 2); // 1-2
        int spawned = 0;
        for (int i = 0; i < leeches; i++) {
            if (cm.spawnPropAmbusher(SCULK_LEECH, pos, 8, 3, 0, 1) != null) spawned++;
        }
        boolean worm = cm.spawnPropAmbusher(SHRIEK_WORM, pos, 20, 7, 3, 3) != null;

        net.minecraft.server.world.ServerWorld world = cm.propWorld();
        net.minecraft.util.math.BlockPos bp = cm.propBlockPos(pos);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.SCULK_SOUL,
            bp.getX() + 0.5, bp.getY() + 0.6, bp.getZ() + 0.5, 24, 0.4, 0.5, 0.4, 0.05);
        world.playSound(null, bp, net.minecraft.sound.SoundEvents.BLOCK_SCULK_SPREAD,
            net.minecraft.sound.SoundCategory.HOSTILE, 1.0f, 0.6f);

        if (spawned > 0 || worm) {
            cm.propMessage("§4The sculk splits open - something was living in it!");
        } else {
            cm.propMessage("§8The infested sculk crumbles. Nothing had room to crawl out.");
        }
        // The recoil lands even when nothing spawned: the burst itself is the shove.
        cm.shovePlayerFrom(pos, 2);
    }

    /**
     * Ancient vase broken: treasure, or a Stalker. Silk Touch instead hands over the
     * vase itself intact (the mod's own silk behaviour), which is the safe play - you
     * trade the roll for a block you can carry out.
     */
    public static void openAncientVase(CombatManager cm, GridPos pos, boolean silkTouch) {
        if (cm == null || pos == null) return;
        Item vaseItem = lookupItem("ancient_vase");
        if (silkTouch && vaseItem != null) {
            cm.givePropLoot(new net.minecraft.item.ItemStack(vaseItem));
            cm.propMessage("§aSilk Touch: you lift the vase out whole, unopened.");
            return;
        }

        net.minecraft.server.world.ServerWorld world = cm.propWorld();
        net.minecraft.util.math.BlockPos bp = cm.propBlockPos(pos);

        if (Math.random() < VASE_AMBUSH_CHANCE) {
            world.spawnParticles(net.minecraft.particle.ParticleTypes.SCULK_SOUL,
                bp.getX() + 0.5, bp.getY() + 0.8, bp.getZ() + 0.5, 30, 0.4, 0.6, 0.4, 0.08);
            world.playSound(null, bp, net.minecraft.sound.SoundEvents.ENTITY_WARDEN_ROAR,
                net.minecraft.sound.SoundCategory.HOSTILE, 0.8f, 1.2f);
            // A Shattered, not a Stalker. The Stalker's behaviour lives in its own entity
            // code rather than in vanilla goals, so the NoAI flag every arena mob is frozen
            // with never held it - it acted between turns and summoned its own minions. It
            // is no longer spawned anywhere in this mod.
            if (cm.spawnPropAmbusher(SHATTERED, pos, 45, 9, 3, 1) != null) {
                cm.propMessage("§4The vase shatters - something unfolds out of it!");
            } else {
                cm.propMessage("§4Something moved inside the vase, but found no room to emerge.");
            }
            return;
        }

        world.playSound(null, bp, net.minecraft.sound.SoundEvents.BLOCK_DECORATED_POT_SHATTER,
            net.minecraft.sound.SoundCategory.BLOCKS, 1.0f, 1.0f);
        for (net.minecraft.item.ItemStack stack : rollVaseTreasure()) {
            cm.givePropLoot(stack);
        }
        cm.propMessage("§6The vase breaks open - treasure spills out.");
    }

    /** The vase payout, weighted after the mod's own ancient_vase loot table. */
    private static java.util.List<net.minecraft.item.ItemStack> rollVaseTreasure() {
        com.crackedgames.craftics.combat.LootPool pool = new com.crackedgames.craftics.combat.LootPool();
        pool.add(net.minecraft.item.Items.DIAMOND, 3);
        pool.add(net.minecraft.item.Items.EMERALD, 4);
        pool.add(net.minecraft.item.Items.GOLD_INGOT, 5);
        pool.add(net.minecraft.item.Items.IRON_INGOT, 5);
        pool.add(net.minecraft.item.Items.LAPIS_LAZULI, 4);
        pool.add(net.minecraft.item.Items.REDSTONE, 4);
        pool.add(net.minecraft.item.Items.GOLDEN_APPLE, 2);
        pool.add(net.minecraft.item.Items.ENCHANTED_GOLDEN_APPLE, 1);
        addItem(pool, "warden_carapace", 2);
        addItem(pool, "reinforced_echo_shard", 1);
        return pool.roll(1, 3, 1, 4);
    }

    // ================================================================
    // Artifacts: soul elytra, heart of the deep
    // ================================================================

    /** Tiles the Soul Elytra can carry you, straight over anything in between. */
    public static final int ELYTRA_GLIDE_TILES = 5;

    /**
     * Combat use for the mod's artifacts, or {@code null} for any item this module
     * doesn't own. Called from {@code ItemUseHandler}'s dispatch, which can't branch
     * on these items directly because they only exist when the mod does.
     *
     * <p>Neither artifact is consumed. They're endgame crafts gated behind the
     * Warden fight, and their AP cost is what limits them.
     */
    public static String tryUseItem(net.minecraft.server.network.ServerPlayerEntity player,
                                    GridArena arena, GridPos targetTile,
                                    Item item, net.minecraft.item.ItemStack held) {
        if (!loaded || item == null) return null;
        Identifier id = Registries.ITEM.getId(item);
        if (id == null || !MOD_ID.equals(id.getNamespace())) return null;

        CombatManager cm = CombatManager.getActiveCombat(player.getUuid());
        if (cm == null) return null;

        return switch (id.getPath()) {
            case "soul_elytra" -> {
                String failure = cm.glideTo(targetTile, ELYTRA_GLIDE_TILES);
                yield failure != null ? failure
                    : "§3You launch on soul-wings and glide clear over the ground!";
            }
            case "heart_of_the_deep" -> {
                int revealed = cm.pulseReveal();
                yield revealed > 0
                    ? "§bThe heart pulses - §f" + revealed
                        + " hidden thing" + (revealed == 1 ? "" : "s") + " dragged into view!"
                    : "§bThe heart pulses. §7Nothing was hiding, but the dark lifts.";
            }
            default -> null;
        };
    }

    /** AP cost for an artifact, or 0 when the item isn't one of ours. */
    public static int apCostFor(Item item) {
        if (!loaded || item == null) return 0;
        Identifier id = Registries.ITEM.getId(item);
        if (id == null || !MOD_ID.equals(id.getNamespace())) return 0;
        return switch (id.getPath()) {
            case "soul_elytra" -> 2;       // a full repositioning, priced like one
            case "heart_of_the_deep" -> 1; // cheap: it answers a problem, it doesn't solve the fight
            default -> 0;
        };
    }

    /**
     * The Sonorous Staff's sonic boom: everything on the straight line through the
     * target is hit, with damage falling off the further down the line it stands
     * (the mod's own {@code dropOffFactor}), and the primary target is shoved back.
     *
     * <p>The falloff is what keeps it from being a strictly-better bow: lining up
     * five enemies rewards you, but the ones at the back take very little, so it is a
     * crowd tool rather than a single-target upgrade.
     */
    private static WeaponAbilityHandler sonicBoom() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            java.util.List<String> messages = new java.util.ArrayList<>();
            java.util.List<com.crackedgames.craftics.combat.CombatEntity> extra =
                new java.util.ArrayList<>();

            GridPos from = arena.getPlayerGridPos();
            GridPos tPos = target.getGridPos();
            int dx = Integer.signum(tPos.x() - from.x());
            int dz = Integer.signum(tPos.z() - from.z());
            if (dx == 0 && dz == 0) dx = 1;

            // Walk past the target along the same heading. Each further tile keeps
            // 60% of the previous tile's damage.
            double falloff = 1.0;
            GridPos scan = new GridPos(tPos.x() + dx, tPos.z() + dz);
            for (int i = 0; i < SONIC_BOOM_LENGTH && arena.isInBounds(scan); i++) {
                falloff *= SONIC_BOOM_FALLOFF;
                com.crackedgames.craftics.combat.CombatEntity hit = arena.getOccupant(scan);
                if (hit != null && hit.isAlive() && !hit.isAlly()) {
                    int dmg = Math.max(1, (int) Math.round(baseDamage * falloff));
                    int dealt = hit.takeDamage(dmg);
                    extra.add(hit);
                    messages.add("§3The boom washes over " + hit.getDisplayName()
                        + " for " + dealt + ".");
                }
                scan = new GridPos(scan.x() + dx, scan.z() + dz);
            }

            // Shove the primary target back along the boom.
            com.crackedgames.craftics.combat.GridPush.Result boom = com.crackedgames.craftics.combat.GridPush.resolve(
                com.crackedgames.craftics.combat.CombatManager.pushGridFor(arena, target),
                target.getGridPos().x(), target.getGridPos().z(),
                target.getSizeX(), target.getSizeZ(),
                dx, dz, SONIC_BOOM_KNOCKBACK, target.isHazardImmune());
            GridPos push = new GridPos(boom.x(), boom.z());
            int pushed = boom.moved();
            if (pushed > 0) {
                arena.moveEntity(target, push);
                if (target.getMobEntity() != null) {
                    var bp = arena.gridToBlockPos(push);
                    target.getMobEntity().requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
                }
                messages.add("§3The blast hurls " + target.getDisplayName()
                    + " back " + pushed + " tile(s)!");
            }
            return new com.crackedgames.craftics.combat.WeaponAbility.AttackResult(
                baseDamage, messages, extra);
        };
    }

    /** Tiles past the primary target the boom keeps travelling. */
    private static final int SONIC_BOOM_LENGTH = 4;
    /** Damage retained per tile further down the boom. */
    private static final double SONIC_BOOM_FALLOFF = 0.6;
    /** Tiles the primary target is shoved. */
    private static final int SONIC_BOOM_KNOCKBACK = 2;

    /** Live block lookup under the {@code deeperdarker:} namespace, or null. */
    private static net.minecraft.block.Block lookupBlock(String path) {
        Identifier id = Identifier.of(MOD_ID, path);
        if (!Registries.BLOCK.containsId(id)) return null;
        return Registries.BLOCK.get(id);
    }

    /** Live registry lookup under the {@code deeperdarker:} namespace, or null. */
    private static Item lookupItem(String path) {
        Identifier id = Identifier.of(MOD_ID, path);
        if (!Registries.ITEM.containsId(id)) return null;
        return Registries.ITEM.get(id);
    }

    public static Item wardenHelmet()     { return lookupItem("warden_helmet"); }
    public static Item resonariumHelmet() { return lookupItem("resonarium_helmet"); }

    /**
     * Full-replace the deep_dark hostile pool with the D&D roster. Called after the
     * other compat modules so this wholesale swap wins.
     */
    public static void applyBiomeOverrides() {
        if (!loaded) return;
        // type, weight, hp, atk, def, range, passive, aiKey, speed. Speeds match
        // the mob spec: Shattered 2, Centipede 2 (hit-and-run), Snapper 1 (slow),
        // Leech fast (3), Shriek Worm 0 (immobile - never moves). Stats tuned to
        // the vanilla deep_dark numbers.
        BiomeCompatHelper.replaceAllHostile("deep_dark", new MobPoolEntry[]{
            new MobPoolEntry(SHATTERED,       5, 14, 5, 2, 1, false, SHATTERED, 2),       // common melee
            new MobPoolEntry(SCULK_CENTIPEDE, 4, 12, 4, 1, 1, false, SCULK_CENTIPEDE, 2), // hit-and-run, poison
            new MobPoolEntry(SCULK_SNAPPER,   3, 14, 5, 2, 1, false, SCULK_SNAPPER, 1),   // slow, roots
            new MobPoolEntry(SCULK_LEECH,     4, 8,  3, 0, 1, false, SCULK_LEECH, 3),     // fast, lifesteal
            new MobPoolEntry(SHRIEK_WORM,     2, 20, 7, 3, 3, false, SHRIEK_WORM, 0),     // immobile turret
            // Angler Fish is safe to leave in the general pool: it's water-locked
            // (AnglerFishAI) and only a threat when the player is in water, which
            // only the Blooming Caverns arena has - so it self-gates to Blooming.
            // A low weight keeps it from crowding the dry sub-areas where it just
            // drifts harmlessly.
            new MobPoolEntry(ANGLER,          2, 8,  6, 0, 1, false, ANGLER, 5),          // water ambusher
            // Sludge was registered (AI + Soaked tag) but never added to any pool, so
            // it could not spawn at all. It belongs to Blooming Caverns thematically,
            // but there is no per-arena spawn gate - a low weight in the shared pool
            // is what actually gets it into fights.
            new MobPoolEntry(SLUDGE,          2, 16, 4, 1, 1, false, SLUDGE, 2),          // slime, Soaked on hit
        });
        applyLootOverrides();
    }

    /**
     * Add the mod's materials to the Deep Dark's level-completion loot pool.
     *
     * <p>This has to happen at runtime rather than in {@code deep_dark.json}: the
     * biome JSON loader drops unknown item ids with a load-time warning, so listing
     * {@code deeperdarker:} ids there would spam the log of every player who doesn't
     * have the mod. {@code appendLoot} resolves against the live registry instead and
     * silently skips what isn't there.
     *
     * <p>Weights sit alongside the vanilla Deep Dark pool (diamond 3, echo_shard 3,
     * deepslate 6): the crafting materials are common, the smithing template that
     * gates the Warden tier is deliberately the rarest thing in the biome.
     */
    private static void applyLootOverrides() {
        BiomeCompatHelper.appendLoot("deep_dark", NS + "sculk_bone", 5);
        BiomeCompatHelper.appendLoot("deep_dark", NS + "soul_dust", 4);
        BiomeCompatHelper.appendLoot("deep_dark", NS + "grime_ball", 4);
        BiomeCompatHelper.appendLoot("deep_dark", NS + "soul_crystal", 2);
        BiomeCompatHelper.appendLoot("deep_dark", NS + "resonarium", 3);
        BiomeCompatHelper.appendLoot("deep_dark", NS + "resonarium_plate", 2);
        BiomeCompatHelper.appendLoot("deep_dark", NS + "warden_carapace", 1);
        BiomeCompatHelper.appendLoot("deep_dark", NS + "reinforced_echo_shard", 1);
        BiomeCompatHelper.appendLoot("deep_dark", NS + "warden_upgrade_smithing_template", 1);
    }

    /**
     * Classify a Blooming-Caverns hazard block into its Craftics hazard tile, or
     * {@code null} if it isn't one. Only the two damaging Blooming-Caverns props
     * count (confirmed against Deeper and Darker 1.3.3):
     * <ul>
     *   <li>{@code deeperdarker:gloomy_geyser} → {@link TileType#GEYSER}
     *       (step-trap: Burning II + random launch)</li>
     *   <li>{@code deeperdarker:gloomy_cactus} → {@link TileType#BLOOM}
     *       (contact damage + Burning)</li>
     * </ul>
     * Everything else in the mod (bloom_planks, blooming_moss_block, the sculk
     * building blocks, ...) stays a normal obstacle/floor and never becomes a
     * hazard tile.
     */
    public static TileType hazardTileFor(BlockState state) {
        if (state == null || state.isAir()) return null;
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        if (id == null || !MOD_ID.equals(id.getNamespace())) return null;
        String path = id.getPath();
        if (path.equals("gloomy_geyser")) return TileType.GEYSER;
        if (path.equals("gloomy_cactus")) return TileType.BLOOM;
        // A sculk jaw baked into a schematic is deliberately NOT classified as a live jaw
        // tile - see the placement site above. Left unclassified it is ordinary scenery.
        return null;
    }
}
