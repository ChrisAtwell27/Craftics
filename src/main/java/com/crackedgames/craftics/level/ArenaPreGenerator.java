package com.crackedgames.craftics.level;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.world.CrafticsSavedData;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;

/**
 * Pre-generates all arena variants at their designated positions when a player's
 * personal world is created. This eliminates edge glitching, loading delays, and
 * chunk sync issues by ensuring all arena blocks exist before combat starts.
 */
public class ArenaPreGenerator {

    /**
     * Pre-generate every arena for a player's world slot.
     * Called once when the personal world is created.
     */
    public static void generateAll(ServerWorld world, UUID playerUuid) {
        CrafticsSavedData data = CrafticsSavedData.get(world);
        CrafticsSavedData.PlayerData pd = data.getPlayerData(playerUuid);

        if (pd.worldSlot < 0) {
            CrafticsMod.LOGGER.warn("ArenaPreGenerator: player {} has no world slot, skipping", playerUuid);
            return;
        }

        List<BiomeTemplate> biomes = BiomeRegistry.getAllBiomes();
        if (biomes.isEmpty()) {
            CrafticsMod.LOGGER.warn("ArenaPreGenerator: no biomes registered, skipping");
            return;
        }

        int totalLevels = BiomeRegistry.getTotalLevelCount();
        CrafticsMod.LOGGER.info("ArenaPreGenerator: pre-generating {} levels for player {} (slot {})",
            totalLevels, playerUuid, pd.worldSlot);

        long startTime = System.currentTimeMillis();

        for (int level = 1; level <= totalLevels; level++) {
            try {
                LevelDefinition levelDef = LevelGenerator.generate(level);
                BlockPos origin = data.getArenaOrigin(playerUuid, level);
                if (origin == null) continue;

                GridArena arena = ArenaBuilder.buildAt(world, levelDef, origin);
                if (arena != null) {
                    pd.storeArenaMetadata(level, arena.getOrigin(),
                        arena.getWidth(), arena.getHeight(), arena.getPlayerStart());
                }
                CrafticsMod.LOGGER.debug("ArenaPreGenerator: built level {} at {}", level, origin);
            } catch (Exception e) {
                CrafticsMod.LOGGER.error("ArenaPreGenerator: failed to build level {}", level, e);
            }
        }

        pd.arenasPreGenerated = true;
        data.markDirty();

        long elapsed = System.currentTimeMillis() - startTime;
        CrafticsMod.LOGGER.info("ArenaPreGenerator: finished {} levels in {}ms", totalLevels, elapsed);
    }
}
