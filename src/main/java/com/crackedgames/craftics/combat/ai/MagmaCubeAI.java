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

        // Adjacent - slam attack
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
                // Validate the WHOLE footprint, not just the anchor tile. A magma
                // cube is 2x2, so an anchor-only check let one of its other three
                // tiles land on the player or another mob (clipping into it).
                // The cube's own tiles count as free - it vacates them mid-bounce.
                if (!AIUtils.canPlaceFootprintIgnoringSelf(arena, landing, self)) continue;

                // Distance from the cube's would-be footprint to the player, so a
                // 2x2 cube correctly counts as "adjacent" (landDist 1) when any of
                // its tiles touches the player rather than measuring from the corner.
                int landDist = CombatEntity.minDistanceFromSizedEntity(
                    landing, self.getSizeX(), self.getSizeZ(), playerPos);
                if (landDist < bestDist) {
                    bestDist = landDist;
                    bestLanding = landing;
                }
            }
        }

        if (bestLanding != null) {
            // Build bounce path (straight line from current to landing for fire trail)
            List<GridPos> bouncePath = buildBouncePath(myPos, bestLanding);
            // Leave fire on the tiles we bounce through - only where something
            // can actually burn (plain walkable floor, not obstacles or water).
            List<GridPos> fireTiles = new ArrayList<>();
            for (int i = 0; i < bouncePath.size() - 1; i++) { // skip the landing tile
                GridPos tile = bouncePath.get(i);
                var gt = arena.getTile(tile);
                if (gt != null && gt.isWalkable() && gt.getType() == TileType.NORMAL
                        && !arena.isOccupied(tile)) {
                    fireTiles.add(tile);
                }
            }

            // The fire trail rides a CompositeAction alongside the bounce itself.
            // (The old code dropped the trail on attack bounces and - because the
            // composite dispatcher ignored Move sub-actions - dropped the MOVE on
            // trail bounces, leaving the cube burning the floor without moving.)
            EnemyAction bounce = bestDist == 1
                ? new EnemyAction.Pounce(bestLanding, self.getAttackPower())
                : new EnemyAction.Move(bouncePath);
            if (fireTiles.isEmpty()) {
                return bounce;
            }
            return new EnemyAction.CompositeAction(List.of(
                new EnemyAction.CreateTerrain(fireTiles, TileType.FIRE, 1), bounce));
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
