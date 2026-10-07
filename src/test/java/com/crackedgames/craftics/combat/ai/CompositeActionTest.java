package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How a bundled boss action is unpacked before it runs.
 *
 * <p>The bugs these guard: three boss charges were built as a charge bundled with something else
 * (a fire trail, a follow-up step, or just a wrapper), and the bundle runner only knew how to
 * start a plain walk. The Rockbreaker's knockback charge was dropped outright - and it was
 * wrapped twice, so the drop happened two layers down where nothing looked. The Bastion Brute's
 * and the Wither's swoop charges were started and cancelled in the same breath, with no damage.
 */
class CompositeActionTest {

    private static final List<GridPos> LANE = List.of(new GridPos(1, 0), new GridPos(2, 0));

    @Test
    void nestedBundlesUnpackIntoOneOrderedList() {
        EnemyAction charge = new EnemyAction.MoveAndAttackWithKnockback(LANE, 7, 3);
        EnemyAction trail = new EnemyAction.CreateTerrain(LANE, TileType.FIRE, 2);
        EnemyAction step = new EnemyAction.Move(LANE);
        // A resolved warning that was itself a bundle, bundled again with the follow-up action.
        EnemyAction nested = new EnemyAction.CompositeAction(List.of(
            new EnemyAction.CompositeAction(List.of(charge, trail)), step));

        assertEquals(List.of(charge, trail, step), EnemyAction.flatten(nested));
    }

    @Test
    void aLoneActionUnpacksToItself() {
        EnemyAction idle = new EnemyAction.Idle();
        assertEquals(List.of(idle), EnemyAction.flatten(idle));
    }

    @Test
    void everyChargeAndStrikeThatAnimatesOwnsTheTurn() {
        assertTrue(EnemyAction.drivesTurn(new EnemyAction.Move(LANE)));
        assertTrue(EnemyAction.drivesTurn(new EnemyAction.MoveAndAttack(LANE, 5)));
        assertTrue(EnemyAction.drivesTurn(new EnemyAction.Pounce(new GridPos(3, 3), 5)));
        // The three that were being dropped or cancelled inside a bundle:
        assertTrue(EnemyAction.drivesTurn(new EnemyAction.Swoop(LANE, 9)));
        assertTrue(EnemyAction.drivesTurn(new EnemyAction.MoveAndAttackWithKnockback(LANE, 7, 3)));
        assertTrue(EnemyAction.drivesTurn(new EnemyAction.AttackWithKnockback(7, 3)));
    }

    @Test
    void instantEffectsDoNotOwnTheTurn() {
        assertFalse(EnemyAction.drivesTurn(new EnemyAction.CreateTerrain(LANE, TileType.FIRE, 2)));
        assertFalse(EnemyAction.drivesTurn(new EnemyAction.TileAreaAttack(LANE, LANE.get(0), 5, null)));
        assertFalse(EnemyAction.drivesTurn(new EnemyAction.Idle()));
    }
}
