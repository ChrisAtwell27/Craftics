package com.crackedgames.craftics.compat.aether.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;

import java.util.List;

/**
 * Zephyr AI: a cloud that does no damage at all and costs you a turn anyway. A full gust
 * is as far as a player walks in a turn, so being caught by one means spending the whole of
 * the next move getting back to where you were.
 * - GUST: with its target in range and in sight, it spits a snowball that throws the target
 *   up to {@link #GUST_TILES} tiles directly away from it. No damage, no roll to avoid it
 * - PANIC: with anything within {@link #PANIC_DISTANCE} tiles it stops blowing and drifts
 *   away instead, which is the opening: it is slow, and a zephyr you have reached is a
 *   zephyr you can hit
 * - DRIFT: with no shot, it floats to the reachable tile with the best one
 *
 * The gust is the engine's own forced-movement action rather than an attack, so it lands on
 * whoever the engine considers this zephyr's target and cannot hurt them by itself. It is
 * also only ever thrown across clear ground: a pit, lava or fire behind the target shortens
 * the throw instead of feeding them to it, which is what keeps "no damage" true.
 */
public class ZephyrAI implements EnemyAI {

    /** How far a gust throws its target, given the room. */
    public static final int GUST_TILES = 3;
    /** A threat this close makes a zephyr run rather than blow. */
    public static final int PANIC_DISTANCE = 2;

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        int range = Math.max(1, self.getRange());
        List<GridPos> threats = AIUtils.threatPositions(arena, playerPos);

        if (AIUtils.minThreatDistance(self.getGridPos(), threats) <= PANIC_DISTANCE) {
            List<GridPos> escape = AetherAi.backAway(self, arena, threats);
            if (!escape.isEmpty()) return new EnemyAction.Move(escape);
            // Nowhere to drift to: blowing the problem away is the next best thing.
        }

        EnemyAction gust = gust(self, arena, playerPos, range);
        if (gust != null) return gust;

        // Somewhere to blow from that has room behind the target: an angle, not just a line.
        List<GridPos> toPerch = AetherAi.walkTo(self, arena,
            AetherAi.firingTile(self, arena, playerPos, threats, range, PANIC_DISTANCE,
                tile -> roomBehind(arena, playerPos, gustDirection(tile, playerPos)) > 0));
        if (!toPerch.isEmpty()) return new EnemyAction.Move(toPerch);

        // In range with no angle to be had: hold. Closing in would only hand over the fight.
        if (AetherAi.hasShot(arena, self.nearestTileTo(playerPos), playerPos, range)) {
            return new EnemyAction.Idle();
        }
        List<GridPos> closer = AetherAi.stepToward(self, arena, playerPos);
        return closer.isEmpty() ? new EnemyAction.Idle() : new EnemyAction.Move(closer);
    }

    /**
     * The shove, or null when there is no shot or the target has nowhere to go. A gust into
     * a wall would spend the turn on nothing, and say nothing while doing it.
     */
    private static EnemyAction gust(CombatEntity self, GridArena arena, GridPos target, int range) {
        // Measured from the nearest tile of the cloud, so a zephyr given a wider footprint
        // still blows straight away from its body rather than from one corner of it.
        GridPos mouth = self.nearestTileTo(target);
        if (!AetherAi.hasShot(arena, mouth, target, range)) return null;
        int[] dir = gustDirection(mouth, target);
        int room = roomBehind(arena, target, dir);
        if (room == 0) return null;

        // -1 is the engine's word for "the player"; a pet is named by its own id.
        CombatEntity pet = arena.getOccupant(target);
        int shoved = pet != null && pet.isAlly() ? pet.getEntityId() : -1;
        return new EnemyAction.ForcedMovement(shoved, dir[0], dir[1], room);
    }

    /**
     * The step a gust pushes along: directly away from the zephyr, so a target off to one
     * side is thrown diagonally. A target standing on the zephyr's own tile cannot happen on
     * the grid; east is only there so the answer is never "nowhere".
     */
    public static int[] gustDirection(GridPos from, GridPos to) {
        int dx = Integer.signum(to.x() - from.x());
        int dz = Integer.signum(to.z() - from.z());
        if (dx == 0 && dz == 0) dx = 1;
        return new int[]{dx, dz};
    }

    /** How many tiles of clear, harmless ground lie behind {@code target}, up to a full gust. */
    private static int roomBehind(GridArena arena, GridPos target, int[] dir) {
        return AetherAi.roomBehind(arena, target, dir, GUST_TILES);
    }
}
