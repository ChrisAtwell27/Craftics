package com.crackedgames.craftics.combat;

/**
 * Per-species minimum stats for tamed combat pets.
 * When a passive mob is tamed, its stats are boosted to at least these values.
 */
public class PetStats {

    public record Stats(int hp, int atk, int def, int speed, int range) {}

    public static Stats get(String entityTypeId) {
        return switch (entityTypeId) {
            // Combat predators — fast, high attack
            case "minecraft:wolf" -> new Stats(8, 3, 0, 3, 1);
            case "minecraft:cat", "minecraft:ocelot" -> new Stats(6, 2, 0, 4, 1);
            case "minecraft:fox" -> new Stats(7, 3, 0, 3, 1);

            // Ranged / special
            case "minecraft:llama" -> new Stats(10, 2, 1, 2, 2);
            case "minecraft:goat" -> new Stats(8, 3, 1, 2, 1);
            case "minecraft:bee" -> new Stats(4, 2, 0, 3, 1);
            case "minecraft:frog" -> new Stats(5, 2, 0, 3, 1);
            case "minecraft:axolotl" -> new Stats(6, 2, 0, 3, 1);
            case "minecraft:parrot" -> new Stats(3, 1, 0, 4, 1);

            // Tanks — high HP/DEF, low attack
            case "minecraft:cow", "minecraft:mooshroom" -> new Stats(12, 1, 2, 1, 1);
            case "minecraft:turtle" -> new Stats(10, 1, 3, 1, 1);
            case "minecraft:sniffer" -> new Stats(10, 1, 1, 1, 1);

            // Balanced / light fighters
            case "minecraft:sheep", "minecraft:pig" -> new Stats(8, 2, 1, 2, 1);
            case "minecraft:chicken", "minecraft:rabbit" -> new Stats(4, 1, 0, 3, 1);

            // Mounts — decent stats when not mounted
            case "minecraft:horse", "minecraft:donkey", "minecraft:camel",
                 "minecraft:mule", "minecraft:skeleton_horse", "minecraft:zombie_horse"
                -> new Stats(12, 2, 1, 3, 1);

            // Fallback for any unknown tameable
            default -> new Stats(6, 1, 0, 2, 1);
        };
    }
}
