package com.crackedgames.craftics.compat.deeperanddarker;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.combat.MobThemeTags;
import com.crackedgames.craftics.combat.ai.AIRegistry;
import com.crackedgames.craftics.combat.ai.SilverfishAI;
import com.crackedgames.craftics.combat.ai.SlimeAI;
import com.crackedgames.craftics.combat.ai.ZombieAI;
import com.crackedgames.craftics.combat.ai.deeperanddarker.AnglerFishAI;
import com.crackedgames.craftics.combat.ai.deeperanddarker.SculkCentipedeAI;
import com.crackedgames.craftics.combat.ai.deeperanddarker.ShriekWormAI;
import com.crackedgames.craftics.combat.ai.deeperanddarker.StalkerAI;
import com.crackedgames.craftics.compat.BiomeCompatHelper;
import com.crackedgames.craftics.core.TileType;
import com.crackedgames.craftics.level.MobPoolEntry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
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

    /**
     * Full-replace the deep_dark hostile pool with the D&D roster. Sludge and
     * Angler are intentionally LEFT OUT of the general pool - they are placed
     * only in the Blooming Caverns arena via the per-arena spawn gate (see
     * {@code DeeperAndDarkerBloomingSpawns}), because the flat biome pool would
     * otherwise scatter them across every sub-area. Called after the other
     * compat modules so this wholesale swap wins.
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
        });
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
        return null;
    }
}
