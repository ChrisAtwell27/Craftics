package com.crackedgames.craftics.raid;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pure JSON to {@link RaidBossDefinition}. No Minecraft registries are touched
 * here: entity and item ids stay as strings and are checked in-game at load, so
 * this whole class is unit-testable without a bootstrap.
 *
 * <p>Hard rules produce errors and reject the definition. Soft rules produce
 * warnings and correct the value, matching BiomeJsonLoader's habit of skipping a
 * bad entry rather than failing a whole file.
 */
public final class RaidBossParser {
    private RaidBossParser() {}

    private static final int MIN_MOVES = 6;
    private static final int MAX_MOVES = 8;

    public record Result(RaidBossDefinition definition, List<String> errors, List<String> warnings) {
        public boolean ok() { return definition != null && errors.isEmpty(); }
    }

    public static Result parse(JsonObject json, String fileStem, Set<String> knownAbilityIds) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        String id = str(json, "id", "", errors);
        if (id.isEmpty()) errors.add("missing required field 'id'");
        else if (!id.equals(fileStem)) errors.add("'id' (" + id + ") must match the filename stem (" + fileStem + ")");

        String name = str(json, "name", "", errors);
        if (name.isEmpty()) errors.add("missing required field 'name'");

        String entity = str(json, "entity", "", errors);
        if (entity.isEmpty()) errors.add("missing required field 'entity'");

        int hp = intOr(json, "hp", Integer.MIN_VALUE, errors);
        if (hp == Integer.MIN_VALUE) errors.add("missing required field 'hp'");
        else if (hp <= 0) errors.add("'hp' must be positive");

        int attack = intOr(json, "attack", Integer.MIN_VALUE, errors);
        if (attack == Integer.MIN_VALUE) errors.add("missing required field 'attack'");
        else if (attack <= 0) errors.add("'attack' must be positive");

        int bounty = intOr(json, "bounty", Integer.MIN_VALUE, errors);
        if (bounty == Integer.MIN_VALUE) errors.add("missing required field 'bounty'");
        else if (bounty <= 0) errors.add("'bounty' must be positive");

        int defense = Math.max(0, intOr(json, "defense", 0, errors));
        int range = Math.max(1, intOr(json, "range", 1, errors));
        int speed = Math.max(0, intOr(json, "speed", 0, errors));
        int weight = Math.max(1, intOr(json, "weight", 10, errors));

        // JSON "arena" is 1-based for authors; -1 means roll a variant per instance.
        int arenaRaw = intOr(json, "arena", 0, errors);
        int arenaVariant = arenaRaw >= 1 ? arenaRaw - 1 : -1;

        String environment = str(json, "environment", "plains", errors);
        if (environment.isEmpty()) environment = "plains";

        List<String> moves = parseMoves(json, knownAbilityIds, errors, warnings);
        RaidBossPower power = parsePower(json, errors);
        List<RaidBossLootEntry> loot = parseLoot(json, errors);
        List<RaidBossObstacle> obstacles = parseObstacles(json, warnings);

        if (!errors.isEmpty()) return new Result(null, errors, warnings);

        return new Result(new RaidBossDefinition(
            id, name, entity, hp, attack, defense, range, speed,
            List.copyOf(moves), power, arenaVariant, environment,
            bounty, List.copyOf(loot), List.copyOf(obstacles), weight), errors, warnings);
    }

    private static List<String> parseMoves(JsonObject json, Set<String> known,
                                           List<String> errors, List<String> warnings) {
        List<String> moves = new ArrayList<>();
        if (!json.has("moves")) {
            errors.add("missing required field 'moves'");
            return moves;
        }
        JsonElement raw = json.get("moves");
        if (!raw.isJsonArray()) {
            errors.add("'moves' must be an array of ability ids");
            return moves;
        }
        for (JsonElement e : raw.getAsJsonArray()) {
            String moveId;
            try {
                moveId = e.getAsString();
            } catch (RuntimeException bad) {
                warnings.add("skipping a non-string entry in 'moves'");
                continue;
            }
            if (!known.contains(moveId)) {
                warnings.add("unknown ability '" + moveId + "' dropped from 'moves'");
                continue;
            }
            if (!moves.contains(moveId)) moves.add(moveId);
        }
        if (moves.isEmpty()) {
            errors.add("no valid moves: every entry in 'moves' was unknown");
        } else if (moves.size() > MAX_MOVES) {
            warnings.add("'moves' truncated from " + moves.size() + " to " + MAX_MOVES);
            moves = new ArrayList<>(moves.subList(0, MAX_MOVES));
        } else if (moves.size() < MIN_MOVES) {
            warnings.add("'moves' has fewer than 6 entries (" + moves.size() + "); the fight will be repetitive");
        }
        return moves;
    }

    private static RaidBossPower parsePower(JsonObject json, List<String> errors) {
        if (!json.has("power") || !json.get("power").isJsonObject()) {
            errors.add("missing required field 'power'");
            return null;
        }
        JsonObject p = json.getAsJsonObject("power");
        String type = str(p, "type", "", errors);
        if ("double_move".equals(type)) return RaidBossPower.doubleMove();
        if ("buff".equals(type)) {
            String effect = str(p, "effect", "", errors);
            if (effect.isEmpty()) {
                errors.add("'power' of type 'buff' needs an 'effect'");
                return null;
            }
            return RaidBossPower.buff(effect, intOr(p, "amplifier", 0, errors));
        }
        errors.add("'power.type' must be 'double_move' or 'buff' (got '" + type + "')");
        return null;
    }

    private static List<RaidBossLootEntry> parseLoot(JsonObject json, List<String> errors) {
        List<RaidBossLootEntry> loot = new ArrayList<>();
        if (!json.has("loot") || !json.get("loot").isJsonArray()) {
            errors.add("missing required field 'loot'");
            return loot;
        }
        JsonArray arr = json.getAsJsonArray("loot");
        for (JsonElement e : arr) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            String item = str(o, "item", "", errors);
            if (item.isEmpty()) continue;
            int weight = Math.max(1, intOr(o, "weight", 5, errors));
            int min = Math.max(1, intOr(o, "min", 1, errors));
            int max = Math.max(min, intOr(o, "max", min, errors));
            loot.add(new RaidBossLootEntry(item, weight, min, max));
        }
        if (loot.isEmpty()) errors.add("'loot' must contain at least one entry with an 'item'");
        return loot;
    }

    /**
     * Optional obstacle rows. A row naming an unknown tile type is dropped with a warning
     * rather than failing the file: one bad row must never cost the server its daily boss.
     */
    private static List<RaidBossObstacle> parseObstacles(JsonObject json, List<String> warnings) {
        List<RaidBossObstacle> out = new ArrayList<>();
        if (!json.has("obstacles") || !json.get("obstacles").isJsonArray()) return out;
        for (JsonElement e : json.getAsJsonArray("obstacles")) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();

            String rawTile;
            try {
                rawTile = o.has("tile") ? o.get("tile").getAsString() : "";
            } catch (RuntimeException bad) {
                warnings.add("skipping an obstacle row: 'tile' must be a string");
                continue;
            }
            String tile = resolveTileType(rawTile);
            if (tile == null) {
                warnings.add("unknown obstacle tile '" + rawTile + "' dropped");
                continue;
            }

            String block;
            try {
                block = o.has("block") ? o.get("block").getAsString() : "";
            } catch (RuntimeException bad) {
                warnings.add("skipping an obstacle row: 'block' must be a string");
                continue;
            }

            int min = 1;
            int max = 1;
            if (o.has("count")) {
                try {
                    JsonElement count = o.get("count");
                    if (count.isJsonObject()) {
                        JsonObject range = count.getAsJsonObject();
                        min = range.has("min") ? range.get("min").getAsInt() : 1;
                        max = range.has("max") ? range.get("max").getAsInt() : min;
                    } else {
                        min = count.getAsInt();
                        max = min;
                    }
                } catch (RuntimeException bad) {
                    warnings.add("skipping an obstacle row: 'count' must be a whole number or a {min,max} object");
                    continue;
                }
            }
            min = Math.max(0, min);
            max = Math.max(min, max);

            int cluster;
            try {
                cluster = Math.max(1, o.has("cluster") ? o.get("cluster").getAsInt() : 1);
            } catch (RuntimeException bad) {
                warnings.add("skipping an obstacle row: 'cluster' must be a whole number");
                continue;
            }

            out.add(new RaidBossObstacle(tile, block, min, max, cluster));
        }
        return out;
    }

    /** Upper-cased TileType name, or null when the string names no tile type. */
    private static String resolveTileType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return com.crackedgames.craftics.core.TileType
                .valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT)).name();
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static String str(JsonObject o, String key, String fallback, List<String> errors) {
        if (!o.has(key) || o.get(key).isJsonNull()) return fallback;
        try {
            return o.get(key).getAsString();
        } catch (RuntimeException e) {
            errors.add("'" + key + "' must be a string");
            return fallback;
        }
    }

    private static int intOr(JsonObject o, String key, int fallback, List<String> errors) {
        if (!o.has(key) || o.get(key).isJsonNull()) return fallback;
        try {
            return o.get(key).getAsInt();
        } catch (RuntimeException e) {
            errors.add("'" + key + "' must be a whole number");
            return fallback;
        }
    }
}
