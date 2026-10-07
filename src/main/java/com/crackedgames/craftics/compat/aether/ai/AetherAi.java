package com.crackedgames.craftics.compat.aether.ai;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * The few moves more than one Aether creature makes: noticing it has been hurt, standing
 * around while it has not, and finding somewhere to shoot from.
 */
final class AetherAi {

    private AetherAi() {}

    /**
     * Whether anything has hurt this creature yet. Latches on the enraged flag, the way every
     * neutral mob in the game does, so one hit is remembered for the rest of the fight.
     *
     * <p>Lost health counts as well as the damaged-this-turn flag: the flag is cleared when the
     * enemy phase ends, and a creature that was stunned through its turn would otherwise never
     * see it.
     */
    static boolean provoked(CombatEntity self) {
        if (!self.isEnraged()
                && (self.wasDamagedSinceLastTurn() || self.getCurrentHp() < self.getMaxHp())) {
            self.setEnraged(true);
        }
        return self.isEnraged();
    }

    /** What a creature with no quarrel does with its turn: drift a tile, or stand. */
    static EnemyAction loiter(CombatEntity self, GridArena arena) {
        var config = CrafticsMod.CONFIG;
        float chance = config != null ? config.passiveMobWanderChance() : 0.5f;
        if (ThreadLocalRandom.current().nextFloat() < chance) return AIUtils.wander(self, arena);
        return new EnemyAction.Idle();
    }

    /**
     * True when the tile the AI was aimed at holds one of the party's pets rather than a
     * player. The engine points an enemy at a pet by handing its tile over as the target, and
     * the actions that only know how to hit a player have to stand aside for it.
     */
    static boolean isPet(GridArena arena, GridPos target) {
        CombatEntity occupant = arena.getOccupant(target);
        return occupant != null && occupant.isAlly();
    }

    /** Every in-bounds tile within {@code radius} steps of {@code center}, center included. */
    static Set<GridPos> diamond(GridArena arena, GridPos center, int radius) {
        Set<GridPos> tiles = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > radius) continue;
                GridPos tile = new GridPos(center.x() + dx, center.z() + dz);
                if (arena.isInBounds(tile)) tiles.add(tile);
            }
        }
        return tiles;
    }

    /** Whether {@code from} is close enough to {@code target} to shoot, with nothing solid between. */
    /**
     * How far something standing on {@code target} can be thrown along {@code dir} before it
     * meets a wall, the edge, another body or a hazard: 0 to {@code maxTiles}. A throw is
     * shortened rather than ended in a pit, so the wind itself never does the killing.
     */
    static int roomBehind(GridArena arena, GridPos target, int[] dir, int maxTiles) {
        int room = 0;
        for (int i = 1; i <= maxTiles; i++) {
            GridPos tile = new GridPos(target.x() + dir[0] * i, target.z() + dir[1] * i);
            if (!arena.isInBounds(tile) || arena.isOccupied(tile)) break;
            com.crackedgames.craftics.core.GridTile ground = arena.getTile(tile);
            if (ground == null || !ground.isWalkable() || AIUtils.isHazardTile(arena, tile)) break;
            room = i;
        }
        return room;
    }

    static boolean hasShot(GridArena arena, GridPos from, GridPos target, int range) {
        return from.manhattanDistance(target) <= range && Pathfinding.hasLineOfSight(arena, from, target);
    }

    /**
     * The best tile this creature can reach this turn and shoot from, or null when there is
     * none. Best means furthest from everything that could hit it back: a shooter standing
     * next to its target has already lost the argument.
     *
     * @param keepAway a tile this close to a threat is only taken if nothing better exists
     * @param worthIt  anything else the shot needs from where it is taken
     */
    static GridPos firingTile(CombatEntity self, GridArena arena, GridPos target,
                              List<GridPos> threats, int range, int keepAway,
                              Predicate<GridPos> worthIt) {
        GridPos myPos = self.getGridPos();
        GridPos best = null;
        int bestScore = Integer.MIN_VALUE;
        for (GridPos candidate : Pathfinding.getReachableTiles(arena, myPos, self.getMoveSpeed(), self)) {
            if (candidate.equals(myPos) || !hasShot(arena, candidate, target, range)) continue;
            if (!worthIt.test(candidate)) continue;
            int gap = AIUtils.minThreatDistance(candidate, threats);
            int score = Math.min(gap, range) * 5;
            if (gap <= keepAway) score -= 15;
            if (AIUtils.isHazardTile(arena, candidate)) score -= 25;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    /** The walk to {@code tile}, or an empty path when there is none this turn. */
    static List<GridPos> walkTo(CombatEntity self, GridArena arena, GridPos tile) {
        if (tile == null) return List.of();
        return Pathfinding.findPathSized(arena, self.getGridPos(), tile, self.getMoveSpeed(), self);
    }

    /** As far toward {@code target} as this turn's movement goes, or an empty path. */
    static List<GridPos> stepToward(CombatEntity self, GridArena arena, GridPos target) {
        GridPos myPos = self.getGridPos();
        GridPos closest = Pathfinding.findClosestReachableTo(
            arena, myPos, target, self.getMoveSpeed(), self, self.getSizeX(), self.getSizeZ());
        if (closest == null || closest.equals(myPos)) return List.of();
        return walkTo(self, arena, closest);
    }

    /** The walk away from everything in {@code threats}, or an empty path when cornered. */
    static List<GridPos> backAway(CombatEntity self, GridArena arena, List<GridPos> threats) {
        return walkTo(self, arena, AIUtils.bestRetreatTile(self, arena, threats));
    }
}
