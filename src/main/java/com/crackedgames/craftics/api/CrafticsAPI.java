package com.crackedgames.craftics.api;

import com.crackedgames.craftics.api.registry.*;
import com.crackedgames.craftics.combat.ai.AIRegistry;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.level.BiomeRegistry;
import com.crackedgames.craftics.level.BiomeTemplate;
import com.crackedgames.craftics.level.EnvironmentStyle;
import net.minecraft.item.Item;

/**
 * Public API for Craftics modding.
 *
 * Other Fabric mods can use this class to:
 * - Register custom biomes (programmatically or via JSON datapacks)
 * - Register custom AI strategies for their mob types
 * - Register custom weapons with damage types, abilities, and stats
 * - Register equipment scanners for addon inventory slots (trinkets, baubles, etc.)
 * - Register custom armor set bonuses
 * - Register custom trim pattern and material effects
 * - Register custom between-level events
 * - Register enchantment stat bonuses
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
 * CrafticsAPI.registerWeapon(myItem, WeaponEntry.builder(myItem)
 *     .damageType(DamageType.SLASHING).attackPower(8).apCost(1).range(1)
 *     .ability(Abilities.bleed().and(Abilities.sweepAdjacent(0.1, 0.05)))
 *     .build());
 * }</pre>
 */
public final class CrafticsAPI {

    private CrafticsAPI() {} // no instances

    // === Existing: AI ===

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

    // === Existing: Biomes ===

    /**
     * Register a custom biome template programmatically.
     * The biome will be inserted into the progression based on its order value.
     *
     * @param template The biome template to register
     */
    public static void registerBiome(BiomeTemplate template) {
        BiomeRegistry.register(template);
    }

    // === New: Weapons ===

    /**
     * Register a weapon with its Craftics combat stats and optional ability.
     * Use {@link WeaponEntry#builder(Item)} for a fluent API.
     *
     * @param item  The weapon item
     * @param entry The weapon's combat data
     */
    public static void registerWeapon(Item item, WeaponEntry entry) {
        WeaponRegistry.register(item, entry);
    }

    // === New: Equipment Scanners ===

    /**
     * Register an equipment scanner that contributes stat bonuses from
     * non-standard inventory slots (trinkets, baubles, curios, etc.).
     * The scanner is called during trim/equipment scanning and its results
     * are merged into the player's combat stats.
     *
     * @param id      Unique scanner ID (e.g., "artifacts")
     * @param scanner Function that scans a player and returns stat modifiers
     */
    public static void registerEquipmentScanner(String id, EquipmentScanner scanner) {
        EquipmentScannerRegistry.register(id, scanner);
    }

    // === New: Armor Sets ===

    /**
     * Register an armor set with damage type bonuses and stat bonuses.
     * The armor set ID should match what {@code PlayerCombatStats.getArmorSet()}
     * returns for your armor material.
     *
     * @param entry The armor set bonus data
     */
    public static void registerArmorSet(ArmorSetEntry entry) {
        ArmorSetRegistry.register(entry);
    }

    // === New: Trim Patterns & Materials ===

    /**
     * Register a trim pattern with per-piece stat bonus and set bonus.
     *
     * @param entry The trim pattern data
     */
    public static void registerTrimPattern(TrimPatternEntry entry) {
        TrimPatternRegistry.register(entry);
    }

    /**
     * Register a trim material with its stat bonus.
     *
     * @param entry The trim material data
     */
    public static void registerTrimMaterial(TrimMaterialEntry entry) {
        TrimMaterialRegistry.register(entry);
    }

    // === New: Events ===

    /**
     * Register a custom between-level event that can occur during biome runs.
     * Events are rolled based on probability after each level.
     *
     * @param entry The event data including handler
     */
    public static void registerEvent(EventEntry entry) {
        EventRegistry.register(entry);
    }

    // === New: Enchantments ===

    /**
     * Register an enchantment that provides passive stat bonuses in Craftics combat.
     * The handler receives the enchantment level and a StatModifiers accumulator.
     *
     * @param enchantmentId Full enchantment ID (e.g., "mymod:holy_blessing")
     * @param handler       Function that applies stat bonuses based on enchantment level
     */
    public static void registerEnchantment(String enchantmentId, EnchantmentEffectHandler handler) {
        EnchantmentRegistry.register(enchantmentId, handler);
    }

    // === Runtime ===

    /**
     * Force a specific event to trigger at the next between-level transition
     * for the given player. Pass null to clear.
     *
     * @param player  The player whose next event to force
     * @param eventId Event ID (e.g., "mymod:enchanted_forge") or null to clear
     */
    public static void forceNextEvent(net.minecraft.server.network.ServerPlayerEntity player, String eventId) {
        com.crackedgames.craftics.combat.CombatManager cm = com.crackedgames.craftics.combat.CombatManager.get(player);
        if (cm != null) {
            cm.setForcedNextEvent(eventId);
        }
    }

    // === Queries ===

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
