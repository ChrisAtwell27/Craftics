package com.crackedgames.craftics.level;

/**
 * Defines a mob type that can spawn in a biome, with base stats and weight.
 */
public record MobPoolEntry(
    String entityTypeId,
    int weight,
    int baseHp,
    int baseAttack,
    int baseDefense,
    int range,
    boolean passive
) {}
