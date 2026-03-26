package com.crackedgames.craftics.level;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.resource.ResourceManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Registry of all biome templates. Supports both:
 * - JSON datapacks (data/{namespace}/craftics/biomes/*.json)
 * - Programmatic registration via {@link com.crackedgames.craftics.api.CrafticsAPI}
 *
 * Biomes are sorted by their 'order' field (which becomes startLevel).
 * Level numbers are auto-assigned based on order during rebuild.
 */
public class BiomeRegistry {
    private static final List<BiomeTemplate> BIOMES = new ArrayList<>();
    private static boolean needsRebuild = true;

    /**
     * Register a biome template. Can be called from mod init or JSON loader.
     * The template's startLevel field is used as the 'order' for sorting.
     */
    public static void register(BiomeTemplate template) {
        // Remove any existing biome with the same ID (allows overrides)
        BIOMES.removeIf(b -> b.biomeId.equals(template.biomeId));
        BIOMES.add(template);
        needsRebuild = true;
    }

    /**
     * Load biomes from JSON datapacks. Called on server resource reload.
     * JSON biomes override built-in biomes with the same ID.
     */
    public static void loadFromDatapacks(ResourceManager resourceManager) {
        List<BiomeTemplate> jsonBiomes = BiomeJsonLoader.loadFromResources(resourceManager);
        for (BiomeTemplate biome : jsonBiomes) {
            register(biome);
        }
        rebuildLevelNumbers();
        CrafticsMod.LOGGER.info("BiomeRegistry: {} biomes loaded ({} from datapacks)",
            BIOMES.size(), jsonBiomes.size());
    }

    /**
     * Sort biomes by order and reassign contiguous level numbers.
     */
    private static void rebuildLevelNumbers() {
        BIOMES.sort(Comparator.comparingInt(b -> b.startLevel));

        // Reassign startLevel to be contiguous
        int currentLevel = 1;
        for (int i = 0; i < BIOMES.size(); i++) {
            BiomeTemplate old = BIOMES.get(i);
            if (old.startLevel != currentLevel) {
                // Create a copy with the corrected startLevel
                BIOMES.set(i, new BiomeTemplate(
                    old.biomeId, old.displayName, currentLevel, old.levelCount,
                    old.baseWidth, old.baseHeight, old.widthGrowth, old.heightGrowth,
                    old.floorBlocks, old.obstacleBlocks,
                    old.baseObstacleDensity, old.obstacleDensityGrowth,
                    old.passiveMobs, old.hostileMobs, old.boss,
                    old.lootItems, old.lootWeights,
                    old.nightLevel, old.environmentStyle
                ));
            }
            currentLevel += BIOMES.get(i).levelCount;
        }
        needsRebuild = false;
    }

    public static BiomeTemplate getForLevel(int levelNumber) {
        if (needsRebuild) rebuildLevelNumbers();
        for (BiomeTemplate biome : BIOMES) {
            if (biome.containsLevel(levelNumber)) {
                return biome;
            }
        }
        return BIOMES.isEmpty() ? null : BIOMES.get(BIOMES.size() - 1);
    }

    public static int getTotalLevelCount() {
        if (needsRebuild) rebuildLevelNumbers();
        if (BIOMES.isEmpty()) return 0;
        BiomeTemplate last = BIOMES.get(BIOMES.size() - 1);
        return last.getEndLevel();
    }

    public static List<BiomeTemplate> getAllBiomes() {
        if (needsRebuild) rebuildLevelNumbers();
        return BIOMES;
    }

    public static void clear() {
        BIOMES.clear();
        needsRebuild = true;
    }
}
