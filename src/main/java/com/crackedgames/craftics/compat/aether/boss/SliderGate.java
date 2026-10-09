package com.crackedgames.craftics.compat.aether.boss;

import com.crackedgames.craftics.api.registry.BossGateRegistry;
import com.crackedgames.craftics.combat.ItemUseHandler;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.function.Predicate;

/**
 * The Bronze Dungeon's boss gate: nobody is let in to the Slider without a pickaxe.
 *
 * <p>A pickaxe is the one thing that hurts the Slider (see {@link SliderAI}), so a party that
 * arrives without one has a fight it cannot win, and once combat has started cannot walk away
 * from either. This asks on the way in. What happens when the answer is no, the dialogue and
 * the two ways out of it, is the engine's: see {@link com.crackedgames.craftics.api.BossGate}.
 *
 * <p>One pickaxe anywhere in the party is enough. It does not have to be in a hand or on the
 * hotbar to count, only carried.
 */
public final class SliderGate {

    private SliderGate() {}

    /**
     * Registered with or without the Aether installed. The gate links against nothing of the
     * Aether's, and without the mod the biome it is keyed by is never loaded, so it is never
     * asked.
     */
    public static void register() {
        BossGateRegistry.register(SliderAI.BIOME_ID, SliderGate::check);
    }

    static List<String> check(List<ServerPlayerEntity> party) {
        return verdict(party, SliderGate::carriesPickaxe);
    }

    // ================================================================
    // Pure rules (unit-tested)
    // ================================================================

    /**
     * The gate's answer for a party, given a way to tell who is carrying a pickaxe.
     *
     * @return nothing when anyone carries one, otherwise the lines to show
     */
    static <P> List<String> verdict(List<P> party, Predicate<P> carriesPickaxe) {
        for (P member : party) {
            if (member != null && carriesPickaxe.test(member)) return List.of();
        }
        return lines(party.size());
    }

    /**
     * What a refused party is told. Plain on purpose: this is the one box in the dungeon that
     * has to be understood rather than enjoyed. The engine follows it with what the two
     * choices do.
     */
    static List<String> lines(int partySize) {
        return List.of(
            partySize > 1
                ? "Nobody in your party is carrying a pickaxe."
                : "You are not carrying a pickaxe.",
            "The Slider cannot be hurt by anything else.",
            "This dungeon's first level drops what a pickaxe is made of.");
    }

    // ================================================================
    // The live half
    // ================================================================

    /** Whether this player has a pickaxe on them, wherever it is. */
    static boolean carriesPickaxe(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        // size() spans the whole inventory: hotbar, backpack, armor slots and the offhand.
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (isPickaxe(inventory.getStack(slot))) return true;
        }
        // A stack picked up in an open screen is in no slot until it is put down again.
        return isPickaxe(player.currentScreenHandler.getCursorStack());
    }

    private static boolean isPickaxe(ItemStack stack) {
        return stack != null && !stack.isEmpty() && ItemUseHandler.isPickaxe(stack.getItem());
    }
}
