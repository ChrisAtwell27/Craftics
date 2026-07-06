package com.crackedgames.craftics.client.menu;

import com.crackedgames.craftics.CrafticsMod;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tiny client-side preference store for the custom title screen
 * ({@code config/craftics_menu.json}, sibling of {@code craftics_hints.json}).
 * Currently holds one thing: the biome the player pinned as their menu
 * backdrop by clicking a node on the campaign strip.
 */
public final class MenuPrefs {

    private MenuPrefs() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("craftics_menu.json");
    }

    /** The pinned backdrop biome id, or {@code null} when the player never picked one. */
    public static String loadBackdropBiome() {
        try {
            Path f = file();
            if (!Files.isRegularFile(f)) return null;
            JsonObject root = JsonParser.parseString(
                Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("backdropBiome")) return null;
            String id = root.get("backdropBiome").getAsString();
            return id.isBlank() ? null : id;
        } catch (Exception e) {
            CrafticsMod.LOGGER.warn("Craftics menu: could not read menu prefs", e);
            return null;
        }
    }

    /** Persist the pinned backdrop biome ({@code null} clears the pin). */
    public static void saveBackdropBiome(String biomeId) {
        try {
            JsonObject root = new JsonObject();
            if (biomeId != null && !biomeId.isBlank()) {
                root.addProperty("backdropBiome", biomeId);
            }
            Files.writeString(file(), root.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            CrafticsMod.LOGGER.warn("Craftics menu: could not save menu prefs", e);
        }
    }
}
