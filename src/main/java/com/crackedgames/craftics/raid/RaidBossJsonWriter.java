package com.crackedgames.craftics.raid;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Definition to JSON, the exact inverse of {@link RaidBossParser}. Kept separate
 * from the file loader so the round trip is unit-testable with no filesystem.
 *
 * <p>The authoring commands write through this, so a command-authored boss and a
 * hand-edited one are the same artifact on disk.
 */
public final class RaidBossJsonWriter {
    private RaidBossJsonWriter() {}

    public static JsonObject toJson(RaidBossDefinition def) {
        JsonObject o = new JsonObject();
        o.addProperty("id", def.id());
        o.addProperty("name", def.name());
        o.addProperty("entity", def.entityTypeId());
        o.addProperty("hp", def.hp());
        o.addProperty("attack", def.attack());
        o.addProperty("defense", def.defense());
        o.addProperty("range", def.range());
        o.addProperty("speed", def.speed());

        JsonArray moves = new JsonArray();
        for (String m : def.moves()) moves.add(m);
        o.add("moves", moves);

        JsonObject power = new JsonObject();
        if (def.power().isDoubleMove()) {
            power.addProperty("type", "double_move");
        } else {
            power.addProperty("type", "buff");
            power.addProperty("effect", def.power().buffEffect());
            power.addProperty("amplifier", def.power().amplifier());
        }
        o.add("power", power);

        // Written 1-based for authors; omitted entirely when the variant is unpinned.
        if (def.arenaVariant() >= 0) o.addProperty("arena", def.arenaVariant() + 1);
        o.addProperty("environment", def.environmentId());
        o.addProperty("bounty", def.bounty());

        JsonArray loot = new JsonArray();
        for (RaidBossLootEntry e : def.loot()) {
            JsonObject row = new JsonObject();
            row.addProperty("item", e.itemId());
            row.addProperty("weight", e.weight());
            row.addProperty("min", e.min());
            row.addProperty("max", e.max());
            loot.add(row);
        }
        o.add("loot", loot);

        // Omitted entirely when empty, so a boss with no obstacles has no dead field.
        if (!def.obstacles().isEmpty()) {
            JsonArray obstacles = new JsonArray();
            for (RaidBossObstacle entry : def.obstacles()) {
                JsonObject row = new JsonObject();
                row.addProperty("tile", entry.tileType().toLowerCase(java.util.Locale.ROOT));
                if (!entry.blockId().isEmpty()) row.addProperty("block", entry.blockId());
                if (entry.minCount() == entry.maxCount()) {
                    row.addProperty("count", entry.minCount());
                } else {
                    JsonObject count = new JsonObject();
                    count.addProperty("min", entry.minCount());
                    count.addProperty("max", entry.maxCount());
                    row.add("count", count);
                }
                if (entry.cluster() > 1) row.addProperty("cluster", entry.cluster());
                obstacles.add(row);
            }
            o.add("obstacles", obstacles);
        }

        o.addProperty("weight", def.weight());
        return o;
    }
}
