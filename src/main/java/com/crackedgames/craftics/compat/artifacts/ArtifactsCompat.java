package com.crackedgames.craftics.compat.artifacts;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.CrafticsAPI;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Compatibility layer for the Artifacts mod (ochotonida's Artifacts).
 * <p>
 * Registers an {@link com.crackedgames.craftics.api.EquipmentScanner} that reads the
 * player's equipped artifacts via reflection against {@code artifacts.equipment.EquipmentHelper}
 * so we don't need a compile-time dependency. When the Artifacts mod isn't present, this
 * whole subsystem is a no-op and costs nothing.
 * <p>
 * Each supported artifact contributes one or more of:
 *   - passive stat bonuses (Power, Speed, Defense, Max HP, ...)
 *   - a {@link com.crackedgames.craftics.api.CombatEffectHandler} for active combat behavior
 * <p>
 * See {@code docs/artifact-abilities.md} for the canonical list of abilities.
 */
public final class ArtifactsCompat {

    public static final String MOD_ID = "artifacts";
    public static final String SCANNER_ID = "craftics:artifacts_compat";

    private static boolean loaded = false;

    private ArtifactsCompat() {}

    /** Called from {@link CrafticsMod#onInitialize()}. Silently no-ops if Artifacts isn't installed. */
    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return;
        }
        if (!ArtifactsReflect.isAvailable()) {
            CrafticsMod.LOGGER.warn(
                "[Craftics × Artifacts] Artifacts mod is loaded but EquipmentHelper could not be resolved "
                + "— artifact combat effects will be disabled.");
            return;
        }
        CrafticsAPI.registerEquipmentScanner(SCANNER_ID, new ArtifactsScanner());
        AbandonedCampsiteEvent.register();
        loaded = true;
        CrafticsMod.LOGGER.info("[Craftics × Artifacts] Artifact compatibility enabled.");
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * Returns true if the given player currently has an equipped Artifacts item with
     * the given path (e.g. {@code "helium_flamingo"}). Cheap when Artifacts isn't loaded
     * (returns false immediately) and a single reflection iteration when it is.
     */
    public static boolean playerHasArtifact(net.minecraft.server.network.ServerPlayerEntity player, String itemPath) {
        if (!loaded || player == null || itemPath == null) return false;
        boolean[] found = { false };
        ArtifactsReflect.iterateEquipment(player, stack -> {
            if (found[0] || stack == null || stack.isEmpty()) return;
            net.minecraft.util.Identifier id = net.minecraft.registry.Registries.ITEM.getId(stack.getItem());
            if (id != null && MOD_ID.equals(id.getNamespace()) && itemPath.equals(id.getPath())) {
                found[0] = true;
            }
        });
        return found[0];
    }
}
