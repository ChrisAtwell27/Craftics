package com.crackedgames.craftics.combat.shadow;

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
 * A Shadow taking real turns on a real arena, with the kit of a Shadow of nobody: default AP
 * and Speed, bare fists.
 *
 * <p>The planner and the balance numbers have their own tests. This is the join between them
 * and the turn machine: one enemy action per ask, and an honest answer to "do you want
 * another?".
 *
 * <p>What a weapon DOES when it lands is not decided here and not tested here. The AI names
 * the weapon and the size of the first hit; the weapon's own code does the rest, in game.
 */
class ShadowCloneAITest {

    private static final GridPos SHADOW_START = new GridPos(0, 1);

    /** 8 wide, 3 deep, all open floor, with the player standing at {@code playerAt}. */
    private static GridArena arena(GridPos playerAt) {
        GridTile[][] tiles = new GridTile[8][3];
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 3; z++) {
                tiles[x][z] = new GridTile(TileType.NORMAL, null);
            }
        }
        return new GridArena(8, 3, tiles, BlockPos.ORIGIN, 1, playerAt);
    }

    /** A boss body with attack 10 and Speed 3, standing at the west end of the arena. */
    private static CombatEntity body() {
        return new CombatEntity(9, "craftics:shadow_clone", SHADOW_START, 75, 10, 4, 1, 1, 3);
    }

    private static final ShadowWeapon FIST =
        new ShadowWeapon(ShadowKit.FIST_SLOT, "fist", 1, 1, 1, false);

    /** Asserts {@code action} is the Shadow attacking with a weapon, and returns it. */
    private static EnemyAction.CustomAction assertStrike(EnemyAction action, String message) {
        EnemyAction.CustomAction strike = assertInstanceOf(EnemyAction.CustomAction.class, action, message);
        assertEquals(ShadowActions.STRIKE, strike.id(), message);
        return strike;
    }

    /** A Shadow with bare fists and the given items, {@code ap} AP and Speed 3. */
    private static ShadowCloneAI shadowCarrying(int ap, ShadowItem... items) {
        ShadowCloneAI ai = new ShadowCloneAI();
        ai.bind(ShadowKit.ofParts(ap, 3, List.of(FIST), List.of(items)), true);
        return ai;
    }

    @Test
    void throwsASnowballThenKnocksItsTargetBack() {
        ShadowItem snowball = ShadowSpells.forThrowable("minecraft:snowball", 5, 1, 1);
        ShadowCloneAI ai = shadowCarrying(4, snowball);
        CombatEntity self = body();
        GridPos player = new GridPos(3, 1);
        GridArena arena = arena(player);

        EnemyAction.TileAreaAttack hit =
            assertInstanceOf(EnemyAction.TileAreaAttack.class, ai.decideAction(self, arena, player));
        assertEquals(List.of(player), hit.tiles(), "one tile: the one it was thrown at");
        // A 1-AP throw is three tenths of the boss's attack of 10, and a snowball is half as
        // good as a weapon: 1.5, rounded up to 2.
        assertEquals(2, hit.damage());
        assertEquals("a_snowball", hit.effectName());
        assertTrue(ai.wantsAnotherAction(self));

        EnemyAction.ForcedMovement shove =
            assertInstanceOf(EnemyAction.ForcedMovement.class, ai.decideAction(self, arena, player));
        assertEquals(-1, shove.targetEntityId(), "the player");
        assertEquals(1, shove.dx(), "away from the Shadow, which is to the west");
        assertEquals(0, shove.dz());
        assertEquals(1, shove.tiles());
    }

    @Test
    void aThrowableRunsOut() {
        ShadowItem snowballs = ShadowSpells.forThrowable("minecraft:snowball", 5, 1, 2);
        ShadowCloneAI ai = shadowCarrying(6, snowballs);
        CombatEntity self = body();
        GridPos player = new GridPos(3, 1);
        GridArena arena = arena(player);

        int thrown = 0;
        for (int guard = 0; guard < 12; guard++) {
            EnemyAction action = ai.decideAction(self, arena, player);
            if (action instanceof EnemyAction.TileAreaAttack) thrown++;
            if (!ai.wantsAnotherAction(self)) break;
        }

        assertEquals(2, thrown, "it was carrying two");
    }

    @Test
    void immolationLandsOnTheTargetAndThoseBesideItAndCarriesTheBurn() {
        ShadowItem immolation = ShadowSpells.forSherd("minecraft:burn_pottery_sherd", 5, 4, 3);
        ShadowCloneAI ai = shadowCarrying(4, immolation);
        CombatEntity self = body();
        GridPos player = new GridPos(3, 1);

        EnemyAction.TileAreaAttack burst = assertInstanceOf(EnemyAction.TileAreaAttack.class,
            ai.decideAction(self, arena(player), player));

        assertEquals(9, burst.tiles().size(), "the target and the eight tiles round it");
        assertEquals(player, burst.center());
        assertEquals(10, burst.damage(), "a 4-AP sherd lands for the boss's whole attack");
        assertEquals("immolation:rider:BURNING,3,1", burst.effectName());
    }

    @Test
    void aSpellNeverLandsOnTheShadowItself() {
        // Tidal Surge goes off around the caster. The caster is not in it.
        ShadowItem surge = ShadowSpells.forSherd("minecraft:flow_pottery_sherd", 5, 4, 0);
        ShadowCloneAI ai = shadowCarrying(4, surge);
        CombatEntity self = body();
        GridPos player = new GridPos(2, 1);

        EnemyAction.TileAreaAttack burst = assertInstanceOf(EnemyAction.TileAreaAttack.class,
            ai.decideAction(self, arena(player), player));

        assertFalse(burst.tiles().contains(SHADOW_START));
        assertTrue(burst.tiles().contains(player));
        assertEquals(SHADOW_START, burst.center());
    }

    @Test
    void soulDrainHealsTheShadowForHalfOfWhatItDeals() {
        ShadowItem drain = ShadowSpells.forSherd("minecraft:mourner_pottery_sherd", 5, 4, 3);
        ShadowCloneAI ai = shadowCarrying(4, drain);
        CombatEntity self = body();
        self.takeDamage(20);
        int before = self.getCurrentHp();
        GridPos player = new GridPos(3, 1);

        ai.decideAction(self, arena(player), player);

        assertEquals(before + 5, self.getCurrentHp(), "half of the 10 it deals");
    }

    @Test
    void riptideHookDragsItsTargetTowardTheShadow() {
        ShadowItem hook = ShadowSpells.forSherd("minecraft:angler_pottery_sherd", 5, 3, 3);
        ShadowCloneAI ai = shadowCarrying(3, hook);
        CombatEntity self = body();
        GridPos player = new GridPos(3, 1);
        GridArena arena = arena(player);

        assertInstanceOf(EnemyAction.TileAreaAttack.class, ai.decideAction(self, arena, player));
        ai.wantsAnotherAction(self);
        EnemyAction.ForcedMovement drag =
            assertInstanceOf(EnemyAction.ForcedMovement.class, ai.decideAction(self, arena, player));

        assertEquals(-1, drag.dx(), "west, toward the Shadow");
        assertEquals(2, drag.tiles());
    }

    @Test
    void throwsAnEnderPearlToLandBesideAFarTarget() {
        ShadowItem pearl = ShadowSpells.forThrowable("minecraft:ender_pearl", 5, 1, 1);
        ShadowCloneAI ai = shadowCarrying(3, pearl);
        CombatEntity self = body();
        GridPos player = new GridPos(7, 1);

        EnemyAction.Teleport blink = assertInstanceOf(EnemyAction.Teleport.class,
            ai.decideAction(self, arena(player), player));

        assertEquals(1, blink.target().manhattanDistance(player), "right beside the player");
        assertTrue(ai.wantsAnotherAction(self), "and it still has AP to swing with");
    }

    @Test
    void callsUpSeekerVexes() {
        ShadowItem vexes = ShadowSpells.forSherd("minecraft:archer_pottery_sherd", 5, 3, 0);
        ShadowCloneAI ai = shadowCarrying(3, vexes);
        CombatEntity self = body();
        GridPos player = new GridPos(5, 1);

        EnemyAction.SummonMinions summon = assertInstanceOf(EnemyAction.SummonMinions.class,
            ai.decideAction(self, arena(player), player));

        assertEquals("minecraft:vex", summon.entityTypeId());
        assertEquals(2, summon.count());
        assertEquals(2, summon.positions().size());
    }

    @Test
    void canBeBuiltBeforeTheGameHasLoaded() {
        // The AI registry makes one while the game is still starting up, before there is a
        // config or an item registry to ask. It must stand on its own.
        ShadowCloneAI ai = new ShadowCloneAI();

        assertEquals(1, ai.kit().weapons().size(), "bare fists and nothing else");
        assertFalse(ai.wantsAnotherAction(body()));
    }

    @Test
    void spendsEveryApOnASwingWhenTheTargetIsBesideIt() {
        ShadowCloneAI ai = new ShadowCloneAI();
        CombatEntity self = body();
        GridPos player = new GridPos(1, 1);
        GridArena arena = arena(player);

        // Three AP, a 1-AP weapon: three swings, each three tenths of the boss's attack of 10.
        for (int swing = 1; swing <= 3; swing++) {
            EnemyAction.CustomAction strike = assertStrike(ai.decideAction(self, arena, player), "swing " + swing);
            assertEquals(3, strike.damage());
            assertEquals(swing < 3, ai.wantsAnotherAction(self), "after swing " + swing);
        }
    }

    @Test
    void aSwingNamesItsWeaponAndTheSizeOfTheHitExactlyOnce() {
        // The combat manager collects this when the swing lands and runs the weapon's own
        // code with it. Collected twice, one swing would land twice.
        ShadowCloneAI ai = new ShadowCloneAI();
        CombatEntity self = body();
        GridPos player = new GridPos(1, 1);

        assertNull(ai.takePendingStrike(), "nothing swung yet");
        ai.decideAction(self, arena(player), player);

        ShadowCloneAI.PendingStrike strike = ai.takePendingStrike();
        assertNotNull(strike);
        assertEquals("fist", strike.weapon().itemId());
        assertEquals(3, strike.damage());
        assertNull(ai.takePendingStrike());
    }

    @Test
    void aStrongerWeaponInTheKitMakesFistsHitSofter() {
        // Fists are the best weapon of a Shadow of nobody. Next to a sword they are not.
        ShadowWeapon sword = new ShadowWeapon(0, "minecraft:diamond_sword", 8, 2, 1, false);
        ShadowCloneAI ai = new ShadowCloneAI();
        ai.bind(ShadowKit.ofParts(1, 3, List.of(FIST, sword), List.of()), true);
        CombatEntity self = body();
        GridPos player = new GridPos(1, 1);

        // One AP: the 2-AP sword is out of reach of the budget, so it punches.
        ai.decideAction(self, arena(player), player);

        ShadowCloneAI.PendingStrike strike = ai.takePendingStrike();
        assertEquals("fist", strike.weapon().itemId());
        assertEquals(2, strike.damage(), "half strength: 10 x 0.3 x 0.5, rounded up");
    }

    @Test
    void walksUpAndStopsWhenItsLegsRunOut() {
        ShadowCloneAI ai = new ShadowCloneAI();
        CombatEntity self = body();
        GridPos player = new GridPos(6, 1);
        GridArena arena = arena(player);

        EnemyAction action = ai.decideAction(self, arena, player);

        EnemyAction.Move move = assertInstanceOf(EnemyAction.Move.class, action);
        assertEquals(3, move.path().size(), "Speed 3 is three tiles");
        // Still three tiles short with fists, and nothing left to walk with.
        assertFalse(ai.wantsAnotherAction(self));
    }

    @Test
    void walksUpThenSwingsInTheSameTurn() {
        ShadowCloneAI ai = new ShadowCloneAI();
        CombatEntity self = body();
        GridPos player = new GridPos(3, 1);
        GridArena arena = arena(player);

        EnemyAction first = ai.decideAction(self, arena, player);
        EnemyAction.Move move = assertInstanceOf(EnemyAction.Move.class, first);
        assertEquals(new GridPos(2, 1), move.path().get(move.path().size() - 1), "stops beside the player");
        assertTrue(ai.wantsAnotherAction(self), "it has not swung yet");

        // The turn machine walks it there before asking again.
        self.setGridPos(new GridPos(2, 1));
        assertStrike(ai.decideAction(self, arena, player), "the swing");
    }

    @Test
    void aRequestForAnotherActionIsOnlyGoodOnce() {
        // If the turn machine comes back round but never reaches the AI (the Shadow was
        // stunned, or lost sight of its target), asking again must end the turn, not loop.
        ShadowCloneAI ai = new ShadowCloneAI();
        CombatEntity self = body();
        GridPos player = new GridPos(1, 1);
        ai.decideAction(self, arena(player), player);

        assertTrue(ai.wantsAnotherAction(self));
        assertFalse(ai.wantsAnotherAction(self));
    }

    @Test
    void aNewTurnStartsWithAFullBudgetAgain() {
        ShadowCloneAI ai = new ShadowCloneAI();
        CombatEntity self = body();
        GridPos player = new GridPos(1, 1);
        GridArena arena = arena(player);

        for (int swing = 0; swing < 3; swing++) {
            ai.decideAction(self, arena, player);
            ai.wantsAnotherAction(self);
        }

        // Next round: three more swings, not zero.
        assertStrike(ai.decideAction(self, arena, player), "first swing of the new turn");
        assertTrue(ai.wantsAnotherAction(self));
    }

    @Test
    void atHalfHealthItGetsOneMoreAp() {
        ShadowCloneAI ai = new ShadowCloneAI();
        CombatEntity self = body();
        self.takeDamage(60);
        GridPos player = new GridPos(1, 1);
        GridArena arena = arena(player);

        int swings = 0;
        do {
            assertStrike(ai.decideAction(self, arena, player), "swing " + (swings + 1));
            swings++;
        } while (ai.wantsAnotherAction(self) && swings < 10);

        assertEquals(4, swings);
    }
}
