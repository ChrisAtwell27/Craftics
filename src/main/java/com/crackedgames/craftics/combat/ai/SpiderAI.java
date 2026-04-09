package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Spider AI: 2x2 ambush predator with ceiling drop and web control.
 *
 * Each turn when in range, the spider chooses between:
 * - ATTACK: pounce/melee for direct damage
 * - WEB SHOT: shoot a cobweb near the player (blocks a tile for 2 turns OR applies slow+stun)
 *
 * When too far to reach, ascends to the ceiling and drops near the player next turn.
 */
public class SpiderAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        // If on the ceiling, drop near the player
        if (self.isOnCeiling()) {
            GridPos dropTarget = findDropTarget(arena, playerPos, 2);
            if (dropTarget != null) {
                return new EnemyAction.CeilingDrop(dropTarget, self.getAttackPower());
            }
            return new EnemyAction.Idle();
        }

        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        int size = self.getSize();

        // Adjacent — always bite
        if (dist == 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        // In combat range (2-3 tiles): choose between attack or web
        if (dist <= 3) {
            boolean shootWeb = Math.random() < 0.45;

            if (shootWeb) {
                // WEB SHOT: shoot a cobweb at a tile adjacent to the player
                EnemyAction webAction = tryWebShot(arena, myPos, playerPos);
                if (webAction != null) return webAction;
            }

            // ATTACK: pounce to adjacent tile
            GridPos pounceTarget = findPounceTarget(arena, myPos, playerPos);
            if (pounceTarget != null) {
                return new EnemyAction.Pounce(pounceTarget, self.getAttackPower());
            }

            // Pounce failed — try web as fallback
            if (!shootWeb) {
                EnemyAction webAction = tryWebShot(arena, myPos, playerPos);
                if (webAction != null) return webAction;
            }
        }

        // Can walk to player this turn? Move + attack (size-aware)
        GridPos adjTarget = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, self.getMoveSpeed(), size);
        if (adjTarget != null) {
            List<GridPos> path = Pathfinding.findPathSized(arena, myPos, adjTarget,
                self.getMoveSpeed(), self, size);
            if (!path.isEmpty()) {
                GridPos endPos = path.get(path.size() - 1);
                if (CombatEntity.minDistanceFromSizedEntity(endPos, size, playerPos) <= 1) {
                    return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
                }
                if (dist <= self.getMoveSpeed() + 3) {
                    return new EnemyAction.Move(path);
                }
            }
        }

        // Too far to reach — ascend to ceiling
        return new EnemyAction.CeilingAscend();
    }

    /**
     * Shoot a cobweb at a tile near the player. Two variants chosen randomly:
     * - Direct web spray: ranged attack that slows + stuns the player
     * - Web trap: places an obstacle tile adjacent to the player, cutting off escape
     */
    private EnemyAction tryWebShot(GridArena arena, GridPos myPos, GridPos playerPos) {
        if (Math.random() < 0.5) {
            // Direct web spray at the player — applies slow + stun
            return new EnemyAction.RangedAttack(1, "web_spray");
        } else {
            // Web trap: place an obstacle on a tile adjacent to the player
            GridPos webTile = findWebTrapTile(arena, myPos, playerPos);
            if (webTile != null) {
                return new EnemyAction.CreateTerrain(List.of(webTile), TileType.OBSTACLE, 2);
            }
            // Fallback to direct spray
            return new EnemyAction.RangedAttack(1, "web_spray");
        }
    }

    /**
     * Find a good tile to place a web trap. Prefers tiles that cut off the player's
     * retreat (behind them relative to the spider), then any adjacent empty tile.
     */
    private GridPos findWebTrapTile(GridArena arena, GridPos spiderPos, GridPos playerPos) {
        int dx = Integer.signum(playerPos.x() - spiderPos.x());
        int dz = Integer.signum(playerPos.z() - spiderPos.z());

        // Prioritize: behind the player (away from spider), then sides
        GridPos[] candidates = {
            new GridPos(playerPos.x() - dx, playerPos.z() - dz), // behind player
            new GridPos(playerPos.x() + dz, playerPos.z() - dx), // left flank
            new GridPos(playerPos.x() - dz, playerPos.z() + dx), // right flank
            new GridPos(playerPos.x() + dx, playerPos.z() + dz), // in front of player
        };

        for (GridPos c : candidates) {
            if (arena.isInBounds(c) && !arena.isOccupied(c)) {
                var tile = arena.getTile(c);
                if (tile != null && tile.isWalkable() && tile.getType() == TileType.NORMAL) {
                    return c;
                }
            }
        }
        return null;
    }

    private GridPos findPounceTarget(GridArena arena, GridPos from, GridPos playerPos) {
        int size = 2; // spider is 2x2
        GridPos[] dirs = {
            new GridPos(0, 1), new GridPos(0, -1),
            new GridPos(1, 0), new GridPos(-1, 0)
        };
        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (GridPos dir : dirs) {
            GridPos landing = new GridPos(playerPos.x() + dir.x(), playerPos.z() + dir.z());
            if (!AIUtils.canPlaceFootprint(arena, landing, size)) continue;
            int pDist = from.manhattanDistance(landing);
            if (pDist <= 3 && pDist > 0 && pDist < bestDist) {
                bestDist = pDist;
                best = landing;
            }
        }
        return best;
    }

    private GridPos findDropTarget(GridArena arena, GridPos playerPos, int radius) {
        int size = 2; // spider is 2x2
        List<GridPos> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                GridPos pos = new GridPos(playerPos.x() + dx, playerPos.z() + dz);
                if (!AIUtils.canPlaceFootprint(arena, pos, size)) continue;
                candidates.add(pos);
            }
        }
        if (candidates.isEmpty()) return null;
        for (GridPos c : candidates) {
            if (CombatEntity.minDistanceFromSizedEntity(c, size, playerPos) <= 1) return c;
        }
        Collections.shuffle(candidates);
        return candidates.get(0);
    }
}
