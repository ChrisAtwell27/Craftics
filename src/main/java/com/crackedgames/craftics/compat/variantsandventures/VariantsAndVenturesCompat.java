package com.crackedgames.craftics.compat.variantsandventures;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.combat.MobThemeTags;
import com.crackedgames.craftics.combat.ai.AIRegistry;
import com.crackedgames.craftics.combat.ai.SkeletonAI;
import com.crackedgames.craftics.combat.ai.ZombieAI;
import com.crackedgames.craftics.compat.BiomeCompatHelper;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Compatibility module for the Variants & Ventures mod.
 * <p>
 * Adds four new hostile mobs to the appropriate Craftics biomes:
 * <ul>
 *   <li><b>Gelid</b> — frozen zombie (ZombieAI fallback). Added to snowy and mountain.</li>
 *   <li><b>Thicket</b> — poison zombie (ZombieAI fallback). Added to jungle.</li>
 *   <li><b>Verdant</b> — jungle skeleton (SkeletonAI fallback). Added to jungle and forest.</li>
 *   <li><b>Murk</b> — river skeleton with shears (SkeletonAI fallback). Added to river.</li>
 * </ul>
 * <p>
 * Base stats mirror the vanilla mob the variant derives from so difficulty
 * stays consistent. Per-variant abilities (freeze, poison, shear cleave, etc.)
 * can be layered on later through the existing combat effect hooks.
 */
public final class VariantsAndVenturesCompat {

    public static final String MOD_ID = "variantsandventures";

    private static boolean loaded = false;
    private static boolean aiRegistered = false;

    private VariantsAndVenturesCompat() {}

    // Canonical entity id paths pulled from the mod's lang file.
    private static final String GELID   = MOD_ID + ":gelid";
    private static final String THICKET = MOD_ID + ":thicket";
    private static final String VERDANT = MOD_ID + ":verdant";
    private static final String MURK    = MOD_ID + ":murk";

    /**
     * Register AI fallbacks and mark loaded. Safe to call whether the mod is
     * present or not — AI registry entries always go in so datapacks/addons
     * referencing these ids by name have a reliable fallback.
     */
    public static void init() {
        if (aiRegistered) return;
        aiRegistered = true;

        ZombieAI zombieAi = new ZombieAI();
        SkeletonAI skeletonAi = new SkeletonAI();

        // Gelid + Thicket share zombie behaviour (melee walkers).
        AIRegistry.register(GELID,   zombieAi);
        AIRegistry.register(THICKET, zombieAi);

        // Verdant + Murk share skeleton behaviour (ranged kiters).
        AIRegistry.register(VERDANT, skeletonAi);
        AIRegistry.register(MURK,    skeletonAi);

        // Theme tags — register regardless of whether the mod is loaded so
        // the on-hit effect fires the moment a Gelid/Thicket/Murk actually
        // shows up in combat. MobThemeTags.applyOnHitEffect no-ops for
        // untagged attackers, so tagging an entity id that never spawns is
        // free.
        MobThemeTags.addColdMob(GELID);       // frozen zombie → WEAKNESS
        MobThemeTags.addJungleMob(THICKET);   // poison zombie → POISON
        MobThemeTags.addJungleMob(VERDANT);   // jungle skeleton → POISON
        MobThemeTags.addWaterMob(MURK);       // river skeleton → SOAKED

        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            CrafticsMod.LOGGER.debug(
                "[Craftics × Variants & Ventures] mod not loaded — AI entries registered for any future use");
            return;
        }
        loaded = true;
        CrafticsMod.LOGGER.info(
            "[Craftics × Variants & Ventures] enabled — registered 4 variant AI entries");
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * Adds the V&V variants to their themed biome hostile pools. Runs after
     * {@code BiomeRegistry.loadFromDatapacks}.
     */
    public static void applyBiomeOverrides() {
        if (!loaded) return;

        // Gelid — frozen zombie. Base stats match the vanilla zombie entry in
        // the snowy biome pool (hp 8, atk 2, def 0, range 1).
        BiomeCompatHelper.appendHostileMob("snowy",    GELID, 5, 8, 2, 0, 1);
        BiomeCompatHelper.appendHostileMob("mountain", GELID, 4, 8, 2, 0, 1);

        // Thicket — poison zombie. Slightly higher base attack than regular zombie
        // to make room for the poison mechanic we'll add later.
        BiomeCompatHelper.appendHostileMob("jungle",   THICKET, 5, 8, 3, 0, 1);

        // Verdant — jungle skeleton. Ranged kiter, range 3 like normal skeletons.
        BiomeCompatHelper.appendHostileMob("jungle",   VERDANT, 4, 6, 2, 0, 3);
        BiomeCompatHelper.appendHostileMob("forest",   VERDANT, 4, 6, 2, 0, 3);

        // Murk — river skeleton with shears. Hits harder in melee since it
        // carries a short-range weapon.
        BiomeCompatHelper.appendHostileMob("river",    MURK,    5, 7, 3, 0, 2);
    }
}
