package com.crackedgames.craftics.compat.aether.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.combat.ai.ProjectileAI;
import com.crackedgames.craftics.combat.ai.SeekingProjectileAI;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * What the Sun Spirit decides to do, turn by turn, and what whole fights against it come to.
 *
 * <p>Nothing here can spawn the Aether's entity, paint a block or resolve a hit: those are
 * the engine's, and belong to the in-game checklist. Two things can be pinned without them.
 * The first is what the AI <em>asks for</em> on a bare grid, which is where its rules live.
 * The second is what those requests add up to: {@link Fight} plays them out by the rules the
 * engine applies to each kind of action, against players who snap at the Ice Crystal, who
 * line the shot up, and who mind where the frost falls, on the room the fight really
 * happens in. Every one of those fights holds the
 * boss to its promise, that a player who ends a turn off the tiles it showed and off fire
 * already burning is not hurt by its turn, and prints what the fight cost them.
 */
class SunSpiritAITest {

    // ── Fixture ──
    // 840 health, so thirds, tenths, twelfths and fortieths are all whole numbers, and no
    // armour, so a hit is exactly what was asked for.

    private static final int HP = 840;
    private static final int ATTACK = 12;
    private static final int SPEED = 2;
    /** A small bare floor for the rules that need no room. Too small for any ring to burn. */
    private static final int SIZE = 12;
    private static final GridPos FAR_CORNER = new GridPos(11, 11);

    /** The room the fight happens in: nineteen by nineteen, a brazier two in from each corner. */
    private static final int ROOM = 19;
    private static final List<GridPos> BRAZIERS = List.of(
        new GridPos(2, 2), new GridPos(16, 2), new GridPos(2, 16), new GridPos(16, 16));
    private static final GridPos CENTRE = new GridPos(8, 8);

    /**
     * The boss with its braziers pointed out to it. On a real floor it finds them by their
     * block, which a bare grid does not have. One that is no longer an obstacle is no longer
     * a brazier, as on a real floor.
     */
    private static final class Lab extends SunSpiritAI {
        private final List<GridPos> stones;

        Lab(long seed, List<GridPos> stones) {
            super(new Random(seed));
            this.stones = stones;
        }

        @Override
        protected List<GridPos> braziers(GridArena arena) {
            List<GridPos> standing = new ArrayList<>();
            for (GridPos stone : stones) {
                GridTile tile = arena.getTile(stone);
                if (tile != null && tile.getType() == TileType.OBSTACLE) standing.add(stone);
            }
            return standing;
        }
    }

    private static GridArena floor(int size, GridPos player) {
        GridTile[][] tiles = new GridTile[size][size];
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                tiles[x][z] = new GridTile(TileType.NORMAL, null);
            }
        }
        GridArena a = new GridArena(size, size, tiles, BlockPos.ORIGIN, 1, player);
        stand(a, player);
        return a;
    }

    private static GridArena arena(GridPos player) {
        return floor(SIZE, player);
    }

    private static GridArena room(GridPos player) {
        GridArena a = floor(ROOM, player);
        for (GridPos brazier : BRAZIERS) paint(a, TileType.OBSTACLE, brazier.x(), brazier.z());
        return a;
    }

    private static void stand(GridArena a, GridPos player) {
        a.setPlayerGridPos(player);
        a.setAllPlayerGridPositions(List.of(player));
    }

    private static void paint(GridArena a, TileType type, int x, int z) {
        a.setTile(new GridPos(x, z), new GridTile(type, null));
    }

    /** Terrain that passes: a tile of this type with this many rounds left to it. */
    private static void lay(GridArena a, TileType type, GridPos tile, int turns) {
        GridTile ground = new GridTile(type, null);
        ground.setTurnsRemaining(turns);
        a.setTile(tile, ground);
    }

    private static void frost(GridArena a, int x, int z) {
        lay(a, SunSpiritAI.FROZEN_FLOOR, new GridPos(x, z), SunSpiritAI.FROST_TURNS);
    }

    private static TileType typeAt(GridArena a, GridPos tile) {
        return a.getTile(tile).getType();
    }

    private static CombatEntity boss(GridArena a, int x, int z) {
        CombatEntity b = new CombatEntity(100, SunSpiritAI.ENTITY_ID, new GridPos(x, z),
            HP, ATTACK, /* defense */ 0, /* range */ 1, /* size */ 2, SPEED);
        b.setBoss(true);
        b.setAiOverrideKey(SunSpiritAI.BOSS_KEY);
        assertTrue(a.placeEntity(b));
        return b;
    }

    /** Take it down to {@code hp} before it has raised its guard. */
    private static CombatEntity hurtTo(CombatEntity boss, int hp) {
        boss.takeDamage(boss.getCurrentHp() - hp);
        assertEquals(hp, boss.getCurrentHp());
        return boss;
    }

    private static int nextId = 500;

    private static CombatEntity other(GridArena a, int x, int z, boolean ally) {
        CombatEntity e = new CombatEntity(nextId++, "minecraft:zombie", new GridPos(x, z), 10, 2, 0, 1, 1, 1);
        e.setAlly(ally);
        assertTrue(a.placeEntity(e));
        return e;
    }

    private static CombatEntity crystal(GridArena a, int x, int z, String type) {
        CombatEntity c = new CombatEntity(nextId++, "aether:fire_crystal", new GridPos(x, z), 1, ATTACK, 0, 1);
        c.setProjectile(true);
        c.setProjectileType(type);
        c.setAiOverrideKey("projectile");
        assertTrue(a.placeEntity(c));
        return c;
    }

    private static Lab ai(int headingX, int headingZ) {
        Lab ai = new Lab(7, List.of());
        ai.setHeading(headingX, headingZ);
        return ai;
    }

    private static Lab inRoom(long seed) {
        return new Lab(seed, BRAZIERS);
    }

    private static List<EnemyAction> flat(EnemyAction action) {
        return EnemyAction.flatten(action);
    }

    private static <T extends EnemyAction> List<T> all(EnemyAction action, Class<T> kind) {
        List<T> found = new ArrayList<>();
        for (EnemyAction sub : flat(action)) {
            if (kind.isInstance(sub)) found.add(kind.cast(sub));
        }
        return found;
    }

    private static <T extends EnemyAction> T only(EnemyAction action, Class<T> kind) {
        List<T> found = all(action, kind);
        assertTrue(found.size() <= 1, "more than one " + kind.getSimpleName() + " in a turn");
        return found.isEmpty() ? null : found.get(0);
    }

    /** The crystals a turn launches of one type, as (spot, damage, health) triples would be too much: spots only. */
    private static List<GridPos> launched(EnemyAction action, String type) {
        List<GridPos> spots = new ArrayList<>();
        for (EnemyAction.SpawnProjectile launch : all(action, EnemyAction.SpawnProjectile.class)) {
            if (type.equals(launch.projectileType())) spots.addAll(launch.positions());
        }
        return spots;
    }

    private static Set<GridPos> body(GridPos anchor) {
        return new HashSet<>(GridArena.getOccupiedTiles(anchor, 2, 2));
    }

    // ================================================================
    // Drift
    // ================================================================

    @Test
    @DisplayName("it travels its move speed along its heading")
    void driftsAlongItsHeading() {
        GridArena a = arena(FAR_CORNER);
        CombatEntity self = boss(a, 4, 4);
        SunSpiritAI.Drift drift = SunSpiritAI.planDrift(a, self, 1, 0, SPEED);
        assertEquals(List.of(new GridPos(5, 4), new GridPos(6, 4)), drift.path());
        assertEquals(1, drift.headingX());
        assertEquals(0, drift.headingZ());
    }

    @Test
    @DisplayName("a wall across one axis flips that axis, a corner flips both, and a bounce does not cost the step")
    void bouncesLikeABilliardBall() {
        GridArena a = arena(FAR_CORNER);
        CombatEntity self = boss(a, 0, 5);
        assertArrayEquals(new int[]{1, 1}, SunSpiritAI.bounce(a, self, self.getGridPos(), -1, 1));

        // The far wall: its body is two wide, so its anchor stops one short of the last column.
        a.moveEntity(self, new GridPos(5, SIZE - 2));
        assertArrayEquals(new int[]{1, -1}, SunSpiritAI.bounce(a, self, self.getGridPos(), 1, 1));

        a.moveEntity(self, new GridPos(0, 0));
        assertArrayEquals(new int[]{1, 1}, SunSpiritAI.bounce(a, self, self.getGridPos(), -1, -1));
        assertArrayEquals(new int[]{1, 0}, SunSpiritAI.bounce(a, self, self.getGridPos(), -1, 0));

        a.moveEntity(self, new GridPos(1, 5));
        SunSpiritAI.Drift drift = SunSpiritAI.planDrift(a, self, -1, 1, SPEED);
        assertEquals(List.of(new GridPos(0, 6), new GridPos(1, 7)), drift.path());
    }

    @Test
    @DisplayName("it bounces off a player, and does not cut a corner past one")
    void bouncesOffAPlayer() {
        GridPos player = new GridPos(6, 4);
        GridArena a = arena(player);
        CombatEntity self = boss(a, 4, 4);
        SunSpiritAI.Drift drift = SunSpiritAI.planDrift(a, self, 1, 0, SPEED);
        for (GridPos step : drift.path()) {
            assertFalse(body(step).contains(player), "its body was planned onto the player at " + step);
        }
        assertEquals(-1, drift.headingX(), "it should have turned back");

        // The diagonal tile itself is free, but the player stands where the body would sweep.
        assertFalse(SunSpiritAI.canStep(a, self, self.getGridPos(), 1, 1));
        assertArrayEquals(new int[]{-1, 1}, SunSpiritAI.bounce(a, self, self.getGridPos(), 1, 1));
    }

    @Test
    @DisplayName("liquid, sunken ground and frozen floor are walls to it; its own fire is not")
    void whatItWillCross() {
        List<TileType> walls = List.of(TileType.WATER, TileType.LAVA, TileType.LOW_GROUND,
            TileType.POWDER_SNOW, SunSpiritAI.FROZEN_FLOOR);
        for (TileType wall : walls) {
            GridArena a = arena(FAR_CORNER);
            CombatEntity self = boss(a, 4, 4);
            paint(a, wall, 6, 4);
            SunSpiritAI.Drift drift = SunSpiritAI.planDrift(a, self, 1, 0, SPEED);
            for (GridPos step : drift.path()) {
                assertFalse(body(step).contains(new GridPos(6, 4)), wall + " was drifted onto");
            }
            assertEquals(-1, drift.headingX(), wall + " should have turned it back");
        }
        assertFalse(SunSpiritAI.crossable(TileType.OBSTACLE));
        assertFalse(SunSpiritAI.crossable(TileType.VOID));
        assertTrue(SunSpiritAI.crossable(TileType.FIRE), "its own trail must not wall it in");
        assertTrue(SunSpiritAI.crossable(TileType.NORMAL));
    }

    @Test
    @DisplayName("boxed in, it plans no move at all")
    void staysPutWhenBoxedIn() {
        GridArena a = arena(FAR_CORNER);
        CombatEntity self = boss(a, 4, 4);
        for (int x = 3; x <= 6; x++) {
            for (int z = 3; z <= 6; z++) {
                boolean inside = x >= 4 && x <= 5 && z >= 4 && z <= 5;
                if (!inside) paint(a, TileType.OBSTACLE, x, z);
            }
        }
        assertTrue(SunSpiritAI.planDrift(a, self, 1, 1, SPEED).path().isEmpty());
        assertNull(SunSpiritAI.bounce(a, self, self.getGridPos(), 1, 0));
    }

    @Test
    @DisplayName("of the eight ways it takes the one that points most nearly at its target")
    void aimsOnEightWays() {
        GridPos at = new GridPos(4, 4);
        assertArrayEquals(new int[]{1, 1}, SunSpiritAI.aimAt(at, 1, 1, new GridPos(9, 9)));
        assertArrayEquals(new int[]{1, 0}, SunSpiritAI.aimAt(at, 1, 1, new GridPos(9, 5)));
        assertArrayEquals(new int[]{0, -1}, SunSpiritAI.aimAt(at, 1, 1, new GridPos(5, 0)));
        assertArrayEquals(new int[]{-1, 1}, SunSpiritAI.aimAt(at, 1, 1, new GridPos(1, 8)));
        assertNull(SunSpiritAI.aimAt(at, 1, 1, at));
        // A body two wide is aimed from its middle, which is the corner its four tiles share.
        assertArrayEquals(new int[]{1, 0}, SunSpiritAI.aimAt(at, 2, 2, new GridPos(9, 5)));

        int[] north = {0, -1};
        int[] south = {0, 1};
        assertEquals(1, SunSpiritAI.leaning(new int[]{1, -1}, new int[]{1, 1}, at, 2, 2, new GridPos(9, 10)));
        assertEquals(-1, SunSpiritAI.leaning(new int[]{1, -1}, new int[]{1, 1}, at, 2, 2, new GridPos(9, 0)));
        assertEquals(0, SunSpiritAI.leaning(north, south, at, 1, 1, new GridPos(9, 4)), "dead level: nothing in it");
    }

    // ================================================================
    // What it hands the engine
    // ================================================================

    @Test
    @DisplayName("the bundle leads with the walk and holds nothing the engine would drop")
    void theBundleIsOneTheEngineRunsWhole() {
        GridArena a = arena(FAR_CORNER);
        CombatEntity self = boss(a, 4, 4);
        EnemyAction action = ai(1, 0).decideAction(self, a, FAR_CORNER);

        assertInstanceOf(EnemyAction.CompositeAction.class, action);
        List<EnemyAction> subs = flat(action);
        assertInstanceOf(EnemyAction.Move.class, subs.get(0), "the walk has to own the turn, so it goes first");
        assertRunsWhole(action);
    }

    /** Every kind in a turn is one the engine resolves inside a bundle, and at most one of them plays out over time. */
    private static void assertRunsWhole(EnemyAction action) {
        int walks = 0;
        int burns = 0;
        for (EnemyAction sub : flat(action)) {
            if (EnemyAction.drivesTurn(sub)) walks++;
            if (sub instanceof EnemyAction.TileAreaAttack) burns++;
            boolean known = sub instanceof EnemyAction.Move
                || sub instanceof EnemyAction.CreateTerrain
                || sub instanceof EnemyAction.TileAreaAttack
                || sub instanceof EnemyAction.SpawnProjectile
                || sub instanceof EnemyAction.SummonMinions
                || (sub instanceof EnemyAction.Idle && sub == action);
            assertTrue(known, sub.getClass().getSimpleName() + " is not a kind a bundle resolves");
            // A plain strike inside a bundle would go round the engine's ceiling on a boss hit.
            assertFalse(sub instanceof EnemyAction.Attack);
        }
        assertTrue(walks <= 1, "only one sub-action may play out over the turn");
        assertTrue(burns <= 1, "nobody takes two of its area hits in one turn");
    }

    @Test
    @DisplayName("the trail is the ground it left, never where it comes to rest")
    void trailIsWhatItLeftBehind() {
        GridArena a = arena(FAR_CORNER);
        CombatEntity self = boss(a, 4, 4);
        EnemyAction action = ai(1, 0).decideAction(self, a, FAR_CORNER);

        EnemyAction.CreateTerrain fire = only(action, EnemyAction.CreateTerrain.class);
        assertNotNull(fire);
        assertEquals(TileType.FIRE, fire.terrainType());
        assertEquals(SunSpiritAI.TRAIL_TURNS, fire.duration());
        assertEquals(Set.of(new GridPos(4, 4), new GridPos(4, 5), new GridPos(5, 4), new GridPos(5, 5)),
            new HashSet<>(fire.tiles()));
    }

    @Test
    @DisplayName("its fire is not laid on frost, on floor already burning, or squarely beside a brazier")
    void whereItsFireGoes() {
        GridArena a = arena(FAR_CORNER);
        CombatEntity self = boss(a, 4, 4);
        List<GridPos> braziers = List.of(new GridPos(3, 5));
        paint(a, TileType.OBSTACLE, 3, 5);
        lay(a, TileType.FIRE, new GridPos(5, 4), SunSpiritAI.LIT_TURNS);

        SunSpiritAI.Drift drift = SunSpiritAI.planDrift(a, self, 1, 0, SPEED);
        List<GridPos> trail = SunSpiritAI.trail(a, self, drift, braziers);
        // (4,5) is squarely beside the brazier, and (5,4) is burning for good already: lighting
        // it again would set it to go out in two turns.
        assertEquals(Set.of(new GridPos(4, 4), new GridPos(5, 5)), new HashSet<>(trail));

        assertFalse(SunSpiritAI.takesFire(a, braziers, new GridPos(3, 4)), "squarely beside");
        assertTrue(SunSpiritAI.takesFire(a, braziers, new GridPos(2, 4)), "corner to corner is not beside");
        frost(a, 8, 8);
        assertFalse(SunSpiritAI.takesFire(a, braziers, new GridPos(8, 8)), "frozen floor is never taken");
        assertFalse(SunSpiritAI.takesFire(a, braziers, new GridPos(3, 5)), "a brazier is not floor");
    }

    @Test
    @DisplayName("the aura rings where it stops, for its attack, and spares its own crystals and minions")
    void auraRingsTheDestination() {
        GridArena a = arena(FAR_CORNER);
        CombatEntity self = boss(a, 4, 4);
        EnemyAction action = ai(1, 0).decideAction(self, a, FAR_CORNER);

        EnemyAction.TileAreaAttack aura = only(action, EnemyAction.TileAreaAttack.class);
        assertNotNull(aura);
        assertEquals(ATTACK, aura.damage());
        assertEquals("burning", aura.effectName());
        Set<GridPos> expected = new HashSet<>(SunSpiritAI.ring(a, new GridPos(6, 4), 2, 2));
        assertEquals(12, expected.size(), "a 2x2 body has twelve neighbours");
        assertEquals(expected, new HashSet<>(aura.tiles()));

        other(a, 5, 6, false);
        other(a, 8, 5, true);
        List<GridPos> ring = SunSpiritAI.aura(a, self, new GridPos(6, 4));
        assertFalse(ring.contains(new GridPos(5, 6)), "an area hit lands on enemies too: its own must be left out");
        assertTrue(ring.contains(new GridPos(8, 5)), "a pet standing next to it burns like anyone else");
    }

    @Test
    @DisplayName("fire it lays is not announced by the engine's own line")
    void itsFireIsQuiet() {
        SunSpiritAI ai = new SunSpiritAI();
        assertEquals("", ai.describeTerrain(TileType.FIRE, 6));
        assertNull(ai.describeTerrain(TileType.OBSTACLE, 1));
        assertEquals(2, ai.getGridSize());
        assertTrue(ai.spawnsAtCenter());
    }

    // ================================================================
    // Phases
    // ================================================================

    @Test
    @DisplayName("its phases are thirds of its health")
    void phasesAreThirds() {
        assertEquals(1, SunSpiritAI.phase(840, 840));
        assertEquals(1, SunSpiritAI.phase(561, 840));
        assertEquals(2, SunSpiritAI.phase(560, 840));
        assertEquals(2, SunSpiritAI.phase(281, 840));
        assertEquals(3, SunSpiritAI.phase(280, 840));
        assertEquals(3, SunSpiritAI.phase(1, 840));

        GridArena a = arena(FAR_CORNER);
        CombatEntity self = hurtTo(boss(a, 4, 4), 560);
        Lab ai = ai(1, 0);
        ai.decideAction(self, a, FAR_CORNER);
        assertTrue(ai.isInPhaseTwo(), "the engine's phase moment comes at two thirds");
    }

    @Test
    @DisplayName("in its last third it is aimed afresh at the nearest player every turn, a tile further, and never veers")
    void lastThirdHunts() {
        GridPos player = new GridPos(4, 10);
        GridArena a = arena(player);
        CombatEntity self = hurtTo(boss(a, 4, 3), 280);
        Lab ai = ai(1, 0);

        EnemyAction action = ai.decideAction(self, a, player);
        EnemyAction.Move move = only(action, EnemyAction.Move.class);
        // Three tiles, straight at the player, whatever heading it was left on.
        assertEquals(List.of(new GridPos(4, 4), new GridPos(4, 5), new GridPos(4, 6)), move.path());

        // And it still bounces: the player is in the way of a fourth, fifth and sixth.
        for (GridPos step : move.path()) a.moveEntity(self, step);
        EnemyAction next = ai.decideAction(self, a, player);
        for (GridPos step : only(next, EnemyAction.Move.class).path()) {
            assertFalse(body(step).contains(player));
        }
    }

    @Test
    @DisplayName("frost and a wall cannot hold it between them: it takes the nearest way that is open")
    void itCannotBeHeld() {
        // Against the west wall, frost down the column east of it, the player due east.
        GridPos player = new GridPos(9, 5);
        GridArena a = arena(player);
        CombatEntity self = hurtTo(boss(a, 0, 5), 280);
        for (int z = 4; z <= 7; z++) frost(a, 2, z);
        assertArrayEquals(new int[]{1, 0}, SunSpiritAI.aimAt(self.getGridPos(), 2, 2, player));
        assertNull(SunSpiritAI.bounce(a, self, self.getGridPos(), 1, 0), "fixture: straight at the player should be shut both ways");

        // In its last third it goes round, and shows that it will.
        assertArrayEquals(new int[]{0, -1}, SunSpiritAI.openWayAt(a, self, player));
        Lab hunting = ai(1, 0);
        Set<GridPos> shown = hunting.computeThreatTiles(self, a);
        EnemyAction.Move round = only(hunting.decideAction(self, a, player), EnemyAction.Move.class);
        assertNotNull(round, "it sat behind the frost");
        assertEquals(List.of(new GridPos(0, 4), new GridPos(0, 3), new GridPos(0, 2)), round.path());
        for (GridPos step : round.path()) assertTrue(shown.containsAll(body(step)), "it went a way it never showed");

        // Above that it keeps a heading, so the one turn is lost. The next is not.
        GridArena b = arena(player);
        CombatEntity early = boss(b, 0, 5);
        for (int z = 4; z <= 7; z++) frost(b, 2, z);
        Lab drifting = ai(1, 0);
        assertNull(only(drifting.decideAction(early, b, player), EnemyAction.Move.class));
        assertEquals(0, drifting.headingX());
        assertEquals(-1, drifting.headingZ());
        assertNotNull(only(drifting.decideAction(early, b, player), EnemyAction.Move.class), "held for a second turn");

        // With every way shut it faces them and waits. Nothing is thrown away on a veer.
        GridArena c = arena(player);
        CombatEntity boxed = boss(c, 0, 0);
        for (int i = 0; i <= 2; i++) {
            frost(c, 2, i);
            frost(c, i, 2);
        }
        assertNull(only(ai(1, 1).decideAction(boxed, c, player), EnemyAction.Move.class));
    }

    @Test
    @DisplayName("a Fire Minion does nothing on the turn it appears")
    void aMinionWaitsATurn() {
        GridPos player = new GridPos(4, 8);
        GridArena a = arena(player);
        CombatEntity self = boss(a, 4, 4);
        Fight fight = new Fight(a, self, ai(1, 0), player, 0);
        fight.enemyPhase();
        SunSpiritAI.struck(self, 0);
        // The player has walked up to it, as anyone would. The minion is born beside them.
        fight.player = new GridPos(self.getGridPos().x(), self.getGridPos().z() + 2);
        stand(a, fight.player);

        fight.enemyPhase();
        assertEquals(1, fight.minions.size());
        assertTrue(fight.minions.get(0).getGridPos().manhattanDistance(fight.player) <= 2, "fixture: it should be born in reach");
        assertEquals(0, fight.hits[Source.MINION.ordinal()], "it struck on the turn it appeared");

        fight.enemyPhase();
        assertEquals(1, fight.hits[Source.MINION.ordinal()], "and the turn after, it is a minion like any other");
    }

    // ================================================================
    // Crystals
    // ================================================================

    @Test
    @DisplayName("every third crystal is ice")
    void twoFireThenIce() {
        assertEquals(3, SunSpiritAI.CRYSTALS_PER_CYCLE);
        for (int launched = 0; launched < 20; launched++) {
            assertEquals(launched % 3 == 2, SunSpiritAI.isIce(launched), "crystal " + launched);
        }
    }

    @Test
    @DisplayName("the crystals stay at or under its attack, and a minion has a fortieth of its health")
    void numbers() {
        assertEquals(12, SunSpiritAI.fireCrystalDamage(12));
        assertEquals(8, SunSpiritAI.iceCrystalDamage(12));
        assertEquals(1, SunSpiritAI.iceCrystalDamage(1));
        assertEquals(70, SunSpiritAI.iceCrystalHp(HP));
        assertEquals(84, SunSpiritAI.deflectDamage(8, HP), "a tenth of its health");
        assertEquals(100, SunSpiritAI.deflectDamage(100, HP));
        assertEquals(21, SunSpiritAI.minionHp(HP));
        assertEquals(6, SunSpiritAI.minionHp(110));
        assertEquals(0.72, SunSpiritAI.fitScale(2), 1e-9);
        // The contact burn is its hitbox less a block and a half. At this scale that is a
        // column 0.3 wide through the middle of a body two tiles across.
        double burnWidth = 2.5 * SunSpiritAI.fitScale(2) - 1.5;
        assertTrue(burnWidth > 0 && burnWidth < 1.0);
    }

    @Test
    @DisplayName("a crystal appears clear of its path and never within three tiles of anyone: fire dead in line with them, ice over its shoulder")
    void whereACrystalAppears() {
        GridPos player = new GridPos(10, 5);
        GridArena a = arena(player);
        CombatEntity self = boss(a, 4, 4);
        SunSpiritAI.Drift drift = SunSpiritAI.planDrift(a, self, 0, 1, SPEED);
        assertEquals(3, SunSpiritAI.HEAD_START);

        GridPos spot = SunSpiritAI.crystalSpot(a, self, drift, player, true);
        assertNotNull(spot);
        assertFalse(SunSpiritAI.swept(self, drift).contains(spot), "the boss would walk into its own crystal");
        assertTrue(spot.chebyshevDistanceTo(player) >= SunSpiritAI.HEAD_START);
        assertTrue(SunSpiritAI.inLine(spot, player), "a crystal that flies straight should be thrown straight at them");

        // An Ice Crystal is thrown the other way, as far from them as its reach allows, so
        // that it does not come at them with the Sun Spirit straight behind it.
        GridPos cold = SunSpiritAI.crystalSpot(a, self, drift, player, false);
        assertNotNull(cold);
        assertFalse(SunSpiritAI.swept(self, drift).contains(cold));
        assertEquals(new GridPos(2, 9), cold, "the far corner of its reach from where it stops");
        assertTrue(cold.manhattanDistance(player) > spot.manhattanDistance(player));

        // With the player on top of it there is nowhere fair to put one, and so none is thrown.
        GridArena tight = floor(6, new GridPos(2, 2));
        CombatEntity cornered = boss(tight, 0, 0);
        SunSpiritAI.Drift still = new SunSpiritAI.Drift(List.of(), 1, 1);
        assertNull(SunSpiritAI.crystalSpot(tight, cornered, still, new GridPos(2, 2), true));
        assertNull(SunSpiritAI.crystalSpot(tight, cornered, still, new GridPos(2, 2), false));
    }

    @Test
    @DisplayName("one crystal a turn, two fire then one ice, the fire ones aimed on eight ways and the ice one a seeker")
    void crystalCadenceAndPattern() {
        GridArena a = room(new GridPos(9, 16));
        CombatEntity self = boss(a, CENTRE.x(), CENTRE.z());
        Fight fight = new Fight(a, self, inRoom(3), new GridPos(9, 16), 0);

        List<String> launched = new ArrayList<>();
        for (int turn = 1; turn <= 9; turn++) {
            EnemyAction action = fight.enemyPhase();
            List<EnemyAction.SpawnProjectile> launches = all(action, EnemyAction.SpawnProjectile.class);
            assertEquals(1, launches.size(), "turn " + turn);
            EnemyAction.SpawnProjectile launch = launches.get(0);
            launched.add(launch.projectileType());
            boolean ice = SunSpiritAI.ICE_CRYSTAL.equals(launch.projectileType());
            assertEquals(ice ? SunSpiritAI.iceCrystalDamage(ATTACK) : SunSpiritAI.fireCrystalDamage(ATTACK), launch.atk());
            if (!ice) assertEquals(1, launch.hp(), "any strike should destroy a Fire Crystal");
        }
        String fire = SunSpiritAI.FIRE_CRYSTAL;
        String ice = SunSpiritAI.ICE_CRYSTAL;
        assertEquals(List.of(fire, fire, ice, fire, fire, ice, fire, fire, ice), launched);
        assertTrue(fight.seekers > 0 && fight.aimedDiagonally + fight.aimedSquare > 0);
    }

    @Test
    @DisplayName("the engine aims a crystal down a row or column; it is aimed again on the nearest of eight ways")
    void fireCrystalsAreAimedAgain() {
        GridPos player = new GridPos(9, 9);
        GridArena a = arena(player);
        CombatEntity self = boss(a, 0, 0);
        Lab ai = ai(1, 0);
        ai.decideAction(self, a, player);

        CombatEntity thrown = crystal(a, 3, 3, SunSpiritAI.FIRE_CRYSTAL);
        thrown.setProjectileDirX(1);
        thrown.setProjectileDirZ(0);
        int counted = ai.crystalsLaunched();
        ai.registerSpawnedProjectile(thrown.getEntityId());
        assertEquals(1, thrown.getProjectileDirX());
        assertEquals(1, thrown.getProjectileDirZ());
        assertEquals("projectile", thrown.getAiKey());
        assertEquals(counted + 1, ai.crystalsLaunched());

        CombatEntity cold = crystal(a, 6, 2, SunSpiritAI.ICE_CRYSTAL);
        ai.registerSpawnedProjectile(cold.getEntityId());
        assertEquals("seeking_projectile", cold.getAiKey());
    }

    @Test
    @DisplayName("a crystal the engine never confirmed is not counted, and is tried again")
    void anUnconfirmedLaunchIsNotCounted() {
        GridArena a = arena(FAR_CORNER);
        CombatEntity self = boss(a, 2, 2);
        Lab ai = new Lab(3, List.of());
        for (int turn = 0; turn < 3; turn++) {
            EnemyAction action = ai.decideAction(self, a, FAR_CORNER);
            assertEquals(1, launched(action, SunSpiritAI.FIRE_CRYSTAL).size(), "it should keep trying until one flies");
            EnemyAction.Move move = only(action, EnemyAction.Move.class);
            if (move != null) for (GridPos step : move.path()) a.moveEntity(self, step);
        }
        assertEquals(0, ai.crystalsLaunched());
    }

    @Test
    @DisplayName("a Fire Crystal bursts on a player or a wall, breaks on anything else, burns out in four turns, and never waits")
    void howAFireCrystalFlies() {
        GridPos player = new GridPos(9, 4);
        GridArena a = arena(player);
        CombatEntity thrown = crystal(a, 2, 4, SunSpiritAI.FIRE_CRYSTAL);
        thrown.setProjectileDirX(1);
        thrown.setProjectileDirZ(0);

        // Open floor: two tiles on, and nothing ends.
        SunSpiritAI.CrystalFlight.Flown open = SunSpiritAI.CrystalFlight.fly(thrown, a, player);
        assertEquals(List.of(new GridPos(3, 4), new GridPos(4, 4)), open.path());
        assertFalse(open.ends());

        // A wall or a brazier: it bursts where it is.
        paint(a, TileType.OBSTACLE, 4, 4);
        SunSpiritAI.CrystalFlight.Flown wall = SunSpiritAI.CrystalFlight.fly(thrown, a, player);
        assertTrue(wall.ends());
        assertFalse(wall.spent());
        assertEquals(new GridPos(3, 4), wall.at());
        paint(a, TileType.NORMAL, 4, 4);

        // Something that is neither: it breaks against it, and does not sit there waiting.
        CombatEntity inTheWay = other(a, 4, 4, false);
        SunSpiritAI.CrystalFlight.Flown broken = SunSpiritAI.CrystalFlight.fly(thrown, a, player);
        assertTrue(broken.ends() && broken.spent());
        a.removeEntity(inTheWay);

        // A player: it bursts on their tile.
        stand(a, new GridPos(4, 4));
        SunSpiritAI.CrystalFlight.Flown hit = SunSpiritAI.CrystalFlight.fly(thrown, a, new GridPos(4, 4));
        assertTrue(hit.ends() && !hit.spent());
        assertEquals(new GridPos(4, 4), hit.at());
        stand(a, player);

        // And left to fly, its fourth turn is its last, with no burst to it.
        GridArena wide = floor(ROOM, new GridPos(0, 18));
        CombatEntity lonely = crystal(wide, 0, 0, SunSpiritAI.FIRE_CRYSTAL);
        lonely.setProjectileDirX(1);
        lonely.setProjectileDirZ(1);
        for (int turn = 1; turn <= SunSpiritAI.FIRE_CRYSTAL_TURNS; turn++) {
            EnemyAction.ProjectileMove flight = assertInstanceOf(EnemyAction.ProjectileMove.class,
                SunSpiritAI.CrystalFlight.INSTANCE.decideAction(lonely, wide, new GridPos(0, 18)));
            assertEquals(2, flight.path().size());
            for (GridPos step : flight.path()) assertTrue(wide.moveEntity(lonely, step));
            assertEquals(turn == SunSpiritAI.FIRE_CRYSTAL_TURNS, flight.impacts(), "turn " + turn);
        }
        assertTrue(SunSpiritAI.isSpent(lonely));
        assertEquals(new GridPos(8, 8), lonely.getGridPos(), "eight tiles: a corner to the middle of the room");
    }

    // ================================================================
    // The guard and the freeze
    // ================================================================

    @Test
    @DisplayName("from its first turn nothing hurts it, and the clue names its ice")
    void holdsItsGuard() {
        GridArena a = arena(FAR_CORNER);
        CombatEntity self = boss(a, 4, 4);
        ai(1, 0).decideAction(self, a, FAR_CORNER);

        assertTrue(self.isDamageImmune());
        assertTrue(self.getDamageImmuneHint().contains("No man, hero, or villain can harm me"));
        assertTrue(self.getDamageImmuneHint().contains("Ice Crystal"));
        assertEquals(0, self.takeDamage(50));
        assertEquals(HP, self.getCurrentHp());
    }

    @Test
    @DisplayName("a struck Ice Crystal lands through the guard and freezes it for two turns, then it flares free")
    void anIceCrystalFreezesIt() {
        GridPos player = new GridPos(4, 10);
        GridArena a = arena(player);
        CombatEntity self = boss(a, 4, 4);
        Lab ai = ai(1, 0);
        ai.decideAction(self, a, player);
        GridPos held = self.getGridPos();

        int dealt = self.takeDamageThroughImmunity(SunSpiritAI.deflectDamage(8, HP));
        assertEquals(84, dealt);
        List<String> told = SunSpiritAI.struck(self, dealt);

        assertTrue(SunSpiritAI.isFrozen(self));
        assertFalse(self.isDamageImmune(), "the freeze is the whole point: the guard has to be down");
        assertTrue(self.isFrozen(), "the engine's own flag puts Frozen on the panel");
        assertTrue(told.contains(SunSpiritAI.FROZEN_CLUE), "the first freeze carries the Aether's clue");
        assertTrue(self.takeDamage(10) > 0, "an ordinary hit lands while it is frozen");
        assertTrue(ai.computeThreatTiles(self, a).isEmpty(), "it threatens nothing with a frozen turn to sit out");

        // Two of its turns are spent frozen. The first brings out the Fire Minion it owes.
        EnemyAction first = ai.decideAction(self, a, player);
        EnemyAction.SummonMinions minion = only(first, EnemyAction.SummonMinions.class);
        assertNotNull(minion);
        assertEquals("aether:fire_minion", minion.entityTypeId());
        assertEquals(1, minion.count());
        assertTrue(SunSpiritAI.ring(a, held, 2, 2).contains(minion.positions().get(0)), "the minion appears beside it");
        assertEquals(SunSpiritAI.minionHp(HP), minion.hp());
        assertTrue(minion.atk() <= ATTACK, "a minion's hit is not capped by the engine, so it is kept under the boss's");
        assertNull(only(first, EnemyAction.Move.class));
        assertNull(only(first, EnemyAction.TileAreaAttack.class));

        EnemyAction second = ai.decideAction(self, a, player);
        assertInstanceOf(EnemyAction.Idle.class, second);
        assertFalse(self.isDamageImmune(), "still open through the player turn after its second frozen turn");
        assertEquals(held, self.getGridPos());

        // The third turn it thaws. What it showed for that turn is the flare around where it
        // was frozen as well as the ring it carries off, and it burns them as one hit.
        Set<GridPos> shown = ai.computeThreatTiles(self, a);
        assertTrue(shown.containsAll(SunSpiritAI.ring(a, held, 2, 2)), "the flare was not shown");
        EnemyAction third = ai.decideAction(self, a, player);
        assertFalse(SunSpiritAI.isFrozen(self));
        assertFalse(self.isFrozen());
        assertTrue(self.isDamageImmune());
        assertNotNull(only(third, EnemyAction.Move.class));
        EnemyAction.TileAreaAttack flare = only(third, EnemyAction.TileAreaAttack.class);
        assertTrue(flare.tiles().containsAll(SunSpiritAI.ring(a, held, 2, 2)));
        assertEquals(flare.tiles().size(), new HashSet<>(flare.tiles()).size(), "no tile is burned twice");
        assertTrue(shown.containsAll(flare.tiles()));
    }

    @Test
    @DisplayName("a freeze costs it two turns in every phase, and freezing it again never shortens what is left")
    void freezeLengths() {
        assertEquals(2, SunSpiritAI.FREEZE_TURNS);

        GridArena a = arena(FAR_CORNER);
        CombatEntity self = boss(a, 4, 4);
        Lab ai = ai(1, 0);
        List<String> first = SunSpiritAI.struck(self, 5);
        List<String> again = SunSpiritAI.struck(self, 5);
        assertTrue(first.contains(SunSpiritAI.FROZEN_CLUE));
        assertFalse(again.contains(SunSpiritAI.FROZEN_CLUE), "the clue is given once");
        ai.decideAction(self, a, FAR_CORNER);
        ai.decideAction(self, a, FAR_CORNER);
        assertTrue(SunSpiritAI.isFrozen(self), "two turns, and the second freeze did not stack a third and fourth");
        ai.decideAction(self, a, FAR_CORNER);
        assertFalse(SunSpiritAI.isFrozen(self));
    }

    @Test
    @DisplayName("it has two Fire Minions out at most")
    void minionsAreCapped() {
        GridArena a = arena(FAR_CORNER);
        CombatEntity self = boss(a, 4, 4);
        Lab ai = ai(1, 0);
        for (int i = 0; i < SunSpiritAI.MAX_MINIONS; i++) {
            CombatEntity minion = other(a, 9, 2 + i, false);
            ai.registerSpawnedMinion(minion.getEntityId());
        }
        SunSpiritAI.freeze(self, 2);
        assertNull(only(ai.decideAction(self, a, FAR_CORNER), EnemyAction.SummonMinions.class));
    }

    @Test
    @DisplayName("an Ice Crystal that kills it says its last words, once")
    void lastWords() {
        GridArena a = arena(FAR_CORNER);
        CombatEntity self = boss(a, 4, 4);
        int dealt = self.takeDamageThroughImmunity(HP * 10);
        assertFalse(self.isAlive());
        List<String> told = SunSpiritAI.struck(self, dealt);
        assertTrue(told.contains(SunSpiritAI.LAST_WORDS));
        assertFalse(SunSpiritAI.isFrozen(self), "nothing is frozen that is already dead");
        assertNull(SunSpiritAI.lastWords(self), "said once and not again");
        assertNull(new SunSpiritAI().getLastWords(self));
        assertNull(SunSpiritAI.lastWords(other(a, 9, 9, false)));
        assertNull(SunSpiritAI.lastWords(null));
    }

    // ================================================================
    // The shot
    // ================================================================

    @Test
    @DisplayName("a struck Ice Crystal flies straight away from whoever struck it, and lands only if that line passes within a tile of it")
    void theReturnIsAShot() {
        GridArena a = arena(FAR_CORNER);
        CombatEntity self = boss(a, 6, 6);

        // Struck from the west it flies east along its row, into the Sun Spirit.
        SunSpiritAI.Shot along = SunSpiritAI.shot(a, self, new GridPos(2, 6), new GridPos(1, 6));
        assertTrue(along.lands());
        assertEquals(1, along.dx());
        assertEquals(0, along.dz());
        assertEquals(new GridPos(5, 6), along.end(new GridPos(2, 6)), "it lands on the first tile within one of it");

        // A line that passes one tile from its body still finds it. Two tiles off does not,
        // and that crystal shatters on the far wall.
        assertTrue(SunSpiritAI.shot(a, self, new GridPos(2, 5), new GridPos(1, 5)).lands());
        SunSpiritAI.Shot wide = SunSpiritAI.shot(a, self, new GridPos(2, 4), new GridPos(1, 4));
        assertFalse(wide.lands());
        assertEquals(new GridPos(SIZE - 1, 4), wide.end(new GridPos(2, 4)));

        // The same crystal struck from the wrong side goes the wrong way.
        assertFalse(SunSpiritAI.shot(a, self, new GridPos(2, 6), new GridPos(3, 6)).lands());

        // A diagonal is as good a line as a row, and distance is nothing to it.
        assertTrue(SunSpiritAI.shot(a, self, new GridPos(1, 1), new GridPos(0, 0)).lands());

        // Struck from further off, it takes the nearest of the eight ways to the line
        // from the striker through the crystal.
        SunSpiritAI.Shot ranged = SunSpiritAI.shot(a, self, new GridPos(3, 6), new GridPos(0, 5));
        assertEquals(1, ranged.dx());
        assertEquals(0, ranged.dz());
        assertTrue(ranged.lands());

        // A block in the way stops it. Frost does not.
        paint(a, TileType.OBSTACLE, 4, 6);
        assertFalse(SunSpiritAI.shot(a, self, new GridPos(2, 6), new GridPos(1, 6)).lands());
        paint(a, TileType.NORMAL, 4, 6);
        frost(a, 4, 6);
        assertTrue(SunSpiritAI.shot(a, self, new GridPos(2, 6), new GridPos(1, 6)).lands());

        // Lying right against it, it still has to be sent the right way.
        assertEquals(1, SunSpiritAI.gap(self.getGridPos(), 2, 2, new GridPos(5, 5)));
        assertFalse(SunSpiritAI.shot(a, self, new GridPos(5, 5), new GridPos(6, 4)).lands());
        assertEquals(0, SunSpiritAI.gap(self.getGridPos(), 2, 2, new GridPos(7, 7)));
        assertEquals(2, SunSpiritAI.gap(self.getGridPos(), 2, 2, new GridPos(9, 4)));
    }

    // ================================================================
    // Frost
    // ================================================================

    @Test
    @DisplayName("the floor freezes three by three where the crystal was struck: fire and all, but not obstacles, and not under it or against it")
    void whereTheFloorFreezes() {
        GridArena a = arena(FAR_CORNER);
        CombatEntity self = boss(a, 4, 4);
        paint(a, TileType.OBSTACLE, 9, 7);
        lay(a, TileType.FIRE, new GridPos(8, 7), 2);
        paint(a, TileType.WATER, 10, 6);

        // Struck at (9,6), well clear of it: the patch reaches (8..10, 5..7).
        Set<GridPos> patch = new HashSet<>(SunSpiritAI.frostPatch(a, self, new GridPos(9, 6)));
        assertTrue(patch.contains(new GridPos(9, 6)));
        assertTrue(patch.contains(new GridPos(8, 7)), "fire on the patch is put out by it");
        assertFalse(patch.contains(new GridPos(9, 7)), "a block is not floor");
        assertFalse(patch.contains(new GridPos(10, 6)), "water is not floor");
        assertEquals(7, patch.size());

        // Struck right against it: the floor under it and the floor touching it stay as they
        // are. Only the far column of the patch freezes.
        Set<GridPos> against = new HashSet<>(SunSpiritAI.frostPatch(a, self, new GridPos(6, 5)));
        assertEquals(Set.of(new GridPos(7, 4), new GridPos(7, 5), new GridPos(7, 6)), against);

        // So no one patch can shut it in. In a corner of the room, the one tile that would
        // close both ways out at once is diagonal to it, and struck there the frost leaves
        // every tile it needs to step through.
        GridArena corner = arena(FAR_CORNER);
        CombatEntity cornered = boss(corner, 0, 0);
        for (GridPos tile : SunSpiritAI.frostPatch(corner, cornered, new GridPos(2, 2))) {
            lay(corner, SunSpiritAI.FROZEN_FLOOR, tile, SunSpiritAI.FROST_TURNS);
        }
        assertTrue(SunSpiritAI.isFrost(corner, new GridPos(3, 3)), "fixture: the patch should have frozen something");
        assertTrue(SunSpiritAI.canStep(corner, cornered, cornered.getGridPos(), 1, 0));
        assertTrue(SunSpiritAI.canStep(corner, cornered, cornered.getGridPos(), 0, 1));

        assertEquals(4, SunSpiritAI.FROST_TURNS);
        assertTrue(SunSpiritAI.FROZEN_FLOOR.walkable, "anyone may walk on it");
        assertEquals(0, SunSpiritAI.FROZEN_FLOOR.damageOnStep, "and it harms nobody");
    }

    @Test
    @DisplayName("frozen floor turns it like stone, so a patch across its path sends it back")
    void frostIsAWall() {
        GridArena a = arena(FAR_CORNER);
        CombatEntity self = boss(a, 2, 4);
        for (int z = 3; z <= 5; z++) frost(a, 5, z);

        SunSpiritAI.Drift drift = SunSpiritAI.planDrift(a, self, 1, 0, SPEED);
        for (GridPos step : drift.path()) {
            for (GridPos tile : body(step)) assertFalse(SunSpiritAI.isFrost(a, tile), "it drifted onto frost at " + tile);
        }
        assertEquals(-1, drift.headingX());
    }

    @Test
    @DisplayName("a brazier with frost touching it is dark, and is lit again the moment the frost is gone")
    void frostDarkensABrazier() {
        GridArena a = room(new GridPos(9, 9));
        GridPos brazier = BRAZIERS.get(0);
        assertTrue(SunSpiritAI.lit(a, brazier));
        frost(a, 3, 3);
        assertFalse(SunSpiritAI.lit(a, brazier), "corner to corner counts as touching");
        assertTrue(SunSpiritAI.lit(a, BRAZIERS.get(1)), "the others burn on");
        frost(a, 5, 5);
        paint(a, TileType.NORMAL, 3, 3);
        assertTrue(SunSpiritAI.lit(a, brazier), "frost two tiles off does not reach it");

        // Frost on its last round is gone by the time anyone looks again.
        lay(a, SunSpiritAI.FROZEN_FLOOR, new GridPos(1, 2), 1);
        assertFalse(SunSpiritAI.lit(a, brazier));
        assertFalse(SunSpiritAI.frostBeside(a, brazier, 1), "it is shown lit a turn early, for when the players next look");
    }

    // ================================================================
    // Braziers
    // ================================================================

    @Test
    @DisplayName("braziers are asleep above two thirds of its health")
    void braziersSleepAtFirst() {
        GridArena a = room(new GridPos(9, 16));
        CombatEntity self = boss(a, CENTRE.x(), CENTRE.z());
        Lab ai = inRoom(1);
        for (int turn = 0; turn < 4; turn++) {
            ai.decideAction(self, a, new GridPos(9, 16));
            assertTrue(ai.lanes().isEmpty());
        }
    }

    @Test
    @DisplayName("a lane runs from its brazier to the wall along the row, column or diagonal that comes closest to the player, and frost stops it")
    void whereALaneRuns() {
        GridArena a = room(new GridPos(9, 9));
        GridPos corner = BRAZIERS.get(0);
        assertArrayEquals(new int[]{1, 1}, SunSpiritAI.laneWay(a, corner, new GridPos(9, 9)));
        assertArrayEquals(new int[]{1, 0}, SunSpiritAI.laneWay(a, corner, new GridPos(12, 3)));
        assertArrayEquals(new int[]{0, 1}, SunSpiritAI.laneWay(a, corner, new GridPos(1, 12)));
        assertArrayEquals(new int[]{-1, -1}, SunSpiritAI.laneWay(a, BRAZIERS.get(3), new GridPos(9, 9)));

        // Across the room, as far as the brazier in the corner opposite.
        SunSpiritAI.Lane diagonal = new SunSpiritAI.Lane(corner, 1, 1);
        List<GridPos> across = SunSpiritAI.laneTiles(a, diagonal);
        assertEquals(13, across.size());
        assertEquals(new GridPos(3, 3), across.get(0));
        assertEquals(new GridPos(15, 15), across.get(12));
        assertEquals(across, SunSpiritAI.laneFire(a, BRAZIERS, List.of(diagonal)));

        // Along a row the floor squarely beside a brazier, its own and the one it runs up
        // to, is left unlit: fire there would burn the brazier down.
        SunSpiritAI.Lane row = new SunSpiritAI.Lane(corner, 1, 0);
        List<GridPos> lit = SunSpiritAI.laneFire(a, BRAZIERS, List.of(row));
        assertEquals(11, lit.size());
        assertFalse(lit.contains(new GridPos(3, 2)));
        assertFalse(lit.contains(new GridPos(15, 2)));

        // Floor already burning is left as it is, and two lanes over one tile light it once.
        lay(a, TileType.FIRE, new GridPos(5, 5), 9);
        assertEquals(12, SunSpiritAI.laneFire(a, BRAZIERS, List.of(diagonal, diagonal)).size());
        paint(a, TileType.NORMAL, 5, 5);

        // Frost stops a lane where the lane meets it, and is not lit.
        frost(a, 8, 8);
        assertEquals(5, SunSpiritAI.laneFire(a, BRAZIERS, List.of(diagonal)).size());

        // And a lane whose brazier has gone dark lights nothing at all.
        frost(a, 1, 1);
        assertTrue(SunSpiritAI.laneFire(a, BRAZIERS, List.of(diagonal, row)).isEmpty());
    }

    @Test
    @DisplayName("from two thirds down one lit brazier marks a lane each turn, in turn round the room, and its next turn lights what was shown, for two turns")
    void braziersTakeTurns() {
        GridPos player = new GridPos(9, 13);
        GridArena a = room(player);
        CombatEntity self = hurtTo(boss(a, 8, 4), 560);
        SunSpiritAI.freeze(self, 9);
        Fight fight = new Fight(a, self, inRoom(1), player, 560);

        List<GridPos> marked = new ArrayList<>();
        int lit = 0;
        for (int turn = 0; turn < 8; turn++) {
            Set<GridPos> shown = fight.ai.computeThreatTiles(self, a);
            if (turn == 0) assertTrue(shown.isEmpty(), "nothing was marked before this");
            if (turn == 1) assertEquals(13, shown.size(), "frozen, the lane is all it shows, and the room does not stop for it");
            EnemyAction action = fight.enemyPhase();
            Set<GridPos> laid = new HashSet<>();
            for (EnemyAction.CreateTerrain fire : all(action, EnemyAction.CreateTerrain.class)) {
                assertEquals(SunSpiritAI.LANE_TURNS, fire.duration());
                laid.addAll(fire.tiles());
            }
            assertEquals(shown, laid, "turn " + turn + ": what was lit is what was shown");
            lit += laid.size();
            assertEquals(1, fight.ai.lanes().size(), "turn " + turn + ": one a turn");
            marked.add(fight.ai.lanes().get(0).brazier());
        }
        List<GridPos> twice = new ArrayList<>(BRAZIERS);
        twice.addAll(BRAZIERS);
        assertEquals(twice, marked);
        assertTrue(lit > 20);
    }

    @Test
    @DisplayName("a dark brazier's turn is lost and not passed on, a lane it had marked is not lit, and two take their turn in its last third")
    void darkBraziersAndTheLastThird() {
        GridPos player = new GridPos(9, 13);
        GridArena a = room(player);
        CombatEntity self = hurtTo(boss(a, 8, 4), 560);
        SunSpiritAI.freeze(self, 20);
        Lab ai = inRoom(1);
        ai.decideAction(self, a, player);
        assertEquals(BRAZIERS.get(0), ai.lanes().get(0).brazier());
        assertFalse(ai.computeThreatTiles(self, a).isEmpty());

        // Frost beside the brazier that marked it, before its lane is lit: nothing is.
        frost(a, 3, 3);
        assertTrue(ai.computeThreatTiles(self, a).isEmpty());
        assertTrue(all(ai.decideAction(self, a, player), EnemyAction.CreateTerrain.class).isEmpty());

        // And for as long as it is dark its turn is lost. The round goes on past it, and
        // nothing is marked in its place.
        List<GridPos> marked = new ArrayList<>();
        for (int turn = 0; turn < 8; turn++) {
            assertTrue(ai.lanes().size() <= 1);
            marked.add(ai.lanes().isEmpty() ? null : ai.lanes().get(0).brazier());
            ai.decideAction(self, a, player);
        }
        assertEquals(java.util.Arrays.asList(
            BRAZIERS.get(1), BRAZIERS.get(2), BRAZIERS.get(3), null,
            BRAZIERS.get(1), BRAZIERS.get(2), BRAZIERS.get(3), null), marked);

        // In its last third two braziers take their turn each turn, and a dark one among
        // them still costs it that lane.
        hurtTo(self, 280);
        ai.decideAction(self, a, player);
        assertEquals(2, ai.lanes().size());
        assertEquals(BRAZIERS.get(2), ai.lanes().get(0).brazier());
        assertEquals(BRAZIERS.get(3), ai.lanes().get(1).brazier());
        ai.decideAction(self, a, player);
        assertEquals(1, ai.lanes().size());
        assertEquals(BRAZIERS.get(1), ai.lanes().get(0).brazier());
    }

    // ================================================================
    // The room burns inward
    // ================================================================

    @Test
    @DisplayName("the fire takes four rings of the real room and leaves eleven by eleven, and a small room less")
    void howFarTheFireComes() {
        GridArena a = room(new GridPos(9, 9));
        assertEquals(4, SunSpiritAI.maxRings(a));
        assertEquals(72, SunSpiritAI.ring(a, 0).size());
        assertEquals(64, SunSpiritAI.ring(a, 1).size());
        assertEquals(ROOM - 2 * SunSpiritAI.maxRings(a), SunSpiritAI.FLOOR_KEPT);
        assertEquals(2, SunSpiritAI.maxRings(floor(15, new GridPos(7, 7))));
        assertEquals(0, SunSpiritAI.maxRings(arena(FAR_CORNER)));
    }

    @Test
    @DisplayName("in its last third a ring catches every second turn it is free, shown the turn before, and stays lit")
    void theRoomBurnsInward() {
        GridPos player = new GridPos(9, 9);
        GridArena a = room(player);
        CombatEntity self = hurtTo(boss(a, 8, 12), 280);
        Fight fight = new Fight(a, self, inRoom(5), player, 280);

        List<Integer> depth = new ArrayList<>();
        for (int turn = 1; turn <= 10; turn++) {
            Set<GridPos> shown = fight.ai.computeThreatTiles(self, a);
            EnemyAction action = fight.enemyPhase();
            for (EnemyAction.CreateTerrain fire : all(action, EnemyAction.CreateTerrain.class)) {
                if (fire.duration() != SunSpiritAI.LIT_TURNS) continue;
                assertTrue(shown.containsAll(fire.tiles()), "turn " + turn + ": floor caught that was not shown");
                assertFalse(fire.tiles().isEmpty());
            }
            depth.add(fight.ai.rings());
        }
        // A turn of warning, then a ring on every second turn, four deep and no further.
        assertEquals(List.of(0, 1, 1, 2, 2, 3, 3, 4, 4, 4), depth);
        for (int ring = 0; ring < 4; ring++) {
            for (GridPos tile : SunSpiritAI.ring(a, ring)) {
                TileType type = typeAt(a, tile);
                if (type == TileType.OBSTACLE) continue;
                boolean besideBrazier = BRAZIERS.stream().anyMatch(b -> b.manhattanDistance(tile) == 1);
                assertEquals(besideBrazier ? TileType.NORMAL : TileType.FIRE, type, "ring " + ring + " at " + tile);
            }
        }
        for (GridPos tile : SunSpiritAI.ring(a, 4)) {
            assertFalse(typeAt(a, tile).isFlames() && a.getTile(tile).getTurnsRemaining() > SunSpiritAI.TRAIL_TURNS,
                "the fire came past its fourth ring at " + tile);
        }
    }

    @Test
    @DisplayName("frozen floor is never taken, and what it leaves when it melts is taken back with the next ring")
    void frostHoldsTheFireOff() {
        GridArena a = room(new GridPos(9, 9));
        for (int x = 5; x <= 7; x++) frost(a, x, 0);
        List<GridPos> first = SunSpiritAI.catching(a, BRAZIERS, 0);
        assertEquals(72 - 3, first.size());
        assertFalse(first.contains(new GridPos(6, 0)));

        for (GridPos tile : first) lay(a, TileType.FIRE, tile, SunSpiritAI.LIT_TURNS);
        for (int x = 5; x <= 7; x++) paint(a, TileType.NORMAL, x, 0);
        List<GridPos> second = SunSpiritAI.catching(a, BRAZIERS, 1);
        assertTrue(second.containsAll(List.of(new GridPos(5, 0), new GridPos(6, 0), new GridPos(7, 0))));
        // Ring one, less the two tiles squarely beside each brazier, and the three left behind.
        assertEquals(64 - 8 + 3, second.size());
    }

    @Test
    @DisplayName("every freeze puts the innermost burning ring out, and the fire takes a breath before it comes on again")
    void aFreezeDrivesTheFireBack() {
        GridPos player = new GridPos(9, 9);
        GridArena a = room(player);
        CombatEntity self = hurtTo(boss(a, 8, 12), 280);
        Fight fight = new Fight(a, self, inRoom(5), player, 280);
        for (int turn = 0; turn < 4; turn++) fight.enemyPhase();
        assertEquals(2, fight.ai.rings());

        SunSpiritAI.struck(self, 0);
        EnemyAction frozen = fight.enemyPhase();
        EnemyAction.CreateTerrain out = null;
        for (EnemyAction.CreateTerrain terrain : all(frozen, EnemyAction.CreateTerrain.class)) {
            if (terrain.duration() == 1) out = terrain;
        }
        assertNotNull(out, "the ring it owed back");
        assertEquals(1, out.duration(), "asked to burn one turn more is how a lit tile is put out");
        assertTrue(SunSpiritAI.ring(a, 1).containsAll(out.tiles()), "only tiles of the innermost burning ring");
        assertFalse(out.tiles().isEmpty());
        assertEquals(1, fight.ai.rings());
        for (GridPos tile : out.tiles()) assertEquals(TileType.NORMAL, typeAt(a, tile), "it is floor again");
        for (GridPos tile : SunSpiritAI.ring(a, 0)) assertTrue(typeAt(a, tile).isFlames(), "the outer ring burns on");

        // Its second frozen turn, then the thaw, then it takes the ring again.
        fight.enemyPhase();
        assertEquals(1, fight.ai.rings());
        fight.enemyPhase();
        assertEquals(1, fight.ai.rings());
        fight.enemyPhase();
        assertEquals(2, fight.ai.rings());
    }

    // ================================================================
    // What the player is shown
    // ================================================================

    @Test
    @DisplayName("what it shows is what it does, and asking does not change the answer")
    void threatTilesAreTheTurn() {
        for (int seed = 0; seed < 40; seed++) {
            Random dice = new Random(seed);
            GridPos rolled = new GridPos(dice.nextInt(ROOM), 14 + dice.nextInt(4));
            GridPos player = BRAZIERS.contains(rolled) ? new GridPos(9, 15) : rolled;
            GridArena a = room(player);
            int hp = new int[]{HP, 560, 280}[seed % 3];
            CombatEntity self = hurtTo(boss(a, 4 + dice.nextInt(10), 4 + dice.nextInt(6)), hp);
            Fight fight = new Fight(a, self, inRoom(seed), player, hp);
            for (int turn = 0; turn < 12; turn++) {
                if (turn == 5) SunSpiritAI.struck(self, 0);
                Set<GridPos> shown = fight.ai.computeThreatTiles(self, a);
                assertEquals(shown, fight.ai.computeThreatTiles(self, a), "asking twice must not change the answer");
                EnemyAction action = fight.enemyPhase();
                Set<GridPos> touched = new HashSet<>();
                for (EnemyAction.CreateTerrain fire : all(action, EnemyAction.CreateTerrain.class)) {
                    if (fire.duration() > 1) touched.addAll(fire.tiles());
                }
                for (EnemyAction.TileAreaAttack burn : all(action, EnemyAction.TileAreaAttack.class)) touched.addAll(burn.tiles());
                assertTrue(shown.containsAll(touched), "seed " + seed + " turn " + turn + ": it burned ground it never showed");
            }
        }
    }

    // ================================================================
    // Whole fights
    // ================================================================

    /** Where a hit on the player came from. */
    private enum Source { AURA, FLARE, TRAIL, RING, LANE, CROSSED_FIRE, FIRE_CRYSTAL, ICE_CRYSTAL, MINION }

    /** What the player at the board does with an Ice Crystal. Each does everything the one before it does. */
    private enum Skill {
        /** Strikes it the moment it is in reach, from wherever that leaves them standing. */
        SNAPS,
        /** Gets round to where the shot lines up first, for as long as the crystal allows, and strikes anyway if it will not. */
        AIMS,
        /** And of the places the shot lines up from, takes the one where the frost will fall somewhere worth having. */
        FENCES
    }

    /**
     * A fight played out on a bare grid. The boss is asked for its turn and each kind of
     * action is resolved the way the engine resolves it: the walk moves it, terrain is laid
     * for its rounds, the area hit lands on whoever is standing in it, crystals are put on
     * the board and then flown by the brains the engine would run for them, minions close
     * and strike, and passing terrain runs down as the round ends. The player is whoever
     * {@link #playerTurn} says they are.
     *
     * <p>Every enemy phase also holds the boss to what it showed. A player who ended their
     * turn off the threat tiles and off burning floor must come through its turn untouched
     * by its aura, its flare and its fire, lanes included. Only a crystal, or a minion, may
     * reach them.
     */
    private static final class Fight {
        private static final EnemyAI STRAIGHT = new ProjectileAI();
        private static final EnemyAI SEEKER = new SeekingProjectileAI();
        private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        /** Tiles a player walks in a turn. */
        private static final int MOVE = 3;
        /** Tiles a Fire Minion walks in a turn. */
        private static final int MINION_REACH = 3;
        /** Turns a player lets an Ice Crystal come on while they look for the shot. */
        private static final int PATIENCE = 3;

        final GridArena a;
        final CombatEntity boss;
        final Lab ai;
        /** The health it is put back to after each deflect, so that a run stays in one phase. Zero lets it fall. */
        final int holdHp;
        GridPos player;
        final List<CombatEntity> crystals = new ArrayList<>();
        final List<CombatEntity> minions = new ArrayList<>();
        /** Which of its fires lit each tile that has burned. */
        final Map<GridPos, Source> litBy = new HashMap<>();

        final int[] hits = new int[Source.values().length];
        int turns, struck, landed, freezes, besideFrozen, blowsLanded, cornered, waited;
        int seekers, aimedDiagonally, aimedSquare, fizzled, minionsCutDown;
        int aloftSum, aloftMost, darkBrazierTurns, laneTilesLit;
        /** Free turns it ended where it began, free turns it made no move at all, and the longest run of those. */
        int cameBack, stuck, stuckRun, stuckLongest, stuckOnFrost;
        /** Free turns it threw no crystal of its own, for want of a fair tile to throw from. */
        int heldItsCrystal;
        /** Of the tiles the player could end a turn on, how many there were and how many would have hurt. */
        int tilesInReach, tilesUnsafe;

        Fight(GridArena a, CombatEntity boss, Lab ai, GridPos player, int holdHp) {
            this.a = a;
            this.boss = boss;
            this.ai = ai;
            this.player = player;
            this.holdHp = holdHp;
            stand(a, player);
        }

        // ── The enemy phase ──

        EnemyAction enemyPhase() {
            stand(a, player);
            boolean wasFrozen = SunSpiritAI.isFrozen(boss);
            boolean satOut = SunSpiritAI.sitsOut(boss);
            boolean frostAgainstIt = false;
            for (GridPos tile : SunSpiritAI.ring(a, boss.getGridPos(), 2, 2)) frostAgainstIt |= SunSpiritAI.isFrost(a, tile);
            SunSpiritAI.Plan plan = ai.plan(boss, a, ai.braziers(a));
            Set<GridPos> laneTiles = new HashSet<>(plan.lanes());
            Set<GridPos> shown = ai.computeThreatTiles(boss, a);
            assertEquals(plan.threat(boss), shown, "what it shows is not what it plans");
            boolean offThePaint = !shown.contains(player) && !burning(player);
            int burnsBefore = hits[Source.AURA.ordinal()] + hits[Source.FLARE.ordinal()];
            GridPos stoodAt = boss.getGridPos();

            EnemyAction action = ai.decideAction(boss, a, player);
            assertRunsWhole(action);
            Set<CombatEntity> fresh = new HashSet<>();
            for (EnemyAction sub : flat(action)) resolve(sub, wasFrozen, fresh, laneTiles);

            for (GridPos tile : GridArena.getOccupiedTiles(boss)) {
                assertTrue(SunSpiritAI.crossable(typeAt(a, tile)), "it came to rest on " + typeAt(a, tile) + " at " + tile);
                assertFalse(tile.equals(player), "it came to rest on the player");
            }
            if (!satOut) {
                if (stoodAt.equals(boss.getGridPos())) cameBack++;
                if (all(action, EnemyAction.Move.class).isEmpty()) {
                    stuck++;
                    if (frostAgainstIt) stuckOnFrost++;
                    stuckLongest = Math.max(stuckLongest, ++stuckRun);
                } else {
                    stuckRun = 0;
                }
                if (launched(action, SunSpiritAI.FIRE_CRYSTAL).isEmpty()
                        && launched(action, SunSpiritAI.ICE_CRYSTAL).isEmpty()) heldItsCrystal++;
            }
            if (offThePaint) {
                assertEquals(burnsBefore, hits[Source.AURA.ordinal()] + hits[Source.FLARE.ordinal()],
                    "turn " + turns + ": burned on a tile it never showed, at " + player);
                assertFalse(burning(player), "turn " + turns + ": fire was laid under a player it never warned, at " + player);
            }

            for (CombatEntity crystal : new ArrayList<>(crystals)) {
                boolean struckThem = fly(crystal);
                assertFalse(struckThem && fresh.contains(crystal),
                    "turn " + turns + ": a crystal landed on the turn it was thrown, before it could be answered");
            }
            for (CombatEntity minion : new ArrayList<>(minions)) {
                // The engine's own rule for a stunned mob: it loses the turn and the stun with it.
                if (minion.isStunned()) minion.setStunned(false);
                else chase(minion);
            }

            for (GridPos brazier : ai.braziers(a)) if (!SunSpiritAI.lit(a, brazier)) darkBrazierTurns++;
            int aloft = 0;
            for (CombatEntity crystal : crystals) if (sourceOf(crystal) == Source.FIRE_CRYSTAL) aloft++;
            aloftSum += aloft;
            aloftMost = Math.max(aloftMost, aloft);
            roundEnds();
            turns++;
            return action;
        }

        private void resolve(EnemyAction sub, boolean wasFrozen, Set<CombatEntity> fresh, Set<GridPos> laneTiles) {
            if (sub instanceof EnemyAction.Move move) {
                for (GridPos step : move.path()) {
                    assertTrue(a.moveEntity(boss, step), "the path led somewhere the grid refused: " + step);
                }
            } else if (sub instanceof EnemyAction.CreateTerrain terrain) {
                assertEquals(TileType.FIRE, terrain.terrainType(), "fire is the only ground it lays");
                for (GridPos tile : terrain.tiles()) {
                    TileType was = typeAt(a, tile);
                    if (terrain.duration() == 1) {
                        assertTrue(was.isFlames(), "only burning floor is put out");
                    } else {
                        assertEquals(TileType.NORMAL, was, "fire was laid on " + was + " at " + tile);
                        for (GridPos brazier : ai.braziers(a)) {
                            assertFalse(brazier.manhattanDistance(tile) == 1,
                                "fire squarely beside a brazier at " + tile + " would burn it down");
                        }
                        boolean lane = laneTiles.contains(tile) && terrain.duration() == SunSpiritAI.LANE_TURNS;
                        if (lane) laneTilesLit++;
                        litBy.put(tile, terrain.duration() == SunSpiritAI.LIT_TURNS ? Source.RING
                            : lane ? Source.LANE : Source.TRAIL);
                    }
                    lay(a, TileType.FIRE, tile, terrain.duration());
                }
            } else if (sub instanceof EnemyAction.TileAreaAttack burn) {
                if (burn.tiles().contains(player)) hits[(wasFrozen ? Source.FLARE : Source.AURA).ordinal()]++;
            } else if (sub instanceof EnemyAction.SpawnProjectile launch) {
                for (int i = 0; i < launch.positions().size(); i++) {
                    GridPos spot = launch.positions().get(i);
                    assertTrue(spot.chebyshevDistanceTo(player) >= SunSpiritAI.HEAD_START,
                        "a crystal was thrown from " + spot + " with the player at " + player);
                    CombatEntity thrown = new CombatEntity(nextId++, launch.entityTypeId(), spot,
                        launch.hp(), launch.atk(), launch.def(), 1);
                    thrown.setProjectile(true);
                    thrown.setProjectileType(launch.projectileType());
                    thrown.setAiOverrideKey("projectile");
                    thrown.setProjectileOwnerId(boss.getEntityId());
                    // The engine's own aim: down the row or the column, whichever is longer.
                    int dx = player.x() - spot.x();
                    int dz = player.z() - spot.z();
                    thrown.setProjectileDirX(Math.abs(dx) >= Math.abs(dz) ? Integer.signum(dx) : 0);
                    thrown.setProjectileDirZ(Math.abs(dx) >= Math.abs(dz) ? 0 : Integer.signum(dz));
                    assertTrue(a.placeEntity(thrown), "a crystal was thrown onto a tile that was not free: " + spot);
                    crystals.add(thrown);
                    fresh.add(thrown);
                    ai.registerSpawnedProjectile(thrown.getEntityId());
                    if ("seeking_projectile".equals(thrown.getAiKey())) seekers++;
                    else if (thrown.getProjectileDirX() != 0 && thrown.getProjectileDirZ() != 0) aimedDiagonally++;
                    else aimedSquare++;
                }
            } else if (sub instanceof EnemyAction.SummonMinions summon) {
                for (GridPos spot : summon.positions()) {
                    CombatEntity minion = new CombatEntity(nextId++, summon.entityTypeId(), spot,
                        summon.hp(), summon.atk(), summon.def(), 1, 1, 3);
                    assertTrue(a.placeEntity(minion), "a minion was summoned onto a tile that was not free");
                    minions.add(minion);
                    ai.registerSpawnedMinion(minion.getEntityId());
                }
            } else if (!(sub instanceof EnemyAction.Idle)) {
                fail("the engine has no way to run " + sub.getClass().getSimpleName() + " here");
            }
        }

        /** Fly one crystal by the brain the engine would run for it. True if it landed on the player. */
        private boolean fly(CombatEntity crystal) {
            if (!(brainOf(crystal).decideAction(crystal, a, player) instanceof EnemyAction.ProjectileMove flight)) return false;
            for (GridPos step : flight.path()) {
                if (!step.equals(player)) assertTrue(a.moveEntity(crystal, step), "a crystal flew into something at " + step);
            }
            if (!flight.impacts()) return false;
            // The engine runs the impact and then removes the crystal. One that broke or
            // burnt out has no burst to it.
            boolean struckThem = !SunSpiritAI.isSpent(crystal)
                && lands(crystal, flight.impactPos() != null ? flight.impactPos() : crystal.getGridPos(), player);
            if (struckThem) hits[sourceOf(crystal).ordinal()]++;
            if (SunSpiritAI.isSpent(crystal)) fizzled++;
            remove(crystal);
            return struckThem;
        }

        /** The brain the engine would run for this crystal: its own if it was given one, else the one its key names. */
        private static EnemyAI brainOf(CombatEntity crystal) {
            if (crystal.getAiInstance() != null) return crystal.getAiInstance();
            return "seeking_projectile".equals(crystal.getAiKey()) ? SEEKER : STRAIGHT;
        }

        /** Whether this crystal's next turn of flight would hurt someone standing on {@code tile}. Changes nothing. */
        private boolean wouldLandOn(CombatEntity crystal, GridPos tile) {
            if (crystal.getAiInstance() instanceof SunSpiritAI.CrystalFlight) {
                SunSpiritAI.CrystalFlight.Flown flown = SunSpiritAI.CrystalFlight.fly(crystal, a, tile);
                return flown.ends() && !flown.spent() && lands(crystal, flown.at(), tile);
            }
            return brainOf(crystal).decideAction(crystal, a, tile) instanceof EnemyAction.ProjectileMove flight
                && flight.impacts()
                && lands(crystal, flight.impactPos() != null ? flight.impactPos() : crystal.getGridPos(), tile);
        }

        private static boolean lands(CombatEntity crystal, GridPos at, GridPos on) {
            return SunSpiritAI.ICE_CRYSTAL.equals(crystal.getProjectileType())
                ? at.equals(on)
                : at.chebyshevDistanceTo(on) <= SunSpiritAI.FIRE_BURST_RADIUS;
        }

        private static Source sourceOf(CombatEntity crystal) {
            return SunSpiritAI.ICE_CRYSTAL.equals(crystal.getProjectileType()) ? Source.ICE_CRYSTAL : Source.FIRE_CRYSTAL;
        }

        /** A Fire Minion walks at the player, three tiles a turn, and hits them from beside. */
        private void chase(CombatEntity minion) {
            GridPos best = minion.getGridPos();
            for (GridPos tile : reach(minion.getGridPos(), MINION_REACH, true).keySet()) {
                if (tile.manhattanDistance(player) < best.manhattanDistance(player)) best = tile;
            }
            a.moveEntity(minion, best);
            if (best.manhattanDistance(player) <= 1) hits[Source.MINION.ordinal()]++;
        }

        private void remove(CombatEntity gone) {
            a.removeEntity(gone);
            gone.takeDamage(1_000_000);
            crystals.remove(gone);
            minions.remove(gone);
        }

        /** The engine's end of round: every passing tile counts down, and one that runs out is floor again. */
        private void roundEnds() {
            for (int x = 0; x < a.getWidth(); x++) {
                for (int z = 0; z < a.getHeight(); z++) {
                    GridTile tile = a.getTile(x, z);
                    if (tile.getTurnsRemaining() <= 0) continue;
                    tile.setTurnsRemaining(tile.getTurnsRemaining() - 1);
                    if (tile.getTurnsRemaining() == 0) paint(a, TileType.NORMAL, x, z);
                }
            }
        }

        private boolean burning(GridPos tile) {
            return typeAt(a, tile).isFlames();
        }

        // ── The player ──

        /** Tiles that can be walked to from {@code from} in {@code steps}, with how far each is. */
        private Map<GridPos, Integer> reach(GridPos from, int steps, boolean throughFire) {
            Map<GridPos, Integer> seen = new LinkedHashMap<>();
            seen.put(from, 0);
            ArrayDeque<GridPos> queue = new ArrayDeque<>(List.of(from));
            while (!queue.isEmpty()) {
                GridPos at = queue.poll();
                int far = seen.get(at);
                if (far == steps) continue;
                for (int[] way : STEPS) {
                    GridPos next = new GridPos(at.x() + way[0], at.z() + way[1]);
                    if (seen.containsKey(next) || !a.isInBounds(next) || next.equals(player)) continue;
                    GridTile ground = a.getTile(next);
                    if (ground == null || !ground.isWalkable() || a.getOccupant(next) != null) continue;
                    if (!throughFire && ground.getType().isFlames()) continue;
                    seen.put(next, far + 1);
                    queue.add(next);
                }
            }
            return seen;
        }

        /** How many things would hurt someone who ended their turn on {@code tile}. */
        private int danger(GridPos tile) {
            GridPos was = player;
            player = tile;
            stand(a, tile);
            int count = 0;
            if (burning(tile)) count += 2;
            if (ai.computeThreatTiles(boss, a).contains(tile)) count += 2;
            for (CombatEntity crystal : crystals) {
                if (wouldLandOn(crystal, tile)) count++;
            }
            for (CombatEntity minion : minions) {
                for (GridPos step : reach(minion.getGridPos(), MINION_REACH, true).keySet()) {
                    if (step.manhattanDistance(tile) <= 1) {
                        count++;
                        break;
                    }
                }
            }
            player = was;
            stand(a, was);
            return count;
        }

        /**
         * The least dangerous tile within {@code steps} of {@code from}, and of those the best
         * liked. Someone with nowhere safe to go on open floor will walk through fire for it,
         * and takes the burn.
         */
        private GridPos settle(GridPos from, int steps, ToIntFunction<GridPos> liking) {
            GridPos best = from;
            int bestDanger = Integer.MAX_VALUE;
            int bestLiking = Integer.MIN_VALUE;
            Map<GridPos, Integer> dry = reach(from, steps, false);
            for (GridPos tile : dry.keySet()) {
                int risk = danger(tile);
                int liked = liking.applyAsInt(tile);
                tilesInReach++;
                if (risk > 0) tilesUnsafe++;
                if (risk < bestDanger || (risk == bestDanger && liked > bestLiking)) {
                    best = tile;
                    bestDanger = risk;
                    bestLiking = liked;
                }
            }
            boolean throughFire = false;
            if (bestDanger > 0) {
                for (GridPos tile : reach(from, steps, true).keySet()) {
                    if (dry.containsKey(tile) || burning(tile)) continue;
                    int risk = danger(tile) + 1;
                    if (risk < bestDanger) {
                        best = tile;
                        bestDanger = risk;
                        throughFire = true;
                    }
                }
            }
            if (bestDanger > 0) cornered++;
            if (throughFire) hits[Source.CROSSED_FIRE.ordinal()]++;
            return best;
        }

        private int wallGap(GridPos tile) {
            return Math.min(Math.min(tile.x(), tile.z()), Math.min(a.getWidth() - 1 - tile.x(), a.getHeight() - 1 - tile.z()));
        }

        private CombatEntity iceCrystal() {
            for (CombatEntity crystal : crystals) {
                if (SunSpiritAI.ICE_CRYSTAL.equals(crystal.getProjectileType())) return crystal;
            }
            return null;
        }

        /**
         * What freezing the floor at {@code tile} would be worth: a lit brazier darkened, and
         * more if it is the one with a lane marked, a marked lane cut short, or frost laid in
         * the Sun Spirit's way.
         */
        private int frostWorth(GridPos tile) {
            List<GridPos> patch = SunSpiritAI.frostPatch(a, boss, tile);
            SunSpiritAI.Plan plan = ai.plan(boss, a, ai.braziers(a));
            int worth = 0;
            if (SunSpiritAI.phase(boss) >= 2) {
                for (GridPos brazier : ai.braziers(a)) {
                    if (!SunSpiritAI.lit(a, brazier)) continue;
                    boolean touched = false;
                    for (GridPos frozen : patch) touched |= frozen.chebyshevDistanceTo(brazier) <= 1;
                    if (!touched) continue;
                    worth += 5;
                    for (SunSpiritAI.Lane lane : ai.lanes()) if (lane.brazier().equals(brazier)) worth += 5;
                }
                for (GridPos frozen : patch) {
                    if (plan.lanes().contains(frozen)) {
                        worth += 3;
                        break;
                    }
                }
            }
            Set<GridPos> inItsWay = SunSpiritAI.swept(boss, plan.drift());
            for (GridPos frozen : patch) {
                if (inItsWay.contains(frozen)) {
                    worth += 2;
                    break;
                }
            }
            return worth;
        }

        /** Whether the crystal at {@code at}, struck from {@code from}, would fly into the Sun Spirit as it stands. */
        private boolean shotLands(GridPos from, GridPos at) {
            return SunSpiritAI.shot(a, boss, at, from).lands();
        }

        /**
         * Strike the Ice Crystal from where the player stands, as the crystal's own handler
         * settles it: the shot lands or flies wide, and the floor freezes either way.
         */
        private boolean strike(CombatEntity ice) {
            struck++;
            GridPos at = ice.getGridPos();
            boolean hit = shotLands(player, at);
            List<GridPos> patch = SunSpiritAI.frostPatch(a, boss, at);
            if (hit) {
                int dealt = boss.takeDamageThroughImmunity(
                    SunSpiritAI.deflectDamage(ice.getAttackPower(), boss.getMaxHp()));
                SunSpiritAI.struck(boss, dealt);
                landed++;
                if (boss.isAlive()) freezes++;
                if (holdHp > 0) boss.restoreHp(holdHp);
            }
            for (GridPos tile : patch) lay(a, SunSpiritAI.FROZEN_FLOOR, tile, SunSpiritAI.FROST_TURNS);
            remove(ice);
            return hit;
        }

        /** The nearest tile in {@code near} that passes {@code touches}, or null. */
        private static GridPos beside(Map<GridPos, Integer> near, java.util.function.Predicate<GridPos> touches) {
            GridPos best = null;
            for (Map.Entry<GridPos, Integer> tile : near.entrySet()) {
                if (touches.test(tile.getKey()) && (best == null || tile.getValue() < near.get(best))) best = tile.getKey();
            }
            return best;
        }

        /** What ending this turn on a tile would leave to be done with the Ice Crystal on the next. */
        private record Outlook(boolean shot, int frost) {}

        /**
         * Where the crystal and the Sun Spirit will each be once its turn is over, if the
         * player ends theirs on {@code tile}, and whether a strike from somewhere in reach
         * of that tile would then land. Both are things a player can read off the board:
         * a seeker comes two tiles along the shortest way, and its own move is painted.
         */
        private Outlook outlook(CombatEntity ice, GridPos tile) {
            GridPos was = player;
            player = tile;
            stand(a, tile);
            try {
                GridPos crystalAt = ice.getGridPos();
                if (brainOf(ice).decideAction(ice, a, tile) instanceof EnemyAction.ProjectileMove flight) {
                    if (flight.impacts()) return new Outlook(false, 0);
                    if (!flight.path().isEmpty()) crystalAt = flight.path().get(flight.path().size() - 1);
                }
                GridPos anchor = ai.plan(boss, a, ai.braziers(a)).drift().end(boss.getGridPos());
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        GridPos from = new GridPos(crystalAt.x() + dx, crystalAt.z() + dz);
                        if ((dx == 0 && dz == 0) || !a.isInBounds(from) || from.manhattanDistance(tile) > MOVE) continue;
                        GridTile ground = a.getTile(from);
                        if (ground == null || !ground.isWalkable() || ground.getType().isFlames()) continue;
                        if (SunSpiritAI.shot(a, anchor, 2, 2, crystalAt, from).lands()) {
                            return new Outlook(true, frostWorth(crystalAt));
                        }
                    }
                }
                return new Outlook(false, 0);
            } finally {
                player = was;
                stand(a, was);
            }
        }

        void playerTurn(Skill skill) {
            stand(a, player);
            if (burning(player)) hits[litBy.getOrDefault(player, Source.TRAIL).ordinal()]++;
            // Three tiles of walking and one blow a turn: at a crystal, at a minion, or at it.
            int left = MOVE;
            boolean blowSpent = false;

            // 1. The Ice Crystal, if it is in reach.
            CombatEntity ice = iceCrystal();
            if (ice != null) {
                GridPos target = ice.getGridPos();
                Map<GridPos, Integer> near = reach(player, left, false);
                GridPos snapFrom = beside(near, tile -> tile.chebyshevDistanceTo(target) <= 1);
                GridPos aimedFrom = beside(near, tile -> tile.chebyshevDistanceTo(target) <= 1 && shotLands(tile, target));
                GridPos from;
                if (skill == Skill.SNAPS) {
                    from = snapFrom;
                } else if (aimedFrom != null) {
                    // A shot that lines up is taken, by anyone. Waiting on a better patch of
                    // frost was tried here and cost more crystals than the frost was worth.
                    from = aimedFrom;
                } else {
                    // No shot from here this turn. Let it come on while there is patience left.
                    from = waited < PATIENCE ? null : snapFrom;
                }
                if (from != null) {
                    left -= near.get(from);
                    player = from;
                    stand(a, player);
                    strike(ice);
                    ice = null;
                    waited = 0;
                    blowSpent = true;
                } else if (snapFrom != null) {
                    waited++;
                }
            }

            // 2. A minion that can be walked up to is cut down before anything else is done
            //    with the blow: it is the one thing in the room that follows.
            if (!blowSpent) {
                for (CombatEntity minion : new ArrayList<>(minions)) {
                    GridPos at = minion.getGridPos();
                    Map<GridPos, Integer> near = reach(player, left, false);
                    GridPos from = beside(near, tile -> tile.chebyshevDistanceTo(at) <= 1);
                    if (from == null) continue;
                    left -= near.get(from);
                    player = from;
                    stand(a, player);
                    remove(minion);
                    minionsCutDown++;
                    blowSpent = true;
                    break;
                }
            }

            // 3. While it is frozen, get beside it, if there is a way back out before it thaws,
            //    and hit it if the turn's blow is still to spend.
            if (SunSpiritAI.isFrozen(boss)) {
                Map<GridPos, Integer> near = reach(player, left, false);
                GridPos from = null;
                for (Map.Entry<GridPos, Integer> tile : near.entrySet()) {
                    if (boss.minChebyshevDistanceTo(tile.getKey()) > 1) continue;
                    if (from != null && near.get(from) <= tile.getValue()) continue;
                    GridPos back = player;
                    player = tile.getKey();
                    boolean wayOut = false;
                    for (GridPos end : reach(player, left - tile.getValue(), false).keySet()) {
                        if (danger(end) == 0) {
                            wayOut = true;
                            break;
                        }
                    }
                    player = back;
                    if (wayOut) from = tile.getKey();
                }
                if (from != null) {
                    left -= near.get(from);
                    player = from;
                }
                stand(a, player);
                if (boss.minChebyshevDistanceTo(player) <= 1) {
                    besideFrozen++;
                    if (!blowSpent) {
                        blowsLanded++;
                        blowSpent = true;
                    }
                }
            }

            // 4. And then somewhere to stand: clear of it, unless it is frozen and worth
            //    closing on. With an Ice Crystal coming, someone who aims stands where the
            //    next turn's shot will line up, and someone who does not simply meets it.
            CombatEntity coming = ice;
            boolean closing = SunSpiritAI.isFrozen(boss);
            player = settle(player, left, tile -> {
                int toIt = boss.minChebyshevDistanceTo(tile);
                int liked = (closing ? -3 * toIt : 2 * Math.min(toIt, 6)) + Math.min(wallGap(tile), 4);
                if (coming == null) return liked;
                if (skill == Skill.SNAPS) return liked - tile.chebyshevDistanceTo(coming.getGridPos());
                Outlook look = outlook(coming, tile);
                // Frost only ever breaks a tie between two tiles liked as well as each other.
                // Weighted any heavier it drew the player toward the walls and the braziers
                // for it, and cost more hits than the frost saved.
                if (look.shot()) liked = 2 * (liked + 30) + (skill == Skill.FENCES && look.frost() > 0 ? 1 : 0);
                else liked = 2 * liked;
                return liked;
            });
            stand(a, player);
        }

        int hitsInAll() {
            int all = 0;
            for (int taken : hits) all += taken;
            return all;
        }

        void play(Skill skill, int rounds) {
            for (int round = 0; round < rounds; round++) {
                playerTurn(skill);
                enemyPhase();
            }
        }
    }

    /** Where the player comes in, by seed. The last third of the fight plays the same from any one of them. */
    private static final List<GridPos> STARTS = List.of(
        new GridPos(9, 15), new GridPos(3, 9), new GridPos(15, 9), new GridPos(9, 3),
        new GridPos(13, 14), new GridPos(4, 13), new GridPos(14, 4));

    private static Fight inTheRoom(long seed, int hp) {
        GridPos player = STARTS.get((int) (seed % STARTS.size()));
        GridArena a = room(player);
        CombatEntity self = hurtTo(boss(a, CENTRE.x(), CENTRE.z()), hp);
        return new Fight(a, self, inRoom(seed), player, hp);
    }

    @Test
    @DisplayName("a player who ends each turn off what it showed, and off the fire, is never burned by it in any phase")
    void thePromiseHolds() {
        int offThePaintTurns = 0;
        int laneTilesLit = 0;
        for (int hp : new int[]{HP, 560, 280}) {
            for (int seed = 0; seed < 12; seed++) {
                Fight fight = inTheRoom(seed, hp);
                Random dice = new Random(seed * 31L + hp);
                for (int turn = 0; turn < 60; turn++) {
                    // Someone who wanders, sometimes carelessly, and now and then strikes an
                    // Ice Crystal if one happens to be beside them, from wherever they are.
                    // The fight itself checks the promise on every turn they happened to
                    // keep their side of it.
                    List<GridPos> near = new ArrayList<>(fight.reach(fight.player, Fight.MOVE, true).keySet());
                    GridPos to = near.get(dice.nextInt(near.size()));
                    if (dice.nextInt(4) > 0) {
                        for (int tries = 0; tries < 12; tries++) {
                            GridPos tile = near.get(dice.nextInt(near.size()));
                            stand(fight.a, tile);
                            fight.player = tile;
                            if (!fight.ai.computeThreatTiles(fight.boss, fight.a).contains(tile) && !fight.burning(tile)) {
                                to = tile;
                                break;
                            }
                        }
                    }
                    fight.player = to;
                    stand(fight.a, to);
                    CombatEntity ice = fight.iceCrystal();
                    if (ice != null && ice.getGridPos().chebyshevDistanceTo(to) <= 1 && dice.nextBoolean()) fight.strike(ice);
                    if (!fight.ai.computeThreatTiles(fight.boss, fight.a).contains(to) && !fight.burning(to)) offThePaintTurns++;
                    fight.enemyPhase();
                }
                if (hp < HP) laneTilesLit += fight.laneTilesLit;
            }
        }
        assertTrue(offThePaintTurns > 1000, "fixture: the promise was hardly put to the test (" + offThePaintTurns + ")");
        assertTrue(laneTilesLit > 1000, "fixture: hardly a lane was lit (" + laneTilesLit + ")");
    }

    @Test
    @DisplayName("NUMBERS: what two hundred turns of each phase cost each kind of player, on the real room")
    void whatTheFightCosts() {
        int seeds = 5;
        int rounds = 200;
        String[] phases = {"", "phase 1 (above two thirds)", "phase 2 (lanes)", "phase 3 (the room burns)"};
        int[] health = {0, HP, 560, 280};
        for (int phase = 1; phase <= 3; phase++) {
            for (Skill skill : Skill.values()) {
                int[] hits = new int[Source.values().length];
                int struck = 0, landed = 0, freezes = 0, beside = 0, blows = 0, cornered = 0, inReach = 0, unsafe = 0;
                int aloftSum = 0, aloftMost = 0, dark = 0, rings = 0, fizzled = 0, cutDown = 0, laneTiles = 0;
                int cameBack = 0, stuck = 0, stuckOnFrost = 0, stuckLongest = 0, heldItsCrystal = 0;
                for (int seed = 0; seed < seeds; seed++) {
                    Fight fight = inTheRoom(seed, health[phase]);
                    fight.play(skill, rounds);
                    assertEquals(phase, SunSpiritAI.phase(fight.boss), "the run left its phase");
                    for (int i = 0; i < hits.length; i++) hits[i] += fight.hits[i];
                    struck += fight.struck;
                    landed += fight.landed;
                    freezes += fight.freezes;
                    beside += fight.besideFrozen;
                    blows += fight.blowsLanded;
                    cornered += fight.cornered;
                    inReach += fight.tilesInReach;
                    unsafe += fight.tilesUnsafe;
                    aloftSum += fight.aloftSum;
                    aloftMost = Math.max(aloftMost, fight.aloftMost);
                    dark += fight.darkBrazierTurns;
                    rings += fight.ai.rings();
                    fizzled += fight.fizzled;
                    cutDown += fight.minionsCutDown;
                    laneTiles += fight.laneTilesLit;
                    cameBack += fight.cameBack;
                    stuck += fight.stuck;
                    stuckOnFrost += fight.stuckOnFrost;
                    stuckLongest = Math.max(stuckLongest, fight.stuckLongest);
                    heldItsCrystal += fight.heldItsCrystal;
                }
                StringBuilder line = new StringBuilder("NUMBERS " + phases[phase] + ", player who "
                    + skill.name().toLowerCase() + ", per " + rounds + " turns:");
                int total = 0;
                for (Source source : Source.values()) {
                    total += hits[source.ordinal()];
                    line.append(' ').append(source.name().toLowerCase()).append(' ').append(per(hits[source.ordinal()], seeds));
                }
                line.append(" | hits in all ").append(per(total, seeds))
                    .append(" | crystals struck ").append(per(struck, seeds))
                    .append(" landed ").append(per(landed, seeds))
                    .append(" (").append(per(landed * 100, Math.max(1, struck))).append("%)")
                    .append(" | freezes ").append(per(freezes, seeds))
                    .append(" | turns beside it frozen ").append(per(beside, seeds))
                    .append(" | free blows on it frozen ").append(per(blows, seeds))
                    .append(" | turns with no safe tile ").append(per(cornered, seeds))
                    .append(" | tiles in reach that were unsafe ").append(per(unsafe * 100, Math.max(1, inReach))).append('%')
                    .append(" | lane tiles lit ").append(per(laneTiles, seeds))
                    .append(" | brazier-turns dark ").append(per(dark, seeds))
                    .append(" | fire crystals aloft avg ").append(per(aloftSum, seeds * rounds)).append(" most ").append(aloftMost)
                    .append(" | fire crystals broken or burnt out ").append(per(fizzled, seeds))
                    .append(" | minions cut down ").append(per(cutDown, seeds))
                    .append(" | free turns it ended where it began ").append(per(cameBack, seeds))
                    .append(" | free turns it made no move at all ").append(per(stuck, seeds))
                    .append(", with frost against it ").append(per(stuckOnFrost, seeds))
                    .append(", longest run ").append(stuckLongest)
                    .append(" | free turns it threw no crystal of its own ").append(per(heldItsCrystal, seeds))
                    .append(" | rings alight at the end ").append(per(rings, seeds));
                System.out.println(line);

                // Nothing here is pinned but what has to be true of any run.
                assertTrue(struck > 0, "no crystal was ever struck in " + phases[phase]);
                if (skill != Skill.SNAPS) assertTrue(landed > 0, "no shot ever landed in " + phases[phase]);
            }
        }
    }

    @Test
    @DisplayName("NUMBERS: a whole fight won by its own cold alone, from full health to none")
    void aWholeFight() {
        int seeds = 21;
        int limit = 400;
        for (Skill skill : Skill.values()) {
            int[] turnsIn = new int[4];
            int[] hitsIn = new int[4];
            int[] bySource = new int[Source.values().length];
            int[] landedIn = new int[4];
            int[] struckIn = new int[4];
            int longest = 0, shortest = Integer.MAX_VALUE, blows = 0, won = 0, turnsOfWins = 0, hitsOfWins = 0;
            for (int seed = 0; seed < seeds; seed++) {
                GridPos player = STARTS.get(seed % STARTS.size());
                GridArena a = room(player);
                // Nothing puts its health back: every shot that lands takes its tenth, and no
                // blow landed on it while it is frozen is counted against it at all.
                Fight fight = new Fight(a, boss(a, CENTRE.x(), CENTRE.z()), inRoom(seed), player, 0);
                int turns = 0;
                while (fight.boss.isAlive() && turns < limit) {
                    int phase = SunSpiritAI.phase(fight.boss);
                    int hitsBefore = fight.hitsInAll();
                    int landedBefore = fight.landed;
                    int struckBefore = fight.struck;
                    fight.playerTurn(skill);
                    if (fight.boss.isAlive()) fight.enemyPhase();
                    turns++;
                    turnsIn[phase]++;
                    hitsIn[phase] += fight.hitsInAll() - hitsBefore;
                    landedIn[phase] += fight.landed - landedBefore;
                    struckIn[phase] += fight.struck - struckBefore;
                }
                if (!fight.boss.isAlive()) {
                    won++;
                    turnsOfWins += turns;
                    hitsOfWins += fight.hitsInAll();
                    longest = Math.max(longest, turns);
                    shortest = Math.min(shortest, turns);
                }
                blows += fight.blowsLanded;
                for (int i = 0; i < bySource.length; i++) bySource[i] += fight.hits[i];
            }
            int turns = turnsIn[1] + turnsIn[2] + turnsIn[3];
            int hits = hitsIn[1] + hitsIn[2] + hitsIn[3];
            StringBuilder sources = new StringBuilder();
            for (Source source : Source.values()) {
                sources.append(' ').append(source.name().toLowerCase()).append(' ').append(per(bySource[source.ordinal()], seeds));
            }
            System.out.println("NUMBERS a whole fight by its own cold alone, player who " + skill.name().toLowerCase() + ", per fight:"
                + " won " + won + " of " + seeds + " inside " + limit + " turns"
                + " | turns in a win " + (won == 0 ? "none" : per(turnsOfWins, won) + " (shortest " + shortest + ", longest " + longest + ")")
                + " | hits taken in a win " + (won == 0 ? "none" : per(hitsOfWins, won))
                + " | turns in all " + per(turns, seeds) + ": phase 1 " + per(turnsIn[1], seeds) + ", phase 2 " + per(turnsIn[2], seeds) + ", phase 3 " + per(turnsIn[3], seeds)
                + " | hits in all " + per(hits, seeds) + ": phase 1 " + per(hitsIn[1], seeds) + ", phase 2 " + per(hitsIn[2], seeds) + ", phase 3 " + per(hitsIn[3], seeds)
                + " | hits per 100 turns: phase 1 " + per(hitsIn[1] * 100, Math.max(1, turnsIn[1])) + ", phase 2 " + per(hitsIn[2] * 100, Math.max(1, turnsIn[2])) + ", phase 3 " + per(hitsIn[3] * 100, Math.max(1, turnsIn[3]))
                + " | hits by source:" + sources
                + " | struck " + per(struckIn[1] + struckIn[2] + struckIn[3], seeds) + " landed " + per(landedIn[1] + landedIn[2] + landedIn[3], seeds)
                + " | turns it stood frozen with a blow free to land " + per(blows, seeds));
            if (skill != Skill.SNAPS) assertEquals(seeds, won, "a player who " + skill.name().toLowerCase() + " did not win inside " + limit + " turns");
        }
    }

    private static String per(int total, int runs) {
        return String.format(java.util.Locale.ROOT, "%.1f", total / (double) runs);
    }
}
