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

        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            CrafticsMod.LOGGER.debug(
                "[Craftics × Pale Garden Backport] mod not loaded — AI registered for any future use");
            return;
        }
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
}
