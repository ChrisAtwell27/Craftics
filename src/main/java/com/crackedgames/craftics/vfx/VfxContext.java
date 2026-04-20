package com.crackedgames.craftics.vfx;

import com.crackedgames.craftics.core.GridArena;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Per-play state passed to every phase. Anchors read from here at fire time.
 *
 * {@code originEntityId == -1} and {@code targetEntityId == -1} are valid — anchors
 * fall back to the corresponding block position.
 */
public record VfxContext(
    int originEntityId,
    int targetEntityId,
    BlockPos originBlock,
    BlockPos targetBlock,
    float damageDealt,
    boolean crit,
    @Nullable GridArena arena
) {
    public static VfxContext ofEntities(int originId, int targetId,
                                         BlockPos originBlock, BlockPos targetBlock,
                                         float damage, boolean crit, @Nullable GridArena arena) {
        return new VfxContext(originId, targetId, originBlock, targetBlock, damage, crit, arena);
    }

    public static VfxContext ofBlocks(BlockPos originBlock, BlockPos targetBlock,
                                       float damage, @Nullable GridArena arena) {
        return new VfxContext(-1, -1, originBlock, targetBlock, damage, false, arena);
    }
}
