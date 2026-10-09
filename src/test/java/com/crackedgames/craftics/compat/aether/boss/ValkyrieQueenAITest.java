package com.crackedgames.craftics.compat.aether.boss;

import com.crackedgames.craftics.api.ProjectileImpactHandler;
import com.crackedgames.craftics.api.registry.ProjectileImpactRegistry;
import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.CombatManager;
import com.crackedgames.craftics.combat.MobTrait;
import com.crackedgames.craftics.combat.MobTraits;
import com.crackedgames.craftics.combat.ai.AIRegistry;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.combat.ai.SeekingProjectileAI;
import com.crackedgames.craftics.combat.ai.boss.BossWarning;
import com.crackedgames.craftics.compat.aether.AetherMobs;
import com.crackedgames.craftics.compat.aether.ai.ValkyrieAI;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the Valkyrie Queen decides, on a bare grid.
 *
 * <p>Nothing here can spawn her, take a real medal out of a real inventory, or resolve an
 * action: that needs the game and the Aether, and belongs to the in-game checklist. What is
 * pinned is the part that is hers alone - when the gate opens, what she will and will not do
 * behind it, what each third of her health adds to the duel, what she puts together into
 * one turn, and that everything of hers that hurts was on the floor a turn before it landed.
 * The party's medals and what she says are the two places she touches the live world, so
 * the fixture stands in for exactly those.
 *
 * <p>Where a test runs more than one of her turns, the fixture does the engine's bookkeeping
 * between them. For a turn or two that is setting down what she sent or threw and moving
 * her where she walked. For a whole fight it is {@code Fight}: every action she asks for
 * carried out the way the engine resolves it, her crystals flown on the engine's own
 * brains and landed through her own handler, and each turn of hers held to her word.
 */
class ValkyrieQueenAITest {

    // ── Fixture ──

    private static final int SIZE = 12;

    /** The Queen with a party whose purse the test fills, and whose ears the test has. */
    private static final class Queen extends ValkyrieQueenAI {
        int purse;
        final List<String> said = new ArrayList<>();

        Queen(int purse) {
            this.purse = purse;
        }

        @Override
        protected int medalsHeld(CombatEntity self) {
            return purse;
        }

        @Override
        protected void takeMedals(CombatEntity self, int count) {
            purse -= count;
        }

        @Override
        protected void announce(CombatEntity self, String message) {
            said.add(message);
        }

        long times(String fragment) {
            return said.stream().filter(line -> line.contains(fragment)).count();
        }
    }

    private static GridArena arena(GridPos player) {
        GridTile[][] tiles = new GridTile[SIZE][SIZE];
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                tiles[x][z] = new GridTile(TileType.NORMAL, null);
            }
        }
        GridArena a = new GridArena(SIZE, SIZE, tiles, BlockPos.ORIGIN, 1, player);
        standPlayer(a, player);
        return a;
    }

    private static void standPlayer(GridArena a, GridPos player) {
        a.setPlayerGridPos(player);
        a.setAllPlayerGridPositions(List.of(player));
    }

    private static int nextId = 500;

    /** Her stats in the Silver Dungeon: 100 health, 11 attack, 3 defense, speed 3. */
    private static CombatEntity queen(GridArena a, int x, int z) {
        CombatEntity q = new CombatEntity(nextId++, ValkyrieQueenAI.QUEEN, new GridPos(x, z),
            100, 11, 3, 1, 1, 3);
        q.setBoss(true);
        q.setAiOverrideKey(ValkyrieQueenAI.BOSS_KEY);
        assertTrue(a.placeEntity(q), "fixture: the Queen did not fit");
        return q;
    }

    /** Do what the engine does with a summon: put the valkyries down and tell her about each. */
    private static List<CombatEntity> arrive(ValkyrieQueenAI ai, GridArena a, EnemyAction.SummonMinions order) {
        List<CombatEntity> arrived = new ArrayList<>();
        for (GridPos tile : order.positions()) {
            CombatEntity valkyrie = new CombatEntity(nextId++, order.entityTypeId(), tile,
                order.hp(), order.atk(), order.def(), 1, 1, 3);
            assertTrue(a.placeEntity(valkyrie), "she sent a valkyrie to a tile that was taken: " + tile);
            ai.registerSpawnedMinion(valkyrie.getEntityId());
            arrived.add(valkyrie);
        }
        return arrived;
    }

    private static void beat(GridArena a, CombatEntity valkyrie) {
        valkyrie.takeDamage(9999);
        a.removeEntity(valkyrie);
    }

    /** A crystal as the engine leaves it the moment it is launched: flying straight. */
    private static CombatEntity crystal(GridArena a, CombatEntity owner, int x, int z) {
        CombatEntity c = new CombatEntity(nextId++, ValkyrieQueenAI.CRYSTAL_BODY, new GridPos(x, z),
            ValkyrieQueenAI.CRYSTAL_HP, 5, 0, 1);
        c.setProjectile(true);
        c.setProjectileType(ValkyrieQueenAI.THUNDER_CRYSTAL);
        c.setProjectileOwnerId(owner.getEntityId());
        c.setAiOverrideKey("projectile");
        assertTrue(a.placeEntity(c), "fixture: the crystal did not fit");
        return c;
    }

    /**
     * Do what the engine does with a throw: put the crystal down and tell her about it. No
     * crystal gets onto the grid here without the head start every one of them is owed.
     */
    private static CombatEntity launch(ValkyrieQueenAI ai, GridArena a, CombatEntity owner,
                                       EnemyAction.SpawnProjectile shot) {
        assertEquals(ValkyrieQueenAI.THUNDER_CRYSTAL, shot.projectileType());
        assertEquals(1, shot.positions().size(), "one crystal to a throw");
        GridPos tile = shot.positions().get(0);
        assertTrue(ValkyrieQueenAI.open(a, tile), "she threw a crystal onto a tile that was taken: " + tile);
        CombatEntity c = crystal(a, owner, tile.x(), tile.z());
        ai.registerSpawnedProjectile(c.getEntityId());
        assertHeadStart(a, c, "as it leaves her hand");
        return c;
    }

    /** The engine's own homing brain, the one a crystal flies on once she has been told about it. */
    private static final SeekingProjectileAI HOMING = new SeekingProjectileAI();

    /**
     * A crystal makes its first move the turn it is thrown, before anyone can strike it, so
     * that move must not be an arrival. Asked of the engine's own homing brain about everyone
     * the crystal could go for. Its turn comes after hers in the same round, so the moment
     * that counts is once she has finished moving.
     */
    private static void assertHeadStart(GridArena a, CombatEntity crystal, String when) {
        assertEquals("seeking_projectile", crystal.getAiKey(), when + ": it was never given the homing brain");
        for (GridPos someone : partySide(a)) {
            EnemyAction first = HOMING.decideAction(crystal, a, someone);
            assertFalse(first instanceof EnemyAction.ProjectileMove flight && flight.impacts(),
                when + ": thrown from " + crystal.getGridPos() + " it lands on " + someone
                    + " before anyone can answer it");
        }
    }

    /** Everyone a crystal could go for: the party, and whatever fights beside it. */
    private static List<GridPos> partySide(GridArena a) {
        List<GridPos> all = new ArrayList<>(a.getAllPlayerGridPositions());
        for (CombatEntity e : a.getOccupants().values()) {
            if (e.isAlly() && e.isAlive()) all.add(e.getGridPos());
        }
        return all;
    }

    /** A crystal that has landed, or been knocked into a wall: gone from the grid. */
    private static void burst(GridArena a, CombatEntity crystal) {
        crystal.takeDamage(9999);
        a.removeEntity(crystal);
    }

    /** Do what the engine does with a walk: take her along it, one free tile at a time. */
    private static void walk(GridArena a, CombatEntity self, List<GridPos> path) {
        for (GridPos step : path) {
            assertFalse(a.isOccupied(step), "she meant to walk through " + step + ", which is taken");
            assertTrue(a.moveEntity(self, step));
        }
    }

    /**
     * Do what the engine does with the parts of a turn that put things on the grid: set down
     * what she sent or threw, and take her where she is going. In the order she gave them,
     * which is the order the engine runs a bundle in. Returns the crystal she threw, or null.
     */
    private static CombatEntity carryOut(ValkyrieQueenAI ai, GridArena a, CombatEntity self, EnemyAction action) {
        CombatEntity thrown = null;
        for (EnemyAction part : EnemyAction.flatten(action)) {
            if (part instanceof EnemyAction.SummonMinions order) {
                arrive(ai, a, order);
            } else if (part instanceof EnemyAction.SpawnProjectile shot) {
                thrown = launch(ai, a, self, shot);
            } else if (part instanceof EnemyAction.Move move) {
                walk(a, self, move.path());
            } else if (part instanceof EnemyAction.MoveAndAttack strike) {
                walk(a, self, strike.path());
            } else if (part instanceof EnemyAction.Teleport blink) {
                // A blink is one step that can start anywhere, and lands on a free tile like any other.
                walk(a, self, List.of(blink.target()));
            } else if (part instanceof EnemyAction.CeilingDrop drop) {
                // Back onto the grid from off it, the way the engine sets anything down.
                assertTrue(ValkyrieQueenAI.open(a, drop.landingPos()), "she came down on a taken tile");
                self.setOnCeiling(false);
                self.setGridPos(drop.landingPos());
                assertTrue(a.placeEntity(self));
            }
        }
        return thrown;
    }

    /** The crystal in a decision of hers, thrown alone or with her walk. Null when there is none. */
    private static EnemyAction.SpawnProjectile thrown(EnemyAction action) {
        if (action instanceof EnemyAction.SpawnProjectile shot) return shot;
        return action instanceof EnemyAction.CompositeAction ? bundle(action).shot() : null;
    }

    /** Her valkyries still standing. */
    private static List<CombatEntity> valkyries(GridArena a) {
        return a.getOccupants().values().stream()
            .filter(e -> e.isAlive() && AetherMobs.VALKYRIE.equals(e.getEntityTypeId())).toList();
    }

    /** Every crystal on the grid, hers or knocked away. */
    private static List<CombatEntity> crystals(GridArena a) {
        return a.getOccupants().values().stream().filter(e -> e.isAlive() && e.isProjectile()).toList();
    }

    /** A Queen whose gate opened on her first turn, so the next decision is the duel's first. */
    private static Queen dueling(CombatEntity self, GridArena a, GridPos player) {
        Queen ai = new Queen(ValkyrieQueenAI.MEDALS_REQUIRED);
        assertInstanceOf(EnemyAction.Idle.class, ai.decideAction(self, a, player));
        assertFalse(ai.isGuardUp(), "fixture: ten medals should have opened the gate");
        return ai;
    }

    /**
     * A guarded Queen with a crystal in hand and nothing of hers on the floor: her first two
     * valkyries fell as they arrived, so her next decision is where to stand and what to throw.
     */
    private static Queen guardedAndAlone(CombatEntity self, GridArena a, GridPos player) {
        Queen ai = new Queen(0);
        for (CombatEntity valkyrie : arrive(ai, a,
                assertInstanceOf(EnemyAction.SummonMinions.class, ai.decideAction(self, a, player)))) {
            beat(a, valkyrie);
        }
        return ai;
    }

    /** A bundle of hers taken apart: the crystal, and the one thing she does on foot beside it. */
    private record Bundle(EnemyAction.SpawnProjectile shot, EnemyAction onFoot) {}

    private static Bundle bundle(EnemyAction action) {
        EnemyAction.CompositeAction both = assertInstanceOf(EnemyAction.CompositeAction.class, action);
        assertEquals(2, both.actions().size(), "a crystal and one thing on foot, no more: " + action);
        return new Bundle(assertInstanceOf(EnemyAction.SpawnProjectile.class, both.actions().get(0)),
            both.actions().get(1));
    }

    /**
     * Hold one decision of hers against every shape she is allowed.
     *
     * <p>Behind her guard she may stand, walk, blink, send valkyries or throw, and a crystal
     * rides with a step away and nothing else. She marks nothing there.
     *
     * <p>In the duel, alone, she may stand, walk, swing, shove, blink, throw, call her guard,
     * run a lane she marked, or let last turn's lightning fall. A bundle is, in this order
     * and each at most once: last turn's lightning, the sweep of a lane three wide, a
     * crystal, and the one thing that is her move. The engine runs one action that takes
     * time per bundle, so there is one, and with a crystal it is a walk. A swing in a bundle
     * is asked for as a shove of no tiles, which the engine takes through the ordinary melee
     * path, and never as a plain attack, which it would not. A blink is the whole of its
     * turn. A call to her guard is the whole of her move, with nothing beside it but what
     * fell due. The one other bundle is the dive coming down: she lands, the square is hit,
     * and whoever she dived at is thrown.
     */
    private static void assertHerShape(Queen ai, EnemyAction action, String when) {
        List<EnemyAction> parts = EnemyAction.flatten(action);
        for (EnemyAction part : parts) {
            assertFalse(part instanceof EnemyAction.TeleportAndAttack,
                when + ": a blink that strikes, which nobody can read");
        }
        if (ai.isGuardUp()) {
            if (action instanceof EnemyAction.CompositeAction) {
                Bundle bundle = bundle(action);
                assertEquals(ValkyrieQueenAI.THUNDER_CRYSTAL, bundle.shot().projectileType(), when);
                assertInstanceOf(EnemyAction.Move.class, bundle.onFoot(),
                    when + ": a crystal bundled with something else");
            } else {
                assertTrue(action instanceof EnemyAction.Idle || action instanceof EnemyAction.Move
                    || action instanceof EnemyAction.SpawnProjectile || action instanceof EnemyAction.Teleport
                    || action instanceof EnemyAction.SummonMinions, when + ": a guarded Queen chose " + action);
            }
            assertNull(ai.getPendingWarning(), when + ": she marked the floor from behind her guard");
            return;
        }

        if (!(action instanceof EnemyAction.CompositeAction)) {
            assertTrue(action instanceof EnemyAction.Idle || action instanceof EnemyAction.Move
                || action instanceof EnemyAction.Attack || action instanceof EnemyAction.AttackWithKnockback
                || action instanceof EnemyAction.MoveAndAttack || action instanceof EnemyAction.Teleport
                || action instanceof EnemyAction.SummonMinions || action instanceof EnemyAction.SpawnProjectile
                || action instanceof EnemyAction.TileAreaAttack, when + ": a dueling Queen chose " + action);
            return;
        }
        assertTrue(parts.size() >= 2, when + ": a bundle of one: " + action);
        assertTrue(parts.stream().filter(EnemyAction::drivesTurn).count() <= 1,
            when + ": two actions that take time in one bundle: " + action);
        if (parts.get(0) instanceof EnemyAction.CeilingDrop) {
            assertInstanceOf(EnemyAction.TileAreaAttack.class, parts.get(1), when + ": a landing with no hit: " + action);
            if (parts.size() > 2) assertInstanceOf(EnemyAction.ForcedMovement.class, parts.get(2), when);
            assertTrue(parts.size() <= 3, when + ": something else rode with the dive: " + action);
            return;
        }
        // What falls due comes first: last turn's lightning, then the sweep of a lane three
        // wide, which is the lesser hit of the two.
        int at = 0;
        int due = 0;
        while (at < parts.size() && parts.get(at) instanceof EnemyAction.TileAreaAttack) {
            at++;
            due++;
        }
        assertTrue(due <= 2, when + ": three area hits in one turn: " + action);
        if (due == 2) {
            assertTrue(((EnemyAction.TileAreaAttack) parts.get(0)).damage()
                > ((EnemyAction.TileAreaAttack) parts.get(1)).damage(), when + ": two bolts, or two sweeps: " + action);
        }
        boolean throwing = at < parts.size() && parts.get(at) instanceof EnemyAction.SpawnProjectile;
        if (throwing) at++;
        if (at < parts.size()) {
            EnemyAction onFoot = parts.get(at++);
            // A strike, or the call to her guard, is the whole of her move: no crystal with it.
            boolean alone = onFoot instanceof EnemyAction.MoveAndAttack
                || onFoot instanceof EnemyAction.AttackWithKnockback || onFoot instanceof EnemyAction.SummonMinions;
            assertTrue(onFoot instanceof EnemyAction.Move || (!throwing && alone),
                when + ": " + (throwing ? "a crystal bundled with " : "what fell due bundled with ") + onFoot);
        }
        assertEquals(parts.size(), at, when + ": something else rode along: " + action);
    }

    /** A decision's shape in a word or two, for saying which of them a sweep reached. */
    private static String shapeOf(Queen ai, EnemyAction action) {
        if (action instanceof EnemyAction.CompositeAction) {
            return "crystal + " + bundle(action).onFoot().getClass().getSimpleName();
        }
        return action.getClass().getSimpleName();
    }

    /** One tile of wall, so that a Queen who starts in the corner beside it can be cornered there. */
    private static final GridPos PILLAR = new GridPos(1, 0);

    private static GridArena pillared(GridPos player) {
        GridArena a = arena(player);
        a.setTile(PILLAR, new GridTile(TileType.OBSTACLE, null));
        return a;
    }

    // ── The counting rule ──

    @Test
    @DisplayName("a medal counts for one and a beaten valkyrie for two, and the tribute stops at ten")
    void tributeRule() {
        assertEquals(2, ValkyrieQueenAI.VALKYRIE_WORTH);
        assertEquals(0, ValkyrieQueenAI.tribute(0, 0, 10));
        assertEquals(3, ValkyrieQueenAI.tribute(3, 0, 10));
        assertEquals(2, ValkyrieQueenAI.tribute(0, 1, 10));
        assertEquals(8, ValkyrieQueenAI.tribute(4, 2, 10));
        assertEquals(10, ValkyrieQueenAI.tribute(12, 0, 10));
        assertEquals(10, ValkyrieQueenAI.tribute(6, 9, 10));
        assertEquals(10, ValkyrieQueenAI.tribute(9, 1, 10), "the last valkyrie is worth more than was owed");
        assertEquals(4, ValkyrieQueenAI.tribute(-3, 2, 10), "a negative count is nothing, not a debt");

        assertTrue(ValkyrieQueenAI.gateOpen(10, 0, 10));
        assertTrue(ValkyrieQueenAI.gateOpen(0, 5, 10), "five valkyries and not one medal");
        assertTrue(ValkyrieQueenAI.gateOpen(4, 3, 10));
        assertFalse(ValkyrieQueenAI.gateOpen(0, 4, 10));
        assertFalse(ValkyrieQueenAI.gateOpen(4, 2, 10));
        assertFalse(ValkyrieQueenAI.gateOpen(1, 4, 10), "nine is not ten");
        assertFalse(ValkyrieQueenAI.gateOpen(0, 0, 10));
    }

    @Test
    @DisplayName("she takes every medal offered, up to what she is still owed")
    void medalsAcceptedRule() {
        assertEquals(3, ValkyrieQueenAI.medalsAccepted(3, 0, 10));
        assertEquals(10, ValkyrieQueenAI.medalsAccepted(14, 0, 10));
        assertEquals(2, ValkyrieQueenAI.medalsAccepted(5, 8, 10));
        assertEquals(0, ValkyrieQueenAI.medalsAccepted(5, 10, 10));
        assertEquals(0, ValkyrieQueenAI.medalsAccepted(0, 4, 10));
    }

    @Test
    @DisplayName("never more than two valkyries, and never more than the tribute still owed takes")
    void escortRule() {
        assertEquals(2, ValkyrieQueenAI.escortToSend(0, 10));
        assertEquals(1, ValkyrieQueenAI.escortToSend(1, 10));
        assertEquals(0, ValkyrieQueenAI.escortToSend(2, 10));

        // What is owed is counted in medals and a valkyrie is worth two of them, so one covers
        // a tribute short by 1 or by 2, and it takes a second from 3.
        assertEquals(1, ValkyrieQueenAI.escortToSend(0, 1));
        assertEquals(0, ValkyrieQueenAI.escortToSend(1, 1), "the one on the floor is the tribute still owed");
        assertEquals(0, ValkyrieQueenAI.escortToSend(2, 1));
        assertEquals(1, ValkyrieQueenAI.escortToSend(0, 2));
        assertEquals(0, ValkyrieQueenAI.escortToSend(1, 2), "the one on the floor covers both");
        assertEquals(0, ValkyrieQueenAI.escortToSend(2, 2));
        assertEquals(2, ValkyrieQueenAI.escortToSend(0, 3));
        assertEquals(1, ValkyrieQueenAI.escortToSend(1, 3));
        assertEquals(0, ValkyrieQueenAI.escortToSend(2, 3));

        assertEquals(0, ValkyrieQueenAI.escortToSend(0, 0), "nothing owed, nobody sent");
        assertEquals(0, ValkyrieQueenAI.escortToSend(3, 10), "one too many on the floor is not an order for minus one");
    }

    @Test
    @DisplayName("her numbers are read off her own: at base they are the dungeon's valkyrie")
    void derivedNumbers() {
        assertEquals(20, ValkyrieQueenAI.escortHp(100));
        assertEquals(8, ValkyrieQueenAI.escortAttack(11));
        assertEquals(2, ValkyrieQueenAI.escortDefense(3));
        assertEquals(0, ValkyrieQueenAI.escortDefense(0));

        assertEquals(5, ValkyrieQueenAI.crystalDamage(11));
        assertEquals(2, ValkyrieQueenAI.crystalDamage(1), "a crystal always stings");

        // Her honour guard: an escort's blade and an escort's health.
        assertEquals(20, ValkyrieQueenAI.guardHp(100));
        assertEquals(156, ValkyrieQueenAI.guardHp(780));
        assertEquals(ValkyrieQueenAI.escortAttack(11), ValkyrieQueenAI.guardAttack(11));
        assertEquals(ValkyrieQueenAI.escortAttack(12), ValkyrieQueenAI.guardAttack(12));

        // The outer tiles of a lane three wide take half her blade.
        assertEquals(5, ValkyrieQueenAI.sweepDamage(11));
        assertEquals(6, ValkyrieQueenAI.sweepDamage(12));
        assertEquals(1, ValkyrieQueenAI.sweepDamage(1));

        // The engine holds a boss's own hit to its ceiling, and nothing a boss sends. So at
        // her base attack and at that ceiling, neither a crystal nor a valkyrie at the end of
        // its longest lunge outdoes her blade.
        for (int attack : new int[]{11, 12}) {
            int guard = ValkyrieQueenAI.guardAttack(attack);
            assertTrue(ValkyrieAI.lungeDamage(guard, ValkyrieAI.LUNGE_MAX - 1) <= attack,
                "a valkyrie of hers hits harder than she does at " + attack);
            assertTrue(ValkyrieQueenAI.crystalDamage(attack) <= attack);
        }
    }

    @Test
    @DisplayName("a struck crystal takes at least a third of what it bursts on, a twentieth of a boss, and a tenth to ground her")
    void reflectedDamageRule() {
        assertEquals(20, ValkyrieQueenAI.reflectedDamage(5, 60, false));
        assertEquals(133, ValkyrieQueenAI.reflectedDamage(5, 400, false));
        assertEquals(10, ValkyrieQueenAI.reflectedDamage(5, 20, false),
            "on the dungeon's own valkyrie twice the crystal is already more than a third");

        assertEquals(10, ValkyrieQueenAI.reflectedDamage(5, 100, true), "twice the crystal, where a twentieth is five");
        assertEquals(20, ValkyrieQueenAI.reflectedDamage(5, 400, true), "a twentieth of a scaled boss");
        assertEquals(39, ValkyrieQueenAI.reflectedDamage(6, 780, true), "and of her at the depth she is met");
        assertEquals(33, ValkyrieQueenAI.reflectedDamage(5, 100, false), "the same hundred health without the crown");

        // The tenth is the prize for bringing her down, and for nothing else.
        assertEquals(10, ValkyrieQueenAI.groundingDamage(5, 100));
        assertEquals(40, ValkyrieQueenAI.groundingDamage(5, 400));
        assertEquals(78, ValkyrieQueenAI.groundingDamage(6, 780));
    }

    // ── The medal gate ──

    @Test
    @DisplayName("how many valkyries are left to beat: the tribute still owed, at two apiece, rounded up")
    void valkyriesLeftToBeat() {
        assertEquals(5, ValkyrieQueenAI.valkyriesToBeat(10));
        assertEquals(4, ValkyrieQueenAI.valkyriesToBeat(8));
        assertEquals(1, ValkyrieQueenAI.valkyriesToBeat(2));
        assertEquals(1, ValkyrieQueenAI.valkyriesToBeat(1), "one medal short is still one valkyrie");
        assertEquals(0, ValkyrieQueenAI.valkyriesToBeat(0));
        assertEquals(0, ValkyrieQueenAI.valkyriesToBeat(-3));
        assertEquals("Beat 3 more of her valkyries and she will fight.", ValkyrieQueenAI.stillToDo(5));
        assertEquals("Paid in full.", ValkyrieQueenAI.stillToDo(10));
    }

    /** A room whose west half is a floor one block up, with a column of stairs down to the hall. */
    private static GridArena twoFloors(GridPos player) {
        GridArena a = arena(player);
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < 6; x++) a.setTile(new GridPos(x, z), new GridTile(TileType.ELEVATED, null));
            a.setTile(new GridPos(6, z), new GridTile(TileType.STAIR, null));
        }
        return a;
    }

    @Test
    @DisplayName("she starts on the first raised floor, as near the middle of the room as it comes, and nowhere of her own in a flat room")
    void startsOnTheFloorAboveTheHall() {
        GridArena a = twoFloors(new GridPos(10, 6));
        GridPos tile = new Queen(0).spawnTile(a, 1, 1);
        assertEquals(new GridPos(5, 6), tile, "the middle is (6, 6), a stair: the raised floor beside it");
        assertEquals(TileType.ELEVATED, a.getTile(tile).getType());

        // A floor above that one is her dais, and not where she starts.
        GridTile dais = new GridTile(TileType.ELEVATED, null);
        dais.setRise(1);
        a.setTile(new GridPos(5, 6), dais);
        GridPos again = new Queen(0).spawnTile(a, 1, 1);
        assertFalse(new GridPos(5, 6).equals(again), "the dais itself");
        assertEquals(TileType.ELEVATED, a.getTile(again).getType());
        assertEquals(0, a.getTile(again).getRise());

        assertNull(new Queen(0).spawnTile(arena(new GridPos(10, 6)), 1, 1),
            "a flat room leaves her where the level put her");
    }

    @Test
    @DisplayName("from a raised floor, the valkyries she sends come down on the floor of the hall, the nearest of it to her")
    void valkyriesLandOnTheHallFloor() {
        GridPos player = new GridPos(10, 6);
        GridArena a = twoFloors(player);
        CombatEntity self = queen(a, 4, 6);
        Queen ai = new Queen(0);

        EnemyAction.SummonMinions order =
            assertInstanceOf(EnemyAction.SummonMinions.class, ai.decideAction(self, a, player));
        assertEquals(ValkyrieQueenAI.MAX_ESCORT, order.count());
        assertEquals(order.count(), new java.util.HashSet<>(order.positions()).size(), "two valkyries, two tiles");
        for (GridPos tile : order.positions()) {
            assertTrue(a.getTile(tile).isOnArenaFloor(), tile + " is a stair or a raised floor");
            assertTrue(ValkyrieQueenAI.open(a, tile), tile + " is not somewhere a valkyrie can stand");
            assertEquals(7, tile.x(), "the first of the hall floor past the stairs");
        }
    }

    @Test
    @DisplayName("first turn, no medals: guard up, her challenge as the hint, two valkyries sent")
    void opensWithHerGuardUp() {
        GridPos player = new GridPos(9, 5);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = new Queen(0);

        EnemyAction action = ai.decideAction(self, a, player);

        assertTrue(self.isDamageImmune());
        assertTrue(ai.isGuardUp());
        assertTrue(self.getDamageImmuneHint().contains(ValkyrieQueenAI.LINE_CHALLENGE));
        assertEquals(1, ai.times(ValkyrieQueenAI.LINE_WAITING));
        assertEquals(1, ai.times("Tribute: 0 of 10"));
        assertEquals(1, ai.times("A Victory Medal counts for 1, and every valkyrie of hers you beat for 2."),
            "the party is told what each is worth");
        assertEquals(1, ai.times("Beat 5 more of her valkyries and she will fight."),
            "and how many of them it will take");
        assertTrue(self.getDamageImmuneHint().contains("Tribute 0 of 10. Beat 5 more of her valkyries"),
            self.getDamageImmuneHint());

        EnemyAction.SummonMinions order = assertInstanceOf(EnemyAction.SummonMinions.class, action);
        assertEquals(AetherMobs.VALKYRIE, order.entityTypeId());
        assertEquals(ValkyrieQueenAI.MAX_ESCORT, order.count());
        assertEquals(order.count(), order.positions().size());
        assertEquals(20, order.hp());
        assertEquals(8, order.atk());
        assertEquals(2, order.def());
        for (GridPos tile : order.positions()) {
            assertTrue(ValkyrieQueenAI.open(a, tile), tile + " is not somewhere a valkyrie can stand");
        }
    }

    @Test
    @DisplayName("ten medals: she takes them, says so, and the guard drops at once")
    void tenMedalsOpenTheGate() {
        GridPos player = new GridPos(8, 5);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = new Queen(13);

        EnemyAction action = ai.decideAction(self, a, player);

        assertInstanceOf(EnemyAction.Idle.class, action, "the turn the gate opens is not an attack");
        assertEquals(3, ai.purse, "she takes ten and no more");
        assertEquals(10, ai.tributeSoFar());
        assertFalse(self.isDamageImmune());
        assertFalse(ai.isGuardUp());
        assertEquals(1, ai.times(ValkyrieQueenAI.LINE_BEGIN));
        assertEquals(0, ai.times(ValkyrieQueenAI.LINE_FIGHT), "that line is for the duel's first turn");
    }

    /**
     * Beat every valkyrie she sends in the round it arrives, until the gate opens, and say how
     * many that took. While it is shut the guard holds, she never swings, and the tribute is
     * the medals she took plus two for each valkyrie beaten: nothing for the crystal that is
     * hunting the whole time.
     */
    private static int valkyriesBeatenAtTheGate(Queen ai, GridArena a, CombatEntity self, GridPos player) {
        int medals = Math.min(ai.purse, ValkyrieQueenAI.MEDALS_REQUIRED);
        int beaten = 0;
        List<CombatEntity> escort = new ArrayList<>();
        for (int turn = 1; turn <= 30 && ai.isGuardUp(); turn++) {
            EnemyAction action = ai.decideAction(self, a, player);
            if (!ai.isGuardUp()) break;

            assertTrue(self.isDamageImmune(), "turn " + turn + ": the guard slipped while the gate was shut");
            assertHerShape(ai, action, "turn " + turn);
            assertEquals(medals + ValkyrieQueenAI.VALKYRIE_WORTH * beaten, ai.tributeSoFar(), "turn " + turn);

            if (action instanceof EnemyAction.SummonMinions order) {
                escort.addAll(arrive(ai, a, order));
                long alive = escort.stream().filter(CombatEntity::isAlive).count();
                assertTrue(alive <= ValkyrieQueenAI.MAX_ESCORT, "turn " + turn + ": " + alive + " valkyries at once");
            }
            if (action instanceof EnemyAction.SpawnProjectile shot) launch(ai, a, self, shot);
            // Beaten in the same round they arrived, which the base class's own list never sees.
            for (CombatEntity valkyrie : escort) {
                if (!valkyrie.isAlive()) continue;
                assertTrue(valkyrie.isEnraged(), "a valkyrie she sends arrives ready to fight");
                beat(a, valkyrie);
                beaten++;
            }
        }
        assertFalse(ai.isGuardUp(), "thirty turns of beaten valkyries should have opened the gate");
        assertFalse(self.isDamageImmune());
        return beaten;
    }

    @Test
    @DisplayName("short of ten: the medals count, and each valkyrie beaten counts as two more")
    void valkyriesMakeUpTheDifference() {
        GridPos player = new GridPos(9, 5);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = new Queen(4);

        assertEquals(3, valkyriesBeatenAtTheGate(ai, a, self, player), "the gate opened on exactly the tenth");
        assertEquals(0, ai.purse);
        assertEquals(1, ai.times("hands the Queen 4 Victory Medals"));
        assertEquals(1, ai.times("Tribute: 4 of 10"));
        assertEquals(1, ai.times("Tribute: 8 of 10"));
        assertEquals(1, ai.times(ValkyrieQueenAI.LINE_BEGIN));
    }

    @Test
    @DisplayName("no medals at all: five valkyries beaten open the gate")
    void fiveValkyriesOpenTheGate() {
        GridPos player = new GridPos(9, 5);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = new Queen(0);

        assertEquals(5, valkyriesBeatenAtTheGate(ai, a, self, player), "five at two apiece");
        assertEquals(0, ai.times("hands the Queen"), "nobody had a medal to give");
        // Two fall to a round, so the count she calls out goes up in fours.
        for (int tribute : new int[]{0, 4, 8}) {
            assertEquals(1, ai.times("Tribute: " + tribute + " of 10"));
        }
        assertEquals(1, ai.times(ValkyrieQueenAI.LINE_BEGIN));
    }

    @Test
    @DisplayName("one medal short: a single valkyrie is sent, and beating it is more than enough")
    void oneMedalShort() {
        GridPos player = new GridPos(9, 5);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = new Queen(9);

        EnemyAction.SummonMinions order =
            assertInstanceOf(EnemyAction.SummonMinions.class, ai.decideAction(self, a, player));
        assertEquals(1, order.count());
        assertEquals(9, ai.tributeSoFar());
        beat(a, arrive(ai, a, order).get(0));

        assertInstanceOf(EnemyAction.Idle.class, ai.decideAction(self, a, player));
        assertFalse(ai.isGuardUp());
        assertEquals(ValkyrieQueenAI.MEDALS_REQUIRED, ai.tributeSoFar(), "worth two, and the tribute still stops at ten");
    }

    @Test
    @DisplayName("a valkyrie of her escort counts the moment it falls: once, with the count said, and not again on her turn")
    void beatenValkyrieCountsAtOnce() {
        GridPos player = new GridPos(9, 5);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = new Queen(0);

        EnemyAction.SummonMinions order =
            assertInstanceOf(EnemyAction.SummonMinions.class, ai.decideAction(self, a, player));
        CombatEntity first = arrive(ai, a, order).get(0);
        beat(a, first);

        String said = ai.onValkyrieBeaten(self, first);
        assertNotNull(said, "her own valkyrie, beaten at her gate");
        assertTrue(said.contains("Beat 4 more of her valkyries and she will fight."), said);
        assertTrue(self.getDamageImmuneHint().contains("Tribute 2 of 10. Beat 4 more of her valkyries"),
            "striking her guard gives the same count: " + self.getDamageImmuneHint());
        assertTrue(said.contains("worth " + ValkyrieQueenAI.VALKYRIE_WORTH + " medals"), said);
        assertTrue(said.contains("Tribute: 2 of 10"), said);
        assertFalse(said.contains("Paid in full"), said);
        assertEquals(2, ai.tributeSoFar());
        assertNull(ai.onValkyrieBeaten(self, first), "the same valkyrie pays once");
        assertEquals(2, ai.tributeSoFar());

        // Her turn finds it already counted, and has nothing new to call out.
        ai.decideAction(self, a, player);
        assertEquals(2, ai.tributeSoFar());
        assertEquals(0, ai.times("Tribute: 2 of 10"));
        assertTrue(self.isDamageImmune());

        // Nothing she did not send pays anything.
        assertNull(ai.onValkyrieBeaten(self, self));
        assertNull(ai.onValkyrieBeaten(self, null));
        assertEquals(2, ai.tributeSoFar());
    }

    @Test
    @DisplayName("the valkyrie that completes the tribute says it is paid, and the gate still opens on her turn")
    void lastValkyriePaysInFull() {
        GridPos player = new GridPos(9, 5);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = new Queen(9);

        EnemyAction.SummonMinions order =
            assertInstanceOf(EnemyAction.SummonMinions.class, ai.decideAction(self, a, player));
        CombatEntity sent = arrive(ai, a, order).get(0);
        beat(a, sent);

        String said = ai.onValkyrieBeaten(self, sent);
        assertNotNull(said);
        assertFalse(said.contains("more of her valkyries"), said);
        assertTrue(said.contains("Tribute: 10 of 10"), said);
        assertTrue(said.contains("Paid in full"), said);
        assertTrue(ai.isGuardUp(), "her guard is hers to drop");

        assertInstanceOf(EnemyAction.Idle.class, ai.decideAction(self, a, player));
        assertFalse(ai.isGuardUp());
        assertEquals(1, ai.times(ValkyrieQueenAI.LINE_BEGIN));
        // Once the duel is on, a valkyrie of her honour guard is no tribute.
        assertNull(ai.onValkyrieBeaten(self, sent));
    }

    @Test
    @DisplayName("nothing changing means nothing said twice, and no free progress")
    void waitingIsQuiet() {
        GridPos player = new GridPos(9, 5);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = new Queen(0);

        List<CombatEntity> escort = new ArrayList<>();
        List<CombatEntity> thrown = new ArrayList<>();
        // Six turns: as long as the crystal she throws on the second has before it burns out.
        for (int turn = 1; turn <= 6; turn++) {
            EnemyAction action = ai.decideAction(self, a, player);
            assertHerShape(ai, action, "turn " + turn);
            if (action instanceof EnemyAction.SummonMinions order) escort.addAll(arrive(ai, a, order));
            if (action instanceof EnemyAction.SpawnProjectile shot) thrown.add(launch(ai, a, self, shot));
        }

        assertEquals(0, ai.tributeSoFar());
        assertEquals(ValkyrieQueenAI.MAX_ESCORT, escort.size(), "she tops the escort up, she does not flood the room");
        assertEquals(ValkyrieQueenAI.GATE_CRYSTALS, thrown.size(), "one crystal hunting, and no other while it does");
        assertEquals(1, ai.times("Tribute:"));
        assertEquals(1, ai.times(ValkyrieQueenAI.LINE_WAITING));
        assertEquals(1, ai.times("A thunder crystal hunts you"), "said as it is thrown, not every turn it flies");
        assertTrue(self.isDamageImmune());
    }

    @Test
    @DisplayName("a crystal is not a valkyrie: however many come and go, the tribute does not move")
    void crystalsPayNoTribute() {
        GridPos player = new GridPos(9, 5);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = new Queen(0);

        int orders = 0;
        List<Integer> thrownOn = new ArrayList<>();
        for (int turn = 1; turn <= 12; turn++) {
            EnemyAction action = ai.decideAction(self, a, player);
            assertEquals(0, ai.tributeSoFar(), "turn " + turn);
            if (action instanceof EnemyAction.SummonMinions order) {
                arrive(ai, a, order);
                orders++;
            }
            if (action instanceof EnemyAction.SpawnProjectile shot) {
                // Gone in the round it was thrown, the way a valkyrie beaten on arrival is. She
                // counts what she sent off a list of her own, and a crystal is never on it.
                burst(a, launch(ai, a, self, shot));
                thrownOn.add(turn);
            }
        }

        assertEquals(List.of(2, 5, 8, 11), thrownOn, "with none left hunting, one every third turn");
        assertEquals(1, orders, "her two valkyries are still standing, so no more are sent");
        assertEquals(ValkyrieQueenAI.MAX_ESCORT, valkyries(a).size());
        assertEquals(1, ai.times("Tribute:"));
        assertTrue(ai.isGuardUp());
        assertTrue(self.isDamageImmune());
    }

    @Test
    @DisplayName("medals that turn up later are taken when they do")
    void lateMedalsStillCount() {
        GridPos player = new GridPos(9, 5);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = new Queen(0);

        ai.decideAction(self, a, player);
        assertTrue(ai.isGuardUp());

        ai.purse = 10;
        assertInstanceOf(EnemyAction.Idle.class, ai.decideAction(self, a, player));
        assertFalse(ai.isGuardUp());
        assertEquals(0, ai.purse);
    }

    @Test
    @DisplayName("guard up and crowded: she steps away rather than swing, and throws nothing from that close")
    void keepsHerDistance() {
        GridPos player = new GridPos(6, 5);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = new Queen(0);

        // Her escort is already out, so the only thing left to decide is where to stand.
        EnemyAction first = ai.decideAction(self, a, player);
        arrive(ai, a, assertInstanceOf(EnemyAction.SummonMinions.class, first));

        EnemyAction action = ai.decideAction(self, a, player);
        EnemyAction.Move move = assertInstanceOf(EnemyAction.Move.class, action,
            "a crystal in her hand, and no tile beside her that is three steps from the party");
        GridPos end = move.path().get(move.path().size() - 1);
        assertTrue(end.manhattanDistance(player) > self.getGridPos().manhattanDistance(player),
            "she moved, but not away");

        // It was held back, not spent: with her distance kept it goes at once, and she stays put.
        carryOut(ai, a, self, action);
        EnemyAction.SpawnProjectile shot =
            assertInstanceOf(EnemyAction.SpawnProjectile.class, ai.decideAction(self, a, player));
        GridPos launch = shot.positions().get(0);
        assertTrue(launch.manhattanDistance(player) >= ValkyrieQueenAI.CRYSTAL_HEAD_START);
        assertEquals(1, Math.max(Math.abs(launch.x() - self.getGridPos().x()),
            Math.abs(launch.z() - self.getGridPos().z())), "it starts beside her");
    }

    @Test
    @DisplayName("guard up: the nearer the party, the further round her the crystal starts, and beside her not at all")
    void crystalsGetAHeadStart() {
        assertEquals(3, ValkyrieQueenAI.CRYSTAL_HEAD_START);
        for (int gap = 1; gap <= 6; gap++) {
            GridPos player = new GridPos(5 + gap, 5);
            GridArena a = arena(player);
            CombatEntity self = queen(a, 5, 5);
            Queen ai = guardedAndAlone(self, a, player);

            EnemyAction action = ai.decideAction(self, a, player);
            EnemyAction.SpawnProjectile shot = thrown(action);
            String when = "the party " + gap + " tiles down the row";
            if (gap == 1) {
                assertInstanceOf(EnemyAction.Move.class, action, when);
                continue;
            }
            assertNotNull(shot, when);
            GridPos launch = shot.positions().get(0);
            assertTrue(launch.manhattanDistance(player) >= ValkyrieQueenAI.CRYSTAL_HEAD_START,
                when + ": thrown from " + launch);
            if (gap == 2) {
                // Still too close for her liking: she backs off, and throws from her far side as she goes.
                EnemyAction.Move away = assertInstanceOf(EnemyAction.Move.class, bundle(action).onFoot(), when);
                assertFalse(away.path().contains(launch), when);
                assertEquals(1, launch.manhattanDistance(new GridPos(5, 5)), when + ": it starts beside her");
                assertTrue(launch.x() <= 5, when + ": and not on the party's side of her");
            } else {
                assertInstanceOf(EnemyAction.SpawnProjectile.class, action, when);
                assertEquals(gap == 3 ? new GridPos(6, 6) : new GridPos(6, 5), launch,
                    when + ": toward her target, or beside that when it is a step too near");
            }
            // The distance is the rule. This is what it is for: asked once she has moved, the
            // crystal's own homing brain does not bring it in this turn.
            assertHeadStart(a, carryOut(ai, a, self, action), when);
        }
    }

    @Test
    @DisplayName("guard up and left alone: she stands where she is and throws a crystal toward her target")
    void watchesFromAfar() {
        GridPos player = new GridPos(10, 10);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 2, 2);
        Queen ai = guardedAndAlone(self, a, player);

        EnemyAction.SpawnProjectile shot = assertInstanceOf(EnemyAction.SpawnProjectile.class,
            ai.decideAction(self, a, player), "nobody is near, so she does not move and the crystal goes alone");
        assertEquals(ValkyrieQueenAI.THUNDER_CRYSTAL, shot.projectileType());
        assertEquals(ValkyrieQueenAI.CRYSTAL_BODY, shot.entityTypeId());
        assertEquals(List.of(new GridPos(3, 2)), shot.positions(), "it leaves her hand toward her target");
        assertEquals(1, shot.directions().size());
        assertEquals(5, shot.atk());
        assertEquals(ValkyrieQueenAI.CRYSTAL_HP, shot.hp());
    }

    @Test
    @DisplayName("guard up: one crystal in the air, and no second until it is gone")
    void oneCrystalBehindTheGate() {
        GridPos player = new GridPos(10, 10);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 2, 2);
        Queen ai = new Queen(0);
        arrive(ai, a, assertInstanceOf(EnemyAction.SummonMinions.class, ai.decideAction(self, a, player)));

        assertEquals(1, ValkyrieQueenAI.GATE_CRYSTALS);
        CombatEntity first = launch(ai, a, self,
            assertInstanceOf(EnemyAction.SpawnProjectile.class, ai.decideAction(self, a, player)));

        // Past its cooldown she still only stands and watches: the one she threw is hunting.
        for (int turn = 1; turn < ValkyrieQueenAI.CRYSTAL_TURNS; turn++) {
            assertInstanceOf(EnemyAction.Idle.class, ai.decideAction(self, a, player), "turn " + turn);
            assertFalse(ValkyrieQueenAI.burntOut(first));
        }

        // Five turns of hunting and it burns out. It is no longer hers, and the next follows at once.
        CombatEntity second = launch(ai, a, self,
            assertInstanceOf(EnemyAction.SpawnProjectile.class, ai.decideAction(self, a, player)));
        assertTrue(ValkyrieQueenAI.burntOut(first));
        burst(a, first);

        // Gone altogether, the next one still has to come to hand.
        burst(a, second);
        assertInstanceOf(EnemyAction.Idle.class, ai.decideAction(self, a, player));
        assertInstanceOf(EnemyAction.Idle.class, ai.decideAction(self, a, player));
        assertInstanceOf(EnemyAction.SpawnProjectile.class, ai.decideAction(self, a, player));

        assertEquals(2, ai.times("A thunder crystal hunts you"), "once for each that was launched");
        assertEquals(0, ai.tributeSoFar());
        assertTrue(ai.isGuardUp());
    }

    @Test
    @DisplayName("guard up and cornered with a crystal in hand: she blinks out first, and throws the turn after")
    void corneredSheBlinksOut() {
        GridPos player = new GridPos(0, 1);
        GridArena a = pillared(player);
        CombatEntity self = queen(a, 0, 0);
        Queen ai = new Queen(0);
        arrive(ai, a, assertInstanceOf(EnemyAction.SummonMinions.class, ai.decideAction(self, a, player)));

        // The pillar on one side and the party on the other: nowhere to step, and nothing thrown yet.
        EnemyAction.Teleport blink = assertInstanceOf(EnemyAction.Teleport.class, ai.decideAction(self, a, player),
            "a blink is the whole of its turn, crystal in hand or not");
        assertEquals(new GridPos(11, 11), blink.target(), "the open tile furthest from the party");
        assertEquals(0, crystals(a).size());

        carryOut(ai, a, self, blink);
        EnemyAction.SpawnProjectile shot = assertInstanceOf(EnemyAction.SpawnProjectile.class,
            ai.decideAction(self, a, player), "the crystal she kept in hand");
        assertEquals(List.of(new GridPos(10, 11)), shot.positions());
    }

    @Test
    @DisplayName("behind her guard, wherever the party stands: no swing, and a crystal rides only with a step away")
    void gateShapesEverywhere() {
        Set<String> seen = new TreeSet<>();
        for (GridPos start : new GridPos[]{new GridPos(5, 5), new GridPos(0, 0)}) {
            for (int x = 0; x < SIZE; x++) {
                for (int z = 0; z < SIZE; z++) {
                    GridPos player = new GridPos(x, z);
                    if (!player.equals(start) && !player.equals(PILLAR)) playTheGate(start, player, seen);
                }
            }
        }
        // Every shape was held to the rule as it came. This is that they all did come.
        for (String shape : List.of("crystal + Move", "SpawnProjectile", "SummonMinions", "Teleport", "Move", "Idle")) {
            assertTrue(seen.contains(shape), "the sweep never reached '" + shape + "', only " + seen);
        }
        assertEquals(List.of("crystal + Move"),
            seen.stream().filter(shape -> shape.startsWith("crystal + ")).toList(), "her one bundle behind the guard");
    }

    /**
     * Nine turns behind her guard against a party that keeps walking up to her: to two tiles
     * off, then right beside her, and round again. Every third turn clears the floor of her
     * valkyries and her crystals.
     */
    private static void playTheGate(GridPos start, GridPos player, Set<String> seen) {
        GridArena a = pillared(player);
        CombatEntity self = queen(a, start.x(), start.z());
        Queen ai = new Queen(0);
        int beaten = 0;
        for (int turn = 1; turn <= 9; turn++) {
            String when = "she began at " + start + ", the party at " + player + ", gate turn " + turn;
            GridPos closer = turn % 4 == 2 ? downALane(a, self, 2) : turn % 4 == 0 ? openTileBeside(a, self) : null;
            if (closer != null) {
                player = closer;
                standPlayer(a, player);
            }

            EnemyAction action = ai.decideAction(self, a, player);

            assertTrue(ai.isGuardUp(), when);
            assertTrue(self.isDamageImmune(), when);
            assertEquals(ValkyrieQueenAI.VALKYRIE_WORTH * beaten, ai.tributeSoFar(), when);
            assertHerShape(ai, action, when);
            seen.add(shapeOf(ai, action));
            CombatEntity thrown = carryOut(ai, a, self, action);
            // However near the party has come, nothing she throws lands the turn it is thrown.
            if (thrown != null) assertHeadStart(a, thrown, when);
            assertTrue(valkyries(a).size() <= ValkyrieQueenAI.MAX_ESCORT, when);

            if (turn % 3 == 0) {
                for (CombatEntity valkyrie : valkyries(a)) {
                    beat(a, valkyrie);
                    beaten++;
                }
                for (CombatEntity crystal : crystals(a)) burst(a, crystal);
            }
        }
    }

    private static GridPos openTileBeside(GridArena a, CombatEntity self) {
        GridPos me = self.getGridPos();
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            GridPos tile = new GridPos(me.x() + d[0], me.z() + d[1]);
            if (ValkyrieQueenAI.open(a, tile)) return tile;
        }
        return null;
    }

    // ── The engine's side of a fight ──

    /** Set her health outright, guard or no guard. */
    private static void setHp(CombatEntity self, int hp) {
        int now = self.getCurrentHp();
        if (now > hp) self.applyDirectDamage(now - hp);
        else self.heal(hp - now);
        assertEquals(hp, self.getCurrentHp(), "fixture: her health did not take");
    }

    /** A health well inside each third of it: all of it, half, and three tenths. */
    private static int healthIn(CombatEntity self, int phase) {
        return phase == 1 ? self.getMaxHp() : phase == 2 ? self.getMaxHp() / 2 : self.getMaxHp() * 3 / 10;
    }

    /** A bare floor, with a shape cut out of it when the room is not a rectangle. */
    private static GridArena floor(int width, int height, boolean[][] inside, GridPos player) {
        GridTile[][] tiles = new GridTile[width][height];
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < height; z++) {
                tiles[x][z] = new GridTile(TileType.NORMAL, null);
            }
        }
        GridArena a = new GridArena(width, height, tiles, BlockPos.ORIGIN, 1, player, inside);
        standPlayer(a, player);
        return a;
    }

    /**
     * The two rooms her fight may be set in. Nothing she does may depend on which, so
     * whatever runs a whole fight runs it in both.
     */
    private static final String[] ROOMS = {"hall", "chamber"};

    /**
     * The hall is ten wide and nineteen long with one end rounded off, and its floor outside
     * the curve is still floor as far as a tile can tell: only the arena's shape says it is
     * not part of the room. The chamber is a plain twenty-five by twenty-one.
     */
    private static GridArena room(String which, GridPos player) {
        if (which.equals("chamber")) return floor(25, 21, null, player);
        boolean[][] inside = new boolean[10][19];
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 19; z++) {
                int cut = z == 18 ? 3 : z == 17 ? 2 : z == 16 ? 1 : 0;
                inside[x][z] = x >= cut && x < 10 - cut;
            }
        }
        return floor(10, 19, inside, player);
    }

    private static GridPos middleOf(GridArena a) {
        return new GridPos(a.getWidth() / 2, a.getHeight() / 2);
    }

    /** Every tile of a room somebody could stand on, a row at a time. */
    private static List<GridPos> floorOf(GridArena a) {
        List<GridPos> tiles = new ArrayList<>();
        for (int x = 0; x < a.getWidth(); x++) {
            for (int z = 0; z < a.getHeight(); z++) {
                GridPos tile = new GridPos(x, z);
                if (a.isInBounds(tile) && a.getTile(tile).isWalkable()) tiles.add(tile);
            }
        }
        return tiles;
    }

    /** Her stats at the depth the Silver Dungeon is met: 780 health, and a blow at the engine's ceiling. */
    private static CombatEntity deepQueen(GridArena a, GridPos at) {
        CombatEntity q = new CombatEntity(nextId++, ValkyrieQueenAI.QUEEN, at, 780, 12, 3, 1, 1, 3);
        q.setBoss(true);
        q.setAiOverrideKey(ValkyrieQueenAI.BOSS_KEY);
        assertTrue(a.placeEntity(q), "fixture: the Queen did not fit");
        return q;
    }

    private static final ValkyrieQueenAI.ThunderCrystal CRYSTAL = new ValkyrieQueenAI.ThunderCrystal();
    private static final ValkyrieAI VALKYRIE = new ValkyrieAI();

    /**
     * What a simulated player has in a turn: the game's base speed, and its base action
     * points with a sword in hand, a swing costing one.
     */
    private static final int STEPS = 3;
    private static final int STRIKES = 3;

    /**
     * The blow that kills one of her honour guard in exactly {@code strikes}, at the depth
     * she is met, found against a valkyrie's own armor.
     */
    private static int blowThatKillsAGuardIn(int strikes) {
        for (int blow = 1; blow < 1000; blow++) {
            CombatEntity guard = new CombatEntity(nextId++, AetherMobs.VALKYRIE, new GridPos(0, 0),
                ValkyrieQueenAI.guardHp(780), ValkyrieQueenAI.guardAttack(12), ValkyrieQueenAI.escortDefense(3),
                1, 1, 3);
            for (int i = 0; i < strikes; i++) guard.takeDamage(blow);
            if (!guard.isAlive()) return blow;
        }
        throw new AssertionError("fixture: no blow kills a guard in " + strikes);
    }

    private enum Source { SWING, SHOVE, LUNGE, SWEEP, DIVE, STORM, CRYSTAL, VALKYRIE }

    /** What a fight is counted in, besides the hits. */
    private enum Count {
        TURNS, THROWN, BURNT_OUT, STRUCK, ON_HER, ON_A_VALKYRIE, TAKEOFFS, LANDINGS, GROUNDINGS, BESIDE_HER_DOWN,
        CALLED, KILLED, BLOWS_ON_HER, STORM_TURNS, TRAPPED
    }

    /** How a simulated player plays. */
    private enum Player {
        /** Ends every turn off every mark and not beside her, as far from her and hers as three steps get. */
        EVADER,
        /**
         * Also strikes: every crystal in reach, at her when the line is there, then a
         * valkyrie in reach, then her, whenever the turn can still be ended somewhere safe.
         */
        ANSWERER,
        /**
         * Also brings her down when a dive allows it. To have a crystal to do it with, this
         * one leads her crystals about rather than strike them away, but for a shot at her.
         */
        GROUNDER,
        /** None of that: ends every turn standing against her, wherever that is, and stays under her when she flies. */
        HUGGER
    }

    /** What a fight came to, for whoever is tuning it. */
    private static final class Tally {
        final int[] hits = new int[Source.values().length];
        final int[] counts = new int[Count.values().length];

        void hit(Source source) {
            hits[source.ordinal()]++;
        }

        void count(Count what) {
            counts[what.ordinal()]++;
        }

        int of(Source source) {
            return hits[source.ordinal()];
        }

        int of(Count what) {
            return counts[what.ordinal()];
        }

        /** Hits that were her own doing: everything but a crystal's and a valkyrie's. */
        int fromHer() {
            return taken() - of(Source.CRYSTAL) - of(Source.VALKYRIE);
        }

        int taken() {
            int sum = 0;
            for (int count : hits) sum += count;
            return sum;
        }

        void add(Tally other) {
            for (int i = 0; i < hits.length; i++) hits[i] += other.hits[i];
            for (int i = 0; i < counts.length; i++) counts[i] += other.counts[i];
        }

        /** What has been counted since {@code earlier} was. */
        Tally since(Tally earlier) {
            Tally rest = new Tally();
            for (int i = 0; i < hits.length; i++) rest.hits[i] = hits[i] - earlier.hits[i];
            for (int i = 0; i < counts.length; i++) rest.counts[i] = counts[i] - earlier.counts[i];
            return rest;
        }

        Tally copy() {
            return since(new Tally());
        }

        /** Scaled to two hundred turns when {@code scaled}, which is how a third is read. As counted when not. */
        String line(boolean scaled) {
            StringBuilder line = new StringBuilder("hits");
            for (Source source : Source.values()) {
                line.append(' ').append(source.name().toLowerCase()).append(' ').append(show(of(source), scaled));
            }
            return line + " (all " + show(taken(), scaled) + ")"
                + " | crystals thrown " + show(of(Count.THROWN), scaled)
                + ", burnt out " + show(of(Count.BURNT_OUT), scaled)
                + ", struck " + show(of(Count.STRUCK), scaled) + ", burst on her " + show(of(Count.ON_HER), scaled)
                + ", on a valkyrie " + show(of(Count.ON_A_VALKYRIE), scaled)
                + " | dives " + show(of(Count.TAKEOFFS), scaled) + ", landed " + show(of(Count.LANDINGS), scaled)
                + ", grounded " + show(of(Count.GROUNDINGS), scaled)
                + ", turns beside her down " + show(of(Count.BESIDE_HER_DOWN), scaled)
                + " | valkyries called " + show(of(Count.CALLED), scaled) + ", killed " + show(of(Count.KILLED), scaled)
                + " | blows on her " + show(of(Count.BLOWS_ON_HER), scaled)
                + " | turns with no safe tile " + show(of(Count.TRAPPED), scaled);
        }

        private String show(int count, boolean scaled) {
            if (!scaled) return Integer.toString(count);
            int turns = of(Count.TURNS);
            return String.format(java.util.Locale.ROOT, "%.1f", turns == 0 ? 0.0 : count * 200.0 / turns);
        }
    }

    /**
     * The engine's side of a fight, as far as her decisions need it: whose turn it is, where
     * everyone stands, and what each action she asks for does once it is carried out. Her
     * crystals fly on the engine's own brain until she gives them another, and land or are
     * struck through her own handler. Her valkyries fight on theirs.
     *
     * <p>Every turn of hers is held to her shapes, and to the promise the fight is built on:
     * nobody is hurt by her turn who was not standing on a mark she had shown, or beside
     * her, when it began. And nobody is hurt by it twice.
     */
    private static final class Fight {
        final GridArena a;
        final CombatEntity self;
        final Queen ai;
        final String where;
        final List<GridPos> party = new ArrayList<>();
        final Tally tally = new Tally();
        /** What she did, a word a turn. */
        final List<String> did = new ArrayList<>();
        /** The floor as she left it painted: what the party sees while it moves. */
        Set<GridPos> shown = Set.of();
        /** What one blow of the simulated player's does before armor. Nothing, for one who never strikes. */
        int blow = 0;
        /** False for a player who never turns to her valkyries. */
        boolean killsHerGuard = true;
        /** True for a player who never takes more than one step to get clear of a mark. */
        boolean oneStep = false;
        /** Above nothing, her health is put back to this after whatever touches it: the third being measured. */
        int heldAt = 0;
        /** Her valkyries that landed on the turn in hand: they act in it, and may not strike in it. */
        final List<CombatEntity> justLanded = new ArrayList<>();

        Fight(GridArena a, CombatEntity self, Queen ai, String where, List<GridPos> party) {
            this.a = a;
            this.self = self;
            this.ai = ai;
            this.where = where;
            this.party.addAll(party);
            stand();
        }

        /** A duel in one of her rooms at the depth she is met, the tribute already paid. */
        static Fight inRoom(String room, GridPos... party) {
            GridArena a = room(room, party[0]);
            CombatEntity self = deepQueen(a, middleOf(a));
            Fight fight = new Fight(a, self, new Queen(ValkyrieQueenAI.MEDALS_REQUIRED), room, List.of(party));
            assertInstanceOf(EnemyAction.Idle.class, fight.ai.decideAction(self, a, party[0]));
            return fight;
        }

        void stand() {
            a.setPlayerGridPos(party.get(0));
            a.setAllPlayerGridPositions(List.copyOf(party));
        }

        void move(int who, GridPos to) {
            party.set(who, to);
            stand();
        }

        GridPos nearestTo(GridPos from) {
            GridPos best = party.get(0);
            for (GridPos member : party) {
                if (member.manhattanDistance(from) < best.manhattanDistance(from)) best = member;
            }
            return best;
        }

        /** Everything of hers that is on the floor for a struck crystal to burst on. */
        List<CombatEntity> hersOnTheFloor() {
            List<CombatEntity> hers = new ArrayList<>(valkyries(a));
            if (self.isAlive() && !self.isOnCeiling()) hers.add(self);
            return hers;
        }

        void hold() {
            if (heldAt > 0 && self.isAlive()) setHp(self, heldAt);
        }

        /** Everything after the party has moved: her turn, then whatever of hers follows her. */
        EnemyAction enemyPhase() {
            EnemyAction action = herTurn();
            crystalsFly();
            guardTakesItsTurns();
            hold();
            return action;
        }

        EnemyAction herTurn() {
            tally.count(Count.TURNS);
            stand();
            Set<GridPos> marks = shown;
            boolean onTheFloor = !self.isOnCeiling();
            GridPos stood = self.getGridPos();
            boolean lunging = !ai.laneMarked().isEmpty();
            boolean wasDown = ai.isDown();
            boolean wasAloft = ai.isAloft();
            String when = where + ", turn " + tally.of(Count.TURNS) + ", she " + (onTheFloor ? "at " : "above ")
                + stood + ", the party at " + party;

            self.setPendingStrikeTile(null);
            GridPos target = nearestTo(stood);
            a.setPlayerGridPos(target);
            EnemyAction action = ai.decideAction(self, a, target);
            assertHerShape(ai, action, when);

            List<GridPos> hurt = new ArrayList<>();
            String word = resolve(action, lunging, hurt, when);
            // The engine asks after every action of hers whether she has more to do this
            // turn. She has only when a dive landed on more than one of them: a throw each.
            for (int more = 0; ai.wantsAnotherAction(self); more++) {
                assertTrue(more < party.size(), when + ": she will not stop asking for another action");
                assertTrue(word.startsWith("dive"), when + ": a second action on a turn that was not a landing");
                self.setPendingStrikeTile(null);
                EnemyAction next = ai.decideAction(self, a, target);
                assertInstanceOf(EnemyAction.ForcedMovement.class, next,
                    when + ": asked back, and it was not for a throw");
                assertTrue(hurt.contains(self.getPendingStrikeTile()), when + ": she threw someone the dive had not hit");
                resolve(next, false, hurt, when);
            }
            for (GridPos tile : hurt) {
                assertTrue(marks.contains(tile) || (onTheFloor && tile.manhattanDistance(stood) == 1),
                    when + ": " + action + " hurt someone on " + tile + ", who stood on no mark of " + marks
                        + " and not beside her");
            }
            assertEquals(Set.copyOf(hurt).size(), hurt.size(),
                when + ": " + action + " hurt somebody twice in the one turn, on " + hurt);
            if (wasDown) {
                assertTrue(hurt.isEmpty() && action instanceof EnemyAction.Idle, when + ": she acted while down");
            }
            if (!wasAloft && ai.isAloft()) {
                tally.count(Count.TAKEOFFS);
                word = "air" + (word.startsWith("bolts") ? "+bolts" : "");
            }
            if (wasDown) word = "down";
            if (!lunging && !ai.laneMarked().isEmpty()) word = "aim" + (word.startsWith("bolts") ? "+bolts" : "");
            did.add(word);

            // What is on the floor now is all of it: the base class holds one warning.
            Set<GridPos> painted = new LinkedHashSet<>();
            if (ai.getPendingWarning() != null) painted.addAll(ai.getPendingWarning().getAffectedTiles());
            Set<GridPos> owed = new LinkedHashSet<>(ai.laneMarked());
            owed.addAll(ai.diveMarked());
            owed.addAll(ai.stormMarked());
            assertEquals(owed, painted, when + ": the floor does not show what she has marked");
            for (GridPos tile : painted) assertTrue(a.isInBounds(tile), when + ": a mark outside the room at " + tile);
            shown = painted;
            stand();
            return action;
        }

        /** Carry one action of hers out, part by part in the order given, and say in a word what it was. */
        private String resolve(EnemyAction action, boolean lunging, List<GridPos> hurt, String when) {
            List<EnemyAction> parts = EnemyAction.flatten(action);
            boolean diving = parts.get(0) instanceof EnemyAction.CeilingDrop;
            List<String> words = new ArrayList<>();
            for (EnemyAction part : parts) {
                if (part instanceof EnemyAction.Idle) {
                    words.add("wait");
                } else if (part instanceof EnemyAction.SummonMinions order) {
                    assertEquals(1, order.count(), when + ": her honour guard comes one at a time");
                    assertEquals(ValkyrieQueenAI.guardHp(self.getMaxHp()), order.hp(), when);
                    assertEquals(ValkyrieQueenAI.guardAttack(self.getAttackPower()), order.atk(), when);
                    assertTrue(valkyries(a).size() < ValkyrieQueenAI.MAX_GUARD,
                        when + ": a call with her guard already on the floor");
                    justLanded.addAll(arrive(ai, a, order));
                    tally.count(Count.CALLED);
                    words.add("summon");
                } else if (part instanceof EnemyAction.SpawnProjectile shot) {
                    launch(ai, a, self, shot);
                    tally.count(Count.THROWN);
                    words.add("throw");
                } else if (part instanceof EnemyAction.Move move) {
                    walk(a, self, move.path());
                    words.add(lunging ? "run" : "walk");
                } else if (part instanceof EnemyAction.Teleport blink) {
                    walk(a, self, List.of(blink.target()));
                    words.add("blink");
                } else if (part instanceof EnemyAction.MoveAndAttack strike) {
                    assertTrue(lunging, when + ": a walk that ends in a swing, with no lane marked for it");
                    walk(a, self, strike.path());
                    swing(Source.LUNGE, 0, hurt);
                    words.add("lunge");
                } else if (part instanceof EnemyAction.Attack) {
                    swing(lunging ? Source.LUNGE : Source.SWING, 0, hurt);
                    words.add(lunging ? "lunge" : "swing");
                } else if (part instanceof EnemyAction.AttackWithKnockback shove) {
                    boolean throwing = shove.knockbackTiles() > 0;
                    swing(throwing ? Source.SHOVE : lunging ? Source.LUNGE : Source.SWING, shove.knockbackTiles(), hurt);
                    words.add(throwing ? "shove" : lunging ? "lunge" : "swing");
                } else if (part instanceof EnemyAction.CeilingDrop drop) {
                    assertTrue(ValkyrieQueenAI.open(a, drop.landingPos()), when + ": she came down on a taken tile");
                    self.setOnCeiling(false);
                    self.setGridPos(drop.landingPos());
                    assertTrue(a.placeEntity(self), when);
                    tally.count(Count.LANDINGS);
                    words.add("dive");
                } else if (part instanceof EnemyAction.TileAreaAttack area) {
                    boolean sweeping = area.damage() == ValkyrieQueenAI.sweepDamage(self.getAttackPower());
                    assertTrue(sweeping || area.damage() == self.getAttackPower(),
                        when + ": an area hit that is neither her attack nor half of it");
                    assertFalse(sweeping && !lunging, when + ": a sweep with no lane marked for it");
                    assertEquals(Set.copyOf(area.tiles()).size(), area.tiles().size(), when + ": a tile hit twice");
                    Source source = sweeping ? Source.SWEEP : diving ? Source.DIVE : Source.STORM;
                    for (GridPos member : party) {
                        if (!area.tiles().contains(member)) continue;
                        tally.hit(source);
                        hurt.add(member);
                    }
                    if (source == Source.STORM) {
                        tally.count(Count.STORM_TURNS);
                        words.add("bolts");
                    } else if (sweeping) {
                        words.add("sweep");
                    }
                } else if (part instanceof EnemyAction.ForcedMovement push) {
                    assertEquals(-1, push.targetEntityId(), when);
                    push(aimedAt(), push.dx(), push.dz(), push.tiles());
                } else {
                    throw new AssertionError(when + ": she asked the engine for " + part);
                }
            }
            return String.join("+", words);
        }

        /** Who the engine gives a strike or a shove of hers to: whoever she named, or failing that the nearest. */
        private int aimedAt() {
            GridPos named = self.getPendingStrikeTile();
            int who = named == null ? -1 : party.indexOf(named);
            return who >= 0 ? who : party.indexOf(nearestTo(self.getGridPos()));
        }

        /** Her blade, as the engine resolves it: on whoever it is for, if they are in reach of where she now stands. */
        private void swing(Source source, int throwTiles, List<GridPos> hurt) {
            int who = aimedAt();
            GridPos victim = party.get(who);
            GridPos her = self.getGridPos();
            if (victim.manhattanDistance(her) > 1) return;
            tally.hit(source);
            hurt.add(victim);
            if (throwTiles > 0) {
                push(who, Integer.signum(victim.x() - her.x()), Integer.signum(victim.z() - her.z()), throwTiles);
            }
        }

        /** Throw a player as the engine does: a tile at a time, until something is in the way. */
        private void push(int who, int dx, int dz, int tiles) {
            GridPos at = party.get(who);
            for (int i = 0; i < tiles; i++) {
                GridPos next = new GridPos(at.x() + dx, at.z() + dz);
                if (!a.isInBounds(next) || a.getOccupant(next) != null || party.contains(next)) break;
                GridTile ground = a.getTile(next);
                if (ground == null || !ground.isWalkable()) break;
                at = next;
            }
            move(who, at);
        }

        /**
         * Her crystals take their turns after hers. The engine asks the brain an entity
         * carries before the one its key names, which is how one that has burnt out goes.
         */
        void crystalsFly() {
            for (CombatEntity crystal : crystals(a)) {
                if (!crystal.isAlive() || a.getOccupant(crystal.getGridPos()) != crystal) continue;
                boolean spent = ValkyrieQueenAI.burntOut(crystal);
                GridPos aim = nearestTo(crystal.getGridPos());
                EnemyAction flight = crystal.getAiInstance() != null
                    ? crystal.getAiInstance().decideAction(crystal, a, aim) : HOMING.decideAction(crystal, a, aim);
                assertFalse(spent && !(flight instanceof EnemyAction.ProjectileMove),
                    where + ": a spent crystal lingers");
                if (!(flight instanceof EnemyAction.ProjectileMove fly)) continue;
                assertFalse(spent && !(fly.path().isEmpty() && fly.impacts()), where + ": a spent crystal flew on");
                for (GridPos step : fly.path()) {
                    // The last step of a flight that lands is onto whoever it lands on.
                    if (!a.isOccupied(step)) assertTrue(a.moveEntity(crystal, step));
                }
                if (!fly.impacts()) continue;
                CRYSTAL.onImpact(new Burst(crystal, fly.impactPos(), false, spent, false));
                if (spent) tally.count(Count.BURNT_OUT);
                burst(a, crystal);
            }
        }

        /** Her valkyries take theirs, and their blows are their own: not hers, and not marked. */
        void guardTakesItsTurns() {
            for (CombatEntity valkyrie : valkyries(a)) {
                if (!valkyrie.isAlive()) continue;
                GridPos aim = nearestTo(valkyrie.getGridPos());
                EnemyAction action = VALKYRIE.decideAction(valkyrie, a, aim);
                // Where a valkyrie means to go is its own affair, and one with no quarrel yet
                // wanders without looking: it is taken as far as the way is clear.
                List<GridPos> way = action instanceof EnemyAction.Move move ? move.path()
                    : action instanceof EnemyAction.MoveAndAttack strike ? strike.path()
                    : action instanceof EnemyAction.Teleport blink ? List.of(blink.target()) : List.of();
                for (GridPos step : way) {
                    if (a.isOccupied(step) || !a.moveEntity(valkyrie, step)) break;
                }
                boolean strikes = action instanceof EnemyAction.Attack || action instanceof EnemyAction.MoveAndAttack;
                // One that a struck crystal caught on the way in has been given its reason.
                boolean untouched = valkyrie.getCurrentHp() == valkyrie.getMaxHp();
                assertFalse(strikes && untouched && justLanded.contains(valkyrie), where + ", turn "
                    + tally.of(Count.TURNS) + ": a valkyrie nobody had touched struck on the turn it was set down");
                if (strikes && nearestTo(valkyrie.getGridPos()).manhattanDistance(valkyrie.getGridPos()) <= 1) {
                    tally.hit(Source.VALKYRIE);
                }
            }
            justLanded.clear();
        }

        /**
         * A player strikes a crystal from where they stand. It is settled on the spot, by
         * her own handler, and the engine takes the crystal off the board.
         */
        void strike(int who, CombatEntity crystal) {
            a.setPlayerGridPos(party.get(who));
            boolean wasAloft = ai.isAloft();
            tally.count(Count.STRUCK);
            assertTrue(CRYSTAL.onDeflect(new Burst(crystal, crystal.getGridPos(), true, false, wasAloft)),
                where + ": a struck crystal was left on the board");
            stand();
            if (crystal.isAlive()) burst(a, crystal);
            if (wasAloft && !ai.isAloft()) {
                tally.count(Count.GROUNDINGS);
                shown = Set.of();
                assertTrue(ai.isDown(), where + ": brought down, and not a turn getting up");
                assertNull(ai.getPendingWarning(), where + ": she fell, and left a mark on the floor");
            }
            hold();
        }

        /** A blow of the simulated player's on a valkyrie, or on her. */
        void blowAt(CombatEntity enemy) {
            enemy.takeDamage(blow);
            if (enemy == self) tally.count(Count.BLOWS_ON_HER);
            fell(enemy);
            hold();
        }

        private void fell(CombatEntity enemy) {
            if (enemy.isAlive()) return;
            if (a.getOccupant(enemy.getGridPos()) == enemy) a.removeEntity(enemy);
            if (enemy != self) tally.count(Count.KILLED);
        }

        /** What a crystal of hers is handed when it lands, burns out or is struck, in one of these fights. */
        private final class Burst implements ProjectileImpactHandler.Context {
            final CombatEntity projectile;
            final GridPos at;
            final boolean struck;
            final boolean spent;
            final boolean sheWasAloft;

            Burst(CombatEntity projectile, GridPos at, boolean struck, boolean spent, boolean sheWasAloft) {
                this.projectile = projectile;
                this.at = at;
                this.struck = struck;
                this.spent = spent;
                this.sheWasAloft = sheWasAloft;
            }

            @Override public CombatEntity projectile() { return projectile; }
            @Override public GridPos impactPos() { return at; }
            @Override public boolean redirected() { return struck; }
            @Override public GridArena arena() { return a; }
            @Override public ServerWorld world() { return null; }
            @Override public CombatManager combat() { return null; }

            /** The engine's list is the fight, not the floor: a Queen in the air is on it, under the tile she left. */
            @Override
            public List<CombatEntity> enemiesNear(int radius) {
                List<CombatEntity> near = new ArrayList<>();
                for (CombatEntity valkyrie : valkyries(a)) {
                    if (valkyrie.minDistanceTo(at) <= radius) near.add(valkyrie);
                }
                if (self.isAlive() && self.minDistanceTo(at) <= radius) near.add(self);
                return near;
            }

            @Override
            public boolean hitPlayers(int radius, int amount, CombatEffects.EffectType effect,
                                      int turns, int amplifier, String message) {
                assertFalse(struck, where + ": a crystal somebody struck hurt the party");
                assertFalse(spent, where + ": a crystal that had burnt out hurt the party");
                if (party.contains(at)) tally.hit(Source.CRYSTAL);
                return false;
            }

            @Override
            public int damage(CombatEntity enemy, int amount) {
                assertTrue(struck, where + ": a crystal nobody struck burst on one of hers");
                int dealt = enemy.takeDamage(amount);
                if (enemy != self) tally.count(Count.ON_A_VALKYRIE);
                else if (!sheWasAloft) tally.count(Count.ON_HER);
                fell(enemy);
                return dealt;
            }

            @Override
            public int pierce(CombatEntity enemy, int amount) {
                throw new AssertionError("a crystal must not go through a guard");
            }

            @Override
            public void message(String text) {
            }
        }

        // ── A simulated player, in the first place of the party ──

        /** The tiles within {@code steps} of {@code from} on foot, nearest first, each with how far it is. */
        Map<GridPos, Integer> stepsFrom(GridPos from, int steps) {
            Map<GridPos, Integer> reached = new LinkedHashMap<>();
            reached.put(from, 0);
            ArrayDeque<GridPos> queue = new ArrayDeque<>(List.of(from));
            while (!queue.isEmpty()) {
                GridPos at = queue.poll();
                if (reached.get(at) >= steps) continue;
                for (int[] way : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    GridPos next = new GridPos(at.x() + way[0], at.z() + way[1]);
                    if (reached.containsKey(next)) continue;
                    if (!next.equals(party.get(0)) && !ValkyrieQueenAI.open(a, next)) continue;
                    reached.put(next, reached.get(at) + 1);
                    queue.add(next);
                }
            }
            return reached;
        }

        /** Whether she can answer this turn: on the floor, and not getting up off it. */
        boolean dangerous() {
            return !self.isOnCeiling() && !ai.isDown();
        }

        /** A tile it is safe to end a turn on: off every mark, and not beside her while she can swing. */
        boolean safe(GridPos tile) {
            if (shown.contains(tile)) return false;
            return !dangerous() || tile.manhattanDistance(self.getGridPos()) > 1;
        }

        /** Her crystals still hunting. */
        List<CombatEntity> hunting() {
            return crystals(a).stream().filter(crystal -> !ValkyrieQueenAI.burntOut(crystal)).toList();
        }

        /** One thing to do with a strike: where to stand for it, and the crystal or the body it is for. */
        private record Act(GridPos stand, CombatEntity crystal, CombatEntity body) {}

        /** What a strike on a crystal is wanted for. */
        private static final int TO_GROUND_HER = 0, AT_HER = 1, AT_A_VALKYRIE = 2, ANYWHERE = 3;

        /**
         * The nearest tile in reach from which a crystal can be struck to do what is wanted.
         * A player's blade reaches the eight tiles round them, corners included.
         */
        private Act shot(int want, Map<GridPos, Integer> reach, int steps) {
            for (Map.Entry<GridPos, Integer> tile : reach.entrySet()) {
                GridPos stand = tile.getKey();
                // Bringing her down clears the floor. Anything else has to leave a way off it.
                if (want != TO_GROUND_HER && !endsSafely(stand, steps - tile.getValue())) continue;
                for (CombatEntity crystal : hunting()) {
                    GridPos at = crystal.getGridPos();
                    if (at.chebyshevDistanceTo(stand) != 1) continue;
                    ValkyrieQueenAI.Shot flight = ValkyrieQueenAI.shot(a, at, stand, hersOnTheFloor(), ai.diveMarked());
                    boolean good = switch (want) {
                        case TO_GROUND_HER -> flight.grounds();
                        case AT_HER -> flight.hit() == self;
                        case AT_A_VALKYRIE -> flight.hit() != null;
                        default -> true;
                    };
                    if (good) return new Act(stand, crystal, null);
                }
            }
            return null;
        }

        /** The nearest tile in reach from which {@code body} can be struck and the turn still ended safely. */
        private Act blowOn(CombatEntity body, Map<GridPos, Integer> reach, int steps, boolean mayStay) {
            for (Map.Entry<GridPos, Integer> tile : reach.entrySet()) {
                GridPos stand = tile.getKey();
                if (body.getGridPos().chebyshevDistanceTo(stand) != 1) continue;
                if (mayStay || endsSafely(stand, steps - tile.getValue())) return new Act(stand, null, body);
            }
            return null;
        }

        /** Whether this player is leading her crystals about for a dive: only once she has one to make. */
        private boolean leads(Player how) {
            return how == Player.GROUNDER && ai.phaseReached() >= 2;
        }

        /** What this player does with their next strike, or null when there is nothing worth one. */
        private Act choose(Player how, Map<GridPos, Integer> reach, int steps) {
            if (how == Player.GROUNDER && ai.isAloft()) {
                Act down = shot(TO_GROUND_HER, reach, steps);
                if (down != null) return down;
            }
            // One who means to bring her down keeps the last crystal by them for it, and leads
            // it about, unless it cannot be led. Any other is a shot at her when the line is there.
            boolean keeps = leads(how) && kited(reach) != null;
            if (!keeps || hunting().size() > 1) {
                Act atHer = shot(AT_HER, reach, steps);
                if (atHer != null) return atHer;
            }
            if (!keeps) {
                Act away = shot(AT_A_VALKYRIE, reach, steps);
                if (away == null) away = shot(ANYWHERE, reach, steps);
                if (away != null) return away;
            }
            if (killsHerGuard) {
                Act best = null;
                for (CombatEntity valkyrie : valkyries(a)) {
                    Act on = blowOn(valkyrie, reach, steps, false);
                    if (on == null) continue;
                    if (best == null || valkyrie.getCurrentHp() < best.body().getCurrentHp()) best = on;
                }
                if (best != null) return best;
            }
            if (self.isAlive() && !self.isOnCeiling()) return blowOn(self, reach, steps, ai.isDown());
            return null;
        }

        /** One turn of a simulated player: three steps and three strikes, in whatever order serves. */
        void playerTurn(Player how) {
            stand();
            int steps = STEPS;
            for (int strikes = blow > 0 ? STRIKES : 0; strikes > 0 && self.isAlive(); strikes--) {
                Map<GridPos, Integer> reach = stepsFrom(party.get(0), steps);
                Act act = choose(how, reach, steps);
                if (act == null) break;
                steps -= reach.get(act.stand());
                move(0, act.stand());
                if (act.crystal() != null) strike(0, act.crystal());
                else blowAt(act.body());
            }
            GridPos end = settle(how, stepsFrom(party.get(0), oneStep ? Math.min(steps, 1) : steps));
            move(0, end);
            if (ai.isDown() && end.manhattanDistance(self.getGridPos()) == 1) tally.count(Count.BESIDE_HER_DOWN);
        }

        private boolean endsSafely(GridPos stand, int left) {
            for (GridPos tile : stepsFrom(stand, left).keySet()) {
                if (safe(tile)) return true;
            }
            return false;
        }

        /**
         * Where someone leading a crystal about ends their turn: somewhere safe that no crystal
         * can reach on its next move, with the nearest of them as close behind as that allows,
         * and for the rest as far from her as can be. Null when no tile in reach is all that.
         */
        private GridPos kited(Map<GridPos, Integer> reach) {
            GridPos her = self.getGridPos();
            GridPos best = null;
            int bestBehind = 0;
            int bestClear = 0;
            for (GridPos tile : reach.keySet()) {
                if (!safe(tile)) continue;
                int behind = Integer.MAX_VALUE;
                for (CombatEntity crystal : hunting()) {
                    behind = Math.min(behind, tile.manhattanDistance(crystal.getGridPos()));
                }
                if (behind < ValkyrieQueenAI.CRYSTAL_HEAD_START) continue;
                int clear = self.isOnCeiling() ? 0 : tile.manhattanDistance(her);
                if (best == null || behind < bestBehind || (behind == bestBehind && clear > bestClear)) {
                    best = tile;
                    bestBehind = behind;
                    bestClear = clear;
                }
            }
            return best;
        }

        /** Where the turn ends: beside her if she is down and this player strikes, and otherwise clear of her. */
        private GridPos settle(Player how, Map<GridPos, Integer> reach) {
            GridPos her = self.getGridPos();
            if (how == Player.HUGGER) {
                if (self.isOnCeiling()) return reach.keySet().iterator().next();
                GridPos nearest = reach.keySet().iterator().next();
                for (GridPos tile : reach.keySet()) {
                    if (tile.manhattanDistance(her) == 1) return tile;
                    if (tile.manhattanDistance(her) < nearest.manhattanDistance(her)) nearest = tile;
                }
                return nearest;
            }
            if (blow > 0 && ai.isDown()) {
                for (GridPos tile : reach.keySet()) {
                    if (!shown.contains(tile) && tile.manhattanDistance(her) == 1) return tile;
                }
            }
            if (leads(how) && kited(reach) != null) return kited(reach);
            GridPos best = null;
            int bestClear = -1;
            for (GridPos tile : reach.keySet()) {
                if (!safe(tile)) continue;
                int clear = self.isOnCeiling() ? Integer.MAX_VALUE : tile.manhattanDistance(her);
                for (GridPos marked : shown) clear = Math.min(clear, tile.manhattanDistance(marked));
                // One who strikes nothing keeps away from her valkyries as well.
                if (blow == 0) {
                    for (CombatEntity valkyrie : valkyries(a)) {
                        clear = Math.min(clear, tile.manhattanDistance(valkyrie.getGridPos()));
                    }
                }
                if (clear > bestClear) {
                    bestClear = clear;
                    best = tile;
                }
            }
            if (best != null) return best;
            tally.count(Count.TRAPPED);
            return reach.keySet().iterator().next();
        }
    }

    // ── The duel on foot ──

    @Test
    @DisplayName("the duel is in thirds of her health, and a third once entered is not left")
    void phasesAreThirds() {
        assertEquals(1, ValkyrieQueenAI.phase(100, 100));
        assertEquals(1, ValkyrieQueenAI.phase(67, 100));
        assertEquals(2, ValkyrieQueenAI.phase(66, 100));
        assertEquals(2, ValkyrieQueenAI.phase(34, 100));
        assertEquals(3, ValkyrieQueenAI.phase(33, 100));
        assertEquals(3, ValkyrieQueenAI.phase(0, 100));
        // At the depth she is met, on the lines themselves.
        assertEquals(1, ValkyrieQueenAI.phase(521, 780));
        assertEquals(2, ValkyrieQueenAI.phase(520, 780));
        assertEquals(2, ValkyrieQueenAI.phase(261, 780));
        assertEquals(3, ValkyrieQueenAI.phase(260, 780));

        GridPos player = new GridPos(9, 9);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 2, 2);
        Queen ai = dueling(self, a, player);
        ai.decideAction(self, a, player);
        assertEquals(1, ai.phaseReached());
        assertFalse(ai.isInPhaseTwo());

        setHp(self, 66);
        ai.decideAction(self, a, player);
        assertEquals(2, ai.phaseReached());
        assertTrue(ai.isInPhaseTwo(), "the engine's own badge turns on the same line");

        setHp(self, 100);
        ai.decideAction(self, a, player);
        assertEquals(2, ai.phaseReached());
    }

    @Test
    @DisplayName("the duel opens with her line, once, and a thunder crystal thrown as she walks up")
    void duelOpensWithACrystal() {
        GridPos player = new GridPos(8, 5);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = dueling(self, a, player);

        Bundle opening = bundle(ai.decideAction(self, a, player));

        EnemyAction.SpawnProjectile shot = opening.shot();
        assertEquals(ValkyrieQueenAI.THUNDER_CRYSTAL, shot.projectileType());
        assertEquals(ValkyrieQueenAI.CRYSTAL_BODY, shot.entityTypeId());
        assertEquals(List.of(new GridPos(6, 6)), shot.positions(),
            "the tile toward her target is in her way and a step too near it, so it leaves her hand beside that");
        assertEquals(1, shot.directions().size());
        assertEquals(5, shot.atk());
        assertEquals(ValkyrieQueenAI.CRYSTAL_HP, shot.hp());
        EnemyAction.Move walk = assertInstanceOf(EnemyAction.Move.class, opening.onFoot(),
            "she walks up, and the blade waits for whoever is still there");
        assertEquals(List.of(new GridPos(6, 5), new GridPos(7, 5)), walk.path());

        for (int turn = 0; turn < 6; turn++) ai.decideAction(self, a, player);
        assertEquals(1, ai.times(ValkyrieQueenAI.LINE_FIGHT));
    }

    @Test
    @DisplayName("she never swings at the end of a walk: the blade is for whoever is still beside her a turn later")
    void noSwingAtTheEndOfAWalk() {
        GridPos player = new GridPos(6, 6);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = dueling(self, a, player);
        // Two crystals already hunting and no lane to her target: all she has this turn is her feet.
        crystal(a, self, 1, 1);
        crystal(a, self, 2, 1);

        EnemyAction approach = ai.decideAction(self, a, player);
        EnemyAction.Move walk = assertInstanceOf(EnemyAction.Move.class, approach,
            "one step brings her beside them, and no swing comes with it");
        assertEquals(1, walk.path().size());
        carryOut(ai, a, self, approach);
        assertEquals(1, self.getGridPos().manhattanDistance(player));

        // They stayed. Her first swing at someone standing against her is the shove.
        EnemyAction.AttackWithKnockback shove =
            assertInstanceOf(EnemyAction.AttackWithKnockback.class, ai.decideAction(self, a, player));
        assertEquals(11, shove.damage());
        assertEquals(ValkyrieQueenAI.REPULSE_TILES, shove.knockbackTiles());
        assertEquals(player, self.getPendingStrikeTile(), "and she names who it is for");

        // Still there: a plain swing, alone, through the ordinary door.
        assertEquals(11, assertInstanceOf(EnemyAction.Attack.class, ai.decideAction(self, a, player)).damage());
    }

    @Test
    @DisplayName("a launched crystal is given the homing brain, and a strike on it is settled then and there")
    void crystalHomesUntilStruck() {
        GridPos player = new GridPos(8, 5);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = dueling(self, a, player);
        ai.decideAction(self, a, player);

        CombatEntity c = crystal(a, self, 9, 5);
        ai.registerSpawnedProjectile(c.getEntityId());

        assertEquals("seeking_projectile", c.getAiKey());
        assertEquals("Thunder Crystal", c.getDisplayName());
        assertNull(c.getAiInstance(), "it flies on the engine's brain until it burns out");

        // Struck from the side she is on, it flies away from both of them, at nothing.
        Impact struck = new Impact(c, c.getGridPos(), true, a);
        struck.inTheFight = List.of(self);
        assertTrue(CRYSTAL.onDeflect(struck), "true is what has the engine take it off the board, not send it flying");
        assertTrue(struck.damaged.isEmpty());
        assertTrue(struck.playerHits.isEmpty());
        assertTrue(struck.messages.get(0).contains("flies off and is gone"));
    }

    @Test
    @DisplayName("a crystal burns out after five turns of hunting: it stops counting at once, and goes out harmlessly on its own turn")
    void crystalBurnsOut() {
        // A walk short of her and off any lane: far enough for a throw, and for nothing else.
        GridPos player = new GridPos(7, 6);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = dueling(self, a, player);
        assertEquals(5, ValkyrieQueenAI.CRYSTAL_TURNS);

        // She is left where she stands, so every third turn is a throw while her hand allows it.
        CombatEntity first = launch(ai, a, self, thrown(ai.decideAction(self, a, player)));
        assertNull(thrown(ai.decideAction(self, a, player)));
        assertNull(thrown(ai.decideAction(self, a, player)));
        CombatEntity second = launch(ai, a, self, thrown(ai.decideAction(self, a, player)));
        assertNull(thrown(ai.decideAction(self, a, player)));
        assertFalse(ValkyrieQueenAI.burntOut(first), "four turns of hunting is not five");

        // Her fifth turn since the first was thrown. It is spent.
        assertNull(thrown(ai.decideAction(self, a, player)), "the next is not yet to hand");
        assertTrue(ValkyrieQueenAI.burntOut(first));
        assertFalse(ValkyrieQueenAI.burntOut(second), "each on its own count");
        assertSame(ValkyrieQueenAI.BURN_OUT, first.getAiInstance(), "the engine asks this brain before the homing one");
        // Still hanging there, it no longer counts: with the second hunting, she throws a third.
        assertNotNull(thrown(ai.decideAction(self, a, player)), "a spent crystal no longer counts against her");

        // Its own turn comes after hers: it ends where it hangs, which the engine takes as landing there.
        EnemyAction.ProjectileMove last = assertInstanceOf(EnemyAction.ProjectileMove.class,
            ValkyrieQueenAI.BURN_OUT.decideAction(first, a, player));
        assertTrue(last.path().isEmpty());
        assertTrue(last.impacts());
        assertEquals(first.getGridPos(), last.impactPos());
        assertEquals(Set.of(), ValkyrieQueenAI.BURN_OUT.computeThreatTiles(first, a), "it threatens nothing");

        // And landing, it hurts nobody, even hanging over one of them.
        Impact fizzle = new Impact(first, player, false, a);
        CRYSTAL.onImpact(fizzle);
        assertTrue(fizzle.playerHits.isEmpty());
        assertTrue(fizzle.damaged.isEmpty());
        assertTrue(fizzle.messages.get(0).contains("burns out"));
    }

    @Test
    @DisplayName("two crystals hunting is her limit; one that is gone no longer counts")
    void crystalLimit() {
        // A walk short of her and off any lane: far enough for a throw, and for nothing else.
        GridPos player = new GridPos(7, 6);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = dueling(self, a, player);

        assertEquals(2, ValkyrieQueenAI.MAX_CRYSTALS);
        CombatEntity one = crystal(a, self, 1, 1);
        crystal(a, self, 2, 1);
        assertInstanceOf(EnemyAction.Move.class, ai.decideAction(self, a, player),
            "with two in flight she only walks");

        burst(a, one);
        assertInstanceOf(EnemyAction.Move.class, bundle(ai.decideAction(self, a, player)).onFoot(),
            "the throw does not cost her the walk");
    }

    @Test
    @DisplayName("on foot, crystals come every third turn")
    void crystalCooldown() {
        GridPos player = new GridPos(7, 6);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = dueling(self, a, player);

        // Left where she stands, a walk short of her target, so each throw goes with that
        // walk, and between throws she walks or blinks.
        List<Integer> thrown = new ArrayList<>();
        for (int turn = 1; turn <= 10; turn++) {
            EnemyAction action = ai.decideAction(self, a, player);
            assertHerShape(ai, action, "turn " + turn);
            if (action instanceof EnemyAction.CompositeAction) {
                assertInstanceOf(EnemyAction.Move.class, bundle(action).onFoot(), "turn " + turn);
                thrown.add(turn);
            } else {
                assertNull(thrown(action), "turn " + turn + ": a throw that left her standing");
            }
        }
        assertEquals(List.of(1, 4, 7, 10), thrown);
    }

    @Test
    @DisplayName("she throws on the move, and never into her own path")
    void throwsOnTheMove() {
        GridPos player = new GridPos(5, 9);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = dueling(self, a, player);

        Bundle thrown = bundle(ai.decideAction(self, a, player));

        EnemyAction.Move walk = assertInstanceOf(EnemyAction.Move.class, thrown.onFoot());
        // The engine runs one action that takes time per bundle, from this list and no other.
        assertTrue(EnemyAction.drivesTurn(walk));
        assertFalse(EnemyAction.drivesTurn(thrown.shot()));
        GridPos launch = thrown.shot().positions().get(0);
        assertFalse(walk.path().contains(launch), "the crystal would be standing where she means to walk");
        assertEquals(1, Math.max(Math.abs(launch.x() - 5), Math.abs(launch.z() - 5)), "it starts beside her");
    }

    @Test
    @DisplayName("beside her target she swings and shoves but never throws: the crystal waits, unspent, for room")
    void noThrowBesideHerTarget() {
        GridPos player = new GridPos(5, 6);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = dueling(self, a, player);

        // A crystal in her hand the whole time, and no tile beside her that gives it a head
        // start. Her first swing there is a shove, and the next shove is the third after it.
        for (int turn = 1; turn <= 4; turn++) {
            EnemyAction action = ai.decideAction(self, a, player);
            if (turn == 1 || turn == 4) {
                EnemyAction.AttackWithKnockback shove = assertInstanceOf(
                    EnemyAction.AttackWithKnockback.class, action, "turn " + turn + ": a shove");
                assertEquals(self.getAttackPower(), shove.damage());
                assertEquals(ValkyrieQueenAI.REPULSE_TILES, shove.knockbackTiles());
            } else {
                EnemyAction.Attack swing = assertInstanceOf(EnemyAction.Attack.class, action,
                    "turn " + turn + ": a swing alone, through the ordinary door");
                assertEquals(self.getAttackPower(), swing.damage());
            }
        }

        // Had any of those turns spent the cooldown, this one would be a walk and nothing more.
        player = new GridPos(5, 8);
        standPlayer(a, player);
        EnemyAction action = ai.decideAction(self, a, player);
        Bundle thrown = bundle(action);
        assertInstanceOf(EnemyAction.Move.class, thrown.onFoot());
        assertEquals(List.of(new GridPos(6, 6)), thrown.shot().positions(),
            "beside her path, three steps from her target");
        assertHeadStart(a, carryOut(ai, a, self, action), "once she has walked");
    }

    @Test
    @DisplayName("the head start is from everyone the crystal could go for: the party and her target, not a pet nearby")
    void headStartFromEveryone() {
        GridPos target = new GridPos(9, 5);
        GridPos other = new GridPos(7, 6);

        // Alone, her target four tiles down the row leaves the tile toward it far enough off.
        GridArena alone = arena(target);
        CombatEntity self = queen(alone, 5, 5);
        Queen ai = guardedAndAlone(self, alone, target);
        assertEquals(List.of(new GridPos(6, 5)), thrown(ai.decideAction(self, alone, target)).positions());

        // A second party member two steps from that tile, and one from the next she would try.
        GridArena party = arena(target);
        party.setAllPlayerGridPositions(List.of(target, other));
        self = queen(party, 5, 5);
        ai = guardedAndAlone(self, party, target);
        EnemyAction action = ai.decideAction(self, party, target);
        assertEquals(List.of(new GridPos(6, 4)), thrown(action).positions(), "the first tile clear of them both");
        assertHeadStart(party, carryOut(ai, party, self, action), "with a second party member near");

        // A pet that fights beside them is another matter. A crystal never turns aside for one,
        // so a wolf parked near her does not get to say where she throws from, or whether.
        GridArena withPet = arena(target);
        CombatEntity wolf = new CombatEntity(nextId++, "minecraft:wolf", other, 10, 3, 0, 1, 1, 3);
        wolf.setAlly(true);
        assertTrue(withPet.placeEntity(wolf), "fixture: the wolf did not fit");
        self = queen(withPet, 5, 5);
        ai = guardedAndAlone(self, withPet, target);
        action = ai.decideAction(self, withPet, target);
        assertEquals(List.of(new GridPos(6, 5)), thrown(action).positions(), "the wolf does not count");
        assertTrue(ValkyrieQueenAI.hasHeadStart(new GridPos(6, 5), List.of(target)));
    }

    @Test
    @DisplayName("the head start is counted the way a crystal flies: on open ground the rule and the homing brain agree")
    void headStartMatchesTheHomingBrain() {
        GridPos player = new GridPos(5, 5);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 11, 11);

        // Square by square and never across a corner: a diagonal neighbour is two steps off.
        assertFalse(ValkyrieQueenAI.hasHeadStart(new GridPos(6, 6), List.of(player)));
        assertFalse(ValkyrieQueenAI.hasHeadStart(new GridPos(7, 5), List.of(player)));
        assertTrue(ValkyrieQueenAI.hasHeadStart(new GridPos(7, 6), List.of(player)));
        assertTrue(ValkyrieQueenAI.hasHeadStart(new GridPos(8, 5), List.of(player)));

        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                GridPos tile = new GridPos(x, z);
                if (!ValkyrieQueenAI.open(a, tile)) continue;
                CombatEntity c = crystal(a, self, x, z);
                EnemyAction first = HOMING.decideAction(c, a, player);
                boolean lands = first instanceof EnemyAction.ProjectileMove flight && flight.impacts();
                assertEquals(!lands, ValkyrieQueenAI.hasHeadStart(tile, List.of(player)), "thrown from " + tile);
                a.removeEntity(c);
            }
        }
    }

    // ── The lunge ──

    @Test
    @DisplayName("a lunge is aimed one turn and run the next, at whoever is still at the end of the lane")
    void lungeIsTelegraphed() {
        GridPos player = new GridPos(5, 9);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = dueling(self, a, player);
        // Two crystals already hunting, and her target four tiles down a clear lane.
        crystal(a, self, 1, 1);
        crystal(a, self, 2, 1);

        EnemyAction aim = ai.decideAction(self, a, player);
        assertInstanceOf(EnemyAction.Idle.class, aim, "she holds still while she aims");
        BossWarning lane = ai.getPendingWarning();
        assertNotNull(lane);
        List<GridPos> marked = List.of(new GridPos(5, 6), new GridPos(5, 7), new GridPos(5, 8), new GridPos(5, 9));
        assertEquals(marked, lane.getAffectedTiles());
        for (GridPos tile : marked) assertArrayEquals(new int[]{0, 1}, lane.arrowAt(tile), "the arrow on " + tile);
        assertEquals(1, ai.times("levels her blade"));
        assertInstanceOf(EnemyAction.Idle.class, ai.getChargingAdvanceAction(self, a, player),
            "an advance during the wind-up would move the lane out from under its own warning");

        EnemyAction run = ai.decideAction(self, a, player);
        EnemyAction.MoveAndAttack strike = assertInstanceOf(EnemyAction.MoveAndAttack.class, run,
            "the lunge is the whole of the turn it lands on");
        assertEquals(List.of(new GridPos(5, 6), new GridPos(5, 7), new GridPos(5, 8)), strike.path());
        assertEquals(ValkyrieAI.lungeDamage(11, 3), strike.damage());
        assertEquals(player, self.getPendingStrikeTile(), "the tile it is for");
        assertNull(ai.getPendingWarning(), "the lane is spent");
    }

    /** A Queen who has just marked the four tiles from (5,6) to (5,9), from (5,5). */
    private static Queen aimedDownTheColumn(GridArena a, CombatEntity self) {
        GridPos player = new GridPos(5, 9);
        standPlayer(a, player);
        Queen ai = dueling(self, a, player);
        crystal(a, self, 1, 1);
        crystal(a, self, 2, 1);
        assertInstanceOf(EnemyAction.Idle.class, ai.decideAction(self, a, player));
        assertEquals(4, ai.laneMarked().size(), "fixture: she should have marked the lane");
        return ai;
    }

    @Test
    @DisplayName("the lunge strikes only someone standing in its lane, and nobody beside where it stops")
    void lungeHitsOnlyTheLane() {
        // They step out beside the last tile she will stand on. Not marked, so not struck.
        GridArena a = arena(new GridPos(5, 9));
        CombatEntity self = queen(a, 5, 5);
        Queen ai = aimedDownTheColumn(a, self);
        standPlayer(a, new GridPos(6, 8));
        EnemyAction.Move crossed = assertInstanceOf(EnemyAction.Move.class, ai.decideAction(self, a, new GridPos(6, 8)),
            "she crosses the lane and strikes nobody");
        assertEquals(List.of(new GridPos(5, 6), new GridPos(5, 7), new GridPos(5, 8)), crossed.path());

        // They step toward her, still in the lane: a shorter run, and the same blade.
        a = arena(new GridPos(5, 9));
        self = queen(a, 5, 5);
        ai = aimedDownTheColumn(a, self);
        standPlayer(a, new GridPos(5, 7));
        EnemyAction.MoveAndAttack early = assertInstanceOf(EnemyAction.MoveAndAttack.class,
            ai.decideAction(self, a, new GridPos(5, 7)));
        assertEquals(List.of(new GridPos(5, 6)), early.path());
        assertEquals(ValkyrieAI.lungeDamage(11, 1), early.damage());
        assertEquals(new GridPos(5, 7), self.getPendingStrikeTile());

        // Right up against her, on the first tile of it: no run at all.
        a = arena(new GridPos(5, 9));
        self = queen(a, 5, 5);
        ai = aimedDownTheColumn(a, self);
        standPlayer(a, new GridPos(5, 6));
        assertEquals(11, assertInstanceOf(EnemyAction.Attack.class,
            ai.decideAction(self, a, new GridPos(5, 6))).damage());

        // Something of hers has drifted into the lane. She stops short of it.
        a = arena(new GridPos(5, 9));
        self = queen(a, 5, 5);
        ai = aimedDownTheColumn(a, self);
        standPlayer(a, new GridPos(9, 9));
        crystal(a, self, 5, 7);
        assertEquals(List.of(new GridPos(5, 6)),
            assertInstanceOf(EnemyAction.Move.class, ai.decideAction(self, a, new GridPos(9, 9))).path());
    }

    @Test
    @DisplayName("shoved off the tile she aimed from, she does not run a lane nobody was shown")
    void lungeIsDroppedWhenSheIsMoved() {
        GridArena a = arena(new GridPos(5, 9));
        CombatEntity self = queen(a, 5, 5);
        Queen ai = aimedDownTheColumn(a, self);

        assertTrue(a.moveEntity(self, new GridPos(4, 5)));
        EnemyAction action = ai.decideAction(self, a, new GridPos(5, 9));

        assertFalse(action instanceof EnemyAction.MoveAndAttack, "the lane on the floor is not the one she would run");
        assertFalse(ai.laneMarked().contains(new GridPos(5, 6)), "and it is off the floor");
    }

    @Test
    @DisplayName("no lunge without a clear straight lane of two to six tiles")
    void lungeNeedsALane() {
        assertEquals(2, ValkyrieQueenAI.LUNGE_MIN);
        assertEquals(6, ValkyrieQueenAI.LUNGE_MAX);
        assertTrue(ValkyrieQueenAI.LUNGE_MAX > ValkyrieAI.LUNGE_MAX, "her own reach, not an ordinary valkyrie's");

        GridArena a = arena(new GridPos(11, 11));
        CombatEntity self = queen(a, 2, 5);

        assertEquals(1, ValkyrieQueenAI.lungeRun(self, a, new GridPos(4, 5)).size());
        assertEquals(5, ValkyrieQueenAI.lungeRun(self, a, new GridPos(8, 5)).size(), "six tiles off is still in reach");
        assertNull(ValkyrieQueenAI.lungeRun(self, a, new GridPos(3, 5)), "adjacent is a swing, not a lunge");
        assertNull(ValkyrieQueenAI.lungeRun(self, a, new GridPos(9, 5)), "seven tiles is too far");
        assertNull(ValkyrieQueenAI.lungeRun(self, a, new GridPos(4, 7)), "not a straight line");

        a.setTile(new GridPos(2, 7), new GridTile(TileType.OBSTACLE, null));
        assertNull(ValkyrieQueenAI.lungeRun(self, a, new GridPos(2, 9)), "a pillar in the lane");

        a.setTile(new GridPos(2, 3), new GridTile(TileType.FIRE, null));
        assertNull(ValkyrieQueenAI.lungeRun(self, a, new GridPos(2, 2)), "she will not run through fire");

        a.setAllPlayerGridPositions(List.of(new GridPos(11, 11), new GridPos(5, 5)));
        assertNull(ValkyrieQueenAI.lungeRun(self, a, new GridPos(7, 5)), "somebody else is standing in it");
    }

    // ── The blink ──

    @Test
    @DisplayName("out of reach: she blinks to the far side of her target, then has to wait to do it again")
    void blinksWhenOutOfReach() {
        GridPos player = new GridPos(10, 9);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 1, 1);
        Queen ai = dueling(self, a, player);
        // Too far to reach this turn, so the crystal goes with a plain walk.
        assertInstanceOf(EnemyAction.Move.class, bundle(ai.decideAction(self, a, player)).onFoot());

        EnemyAction action = ai.decideAction(self, a, player);
        EnemyAction.Teleport blink = assertInstanceOf(EnemyAction.Teleport.class, action,
            "the blink is only how she gets there");
        assertEquals(1, blink.target().manhattanDistance(player));
        assertEquals(new GridPos(11, 9), blink.target(), "the side of her target she was not on");

        // Left where she was, still out of reach: the blink is on cooldown, so she walks.
        assertInstanceOf(EnemyAction.Move.class, ai.decideAction(self, a, player));
    }

    @Test
    @DisplayName("the far side is the open tile beside the target furthest from her")
    void flankTile() {
        GridPos player = new GridPos(6, 6);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 2, 6);

        assertEquals(new GridPos(7, 6), ValkyrieQueenAI.flankTile(self, a, player));

        a.setTile(new GridPos(7, 6), new GridTile(TileType.OBSTACLE, null));
        GridPos second = ValkyrieQueenAI.flankTile(self, a, player);
        assertEquals(1, second.manhattanDistance(player));
        assertTrue(ValkyrieQueenAI.open(a, second));

        for (int[] d : new int[][]{{-1, 0}, {0, 1}, {0, -1}}) {
            a.setTile(new GridPos(6 + d[0], 6 + d[1]), new GridTile(TileType.OBSTACLE, null));
        }
        assertNull(ValkyrieQueenAI.flankTile(self, a, player), "walled in on every side");
    }

    // ── On the wing ──

    /** A dueling Queen at (5,5) who has just taken to the air over a target at (8,5). */
    private static Queen aloftOverEightFive(GridArena a, CombatEntity self) {
        GridPos player = new GridPos(8, 5);
        Queen ai = dueling(self, a, player);
        setHp(self, 66);
        assertInstanceOf(EnemyAction.Idle.class, ai.decideAction(self, a, player),
            "leaving the floor asks nothing of the engine");
        assertTrue(ai.isAloft(), "fixture: two thirds should have put her in the air");
        return ai;
    }

    @Test
    @DisplayName("at two thirds she takes to the air: off the floor, out of reach, and a square marked where her target stands")
    void takesWing() {
        GridPos player = new GridPos(8, 5);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = aloftOverEightFive(a, self);

        assertTrue(self.isOnCeiling());
        assertNull(a.getOccupant(new GridPos(5, 5)), "she is in nobody's way");
        assertTrue(ValkyrieQueenAI.open(a, new GridPos(5, 5)));
        assertTrue(self.isDamageImmune());
        assertEquals(ValkyrieQueenAI.ALOFT_HINT, self.getDamageImmuneHint());
        assertEquals(0, self.takeDamage(50), "nothing reaches her up there");
        assertEquals(1, ai.times("takes to the air"));

        BossWarning mark = ai.getPendingWarning();
        List<GridPos> square = mark.getAffectedTiles();
        assertEquals(9, square.size());
        for (GridPos tile : square) assertTrue(tile.chebyshevDistanceTo(player) <= 1, tile + " is not in the square");
        assertNull(mark.arrowAt(player), "the middle is where she comes down");
        assertArrayEquals(new int[]{1, -1}, mark.arrowAt(new GridPos(9, 4)), "the arrows point the way they are thrown");
        assertArrayEquals(new int[]{-1, 0}, mark.arrowAt(new GridPos(7, 5)));
        assertArrayEquals(new int[]{0, 1}, mark.arrowAt(new GridPos(8, 6)));

        // The square is where they stood. It does not follow them.
        standPlayer(a, new GridPos(11, 5));
        assertEquals(square, ai.getPendingWarning().getAffectedTiles());
        assertEquals(square, ai.diveMarked());
    }

    @Test
    @DisplayName("a square against the edge of the room is as much of it as is in the room")
    void squareAtTheEdge() {
        GridArena hall = room("hall", new GridPos(0, 0));
        assertEquals(4, ValkyrieQueenAI.square(hall, new GridPos(0, 0)).size());
        assertEquals(6, ValkyrieQueenAI.square(hall, new GridPos(0, 9)).size());
        // On the curve: (2,17) is the first tile of its row, and the row past it starts a tile further in.
        List<GridPos> curved = ValkyrieQueenAI.square(hall, new GridPos(2, 17));
        assertEquals(6, curved.size());
        for (GridPos tile : curved) assertTrue(hall.isInBounds(tile), tile + " is outside the room");
    }

    @Test
    @DisplayName("she comes down on the square: everyone in it takes her attack, and whoever she dived at is thrown two tiles out")
    void diveLands() {
        // They stood still, on the middle of it. She lands on the open tile nearest that.
        GridPos player = new GridPos(8, 5);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = aloftOverEightFive(a, self);
        List<GridPos> square = ai.diveMarked();

        EnemyAction landing = ai.decideAction(self, a, player);
        assertHerShape(ai, landing, "the landing");
        List<EnemyAction> parts = EnemyAction.flatten(landing);
        assertEquals(3, parts.size());
        EnemyAction.CeilingDrop drop = assertInstanceOf(EnemyAction.CeilingDrop.class, parts.get(0));
        assertEquals(new GridPos(7, 5), drop.landingPos());
        assertEquals(0, drop.damage(), "the landing is not the hit");
        EnemyAction.TileAreaAttack hit = assertInstanceOf(EnemyAction.TileAreaAttack.class, parts.get(1));
        assertEquals(square, hit.tiles(), "the square as it was painted, and nothing beside it");
        assertEquals(11, hit.damage());
        EnemyAction.ForcedMovement thrown = assertInstanceOf(EnemyAction.ForcedMovement.class, parts.get(2));
        assertEquals(-1, thrown.targetEntityId());
        assertEquals(1, thrown.dx(), "away from where she landed");
        assertEquals(0, thrown.dz());
        assertEquals(ValkyrieQueenAI.DIVE_THROW, thrown.tiles());
        assertEquals(player, self.getPendingStrikeTile(), "the engine is told who the throw is for");
        assertFalse(ai.isAloft());
        assertFalse(self.isDamageImmune(), "down, she can be hurt again");
        assertNull(ai.getPendingWarning(), "the square is spent");
        assertEquals(1, ai.times("dives"));

        // They stepped to a corner of it. She lands on the middle, and they are thrown out through that corner.
        a = arena(player);
        self = queen(a, 5, 5);
        ai = aloftOverEightFive(a, self);
        standPlayer(a, new GridPos(9, 6));
        parts = EnemyAction.flatten(ai.decideAction(self, a, new GridPos(9, 6)));
        assertEquals(player, assertInstanceOf(EnemyAction.CeilingDrop.class, parts.get(0)).landingPos());
        thrown = assertInstanceOf(EnemyAction.ForcedMovement.class, parts.get(2));
        assertEquals(1, thrown.dx());
        assertEquals(1, thrown.dz());

        // They left it. She lands on nobody, and nobody is thrown.
        a = arena(player);
        self = queen(a, 5, 5);
        ai = aloftOverEightFive(a, self);
        standPlayer(a, new GridPos(11, 5));
        landing = ai.decideAction(self, a, new GridPos(11, 5));
        parts = EnemyAction.flatten(landing);
        assertEquals(2, parts.size(), "a landing and its hit, on an empty square");
        carryOut(ai, a, self, landing);
        assertEquals(player, self.getGridPos());
        assertSame(self, a.getOccupant(player));
        assertFalse(self.isOnCeiling());
    }

    @Test
    @DisplayName("everyone the dive lands on is thrown: the first with the landing, the rest an action each in that same turn")
    void diveThrowsEveryone() {
        GridPos target = new GridPos(8, 5);
        GridPos corner = new GridPos(9, 6);
        GridPos clear = new GridPos(11, 9);
        GridArena a = arena(target);
        a.setAllPlayerGridPositions(List.of(target, corner, clear));
        CombatEntity self = queen(a, 5, 5);
        Queen ai = aloftOverEightFive(a, self);
        assertFalse(ai.wantsAnotherAction(self), "taking off is one action");
        // A crystal of hers thrown as she took off, far from all of this.
        CombatEntity hunting = crystal(a, self, 0, 0);
        ai.registerSpawnedProjectile(hunting.getEntityId());

        EnemyAction landing = ai.decideAction(self, a, target);
        List<EnemyAction> parts = EnemyAction.flatten(landing);
        assertEquals(3, parts.size(),
            "one throw rides with the landing, since the engine throws one player to an action");
        EnemyAction.ForcedMovement first = assertInstanceOf(EnemyAction.ForcedMovement.class, parts.get(2));
        assertEquals(target, self.getPendingStrikeTile(), "her target's");
        assertEquals(1, first.dx());
        assertEquals(0, first.dz());
        carryOut(ai, a, self, landing);

        // The engine asks whether she has more to do, and she has: the one in the corner.
        assertTrue(ai.wantsAnotherAction(self));
        self.setPendingStrikeTile(null);
        EnemyAction.ForcedMovement second =
            assertInstanceOf(EnemyAction.ForcedMovement.class, ai.decideAction(self, a, target));
        assertEquals(corner, self.getPendingStrikeTile());
        assertEquals(-1, second.targetEntityId());
        assertEquals(1, second.dx(), "out through the corner they stood in");
        assertEquals(1, second.dz());
        assertEquals(ValkyrieQueenAI.DIVE_THROW, second.tiles());
        assertFalse(ai.wantsAnotherAction(self), "and nobody after them: the third was never in the square");

        // That second action was not a turn. The next dive still comes five turns after the last,
        // and a crystal that was hunting as she came down is not a turn older for it either.
        for (int turn = 3; turn <= 5; turn++) {
            ai.decideAction(self, a, target);
            assertFalse(ai.wantsAnotherAction(self), "turn " + turn);
            assertFalse(ai.isAloft(), "turn " + turn + ": a throw was counted as a turn of its own");
        }
        assertFalse(ValkyrieQueenAI.burntOut(hunting), "four of her turns since it was thrown, not five");
        ai.decideAction(self, a, target);
        assertTrue(ai.isAloft());
        assertTrue(ValkyrieQueenAI.burntOut(hunting));
    }

    @Test
    @DisplayName("past two thirds: the dive every fifth turn, her guard called between, a third crystal, and the blink still only a blink")
    void onTheWing() {
        GridPos player = new GridPos(7, 6);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = dueling(self, a, player);
        setHp(self, 66);
        assertEquals(3, ValkyrieQueenAI.MAX_CRYSTALS_ENRAGED);
        assertEquals(5, ValkyrieQueenAI.DIVE_EVERY);
        crystal(a, self, 1, 1);
        crystal(a, self, 2, 1);

        // Up, and down again on the ground they have left.
        assertInstanceOf(EnemyAction.Idle.class, ai.decideAction(self, a, player));
        assertTrue(ai.isAloft());
        assertEquals(2, crystals(a).size(), "she throws nothing from the air");
        player = new GridPos(10, 8);
        standPlayer(a, player);
        carryOut(ai, a, self, ai.decideAction(self, a, player));
        assertEquals(new GridPos(7, 6), self.getGridPos());

        // On her feet with nobody of hers beside her: she calls one, and that is the turn.
        for (CombatEntity valkyrie : arrive(ai, a,
                assertInstanceOf(EnemyAction.SummonMinions.class, ai.decideAction(self, a, player)))) {
            beat(a, valkyrie);
        }

        // With two hunting, a third joins them.
        EnemyAction third = ai.decideAction(self, a, player);
        assertInstanceOf(EnemyAction.Move.class, bundle(third).onFoot());
        launch(ai, a, self, bundle(third).shot());

        // Out of reach on foot, so she blinks, and hurt or not it is a blink and nothing more.
        assertInstanceOf(EnemyAction.Teleport.class, ai.decideAction(self, a, player));

        // The fifth turn after the last takeoff: up again.
        assertInstanceOf(EnemyAction.Idle.class, ai.decideAction(self, a, player));
        assertTrue(ai.isAloft());
        assertEquals(2, ai.times("takes to the air"));

        // Down, with a crystal long back in her hand and three still hunting: it stays there.
        GridPos away = new GridPos(10, 2);
        standPlayer(a, away);
        carryOut(ai, a, self, ai.decideAction(self, a, away));
        assertNull(thrown(ai.decideAction(self, a, away)), "three is her limit now");
    }

    /** Take any crystal that has burnt out off the board, as its own turn would have. */
    private static void clearSpent(GridArena a) {
        for (CombatEntity crystal : crystals(a)) {
            if (ValkyrieQueenAI.burntOut(crystal)) burst(a, crystal);
        }
    }

    private static boolean calls(EnemyAction action) {
        return EnemyAction.flatten(action).stream().anyMatch(part -> part instanceof EnemyAction.SummonMinions);
    }

    @Test
    @DisplayName("from two thirds she calls her honour guard one at a time: a turn of its own, calm on landing, never more than two of hers")
    void honourGuard() {
        GridArena a = arena(new GridPos(8, 5));
        CombatEntity self = queen(a, 5, 5);
        Queen ai = aloftOverEightFive(a, self);
        assertEquals(2, ValkyrieQueenAI.MAX_GUARD);
        assertEquals(6, ValkyrieQueenAI.GUARD_EVERY);
        assertEquals(4, ValkyrieQueenAI.GUARD_EVERY_STORM);
        GridPos away = new GridPos(11, 11);
        standPlayer(a, away);
        carryOut(ai, a, self, ai.decideAction(self, a, away));

        // Her third turn past two thirds: the dive has been, and now the call.
        EnemyAction.SummonMinions call = assertInstanceOf(EnemyAction.SummonMinions.class,
            ai.decideAction(self, a, away), "the call is the whole of her move");
        assertEquals(AetherMobs.VALKYRIE, call.entityTypeId());
        assertEquals(1, call.count());
        assertEquals(1, call.positions().size());
        assertEquals(20, call.hp(), "an escort's twenty");
        assertEquals(8, call.atk(), "an escort's blade");
        assertEquals(2, call.def());
        assertEquals(1, ai.times("honour guard"));
        // It takes a turn the round it lands, like anything else set down, so it lands calm:
        // nothing that arrives may strike before anyone has seen it standing there.
        CombatEntity first = arrive(ai, a, call).get(0);
        assertFalse(first.isEnraged(), "provoked the turn it landed");

        // Her next turn sets it on them. The next call is six turns after the last, or the
        // first turn after that on which she has nothing already promised.
        int second = 0;
        for (int turn = 4; second == 0 && turn <= 12; turn++) {
            EnemyAction action = ai.decideAction(self, a, away);
            if (turn == 4) assertTrue(first.isEnraged(), "set on the party a turn after landing");
            if (calls(action)) second = turn;
            carryOut(ai, a, self, action);
            clearSpent(a);
        }
        assertTrue(second >= 3 + ValkyrieQueenAI.GUARD_EVERY && second <= 4 + ValkyrieQueenAI.GUARD_EVERY,
            "called again on turn " + second);
        assertEquals(2, valkyries(a).size());

        // Two of hers on the floor: she calls no more, however long it goes on.
        for (int turn = 0; turn < 14; turn++) {
            EnemyAction action = ai.decideAction(self, a, away);
            assertFalse(calls(action), "a third of hers");
            carryOut(ai, a, self, action);
            clearSpent(a);
        }

        // One falls, with the call long ready: another comes as soon as she has a turn free.
        beat(a, first);
        boolean again = false;
        for (int turn = 0; turn < 5 && !again; turn++) {
            EnemyAction action = ai.decideAction(self, a, away);
            again = calls(action);
            carryOut(ai, a, self, action);
            clearSpent(a);
        }
        assertTrue(again, "one of hers fell, and nobody came");
        assertEquals(3, ai.times("honour guard"));
    }

    /**
     * Hurt and on her feet at (8,5), the dive and her guard both just spent and her hand
     * full, with her target five tiles down the column from her: she takes aim at (8,10).
     */
    private static Queen hurtAndAimedDownTheColumn(GridArena a, CombatEntity self) {
        Queen ai = aloftOverEightFive(a, self);
        // Three of hers already hunting, far off, so that no throw comes before the lunge.
        crystal(a, self, 0, 0);
        crystal(a, self, 1, 0);
        crystal(a, self, 2, 0);
        GridPos target = new GridPos(8, 10);
        standPlayer(a, target);
        carryOut(ai, a, self, ai.decideAction(self, a, target));
        assertEquals(new GridPos(8, 5), self.getGridPos(), "fixture: she should have landed where she dived");
        for (CombatEntity valkyrie : arrive(ai, a,
                assertInstanceOf(EnemyAction.SummonMinions.class, ai.decideAction(self, a, target)))) {
            beat(a, valkyrie);
        }
        assertInstanceOf(EnemyAction.Idle.class, ai.decideAction(self, a, target), "she holds still to take aim");
        return ai;
    }

    @Test
    @DisplayName("from two thirds the lane is three wide: her blade for the first in the middle, half of it for anyone on the outer tiles she passes")
    void laneThreeWide() {
        GridArena a = arena(new GridPos(8, 5));
        CombatEntity self = queen(a, 5, 5);
        Queen ai = hurtAndAimedDownTheColumn(a, self);

        List<GridPos> marked = ai.laneMarked();
        assertEquals(15, marked.size(), "five tiles of lane, and the tile either side of each");
        assertEquals(15, Set.copyOf(marked).size());
        for (int z = 6; z <= 10; z++) {
            for (int x = 7; x <= 9; x++) assertTrue(marked.contains(new GridPos(x, z)), x + "," + z);
        }
        assertEquals(Set.copyOf(marked), Set.copyOf(ai.getPendingWarning().getAffectedTiles()));
        assertArrayEquals(new int[]{0, 1}, ai.getPendingWarning().arrowAt(new GridPos(7, 8)),
            "the outer tiles point the way she runs too");

        // One step aside used to be enough. She crosses the lane, her blade finds nobody,
        // and whoever is on an outer tile takes half of it.
        GridPos aside = new GridPos(9, 10);
        standPlayer(a, aside);
        EnemyAction run = ai.decideAction(self, a, aside);
        assertHerShape(ai, run, "a run with a sweep");
        List<EnemyAction> parts = EnemyAction.flatten(run);
        assertEquals(2, parts.size());
        EnemyAction.TileAreaAttack sweep = assertInstanceOf(EnemyAction.TileAreaAttack.class, parts.get(0));
        assertEquals(ValkyrieQueenAI.sweepDamage(11), sweep.damage());
        assertEquals(10, sweep.tiles().size(), "the outer tiles, and none of the middle");
        assertTrue(sweep.tiles().contains(aside));
        for (GridPos tile : sweep.tiles()) assertTrue(tile.x() != 8, tile + " is in the middle");
        EnemyAction.Move crossed = assertInstanceOf(EnemyAction.Move.class, parts.get(1));
        assertEquals(new GridPos(8, 9), crossed.path().get(crossed.path().size() - 1), "short of the tile she aimed at");
        assertNull(ai.getPendingWarning(), "the lane is spent");

        // Three of them: one still in the middle, one on an outer tile she passes on the way
        // there, and one on an outer tile further down, which her blade never reaches.
        a = arena(new GridPos(8, 5));
        self = queen(a, 5, 5);
        ai = hurtAndAimedDownTheColumn(a, self);
        GridPos middle = new GridPos(8, 8);
        GridPos outer = new GridPos(7, 7);
        GridPos beyond = new GridPos(9, 10);
        a.setPlayerGridPos(middle);
        a.setAllPlayerGridPositions(List.of(middle, outer, beyond));
        run = ai.decideAction(self, a, middle);
        assertHerShape(ai, run, "a strike with a sweep");
        parts = EnemyAction.flatten(run);
        assertEquals(2, parts.size());
        sweep = assertInstanceOf(EnemyAction.TileAreaAttack.class, parts.get(0));
        assertEquals(Set.of(new GridPos(7, 6), new GridPos(9, 6), outer, new GridPos(9, 7), new GridPos(7, 8),
            new GridPos(9, 8)), Set.copyOf(sweep.tiles()), "as far as her blade went, and no further");
        EnemyAction.MoveAndAttack strike = assertInstanceOf(EnemyAction.MoveAndAttack.class, parts.get(1));
        assertEquals(List.of(new GridPos(8, 6), new GridPos(8, 7)), strike.path());
        assertEquals(ValkyrieAI.lungeDamage(11, 2), strike.damage());
        assertEquals(middle, self.getPendingStrikeTile(), "the blade is for the one in the middle, and only them");

        // Two tiles aside is clear of it: a run, and nothing asked of the engine beside it.
        a = arena(new GridPos(8, 5));
        self = queen(a, 5, 5);
        ai = hurtAndAimedDownTheColumn(a, self);
        GridPos clear = new GridPos(6, 10);
        standPlayer(a, clear);
        assertInstanceOf(EnemyAction.Move.class, ai.decideAction(self, a, clear));
    }

    @Test
    @DisplayName("a crystal struck across the square brings her down hard: on the ground she marked, hurt for a tenth, and a turn getting up")
    void broughtDown() {
        GridPos player = new GridPos(8, 5);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = aloftOverEightFive(a, self);

        // One of hers, hunting, a tile outside the square. They step round to its far side and strike.
        CombatEntity c = crystal(a, self, 10, 5);
        ai.registerSpawnedProjectile(c.getEntityId());
        GridPos striker = new GridPos(11, 5);
        standPlayer(a, striker);
        ValkyrieQueenAI.Shot shot = ValkyrieQueenAI.shot(a, c.getGridPos(), striker, List.of(), ai.diveMarked());
        assertTrue(shot.grounds());
        assertEquals(List.of(new GridPos(9, 5)), shot.path(), "the first tile of the square in its way");
        Impact strike = new Impact(c, c.getGridPos(), true, a);
        // She is in the fight, under the tile she left, and is not there to be hit.
        strike.inTheFight = List.of(self);

        assertTrue(CRYSTAL.onDeflect(strike), "settled on the spot: left to fly, it would arrive after she had landed");
        burst(a, c);

        assertFalse(ai.isAloft());
        assertTrue(ai.isDown());
        assertFalse(self.isOnCeiling());
        assertEquals(player, self.getGridPos(), "the middle of the square, where she meant to land");
        assertSame(self, a.getOccupant(player));
        assertFalse(self.isDamageImmune(), "and open to whatever comes next");
        assertEquals(List.of(self), strike.damaged);
        assertEquals(List.of(ValkyrieQueenAI.groundingDamage(c.getAttackPower(), self.getMaxHp())), strike.damage,
            "a tenth of her: the prize for it, and twice what the same crystal does to her on the ground");
        assertNull(ai.getPendingWarning(), "the square is spent");
        assertTrue(strike.messages.get(0).contains("comes down hard"));

        // The turn she loses.
        assertInstanceOf(EnemyAction.Idle.class, ai.decideAction(self, a, striker));
        assertFalse(ai.isDown());
        assertNull(ai.getPendingWarning(), "she marks nothing from the floor");
        assertEquals(1, ai.times("gets back to her feet"));
        // And then she is in it again.
        ai.decideAction(self, a, striker);
        assertEquals(1, ai.times("gets back to her feet"));

        // At the depth she is met the tenth is the larger share by far: seventy-eight, where
        // the same crystal struck at her on the ground does thirty-nine.
        a = arena(player);
        self = deepQueen(a, new GridPos(5, 5));
        ai = dueling(self, a, player);
        setHp(self, 500);
        assertInstanceOf(EnemyAction.Idle.class, ai.decideAction(self, a, player));
        assertTrue(ai.isAloft());
        CombatEntity deep = crystal(a, self, 10, 5);
        standPlayer(a, striker);
        assertEquals(List.of(78), struck(a, deep, self).damage);
        assertTrue(ai.isDown());
    }

    @Test
    @DisplayName("grounding has to be made: only a crystal of hers, struck while she is up, on a line that crosses the square")
    void notEveryStrikeBringsHerDown() {
        GridPos player = new GridPos(8, 5);

        // Struck down a column that passes beside the square, and over the tile she left:
        // it flies off, and she is not on that tile to be hit.
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        Queen ai = aloftOverEightFive(a, self);
        CombatEntity wide = crystal(a, self, 4, 3);
        ai.registerSpawnedProjectile(wide.getEntityId());
        standPlayer(a, new GridPos(4, 2));
        Impact miss = struck(a, wide, self);
        assertTrue(miss.damaged.isEmpty());
        assertTrue(miss.messages.get(0).contains("flies off and is gone"));
        assertTrue(ai.isAloft());

        // Struck the right way with a wall between, it bursts on the wall.
        a = arena(player);
        a.setTile(new GridPos(8, 7), new GridTile(TileType.OBSTACLE, null));
        self = queen(a, 5, 5);
        ai = aloftOverEightFive(a, self);
        CombatEntity walled = crystal(a, self, 8, 9);
        ai.registerSpawnedProjectile(walled.getEntityId());
        standPlayer(a, new GridPos(8, 10));
        assertTrue(struck(a, walled, self).damaged.isEmpty());
        assertTrue(ai.isAloft());

        // Lying in the square is not enough. Struck out of it, it is only a crystal struck away.
        a = arena(player);
        self = queen(a, 5, 5);
        ai = aloftOverEightFive(a, self);
        CombatEntity lying = crystal(a, self, 9, 5);
        ai.registerSpawnedProjectile(lying.getEntityId());
        assertTrue(ai.diveMarked().contains(lying.getGridPos()));
        assertTrue(struck(a, lying, self).damaged.isEmpty(), "struck from the middle of the square, outward");
        assertTrue(ai.isAloft());

        // Struck across it, from the same square, it is enough.
        a = arena(player);
        self = queen(a, 5, 5);
        ai = aloftOverEightFive(a, self);
        CombatEntity across = crystal(a, self, 9, 6);
        ai.registerSpawnedProjectile(across.getEntityId());
        standPlayer(a, new GridPos(10, 7));
        assertEquals(List.of(self), struck(a, across, self).damaged);
        assertTrue(ai.isDown());

        // One that lands in the square by itself does nothing to her.
        a = arena(player);
        self = queen(a, 5, 5);
        ai = aloftOverEightFive(a, self);
        CombatEntity landing = crystal(a, self, 9, 4);
        ai.registerSpawnedProjectile(landing.getEntityId());
        Impact landed = new Impact(landing, player, false, a);
        landed.inTheFight = List.of(self);
        CRYSTAL.onImpact(landed);
        assertEquals(1, landed.playerHits.size(), "it is whoever stands there that it hits");
        assertTrue(landed.damaged.isEmpty());
        assertTrue(ai.isAloft());

        // Somebody else's crystal is nothing to do with her.
        a = arena(player);
        self = queen(a, 5, 5);
        ai = aloftOverEightFive(a, self);
        CombatEntity stranger = new CombatEntity(nextId++, ValkyrieQueenAI.QUEEN, new GridPos(0, 11),
            100, 11, 3, 1, 1, 3);
        CombatEntity theirs = crystal(a, stranger, 10, 5);
        standPlayer(a, new GridPos(11, 5));
        assertTrue(struck(a, theirs, self).damaged.isEmpty());
        assertTrue(ai.isAloft());

        // And on the ground there is no square. The same strike is a shot at her, for a twentieth.
        a = arena(player);
        self = queen(a, 5, 5);
        ai = dueling(self, a, player);
        ai.decideAction(self, a, player);
        CombatEntity plain = crystal(a, self, 10, 5);
        ai.registerSpawnedProjectile(plain.getEntityId());
        standPlayer(a, new GridPos(11, 5));
        Impact onHer = struck(a, plain, self);
        assertEquals(List.of(ValkyrieQueenAI.reflectedDamage(plain.getAttackPower(), self.getMaxHp(), true)),
            onHer.damage);
        assertFalse(ai.isDown());
    }

    // ── The storm ──

    @Test
    @DisplayName("at a third she calls the storm down, with nobody called to her side for it: three by three under every player, struck on her next turn")
    void theStormBreaks() {
        GridPos player = new GridPos(9, 9);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 2, 2);
        Queen ai = dueling(self, a, player);
        setHp(self, 33);

        // Said as she takes wing, which is what she had ready. It costs her no turn.
        assertInstanceOf(EnemyAction.Idle.class, ai.decideAction(self, a, player));
        assertEquals(3, ai.phaseReached());
        assertEquals(1, ai.times("calls the storm down"));
        assertTrue(ai.isAloft());
        // In the air she calls nothing: the floor shows the square she is diving at and only that.
        assertTrue(ai.stormMarked().isEmpty());
        assertEquals(Set.copyOf(ValkyrieQueenAI.square(a, player)),
            Set.copyOf(ai.getPendingWarning().getAffectedTiles()));

        // She lands with no lightning behind her, and on the ground calls the first: three
        // by three on where they stand, with no arrows.
        GridPos away = new GridPos(9, 2);
        standPlayer(a, away);
        EnemyAction landing = ai.decideAction(self, a, away);
        assertEquals(2, EnemyAction.flatten(landing).size());
        assertInstanceOf(EnemyAction.CeilingDrop.class, EnemyAction.flatten(landing).get(0));
        carryOut(ai, a, self, landing);
        List<GridPos> marked = ai.stormMarked();
        assertEquals(9, marked.size());
        assertEquals(Set.copyOf(ValkyrieQueenAI.square(a, away)), Set.copyOf(marked));
        assertEquals(Set.copyOf(marked), Set.copyOf(ai.getPendingWarning().getAffectedTiles()));
        for (GridPos tile : marked) assertNull(ai.getPendingWarning().arrowAt(tile));

        // It falls on her next turn, whatever else that turn is. Here that is the call to her
        // guard, which is the whole of her move and still not the whole of her turn.
        GridPos moved = new GridPos(5, 2);
        standPlayer(a, moved);
        EnemyAction fall = ai.decideAction(self, a, moved);
        assertHerShape(ai, fall, "lightning and the call");
        List<EnemyAction> parts = EnemyAction.flatten(fall);
        assertEquals(2, parts.size());
        EnemyAction.TileAreaAttack bolts = assertInstanceOf(EnemyAction.TileAreaAttack.class, parts.get(0));
        assertEquals(marked, bolts.tiles(), "where it was marked, not where they went");
        assertEquals(11, bolts.damage());
        EnemyAction.SummonMinions call = assertInstanceOf(EnemyAction.SummonMinions.class, parts.get(1));
        assertEquals(1, call.count());
        for (CombatEntity valkyrie : arrive(ai, a, call)) {
            assertFalse(valkyrie.isEnraged(), "provoked the turn it landed");
            beat(a, valkyrie);
        }
        // And the next is already marked, where they stand now.
        assertEquals(Set.copyOf(ValkyrieQueenAI.square(a, moved)), Set.copyOf(ai.stormMarked()));

        // In the storm the call comes round in four turns, not six. She is left standing
        // where she is, off any lane of theirs, so only the dive comes between.
        int next = 0;
        for (int turn = 4; next == 0 && turn <= 12; turn++) {
            EnemyAction action = ai.decideAction(self, a, moved);
            if (calls(action)) next = turn;
            for (EnemyAction part : EnemyAction.flatten(action)) {
                if (part instanceof EnemyAction.CeilingDrop) carryOut(ai, a, self, part);
            }
        }
        assertTrue(next >= 3 + ValkyrieQueenAI.GUARD_EVERY_STORM && next < 3 + ValkyrieQueenAI.GUARD_EVERY,
            "called again on turn " + next);
        assertEquals(1, ai.times("calls the storm down"), "said the once");
    }

    @Test
    @DisplayName("the storm breaks with no guard called on that turn, even with the call ready: it comes the turn after")
    void theStormBreaksAlone() {
        GridArena a = arena(new GridPos(8, 5));
        CombatEntity self = queen(a, 5, 5);
        Queen ai = aloftOverEightFive(a, self);
        GridPos away = new GridPos(11, 11);
        standPlayer(a, away);
        carryOut(ai, a, self, ai.decideAction(self, a, away));

        // On her feet past two thirds, the dive just spent and nobody of hers on the floor:
        // this would be her call. But she is at a third now, and the turn is the storm's.
        setHp(self, 33);
        EnemyAction breaks = ai.decideAction(self, a, away);
        assertFalse(calls(breaks), "a valkyrie called on the turn the storm broke");
        assertEquals(1, ai.times("calls the storm down"));
        assertEquals(0, ai.times("honour guard"));
        assertEquals(Set.copyOf(ValkyrieQueenAI.square(a, away)), Set.copyOf(ai.stormMarked()),
            "the first strike is marked on that same turn");

        EnemyAction after = ai.decideAction(self, a, away);
        assertHerShape(ai, after, "the call, the turn after");
        assertTrue(calls(after));
        assertInstanceOf(EnemyAction.TileAreaAttack.class, EnemyAction.flatten(after).get(0));
    }

    @Test
    @DisplayName("one strike over all the marked ground: nobody takes two bolts, and whoever her blade is for takes none")
    void oneStrikeOverTheUnion() {
        GridPos near = new GridPos(5, 6);
        GridPos far = new GridPos(5, 8);
        GridArena a = arena(near);
        a.setAllPlayerGridPositions(List.of(near, far));
        CombatEntity self = queen(a, 5, 5);
        Queen ai = dueling(self, a, near);
        setHp(self, 33);

        // The storm breaks as she takes wing over the nearer of them, who then gets clear.
        assertInstanceOf(EnemyAction.Idle.class, ai.decideAction(self, a, near));
        near = new GridPos(3, 8);
        a.setPlayerGridPos(near);
        a.setAllPlayerGridPositions(List.of(near, far));
        carryOut(ai, a, self, ai.decideAction(self, a, far));
        assertEquals(new GridPos(5, 6), self.getGridPos());

        // Down, she calls the first strike. Their two squares share a column of three.
        List<GridPos> called = ai.stormMarked();
        assertEquals(15, called.size(), "eighteen tiles of square, and the three they share counted once");
        assertEquals(15, Set.copyOf(called).size());

        // It falls with her call to her guard, as one hit over all of it.
        List<EnemyAction> parts = EnemyAction.flatten(ai.decideAction(self, a, far));
        EnemyAction.TileAreaAttack first = assertInstanceOf(EnemyAction.TileAreaAttack.class, parts.get(0));
        assertEquals(called, first.tiles());
        for (CombatEntity valkyrie : arrive(ai, a,
                assertInstanceOf(EnemyAction.SummonMinions.class, parts.get(1)))) {
            beat(a, valkyrie);
        }
        List<GridPos> next = ai.stormMarked();
        assertEquals(15, next.size());

        // One of them walks up to her, onto ground the other one's square has marked.
        near = new GridPos(5, 7);
        assertTrue(next.contains(near));
        a.setPlayerGridPos(near);
        a.setAllPlayerGridPositions(List.of(near, far));
        EnemyAction turn = ai.decideAction(self, a, near);
        assertHerShape(ai, turn, "blade and lightning in one turn");
        parts = EnemyAction.flatten(turn);
        EnemyAction.TileAreaAttack bolts = assertInstanceOf(EnemyAction.TileAreaAttack.class, parts.get(0));
        assertFalse(bolts.tiles().contains(near), "her blade is for that tile, so her lightning is not");
        assertEquals(14, bolts.tiles().size());
        assertTrue(bolts.tiles().contains(far));
        EnemyAction.AttackWithKnockback shove = assertInstanceOf(EnemyAction.AttackWithKnockback.class, parts.get(1));
        assertEquals(ValkyrieQueenAI.REPULSE_TILES, shove.knockbackTiles());
        assertEquals(near, self.getPendingStrikeTile());

        // Still beside her the turn after: a swing this time, and in a bundle it is a shove of
        // no tiles, which is the one shape the engine takes through the ordinary melee path.
        parts = EnemyAction.flatten(ai.decideAction(self, a, near));
        assertInstanceOf(EnemyAction.TileAreaAttack.class, parts.get(0));
        EnemyAction.AttackWithKnockback swing = assertInstanceOf(EnemyAction.AttackWithKnockback.class, parts.get(1));
        assertEquals(0, swing.knockbackTiles());
        assertEquals(11, swing.damage());
        assertTrue(EnemyAction.drivesTurn(swing));
    }

    // ── What she shows, and what she holds herself to ──

    private static GridPos farthestOpenTile(GridArena a, CombatEntity self) {
        GridPos me = self.getGridPos();
        GridPos best = null;
        for (GridPos tile : floorOf(a)) {
            if (!ValkyrieQueenAI.open(a, tile)) continue;
            if (best == null || me.manhattanDistance(tile) > me.manhattanDistance(best)) best = tile;
        }
        return best;
    }

    /** The tile {@code tiles} down a clear row or column from her, or null when no lane is clear. */
    private static GridPos downALane(GridArena a, CombatEntity self, int tiles) {
        GridPos me = self.getGridPos();
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            boolean clear = true;
            for (int i = 1; i <= tiles && clear; i++) {
                clear = ValkyrieQueenAI.open(a, new GridPos(me.x() + d[0] * i, me.z() + d[1] * i));
            }
            if (clear) return new GridPos(me.x() + d[0] * tiles, me.z() + d[1] * tiles);
        }
        return null;
    }

    /**
     * Forty-eight turns against a party that takes no care at all, a third of them in each
     * third of her health. It stands down her lanes, steps one tile out of a lane three wide
     * as if that were enough, walks up to her, runs for the far wall and now and then hits
     * whatever crystal is beside it, on a timetable that has nothing to do with what is on
     * the floor. Whatever that walks into, the fight holds her to it. Nobody touches her
     * valkyries, but that the ones she has fall as the storm breaks, so that more are called.
     */
    private static void carelessFight(String room, GridPos start, GridPos second, Set<String> seen) {
        Fight fight = second == null ? Fight.inRoom(room, start) : Fight.inRoom(room, start, second);
        for (int turn = 1; turn <= 48; turn++) {
            if (turn == 17) setHp(fight.self, healthIn(fight.self, 2));
            if (turn == 33) {
                setHp(fight.self, healthIn(fight.self, 3));
                for (CombatEntity valkyrie : valkyries(fight.a)) beat(fight.a, valkyrie);
            }
            List<GridPos> lane = fight.ai.laneMarked();
            // Past six tiles it is three wide, and its last tile is an outer one.
            GridPos outer = lane.size() > ValkyrieQueenAI.LUNGE_MAX ? lane.get(lane.size() - 1) : null;
            GridPos to = null;
            if (outer != null && ValkyrieQueenAI.open(fight.a, outer)) to = outer;
            else if (turn % 5 == 0) to = farthestOpenTile(fight.a, fight.self);
            else if (turn % 7 == 3 && !fight.self.isOnCeiling()) to = openTileBeside(fight.a, fight.self);
            else if (turn % 2 == 0 && !fight.self.isOnCeiling()) to = downALane(fight.a, fight.self, 3 + turn % 3);
            if (to != null) fight.move(0, to);
            if (turn % 4 == 1) {
                for (CombatEntity crystal : fight.hunting()) {
                    if (crystal.getGridPos().chebyshevDistanceTo(fight.party.get(0)) == 1) {
                        fight.strike(0, crystal);
                        break;
                    }
                }
            }
            fight.enemyPhase();
        }
        seen.addAll(fight.did);
    }

    @Test
    @DisplayName("in both rooms, wherever a careless party stands: every hit of hers is on a mark she showed, or on someone beside her")
    void honestEverywhere() {
        Set<String> seen = new TreeSet<>();
        for (String room : ROOMS) {
            GridArena plan = room(room, new GridPos(0, 0));
            List<GridPos> floor = floorOf(plan);
            int every = room.equals("hall") ? 1 : 3;
            for (int i = 0; i < floor.size(); i += every) {
                if (floor.get(i).equals(middleOf(plan))) continue;
                carelessFight(room, floor.get(i), null, seen);
            }
            // And with a second player who never moves, for the marks and the hits that are not her target's.
            for (int i = 0; i < floor.size(); i += 7 * every) {
                GridPos second = new GridPos(middleOf(plan).x() - 2, middleOf(plan).z() + 3);
                if (floor.get(i).equals(middleOf(plan)) || floor.get(i).equals(second)) continue;
                carelessFight(room, floor.get(i), second, seen);
            }
        }
        // Every turn was held to her word as it came. This is that the turns worth holding did come.
        for (String word : List.of("throw+walk", "aim", "lunge", "run", "blink", "shove", "swing",
                "air", "dive", "summon", "sweep+run", "air+bolts", "bolts+swing", "bolts+shove", "bolts+walk",
                "bolts+summon")) {
            assertTrue(seen.contains(word), "the sweep never reached '" + word + "', only " + seen);
        }
    }

    /** A kind of simulated player, and how hard it hits when it hits at all. */
    private record Kind(String name, Player how, int strikesAGuardTakes, boolean killsHerGuard, boolean oneStep) {
        Fight in(String room, GridPos start) {
            Fight fight = Fight.inRoom(room, start);
            fight.blow = strikes() ? blowThatKillsAGuardIn(strikesAGuardTakes) : 0;
            fight.killsHerGuard = killsHerGuard;
            fight.oneStep = oneStep;
            return fight;
        }

        boolean strikes() {
            return strikesAGuardTakes > 0;
        }
    }

    /**
     * The players the fight is measured against. Two strengths for the ones who strike,
     * since what a real player hits for at this depth is not known here: one that kills a
     * valkyrie of her honour guard in four strikes, and one that needs eight. And two who
     * play it wrong on purpose: one who never turns to her valkyries, and one who still
     * thinks a single step clears a mark.
     */
    private static final List<Kind> KINDS = List.of(
        new Kind("EVADER", Player.EVADER, 0, false, false),
        new Kind("ANSWERER/4", Player.ANSWERER, 4, true, false),
        new Kind("ANSWERER/8", Player.ANSWERER, 8, true, false),
        new Kind("GROUNDER/4", Player.GROUNDER, 4, true, false),
        new Kind("GROUNDER/8", Player.GROUNDER, 8, true, false),
        new Kind("SPARES-HER-GUARD/4", Player.ANSWERER, 4, false, false),
        new Kind("ONE-STEP/4", Player.ANSWERER, 4, true, true),
        new Kind("HUGGER", Player.HUGGER, 0, false, false));

    /** Rounds after which a whole fight is called off, with her still standing. */
    private static final int LONGEST_FIGHT = 300;

    @Test
    @DisplayName("a whole fight in each room against each kind of player who strikes: every turn keeps her shapes and her word")
    void wholeFights() {
        for (String room : ROOMS) {
            for (Kind kind : KINDS) {
                if (!kind.strikes()) continue;
                Fight fight = kind.in(room, new GridPos(1, 1));
                // The count as each third of her health began, to tell the thirds apart afterwards.
                Tally[] began = new Tally[5];
                int[] beganAt = new int[5];
                began[1] = new Tally();
                int reached = 1;
                int rounds = 0;
                while (fight.self.isAlive() && rounds < LONGEST_FIGHT) {
                    fight.playerTurn(kind.how());
                    if (!fight.self.isAlive()) break;
                    int third = ValkyrieQueenAI.phase(fight.self.getCurrentHp(), fight.self.getMaxHp());
                    for (; reached < third; reached++) {
                        began[reached + 1] = fight.tally.copy();
                        beganAt[reached + 1] = rounds;
                    }
                    fight.enemyPhase();
                    rounds++;
                }
                for (; reached < 4; reached++) {
                    began[reached + 1] = fight.tally.copy();
                    beganAt[reached + 1] = rounds;
                }
                String who = kind.name() + " in the " + room;
                boolean fell = !fight.self.isAlive();
                // The promise itself: off every mark and not beside her, her turn does not hurt you.
                assertTrue(fight.tally.fromHer() <= fight.tally.of(Count.TRAPPED),
                    who + ": hurt by her " + fight.tally.fromHer() + " times with a safe tile in reach");
                // How long it takes is for whoever is tuning her. That it ends, and that she
                // was seen through all three thirds on the way, is not.
                assertTrue(fell, who + ": she was still standing after " + rounds + " rounds");
                assertEquals(3, fight.ai.phaseReached(), who);
                assertTrue(fight.tally.of(Count.TAKEOFFS) > 0, who + ": she never took wing");
                for (int third = 1; third <= 3; third++) {
                    System.out.println("QUEEN whole fight, " + who + ", third " + third + ": "
                        + (beganAt[third + 1] - beganAt[third]) + " rounds. "
                        + began[third + 1].since(began[third]).line(false));
                }
                System.out.println("QUEEN whole fight, " + who + ", in all: " + rounds + " rounds, "
                    + (fell ? "she fell. " : "she still had " + fight.self.getCurrentHp() + ". ")
                    + fight.tally.line(false));
            }
        }
    }

    private static List<GridPos> startsIn(String room) {
        return room.equals("hall")
            ? List.of(new GridPos(1, 1), new GridPos(8, 1), new GridPos(1, 9), new GridPos(5, 15))
            : List.of(new GridPos(1, 1), new GridPos(23, 19), new GridPos(12, 1), new GridPos(23, 10));
    }

    /**
     * Not a test of what the numbers are. They are printed, for whoever is tuning the fight,
     * and every turn of it is held to her word like any other.
     *
     * <p>Each kind of player, for 200 turns in each third of her health, from each of four
     * places in each room, her health put back after everything that touches it so that she
     * stays in the third being measured.
     */
    @Test
    @DisplayName("and the numbers, for whoever is tuning her: each kind of player, 200 turns in each third, in both rooms")
    void theNumbers() {
        for (int phase = 1; phase <= 3; phase++) {
            for (Kind kind : KINDS) {
                Tally all = new Tally();
                for (String room : ROOMS) {
                    for (GridPos start : startsIn(room)) {
                        Fight fight = kind.in(room, start);
                        fight.heldAt = healthIn(fight.self, phase);
                        fight.hold();
                        for (int turn = 0; turn < 200; turn++) {
                            fight.playerTurn(kind.how());
                            fight.enemyPhase();
                        }
                        assertEquals(phase, fight.ai.phaseReached(), "fixture: she left the third being measured");
                        all.add(fight.tally);
                    }
                }
                String who = "phase " + phase + ", " + kind.name();
                System.out.println("QUEEN " + who + ": " + all.line(true));
                if (kind.how() != Player.HUGGER) {
                    assertTrue(all.fromHer() <= all.of(Count.TRAPPED),
                        who + ": hurt by her " + all.fromHer() + " times with a safe tile in reach");
                }
            }
        }
    }

    @Test
    @DisplayName("she is one tile wide")
    void footprint() {
        assertEquals(1, new ValkyrieQueenAI().getGridSize());
    }

    // ── The thunder crystal's landing ──

    /** An impact as the engine describes it, recording what the handler asks for. */
    private static final class Impact implements ProjectileImpactHandler.Context {
        final CombatEntity projectile;
        final GridPos at;
        final boolean redirected;
        final GridArena arena;
        /** Every enemy in the fight, as the engine keeps them: on the floor or, like a Queen in the air, off it. */
        List<CombatEntity> inTheFight = List.of();
        final List<int[]> playerHits = new ArrayList<>();
        final List<CombatEntity> damaged = new ArrayList<>();
        final List<Integer> damage = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        /** What each hit comes to after the target's guard and armor. Null means all of it. */
        Integer dealt;

        Impact(CombatEntity projectile, GridPos at, boolean redirected, GridArena arena) {
            this.projectile = projectile;
            this.at = at;
            this.redirected = redirected;
            this.arena = arena;
        }

        @Override public CombatEntity projectile() { return projectile; }
        @Override public GridPos impactPos() { return at; }
        @Override public boolean redirected() { return redirected; }
        @Override public GridArena arena() { return arena; }
        @Override public ServerWorld world() { return null; }
        @Override public CombatManager combat() { return null; }

        @Override
        public List<CombatEntity> enemiesNear(int radius) {
            return inTheFight.stream().filter(e -> e.isAlive() && e.minDistanceTo(at) <= radius).toList();
        }

        @Override
        public boolean hitPlayers(int radius, int amount, CombatEffects.EffectType effect,
                                  int turns, int amplifier, String message) {
            assertNull(effect);
            // The engine fills these in. A stray percent sign would throw there, mid-fight.
            assertNotNull(String.format(message, "you", amount));
            playerHits.add(new int[]{radius, amount});
            return false;
        }

        @Override
        public int damage(CombatEntity enemy, int amount) {
            damaged.add(enemy);
            damage.add(amount);
            return dealt != null ? dealt : amount;
        }

        @Override
        public int pierce(CombatEntity enemy, int amount) {
            throw new AssertionError("a crystal must not go through a guard");
        }

        @Override
        public void message(String text) {
            messages.add(text);
        }
    }

    @Test
    @DisplayName("a crystal that arrives hits whoever is on that tile, for what it was thrown with")
    void crystalStrikesThePlayer() {
        GridPos player = new GridPos(8, 5);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        CombatEntity c = crystal(a, self, 7, 5);
        Impact impact = new Impact(c, player, false, a);

        new ValkyrieQueenAI.ThunderCrystal().onImpact(impact);

        assertEquals(1, impact.playerHits.size());
        assertEquals(0, impact.playerHits.get(0)[0], "the one tile, not a blast");
        assertEquals(c.getAttackPower(), impact.playerHits.get(0)[1]);
        assertTrue(impact.damaged.isEmpty());
    }

    /** Strike {@code c} from where the arena has its player standing, with these of hers in the fight. */
    private static Impact struck(GridArena a, CombatEntity c, CombatEntity... hers) {
        Impact strike = new Impact(c, c.getGridPos(), true, a);
        strike.inTheFight = List.of(hers);
        assertTrue(new ValkyrieQueenAI.ThunderCrystal().onDeflect(strike),
            "a strike is settled as it is made, whatever it finds");
        return strike;
    }

    @Test
    @DisplayName("a struck crystal flies away from the striker on the nearest of the eight ways")
    void struckAwayOnEightWays() {
        GridPos c = new GridPos(5, 5);
        assertArrayEquals(new int[]{1, 0}, ValkyrieQueenAI.knockedAway(c, new GridPos(4, 5)));
        assertArrayEquals(new int[]{0, -1}, ValkyrieQueenAI.knockedAway(c, new GridPos(5, 6)));
        assertArrayEquals(new int[]{1, 1}, ValkyrieQueenAI.knockedAway(c, new GridPos(4, 4)), "corner to corner");
        assertArrayEquals(new int[]{-1, 1}, ValkyrieQueenAI.knockedAway(c, new GridPos(6, 4)));
        // From further off, an arrow's line: whichever of the eight is nearest to it.
        assertArrayEquals(new int[]{1, 0}, ValkyrieQueenAI.knockedAway(c, new GridPos(1, 4)));
        assertArrayEquals(new int[]{1, 1}, ValkyrieQueenAI.knockedAway(c, new GridPos(2, 3)));
        assertArrayEquals(new int[]{1, 1}, ValkyrieQueenAI.knockedAway(c, new GridPos(3, 4)),
            "two across and one up is nearer the corner");
        assertArrayEquals(new int[]{0, -1}, ValkyrieQueenAI.knockedAway(c, c),
            "struck from its own tile, which cannot happen");
    }

    @Test
    @DisplayName("a struck crystal bursts on the first of hers its line comes within a tile of, however far, and never on the party")
    void aStruckCrystalIsAShot() {
        // Her on the line itself, six tiles down it, with a second player standing in the way.
        GridPos player = new GridPos(11, 5);
        GridArena a = arena(player);
        a.setAllPlayerGridPositions(List.of(player, new GridPos(7, 5)));
        CombatEntity self = queen(a, 3, 5);
        CombatEntity c = crystal(a, self, 10, 5);
        Impact strike = struck(a, c, self);
        assertTrue(strike.playerHits.isEmpty(), "it passes over anyone who is not hers");
        assertEquals(List.of(self), strike.damaged);
        assertEquals(List.of(ValkyrieQueenAI.reflectedDamage(c.getAttackPower(), self.getMaxHp(), true)),
            strike.damage);
        assertTrue(strike.messages.get(0).contains("bursts on"));

        // A tile off the line is near enough, and two tiles off is not.
        a = arena(player);
        self = queen(a, 3, 6);
        assertEquals(List.of(self), struck(a, crystal(a, self, 10, 5), self).damaged);
        a = arena(player);
        self = queen(a, 3, 7);
        Impact wide = struck(a, crystal(a, self, 10, 5), self);
        assertTrue(wide.damaged.isEmpty());
        assertTrue(wide.messages.get(0).contains("flies off and is gone"));

        // Behind the striker is not on the line at all.
        a = arena(new GridPos(5, 5));
        self = queen(a, 2, 5);
        assertTrue(struck(a, crystal(a, self, 6, 5), self).damaged.isEmpty());

        // The first of hers on it: the valkyrie nearer the crystal, and she is not touched.
        a = arena(player);
        self = queen(a, 3, 5);
        CombatEntity valkyrie = new CombatEntity(nextId++, AetherMobs.VALKYRIE, new GridPos(7, 4), 20, 8, 2, 1, 1, 3);
        assertTrue(a.placeEntity(valkyrie));
        assertEquals(List.of(valkyrie), struck(a, crystal(a, self, 10, 5), self, valkyrie).damaged);

        // A wall on the line stops it short of her.
        a = arena(player);
        a.setTile(new GridPos(6, 5), new GridTile(TileType.OBSTACLE, null));
        self = queen(a, 3, 5);
        assertTrue(struck(a, crystal(a, self, 10, 5), self).damaged.isEmpty());

        // Corner to corner it flies the diagonal.
        a = arena(new GridPos(10, 10));
        self = queen(a, 4, 5);
        assertEquals(List.of(self), struck(a, crystal(a, self, 9, 9), self).damaged, "(5,5) is on its line, beside her");
    }

    @Test
    @DisplayName("a guarded Queen is told about the crystal and takes nothing from it")
    void crystalOnAGuardedQueen() {
        GridArena a = arena(new GridPos(8, 5));
        CombatEntity self = queen(a, 5, 5);
        CombatEntity c = crystal(a, self, 7, 5);
        Impact strike = new Impact(c, c.getGridPos(), true, a);
        strike.inTheFight = List.of(self);
        strike.dealt = 0;

        assertTrue(new ValkyrieQueenAI.ThunderCrystal().onDeflect(strike));

        assertEquals(1, strike.damaged.size(), "asked through the door that honours the guard");
        assertTrue(strike.messages.get(0).contains("does nothing"));
    }

    @Test
    @DisplayName("a crystal struck at a valkyrie with the health to shrug off its blow takes a third of it instead")
    void crystalBurstsOnAValkyrie() {
        GridArena a = arena(new GridPos(3, 6));
        CombatEntity self = queen(a, 9, 9);
        // The valkyrie of a Queen with three times her health: sixty, where the dungeon's has twenty.
        CombatEntity valkyrie = new CombatEntity(nextId++, AetherMobs.VALKYRIE, new GridPos(3, 2),
            ValkyrieQueenAI.escortHp(300), 8, 2, 1, 1, 3);
        Impact strike = struck(a, crystal(a, self, 3, 5), self, valkyrie);

        assertTrue(strike.playerHits.isEmpty());
        assertEquals(List.of(valkyrie), strike.damaged);
        assertEquals(List.of(20), strike.damage, "a third of sixty, where twice the crystal's five is ten");
    }

    @Test
    @DisplayName("lightning doubles on a Soaked target, hers included")
    void crystalOnASoakedValkyrie() {
        GridArena a = arena(new GridPos(3, 6));
        CombatEntity self = queen(a, 9, 9);
        CombatEntity valkyrie = new CombatEntity(nextId++, AetherMobs.VALKYRIE, new GridPos(3, 2), 20, 8, 2, 1, 1, 3);
        valkyrie.setSoakedTurns(2);
        CombatEntity c = crystal(a, self, 3, 5);

        assertEquals(List.of(2 * ValkyrieQueenAI.reflectedDamage(c.getAttackPower(), 20, false)),
            struck(a, c, self, valkyrie).damage);
    }

    @Test
    @DisplayName("a crystal sent astray some other way lands on nobody")
    void crystalAstray() {
        GridPos player = new GridPos(8, 5);
        GridArena a = arena(player);
        CombatEntity self = queen(a, 5, 5);
        CombatEntity c = crystal(a, self, 7, 5);
        Impact impact = new Impact(c, player, true, a);
        impact.inTheFight = List.of(self);

        new ValkyrieQueenAI.ThunderCrystal().onImpact(impact);

        assertTrue(impact.playerHits.isEmpty());
        assertTrue(impact.damaged.isEmpty());
    }

    // ── Her last words, and the wiring ──

    @Test
    @DisplayName("her farewell is owed once, by her, and only when she is dead")
    void farewellIsOwedOnce() {
        GridArena a = arena(new GridPos(8, 5));
        CombatEntity self = queen(a, 5, 5);
        assertFalse(ValkyrieQueenAI.owesFarewell(self), "she is still standing");
        assertFalse(ValkyrieQueenAI.owesFarewell(null));

        self.takeDamage(9999);
        assertTrue(ValkyrieQueenAI.owesFarewell(self));

        CombatEntity valkyrie = new CombatEntity(nextId++, AetherMobs.VALKYRIE, new GridPos(1, 1), 20, 8, 2, 1, 1, 3);
        valkyrie.takeDamage(9999);
        assertFalse(ValkyrieQueenAI.owesFarewell(valkyrie), "a valkyrie is not the Queen");

        self.setAiMemory("valkyrie_queen_farewell", 1);
        assertFalse(ValkyrieQueenAI.owesFarewell(self), "said already");
    }

    @Test
    @DisplayName("asked for her last words she gives her line, under her own name, the once")
    void lastWords() {
        GridArena a = arena(new GridPos(8, 5));
        CombatEntity self = queen(a, 5, 5);
        ValkyrieQueenAI ai = new ValkyrieQueenAI();
        assertNull(ai.getLastWords(self), "she is still standing");

        self.takeDamage(9999);
        assertEquals(ValkyrieQueenAI.voice(self.getDisplayName(), ValkyrieQueenAI.LINE_DEFEATED),
            ai.getLastWords(self));
        assertFalse(ValkyrieQueenAI.owesFarewell(self));
        assertNull(ai.getLastWords(self), "said already");

        CombatEntity valkyrie = new CombatEntity(nextId++, AetherMobs.VALKYRIE, new GridPos(1, 1), 20, 8, 2, 1, 1, 3);
        valkyrie.takeDamage(9999);
        assertNull(ai.getLastWords(valkyrie), "a valkyrie is not the Queen");
    }

    @Test
    @DisplayName("she speaks the way the Aether prints her")
    void voice() {
        assertEquals("§e[The Valkyrie Queen]§f: " + ValkyrieQueenAI.LINE_FIGHT,
            ValkyrieQueenAI.voice("The Valkyrie Queen", ValkyrieQueenAI.LINE_FIGHT));
    }

    @Test
    @DisplayName("registering wires the brain, the traits and the crystal, and can be asked twice")
    void registers() {
        ValkyrieQueenAI.register();
        ValkyrieQueenAI.register();

        assertEquals("boss:aether_silver_dungeon", ValkyrieQueenAI.BOSS_KEY);
        assertInstanceOf(ValkyrieQueenAI.class, AIRegistry.get(ValkyrieQueenAI.BOSS_KEY));
        // Every fight gets a Queen of its own: the gate is state, and must not be shared.
        assertNotSame(AIRegistry.createFresh(ValkyrieQueenAI.BOSS_KEY),
            AIRegistry.createFresh(ValkyrieQueenAI.BOSS_KEY));

        assertNotNull(ProjectileImpactRegistry.get(ValkyrieQueenAI.THUNDER_CRYSTAL));
        assertTrue(ProjectileImpactRegistry.isDeflectable(ValkyrieQueenAI.THUNDER_CRYSTAL));

        List<MobTrait> traits = MobTraits.forType(ValkyrieQueenAI.QUEEN, ValkyrieQueenAI.BOSS_KEY, true, false, false);
        assertTrue(traits.contains(MobTraits.ETHEREAL), "she blinks");
        assertTrue(traits.contains(MobTraits.BERZERKER), "she lunges down a lane");
        assertTrue(traits.contains(MobTraits.FORCEFUL), "her shove and her dive both throw");
        assertTrue(traits.contains(MobTraits.ACROBATIC), "she leaves the floor");
    }
}
