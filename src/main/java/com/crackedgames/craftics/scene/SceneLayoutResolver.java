package com.crackedgames.craftics.scene;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure classification of scanned scene markers into a {@link SceneLayout}. No
 * Minecraft world/item types so it runs under the no-bootstrap test harness;
 * the world-touching scan lives in {@link SceneScanner}.
 *
 * <p>Booth pairing: a booth is two {@code STAND} corner markers forming a
 * rectangle with exactly one {@code NPC} marker inside it. For each NPC marker we
 * pick the unique corner pair whose bounding rectangle contains the NPC and
 * contains no other corner; that tight pair is the booth's clickable rectangle.
 * Ambiguous or unmatched NPC markers are skipped (the caller logs a warning).
 */
public final class SceneLayoutResolver {

    private SceneLayoutResolver() {}

    /** Deterministic booth ordering so a schematic always indexes its booths the same way. */
    private static final Comparator<StandSlot> BOOTH_ORDER =
        Comparator.comparingInt(StandSlot::minX)
            .thenComparingInt(StandSlot::minZ)
            .thenComparingInt(StandSlot::y);

    /** Result of resolving, including booths that could not be formed (for logging). */
    public record Result(SceneLayout layout, int skippedNpcMarkers) {}

    /** Convenience overload returning just the layout. */
    public static SceneLayout resolve(List<RawMarker> markers) {
        return resolveWithDiagnostics(markers).layout();
    }

    public static Result resolveWithDiagnostics(List<RawMarker> markers) {
        RawMarker spawn = null;
        List<RawMarker> corners = new ArrayList<>();
        List<RawMarker> npcs = new ArrayList<>();
        for (RawMarker m : markers) {
            switch (m.kind()) {
                case SPAWN -> { if (spawn == null) spawn = m; }
                case STAND -> corners.add(m);
                case NPC -> npcs.add(m);
            }
        }

        List<StandSlot> slots = new ArrayList<>();
        int skipped = 0;
        for (RawMarker npc : npcs) {
            int[] pair = findContainingCornerPair(corners, npc);
            if (pair == null) { skipped++; continue; }
            RawMarker a = corners.get(pair[0]);
            RawMarker b = corners.get(pair[1]);
            slots.add(buildBooth(a, b, npc));
        }

        slots.sort(BOOTH_ORDER);

        SceneLayout layout = (spawn == null)
            ? new SceneLayout(0, 0, 0, Float.NaN, slots)
            : new SceneLayout(spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), slots);
        return new Result(layout, skipped);
    }

    /**
     * The unique pair of corner indices whose horizontal bounding rectangle contains
     * {@code npc} and contains no OTHER corner. Returns null when there is no such
     * pair or more than one (ambiguous), so the caller can skip + warn.
     */
    private static int[] findContainingCornerPair(List<RawMarker> corners, RawMarker npc) {
        int[] found = null;
        for (int i = 0; i < corners.size(); i++) {
            for (int j = i + 1; j < corners.size(); j++) {
                RawMarker a = corners.get(i), b = corners.get(j);
                int minX = Math.min(a.x(), b.x()), maxX = Math.max(a.x(), b.x());
                int minZ = Math.min(a.z(), b.z()), maxZ = Math.max(a.z(), b.z());
                if (npc.x() < minX || npc.x() > maxX || npc.z() < minZ || npc.z() > maxZ) continue;
                if (containsOtherCorner(corners, i, j, minX, minZ, maxX, maxZ)) continue;
                if (found != null) return null; // ambiguous: more than one tight pair contains this NPC
                found = new int[]{i, j};
            }
        }
        return found;
    }

    private static boolean containsOtherCorner(List<RawMarker> corners, int i, int j,
                                               int minX, int minZ, int maxX, int maxZ) {
        for (int k = 0; k < corners.size(); k++) {
            if (k == i || k == j) continue;
            RawMarker c = corners.get(k);
            if (c.x() >= minX && c.x() <= maxX && c.z() >= minZ && c.z() <= maxZ) return true;
        }
        return false;
    }

    private static StandSlot buildBooth(RawMarker a, RawMarker b, RawMarker npc) {
        int minX = Math.min(a.x(), b.x()), maxX = Math.max(a.x(), b.x());
        int minZ = Math.min(a.z(), b.z()), maxZ = Math.max(a.z(), b.z());
        int y = Math.max(a.y(), b.y()); // floor is whatever the player stands on; prefer the higher marker

        StandSlot.Kind kind = StandSlot.isOverflowWildcard(npc.occupant())
            ? StandSlot.Kind.OVERFLOW : StandSlot.Kind.DEDICATED;

        // Player stands TWO tiles in front of the NPC (along the NPC's facing) and faces back
        // at it - one tile of breathing room, so the counter sits between them instead of the
        // player pressing up against the merchant.
        int[] off = yawToOffset(npc.yaw());
        int playerX = npc.x() + off[0] * 2;
        int playerY = npc.y();
        int playerZ = npc.z() + off[1] * 2;
        float playerYaw = wrap(npc.yaw() + 180f);

        return new StandSlot(minX, minZ, maxX, maxZ, y,
            npc.x(), npc.y(), npc.z(), npc.yaw(),
            playerX, playerY, playerZ, playerYaw,
            npc.occupant(), kind);
    }

    /**
     * Cardinal (dx, dz) one tile in the direction of a Minecraft block yaw:
     * 0=south(+Z), 90=west(-X), 180=north(-Z), 270=east(+X). The yaw is snapped
     * to the nearest cardinal, since marker facings are always horizontal cardinals.
     */
    private static int[] yawToOffset(float yaw) {
        int q = Math.floorMod(Math.round(yaw / 90f), 4); // 0..3
        return switch (q) {
            case 0 -> new int[]{0, 1};   // south
            case 1 -> new int[]{-1, 0};  // west
            case 2 -> new int[]{0, -1};  // north
            default -> new int[]{1, 0};  // east
        };
    }

    /** Normalize a yaw to [0, 360). */
    private static float wrap(float yaw) {
        float y = yaw % 360f;
        return y < 0 ? y + 360f : y;
    }
}
