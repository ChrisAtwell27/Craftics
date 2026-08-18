package com.crackedgames.craftics.api;

import net.minecraft.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * A control item pinned to the player's hotbar for the duration of a fight, sitting beside
 * the Move item.
 *
 * <p>Craftics already does this for exactly one item. The Move item is created when combat
 * starts, locked to a slot the player chose, restocked if it goes missing, prevented from
 * being dropped, and destroyed when the fight ends. It is not an item in the ordinary sense -
 * it is a button that happens to live in the hotbar.
 *
 * <p>A total-conversion mod needs more of those buttons. If your fights are commanded rather
 * than swung - pick a move, switch a creature, check a matchup - each of those is a control,
 * and a control the player has to remember to carry is a control they will lose. Registering
 * a {@code CombatTool} gets it the same treatment: it appears when the fight starts, sits at a
 * predictable place next to Move, cannot be dropped or dragged away, and vanishes afterwards.
 *
 * <h2>Opening a menu from one</h2>
 *
 * <p>{@link #onUse} fires server-side when the player right-clicks the tool during a fight,
 * and Craftics does nothing else with the click. From there the addon opens whatever it
 * likes - a vanilla {@code ScreenHandler}, or its own payload to its own client screen.
 * Craftics deliberately provides no UI framework here: a move-selection screen is the addon's
 * design, and anything Craftics invented would be a worse fit than the addon's own.
 *
 * <pre>{@code
 * CrafticsAPI.registerCombatTool(CombatTool.builder("mymod:lead", MyItems.LEAD)
 *     .order(1)                     // one slot right of Move
 *     .onUse((player, combat) -> {
 *         MyMoveMenu.open(player);   // your screen, your rules
 *         return true;               // handled; no default item use
 *     })
 *     .build());
 * }</pre>
 *
 * @param id                 unique id, e.g. {@code "mymod:lead"}
 * @param item               the item to pin. Give it a distinct item; a tool that shares an
 *                           item with ordinary gear would be stripped along with the tool
 * @param order              how far right of the Move slot it sits, wrapping around the
 *                           hotbar. {@code 1} is immediately adjacent. Two tools claiming the
 *                           same order is resolved by registration order, but authoring
 *                           distinct values is clearer
 * @param stripOutsideCombat destroy every copy when no fight is running. True for a control,
 *                           which is almost always what you want - a button that survives
 *                           into the hub is an item the player can hoard, trade or lose
 * @param onUse              fired when the player uses it mid-fight, or null for an inert
 *                           tool. Returning true suppresses the item's ordinary use
 * @since 0.4.0
 */
public record CombatTool(String id,
                         Item item,
                         int order,
                         boolean stripOutsideCombat,
                         @Nullable ToolUseHandler onUse) {

    public CombatTool {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("CombatTool requires a non-blank id");
        }
        if (item == null) {
            throw new IllegalArgumentException("CombatTool " + id + " requires an item");
        }
        if (order < 1) order = 1;   // 0 is the Move item's own slot
    }

    /** What happens when the player uses a combat tool during a fight. */
    @FunctionalInterface
    public interface ToolUseHandler {
        /**
         * @param player  the player who used it
         * @param combat  the fight they are in. Never null - the handler only fires in combat
         * @return true when the tool handled the click, which suppresses the item's ordinary
         *         use. Return false to let vanilla behaviour continue, which is rarely what a
         *         control wants
         */
        boolean onUse(net.minecraft.server.network.ServerPlayerEntity player,
                      com.crackedgames.craftics.combat.CombatManager combat);
    }

    public static Builder builder(String id, Item item) {
        return new Builder(id, item);
    }

    /** Fluent builder for {@link CombatTool}. */
    public static final class Builder {
        private final String id;
        private final Item item;
        private int order = 1;
        private boolean stripOutsideCombat = true;
        private ToolUseHandler onUse;

        public Builder(String id, Item item) {
            this.id = id;
            this.item = item;
        }

        /** Slots right of Move, wrapping the hotbar. Default {@code 1} (adjacent). */
        public Builder order(int order) {
            this.order = order;
            return this;
        }

        /** Whether to destroy it outside combat. Default true. */
        public Builder stripOutsideCombat(boolean strip) {
            this.stripOutsideCombat = strip;
            return this;
        }

        /** Handler for a mid-fight use, typically opening a menu. */
        public Builder onUse(ToolUseHandler onUse) {
            this.onUse = onUse;
            return this;
        }

        public CombatTool build() {
            return new CombatTool(id, item, order, stripOutsideCombat, onUse);
        }
    }
}
