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
 * Pre-generates all arena variants at their designated positions when a player's
 * personal world is created. This eliminates edge glitching, loading delays, and
 * chunk sync issues by ensuring all arena blocks exist before combat starts.
 *
 * Also provides targeted regeneration — any single biome can be rebuilt without
 * wiping the rest, and corrupted arenas (missing floors, too much void) can be
 * auto-detected and repaired without player intervention.
 */
public class ArenaPreGenerator {

    /** Fraction of arena floor tiles allowed to be void/air before we flag an arena as corrupted. */
    private static final double CORRUPTION_THRESHOLD = 0.25;

    /**
     * Pre-generate every arena for a player's world slot.
     * Called once when the personal world is created.
     */
    public static void generateAll(ServerWorld world, UUID playerUuid) {
        regenerate(world, playerUuid, null);
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
