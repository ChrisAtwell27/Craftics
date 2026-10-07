package com.crackedgames.craftics.compat.aether.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.combat.ai.SlimeAI;
import com.crackedgames.craftics.compat.aether.AetherMobs;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Swet AI (blue and golden): hops straight at its prey like a slime and slams into it.
 * - SWALLOW: the hit is weak, but it lifts whoever it lands on off the ground. That part is
 *   not decided here - it rides the hit itself, see {@code AetherMobs.Riders}
 * - SWET CAPE: a player wearing one is not prey. The swet goes for the nearest player who
 *   is not, and if everyone is wearing one it just wanders
 * - WATER: a swet never hops into water, and one that starts its turn standing in it
 *   dissolves. That is the Aether's own rule, and here it is also what keeps the grid and
 *   the world agreeing: the real entity deletes itself after a couple of seconds in water
 *   whether or not it is anybody's turn
 */
public class SwetAI implements EnemyAI {

    /** The engine's own beeline hopper. On dry ground a swet moves exactly like one. */
    private static final EnemyAI HOP = new SlimeAI();

    private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Not a threat to a party that is all wearing Swet Capes. */
    @Override
    public boolean isHostileThreat(CombatEntity self, GridArena arena, GridPos playerPos) {
        return AetherMobs.swetPrey(self, arena, playerPos) != null;
    }

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        if (isWater(arena, self.getGridPos())) {
            return new EnemyAction.CustomAction(AetherMobs.SWET_DISSOLVE);
        }
        GridPos prey = AetherMobs.swetPrey(self, arena, playerPos);
        if (prey == null) {
            EnemyAction stroll = AIUtils.wander(self, arena);
            return staysDry(arena, stroll) ? stroll : new EnemyAction.Idle();
        }
        EnemyAction hop = HOP.decideAction(self, arena, prey);
        return staysDry(arena, hop) ? hop : dryHop(self, arena, prey);
    }

    /** Nobody to chase means nobody to warn. */
    @Override
    public Set<GridPos> computeThreatTiles(CombatEntity self, GridArena arena) {
        return AetherMobs.swetPrey(self, arena, arena.getPlayerGridPos()) != null ? null : Set.of();
    }

    /**
     * The hop for when the straight one would get its feet wet: the nearest tile to the prey
     * that can be reached over dry, harmless ground this turn. A swet that cannot get any
     * closer without touching water waits where it is.
     *
     * <p>Searched here rather than asked of the shared pathfinder, which treats shallow
     * water as ordinary floor and picks a wet route as readily as a dry one of the same
     * length. Swets are one tile wide, so a plain flood over single tiles is all it takes.
     */
    private static EnemyAction dryHop(CombatEntity self, GridArena arena, GridPos prey) {
        GridPos start = self.getGridPos();
        int speed = self.getMoveSpeed();
        Map<GridPos, GridPos> cameFrom = new HashMap<>();
        Map<GridPos, Integer> steps = new HashMap<>();
        Deque<GridPos> open = new ArrayDeque<>();
        steps.put(start, 0);
        open.add(start);

        // Breadth first, so the first tile found at any distance from the prey is also the
        // one that takes the fewest steps to reach.
        GridPos landing = null;
        int landingDist = start.manhattanDistance(prey);
        while (!open.isEmpty()) {
            GridPos at = open.poll();
            int dist = at.manhattanDistance(prey);
            if (dist < landingDist) {
                landingDist = dist;
                landing = at;
            }
            int taken = steps.get(at);
            if (taken >= speed) continue;
            for (int[] dir : CARDINALS) {
                GridPos next = new GridPos(at.x() + dir[0], at.z() + dir[1]);
                if (steps.containsKey(next) || !isDryFooting(arena, next)) continue;
                steps.put(next, taken + 1);
                cameFrom.put(next, at);
                open.add(next);
            }
        }
        if (landing == null) return new EnemyAction.Idle();

        List<GridPos> path = new ArrayList<>();
        for (GridPos at = landing; !at.equals(start); at = cameFrom.get(at)) path.add(at);
        Collections.reverse(path);
        return landingDist <= 1
            ? new EnemyAction.MoveAndAttack(path, self.getAttackPower())
            : new EnemyAction.Move(path);
    }

    /** A free tile a swet can stand on without dissolving or burning. */
    private static boolean isDryFooting(GridArena arena, GridPos pos) {
        if (!arena.isInBounds(pos) || arena.isOccupied(pos)) return false;
        GridTile tile = arena.getTile(pos);
        return tile != null && tile.isWalkable() && !tile.isWater() && !AIUtils.isHazardTile(arena, pos);
    }

    /** Whether an action keeps the swet out of water. An action with no walk in it always does. */
    private static boolean staysDry(GridArena arena, EnemyAction action) {
        List<GridPos> path = action instanceof EnemyAction.Move move ? move.path()
            : action instanceof EnemyAction.MoveAndAttack hop ? hop.path()
            : List.of();
        for (GridPos step : path) {
            if (isWater(arena, step)) return false;
        }
        return true;
    }

    private static boolean isWater(GridArena arena, GridPos pos) {
        GridTile tile = arena.getTile(pos);
        return tile != null && tile.isWater();
    }
}
