package com.crackedgames.craftics.api;

import com.crackedgames.craftics.api.registry.AllyEntry;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

/**
 * One creature on an enemy trainer's bench: carried into the fight, on no tile, fielded only
 * when the trainer switches it in.
 *
 * <p>The mirror of the player's own bench, and it exists because a trainer with a team is not
 * a boss mechanic. Any enemy can have one - a route trainer with three creatures is the
 * ordinary case, and a gym leader is the same thing with better ones.
 *
 * <p>Deliberately shaped like {@code FieldAllyProvider.FieldAlly} rather than reusing it. They
 * describe the same idea from opposite sides, and the ally form carries assumptions that do
 * not hold here: it is owned by a player, it is temporary because it was never a hub entity,
 * and it is fielded by a provider that is asked once per participant. A trainer's bench is
 * owned by the trainer, lives as long as the trainer does, and is set by whoever built the
 * fight. Sharing the type would have meant a record where half the fields mean something
 * different depending on which side is holding it.
 *
 * @param entityTypeId the entity type to render it as
 * @param stats        its combat stats. Required - there is no world entity to derive them from
 * @param aiKey        AI, typing and spawn-hook key, or null to use {@code entityTypeId}. What
 *                     lets one entity type field a whole team of different creatures
 * @param spawnNbt     NBT merged onto the mob when it is switched in, or null
 * @param displayName  name shown in combat, or null for the entity type's own. Without it a
 *                     team sharing an entity type reads as several copies of one creature
 * @since 0.4.0
 */
public record EnemyBench(String entityTypeId,
                         AllyEntry stats,
                         @Nullable String aiKey,
                         @Nullable NbtCompound spawnNbt,
                         @Nullable String displayName) {

    public EnemyBench {
        if (entityTypeId == null || entityTypeId.isBlank()) {
            throw new IllegalArgumentException("EnemyBench requires a non-blank entityTypeId");
        }
        if (stats == null) {
            throw new IllegalArgumentException(
                "EnemyBench " + entityTypeId + " requires stats - there is no world entity to derive them from");
        }
    }

    public static Builder builder(String entityTypeId) {
        return new Builder(entityTypeId);
    }

    /** Fluent builder for {@link EnemyBench}. */
    public static final class Builder {
        private final String entityTypeId;
        private AllyEntry stats;
        private String aiKey;
        private NbtCompound spawnNbt;
        private String displayName;

        public Builder(String entityTypeId) {
            this.entityTypeId = entityTypeId;
        }

        /** Combat stats. Required. */
        public Builder stats(AllyEntry stats) {
            this.stats = stats;
            return this;
        }

        /** AI, typing and spawn-hook key. Defaults to the entity type id. */
        public Builder aiKey(String aiKey) {
            this.aiKey = aiKey;
            return this;
        }

        /** NBT merged onto the mob when it is switched in. */
        public Builder spawnNbt(NbtCompound spawnNbt) {
            this.spawnNbt = spawnNbt;
            return this;
        }

        /** Name shown in combat. */
        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public EnemyBench build() {
            return new EnemyBench(entityTypeId, stats, aiKey, spawnNbt, displayName);
        }
    }
}
