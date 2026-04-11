package com.crackedgames.craftics.compat.artifacts;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * AI for the Artifacts mod Mimic during the Abandoned Campsite encounter.
 * <p>
 * The mimic alternates between two compound actions each turn, regardless of
 * its HP:
 * <ul>
 *   <li><b>Tantrum</b> — a single attack where the mimic hops through 4–6
 *       random adjacent tiles in sequence. If any hop lands on the player's
 *       tile, the player takes {@link #TANTRUM_DAMAGE} damage and the tantrum
 *       stops. Resolved by {@code EnemyAction.MimicTantrum} in CombatManager.</li>
 *   <li><b>Dash</b> — picks a cardinal direction and dashes in a straight line
 *       until it hits a wall or obstacle, shoving and damaging anything in the
 *       path. Resolved by {@code EnemyAction.MimicDash} in CombatManager.</li>
 * </ul>
 * The alternation is state on the AI instance — each registered Mimic spawn
 * uses its own instance (see {@link #createInstance()}) so the cadence isn't
 * shared between simultaneous mimic fights.
 */
public final class MimicAI implements EnemyAI {

    public static final int TANTRUM_DAMAGE = 10;
    public static final int DASH_DAMAGE = 10;

    /** 8-neighbour adjacency (cardinals + diagonals) for tantrum hops. */
    private static final int[][] ADJACENCY_8 = {
        { 1,  0}, {-1,  0}, { 0,  1}, { 0, -1},
        { 1,  1}, { 1, -1}, {-1,  1}, {-1, -1},
    };

    /** 4-way cardinals for dash direction selection. */
    private static final int[][] CARDINALS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1},
    };

    private final Random rng = new Random();

    /**
     * Alternation state. Starts in TANTRUM so the first player turn facing
     * a fresh mimic sees the flashier move. Flipped after every committed
     * action (tantrum or dash).
     */
    private enum Mode { TANTRUM, DASH }
    private Mode nextMode = Mode.TANTRUM;

    /**
     * Factory — produces a fresh instance per spawn so two mimics in the same
     * arena don't share alternation state. Called from the AIRegistry binding
     * in AbandonedCampsiteEvent.
     */
    public static MimicAI createInstance() {
        return new MimicAI();
    }

    /**
     * Threat preview for the combat HUD's danger-tile overlay. Returns the union of:
     * <ul>
     *   <li><b>Tantrum reach</b> — 8-way BFS flood from the mimic's current tile up to
     *       {@code MAX_TANTRUM_HOPS}, respecting obstacles/void. This is a superset of
     *       any specific randomly-rolled tantrum path, so the player sees every tile
     *       the mimic could plausibly land on next turn.</li>
     *   <li><b>Dash reach</b> — each of the 4 cardinal rays from the mimic up to the
     *       first obstacle or out-of-bounds tile. Matches {@link #decideDashOrIdle}'s
     *       walk-until-blocked logic.</li>
     * </ul>
     * This overrides the generic {@code speed + range} diamond which massively
     * undersells the mimic's actual attack coverage.
     */
    @Override
    public Set<GridPos> computeThreatTiles(CombatEntity self, GridArena arena) {
        Set<GridPos> threat = new HashSet<>();
        GridPos start = self.getGridPos();

        // --- Tantrum reach: 8-way BFS flood up to MAX_TANTRUM_HOPS ---
        final int maxHops = 6; // upper bound from decideTantrum's hopCount range
        Map<GridPos, Integer> dist = new HashMap<>();
        Deque<GridPos> queue = new ArrayDeque<>();
        dist.put(start, 0);
        queue.add(start);
        while (!queue.isEmpty()) {
            GridPos p = queue.poll();
            int d = dist.get(p);
            if (d >= maxHops) continue;
            for (int[] off : ADJACENCY_8) {
                GridPos n = new GridPos(p.x() + off[0], p.z() + off[1]);
                if (!arena.isInBounds(n)) continue;
                if (dist.containsKey(n)) continue;
                var tile = arena.getTile(n);
                if (tile == null) continue;
                if (tile.getType() == TileType.OBSTACLE) continue;
                if (tile.getType() == TileType.VOID) continue;
                dist.put(n, d + 1);
                threat.add(n);
                queue.add(n);
            }
        }

        // --- Dash reach: 4 cardinal rays up to first wall/oob ---
        int maxDashLen = Math.max(arena.getWidth(), arena.getHeight()) + 1;
        for (int[] d : CARDINALS) {
            GridPos cur = start;
            for (int i = 0; i < maxDashLen; i++) {
                GridPos next = new GridPos(cur.x() + d[0], cur.z() + d[1]);
                if (!arena.isInBounds(next)) break;
                var tile = arena.getTile(next);
                if (tile == null || tile.getType() == TileType.OBSTACLE) break;
                threat.add(next);
                cur = next;
            }
        }

        return threat;
    }

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        Mode thisTurn = nextMode;
        nextMode = (nextMode == Mode.TANTRUM) ? Mode.DASH : Mode.TANTRUM;

        if (thisTurn == Mode.TANTRUM) {
            EnemyAction tantrum = decideTantrum(self, arena, playerPos);
            if (tantrum != null) return tantrum;
            // Fallback to dash if we couldn't build a tantrum path
            return decideDashOrIdle(self, arena, playerPos);
        } else {
            EnemyAction dash = decideDashOrIdle(self, arena, playerPos);
            if (dash instanceof EnemyAction.MimicDash) return dash;
            // Fallback to tantrum if no dash direction has any travel
            EnemyAction tantrum = decideTantrum(self, arena, playerPos);
            return tantrum != null ? tantrum : new EnemyAction.Idle();
        }
    }

    /**
     * Build a tantrum hop list: 4–6 random adjacent steps from the mimic's
     * current position. Each step is strictly adjacent to the previous one
     * (8-way neighbourhood). The path INCLUDES the destination tiles only —
     * the starting tile is implied from the mimic's current grid pos.
     * <p>
     * The loop stops as soon as a candidate step lands on the player's tile
     * (that tile IS included in the path — CombatManager handles the collision
     * when it visits it). Otherwise it keeps walking until it's built the
     * target number of hops or runs out of legal neighbours.
     */
    private EnemyAction decideTantrum(CombatEntity self, GridArena arena, GridPos playerPos) {
        int hopCount = 4 + rng.nextInt(3); // 4, 5, or 6
        List<GridPos> path = new ArrayList<>(hopCount);
        GridPos cursor = self.getGridPos();

        for (int hop = 0; hop < hopCount; hop++) {
            List<int[]> shuffled = new ArrayList<>(ADJACENCY_8.length);
            for (int[] d : ADJACENCY_8) shuffled.add(d);
            Collections.shuffle(shuffled, rng);

            GridPos chosen = null;
            for (int[] d : shuffled) {
                GridPos cand = new GridPos(cursor.x() + d[0], cursor.z() + d[1]);
                if (!arena.isInBounds(cand)) continue;
                // Tiles occupied by other enemies block the hop. The player's
                // tile IS allowed — that's how the tantrum scores a hit.
                if (!cand.equals(playerPos) && arena.isOccupied(cand)) continue;
                var tile = arena.getTile(cand);
                if (tile == null) continue;
                if (tile.getType() == TileType.OBSTACLE) continue;
                if (tile.getType() == TileType.VOID) continue;
                chosen = cand;
                break;
            }

            if (chosen == null) {
                // No legal neighbour — stop building. Still ship whatever
                // hops we have (possibly zero) so the handler can decide.
                break;
            }

            path.add(chosen);
            cursor = chosen;

            // If we just stepped onto the player, stop. CombatManager's handler
            // will detect the hit and abort the rest of the list anyway, but
            // there's no point queueing hops past the kill tile.
            if (chosen.equals(playerPos)) break;
        }

        if (path.isEmpty()) return null;
        return new EnemyAction.MimicTantrum(path, TANTRUM_DAMAGE);
    }

    /**
     * Build a dash action, picking the direction that travels farthest AND
     * crosses the player if possible. Returns {@link EnemyAction.Idle} if the
     * mimic can't dash in any direction at all (boxed in on all 4 sides).
     */
    private EnemyAction decideDashOrIdle(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();

        int bestScore = Integer.MIN_VALUE;
        int[] bestDir = null;
        List<int[]> shuffled = new ArrayList<>();
        for (int[] d : CARDINALS) shuffled.add(d);
        Collections.shuffle(shuffled, rng);

        for (int[] d : shuffled) {
            int len = 0;
            boolean crossesPlayer = false;
            GridPos cur = myPos;
            for (int i = 0; i < Math.max(arena.getWidth(), arena.getHeight()) + 1; i++) {
                GridPos next = new GridPos(cur.x() + d[0], cur.z() + d[1]);
                if (!arena.isInBounds(next)) break;
                var tile = arena.getTile(next);
                if (tile == null || tile.getType() == TileType.OBSTACLE) break;
                if (next.equals(playerPos)) crossesPlayer = true;
                cur = next;
                len++;
            }
            if (len == 0) continue;
            int score = len + (crossesPlayer ? 100 : 0);
            if (score > bestScore) {
                bestScore = score;
                bestDir = d;
            }
        }

        if (bestDir == null) return new EnemyAction.Idle();
        return new EnemyAction.MimicDash(bestDir[0], bestDir[1], DASH_DAMAGE);
    }
}
