package com.crackedgames.craftics.vfx;

import java.util.List;

/**
 * One tick-delayed beat of a VfxDescriptor.
 * {@code delayTicks} is relative to the {@code Vfx.play} call.
 */
public record VfxPhase(int delayTicks, List<VfxPrimitive> primitives) {
    public VfxPhase {
        primitives = List.copyOf(primitives);
    }
}
