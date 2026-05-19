package com.crackedgames.craftics.api.registry;

/**
 * Immutable definition of a reusable enemy template — combat stats, the entity
 * type rendered for it (its appearance), and which {@code AIRegistry} strategy
 * drives it.
 *
 * <p>Registered enemies are referenced by {@link #id() id} from biome JSON
 * ({@code "enemy": "<id>"}) instead of redefining the same stats inline in every
 * biome that uses the enemy.
 *
 * <p>Build entries with {@link #builder(String, String)}:
 *
 * <pre>{@code
 * EnemyEntry.builder("craftics:parched_husk", "minecraft:husk")
 *     .ai("minecraft:skeleton")
 *     .hp(10).attack(3).defense(1).range(1).speed(2)
 *     .build();
 * }</pre>
 *
 * @param id           registry key, e.g. {@code "craftics:parched_husk"}
 * @param entityTypeId entity type rendered for this enemy (its appearance)
 * @param aiKey        {@code AIRegistry} lookup key; defaults to {@code entityTypeId}
 * @param hp           base health
 * @param attack       base attack
 * @param defense      base defense
 * @param range        attack range in tiles
 * @param speed        combat move speed in tiles per turn; {@code 0} = use the entity type's default
 * @since 0.2.0
 */
public record EnemyEntry(
    String id,
    String entityTypeId,
    String aiKey,
    int hp,
    int attack,
    int defense,
    int range,
    int speed
) {
    public static Builder builder(String id, String entityTypeId) {
        return new Builder(id, entityTypeId);
    }

    /** Fluent builder for {@link EnemyEntry}. */
    public static class Builder {
        private final String id;
        private final String entityTypeId;
        private String aiKey;
        private int hp = 6;
        private int attack = 2;
        private int defense = 0;
        private int range = 1;
        private int speed = 0;

        public Builder(String id, String entityTypeId) {
            this.id = id;
            this.entityTypeId = entityTypeId;
        }

        /** AIRegistry lookup key. Defaults to the entity type id. */
        public Builder ai(String aiKey) {
            this.aiKey = aiKey;
            return this;
        }

        /** Base health. Default {@code 6}. */
        public Builder hp(int hp) {
            this.hp = hp;
            return this;
        }

        /** Base attack. Default {@code 2}. */
        public Builder attack(int attack) {
            this.attack = attack;
            return this;
        }

        /** Base defense. Default {@code 0}. */
        public Builder defense(int defense) {
            this.defense = defense;
            return this;
        }

        /** Attack range in tiles. Default {@code 1}. */
        public Builder range(int range) {
            this.range = range;
            return this;
        }

        /**
         * Combat move speed in tiles per turn. Default {@code 0} — the enemy moves
         * at the speed Craftics assigns its entity type. Set a positive value to
         * override that with a fixed speed.
         */
        public Builder speed(int speed) {
            this.speed = speed;
            return this;
        }

        public EnemyEntry build() {
            if (id == null || id.isBlank()) {
                throw new IllegalStateException("EnemyEntry requires a non-blank id");
            }
            if (entityTypeId == null || entityTypeId.isBlank()) {
                throw new IllegalStateException(
                    "EnemyEntry " + id + " requires a non-blank entityTypeId");
            }
            return new EnemyEntry(id, entityTypeId,
                aiKey != null ? aiKey : entityTypeId,
                hp, attack, defense, range, speed);
        }
    }
}
