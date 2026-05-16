package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.TargetType;
import com.crackedgames.craftics.api.UsableItemHandler;
import net.minecraft.item.Item;

/**
 * Immutable definition of an item the player can use during a Craftics turn —
 * a consumable, a throwable, or a special-effect item.
 *
 * <p>Build entries with {@link #builder(Item)}:
 *
 * <pre>{@code
 * UsableItemEntry.builder(MyItems.HEALING_DRAUGHT)
 *     .apCost(1).targetType(TargetType.SELF).consumedOnUse(true)
 *     .handler(ItemEffects.heal(8))
 *     .build();
 * }</pre>
 *
 * @param item          the item this entry describes
 * @param apCost        action points spent to use it
 * @param range         max tile distance to the target ({@code 0} = self / no range check)
 * @param targetType    what the item targets
 * @param consumedOnUse whether one of the item is consumed on a successful use
 * @param handler       the effect to run
 * @since 0.2.0
 */
public record UsableItemEntry(
    Item item,
    int apCost,
    int range,
    TargetType targetType,
    boolean consumedOnUse,
    UsableItemHandler handler
) {
    public static Builder builder(Item item) {
        return new Builder(item);
    }

    /** Fluent builder for {@link UsableItemEntry}. */
    public static class Builder {
        private final Item item;
        private int apCost = 1;
        private int range = 0;
        private TargetType targetType = TargetType.SELF;
        private boolean consumedOnUse = true;
        private UsableItemHandler handler;

        public Builder(Item item) {
            this.item = item;
        }

        /** AP spent per use. Default {@code 1}. */
        public Builder apCost(int cost) {
            this.apCost = cost;
            return this;
        }

        /** Max tile distance to the target. Default {@code 0} (self / no range check). */
        public Builder range(int range) {
            this.range = range;
            return this;
        }

        /** What the item targets. Default {@link TargetType#SELF}. */
        public Builder targetType(TargetType targetType) {
            this.targetType = targetType;
            return this;
        }

        /** Whether one of the item is consumed on a successful use. Default {@code true}. */
        public Builder consumedOnUse(boolean consumed) {
            this.consumedOnUse = consumed;
            return this;
        }

        /** The effect to run. Required. */
        public Builder handler(UsableItemHandler handler) {
            this.handler = handler;
            return this;
        }

        public UsableItemEntry build() {
            if (handler == null) {
                throw new IllegalStateException("UsableItemEntry for " + item + " requires a handler");
            }
            return new UsableItemEntry(item, apCost, range, targetType, consumedOnUse, handler);
        }
    }
}
