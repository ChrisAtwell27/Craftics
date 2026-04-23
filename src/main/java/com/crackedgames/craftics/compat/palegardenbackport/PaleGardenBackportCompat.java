package com.crackedgames.craftics.compat.palegardenbackport;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.combat.ai.AIRegistry;
import com.crackedgames.craftics.combat.ai.CreakingAI;
import com.crackedgames.craftics.combat.ai.CreakingHeartAI;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Compatibility module for the Pale Garden Backport mod (DanBrown_).
 * <p>
 * On 1.21.4+ Minecraft has its own {@code minecraft:creaking} entity and
 * {@code minecraft:creaking_heart} block, and the Pale Garden sub-biome already
 * spawns from {@link com.crackedgames.craftics.level.LevelGenerator}. On
 * 1.21.1 / 1.21.3 the mod brings the same content under the
 * {@code palegardenbackport:} namespace; this compat layer makes the existing
 * Pale Garden encounter use the modded entity/block transparently so the
 * experience matches 1.21.4 exactly.
 * <p>
 * Hooks consumed elsewhere:
 * <ul>
 *   <li>{@link com.crackedgames.craftics.combat.ai.AIRegistry} — registers
 *       {@code palegardenbackport:creaking} pointing at {@link CreakingAI}
 *       (always, even when the mod isn't loaded, so the AI is ready if the
 *       entity ever appears).</li>
 *   <li>{@link com.crackedgames.craftics.level.LevelGenerator} — calls
 *       {@link #shouldSpawnPaleGarden()} / {@link #creakingEntityId()} /
 *       {@link #paleMossBlock()} when building the forest midpoint level.</li>
 *   <li>{@link com.crackedgames.craftics.combat.CombatManager} — calls
 *       {@link #isCreakingEntity(String)} for invulnerability/freeze checks
 *       and {@link #creakingHeartBlock()} when placing the heart in-world.</li>
 * </ul>
 */
public final class PaleGardenBackportCompat {

    public static final String MOD_ID = "palegardenbackport";

    private static boolean loaded = false;
    private static boolean aiRegistered = false;

    private PaleGardenBackportCompat() {}

    public static boolean isLoaded() {
        return loaded;
    }

    public static void init() {
        if (aiRegistered) return;
        aiRegistered = true;

        // Register modded entity AI regardless of mod presence so the registry is
        // ready if the entity ever shows up (mirrors the V&V compat pattern).
        AIRegistry.register("palegardenbackport:creaking", new CreakingAI());
        // Heart AI shares the same id across versions (craftics:creaking_heart) and
        // is registered in AIRegistry's static block already.

        boolean modLoaded = FabricLoader.getInstance().isModLoaded(MOD_ID);
        CrafticsMod.LOGGER.info(
            "[Craftics × Pale Garden Backport] init() — FabricLoader.isModLoaded({}) = {}", MOD_ID, modLoaded);
        if (!modLoaded) return;
        loaded = true;
        CrafticsMod.LOGGER.info(
            "[Craftics × Pale Garden Backport] enabled — pale garden uses modded creaking entity");
    }

    /** True when a creaking entity (vanilla or backport) is available to spawn. */
    public static boolean shouldSpawnPaleGarden() {
        //? if >=1.21.4 {
        /*return true;
        *///?} else {
        return loaded;
        //?}
    }

    /** Returns the entity id to spawn for the Pale Garden creaking. */
    public static String creakingEntityId() {
        //? if >=1.21.4 {
        /*// Prefer vanilla creaking on 1.21.4+ so existing AI/loot keys keep matching.
        return "minecraft:creaking";
        *///?} else {
        return "palegardenbackport:creaking";
        //?}
    }

    /** True if the given entity type id is any flavor of "creaking". */
    public static boolean isCreakingEntity(String entityTypeId) {
        return "minecraft:creaking".equals(entityTypeId)
            || "palegardenbackport:creaking".equals(entityTypeId);
    }

    /** Pale moss block for the Pale Garden floor — falls back to moss block then podzol. */
    public static Block paleMossBlock() {
        //? if >=1.21.4 {
        /*return Blocks.PALE_MOSS_BLOCK;
        *///?} else {
        Block b = lookupBlock("palegardenbackport", "pale_moss_block");
        if (b != null && b != Blocks.AIR) return b;
        return Blocks.MOSS_BLOCK;
        //?}
    }

    /** Block placed at the heart's world position (visual only — entity is the actual target). */
    public static Block creakingHeartBlock() {
        //? if >=1.21.4 {
        /*return Blocks.CREAKING_HEART;
        *///?} else {
        Block b = lookupBlock("palegardenbackport", "creaking_heart");
        if (b != null && b != Blocks.AIR) return b;
        return Blocks.OAK_LOG;
        //?}
    }

    @SuppressWarnings("unused") // referenced from version-conditional branches above
    private static Block lookupBlock(String namespace, String path) {
        try {
            Identifier id = Identifier.of(namespace, path);
            if (!Registries.BLOCK.containsId(id)) return null;
            return Registries.BLOCK.get(id);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Identifier of the "pale garden" overworld biome, dependent on what's
     * actually present at runtime: vanilla on 1.21.4+, the backport mod's
     * version on 1.21.1.
     */
    public static Identifier paleGardenBiomeId() {
        //? if >=1.21.4 {
        /*return Identifier.of("minecraft", "pale_garden");
        *///?} else {
        return Identifier.of("palegardenbackport", "pale_garden");
        //?}
    }

    /**
     * Remap a {@code minecraft:}-namespaced pale-garden block id to its
     * {@code palegardenbackport:} equivalent when the backport mod is loaded
     * and the vanilla id isn't registered. Used by {@code SchemLoader} so the
     * existing pale_garden.schem (built against vanilla 1.21.4 ids) loads
     * correctly on 1.21.1 with the backport mod.
     *
     * @param fullId block id like {@code "minecraft:pale_oak_log"} (may include
     *               trailing {@code [props]} — those are handled by the caller)
     * @return remapped id, or the original if no remap applies
     */
    public static String remapBlockId(String fullId) {
        if (!loaded || fullId == null) return fullId;
        if (!fullId.startsWith("minecraft:")) return fullId;
        String path = fullId.substring("minecraft:".length());
        if (!isPaleGardenPath(path)) return fullId;
        // Skip remap if the vanilla id is somehow already registered (e.g. on 1.21.4+).
        try {
            Identifier vanillaId = Identifier.of("minecraft", path);
            if (Registries.BLOCK.containsId(vanillaId)) return fullId;
            Identifier modId = Identifier.of("palegardenbackport", path);
            if (Registries.BLOCK.containsId(modId)) return modId.toString();
        } catch (Throwable ignored) {}
        return fullId;
    }

    /** True if the block path looks like something the backport mod adds. */
    private static boolean isPaleGardenPath(String path) {
        return path.contains("pale_")
            || path.contains("resin")
            || path.equals("creaking_heart")
            || path.contains("eyeblossom");
    }
}
