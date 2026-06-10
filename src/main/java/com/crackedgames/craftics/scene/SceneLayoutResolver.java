package com.crackedgames.craftics.scene;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure classification of scanned scene markers into a {@link SceneLayout}. No
 * Minecraft world/item types so it runs under the no-bootstrap test harness;
 * the world-touching scan lives in {@link SceneScanner}.
 */
public final class SceneLayoutResolver {

    private SceneLayoutResolver() {}

    /** Deterministic booth ordering so a schematic always indexes its stands the same way. */
    private static final Comparator<RawMarker> STAND_ORDER =
        Comparator.comparingInt(RawMarker::x)
            .thenComparingInt(RawMarker::z)
            .thenComparingInt(RawMarker::y);

    public static SceneLayout resolve(List<RawMarker> markers) {
        RawMarker spawn = null;
        List<RawMarker> stands = new ArrayList<>();
        for (RawMarker m : markers) {
            switch (m.kind()) {
                case SPAWN -> { if (spawn == null) spawn = m; }
                case STAND -> stands.add(m);
            }
        }

        stands.sort(STAND_ORDER);
        List<StandSlot> slots = new ArrayList<>(stands.size());
        for (RawMarker m : stands) {
            StandSlot.Kind kind = StandSlot.isOverflowWildcard(m.occupant())
                ? StandSlot.Kind.OVERFLOW : StandSlot.Kind.DEDICATED;
            slots.add(new StandSlot(m.x(), m.y(), m.z(), m.yaw(), m.occupant(), kind));
        }

        if (spawn == null) {
            return new SceneLayout(0, 0, 0, Float.NaN, slots);
        }
        return new SceneLayout(spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), slots);
    }
}
