package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Phantom AI: Aerial dive bomber with stacking speed.
 * - SWOOP: flies in a straight line, damages player if in path, flies over obstacles
 * - CIRCLING: if can't line up a swoop, repositions to align (never wanders aimlessly)
 * - STACKING SPEED: +1 speed each turn it doesn't hit the player (resets on hit).
 *   The streak lives in the entity's AI memory - the old instance field sat on
 *   the shared AI object, so every phantom on the server pooled one streak.
 * - Base speed 4
 */
public class PhantomAI implements EnemyAI {
    private static final String MISS_STREAK = "phantom_miss_streak";

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int missStreak = self.getAiMemory(MISS_STREAK, 0);
        int swoopRange = self.getMoveSpeed() + missStreak; // stacking speed

        // Try to swoop through the player
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            List<GridPos> swoopPath = buildSwoopPath(arena, myPos, dir[0], dir[1], swoopRange);
            if (swoopPathHitsPlayer(swoopPath, playerPos) && !swoopPath.isEmpty()) {
                self.setAiMemory(MISS_STREAK, 0); // reset on hit
                return new EnemyAction.Swoop(swoopPath, self.getAttackPower());
            }
        }

        // Missed - increment speed stack
        self.setAiMemory(MISS_STREAK, missStreak + 1);

        // Can't hit player from here - reposition to align for next swoop
        GridPos alignTarget = findAlignmentTarget(arena, myPos, playerPos, swoopRange);
        if (alignTarget != null) {
            List<GridPos> path = new ArrayList<>();
            path.add(alignTarget);
            return new EnemyAction.Move(path);
        }

        return AIUtils.seekOrWander(self, arena, playerPos);
    }

    private List<GridPos> buildSwoopPath(GridArena arena, GridPos start, int dx, int dz, int maxLen) {
        List<GridPos> path = new ArrayList<>();
        GridPos current = start;
        for (int i = 0; i < maxLen; i++) {
            GridPos next = new GridPos(current.x() + dx, current.z() + dz);
            if (!arena.isInBounds(next)) break;
            // Phantoms fly over everything - only need to be in bounds
            path.add(next);
            current = next;
        }
        return path;
    }

    private boolean swoopPathHitsPlayer(List<GridPos> path, GridPos playerPos) {
        for (GridPos pos : path) {
            if (pos.equals(playerPos)) return true;
        }
        return false;
    }

    private GridPos findAlignmentTarget(GridArena arena, GridPos self, GridPos playerPos, int range) {
        GridPos best = null;
        int bestScore = Integer.MAX_VALUE;

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > range) continue;
                if (dx == 0 && dz == 0) continue;

                GridPos candidate = new GridPos(self.x() + dx, self.z() + dz);
                // isOccupied, not isEnemyOccupied - the old check let the
                // phantom park itself on the player's or an ally's tile.
                if (!arena.isInBounds(candidate) || arena.isOccupied(candidate)) continue;

                // Check if this position can swoop through the player
                boolean canSwoop = (candidate.x() == playerPos.x() || candidate.z() == playerPos.z());
                if (canSwoop) {
                    int dist = candidate.manhattanDistance(playerPos);
                    // Prefer 2-4 distance for a good swoop run
                    if (dist >= 2 && dist <= range && dist < bestScore) {
                        bestScore = dist;
                        best = candidate;
                    }
                }
            }
        }

        return best;
    }
}
