package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Endermite AI: Tiny void pest with short-range blink.
 * - BLINK: teleports 2-3 tiles to get adjacent to the player
 * - SWARM: attacks immediately on adjacency, never idles
 * - ERRATIC: if can't blink to player, blinks to a random nearby tile then rushes
 * - Distinct from silverfish — no flanking, instead uses short teleports
 */
public class EndermiteAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Adjacent — attack immediately
        if (dist == 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        // Within blink range (2-3) — teleport adjacent and attack
        if (dist <= 3) {
            GridPos landing = findAdjacentLanding(arena, playerPos);
            if (landing != null) {
                return new EnemyAction.TeleportAndAttack(landing, self.getAttackPower());
            }
        }

        // Too far for blink — walk toward the player, then try blink next turn
        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, self.getMoveSpeed());
        if (target == null) target = playerPos;

        List<GridPos> path = Pathfinding.findPath(arena, myPos, target, self.getMoveSpeed(), self);
        if (path.isEmpty()) return AIUtils.seekOrWander(self, arena, playerPos);

        GridPos endPos = path.get(path.size() - 1);
        // If we walked into adjacency, attack
        if (endPos.manhattanDistance(playerPos) == 1) {
            return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
        }
        // If we're now within blink range after moving, blink instead
        if (endPos.manhattanDistance(playerPos) <= 3) {
            GridPos landing = findAdjacentLanding(arena, playerPos);
            if (landing != null) {
                return new EnemyAction.TeleportAndAttack(landing, self.getAttackPower());
            }
        }
        return new EnemyAction.Move(path);
    }

    /** Find a walkable, unoccupied tile adjacent to the player to blink to. */
    private GridPos findAdjacentLanding(GridArena arena, GridPos playerPos) {
        List<GridPos> candidates = new ArrayList<>();
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            GridPos landing = new GridPos(playerPos.x() + d[0], playerPos.z() + d[1]);
            if (arena.isInBounds(landing) && !arena.isOccupied(landing)) {
                var tile = arena.getTile(landing);
                if (tile != null && tile.isWalkable()) {
                    candidates.add(landing);
                }
            }
        }
        if (candidates.isEmpty()) return null;
        Collections.shuffle(candidates);
        return candidates.get(0);
    }
}
