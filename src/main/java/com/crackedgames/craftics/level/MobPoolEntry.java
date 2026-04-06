package com.crackedgames.craftics.level;

public record MobPoolEntry(
    String entityTypeId,
    int weight,
    int baseHp,
    int baseAttack,
    int baseDefense,
    int range,
    boolean passive
) {}
