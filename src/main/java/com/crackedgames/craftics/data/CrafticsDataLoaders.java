package com.crackedgames.craftics.data;

import com.crackedgames.craftics.CrafticsMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resource.ResourceManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Central registry of {@link CrafticsDataLoader}s and the hooks that drive them.
 *
 * <p>All registered loaders run on {@code SERVER_STARTED} and after a successful
 * {@code /reload}, in registration order. {@link #init()} is called once from
 * {@code CrafticsMod.onInitialize()}.
 *
 * @since 0.2.0
 */
public final class CrafticsDataLoaders {

    private static final List<CrafticsDataLoader<?>> LOADERS = new ArrayList<>();
    private static boolean initialized = false;

    private CrafticsDataLoaders() {}

    /** Add a loader to the run list. Loaders execute in the order they are registered. */
    public static void register(CrafticsDataLoader<?> loader) {
        LOADERS.add(loader);
    }

    /** Register the built-in loaders and wire the server-start / reload hooks. */
    public static void init() {
        if (initialized) return;
        initialized = true;

        // Built-in loaders. The armor-set and trim loaders are intentionally not yet
        // registered — they are pending the armor-class overhaul, which may change the
        // armor/trim data shapes. The event loader is pending EventTemplates.
        register(new WeaponJsonLoader());
        register(new EnchantmentJsonLoader());

        ServerLifecycleEvents.SERVER_STARTED.register(
            server -> loadAll(server.getResourceManager()));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) loadAll(server.getResourceManager());
        });
    }

    private static void loadAll(ResourceManager resourceManager) {
        for (CrafticsDataLoader<?> loader : LOADERS) {
            try {
                loader.load(resourceManager);
            } catch (Exception e) {
                CrafticsMod.LOGGER.error("Craftics data loader failed: {}",
                    loader.getClass().getSimpleName(), e);
            }
        }
    }
}
