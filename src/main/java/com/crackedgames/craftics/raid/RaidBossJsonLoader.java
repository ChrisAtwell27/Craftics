package com.crackedgames.craftics.raid;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.combat.ai.boss.InfiniteAbilityPool;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.Resource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reads and writes raid boss definitions in {@code config/craftics/raidbosses/}.
 *
 * <p>Bundled examples ship inside the jar and are COPIED OUT on first run rather
 * than merged at runtime, so after that first copy the config folder is the only
 * source of truth and an admin's edits are never shadowed by a bundled file of
 * the same id.
 */
public final class RaidBossJsonLoader {
    private RaidBossJsonLoader() {}

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final String BUNDLED_PATH = "raidbosses";

    public static Path directory() {
        return FabricLoader.getInstance().getConfigDir().resolve("craftics").resolve("raidbosses");
    }

    /** Copy the jar's example definitions the first time the directory is missing. */
    public static void copyBundledIfAbsent(MinecraftServer server) {
        Path dir = directory();
        if (Files.isDirectory(dir)) return;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            CrafticsMod.LOGGER.error("Could not create {}", dir, e);
            return;
        }
        Map<Identifier, Resource> bundled = server.getResourceManager()
            .findResources(BUNDLED_PATH, id -> id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> entry : bundled.entrySet()) {
            String path = entry.getKey().getPath();
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            try (InputStream in = entry.getValue().getInputStream()) {
                Files.copy(in, dir.resolve(fileName));
                CrafticsMod.LOGGER.info("Copied bundled raid boss example {}", fileName);
            } catch (IOException e) {
                CrafticsMod.LOGGER.error("Could not copy bundled raid boss {}", fileName, e);
            }
        }
    }

    /** Parse every JSON file in the config directory. Bad files are logged and skipped. */
    public static List<RaidBossDefinition> loadAll() {
        List<RaidBossDefinition> out = new ArrayList<>();
        Path dir = directory();
        if (!Files.isDirectory(dir)) return out;
        Set<String> known = new HashSet<>(InfiniteAbilityPool.allIds());
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String stem = file.getFileName().toString();
                stem = stem.substring(0, stem.length() - ".json".length());
                try (InputStreamReader reader = new InputStreamReader(
                        Files.newInputStream(file), StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    RaidBossParser.Result result = RaidBossParser.parse(json, stem, known);
                    for (String w : result.warnings()) {
                        CrafticsMod.LOGGER.warn("Raid boss '{}': {}", stem, w);
                    }
                    if (!result.ok()) {
                        for (String err : result.errors()) {
                            CrafticsMod.LOGGER.error("Raid boss '{}' rejected: {}", stem, err);
                        }
                        continue;
                    }
                    out.add(result.definition());
                    CrafticsMod.LOGGER.info("Loaded raid boss '{}' ({})",
                        result.definition().id(), result.definition().name());
                } catch (Exception e) {
                    CrafticsMod.LOGGER.error("Could not read raid boss file {}", file, e);
                }
            }
        } catch (IOException e) {
            CrafticsMod.LOGGER.error("Could not list {}", dir, e);
        }
        return out;
    }

    /** Write a definition to {@code <id>.json}, creating the directory when needed. */
    public static boolean save(RaidBossDefinition def) {
        Path dir = directory();
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(def.id() + ".json");
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                PRETTY.toJson(RaidBossJsonWriter.toJson(def), w);
            }
            return true;
        } catch (IOException e) {
            CrafticsMod.LOGGER.error("Could not save raid boss '{}'", def.id(), e);
            return false;
        }
    }

    public static boolean delete(String id) {
        try {
            return Files.deleteIfExists(directory().resolve(id + ".json"));
        } catch (IOException e) {
            CrafticsMod.LOGGER.error("Could not delete raid boss '{}'", id, e);
            return false;
        }
    }
}
