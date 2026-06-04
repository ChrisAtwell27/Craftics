package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Pure-logic helpers for MoreTotems combat effects, kept free of Minecraft runtime
 * types so they are JUnit-testable. {@code CombatManager} calls these.
 */
public final class MoreTotemsEffects {

    private MoreTotemsEffects() {}

    /**
     * Damage one enemy takes from the Explosive totem: half its max HP plus a flat
     * bonus equal to the arena's biome ordinal. Always at least 1.
     */
    public static int explosionDamage(int enemyMaxHp, int biomeOrdinal) {
        int dmg = (enemyMaxHp / 2) + Math.max(0, biomeOrdinal);
        return Math.max(1, dmg);
    }

    /**
     * Pick the safest tile: the candidate whose distance to its NEAREST enemy is the
     * greatest (manhattan). Candidates must already be filtered to valid landing tiles.
     * Ties resolve to the first candidate in iteration order. {@code null} if no candidates.
     * With no enemies, the first candidate is returned.
     */
    public static GridPos safestTile(List<GridPos> candidates, List<GridPos> enemies) {
        GridPos best = null;
        int bestNearest = -1;
        for (GridPos c : candidates) {
            int nearest = Integer.MAX_VALUE;
            for (GridPos e : enemies) {
                nearest = Math.min(nearest, c.manhattanDistance(e));
            }
            if (enemies.isEmpty()) nearest = Integer.MAX_VALUE;
            if (nearest > bestNearest) {
                bestNearest = nearest;
                best = c;
            }
        }
        return best;
    }
}
