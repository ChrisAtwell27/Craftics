package com.crackedgames.craftics.level;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.level.campaign.CampaignManager;
import net.minecraft.resource.ResourceManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of all biome templates.
 * Add biomes via JSON datapacks (data/{namespace}/craftics/biomes/*.json)
 * or programmatically via CrafticsAPI.registerBiome().
 *
 * <p>Global level numbers are assigned on rebuild in the order dictated by the
 * <em>active campaign</em> ({@link CampaignManager#orderedBiomeIds(int)} for branch 0),
 * which is the single source of biome sequence. Biomes present in the active campaign are
 * numbered first, in campaign order, starting at level 1. Biomes that are NOT part of the
 * active campaign (full-replace semantics: they aren't part of the run) are excluded from the
 * playable sequence — they are sorted AFTER all in-campaign biomes (ordered among themselves
 * by their old {@code startLevel} for determinism) so they still exist in the registry (no
 * stray reference NPEs, {@code getAllBiomes} still returns them) but sit beyond the campaign's
 * level range. If there is no active campaign (empty order — defensive, shouldn't happen
 * post-init), numbering falls back to the legacy {@code startLevel} sort.
 */
public class BiomeRegistry {
    private static final List<BiomeTemplate> BIOMES = new ArrayList<>();
    private static boolean needsRebuild = true;

    /** Register a biome. Same-ID entries get replaced (so datapacks can override built-ins) */
    public static void register(BiomeTemplate template) {
        BIOMES.removeIf(b -> b.biomeId.equals(template.biomeId));
        BIOMES.add(template);
        needsRebuild = true;
    }

    /** Load biomes from JSON datapacks (called on server start and /reload) */
    public static void loadFromDatapacks(ResourceManager resourceManager) {
        List<BiomeTemplate> jsonBiomes = BiomeJsonLoader.loadFromResources(resourceManager);
        for (BiomeTemplate biome : jsonBiomes) {
            register(biome);
        }
        rebuildLevelNumbers();
        CrafticsMod.LOGGER.info("BiomeRegistry: {} biomes loaded ({} from datapacks)",
            BIOMES.size(), jsonBiomes.size());
    }

    private static void rebuildLevelNumbers() {
        // Order biomes by their position in the active campaign (branch 0 = canonical/unswapped;
        // global level NUMBERS are branch-independent). In-campaign biomes come first in campaign
        // order; out-of-campaign biomes are excluded from the playable run and sorted last.
        List<String> campaignOrder = CampaignManager.orderedBiomeIds(0);

        if (campaignOrder.isEmpty()) {
            // Defensive fallback (no active campaign — shouldn't happen post-init): keep the
            // legacy behaviour of ordering by the JSON 'order' loaded into startLevel.
            BIOMES.sort(Comparator.comparingInt(b -> b.startLevel));
        } else {
            Map<String, Integer> campaignIndex = new HashMap<>();
            for (int i = 0; i < campaignOrder.size(); i++) {
                campaignIndex.putIfAbsent(campaignOrder.get(i), i);
            }
            // In-campaign biomes first (by campaign position), then out-of-campaign biomes
            // (ordered among themselves by old startLevel for determinism). Comparator sentinel
            // Integer.MAX_VALUE pushes any biome absent from the campaign to the end.
            BIOMES.sort(Comparator
                .comparingInt((BiomeTemplate b) -> campaignIndex.getOrDefault(b.biomeId, Integer.MAX_VALUE))
                .thenComparingInt(b -> b.startLevel));
        }

        int currentLevel = 1;
        for (int i = 0; i < BIOMES.size(); i++) {
            BiomeTemplate old = BIOMES.get(i);
            if (old.startLevel != currentLevel) {
                BIOMES.set(i, new BiomeTemplate(
                    old.biomeId, old.displayName, currentLevel, old.levelCount,
                    old.baseWidth, old.baseHeight, old.widthGrowth, old.heightGrowth,
                    old.floorBlocks, old.obstacleBlocks,
                    old.baseObstacleDensity, old.obstacleDensityGrowth,
                    old.passiveMobs, old.hostileMobs, old.boss,
                    old.lootItems, old.lootWeights,
                    old.enchantmentLootIds, old.enchantmentLootWeights,
                    old.nightLevel, old.environmentId
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
