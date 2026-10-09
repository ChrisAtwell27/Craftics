package com.crackedgames.craftics.compat.aether;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.Abilities;
import com.crackedgames.craftics.api.CrafticsAPI;
import com.crackedgames.craftics.api.ItemUseResult;
import com.crackedgames.craftics.api.TargetType;
import com.crackedgames.craftics.api.UsableItemHandler;
import com.crackedgames.craftics.api.WeaponAbilityHandler;
import com.crackedgames.craftics.api.registry.ArmorSetEntry;
import com.crackedgames.craftics.api.registry.ArmorSetRegistry;
import com.crackedgames.craftics.api.registry.UsableItemEntry;
import com.crackedgames.craftics.api.registry.UsableItemRegistry;
import com.crackedgames.craftics.api.registry.WeaponEntry;
import com.crackedgames.craftics.api.registry.WeaponRegistry;
import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.CombatManager;
import com.crackedgames.craftics.combat.DamageType;
import com.crackedgames.craftics.combat.PlayerCombatStats;
import com.crackedgames.craftics.combat.ProjectileSpawner;
import com.crackedgames.craftics.combat.WeaponAbility;
import com.crackedgames.craftics.core.GridArena;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;

/**
 * Compatibility for The Aether ({@code aether}).
 *
 * <p>This is the gear half: every Aether weapon, tool, armor set and consumable gets Craftics
 * stats, and each keeps the thing that made it worth carrying in the Aether - a zanite blade
 * that hits harder the more worn it is, gravitite that leaves whatever it touches floating,
 * a vampire blade that drinks. The worn half (armor set effects, gloves, rings, capes) is
 * {@link AetherScanner}.
 *
 * <p>Nothing here links against the Aether: items are found by registry id, late, the way
 * every gear compat in this package does it. Without the mod installed {@link #init} returns
 * before doing anything and every predicate below answers false.
 *
 * <h2>Where the numbers come from</h2>
 * Tiers are placed by the Aether's own tool tiers rather than by feel: Skyroot mines like
 * wood, Holystone like stone, Zanite like iron, Gravitite and Valkyrie like diamond, and each
 * borrows that vanilla tier's Craftics damage through the live config so retuning vanilla
 * retunes these with it. The dungeon swords are all diamond-tier in the Aether, and are here.
 */
public final class AetherCompat {

    private AetherCompat() {}

    public static final String MOD_ID = "aether";
    private static final String SCANNER_ID = "craftics:aether";

    private static boolean loaded = false;
    private static boolean gearRegistered = false;

    /** Called once from mod init. No-ops entirely without the Aether. */
    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) return;
        loaded = true;
        // The scanner resolves nothing until it is asked to scan a player, so it is safe to
        // register before the Aether's own entrypoint has populated the item registry.
        CrafticsAPI.registerEquipmentScanner(SCANNER_ID, new AetherScanner());
        registerRegion();
        CrafticsMod.LOGGER.info("[Craftics × Aether] enabled");
    }

    public static boolean isLoaded() {
        return loaded;
    }

    // ================================================================
    // The region: three dungeons, opening beside The End
    // ================================================================

    public static final String REGION_ID = "aether";
    public static final String BRONZE_DUNGEON = "aether_bronze_dungeon";
    public static final String SILVER_DUNGEON = "aether_silver_dungeon";
    public static final String GOLD_DUNGEON = "aether_gold_dungeon";
    /** The campaign biome whose clear opens the Aether: the Nether's last, so it opens with The End. */
    public static final String UNLOCKS_AFTER = "basalt_deltas";
    /** Where the dungeon biome JSON lives. Deliberately NOT under {@code craftics/biomes}. */
    private static final String BIOME_DIRECTORY = "craftics/compat/aether/biomes";

    /**
     * The Aether as an optional side region: not on the campaign line, never needed to finish
     * it, and cleared against a count of its own. Registered on both sides, since the level
     * select screen builds its tabs from this.
     */
    private static void registerRegion() {
        com.crackedgames.craftics.level.campaign.CampaignManager.registerSideRegion(
            new com.crackedgames.craftics.level.campaign.CampaignSideRegion(
                com.crackedgames.craftics.level.campaign.CampaignRegion.builder(REGION_ID)
                    .displayName("The Aether").color("§b").icon("☁").mapColor(0xAA88DDFF)
                    .node(BRONZE_DUNGEON, "Bronze Dungeon")
                    .node(SILVER_DUNGEON, "Silver Dungeon")
                    .node(GOLD_DUNGEON, "Gold Dungeon")
                    .build(),
                UNLOCKS_AFTER));
    }

    /**
     * Load the three dungeon biomes. Called after every {@code BiomeRegistry.loadFromDatapacks},
     * which clears the registry first - so, like the pool overrides the other compat modules
     * apply, this has to be redone each time.
     *
     * <p>The JSON sits outside the directory the standard loader scans because that loader
     * runs on every install. Shipped there, the dungeons would be registered biomes on a
     * server with no Aether: in the atlas, counted, and built out of blocks that do not exist.
     */
    public static void loadBiomes(net.minecraft.resource.ResourceManager resourceManager) {
        if (!loaded || resourceManager == null) return;
        registerEnvironments();
        int count = 0;
        for (com.crackedgames.craftics.level.BiomeTemplate biome
                : com.crackedgames.craftics.level.BiomeJsonLoader.loadFromResources(resourceManager, BIOME_DIRECTORY)) {
            com.crackedgames.craftics.level.BiomeRegistry.register(biome);
            count++;
        }
        CrafticsMod.LOGGER.info("[Craftics × Aether] {} dungeon biome(s) loaded", count);
    }

    /** Arena dressing per dungeon: the floor under the grid, the border posts, the lights on them. */
    private static void registerEnvironments() {
        environment("aether_bronze", "carved_stone", "carved_wall");
        environment("aether_silver", "angelic_stone", "angelic_wall");
        environment("aether_gold", "hellfire_stone", "hellfire_wall");
        // Above ground, on the way to each dungeon: grass underfoot, skyroot fencing at the edge.
        environment("aether_highlands", "aether_grass_block", "skyroot_fence");
    }

    private static void environment(String id, String floorPath, String postPath) {
        net.minecraft.block.Block floor = lookupBlock(floorPath);
        if (floor == null) return;
        net.minecraft.block.Block post = lookupBlock(postPath);
        com.crackedgames.craftics.api.EnvironmentDef.Builder b =
            com.crackedgames.craftics.api.EnvironmentDef.builder(id).floorBlock(floor);
        // No Aether block hangs like a lantern, so the lights stay vanilla's default.
        if (post != null) b.postBlock(post);
        com.crackedgames.craftics.api.registry.EnvironmentRegistry.register(b.build());
    }

    /** Whether this biome's arenas are hand-built rooms to be kept exactly as they were saved. */
    public static boolean isHandBuiltArena(String biomeId) {
        return BRONZE_DUNGEON.equals(biomeId) || SILVER_DUNGEON.equals(biomeId) || GOLD_DUNGEON.equals(biomeId);
    }

    /**
     * The block an arena should be built with in place of {@code blockId}.
     *
     * <p>A dungeon copied out of the Aether brings its trapped stone with it: blocks that
     * look like the floor around them and, when a player steps on one, turn to plain stone
     * and spawn a live sentry, valkyrie or fire minion with its own AI. In an arena that is
     * a real mob loose in a turn-based fight. They are laid as the stone they imitate.
     *
     * <p>A treasure doorway is the lid over a dungeon's loot room, which the Aether opens
     * when its boss falls. Nothing here ever opens it, and in the Valkyrie Queen's room it
     * is four tiles of the floor in front of her throne. It is laid as the locked stone
     * around it, which is certain to hold whoever stands there. Any other id comes back
     * unchanged.
     */
    public static String arenaSafeBlockId(String blockId) {
        if (blockId == null) return null;
        String trapped = MOD_ID + ":trapped_";
        if (blockId.startsWith(trapped)) {
            return MOD_ID + ":" + blockId.substring(trapped.length());
        }
        String treasureDoor = MOD_ID + ":treasure_doorway_";
        if (blockId.startsWith(treasureDoor)) {
            return MOD_ID + ":locked_" + blockId.substring(treasureDoor.length());
        }
        return blockId;
    }

    /** The {@code aether:<path>} block, or null when it is not registered. */
    public static net.minecraft.block.Block lookupBlock(String path) {
        Identifier id = Identifier.of(MOD_ID, path);
        if (!Registries.BLOCK.containsId(id)) return null;
        return Registries.BLOCK.get(id);
    }

    /**
     * Late-phase gear registration. {@link WeaponRegistry} is keyed by {@link Item} instance
     * and Fabric does not promise the Aether's entrypoint ran before ours, so this runs on
     * SERVER_STARTING / CLIENT_STARTED instead of in {@link #init}. Idempotent, and silent on
     * its early exits because the tooltip render path calls it as a fallback.
     */
    public static void registerDeferred() {
        if (gearRegistered || !loaded) return;
        boolean any = registerWeapons() | registerArmorSets() | registerUsables();
        if (any) {
            gearRegistered = true;
            CrafticsMod.LOGGER.info("[Craftics × Aether] gear registered");
        }
    }

    // ================================================================
    // Lookups and predicates
    // ================================================================

    /** The {@code aether:<path>} item, or null when it is not registered. */
    public static Item lookupItem(String path) {
        Identifier id = Identifier.of(MOD_ID, path);
        if (!Registries.ITEM.containsId(id)) return null;
        return Registries.ITEM.get(id);
    }

    /** The registry path of an Aether item ({@code "zanite_sword"}), or null for anything else. */
    public static String pathOf(Item item) {
        if (!loaded || item == null) return null;
        Identifier id = Registries.ITEM.getId(item);
        return MOD_ID.equals(id.getNamespace()) ? id.getPath() : null;
    }

    public static boolean isAetherItem(Item item) {
        return pathOf(item) != null;
    }

    /** Whether {@code player} has the {@code aether:<path>} item anywhere on them, held or not. */
    static boolean carries(ServerPlayerEntity player, String path) {
        if (!loaded || player == null) return false;
        Item item = lookupItem(path);
        if (item == null) return false;
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isOf(item)) return true;
        }
        return false;
    }

    // ── Gravitite: whatever it touches floats ──

    /** Turns of Levitation gravitite puts on whatever it lifts. */
    public static final int LEVITATION_TURNS = 2;
    /** Tiles a Gravitite Axe throws its target. */
    public static final int HURL_TILES = 2;

    /**
     * Whether gravitite can lift this. Whatever cannot be thrown in the Aether cannot be
     * thrown here: things rooted to the ground, and anything that flies already.
     */
    static boolean canLift(CombatEntity target) {
        return target != null && target.isAlive() && !target.isImmovable() && !target.isBackgroundBoss()
            && !target.isInertObject() && !target.isFlying()
            && !"aether:aechor_plant".equals(target.getEntityTypeId());
    }

    /** Set {@code target} Levitating, if it is something that can be lifted. */
    public static boolean levitate(CombatEntity target) {
        if (!canLift(target)) return false;
        target.applyLevitationState(LEVITATION_TURNS, 0);
        return true;
    }

    /**
     * Gravitite Pickaxe: the block it mines floats up off its tile instead of breaking, and
     * comes back down later. See {@code CombatManager.liftObstacle}.
     */
    public static boolean liftsBlocks(Item item) {
        return "gravitite_pickaxe".equals(pathOf(item));
    }

    /**
     * Gravitite Shovel: every pet of whoever carries it floats over obstacles. A shovel is a
     * focus for pets rather than something swung, so, like the shovel enchantments, it works
     * from anywhere in the inventory.
     */
    public static boolean petsFloat(ServerPlayerEntity owner) {
        return carries(owner, "gravitite_shovel");
    }

    /**
     * Gravitite Hoe: while it is carried, anything of the player's that deals Special damage
     * (a hoe, a sherd, a horn, a thrown potion) also leaves what it hits Levitating.
     *
     * @return true if {@code target} was lifted by it
     */
    public static boolean levitateOnSpecialHit(ServerPlayerEntity player, CombatEntity target) {
        if (!loaded || target == null || target.isAlly() || !target.isAlive()) return false;
        return carries(player, "gravitite_hoe") && levitate(target);
    }

    /** The Phoenix Bow draws ordinary arrows, so it is a bow for every rule that asks. */
    public static boolean isPhoenixBow(Item item) {
        return "phoenix_bow".equals(pathOf(item));
    }

    /** The Aether weapons that strike from a distance, for the HUD's ranged-attack mode. */
    public static boolean isRangedWeapon(Item item) {
        String path = pathOf(item);
        if (path == null) return false;
        return path.equals("phoenix_bow") || path.endsWith("_dart_shooter")
            || path.equals("cloud_staff") || path.equals("hammer_of_kingbdogz");
    }

    /**
     * The dart a dart shooter fires, or null for anything that is not a dart shooter. Each
     * shooter takes exactly its own dart, as in the Aether.
     */
    public static Item dartFor(Item weapon) {
        String path = pathOf(weapon);
        if (path == null || !path.endsWith("_dart_shooter")) return null;
        return lookupItem(path.substring(0, path.length() - "_shooter".length()));
    }

    /** Spend one dart. The caller has already checked there is one. */
    public static void consumeDart(ServerPlayerEntity player, Item dart) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isOf(dart)) {
                stack.decrement(1);
                return;
            }
        }
    }

    // ================================================================
    // Pure rules (unit-tested)
    // ================================================================

    /**
     * Extra damage a zanite weapon deals for being worn.
     *
     * <p>The Aether's own formula, kept whole: the weapon is worth half again less than
     * nothing extra until a quarter of its durability is gone, then climbs in a straight line
     * to one and a half times its base on the swing before it breaks. On the Aether's
     * 5-damage blade that is +3 at half worn and +8 at the end; here it scales the same way
     * off whatever the iron tier is configured to.
     *
     * @param power     the weapon's base attack power
     * @param damage    durability used so far
     * @param maxDamage total durability
     */
    public static int zaniteBonus(int power, int damage, int maxDamage) {
        if (power <= 0 || maxDamage <= 0 || damage <= 0) return 0;
        double worn = Math.min(1.0, (double) damage / maxDamage);
        return (int) Math.round(Math.max(0.0, power * (2.0 * worn - 0.5)));
    }

    /** The Aether's {@code aether:pigs} entity tag: what the Pig Slayer was forged for. */
    private static final Set<String> PIGS = Set.of(
        "minecraft:pig", "aether:phyg", "minecraft:piglin", "minecraft:piglin_brute",
        "minecraft:zombified_piglin", "minecraft:hoglin", "minecraft:zoglin");

    public static boolean isPig(String entityTypeId) {
        return entityTypeId != null && PIGS.contains(entityTypeId);
    }

    /** The Aether's {@code aether:fire_mob} tag, which cloud crystals hit harder. */
    private static final Set<String> FIRE_MOBS = Set.of("minecraft:blaze", "aether:fire_minion");

    public static boolean isFireMob(String entityTypeId) {
        return entityTypeId != null && FIRE_MOBS.contains(entityTypeId);
    }

    /** What a material is worth as a weapon: the vanilla tier it mines like. */
    enum Tier {
        SKYROOT(2, 1), HOLYSTONE(3, 1), ZANITE(4, 2), GRAVITITE(5, 3), VALKYRIE(5, 3);

        /** Fixed shovel and hoe damage, mirroring the vanilla tier (those have no config entry). */
        final int shovel, hoe;

        Tier(int shovel, int hoe) {
            this.shovel = shovel;
            this.hoe = hoe;
        }

        int sword() {
            var c = CrafticsMod.CONFIG;
            return switch (this) {
                case SKYROOT -> c.dmgWoodenSword();
                case HOLYSTONE -> c.dmgStoneSword();
                case ZANITE -> c.dmgIronSword();
                case GRAVITITE, VALKYRIE -> c.dmgDiamondSword();
            };
        }

        int axe() {
            var c = CrafticsMod.CONFIG;
            return switch (this) {
                case SKYROOT -> c.dmgWoodenAxe();
                case HOLYSTONE -> c.dmgStoneAxe();
                case ZANITE -> c.dmgIronAxe();
                case GRAVITITE, VALKYRIE -> c.dmgDiamondAxe();
            };
        }
    }

    // ================================================================
    // Weapons
    // ================================================================

    /** Tiles a Valkyrie weapon reaches. The Aether gives the whole tier +3.5 blocks of reach. */
    public static final int VALKYRIE_REACH = 2;
    /** Tiles the Hammer of Kingbdogz, the dart shooters and the Cloud Staff reach. */
    public static final int THROWN_RANGE = 4;
    /** Cloud crystal damage, and its bonus against fire mobs. */
    public static final int CLOUD_CRYSTAL_DAMAGE = 8;
    public static final int CLOUD_CRYSTAL_FIRE_BONUS = 4;
    /** Lightning Knife damage before the Soaked doubling. */
    public static final int LIGHTNING_KNIFE_DAMAGE = 8;

    private static boolean registerWeapons() {
        boolean any = false;

        // ── The four mined tiers: sword and axe carry the material's ability ──
        any |= tier("skyroot", Tier.SKYROOT, bounty(), 1);
        any |= tier("holystone", Tier.HOLYSTONE, ambrosiumVein(), 1);
        any |= tier("zanite", Tier.ZANITE, null, 1);       // wear scaling is added per item below
        any |= tier("gravitite", Tier.GRAVITITE, launch(), hurl(), 1);
        // Valkyrie tools reach a tile further; the lance stands in for the tier's sword.
        any |= tier("valkyrie", Tier.VALKYRIE, null, VALKYRIE_REACH);
        // A lance does not sweep (the Aether refuses it Sweeping Edge), so it gets reach and
        // nothing else. 2 AP, like every other weapon that hits from two tiles away.
        any |= melee("valkyrie_lance", DamageType.SLASHING, Tier.VALKYRIE::sword, 2, VALKYRIE_REACH, null);

        // ── Dungeon swords: all diamond-tier in the Aether ──
        IntSupplier dungeon = Tier.GRAVITITE::sword;
        any |= melee("flaming_sword", DamageType.SLASHING, dungeon, 1, 1, sword().and(ignite()));
        any |= melee("lightning_sword", DamageType.SLASHING, dungeon, 1, 1, sword().and(lightningStrike()));
        any |= melee("holy_sword", DamageType.SLASHING, dungeon, 1, 1, sword().and(holy()));
        any |= melee("vampire_blade", DamageType.SLASHING, dungeon, 1, 1, sword().and(lifedrain()));
        any |= melee("pig_slayer", DamageType.SLASHING, Tier.ZANITE::sword, 1, 1, sword().and(pigSlayer()));
        any |= melee("candy_cane_sword", DamageType.SLASHING, Tier.SKYROOT::sword, 1, 1, sword().and(candy()));

        // ── Ranged ──
        // The hammer is swung like a sword in the Aether and thrown as a shockwave; here it
        // is the shockwave, since a weapon gets one attack and that is the one worth having.
        any |= ranged("hammer_of_kingbdogz", DamageType.BLUNT, () -> Tier.ZANITE.sword() + 1, 2,
            THROWN_RANGE, Abilities.aoe(1, 0.5).and(Abilities.knockbackDirection(1)));
        // Bow-relative, by what each dart does in the Aether next to a full-draw arrow.
        any |= ranged("golden_dart_shooter", DamageType.RANGED, () -> scaled(0.55), 1, THROWN_RANGE, null);
        any |= ranged("enchanted_dart_shooter", DamageType.RANGED, () -> scaled(0.90), 1, THROWN_RANGE, null);
        any |= ranged("poison_dart_shooter", DamageType.RANGED, () -> scaled(0.35), 1, THROWN_RANGE,
            Abilities.applyEffect(CombatEffects.EffectType.POISON, 3, 0));
        // A bow in every respect (see isPhoenixBow), with arrows that are already alight.
        any |= ranged("phoenix_bow", DamageType.RANGED, CrafticsMod.CONFIG::dmgBow, 1, 3, ignite());
        any |= ranged("cloud_staff", DamageType.SPECIAL, () -> CLOUD_CRYSTAL_DAMAGE, 2, THROWN_RANGE, cloudCrystal());
        return any;
    }

    private static int scaled(double ofBow) {
        return Math.max(1, (int) Math.round(CrafticsMod.CONFIG.dmgBow() * ofBow));
    }

    /**
     * Sword, axe, shovel and hoe of one material. Pickaxes are skipped, as vanilla's are: they
     * are not combat weapons (they are what the Slider is hurt by, which is its own story).
     */
    private static boolean tier(String material, Tier tier, WeaponAbilityHandler ability, int range) {
        return tier(material, tier, ability, ability, range);
    }

    /** As above, for a material whose sword and axe do different things with it. */
    private static boolean tier(String material, Tier tier, WeaponAbilityHandler swordExtra,
                                WeaponAbilityHandler axeExtra, int range) {
        boolean zanite = tier == Tier.ZANITE;
        int axeAp = 2;
        int swordAp = 1;
        WeaponAbilityHandler swordAbility = sword();
        WeaponAbilityHandler axeAbility = Abilities.armorIgnore(0.05, 0.03);
        if (zanite) {
            swordAbility = swordAbility.and(wearScaling(tier::sword));
            axeAbility = axeAbility.and(wearScaling(tier::axe));
        } else {
            if (swordExtra != null) swordAbility = swordAbility.and(swordExtra);
            if (axeExtra != null) axeAbility = axeAbility.and(axeExtra);
        }
        boolean any = false;
        any |= melee(material + "_sword", DamageType.SLASHING, tier::sword, swordAp, range, swordAbility);
        any |= melee(material + "_axe", DamageType.CLEAVING, tier::axe, axeAp, range, axeAbility);
        any |= melee(material + "_shovel", DamageType.PET, () -> tier.shovel, 1, range, null);
        any |= melee(material + "_hoe", DamageType.SPECIAL, () -> tier.hoe, 1, range, null);
        return any;
    }

    /** The base sword sweep, at vanilla's odds. Every Aether sword starts from it. */
    private static WeaponAbilityHandler sword() {
        return Abilities.sweepAdjacent(0.10, 0.03);
    }

    private static boolean melee(String path, DamageType type, IntSupplier power,
                                 int apCost, int range, WeaponAbilityHandler ability) {
        return register(path, type, power, apCost, range, false, ability);
    }

    private static boolean ranged(String path, DamageType type, IntSupplier power,
                                  int apCost, int range, WeaponAbilityHandler ability) {
        return register(path, type, power, apCost, range, true, ability);
    }

    private static boolean register(String path, DamageType type, IntSupplier power,
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

    // ── Abilities ──
    // A handler's returned damage is not what hits the primary target (CombatManager deals
    // that itself), so anything extra is applied here with takeDamage and reported.

    /** Skyroot: a mob it kills drops its loot twice. Not bosses, as in the Aether. */
    private static WeaponAbilityHandler bounty() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            if (!target.isAlive() && !target.isBoss() && !target.isAlly() && !target.hasDoubleDrops()) {
                target.setDoubleDrops(true);
                msgs.add("§a✦ Skyroot bounty! " + target.getDisplayName() + " will drop double.");
            }
            return new WeaponAbility.AttackResult(baseDamage, msgs, List.of());
        };
    }

    /** Holystone: a hit sometimes knocks an ambrosium shard loose. */
    private static WeaponAbilityHandler ambrosiumVein() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            Item shard = lookupItem("ambrosium_shard");
            if (shard != null && Math.random() < 0.10 + luckPoints * 0.02) {
                player.getInventory().offerOrDrop(new ItemStack(shard));
                msgs.add("§e✦ An ambrosium shard breaks loose!");
            }
            return new WeaponAbility.AttackResult(baseDamage, msgs, List.of());
        };
    }

    /** Zanite: hits harder the more worn it is. See {@link #zaniteBonus}. */
    private static WeaponAbilityHandler wearScaling(IntSupplier power) {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int total = baseDamage;
            ItemStack held = player.getMainHandStack();
            int bonus = held.isDamageable()
                ? zaniteBonus(power.getAsInt(), held.getDamage(), held.getMaxDamage()) : 0;
            if (bonus > 0 && target.isAlive()) {
                int dealt = target.takeDamage(bonus);
                total += dealt;
                msgs.add("§d✦ Worn zanite bites deeper for +" + dealt + ".");
            }
            return new WeaponAbility.AttackResult(total, msgs, List.of());
        };
    }

    /**
     * Gravitite Sword: knocks the target back a tile and leaves it floating.
     *
     * <p>No bonus damage. The sword's worth is where it puts things: a tile further off, and
     * slowed by Levitation for the turns it takes to drift back down.
     */
    private static WeaponAbilityHandler launch() {
        WeaponAbilityHandler shove = Abilities.knockbackDirection(1);
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            if (canLift(target)) {
                msgs.addAll(shove.apply(player, target, arena, baseDamage, stats, luckPoints).messages());
                if (levitate(target)) {
                    burst(player, arena, target, ParticleTypes.CLOUD, 14);
                    msgs.add("§b✦ Launched! " + target.getDisplayName() + " floats, Levitating for "
                        + LEVITATION_TURNS + " turns.");
                }
            }
            return new WeaponAbility.AttackResult(baseDamage, msgs, List.of());
        };
    }

    /**
     * Gravitite Axe: throws the target {@value #HURL_TILES} tiles, and it lands hard if
     * something stops it short - a wall, the arena's edge, another body.
     */
    private static WeaponAbilityHandler hurl() {
        WeaponAbilityHandler shove = Abilities.knockbackDirection(HURL_TILES);
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int total = baseDamage;
            if (canLift(target)) {
                com.crackedgames.craftics.core.GridPos from = target.getGridPos();
                msgs.addAll(shove.apply(player, target, arena, baseDamage, stats, luckPoints).messages());
                burst(player, arena, target, ParticleTypes.CLOUD, 14);
                boolean stoppedShort = from.chebyshevDistanceTo(target.getGridPos()) < HURL_TILES;
                if (stoppedShort && target.isAlive()) {
                    int dealt = target.takeDamage(Math.max(1, baseDamage / 4));
                    total += dealt;
                    msgs.add("§b✦ Hurled! " + target.getDisplayName() + " slams down for +" + dealt + ".");
                }
            }
            return new WeaponAbility.AttackResult(total, msgs, List.of());
        };
    }

    /** Flaming Sword and Phoenix Bow: sets the target alight. */
    private static WeaponAbilityHandler ignite() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            if (target.isAlive() && !target.isFireImmune()) {
                target.stackBurning(3, 0);
                if (target.getMobEntity() != null) target.getMobEntity().setFireTicks(3 * 80);
                msgs.add("§6✦ " + target.getDisplayName() + " is set alight!");
            }
            return new WeaponAbility.AttackResult(baseDamage, msgs, List.of());
        };
    }

    /** Lightning Sword: every hit calls a bolt down. Doubled on a Soaked target, like all lightning. */
    private static WeaponAbilityHandler lightningStrike() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int total = baseDamage;
            if (target.isAlive()) {
                bolt(player, arena, target);
                int dealt = target.takeLightningDamage(Math.max(3, baseDamage / 4));
                total += dealt;
                msgs.add("§e✦ Lightning strikes " + target.getDisplayName() + " for +" + dealt + "!");
            }
            return new WeaponAbility.AttackResult(total, msgs, List.of());
        };
    }

    /** Holy Sword: half again as much to the undead. */
    private static WeaponAbilityHandler holy() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int total = baseDamage;
            if (target.isAlive() && PlayerCombatStats.isUndead(target.getEntityTypeId())) {
                int dealt = target.takeDamage(Math.max(3, baseDamage / 2));
                total += dealt;
                burst(player, arena, target, ParticleTypes.END_ROD, 10);
                msgs.add("§f✦ Holy light sears " + target.getDisplayName() + " for +" + dealt + "!");
            }
            return new WeaponAbility.AttackResult(total, msgs, List.of());
        };
    }

    /** Vampire Blade: heals you for a fifth of the hit while you are hurt. */
    private static WeaponAbilityHandler lifedrain() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            if (baseDamage > 0 && player.getHealth() < player.getMaxHealth()) {
                float before = player.getHealth();
                int heal = Math.max(1, (int) Math.round(baseDamage * 0.20));
                player.setHealth(Math.min(player.getMaxHealth(), before + heal));
                int healed = (int) (player.getHealth() - before);
                if (healed > 0) msgs.add("§4✦ The blade drinks. §cYou recover " + healed + ".");
            }
            return new WeaponAbility.AttackResult(baseDamage, msgs, List.of());
        };
    }

    /**
     * Pig Slayer: double damage to pigs, piglins and hoglins. Half that against a boss - the
     * Bastion Brute is a piglin brute, and a sword found in a chest should not halve a boss.
     */
    private static WeaponAbilityHandler pigSlayer() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int total = baseDamage;
            if (target.isAlive() && isPig(target.getEntityTypeId())) {
                int dealt = target.takeDamage(Math.max(1, target.isBoss() ? baseDamage / 2 : baseDamage));
                total += dealt;
                burst(player, arena, target, ParticleTypes.FLAME, 20);
                msgs.add("§c✦ Pig Slayer! " + target.getDisplayName() + " takes +" + dealt + ".");
            }
            return new WeaponAbility.AttackResult(total, msgs, List.of());
        };
    }

    /** Candy Cane Sword: hits knock candy canes loose. */
    private static WeaponAbilityHandler candy() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            Item cane = lookupItem("candy_cane");
            if (cane != null && Math.random() < 0.25 + luckPoints * 0.02) {
                player.getInventory().offerOrDrop(new ItemStack(cane));
                msgs.add("§c✦ A candy cane snaps off!");
            }
            return new WeaponAbility.AttackResult(baseDamage, msgs, List.of());
        };
    }

    /** Cloud Staff: the crystal chills, and hits fire mobs harder. */
    private static WeaponAbilityHandler cloudCrystal() {
        return Abilities.applyEffect(CombatEffects.EffectType.WEAKNESS, 2, 0).and(
            (player, target, arena, baseDamage, stats, luckPoints) -> {
                List<String> msgs = new ArrayList<>();
                int total = baseDamage;
                if (target.isAlive() && isFireMob(target.getEntityTypeId())) {
                    int dealt = target.takeDamage(CLOUD_CRYSTAL_FIRE_BONUS);
                    total += dealt;
                    msgs.add("§b✦ The cloud crystal quenches " + target.getDisplayName()
                        + " for +" + dealt + "!");
                }
                return new WeaponAbility.AttackResult(total, msgs, List.of());
            });
    }

    // ── FX ──

    private static void burst(ServerPlayerEntity player, GridArena arena, CombatEntity target,
                              ParticleEffect particle, int count) {
        if (!(player.getEntityWorld() instanceof ServerWorld sw)) return;
        BlockPos bp = arena.gridToBlockPos(target.getGridPos());
        sw.spawnParticles(particle, bp.getX() + 0.5, bp.getY() + 1.2, bp.getZ() + 0.5,
            count, 0.35, 0.5, 0.35, 0.03);
    }

    private static void bolt(ServerPlayerEntity player, GridArena arena, CombatEntity target) {
        if (!(player.getEntityWorld() instanceof ServerWorld sw)) return;
        BlockPos bp = arena.gridToBlockPos(target.getGridPos());
        ProjectileSpawner.spawnImpact(sw, bp, "lightning");
        SoundEvent thunder = SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER;
        sw.playSound(null, bp, thunder, SoundCategory.PLAYERS, 0.8f, 1.4f);
    }

    // ================================================================
    // Armor sets
    // ================================================================

    /** One affinity half-point per piece, like every other registered set. */
    private static final int PIECE_AFFINITY = 1;

    /**
     * The six sets plus the Sentry Boots. Armor class follows the Aether's own armor values:
     * Zanite and Neptune defend like iron (15), Gravitite, Valkyrie and Phoenix like diamond
     * (20), and Obsidian matches netherite's toughness.
     *
     * <p>Set detection needs no code: {@code ArmorClassTable.armorSetKeyOf} derives the key
     * from the item path. The conditional halves - Zanite hardening, Gravitite's updraft,
     * Valkyrie's footing, Neptune staying dry, Phoenix not burning, Obsidian's tempered plate -
     * are {@link AetherEffects}, attached by {@link AetherScanner}. Every set has one, and no
     * two are the same.
     */
    private static boolean registerArmorSets() {
        boolean any = false;
        // Pet, not the Cleaving it had for mirroring iron. No other armor in the game carries
        // Pet affinity, and Zanite is the crafted, common Aether tier, so a pet build can wear
        // its armor from the first Aether levels.
        any |= set("zanite", DamageType.PET, 4, 0, 0,
            "§5Zanite: §7+2 Pet Power, and it hardens as it is hit: each blow you take"
                + " makes the next deal 1 less, up to 3");
        any |= set("gravitite", DamageType.BLUNT, 6, 1, 0,
            "§dGravitite: §7+1 Speed, and a 25% chance that whatever hits you is sent Levitating");
        any |= set("valkyrie", DamageType.SLASHING, 6, 1, 0,
            "§fValkyrie: §7+1 Speed, and winged: you cannot be knocked back");
        any |= set("neptune", DamageType.WATER, 4, 0, 0,
            "§3Neptune: §7+2 Water Power, and you cannot be Soaked");
        any |= set("phoenix", DamageType.SPECIAL, 6, 0, 0,
            "§6Phoenix: §7+2 Special Power, and you cannot be set Burning");
        any |= set("obsidian", DamageType.PHYSICAL, 7, 0, 1,
            "§8Obsidian: §7+1 Defense, and the first hit you take each round deals half");
        // A set of one piece: registering it is what gives the boots an armor class at all.
        any |= set("sentry", DamageType.BLUNT, 4, 0, 0, "", "sentry_boots");
        return any;
    }

    private static boolean set(String key, DamageType affinity, int armorClass,
                               int speedBonus, int defenseBonus, String description) {
        return set(key, affinity, armorClass, speedBonus, defenseBonus, description, key + "_helmet");
    }

    private static boolean set(String key, DamageType affinity, int armorClass, int speedBonus,
                               int defenseBonus, String description, String probePath) {
        if (lookupItem(probePath) == null) return false;
        ArmorSetEntry.Builder b = ArmorSetEntry.builder(key)
            .damageBonus(affinity, PIECE_AFFINITY)
            .armorClass(armorClass)
            .description(description);
        if (speedBonus != 0) b.speedBonus(speedBonus);
        if (defenseBonus != 0) b.defenseBonus(defenseBonus);
        ArmorSetRegistry.register(b.build());
        return true;
    }

    // ================================================================
    // Consumables and throwables
    // ================================================================

    /** HP an ambrosium shard restores. Small on purpose: it is the Aether's pocket snack. */
    public static final int AMBROSIUM_HEAL = 2;

    /**
     * Items that do something other than be eaten. Aether food (berries, gummy swets, candy
     * canes) needs nothing here: anything with a food component is already usable, healing by
     * its nutrition. These are registered because the registry is consulted before that rule,
     * and each of them either is not food or is food whose point is not the meal.
     */
    private static boolean registerUsables() {
        boolean any = false;
        any |= usable("ambrosium_shard", 1, 0, TargetType.SELF, true, ctx -> {
            ctx.healPlayer(AMBROSIUM_HEAL);
            ctx.message("§e✦ Ambrosium restores " + AMBROSIUM_HEAL + " HP.");
            return ItemUseResult.ok();
        });
        any |= usable("healing_stone", 1, 0, TargetType.SELF, true, ctx -> {
            ctx.applyPlayerEffect(CombatEffects.EffectType.REGENERATION, 3, 0);
            ctx.message("§d✦ The healing stone glows. Regeneration for 3 turns.");
            return ItemUseResult.ok();
        });
        // Remedy: cures the Aether's poison and nothing else. The apple is spent; the bucket
        // comes back empty.
        any |= usable("white_apple", 1, 0, TargetType.SELF, true, remedy());
        any |= usable("skyroot_remedy_bucket", 1, 0, TargetType.SELF, true, remedy().and(ctx -> {
            Item bucket = lookupItem("skyroot_bucket");
            if (bucket != null) ctx.player().getInventory().offerOrDrop(new ItemStack(bucket));
            return ItemUseResult.ok();
        }));
        any |= usable("lightning_knife", 1, THROWN_RANGE, TargetType.SINGLE_ENEMY, true, ctx -> {
            CombatEntity target = ctx.targetEntity();
            if (target == null || !target.isAlive()) return ItemUseResult.fail("§cNo target there.");
            if (ctx.player().getEntityWorld() instanceof ServerWorld sw) {
                BlockPos bp = ctx.arena().gridToBlockPos(target.getGridPos());
                ProjectileSpawner.spawnImpact(sw, bp, "lightning");
                sw.playSound(null, bp, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                    SoundCategory.PLAYERS, 0.8f, 1.4f);
            }
            // The Soaked doubling every lightning source honours, applied before the damage
            // goes through the item pipeline so a kill is still booked as a kill.
            int raw = target.isSoaked() ? LIGHTNING_KNIFE_DAMAGE * 2 : LIGHTNING_KNIFE_DAMAGE;
            int dealt = ctx.damage(target, raw);
            ctx.message("§e✦ The knife calls lightning onto " + target.getDisplayName()
                + " for " + dealt + "!");
            return ItemUseResult.ok();
        });
        return any;
    }

    private static UsableItemHandler remedy() {
        return ctx -> {
            CombatManager cm = CombatManager.getActiveCombat(ctx.player().getUuid());
            boolean cured = cm != null && cm.getCombatEffects().removeEffect(CombatEffects.EffectType.POISON);
            ctx.message(cured ? "§a✦ Remedy! The poison is gone." : "§7You were not poisoned.");
            return ItemUseResult.ok();
        };
    }

    private static boolean usable(String path, int apCost, int range, TargetType targetType,
                                  boolean consumed, UsableItemHandler handler) {
        Item item = lookupItem(path);
        if (item == null) return false;
        UsableItemRegistry.register(item, UsableItemEntry.builder(item)
            .apCost(apCost).range(range).targetType(targetType).consumedOnUse(consumed)
            .handler(handler).build());
        return true;
    }
}
