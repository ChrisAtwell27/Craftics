package com.crackedgames.craftics.client.hints;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class HintDismissalStore {

    private final Path file;
    private final Set<String> dismissed = new LinkedHashSet<>();

    public HintDismissalStore(Path file) {
        this.file = file;
        load();
    }

    public Set<String> dismissed() {
        return Collections.unmodifiableSet(dismissed);
    }

    public boolean isDismissed(String id) {
        return dismissed.contains(id);
    }

    public synchronized void markDismissed(String id) {
        if (dismissed.add(id)) {
            save();
        }
    }

    public synchronized void clear() {
        dismissed.clear();
        try { Files.deleteIfExists(file); } catch (IOException ignored) {}
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            String body = Files.readString(file);
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            obj.getAsJsonArray("dismissed").forEach(e -> dismissed.add(e.getAsString()));
        } catch (Exception ignored) {
            // Corrupt or unreadable — start fresh; do not delete the user's file.
        }
    }

    private void save() {
        JsonObject obj = new JsonObject();
        obj.add("dismissed", new Gson().toJsonTree(dismissed));
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, obj.toString());
        } catch (IOException ignored) {
            // Disk-full or permissions — silently drop; hints will simply re-fire next launch.
        }
    }
}
