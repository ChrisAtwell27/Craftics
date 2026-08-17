package com.crackedgames.craftics.compat.takesapillage;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Skirmisher AI: the vindicator's rook charge on legs that can also just walk.
 *
 * <p>The vindicator is all-or-nothing: it moves ONLY in straight dash lines, so denying it
 * a lane parks it. The skirmisher keeps the scary half - when a cardinal lane to the player
 * is open it charges down it at unlimited distance, damage growing with tiles traveled -
 * but when no lane exists it doesn't sulk into dash-and-adjust geometry; it walks at the
 * player like an ordinary fast mob (3 SPD from its registration). The counterplay shifts
 * from "break the lane" to "break the lane OR keep distance", which is what makes it a
 * skirmisher rather than a budget vindicator.
 *
 * <p>Charge rules match the vindicator's dash: cardinal only, blocked by walls, obstacles,
 * hazards and other enemies, +1 damage per tile beyond 2. No rage mechanic - the stat line
 * gave up the berserker half along with the attack and defense points.
 */
public class SkirmisherAI implements EnemyAI {

    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int baseDamage = self.getAttackPower();

        // Adjacent: swing.
        if (self.minDistanceTo(playerPos) == 1) {
            return new EnemyAction.Attack(baseDamage);
        }

        // Rook charge: if a clear cardinal lane reaches the player, take it - the whole
        // lane in one action, stopping adjacent, charge bonus per tile beyond 2.
        EnemyAction charge = tryCharge(arena, myPos, playerPos, baseDamage);
        if (charge != null) return charge;

        // No lane - walk like anyone else. Pathfinding at natural speed (3), which is the
        // half the vindicator doesn't have.
        return AIUtils.seekOrWander(self, arena, playerPos);
    }

    /** A lane charge that ends adjacent to the player, or null when no cardinal lane is open. */
    private EnemyAction tryCharge(GridArena arena, GridPos myPos, GridPos playerPos, int baseDamage) {
        for (int[] dir : DIRECTIONS) {
            List<GridPos> path = new ArrayList<>();
            GridPos current = myPos;
            while (true) {
                GridPos next = new GridPos(current.x() + dir[0], current.z() + dir[1]);
                if (next.equals(playerPos)) {
                    // Lane reaches the player: stop on the tile before them and hit.
                    int chargeDamage = baseDamage + Math.max(0, path.size() - 2);
                    if (path.isEmpty()) return new EnemyAction.Attack(chargeDamage);
                    return new EnemyAction.MoveAndAttack(path, chargeDamage);
                }
                if (!arena.isInBounds(next)) break;
                var tile = arena.getTile(next);
                if (tile == null || !tile.isWalkable()) break;
                if (AIUtils.isHazardTile(arena, next)) break;
                if (arena.isEnemyOccupied(next)) break;
                path.add(next);
                current = next;
            }
        }
        return null;
    }
}
