package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Revenant's Burrow is a two-turn sequence: turn N emits a top-level Burrow (which the manager
 * turns into "digs under" + untargetable), turn N+1 emits Surface ("erupts from the grave").
 *
 * <p>The bug this guards: on the turn a telegraphed ability resolves, {@code BossAI.decideAction}
 * calls {@code chooseAbility} a SECOND time for a follow-up action. When that follow-up came back as
 * Burrow, it was bundled into a {@code CompositeAction} alongside the resolved action. Burrow is a
 * turn-owning action that the CompositeAction dispatch path silently drops, so the boss never
 * actually buried (no untargetable window, no "digs under" message) yet its {@code burrowed} flag
 * was left set, and it "erupted from the grave" a turn later out of a dig that never happened.
 *
 * <p>Pure state machine only: the untargetable flag / grid removal live in CombatManager and need a
 * ServerWorld, so those are covered by the in-game checklist. This test asserts the AI never emits a
 * Burrow packed inside a CompositeAction, and that a discarded follow-up Burrow does not consume the
 * ability.
 */
class RevenantBurrowSequenceTest {

    private static final int SIZE = 12;

    private static GridArena arena() {
        GridTile[][] tiles = new GridTile[SIZE][SIZE];
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                tiles[x][z] = new GridTile(TileType.NORMAL, null);
            }
        }
        return new GridArena(SIZE, SIZE, tiles, BlockPos.ORIGIN, 1, new GridPos(1, 1));
    }

    private static CombatEntity boss(int x, int z) {
        CombatEntity b = new CombatEntity(100, "minecraft:zombie", new GridPos(x, z),
            /* maxHp */ 60, /* attack */ 3, /* defense */ 0, /* range */ 1,
            /* sizeOverride */ 1, /* speedOverride */ 1);
        b.setBoss(true);
        return b;
    }

    private static void setPlayer(GridArena a, GridPos p) {
        a.setPlayerGridPos(p);
        a.setAllPlayerGridPositions(List.of(p));
    }

    /** Collect every action in a (possibly composite) decision, flattened. */
    private static List<EnemyAction> flatten(EnemyAction action) {
        if (action instanceof EnemyAction.CompositeAction ca) {
            java.util.List<EnemyAction> out = new java.util.ArrayList<>();
            for (EnemyAction sub : ca.actions()) out.addAll(flatten(sub));
            return out;
        }
        return List.of(action);
    }

    /**
     * Drive the boss with a player pinned adjacent (so a warning-resolve follow-up would pick
     * Burrow) across many turns. A Burrow must NEVER appear inside a CompositeAction: it is a
     * top-level, turn-owning action. When it does fire, the very next decision must be Surface.
     */
    @Test
    void burrowNeverCollapsesIntoACompositeAndAlwaysPairsWithSurface() {
        GridArena a = arena();
        RevenantAI ai = new RevenantAI();

        GridPos bossPos = new GridPos(5, 5);
        CombatEntity self = boss(bossPos.x(), bossPos.z());
        a.placeEntity(self);
        ai.registerGrave(new GridPos(3, 5));
        ai.registerGrave(new GridPos(7, 5));

        GridPos player = new GridPos(6, 5); // adjacent every turn
        setPlayer(a, player);

        boolean sawTopLevelBurrow = false;
        for (int turn = 0; turn < 20; turn++) {
            EnemyAction action = ai.decideAction(self, a, player);

            // A Burrow must always be the whole decision, never a CompositeAction sub-action.
            for (EnemyAction sub : flatten(action)) {
                if (sub instanceof EnemyAction.Burrow) {
                    assertFalse(action instanceof EnemyAction.CompositeAction,
                        "Burrow leaked into a CompositeAction on turn " + turn
                            + " - it would be silently dropped by the dispatcher");
                }
            }

            if (action instanceof EnemyAction.Burrow) {
                sawTopLevelBurrow = true;
                assertTrue(ai.isBurrowed(), "the flag must be set the turn Burrow is emitted");

                // Next decision must be Surface (the second half of the two-turn sequence).
                EnemyAction next = ai.decideAction(self, a, player);
                assertInstanceOf(EnemyAction.Surface.class, next,
                    "the turn after a Burrow must Surface, not do anything else");
                // The AI still believes it is burrowed until CombatManager.surfaceEnemy clears the
                // flag; that clear needs a ServerWorld and is covered by the in-game checklist.
            }
        }

        assertTrue(sawTopLevelBurrow,
            "with the player pinned adjacent the boss must eventually emit a real top-level Burrow");
    }
}
