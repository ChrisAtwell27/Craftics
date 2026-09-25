package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.boss.BossWarning;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Ender Dragon's tiers, volleys, breath clouds and perch summons.
 *
 * <p>Pure state machine: resolving the actions (damage, fire, clouds, spawning) happens in
 * CombatManager against a live world and is covered by the in-game checklist. These tests pin what
 * the AI asks for. The fixture is a 12x12 arena with the player on (2,3), outside the perch zone
 * (x 4-6, z 2-8), and a 90 HP dragon with 12 attack, so the tier lines sit at 60 and 30 HP.
 */
class DragonAITest {

    private static final int SIZE = 12;
    private static final GridPos PLAYER = new GridPos(2, 3);

    private static GridArena arena() {
        GridTile[][] tiles = new GridTile[SIZE][SIZE];
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                tiles[x][z] = new GridTile(TileType.NORMAL, null);
            }
        }
        GridArena a = new GridArena(SIZE, SIZE, tiles, BlockPos.ORIGIN, 1, PLAYER);
        a.setPlayerGridPos(PLAYER);
        a.setAllPlayerGridPositions(List.of(PLAYER));
        return a;
    }

    /** A 90 HP background-boss dragon parked on its perch corner, already down to {@code hp}. */
    private static CombatEntity dragon(int hp) {
        CombatEntity d = new CombatEntity(500, "minecraft:ender_dragon", new GridPos(4, 2),
            90, 12, 0, 1, 1, 1);
        d.setBoss(true);
        d.setBackgroundBoss(true);
        if (hp < 90) d.takeDamage(90 - hp);
        assertEquals(hp, d.getCurrentHp(), "fixture: dragon HP");
        return d;
    }

    private static List<EnemyAction> flatten(EnemyAction action) {
        if (action instanceof EnemyAction.CompositeAction ca) {
            List<EnemyAction> out = new ArrayList<>();
            for (EnemyAction sub : ca.actions()) out.addAll(flatten(sub));
            return out;
        }
        return List.of(action);
    }

    /** One dragon turn, then the end of the player's turn - the order CombatManager runs them. */
    private static EnemyAction turn(DragonAI ai, CombatEntity self, GridArena a) {
        EnemyAction act = ai.decideAction(self, a, PLAYER);
        ai.tickWarning();
        return act;
    }

    /** Take turns until a volley matching {@code want} is telegraphed; returns its moves. */
    private static List<DragonAI.Move> driveUntilVolley(DragonAI ai, CombatEntity self, GridArena a,
                                                       Predicate<List<DragonAI.Move>> want) {
        for (int i = 0; i < 60; i++) {
            ai.decideAction(self, a, PLAYER);
            List<DragonAI.Move> volley = ai.consumeAnnouncedVolley();
            if (volley != null && want.test(volley)) return volley;
            ai.tickWarning();
        }
        fail("no matching volley within 60 turns");
        return null;
    }

    private static Set<GridPos> rect(int x0, int x1, int z0, int z1) {
        Set<GridPos> out = new HashSet<>();
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) out.add(new GridPos(x, z));
        return out;
    }

    private static Set<GridPos> warned(DragonAI ai) {
        BossWarning w = ai.getPendingWarning();
        assertNotNull(w, "a volley must be telegraphed");
        return new HashSet<>(w.getAffectedTiles());
    }

    private static List<EnemyAction.BreathCloud> clouds(DragonAI ai) {
        List<EnemyAction.BreathCloud> out = new ArrayList<>();
        for (EnemyAction e : flatten(ai.getPendingWarning().getResolveAction())) {
            if (e instanceof EnemyAction.BreathCloud c) out.add(c);
        }
        return out;
    }

    private static int summoned(EnemyAction action) {
        int n = 0;
        for (EnemyAction e : flatten(action)) {
            if (e instanceof EnemyAction.SummonMinions sm) n += sm.positions().size();
        }
        return n;
    }

    // ─── Volleys ─────────────────────────────────────────────────────────

    @Test
    void breathWaveRollsInFromTheNearestEdgeAllTheWayToThePlayer() {
        DragonAI ai = new DragonAI(new Random(1));
        CombatEntity d = dragon(90);
        GridArena a = arena();

        List<DragonAI.Move> volley = driveUntilVolley(ai, d, a, v -> true);

        assertEquals(List.of(DragonAI.Move.BREATH_WAVE), volley);
        // 3 wide around x=2, from the z=0 edge down to the player's row z=3.
        assertEquals(rect(1, 3, 0, 3), warned(ai));
    }

    @Test
    void fireballHitsAPlusOnThePlayerAndLeavesAThreeTurnCloudThere() {
        DragonAI ai = new DragonAI(new Random(1));
        CombatEntity d = dragon(90);
        GridArena a = arena();

        driveUntilVolley(ai, d, a, v -> v.equals(List.of(DragonAI.Move.FIREBALL)));

        Set<GridPos> plus = Set.of(new GridPos(2, 3), new GridPos(1, 3), new GridPos(3, 3),
            new GridPos(2, 2), new GridPos(2, 4));
        assertEquals(plus, warned(ai));
        List<EnemyAction.BreathCloud> cs = clouds(ai);
        assertEquals(1, cs.size());
        assertEquals(plus, new HashSet<>(cs.get(0).tiles()));
        assertEquals(3, cs.get(0).turns());
        assertEquals(4, cs.get(0).damage(), "a third of the dragon's 12 attack");
    }

    @Test
    void swoopLeavesATwoTurnCloudDownTheMiddleOfItsCorridor() {
        DragonAI ai = new DragonAI(new Random(1));
        CombatEntity d = dragon(90);
        GridArena a = arena();

        driveUntilVolley(ai, d, a, v -> v.equals(List.of(DragonAI.Move.SWOOP)));

        List<EnemyAction.BreathCloud> cs = clouds(ai);
        assertEquals(1, cs.size());
        Set<GridPos> lane = new HashSet<>(cs.get(0).tiles());
        // The middle lane runs through the player: their row z=3 or their column x=2.
        assertTrue(lane.equals(rect(0, 11, 3, 3)) || lane.equals(rect(2, 2, 0, 11)),
            "cloud lane was " + lane);
        assertTrue(warned(ai).containsAll(lane), "the cloud must sit inside the telegraph");
        assertEquals(2, cs.get(0).turns());
    }

    @Test
    void aVolleyLandsAsOneHitOverTheWholeHighlight() {
        DragonAI ai = new DragonAI(new Random(1));
        CombatEntity d = dragon(60);
        GridArena a = arena();

        List<DragonAI.Move> volley = driveUntilVolley(ai, d, a, v -> v.size() > 1);

        assertEquals(2, volley.size(), "at 2/3 HP a volley is two attacks");
        List<EnemyAction.TileAreaAttack> hits = new ArrayList<>();
        for (EnemyAction e : flatten(ai.getPendingWarning().getResolveAction())) {
            assertFalse(e instanceof EnemyAction.LineAttack, "every hit goes through the one area attack");
            if (e instanceof EnemyAction.TileAreaAttack t) hits.add(t);
        }
        assertEquals(1, hits.size(), "overlapping attacks must not hit twice");
        assertEquals(warned(ai), new HashSet<>(hits.get(0).tiles()));
        assertEquals(15, hits.get(0).damage(), "12 attack + 3 once enraged");
    }

    @Test
    void belowAThirdEveryVolleyIsThreeAttacksAndTheNextIsReadyAsTheLastLands() {
        DragonAI ai = new DragonAI(new Random(1));
        CombatEntity d = dragon(30);
        GridArena a = arena();

        List<DragonAI.Move> first = driveUntilVolley(ai, d, a, v -> true);
        assertEquals(3, first.size());
        ai.tickWarning();

        EnemyAction landing = turn(ai, d, a);
        assertTrue(flatten(landing).stream().anyMatch(e -> e instanceof EnemyAction.TileAreaAttack),
            "the first volley lands this turn");
        List<DragonAI.Move> next = ai.consumeAnnouncedVolley();
        assertNotNull(next, "and the next volley is telegraphed the same turn");
        assertEquals(3, next.size());
    }

    @Test
    void aboveAThirdTheDragonRestsTheTurnAVolleyLands() {
        DragonAI ai = new DragonAI(new Random(1));
        CombatEntity d = dragon(90);
        GridArena a = arena();

        driveUntilVolley(ai, d, a, v -> true);
        ai.tickWarning();
        turn(ai, d, a);

        assertNull(ai.consumeAnnouncedVolley());
        assertNull(ai.getPendingWarning());
    }

    // ─── Perches and summons ─────────────────────────────────────────────

    /** Take turns until the dragon perches; returns the action from the turn it did. */
    private static EnemyAction driveUntilPerch(DragonAI ai, CombatEntity d, GridArena a) {
        for (int i = 0; i < 60; i++) {
            EnemyAction act = turn(ai, d, a);
            if (ai.getState() == DragonAI.State.PERCHING) return act;
        }
        fail("the dragon never perched");
        return null;
    }

    @Test
    void aFullHpPerchCallsThree() {
        DragonAI ai = new DragonAI(new Random(1));
        CombatEntity d = dragon(90);
        GridArena a = arena();

        EnemyAction perch = driveUntilPerch(ai, d, a);

        assertEquals(3, summoned(perch));
        for (EnemyAction e : flatten(perch)) {
            if (!(e instanceof EnemyAction.SummonMinions sm)) continue;
            assertTrue(Set.of("minecraft:enderman", "minecraft:shulker", "minecraft:phantom")
                .contains(sm.entityTypeId()));
            for (GridPos p : sm.positions()) {
                assertTrue(p.manhattanDistance(PLAYER) >= 3, p + " is too close to the player");
                assertFalse(rect(4, 6, 2, 8).contains(p), p + " is on the perch zone");
            }
        }
    }

    @Test
    void droppingToTwoThirdsForcesAPerchThatCallsFive() {
        DragonAI ai = new DragonAI(new Random(1));
        CombatEntity d = dragon(60);
        GridArena a = arena();

        EnemyAction first = turn(ai, d, a);

        assertEquals(DragonAI.State.PERCHING, ai.getState());
        assertEquals(5, summoned(first));
    }

    @Test
    void droppingToAThirdForcesAPerchThatCallsSevenAndPeaksOnce() {
        DragonAI ai = new DragonAI(new Random(1));
        CombatEntity d = dragon(30);
        GridArena a = arena();

        EnemyAction first = turn(ai, d, a);

        assertEquals(DragonAI.State.PERCHING, ai.getState());
        assertEquals(7, summoned(first));
        assertTrue(ai.consumeFuryPeaked());
        assertFalse(ai.consumeFuryPeaked(), "the fury line is announced once");
    }

    @Test
    void aPerchOnlyTopsTheAddsBackUp() {
        DragonAI ai = new DragonAI(new Random(1));
        CombatEntity d = dragon(90);
        GridArena a = arena();
        // Two adds from an earlier perch are still alive.
        for (int i = 0; i < 2; i++) {
            CombatEntity add = new CombatEntity(600 + i, "minecraft:enderman",
                new GridPos(10, 10 - i), 22, 9, 2, 1, 1, 1);
            assertTrue(a.placeEntity(add));
            ai.registerSpawnedMinion(add.getEntityId());
        }

        EnemyAction perch = driveUntilPerch(ai, d, a);

        assertEquals(1, summoned(perch), "3 wanted, 2 still standing");
    }
}
