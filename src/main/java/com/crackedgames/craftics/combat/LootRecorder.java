package com.crackedgames.craftics.combat;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-fight, per-player loot tally. Records every item delivered to a player
 * during a combat (via the single {@link LootDelivery#deliver} choke point) so
 * the victory screen can show "everything you collected this level" as an
 * icon x amount grid. Static because LootDelivery.deliver is static and the
 * tally must survive across the many CombatManager calls within one fight.
 */
public final class LootRecorder {
    private LootRecorder() {}

    private static final int MAX_STACKS = 45;      // packet/grid bound
    private static final int MAX_PER_STACK = 999;  // display cap per merged stack

    // player UUID -> ordered list of (representative stack, summed count).
    private static final Map<UUID, List<ItemStack>> TALLIES = new ConcurrentHashMap<>();
    private static final Set<UUID> RECORDING = ConcurrentHashMap.newKeySet();

    /** Arm recording for these players, clearing any prior tally. */
    public static void begin(Collection<UUID> players) {
        TALLIES.clear();
        RECORDING.clear();
        RECORDING.addAll(players);
    }

    /** Record one delivered stack against a player's tally. No-op if not recording. */
    public static void record(ServerPlayerEntity player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return;
        UUID id = player.getUuid();
        if (!RECORDING.contains(id)) return;
        List<ItemStack> list = TALLIES.computeIfAbsent(id, k -> new ArrayList<>());
        // Merge into an existing matching stack if items + components are equal.
        for (ItemStack existing : list) {
            if (ItemStack.areItemsAndComponentsEqual(existing, stack)) {
                int sum = Math.min(MAX_PER_STACK, existing.getCount() + stack.getCount());
                existing.setCount(sum);
                return;
            }
        }
        if (list.size() >= MAX_STACKS) return; // bound distinct entries
        ItemStack copy = stack.copy();
        copy.setCount(Math.min(MAX_PER_STACK, stack.getCount()));
        list.add(copy);
    }

    /** Drain and return a player's tally (merged stacks), clearing it. */
    public static List<ItemStack> drain(UUID player) {
        List<ItemStack> list = TALLIES.remove(player);
        return list != null ? list : new ArrayList<>();
    }

    /** Wipe all tallies and stop recording. */
    public static void clear() {
        TALLIES.clear();
        RECORDING.clear();
    }
}
