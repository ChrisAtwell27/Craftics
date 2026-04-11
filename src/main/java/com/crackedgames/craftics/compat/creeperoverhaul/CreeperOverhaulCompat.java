package com.crackedgames.craftics.compat.creeperoverhaul;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.MobThemeTags;
import com.crackedgames.craftics.combat.ai.AIRegistry;
import com.crackedgames.craftics.combat.ai.CreeperAI;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.compat.BiomeCompatHelper;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Compatibility module for the Creeper Overhaul mod.
 * <p>
 * Adds 12 biome-matching creeper variants to the Craftics biome pools. Each
 * variant has unique mechanical behaviour driven by a shared
 * {@link VariantCreeperAI} config — blast effects, fuse length, camouflage,
 * extra speed, etc. — instead of individual AI subclasses.
 * <p>
 * Variant-to-biome mapping (all 18 Craftics biomes considered):
 * <pre>
 *   desert       → desert_creeper      (short fuse, BLINDNESS on blast)
 *   jungle       → jungle_creeper      (camouflage + POISON tag)
 *   jungle       → bamboo_creeper      (camouflage + POISON tag, stacked)
 *   cave         → cave_creeper        (BLINDNESS + MINING_FATIGUE on blast)
 *   deep_dark    → cave_creeper        (same)
 *   cave         → dripstone_creeper   (piercing — +1 base damage)
 *   snowy        → snowy_creeper       (SLOWNESS on blast + WEAKNESS tag)
 *   mountain     → hills_creeper       (+1 speed, larger blast radius)
 *   forest       → dark_oak_creeper    (tanky +2 HP +1 DEF, vanilla behaviour)
 *   plains       → plains_creeper      (weaker starter variant)
 *   river        → beach_creeper       (3-tile knockback, SOAKED tag)
 *   river        → ocean_creeper       (underwater — larger blast, SOAKED tag)
 *   chorus_grove → mushroom_creeper    (fungal — POISON on blast)
 * </pre>
 * <p>
 * Biomes not currently in Craftics (badlands, savannah, spruce, swamp) are
 * left out — their variants would have no biome home. Those four still get
 * their AI entries registered so any future datapack / custom biome that
 * references them will work without a crash.
 */
public final class CreeperOverhaulCompat {

    public static final String MOD_ID = "creeperoverhaul";

    private static boolean loaded = false;
    private static boolean aiRegistered = false;

    private CreeperOverhaulCompat() {}

    // === Variant AI instances ===
    // Each Config captures the variant's entire behaviour profile. Declared as
    // static finals so they're shared by every spawn of that variant.

    private static final EnemyAI DESERT_AI = new VariantCreeperAI(
        VariantCreeperAI.of("desert_creeper")
            .shortFuse()
            .radius(1, 2)
            .blast(CombatEffects.EffectType.BLINDNESS, 2));

    private static final EnemyAI JUNGLE_AI = new VariantCreeperAI(
        VariantCreeperAI.of("jungle_creeper")
            .camouflage(3)
            .blast(CombatEffects.EffectType.POISON, 3));

    private static final EnemyAI BAMBOO_AI = new VariantCreeperAI(
        VariantCreeperAI.of("bamboo_creeper")
            .camouflage(4)
            .blast(CombatEffects.EffectType.POISON, 3, 1));

    private static final EnemyAI CAVE_AI = new VariantCreeperAI(
        VariantCreeperAI.of("cave_creeper")
            .blast(CombatEffects.EffectType.BLINDNESS, 4)
            .blast(CombatEffects.EffectType.MINING_FATIGUE, 2));

    private static final EnemyAI DRIPSTONE_AI = new VariantCreeperAI(
        VariantCreeperAI.of("dripstone_creeper")
            .damageBonus(5)                  // piercing stalactite chunks
            .radius(1, 2));

    private static final EnemyAI SNOWY_AI = new VariantCreeperAI(
        VariantCreeperAI.of("snowy_creeper")
            .blast(CombatEffects.EffectType.SLOWNESS, 2));

    private static final EnemyAI HILLS_AI = new VariantCreeperAI(
        VariantCreeperAI.of("hills_creeper")
            .extraSpeed(1)
            .radius(2, 3));

    private static final EnemyAI DARK_OAK_AI = new VariantCreeperAI(
        VariantCreeperAI.of("dark_oak_creeper")
            .damageBonus(4));                // slightly heavier hit

    private static final EnemyAI PLAINS_AI = new VariantCreeperAI(
        VariantCreeperAI.of("plains_creeper")
            .damageBonus(2)                  // weaker starter variant
            .radius(1, 1));

    private static final EnemyAI BEACH_AI = new VariantCreeperAI(
        VariantCreeperAI.of("beach_creeper")
            .blast(CombatEffects.EffectType.SOAKED, 3));

    private static final EnemyAI OCEAN_AI = new VariantCreeperAI(
        VariantCreeperAI.of("ocean_creeper")
            .radius(2, 3)
            .blast(CombatEffects.EffectType.SOAKED, 4));

    private static final EnemyAI MUSHROOM_AI = new VariantCreeperAI(
        VariantCreeperAI.of("mushroom_creeper")
            .radius(2, 2)
            .blast(CombatEffects.EffectType.POISON, 4));

    /** Variants with no matching biome yet — fall back to vanilla CreeperAI. */
    private static final String[] UNASSIGNED_VARIANTS = {
        "badlands_creeper", "birch_creeper", "savannah_creeper", "spruce_creeper", "swamp_creeper",
    };

    /**
     * Called from mod init to register variant AI entries. Safe to call whether
     * the mod is loaded or not — registry entries always go in so addon code
     * referencing these ids by name has a reliable fallback.
     */
    public static void init() {
        if (aiRegistered) return;
        aiRegistered = true;

        // Biome-matched variants → per-variant AI
        AIRegistry.register(MOD_ID + ":desert_creeper",    DESERT_AI);
        AIRegistry.register(MOD_ID + ":jungle_creeper",    JUNGLE_AI);
        AIRegistry.register(MOD_ID + ":bamboo_creeper",    BAMBOO_AI);
        AIRegistry.register(MOD_ID + ":cave_creeper",      CAVE_AI);
        AIRegistry.register(MOD_ID + ":dripstone_creeper", DRIPSTONE_AI);
        AIRegistry.register(MOD_ID + ":snowy_creeper",     SNOWY_AI);
        AIRegistry.register(MOD_ID + ":hills_creeper",     HILLS_AI);
        AIRegistry.register(MOD_ID + ":dark_oak_creeper",  DARK_OAK_AI);
        AIRegistry.register(MOD_ID + ":plains_creeper",    PLAINS_AI);
        AIRegistry.register(MOD_ID + ":beach_creeper",     BEACH_AI);
        AIRegistry.register(MOD_ID + ":ocean_creeper",     OCEAN_AI);
        AIRegistry.register(MOD_ID + ":mushroom_creeper",  MUSHROOM_AI);

        // Unmatched variants → vanilla fallback so datapacks referencing them
        // by name don't crash. They simply never appear in Craftics combats.
        CreeperAI fallback = new CreeperAI();
        for (String path : UNASSIGNED_VARIANTS) {
            AIRegistry.register(MOD_ID + ":" + path, fallback);
        }

        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            CrafticsMod.LOGGER.debug(
                "[Craftics × Creeper Overhaul] mod not loaded — AI entries registered for any future use");
            return;
        }
        loaded = true;
        CrafticsMod.LOGGER.info(
            "[Craftics × Creeper Overhaul] enabled — 12 biome-matched variants wired");
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * Patch the biome pool. Must run after every
     * {@code BiomeRegistry.loadFromDatapacks} — CrafticsMod calls this from
     * both the {@code SERVER_STARTED} hook and the
     * {@code END_DATA_PACK_RELOAD} hook.
     */
    public static void applyBiomeOverrides() {
        if (!loaded) return;

        // Biomes that already have a vanilla creeper → swap for themed variant.
        // Preserves the original weight/stats, so difficulty stays consistent.
        BiomeCompatHelper.replaceHostileMob("desert",    "minecraft:creeper", MOD_ID + ":desert_creeper");
        BiomeCompatHelper.replaceHostileMob("jungle",    "minecraft:creeper", MOD_ID + ":jungle_creeper");
        BiomeCompatHelper.replaceHostileMob("cave",      "minecraft:creeper", MOD_ID + ":cave_creeper");
        BiomeCompatHelper.replaceHostileMob("deep_dark", "minecraft:creeper", MOD_ID + ":cave_creeper");

        // Additional themed variants appended to existing biomes. Stats mirror
        // the vanilla creeper entry (weight 3, 8 HP, 3 atk, 0 def, 1 range)
        // unless a variant explicitly deserves different numbers.
        BiomeCompatHelper.appendHostileMob("snowy",      MOD_ID + ":snowy_creeper",     3, 8, 3, 0, 1);
        BiomeCompatHelper.appendHostileMob("mountain",   MOD_ID + ":hills_creeper",     3, 8, 3, 0, 1);
        BiomeCompatHelper.appendHostileMob("forest",     MOD_ID + ":dark_oak_creeper",  3, 10, 3, 1, 1); // +2 HP, +1 DEF
        BiomeCompatHelper.appendHostileMob("plains",     MOD_ID + ":plains_creeper",    3, 7, 2, 0, 1);  // weaker
        BiomeCompatHelper.appendHostileMob("river",      MOD_ID + ":beach_creeper",     3, 8, 3, 0, 1);
        BiomeCompatHelper.appendHostileMob("jungle",     MOD_ID + ":bamboo_creeper",    3, 8, 3, 0, 1);
        BiomeCompatHelper.appendHostileMob("cave",       MOD_ID + ":dripstone_creeper", 3, 9, 3, 0, 1);  // +1 HP
        BiomeCompatHelper.appendHostileMob("river",      MOD_ID + ":ocean_creeper",     3, 8, 3, 0, 1);
        BiomeCompatHelper.appendHostileMob("chorus_grove", MOD_ID + ":mushroom_creeper",3, 8, 3, 0, 1);

        // Theme tags — "water / jungle / cold" enemies automatically apply
        // SOAKED / POISON / WEAKNESS on hit via MobThemeTags.applyOnHitEffect
        // from CombatManager.damagePlayer. These stack with the explicit
        // blast effects in each variant's AI config.
        MobThemeTags.addWaterMob(MOD_ID + ":ocean_creeper");
        MobThemeTags.addWaterMob(MOD_ID + ":beach_creeper");
        MobThemeTags.addWaterMob(MOD_ID + ":swamp_creeper");
        MobThemeTags.addJungleMob(MOD_ID + ":jungle_creeper");
        MobThemeTags.addJungleMob(MOD_ID + ":bamboo_creeper");
        MobThemeTags.addColdMob(MOD_ID + ":snowy_creeper");
        MobThemeTags.addColdMob(MOD_ID + ":hills_creeper");
    }
}
