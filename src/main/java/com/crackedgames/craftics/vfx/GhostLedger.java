package com.crackedgames.craftics.vfx;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which blocks are currently being shown as something they are not, and until when.
 *
 * <p>The bookkeeping half of {@link GhostBlocks}, kept apart from it so the one rule that
 * matters can be tested without a world: a block that two effects are both showing stays shown
 * until the LATER of them is done. Without that, a short effect finishing would put the real
 * block back in the middle of a longer one still playing over it.
 */
public final class GhostLedger {

    /** Block position to the world time at which it stops being shown. */
    private final Map<BlockPos, Long> until = new LinkedHashMap<>();

    /** Show {@code pos} until at least {@code time}. Never shortens a hold already in place. */
    public void hold(BlockPos pos, long time) {
        until.merge(pos.toImmutable(), time, Math::max);
    }

    /** Every position whose hold ran out at or before {@code now}. They are forgotten on return. */
    public List<BlockPos> release(long now) {
        List<BlockPos> due = new ArrayList<>();
        var it = until.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.getValue() <= now) {
                due.add(entry.getKey());
                it.remove();
            }
        }
        return due;
    }

    /** Every position still held, forgotten on return. For when the fight ends mid-effect. */
    public List<BlockPos> releaseAll() {
        List<BlockPos> all = new ArrayList<>(until.keySet());
        until.clear();
        return all;
    }

    /**
     * Stop showing {@code pos} now, whatever it had left.
     *
     * @return whether it was being shown
     */
    public boolean drop(BlockPos pos) {
        return until.remove(pos) != null;
    }

    public boolean isEmpty() {
        return until.isEmpty();
    }

    public boolean holds(BlockPos pos) {
        return until.containsKey(pos);
    }
}
