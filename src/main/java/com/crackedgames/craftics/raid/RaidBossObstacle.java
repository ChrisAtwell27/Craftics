package com.crackedgames.craftics.raid;

/**
 * One kind of obstacle a raid boss scatters through its arena: a tile behaviour, an
 * optional block to show for it, how many to place, and how tightly they clump.
 *
 * <p>{@code tileType} is a {@link com.crackedgames.craftics.core.TileType} name in upper
 * case. It carries the BEHAVIOUR (lava burns, water soaks, tall grass hides you, obstacle
 * blocks the path); {@code blockId} only decides what it LOOKS like, which is how one
 * OBSTACLE entry can be a cactus and another a fallen log. An empty {@code blockId} lets
 * the placer choose a default for the tile type.
 *
 * <p>Ids stay Strings so this record and the parser never touch a Minecraft registry.
 *
 * @param minCount lowest number of placements, rolled per raid instance
 * @param maxCount highest number of placements, never below {@code minCount}
 * @param cluster  tiles each placement grows into; 1 scatters singles, 3 to 6 grows blobs
 */
public record RaidBossObstacle(String tileType, String blockId,
                               int minCount, int maxCount, int cluster) {}
