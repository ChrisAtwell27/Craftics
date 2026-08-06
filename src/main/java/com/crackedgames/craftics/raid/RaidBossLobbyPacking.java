package com.crackedgames.craftics.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Turns a join-ordered roster into eight-player instances.
 *
 * <p>Fill-first, not balanced: a full group of eight is the intended raid and
 * overflow is the exception, so joiner nine opens instance two rather than
 * rebalancing into two groups of four and five.
 */
public final class RaidBossLobbyPacking {
    private RaidBossLobbyPacking() {}

    public static final int INSTANCE_SIZE = 8;

    /** True while another joiner would still fit under the instance cap. */
    public static boolean hasRoom(int currentJoinCount, int maxInstances) {
        return currentJoinCount < Math.max(1, maxInstances) * INSTANCE_SIZE;
    }

    /** Which instance the nth joiner lands in. */
    public static int instanceIndexFor(int joinIndex) {
        return Math.max(0, joinIndex) / INSTANCE_SIZE;
    }

    /** Pack the roster, discarding anything past the cap (join already refused it). */
    public static List<List<UUID>> pack(List<UUID> joinOrder, int maxInstances) {
        List<List<UUID>> instances = new ArrayList<>();
        if (joinOrder == null || joinOrder.isEmpty()) return instances;
        int cap = Math.max(1, maxInstances) * INSTANCE_SIZE;
        for (int i = 0; i < joinOrder.size() && i < cap; i++) {
            int index = instanceIndexFor(i);
            while (instances.size() <= index) instances.add(new ArrayList<>());
            instances.get(index).add(joinOrder.get(i));
        }
        return instances;
    }
}
