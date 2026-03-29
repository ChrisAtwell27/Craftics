package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;

import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.List;

/**
 * Magma Cube AI: Bouncing lava blob that ignores obstacles and leaves fire.
 * - BOUNCE: moves by hopping up to 3 tiles, ignores obstacles (flies over them)
 * - FIRE TRAIL: leaves 1-turn fire tile on every tile it bounces through
 * - SPLIT ON DEATH: when killed, spawns 2 smaller cubes (handled by CombatManager)
 * - Always attacks on landing if adjacent to player
 */
public class MagmaCubeAI implements EnemyAI {
    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Adjacent — slam attack
        if (dist == 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        // Bounce toward player (ignores obstacles like phantom, up to 3 tiles)
        // Find best landing spot adjacent to or near the player
        int bounceRange = 3;
        GridPos bestLanding = null;
        int bestDist = Integer.MAX_VALUE;

        for (int dx = -bounceRange; dx <= bounceRange; dx++) {
            for (int dz = -bounceRange; dz <= bounceRange; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > bounceRange || (dx == 0 && dz == 0)) continue;
                GridPos landing = new GridPos(myPos.x() + dx, myPos.z() + dz);
                if (!arena.isInBounds(landing) || arena.isEnemyOccupied(landing)) continue;
                var tile = arena.getTile(landing);
                if (tile == null || !tile.isWalkable()) continue;

                int landDist = landing.manhattanDistance(playerPos);
                if (landDist < bestDist) {
                    bestDist = landDist;
                    bestLanding = landing;
                }
            }
        }

        if (bestLanding != null) {
            // Build bounce path (straight line from current to landing for fire trail)
            List<GridPos> bouncePath = buildBouncePath(myPos, bestLanding);
            // Leave fire on the tiles we bounce through
            List<GridPos> fireTiles = new ArrayList<>(bouncePath);
            fireTiles.remove(fireTiles.size() - 1); // don't fire the landing tile

            if (bestDist == 1) {
                // Landing adjacent to player — bounce + attack
                // Use composite: create fire trail + move + attack
                List<EnemyAction> actions = new ArrayList<>();
                if (!fireTiles.isEmpty()) {
                    actions.add(new EnemyAction.CreateTerrain(fireTiles, TileType.FIRE, 1));
                }
                return new EnemyAction.Pounce(bestLanding, self.getAttackPower());
            } else {
                // Bounce closer but not adjacent
                List<EnemyAction> actions = new ArrayList<>();
                if (!fireTiles.isEmpty()) {
                    actions.add(new EnemyAction.CreateTerrain(fireTiles, TileType.FIRE, 1));
                }
                actions.add(new EnemyAction.Move(bouncePath));
                return actions.size() == 1 ? actions.get(0) : new EnemyAction.CompositeAction(actions);
            }
        }

        return AIUtils.seekOrWander(self, arena, playerPos);
    }

    /** Build a straight-line path from start to end (for fire trail calculation). */
    private List<GridPos> buildBouncePath(GridPos from, GridPos to) {
        List<GridPos> path = new ArrayList<>();
        int dx = Integer.signum(to.x() - from.x());
        int dz = Integer.signum(to.z() - from.z());
        GridPos current = from;
        int steps = Math.max(Math.abs(to.x() - from.x()), Math.abs(to.z() - from.z()));
        for (int i = 0; i < steps; i++) {
            current = new GridPos(current.x() + dx, current.z() + dz);
            path.add(current);
        }
        // Make sure destination is in the path
        if (path.isEmpty() || !path.get(path.size() - 1).equals(to)) {
            path.add(to);
        }
        return path;
    }
}
