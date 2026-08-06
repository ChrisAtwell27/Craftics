package com.crackedgames.craftics.raid;

import java.util.List;

/**
 * One authored raid boss. Stats are flat and never scaled by participant count.
 *
 * @param arenaVariant 0-based index into the "raidboss" schem set, or -1 to roll
 *                     a variant per instance. The JSON field is 1-based.
 * @param obstacles    arena hazards this boss scatters at build time (water, lava,
 *                     soul fire, cactus, fallen trees, tall grass, and so on).
 *                     Empty when the boss declares none.
 */
public record RaidBossDefinition(String id,
                                 String name,
                                 String entityTypeId,
                                 int hp,
                                 int attack,
                                 int defense,
                                 int range,
                                 int speed,
                                 List<String> moves,
                                 RaidBossPower power,
                                 int arenaVariant,
                                 String environmentId,
                                 int bounty,
                                 List<RaidBossLootEntry> loot,
                                 List<RaidBossObstacle> obstacles,
                                 int weight) {

    /** Arena biome id for the schematic lookup: one shared "raidboss" schem set. */
    public static final String ARENA_BIOME_ID = "raidboss";

    /** EnemySpawn aiKey for this boss. */
    public String aiKey() { return "raidboss/" + id; }

    /** AIRegistry key CombatManager resolves for the boss spawn ("boss:" + biome id). */
    public String bossAiRegistryKey() { return "boss:" + aiKey(); }
}
