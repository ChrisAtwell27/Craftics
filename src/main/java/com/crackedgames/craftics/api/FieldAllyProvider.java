package com.crackedgames.craftics.api;

import com.crackedgames.craftics.api.registry.AllyEntry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Supplies allies that should take the field with a player at the start of a fight,
 * sourced from state Craftics knows nothing about.
 *
 * <p>Craftics' own battle party is built from <b>real mobs standing in the hub</b>: you
 * tag a wolf, it gets snapshotted and discarded when combat starts, and it is put back
 * afterwards. That model assumes the ally exists in the world before the fight.
 *
 * <p>A mod whose party is <b>data on the player</b> rather than entities in a yard cannot
 * use that. There is no wolf to tag; there is a list of creatures the player is carrying,
 * and they should appear in the arena because the player owns them. This is the hook for
 * that: Craftics asks, the addon answers with specs, and they are fielded alongside any
 * real hub pets.
 *
 * <pre>{@code
 * CrafticsAPI.registerFieldAllyProvider("mymod:party", (world, player, freeSlots) -> {
 *     List<FieldAlly> out = new ArrayList<>();
 *     for (Creature c : MyModApi.partyOf(player)) {
 *         out.add(FieldAlly.builder("mymod:creature")
 *             .stats(AllyEntry.builder("mymod:creature")
 *                 .hp(c.hp()).attack(c.attack()).defense(c.defense()).range(1).build())
 *             .aiKey("mymod:" + c.species())      // per-species AI and typing
 *             .spawnNbt(c.toNbt())                // what it IS
 *             .build());
 *     }
 *     return out;
 * });
 * }</pre>
 *
 * <h2>Provider allies never touch the hub</h2>
 *
 * <p>They are fielded as <b>temporary</b> allies: they fight the battle and are gone, never
 * carried between levels and never materialised into the hub world afterwards. That is the
 * only correct behaviour for an ally that was never a hub entity - putting one "back" would
 * mean spawning a real creature into the world that the owning mod is still tracking in its
 * own party, giving the player two of it.
 *
 * <p>If the addon wants a creature's damage to persist across a run, it keeps that in its
 * own state and reflects it in the stats it hands back next time.
 *
 * @since 0.3.9
 */
@FunctionalInterface
public interface FieldAllyProvider {

    /**
     * Which allies this provider wants on the field.
     *
     * @param world     the world the fight is starting in
     * @param player    the player these allies belong to. Called once per participant, so
     *                  a party fight asks every member's provider separately
     * @param freeSlots how many slots are left under the player's own party cap after real
     *                  hub pets were counted, which may be zero or negative. <b>Advisory.</b>
     *                  Craftics does not truncate the returned list - a mod with its own
     *                  party rules owns them, and a six-creature party should not be cut to
     *                  one by a cap written for tamed wolves. Respect it if it suits you
     * @return the allies to field, or an empty list. Never null
     */
    List<FieldAlly> provide(ServerWorld world, ServerPlayerEntity player, int freeSlots);

    /**
     * One ally a provider wants fielded.
     *
     * @param entityTypeId the entity type to render it as
     * @param stats        its combat stats. Required - there is no hub mob to derive them from
     * @param aiKey        AI and typing key, or null to use {@code entityTypeId}. This is what
     *                     lets one entity type field many different creatures
     * @param spawnNbt     NBT merged onto the mob at spawn, or null. Same rules as an enemy's
     * @param displayName  name shown in combat, or null for the entity's own
     */
    record FieldAlly(String entityTypeId,
                     AllyEntry stats,
                     @Nullable String aiKey,
                     @Nullable NbtCompound spawnNbt,
                     @Nullable String displayName) {

        public FieldAlly {
            if (entityTypeId == null || entityTypeId.isBlank()) {
                throw new IllegalArgumentException("FieldAlly requires a non-blank entityTypeId");
            }
            if (stats == null) {
                throw new IllegalArgumentException(
                    "FieldAlly " + entityTypeId + " requires stats - there is no hub mob to derive them from");
            }
        }

        public static Builder builder(String entityTypeId) {
            return new Builder(entityTypeId);
        }

        /** Fluent builder for {@link FieldAlly}. */
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

            /** AI and typing key. Defaults to the entity type id. */
            public Builder aiKey(String aiKey) {
                this.aiKey = aiKey;
                return this;
            }

            /** NBT merged onto the mob at spawn. */
            public Builder spawnNbt(NbtCompound spawnNbt) {
                this.spawnNbt = spawnNbt;
                return this;
            }

            /** Name shown in combat. Defaults to the entity's own. */
            public Builder displayName(String displayName) {
                this.displayName = displayName;
                return this;
            }

            public FieldAlly build() {
                return new FieldAlly(entityTypeId, stats, aiKey, spawnNbt, displayName);
            }
        }
    }
}
