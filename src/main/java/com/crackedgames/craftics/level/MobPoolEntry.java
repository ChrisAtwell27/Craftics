package com.crackedgames.craftics.level;

/**
 * One enemy slot in a biome's spawn pool.
 *
 * <p>{@code aiKey} is the {@code AIRegistry} lookup key. It is normally the same
 * as {@code entityTypeId}; it differs only when a registered {@code EnemyEntry}
 * pairs an entity's appearance with a different AI strategy.
 */
public record MobPoolEntry(
    String entityTypeId,
    int weight,
    int baseHp,
    int baseAttack,
    int baseDefense,
    int range,
    boolean passive,
    String aiKey
) {
    /** Pool entry whose AI matches its entity type (the common case). */
    public MobPoolEntry(String entityTypeId, int weight, int baseHp, int baseAttack,
                        int baseDefense, int range, boolean passive) {
        this(entityTypeId, weight, baseHp, baseAttack, baseDefense, range, passive,
             entityTypeId);
    }
}
