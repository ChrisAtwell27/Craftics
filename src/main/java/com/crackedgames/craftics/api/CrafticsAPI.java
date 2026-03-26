package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.ai.AIRegistry;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.level.BiomeRegistry;
import com.crackedgames.craftics.level.BiomeTemplate;
import com.crackedgames.craftics.level.EnvironmentStyle;

/**
 * Public API for Craftics modding.
 *
 * Other Fabric mods can use this class to:
 * - Register custom biomes (programmatically or via JSON datapacks)
 * - Register custom AI strategies for their mob types
 * - Register custom environment styles
 *
 * <h2>Datapack Modding (no code required)</h2>
 * Place JSON files in: {@code data/<your_mod_id>/craftics/biomes/your_biome.json}
 * <p>
 * See the built-in biomes in {@code data/craftics/craftics/biomes/} for examples.
 *
 * <h2>Code Modding (Fabric mod)</h2>
 * <pre>{@code
 * // In your mod initializer:
 * CrafticsAPI.registerAI("mymod:custom_mob", new MyCustomAI());
 * CrafticsAPI.registerBiome(myBiomeTemplate);
 * }</pre>
 */
public final class CrafticsAPI {

    private CrafticsAPI() {} // no instances

    /**
     * Register a custom AI strategy for a mob type.
     * Call this during mod initialization.
     *
     * @param entityTypeId Full entity type ID (e.g., "mymod:custom_zombie")
     * @param ai           The AI strategy to use for this mob type
     */
    public static void registerAI(String entityTypeId, EnemyAI ai) {
        AIRegistry.register(entityTypeId, ai);
    }

    /**
     * Register a custom biome template programmatically.
     * The biome will be inserted into the progression based on its order value.
     *
     * @param template The biome template to register
     */
    public static void registerBiome(BiomeTemplate template) {
        BiomeRegistry.register(template);
    }

    /**
     * Get the total number of levels across all registered biomes.
     */
    public static int getTotalLevels() {
        return BiomeRegistry.getTotalLevelCount();
    }

    /**
     * Check if a custom environment style is registered.
     */
    public static boolean hasEnvironmentStyle(String styleName) {
        try {
            EnvironmentStyle.valueOf(styleName.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
