package com.crackedgames.craftics.level;

import java.util.*;

/**
 * Defines the branching biome path structure.
 *
 * Fixed structure:
 *   Plains → [Branch A] → River Delta → [Branch B] → Underground Caverns → Deep Dark
 *
 * Branch groups (randomly assigned to A/B per world):
 *   Warm: Scorching Desert, Dense Jungle, Dark Forest
 *   Cool: Snowy Tundra, Stony Peaks
 *
 * The branch assignment is determined once per world save and persisted.
 */
public class BiomePath {

    public static final String PLAINS = "plains";
    public static final String RIVER_DELTA = "river";
    public static final String UNDERGROUND_CAVERNS = "cave";
    public static final String DEEP_DARK = "deep_dark";

    public static final String DARK_FOREST = "forest";
    public static final String SCORCHING_DESERT = "desert";
    public static final String DENSE_JUNGLE = "jungle";

    public static final String STONY_PEAKS = "mountain";
    public static final String SNOWY_TUNDRA = "snowy";

    /**
     * Ordered biome IDs for a world's path
     * @param branchChoice 0 = Warm first, Cool second; 1 = Cool first, Warm second
     */
    public static List<String> getPath(int branchChoice) {
        List<String> path = new ArrayList<>();
        path.add(PLAINS);

        if (branchChoice == 0) {
            path.add(SCORCHING_DESERT);
            path.add(DENSE_JUNGLE);
            path.add(DARK_FOREST);
            path.add(RIVER_DELTA);
            path.add(SNOWY_TUNDRA);
            path.add(STONY_PEAKS);
        } else {
            path.add(SNOWY_TUNDRA);
            path.add(STONY_PEAKS);
            path.add(RIVER_DELTA);
            path.add(SCORCHING_DESERT);
            path.add(DENSE_JUNGLE);
            path.add(DARK_FOREST);
        }

        path.add(UNDERGROUND_CAVERNS);
        path.add(DEEP_DARK);

        return path;
    }

    public static NodeInfo getNodeInfo(String biomeId) {
        return switch (biomeId) {
            case PLAINS -> new NodeInfo("Plains", "§a", "\u2600", 0xAA55FF55); // green sun
            case DARK_FOREST -> new NodeInfo("Dark Forest", "§2", "\u2663", 0xAA00AA00); // dark green clover
            case SCORCHING_DESERT -> new NodeInfo("Scorching Desert", "§6", "\u2604", 0xAAFFAA00); // orange comet
            case DENSE_JUNGLE -> new NodeInfo("Dense Jungle", "§a", "\u2618", 0xAA55FF55); // green shamrock
            case RIVER_DELTA -> new NodeInfo("River Delta", "§b", "\u2248", 0xAA55FFFF); // cyan waves
            case STONY_PEAKS -> new NodeInfo("Stony Peaks", "§7", "\u25B2", 0xAAAAAAAA); // gray triangle
            case SNOWY_TUNDRA -> new NodeInfo("Snowy Tundra", "§f", "\u2744", 0xAAFFFFFF); // white snowflake
            case UNDERGROUND_CAVERNS -> new NodeInfo("Underground Caverns", "§8", "\u2666", 0xAA555555); // dark diamond
            case DEEP_DARK -> new NodeInfo("The Deep Dark", "§5", "\u2620", 0xAAAA00AA); // purple skull
            default -> new NodeInfo("Unknown", "§7", "?", 0xAA888888);
        };
    }

    public record NodeInfo(String displayName, String colorCode, String icon, int mapColor) {}

    public static final String NETHER_WASTES = "nether_wastes";
    public static final String SOUL_SAND_VALLEY = "soul_sand_valley";
    public static final String CRIMSON_FOREST = "crimson_forest";
    public static final String WARPED_FOREST = "warped_forest";
    public static final String BASALT_DELTAS = "basalt_deltas";

    public static List<String> getNetherPath() {
        return List.of(
            NETHER_WASTES,
            SOUL_SAND_VALLEY,
            CRIMSON_FOREST,
            WARPED_FOREST,
            BASALT_DELTAS
        );
    }

    public enum Dimension {
        OVERWORLD("Overworld", "§a"),
        NETHER("The Nether", "§c");

        public final String displayName;
        public final String colorCode;

        Dimension(String displayName, String colorCode) {
            this.displayName = displayName;
            this.colorCode = colorCode;
        }
    }

    public static NodeInfo getNetherNodeInfo(String biomeId) {
        return switch (biomeId) {
            case NETHER_WASTES -> new NodeInfo("Nether Wastes", "§c", "\u2668", 0xAAFF5555); // red hot springs
            case SOUL_SAND_VALLEY -> new NodeInfo("Soul Sand Valley", "§3", "\u2623", 0xAA00AAAA); // teal biohazard
            case CRIMSON_FOREST -> new NodeInfo("Crimson Forest", "§4", "\u2741", 0xAACC0000); // dark red flower
            case WARPED_FOREST -> new NodeInfo("Warped Forest", "§b", "\u273F", 0xAA00CCCC); // cyan flower
            case BASALT_DELTAS -> new NodeInfo("Basalt Deltas", "§8", "\u25A0", 0xAA444444); // dark square
            default -> getNodeInfo(biomeId); // fallback to overworld
        };
    }

    public static final String OUTER_END_ISLANDS = "outer_end_islands";
    public static final String END_CITY = "end_city";
    public static final String CHORUS_GROVE = "chorus_grove";
    public static final String DRAGONS_NEST = "dragons_nest";

    public static List<String> getEndPath() {
        return List.of(
            OUTER_END_ISLANDS,
            END_CITY,
            CHORUS_GROVE,
            DRAGONS_NEST
        );
    }

    /** Full path across all dimensions, used for biome unlock ordering */
    public static List<String> getFullPath(int branchChoice) {
        List<String> full = new ArrayList<>(getPath(branchChoice));
        full.addAll(getNetherPath());
        full.addAll(getEndPath());
        return full;
    }

    public static NodeInfo getEndNodeInfo(String biomeId) {
        return switch (biomeId) {
            case OUTER_END_ISLANDS -> new NodeInfo("Outer End Islands", "§d", "\u2726", 0xAACC88FF); // light purple star
            case END_CITY -> new NodeInfo("End City", "§5", "\u2656", 0xAAAA55CC); // purple rook
            case CHORUS_GROVE -> new NodeInfo("Chorus Grove", "§d", "\u2698", 0xAADD77FF); // pink flower
            case DRAGONS_NEST -> new NodeInfo("Dragon's Nest", "§0", "\u2620", 0xAA220022); // dark skull
            default -> getNodeInfo(biomeId);
        };
    }
}
