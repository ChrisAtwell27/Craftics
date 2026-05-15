package com.crackedgames.craftics.data;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.RegistrationSource;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Base class for Craftics JSON datapack loaders.
 *
 * <p>A loader scans every loaded datapack for {@code data/<namespace>/<subPath>/*.json},
 * parses each file, and registers the result into a registry with
 * {@link RegistrationSource#DATAPACK}. {@link #load(ResourceManager)} runs on server
 * start and after every successful {@code /reload}; it always clears the previous
 * datapack entries first so removed or edited files take effect.
 *
 * <p>Subclasses implement {@link #parse}, {@link #register}, and
 * {@link #clearDatapackEntries}. Register loader instances with
 * {@link CrafticsDataLoaders#register(CrafticsDataLoader)}.
 *
 * @param <T> the parsed entry type this loader produces
 * @since 0.2.0
 */
public abstract class CrafticsDataLoader<T> {

    /** Shared Gson instance — datapack JSON needs no custom adapters. */
    protected static final Gson GSON = new Gson();

    private final String subPath;
    private final String typeName;

    /**
     * @param subPath  resource sub-path under {@code data/<namespace>/}, e.g. {@code "craftics/items"}
     * @param typeName human-readable content type for log messages, e.g. {@code "usable item"}
     */
    protected CrafticsDataLoader(String subPath, String typeName) {
        this.subPath = subPath;
        this.typeName = typeName;
    }

    /**
     * Clear prior datapack entries, then scan, parse, and register every JSON file
     * for this content type. Malformed files are logged and skipped — one bad file
     * never aborts the rest of the load.
     */
    public final void load(ResourceManager resourceManager) {
        clearDatapackEntries();

        Map<Identifier, Resource> resources =
            resourceManager.findResources(subPath, id -> id.getPath().endsWith(".json"));

        int loaded = 0;
        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            try (InputStream stream = entry.getValue().getInputStream();
                 InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                T parsed = parse(entry.getKey(), json);
                if (parsed != null) {
                    register(parsed, RegistrationSource.DATAPACK);
                    loaded++;
                }
            } catch (Exception e) {
                CrafticsMod.LOGGER.error("Failed to load {} JSON: {}", typeName, entry.getKey(), e);
            }
        }
        if (loaded > 0) {
            CrafticsMod.LOGGER.info("Loaded {} {} definition(s) from datapacks", loaded, typeName);
        }
    }

    /**
     * Parse one JSON file into an entry. Return {@code null} to skip the file —
     * log the reason first so the datapack author can fix it.
     *
     * @param fileId the resource {@link Identifier} of the JSON file, for diagnostics
     * @param json   the parsed JSON object
     */
    protected abstract T parse(Identifier fileId, JsonObject json);

    /** Register a parsed entry into its registry with the given source. */
    protected abstract void register(T parsed, RegistrationSource source);

    /** Remove every {@link RegistrationSource#DATAPACK} entry from the target registry. */
    protected abstract void clearDatapackEntries();
}
