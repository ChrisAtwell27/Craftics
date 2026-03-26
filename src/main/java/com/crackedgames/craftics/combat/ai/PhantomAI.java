package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Phantom AI: Aerial dive bomber — swoops in lines across the arena.
 * - SWOOP: flies up to 4 tiles in a straight line, damaging player if in path
 * - CAN FLY OVER obstacles and other entities (phantoms are airborne)
 * - CIRCLING: if can't line up, repositions to align for next swoop
 * - Speed 4 makes them very mobile — always repositioning
 */
public class PhantomAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();

        // Try to swoop through the player
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            List<GridPos> swoopPath = buildSwoopPath(arena, myPos, dir[0], dir[1], self.getMoveSpeed());
            if (swoopPathHitsPlayer(swoopPath, playerPos) && !swoopPath.isEmpty()) {
                return new EnemyAction.Swoop(swoopPath, self.getAttackPower());
            }
        }

        // Can't hit player from here — reposition to align
        GridPos alignTarget = findAlignmentTarget(arena, myPos, playerPos, self.getMoveSpeed());
        if (alignTarget != null) {
            // Move to the alignment position (phantom teleports for repositioning)
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
            // Phantoms fly over everything — only need to be in bounds
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
                if (!arena.isInBounds(candidate) || arena.isEnemyOccupied(candidate)) continue;

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
