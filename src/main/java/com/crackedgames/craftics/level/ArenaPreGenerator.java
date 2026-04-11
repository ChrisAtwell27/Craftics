package com.crackedgames.craftics.level;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.world.CrafticsSavedData;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;

/**
 * Manages on-demand arena generation in a player's personal world slot.
 *
 * <h2>Lazy generation</h2>
 * Arenas used to be built eagerly at island creation time — every level, every
 * biome, all placed into the world slot before the player could move. On
 * lower-end servers this froze the main thread for multiple seconds and
 * sometimes crashed the tick loop. The new flow:
 * <ol>
 *   <li>Island creation does zero arena work. {@link #generateAll} is a no-op.</li>
 *   <li>{@link com.crackedgames.craftics.combat.CombatManager#buildArena} calls
 *       {@link #ensureArena} on the way into a fight.</li>
 *   <li>{@code ensureArena} builds only the requested level's arena, stores its
 *       metadata, and returns immediately. Subsequent fights at the same level
 *       hit the cached metadata and skip the build entirely.</li>
 * </ol>
 *
 * <h2>Wipe bypass on fresh slots</h2>
 * {@link #wipeArenaFootprint} is a ≥133,000 block-touch loop per arena. It's
 * needed for rebuilds (to clear old schematic decorations / worldedits) but on
 * a fresh, never-touched slot the volume is already pure air, so the wipe is
 * pure overhead. {@link #ensureArena} passes {@code freshSlot=true} on the
 * first build of a level in a slot and skips the wipe.
 *
 * <h2>Regeneration + corruption repair</h2>
 * Admin paths ({@code /craftics rebuild_arenas}) still use {@link #regenerate}
 * which iterates every level. The corruption auto-repair path still uses
 * {@link #regenerateLevel} which does wipe (corrupted = possibly dirty).
 */
public class ArenaPreGenerator {

    /** Fraction of arena floor tiles allowed to be void/air before we flag an arena as corrupted. */
    private static final double CORRUPTION_THRESHOLD = 0.25;

    /**
     * Legacy entry point. Used to pre-build every arena at island creation.
     * Now a no-op — arenas are built lazily on first combat entry via
     * {@link #ensureArena}. Kept so existing callers don't need to change.
     */
    public static void generateAll(ServerWorld world, UUID playerUuid) {
        CrafticsSavedData data = CrafticsSavedData.get(world);
        CrafticsSavedData.PlayerData pd = data.getPlayerData(playerUuid);
        if (pd.worldSlot < 0) return;
        // Mark the system as "enabled" so CombatManager.buildArena takes the
        // pre-built-metadata path. Actual arenas get built on demand.
        pd.arenasPreGenerated = true;
        data.markDirty();
        CrafticsMod.LOGGER.info(
            "ArenaPreGenerator: lazy mode — no arenas built at creation for slot {} (player {})",
            pd.worldSlot, playerUuid);
    }

    /**
     * Build the requested level's arena if it hasn't been built yet for this
     * player's slot. If metadata already exists, this is a cheap HashMap lookup
     * and returns the cached origin. If not, it performs a single arena build
     * (no wipe on fresh slots) and stores the metadata.
     *
     * @return the stored arena metadata after the call, or {@code null} if the
     *         player has no world slot or the build failed.
     */
    public static int[] ensureArena(ServerWorld world, UUID playerUuid, int level) {
        CrafticsSavedData data = CrafticsSavedData.get(world);
        CrafticsSavedData.PlayerData pd = data.getPlayerData(playerUuid);
        if (pd.worldSlot < 0) return null;

        int[] existing = pd.getArenaMetadata(level);
        if (existing != null) return existing;

        BiomeTemplate biome = BiomeRegistry.getForLevel(level);
        if (biome == null) {
            CrafticsMod.LOGGER.warn("ArenaPreGenerator.ensureArena: no biome for level {}", level);
            return null;
        }

        try {
            LevelDefinition levelDef = LevelGenerator.generate(level);
            BlockPos origin = data.getArenaOrigin(playerUuid, level);
            if (origin == null) return null;

            // Fresh slot: no previous blocks to wipe, just build directly.
            GridArena arena = ArenaBuilder.buildAt(world, levelDef, origin);
            if (arena == null) return null;

            pd.storeArenaMetadata(level, arena.getOrigin(),
                arena.getWidth(), arena.getHeight(), arena.getPlayerStart());
            pd.arenasPreGenerated = true;
            data.markDirty();
            CrafticsMod.LOGGER.debug(
                "ArenaPreGenerator.ensureArena: built level {} ({}) for slot {} lazily",
                level, biome.biomeId, pd.worldSlot);
            return pd.getArenaMetadata(level);
        } catch (Exception e) {
            CrafticsMod.LOGGER.error(
                "ArenaPreGenerator.ensureArena: failed to build level {} for player {}",
                level, playerUuid, e);
            return null;
        }
    }

    /**
     * Rebuild arenas for a player's world slot. If {@code biomeFilter} is null,
     * every arena is rebuilt; otherwise only arenas belonging to that biome id.
     * Returns the number of arenas rebuilt.
     */
    public static int regenerate(ServerWorld world, UUID playerUuid, String biomeFilter) {
        CrafticsSavedData data = CrafticsSavedData.get(world);
        CrafticsSavedData.PlayerData pd = data.getPlayerData(playerUuid);

        if (pd.worldSlot < 0) {
            CrafticsMod.LOGGER.warn("ArenaPreGenerator: player {} has no world slot, skipping", playerUuid);
            return 0;
        }

        List<BiomeTemplate> biomes = BiomeRegistry.getAllBiomes();
        if (biomes.isEmpty()) {
            CrafticsMod.LOGGER.warn("ArenaPreGenerator: no biomes registered, skipping");
            return 0;
        }

        int totalLevels = BiomeRegistry.getTotalLevelCount();
        String scope = biomeFilter != null ? "biome '" + biomeFilter + "'" : "all biomes";
        CrafticsMod.LOGGER.info("ArenaPreGenerator: regenerating {} for player {} (slot {})",
            scope, playerUuid, pd.worldSlot);

        long startTime = System.currentTimeMillis();
        int rebuilt = 0;

        for (int level = 1; level <= totalLevels; level++) {
            BiomeTemplate biome = BiomeRegistry.getForLevel(level);
            if (biome == null) continue;
            if (biomeFilter != null && !biomeFilter.equals(biome.biomeId)) continue;

            try {
                LevelDefinition levelDef = LevelGenerator.generate(level);
                BlockPos origin = data.getArenaOrigin(playerUuid, level);
                if (origin == null) continue;

                wipeArenaFootprint(world, origin, levelDef);

                GridArena arena = ArenaBuilder.buildAt(world, levelDef, origin);
                if (arena != null) {
                    pd.storeArenaMetadata(level, arena.getOrigin(),
                        arena.getWidth(), arena.getHeight(), arena.getPlayerStart());
                    rebuilt++;
                }
                CrafticsMod.LOGGER.debug("ArenaPreGenerator: rebuilt level {} ({}) at {}", level, biome.biomeId, origin);
            } catch (Exception e) {
                CrafticsMod.LOGGER.error("ArenaPreGenerator: failed to rebuild level {}", level, e);
            }
        }

        pd.arenasPreGenerated = true;
        data.markDirty();

        long elapsed = System.currentTimeMillis() - startTime;
        CrafticsMod.LOGGER.info("ArenaPreGenerator: finished {} levels ({}) in {}ms",
            rebuilt, scope, elapsed);
        return rebuilt;
    }

    /**
     * Rebuild a single arena for a player (used by corruption auto-repair).
     * Returns true on success.
     */
    public static boolean regenerateLevel(ServerWorld world, UUID playerUuid, int level) {
        CrafticsSavedData data = CrafticsSavedData.get(world);
        CrafticsSavedData.PlayerData pd = data.getPlayerData(playerUuid);
        if (pd.worldSlot < 0) return false;

        try {
            LevelDefinition levelDef = LevelGenerator.generate(level);
            BlockPos origin = data.getArenaOrigin(playerUuid, level);
            if (origin == null) return false;

            wipeArenaFootprint(world, origin, levelDef);

            GridArena arena = ArenaBuilder.buildAt(world, levelDef, origin);
            if (arena != null) {
                pd.storeArenaMetadata(level, arena.getOrigin(),
                    arena.getWidth(), arena.getHeight(), arena.getPlayerStart());
                data.markDirty();
                CrafticsMod.LOGGER.info("ArenaPreGenerator: auto-repaired level {}", level);
                return true;
            }
        } catch (Exception e) {
            CrafticsMod.LOGGER.error("ArenaPreGenerator: failed to repair level {}", level, e);
        }
        return false;
    }

    /**
     * Check a stored arena for corruption. Looks at the recorded footprint and
     * counts how many tiles on the expected floor Y are air or have a hazardous
     * void directly below (lava / fluid). If that fraction exceeds
     * {@link #CORRUPTION_THRESHOLD} the arena is considered corrupted.
     *
     * Returns true if corrupted, false otherwise (or if no metadata).
     */
    public static boolean isCorrupted(ServerWorld world, UUID playerUuid, int level) {
        CrafticsSavedData data = CrafticsSavedData.get(world);
        CrafticsSavedData.PlayerData pd = data.getPlayerData(playerUuid);
        int[] meta = pd.getArenaMetadata(level);
        if (meta == null) return false;

        int ox = meta[0], oy = meta[1], oz = meta[2];
        int w = meta[3], h = meta[4];
        if (w <= 0 || h <= 0) return true;

        int voidTiles = 0;
        int total = w * h;
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                BlockPos floorPos = new BlockPos(ox + x, oy, oz + z);
                BlockState floor = world.getBlockState(floorPos);
                // Air at floor Y is a void tile. Fluid at floor Y (lava/water)
                // is fine — it's a hazard, not a hole.
                if (floor.isAir()) {
                    BlockPos belowPos = new BlockPos(ox + x, oy - 1, oz + z);
                    BlockState below = world.getBlockState(belowPos);
                    // Count as void only if below is empty too — a shallow pit
                    // (LOW_GROUND) with solid ground 1 block down is fine.
                    if (below.isAir() || !below.getFluidState().isEmpty()) {
                        voidTiles++;
                    }
                }
            }
        }

        double ratio = (double) voidTiles / total;
        if (ratio >= CORRUPTION_THRESHOLD) {
            CrafticsMod.LOGGER.warn("ArenaPreGenerator: level {} looks corrupted — {}/{} void tiles ({}%)",
                level, voidTiles, total, (int)(ratio * 100));
            return true;
        }
        return false;
    }

    /**
     * Wipe a generous volume around an arena's footprint so the next build
     * starts clean. Uses the bigger of the level definition's requested size
     * and a 48x48 minimum — that way structure presets with larger footprints
     * than the grid still get their decorations cleared.
     */
    private static void wipeArenaFootprint(ServerWorld world, BlockPos origin, LevelDefinition def) {
        int w = Math.max(48, def.getWidth() + 16);
        int h = Math.max(48, def.getHeight() + 16);
        int ox = origin.getX() - 8;
        int oz = origin.getZ() - 8;
        int yMin = origin.getY() - 10;
        int yMax = origin.getY() + 48;

        net.minecraft.block.BlockState air = Blocks.AIR.getDefaultState();
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                for (int y = yMin; y <= yMax; y++) {
                    BlockPos bp = new BlockPos(ox + x, y, oz + z);
                    if (!world.getBlockState(bp).isAir()) {
                        world.setBlockState(bp, air,
                            net.minecraft.block.Block.FORCE_STATE);
                    }
                }
            }
        }
    }
}
