package com.crackedgames.craftics.compat.aether.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * Cockatrice AI: a needle-spitting bird that will not let you get close.
 * - SHOOT: with its target in range and nothing solid between them, it fires. The needle is
 *   weak; the poison it carries (the jungle theme tag) is the point
 * - SHOOT AND SCOOT: with anything within {@link #KITE_THRESHOLD} tiles, it fires first and
 *   then spends its movement getting away, all in one turn
 * - REPOSITION: with no shot, it walks to the tile with the best one and fires next turn
 *
 * A cockatrice never moves and then shoots. The engine only treats a move-then-attack as a
 * shot for mobs on its own hardcoded ranged list, and for anything else resolves it as a
 * melee swing that needs the target adjacent. Firing before moving needs no such listing.
 */
public class CockatriceAI implements EnemyAI {

    /** A threat this close is too close. */
    public static final int KITE_THRESHOLD = 2;

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int range = Math.max(1, self.getRange());
        // Pets count: a wolf at its feet makes a cockatrice back off just as a player does.
        List<GridPos> threats = AIUtils.threatPositions(arena, playerPos);
        boolean shot = AetherAi.hasShot(arena, myPos, playerPos, range);
        EnemyAction fire = new EnemyAction.RangedAttack(self.getAttackPower(), AechorPlantAI.NEEDLE);

        if (AIUtils.minThreatDistance(myPos, threats) <= KITE_THRESHOLD) {
            List<GridPos> escape = AetherAi.backAway(self, arena, threats);
            if (!escape.isEmpty()) {
                // The bundled shot always goes to a player, so when the engine has aimed this
                // cockatrice at a pet it only runs. It shoots the pet properly once it is clear.
                if (shot && !AetherAi.isPet(arena, playerPos)) {
                    return new EnemyAction.CompositeAction(List.of(fire, new EnemyAction.Move(escape)));
                }
                return new EnemyAction.Move(escape);
            }
            // Cornered: fall through and stand its ground.
        }

        if (shot) return fire;

        List<GridPos> toPerch = AetherAi.walkTo(self, arena,
            AetherAi.firingTile(self, arena, playerPos, threats, range, KITE_THRESHOLD, tile -> true));
        if (!toPerch.isEmpty()) return new EnemyAction.Move(toPerch);

        // Nothing to shoot from this turn: close the distance like anything else would.
        return AIUtils.seekOrWander(self, arena, playerPos);
    }
}
