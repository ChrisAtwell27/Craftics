package com.crackedgames.craftics.level;

/**
 * One enemy slot in a biome's spawn pool.
 *
 * <p>{@code aiKey} is the {@code AIRegistry} lookup key. It is normally the same
 * as {@code entityTypeId}; it differs only when a registered {@code EnemyEntry}
 * pairs an entity's appearance with a different AI strategy.
 *
 * <p>{@code speed} is the enemy's combat move speed in tiles per turn. {@code 0}
 * means "use the move speed Craftics assigns the entity type"; a positive value
 * overrides it.
 */
public record MobPoolEntry(
    String entityTypeId,
    int weight,
    int baseHp,
    int baseAttack,
    int baseDefense,
    int range,
    boolean passive,
    String aiKey,
    int speed
) {
    /** Pool entry whose AI matches its entity type, at its entity type's default speed. */
    public MobPoolEntry(String entityTypeId, int weight, int baseHp, int baseAttack,
                        int baseDefense, int range, boolean passive) {
        this(entityTypeId, weight, baseHp, baseAttack, baseDefense, range, passive,
             entityTypeId, 0);
    }

    /** Pool entry with an explicit AI key, at its entity type's default speed. */
    public MobPoolEntry(String entityTypeId, int weight, int baseHp, int baseAttack,
                        int baseDefense, int range, boolean passive, String aiKey) {
        this(entityTypeId, weight, baseHp, baseAttack, baseDefense, range, passive,
             aiKey, 0);
    }
}
