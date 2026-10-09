package com.crackedgames.craftics.compat.aether.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.MobTrait;
import com.crackedgames.craftics.combat.MobTraits;
import com.crackedgames.craftics.combat.ai.AIRegistry;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.combat.ai.boss.BossWarning;
import com.crackedgames.craftics.compat.aether.ai.SentryAI;
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
import java.util.WeakHashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the Slider decides to do, turn by turn, on a bare grid.
 *
 * <p>Nothing here can spawn the Aether's entity or resolve an action: the slide itself, the
 * strike on arrival, the throw and the raising of a brick are the engine's, and belong to the
 * in-game checklist. What is pinned is what the AI <em>asks for</em>, which is where its rules
 * live: that awake it never idles, building and sliding turn about; that a slide is runs to
 * the next thing that stops it, as many as its health allows; that it puts its bricks where
 * the next slide can use them; and that the first run of a slide goes the way it was shown.
 *
 * <p>It never changes a tile itself. A hole it crosses and a block it runs into are asked to
 * be made floor ahead of the run, a brick is asked to be raised, and the harness does both
 * the way the engine would, down to the brick crumbling a few turns on.
 */
class SliderAITest {

    // ── Fixture ──
    // A 14x14 floor of plain tiles. 90 health and no armour, so its thirds are whole numbers:
    // one run a slide above 60, up to three from 60 down, up to five from 30 down.

    private static final int SIZE = 14;
    private static final int HP = 90;
    private static final int ATTACK = 9;
    /** What its passing does to a tile beside the lane: half of {@link #ATTACK}, rounded down. */
    private static final int SHAKE = 4;
    /** The side of the room it is really fought in, where it starts in the middle. */
    private static final int ROOM = 14;
    /** The first strike that leaves it in each phase, first to last. */
    private static final int[] EACH_PHASE = {3, 30, 60};

    private static GridArena arena(GridPos player) {
        return arena(SIZE, player);
    }

    /** The same floor at another size. The room it is fought in is 16 by 16. */
    private static GridArena arena(int size, GridPos player) {
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

    private static void stand(GridArena a, GridPos player) {
        a.setPlayerGridPos(player);
        a.setAllPlayerGridPositions(List.of(player));
    }

    /** The usual floor with some of its tiles no part of the room: nothing there, not even ground. */
    private static GridArena arenaWithout(GridPos player, GridPos... gone) {
        GridTile[][] tiles = new GridTile[SIZE][SIZE];
        boolean[][] inside = new boolean[SIZE][SIZE];
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                tiles[x][z] = new GridTile(TileType.NORMAL, null);
                inside[x][z] = true;
            }
        }
        for (GridPos tile : gone) inside[tile.x()][tile.z()] = false;
        GridArena a = new GridArena(SIZE, SIZE, tiles, BlockPos.ORIGIN, 1, player, inside);
        stand(a, player);
        return a;
    }

    /** An obstacle painted this way is a block anyone could have put down. */
    private static void paint(GridArena a, TileType type, int x, int z) {
        a.setTile(new GridPos(x, z), new GridTile(type, null));
    }

    /** A block the arena holds as permanent: stone of the room itself, which no pickaxe breaks. */
    private static void wall(GridArena a, int x, int z) {
        a.setTile(new GridPos(x, z), new GridTile(TileType.OBSTACLE, null, true));
    }

    /** The three kinds of thing that stand on the floor. It treats them all alike. */
    private static final List<String> BLOCKS = List.of("a block put down", "stone of the room", "rubble");

    /** One of {@link #BLOCKS}, at (x, z). */
    private static void block(GridArena a, String kind, int x, int z) {
        if (kind.equals("stone of the room")) wall(a, x, z);
        else paint(a, kind.equals("rubble") ? TileType.RUBBLE : TileType.OBSTACLE, x, z);
    }

    private static boolean isBlock(TileType type) {
        return type == TileType.OBSTACLE || type == TileType.RUBBLE;
    }

    private static TileType typeAt(GridArena a, int x, int z) {
        return a.getTile(x, z).getType();
    }

    private static CombatEntity slider(GridArena a, int x, int z) {
        return slider(a, x, z, HP);
    }

    private static CombatEntity slider(GridArena a, int x, int z, int hp) {
        CombatEntity boss = new CombatEntity(100, SliderAI.ENTITY_TYPE, new GridPos(x, z),
            hp, ATTACK, 0, 1, /* size */ 2, /* speed */ 3);
        boss.setBoss(true);
        assertTrue(a.placeEntity(boss), "fixture: the slider did not fit at " + x + "," + z);
        return boss;
    }

    private static CombatEntity zombie(GridArena a, int x, int z) {
        CombatEntity other = new CombatEntity(200 + x * SIZE + z, "minecraft:zombie", new GridPos(x, z), 10, 2, 0, 1, 1, 2);
        assertTrue(a.placeEntity(other), "fixture: no room for a bystander at " + x + "," + z);
        return other;
    }

    /** The blast of a sentry of its own, which is less than a crush. */
    private static final int BLAST = 6;

    /** A sentry: a creature that goes off when it is run into. */
    private static CombatEntity sentry(GridArena a, int x, int z) {
        CombatEntity mine = new CombatEntity(400 + x * SIZE + z, SliderAI.SENTRY_TYPE, new GridPos(x, z),
            SliderAI.SENTRY_HP, BLAST, 0, 1, 1, 3);
        mine.setAiInstance(new SentryAI());
        assertTrue(a.placeEntity(mine), "fixture: no room for a sentry at " + x + "," + z);
        return mine;
    }

    /** One pickaxe swing, the way the item path lands it. */
    private static void strike(CombatEntity boss, int damage) {
        assertEquals(damage, boss.takeDamageThroughImmunity(damage), "fixture: the strike did not land whole");
    }

    /** A fresh brain, struck once for {@code first} and given the turn it spends waking. */
    private static SliderAI woken(CombatEntity boss, GridArena a, GridPos player, int first) {
        SliderAI ai = new SliderAI();
        strike(boss, first);
        assertInstanceOf(EnemyAction.CustomAction.class, turn(ai, boss, a, player), "fixture: the turn it wakes");
        return ai;
    }

    // ── What the engine does with what it asks for ──

    /** How long the bricks standing on each arena have left. Nothing here can tick a tile for it. */
    private static final Map<GridArena, Map<GridPos, Integer>> STANDING = new WeakHashMap<>();

    private static Map<GridPos, Integer> standing(GridArena a) {
        return STANDING.computeIfAbsent(a, fresh -> new HashMap<>());
    }

    /** One of its turns: what it asks for, carried out, and then the round turning over. */
    private static EnemyAction turn(SliderAI ai, CombatEntity boss, GridArena a, GridPos player) {
        EnemyAction action = decide(ai, boss, a, player);
        carryOut(action, boss, a);
        crumble(a);
        return action;
    }

    /** What it asks for. The tile its strike is meant for is wiped first, as the engine wipes it. */
    private static EnemyAction decide(SliderAI ai, CombatEntity boss, GridArena a, GridPos player) {
        boss.setPendingStrikeTile(null);
        EnemyAction action = ai.decideAction(boss, a, player);
        boss.setDamagedSinceLastTurn(false);
        return action;
    }

    /**
     * What the engine does to the grid with an action, in the engine's order. Terrain it is
     * asked for is laid at once: floor for good, a brick for as long as it was given. The
     * first action that takes time is given the turn and its path walked, and nothing after
     * that touches the grid.
     *
     * <p>The path has to be good as it stands when its turn comes, every tile of the body on
     * ground it can walk at every step: the engine checks less than that, and draws a move the
     * grid refused all the same. So ground to be made floor has to be asked for ahead of the
     * run. Stone of the room made floor for good is floor like any other from then on, which
     * is the engine's rule. A tile retyped any other way keeps what the arena said about it.
     */
    private static void carryOut(EnemyAction action, CombatEntity boss, GridArena a) {
        for (EnemyAction part : EnemyAction.flatten(action)) {
            if (part instanceof EnemyAction.CreateTerrain terrain) {
                boolean floorForGood = terrain.terrainType() == TileType.NORMAL && terrain.duration() <= 0;
                for (GridPos tile : terrain.tiles()) {
                    boolean permanent = !floorForGood && a.getTile(tile) != null && a.getTile(tile).isPermanent();
                    GridTile laid = new GridTile(terrain.terrainType(), null, permanent);
                    if (terrain.duration() > 0) laid.setTurnsRemaining(terrain.duration());
                    a.setTile(tile, laid);
                    if (terrain.duration() > 0) standing(a).put(tile, terrain.duration());
                    else standing(a).remove(tile);
                }
            }
            if (!EnemyAction.drivesTurn(part)) continue;
            for (GridPos step : pathOf(part)) {
                for (GridPos tile : GridArena.getOccupiedTiles(step, boss.getSizeX(), boss.getSizeZ())) {
                    GridTile ground = a.getTile(tile);
                    assertTrue(ground != null && ground.isWalkable(),
                        "it asked to move over ground it cannot walk on: " + tile);
                }
                assertTrue(a.moveEntity(boss, step), "it asked to move somewhere the grid refuses: " + step);
            }
            return;
        }
    }

    /**
     * The round turning over: every brick still standing has a turn less, and one with none
     * left is floor again. The engine does this once a round, before the players move.
     */
    private static void crumble(GridArena a) {
        Map<GridPos, Integer> left = standing(a);
        for (GridPos tile : new ArrayList<>(left.keySet())) {
            int turns = left.get(tile) - 1;
            if (turns > 0) {
                left.put(tile, turns);
                // The tile says how long it has, which is how it knows not to count on one
                // that is on its last turn.
                if (a.getTile(tile).getType() == TileType.OBSTACLE) a.getTile(tile).setTurnsRemaining(turns);
                continue;
            }
            left.remove(tile);
            if (a.getTile(tile).getType() == TileType.OBSTACLE) {
                a.setTile(tile, new GridTile(TileType.NORMAL, null, a.getTile(tile).isPermanent()));
            }
        }
    }

    /**
     * The part of a turn that takes time, picked the way the engine picks it: the action
     * itself, or the first one in a bundle that drives the turn. A slide that broke, filled
     * or shook anything comes as a bundle, and looked at whole it would seem never to move.
     */
    private static EnemyAction runOf(EnemyAction action) {
        if (!(action instanceof EnemyAction.CompositeAction)) return action;
        for (EnemyAction part : EnemyAction.flatten(action)) {
            if (EnemyAction.drivesTurn(part)) return part;
        }
        return action;
    }

    /** The tiles a turn asked to have made floor: holes it crossed and blocks it broke. */
    private static List<GridPos> levelledOf(EnemyAction action) {
        for (EnemyAction part : EnemyAction.flatten(action)) {
            if (part instanceof EnemyAction.CreateTerrain terrain && terrain.terrainType() == TileType.NORMAL) {
                return terrain.tiles();
            }
        }
        return List.of();
    }

    /** The bricks a turn asked to have raised. */
    private static List<GridPos> raisedOf(EnemyAction action) {
        for (EnemyAction part : EnemyAction.flatten(action)) {
            if (part instanceof EnemyAction.CreateTerrain terrain && terrain.terrainType() == TileType.OBSTACLE) {
                return terrain.tiles();
            }
        }
        return List.of();
    }

    /** The last part of a slide's bundle, or null when the turn shook nothing. */
    private static EnemyAction.TileAreaAttack shakeOf(EnemyAction action) {
        for (EnemyAction part : EnemyAction.flatten(action)) {
            if (part instanceof EnemyAction.TileAreaAttack shake) return shake;
        }
        return null;
    }

    /** The tiles its passing shook this turn. Empty when it went nowhere. */
    private static Set<GridPos> wakeOf(EnemyAction action) {
        EnemyAction.TileAreaAttack shake = shakeOf(action);
        return shake == null ? Set.of() : new HashSet<>(shake.tiles());
    }

    /**
     * The only bundle it may ask for: ground to make floor if there is any, then one run, then
     * whoever it runs into, then the wake if there is one, and nothing else. The order is the
     * engine's rule and not a matter of taste. The ground has to be floor before the path is
     * checked, and only the first action in a bundle that takes time is given the turn, so
     * there is exactly one: however many runs a slide has, they are one path with corners in
     * it. Whoever it runs into comes after the run, because that is what makes the engine
     * hold the blow until the run has got there.
     */
    private static void assertSlideBundle(EnemyAction action, String where) {
        if (!(action instanceof EnemyAction.CompositeAction bundle)) return;
        List<EnemyAction> parts = new ArrayList<>(bundle.actions());
        assertTrue(parts.size() >= 2, "a bundle of the wrong size. " + where);

        if (parts.get(0) instanceof EnemyAction.CreateTerrain terrain) {
            parts.remove(0);
            assertEquals(TileType.NORMAL, terrain.terrainType(), "a slide makes floor and nothing else. " + where);
            assertEquals(0, terrain.duration(), "and for good. " + where);
            assertFalse(terrain.tiles().isEmpty(), "no ground to change is not worth asking for. " + where);
            assertEquals(terrain.tiles().size(), new HashSet<>(terrain.tiles()).size(),
                "a tile made floor twice. " + where);
        }
        if (parts.get(0) instanceof EnemyAction.Ram) {
            // It went nowhere: there is no run, and nothing for a blow to wait on.
            assertRams(parts, 0, where);
            assertTrue(parts.isEmpty(), "something after a slide that went nowhere. " + where);
            return;
        }
        EnemyAction run = parts.remove(0);
        assertTrue(run instanceof EnemyAction.Move || isCrush(run), "the run is not where it belongs. " + where);
        assertTrue(EnemyAction.drivesTurn(run), "the engine would not give the run the turn. " + where);
        assertRams(parts, pathOf(run).size(), where);
        if (parts.isEmpty()) return;

        EnemyAction.TileAreaAttack shake = assertInstanceOf(EnemyAction.TileAreaAttack.class,
            parts.remove(0), where);
        assertTrue(parts.isEmpty(), "something after the wake. " + where);
        assertFalse(pathOf(run).isEmpty(), "it went nowhere, and still shook the ground. " + where);
        assertFalse(shake.tiles().isEmpty(), "a wake of no tiles is not worth asking for. " + where);
        assertEquals(shake.tiles().size(), new HashSet<>(shake.tiles()).size(), "a tile shaken twice. " + where);
        assertEquals(SHAKE, shake.damage(), "the wake is half a crush. " + where);
        assertNull(shake.effectName(), "the wake carries no effect of its own. " + where);
    }

    /** Take the blows off the head of {@code parts}: each a crush, for a step the run does reach. */
    private static void assertRams(List<EnemyAction> parts, int steps, String where) {
        int last = 0;
        while (!parts.isEmpty() && parts.get(0) instanceof EnemyAction.Ram ram) {
            parts.remove(0);
            assertFalse(ram.tiles().isEmpty(), "running into nobody is not worth asking for. " + where);
            assertEquals(ATTACK, ram.damage(), "what it runs into takes a crush. " + where);
            assertTrue(ram.afterSteps() >= last && ram.afterSteps() <= steps,
                "a blow for a step the run never makes, or out of order. " + where);
            last = ram.afterSteps();
        }
    }

    /** Whoever a turn ran into, in the order it got to them. */
    private static List<EnemyAction.Ram> ramsOf(EnemyAction action) {
        List<EnemyAction.Ram> rams = new ArrayList<>();
        for (EnemyAction part : EnemyAction.flatten(action)) {
            if (part instanceof EnemyAction.Ram ram) rams.add(ram);
        }
        return rams;
    }

    private static List<GridPos> pathOf(EnemyAction action) {
        EnemyAction run = runOf(action);
        if (run instanceof EnemyAction.Move move) return move.path();
        if (run instanceof EnemyAction.MoveAndAttackWithKnockback charge) return charge.path();
        return List.of();
    }

    private static boolean isCrush(EnemyAction action) {
        EnemyAction run = runOf(action);
        return run instanceof EnemyAction.MoveAndAttackWithKnockback
            || run instanceof EnemyAction.AttackWithKnockback;
    }

    /**
     * Everything on its danger map: all the ground the slide it has aimed could hurt someone
     * on. It is what it reasons with. Since it took to showing only its first run, it is not
     * what is painted, which {@link #shown} reads.
     */
    private static Set<GridPos> painted(SliderAI ai) {
        return new HashSet<>(ai.danger().tiles());
    }

    /** The tiles of that map that carry an arrow, which is how ground a crush can land on is told apart. */
    private static Set<GridPos> arrowed(SliderAI ai) {
        return new HashSet<>(ai.danger().arrows().keySet());
    }

    /** The tiles of that map whose arrow points along (dx, dz). */
    private static Set<GridPos> arrowed(SliderAI ai, int dx, int dz) {
        Set<GridPos> tiles = new HashSet<>();
        for (Map.Entry<GridPos, int[]> arrow : ai.danger().arrows().entrySet()) {
            if (arrow.getValue()[0] == dx && arrow.getValue()[1] == dz) tiles.add(arrow.getKey());
        }
        return tiles;
    }

    /** What the player is shown, as it reads at this moment: every painted tile. */
    private static Set<GridPos> shown(SliderAI ai) {
        BossWarning warning = ai.getPendingWarning();
        return warning == null ? Set.of() : new HashSet<>(warning.getAffectedTiles());
    }

    /** The shown tiles that carry an arrow. */
    private static Set<GridPos> shownArrows(SliderAI ai) {
        Set<GridPos> tiles = new HashSet<>();
        BossWarning warning = ai.getPendingWarning();
        if (warning == null) return tiles;
        for (GridPos tile : warning.getAffectedTiles()) {
            if (warning.arrowAt(tile) != null) tiles.add(tile);
        }
        return tiles;
    }

    /** Every tile from (x0, z0) to (x1, z1), both corners included. */
    private static Set<GridPos> box(int x0, int z0, int x1, int z1) {
        Set<GridPos> tiles = new HashSet<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) tiles.add(new GridPos(x, z));
        }
        return tiles;
    }

    /** The row a 2x2 body cornered at {@code at} would step into next along (dx, dz). */
    private static List<GridPos> rowAhead(GridPos at, int dx, int dz) {
        return SliderAI.leadingEdge(at, 2, 2, dx, dz);
    }

    private static GridPos end(List<GridPos> path) {
        return path.get(path.size() - 1);
    }

    private static final int[] EAST = {1, 0};
    private static final int[] WEST = {-1, 0};
    private static final int[] SOUTH = {0, 1};
    private static final int[] NORTH = {0, -1};
    private static final int[][] WAYS = {EAST, WEST, SOUTH, NORTH};

    // ── What it is ──

    @Test
    @DisplayName("a 2x2 boss of the Bronze Dungeon, keyed to the biome, that builds in the dungeon's own stone")
    void identity() {
        assertEquals(2, new SliderAI().getGridSize());
        assertEquals("boss:aether_bronze_dungeon", SliderAI.AI_KEY);
        assertEquals("aether:slider", SliderAI.ENTITY_TYPE);
        assertTrue(SliderAI.PICKAXE_HINT.startsWith("§c"), "the refusal is red, like every other");
        assertTrue(SliderAI.PICKAXE_HINT.endsWith("Hmm. Perhaps I need to attack it with a Pickaxe?"),
            "the line is the Aether's own");
        assertEquals("aether:carved_stone", new SliderAI().getWallBlockId(), "which is what the engine builds its bricks out of");
        assertEquals(4, SliderAI.BRICK_TURNS, "an even number, so a brick never crumbles between the turn it is shown and the slide");
        assertEquals(3, SliderAI.KNOCKBACK_TILES);
    }

    @Test
    @DisplayName("registering it gives the biome's boss a brain of its own per fight, and the two traits it earns")
    void registration() {
        SliderAI.register();
        SliderAI.register();   // safe to call twice

        assertInstanceOf(SliderAI.class, AIRegistry.get(SliderAI.AI_KEY));
        EnemyAI one = AIRegistry.createFresh(SliderAI.AI_KEY);
        assertInstanceOf(SliderAI.class, one);
        assertNotSame(one, AIRegistry.createFresh(SliderAI.AI_KEY), "its state is per fight");

        List<MobTrait> traits = MobTraits.forType(SliderAI.ENTITY_TYPE, SliderAI.AI_KEY, true, true, true);
        assertTrue(traits.contains(MobTraits.BERZERKER), "it charges down a straight lane");
        assertTrue(traits.contains(MobTraits.FORCEFUL), "and throws what it crushes");
        assertTrue(traits.contains(MobTraits.LARGE));
        assertFalse(traits.contains(MobTraits.ETHEREAL), "nothing it does not do");
        assertFalse(traits.contains(MobTraits.IMMOVABLE), "it moves itself, so it cannot be flagged immovable");
    }

    @Test
    @DisplayName("its own hitbox fills a 2x2 footprint exactly, and is never enlarged")
    void fitScale() {
        assertEquals(1.0, SliderAI.fitScale(2), 1e-9);
        assertEquals(0.5, SliderAI.fitScale(1), 1e-9);
        assertEquals(1.0, SliderAI.fitScale(3), 1e-9);
        assertEquals(1.0, SliderAI.fitScale(0), 1e-9);
    }

    // ── Asleep ──

    @Test
    @DisplayName("asleep it does nothing, paints nothing and threatens nothing, however close you stand")
    void sleepsUntilHurt() {
        GridPos player = new GridPos(8, 6);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);
        SliderAI ai = new SliderAI();

        for (int i = 0; i < 6; i++) {
            assertInstanceOf(EnemyAction.Idle.class, turn(ai, boss, a, player), "turn " + i);
            assertNull(ai.getPendingWarning());
        }
        assertFalse(ai.isAwake());
        assertTrue(ai.computeThreatTiles(boss, a).isEmpty(), "a sleeping block paints no danger");
        assertTrue(ai.isHostileThreat(boss, a, player), "asleep is not harmless: the room does not end itself");
    }

    @Test
    @DisplayName("the guard is up from its first turn and only a strike through it lands")
    void onlyAPickaxeHurtsIt() {
        GridPos player = new GridPos(8, 6);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);
        SliderAI ai = new SliderAI();
        turn(ai, boss, a, player);

        assertTrue(boss.isDamageImmune());
        assertTrue(boss.isPickaxeVulnerable());
        assertEquals(SliderAI.PICKAXE_HINT, boss.getDamageImmuneHint());
        assertEquals(0, boss.takeDamage(50), "a sword does nothing");
        assertEquals(HP, boss.getCurrentHp());
        assertFalse(boss.wasDamagedSinceLastTurn(), "and a refused swing does not wake it");
        assertInstanceOf(EnemyAction.Idle.class, turn(ai, boss, a, player));

        strike(boss, 3);
        turn(ai, boss, a, player);
        assertTrue(boss.isDamageImmune(), "being hurt does not drop it");
    }

    @Test
    @DisplayName("the first strike wakes it: it says so, and that is the whole of that turn")
    void wakesWhenStruck() {
        GridPos player = new GridPos(10, 9);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);
        SliderAI ai = new SliderAI();
        turn(ai, boss, a, player);

        strike(boss, 3);
        EnemyAction.CustomAction wake = assertInstanceOf(EnemyAction.CustomAction.class,
            turn(ai, boss, a, player), "a bare top-level action, or the engine would drop it");
        assertEquals(SliderAI.AWAKEN, wake.id());
        assertTrue(ai.isAwake());
        assertNull(ai.getPendingWarning(), "nothing is marked yet");
        assertFalse(ai.isSliding(), "its next turn is a build, which is where the marking is done");
    }

    @Test
    @DisplayName("lost health wakes it even when the damaged flag was never seen")
    void wakesOnLostHealthAlone() {
        GridPos player = new GridPos(10, 9);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);
        SliderAI ai = new SliderAI();

        strike(boss, 3);
        boss.setDamagedSinceLastTurn(false);   // as after a turn it was stunned through
        assertInstanceOf(EnemyAction.CustomAction.class, turn(ai, boss, a, player));
    }

    @Test
    @DisplayName("being told of a strike changes nothing it does: strikes are no longer counted")
    void strikesAreNotCounted() {
        GridPos player = new GridPos(10, 9);
        List<String> plain = new ArrayList<>();
        List<String> told = new ArrayList<>();
        for (List<String> log : List.of(plain, told)) {
            GridArena a = arena(player);
            CombatEntity boss = slider(a, 6, 6);
            SliderAI ai = woken(boss, a, player, 3);
            for (int t = 0; t < 6; t++) {
                if (log == told) {
                    for (int i = 0; i < 5; i++) ai.notifyStruck();
                }
                log.add(turn(ai, boss, a, player).toString());
            }
        }
        assertEquals(plain, told);
    }

    // ── Numbers ──

    @Test
    @DisplayName("three phases, by thirds of its health, and it turns red at the second")
    void phases() {
        assertEquals(1, SliderAI.phase(90, 90));
        assertEquals(1, SliderAI.phase(61, 90));
        assertEquals(2, SliderAI.phase(60, 90), "exactly two thirds is the second phase");
        assertEquals(2, SliderAI.phase(31, 90));
        assertEquals(3, SliderAI.phase(30, 90), "exactly a third is the third");
        assertEquals(3, SliderAI.phase(1, 90));
        // The dungeon's boss has 85, which no third divides.
        assertEquals(1, SliderAI.phase(57, 85));
        assertEquals(2, SliderAI.phase(56, 85));
        assertEquals(2, SliderAI.phase(29, 85));
        assertEquals(3, SliderAI.phase(28, 85));

        GridPos player = new GridPos(10, 9);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);
        SliderAI ai = woken(boss, a, player, 29);
        assertFalse(ai.isInPhaseTwo());
        assertFalse(boss.isEnraged());
        strike(boss, 1);
        turn(ai, boss, a, player);
        assertTrue(ai.isInPhaseTwo(), "which is what the engine reads to show it red");
        assertTrue(boss.isEnraged());
    }

    @Test
    @DisplayName("one run a slide, then up to three, then up to five; two bricks a build, then three, then four")
    void runsAndBricksByPhase() {
        assertEquals(1, SliderAI.legs(1));
        assertEquals(3, SliderAI.legs(2));
        assertEquals(5, SliderAI.legs(3));
        assertEquals(2, SliderAI.bricksFor(1));
        assertEquals(3, SliderAI.bricksFor(2));
        assertEquals(4, SliderAI.bricksFor(3));
    }

    @Test
    @DisplayName("its passing does half a crush, rounded down, and never nothing")
    void wakeDamage() {
        assertEquals(SHAKE, SliderAI.wakeDamage(ATTACK));
        assertEquals(5, SliderAI.wakeDamage(10));
        assertEquals(1, SliderAI.wakeDamage(3));
        assertEquals(1, SliderAI.wakeDamage(1), "a boss weak enough to halve to nothing still shakes");
        assertEquals(1, SliderAI.wakeDamage(0));
    }

    // ── One run ──

    @Test
    @DisplayName("on open floor a run goes to the edge of the arena, whichever way it sets off")
    void aRunGoesToTheEdge() {
        GridPos player = new GridPos(10, 9);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);
        GridPos from = boss.getGridPos();

        assertEquals(new SliderAI.Leg(from, 1, 0, 6, SliderAI.Stop.EDGE, List.of(), null),
            SliderAI.leg(a, boss, player, from, 1, 0));
        assertEquals(new GridPos(12, 6), SliderAI.leg(a, boss, player, from, 1, 0).end());
        assertEquals(new GridPos(0, 6), SliderAI.leg(a, boss, player, from, -1, 0).end());
        assertEquals(new GridPos(6, 12), SliderAI.leg(a, boss, player, from, 0, 1).end());
        assertEquals(new GridPos(6, 0), SliderAI.leg(a, boss, player, from, 0, -1).end());

        SliderAI.Leg none = SliderAI.leg(a, boss, player, new GridPos(12, 6), 1, 0);
        assertEquals(0, none.steps());
        assertFalse(none.taken(), "against the edge there is no run that way");
    }

    @Test
    @DisplayName("a block in either half of its width stops it in front of the row it stands in, and every block in that row is broken")
    void aBlockStopsARun() {
        GridPos player = new GridPos(2, 2);
        for (String kind : BLOCKS) {
            GridArena a = arena(player);
            // In the half of its width the body's corner never touches.
            block(a, kind, 10, 7);
            CombatEntity boss = slider(a, 6, 6);

            SliderAI.Leg leg = SliderAI.leg(a, boss, player, boss.getGridPos(), 1, 0);
            assertEquals(new SliderAI.Leg(boss.getGridPos(), 1, 0, 2, SliderAI.Stop.BLOCK,
                List.of(new GridPos(10, 7)), null), leg, kind);
            assertEquals(new GridPos(8, 6), leg.end(), kind);

            block(a, kind, 10, 6);
            assertEquals(List.of(new GridPos(10, 6), new GridPos(10, 7)),
                SliderAI.leg(a, boss, player, boss.getGridPos(), 1, 0).smashed(), kind + ": both of them");
        }
    }

    @Test
    @DisplayName("against a block a run of no length is still a run: it breaks what it is up against")
    void aRunOfNoLength() {
        GridPos player = new GridPos(2, 2);
        GridArena a = arena(player);
        wall(a, 8, 6);
        CombatEntity boss = slider(a, 6, 6);

        SliderAI.Leg leg = SliderAI.leg(a, boss, player, boss.getGridPos(), 1, 0);
        assertEquals(0, leg.steps());
        assertEquals(List.of(new GridPos(8, 6)), leg.smashed());
        assertTrue(leg.taken());
    }

    @Test
    @DisplayName("a hole does not stop a run, and ground it can walk on is just ground")
    void holesAndSoftGroundDoNotStopARun() {
        GridPos player = new GridPos(2, 2);
        for (TileType ground : List.of(TileType.VOID, TileType.DEEP_WATER, TileType.WATER, TileType.LOW_GROUND, TileType.LAVA)) {
            GridArena a = arena(player);
            paint(a, ground, 8, 6);
            paint(a, ground, 10, 7);
            CombatEntity boss = slider(a, 6, 6);
            assertEquals(new SliderAI.Leg(boss.getGridPos(), 1, 0, 6, SliderAI.Stop.EDGE, List.of(), null),
                SliderAI.leg(a, boss, player, boss.getGridPos(), 1, 0), ground.toString());
        }
    }

    @Test
    @DisplayName("whoever is in its way stops it and is crushed; another creature stops it too, and is run into")
    void bodiesStopARun() {
        GridPos player = new GridPos(10, 7);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);
        assertEquals(new SliderAI.Leg(boss.getGridPos(), 1, 0, 2, SliderAI.Stop.BODY, List.of(), player),
            SliderAI.leg(a, boss, player, boss.getGridPos(), 1, 0), "it stops against them, not on them");

        GridPos elsewhere = new GridPos(2, 2);
        GridArena b = arena(elsewhere);
        CombatEntity other = slider(b, 6, 6);
        zombie(b, 10, 7);
        assertEquals(new SliderAI.Leg(other.getGridPos(), 1, 0, 2, SliderAI.Stop.BODY, List.of(), null,
                List.of(new GridPos(10, 7))),
            SliderAI.leg(b, other, elsewhere, other.getGridPos(), 1, 0), "in front of it, as with anyone");
    }

    @Test
    @DisplayName("a creature beside the block it runs into is run into as well, and the block is broken all the same")
    void aCreatureAgainstABlock() {
        GridPos elsewhere = new GridPos(2, 2);
        GridArena a = arena(elsewhere);
        wall(a, 10, 7);
        zombie(a, 10, 6);
        CombatEntity boss = slider(a, 6, 6);
        assertEquals(new SliderAI.Leg(boss.getGridPos(), 1, 0, 2, SliderAI.Stop.BLOCK,
                List.of(new GridPos(10, 7)), null, List.of(new GridPos(10, 6))),
            SliderAI.leg(a, boss, elsewhere, boss.getGridPos(), 1, 0));
    }

    @Test
    @DisplayName("a creature standing where the room ends is not run into: it never gets that far")
    void nobodyIsRunIntoAtTheEdge() {
        GridPos elsewhere = new GridPos(2, 2);
        GridArena a = arenaWithout(elsewhere, new GridPos(10, 7));
        zombie(a, 10, 6);
        CombatEntity boss = slider(a, 6, 6);
        SliderAI.Leg leg = SliderAI.leg(a, boss, elsewhere, boss.getGridPos(), 1, 0);
        assertEquals(SliderAI.Stop.EDGE, leg.stop());
        assertTrue(leg.rammed().isEmpty());
    }

    @Test
    @DisplayName("whoever stands beside the block it runs into is crushed against it, and the block is broken all the same")
    void pressedAgainstABlock() {
        GridPos player = new GridPos(10, 6);
        GridArena a = arena(player);
        wall(a, 10, 7);
        CombatEntity boss = slider(a, 6, 6);
        assertEquals(new SliderAI.Leg(boss.getGridPos(), 1, 0, 2, SliderAI.Stop.BLOCK,
            List.of(new GridPos(10, 7)), player), SliderAI.leg(a, boss, player, boss.getGridPos(), 1, 0));
    }

    @Test
    @DisplayName("the pet it was sent after is crushed like a player; a pet it was not sent after is run into like any creature")
    void thePetItWasSentAfter() {
        GridPos player = new GridPos(2, 2);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);
        CombatEntity pet = zombie(a, 10, 6);
        pet.setAlly(true);

        assertEquals(pet.getGridPos(), SliderAI.leg(a, boss, pet.getGridPos(), boss.getGridPos(), 1, 0).crushed(),
            "its target");
        assertNull(SliderAI.leg(a, boss, player, boss.getGridPos(), 1, 0).crushed(), "not its target");
        assertEquals(SliderAI.Stop.BODY, SliderAI.leg(a, boss, player, boss.getGridPos(), 1, 0).stop());
        assertEquals(List.of(pet.getGridPos()), SliderAI.leg(a, boss, player, boss.getGridPos(), 1, 0).rammed());
        assertTrue(SliderAI.leg(a, boss, pet.getGridPos(), boss.getGridPos(), 1, 0).rammed().isEmpty(),
            "crushed, it is not run into as well");
    }

    // ── Aiming a run ──

    @Test
    @DisplayName("first, a way that crushes someone: even when its target is somewhere else")
    void aimsAtACrushFirst() {
        GridPos target = new GridPos(12, 3);
        GridPos inLine = new GridPos(6, 10);
        GridArena a = arena(target);
        a.setAllPlayerGridPositions(List.of(target, inLine));
        CombatEntity boss = slider(a, 6, 6);

        assertArrayEquals(SOUTH, SliderAI.aim(a, boss, target, boss.getGridPos(), null, 1, 0),
            "its target is east and a little north, and someone is standing due south of it");
        stand(a, target);
        assertArrayEquals(EAST, SliderAI.aim(a, boss, target, boss.getGridPos(), null, 1, 0),
            "fixture: with nobody there it goes for its target");
    }

    @Test
    @DisplayName("then, with a run to come after, a way that stops where the next run can crush")
    void aimsToSetUpTheNextRun() {
        GridPos player = new GridPos(10, 9);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);
        assertArrayEquals(EAST, SliderAI.aim(a, boss, player, boss.getGridPos(), null, 2, 0),
            "fixture: on open floor nothing stops it level with them, so it only goes their way");

        // A block two rows south: stopped by it, it stands level with them.
        wall(a, 6, 10);
        assertArrayEquals(SOUTH, SliderAI.aim(a, boss, player, boss.getGridPos(), null, 2, 0));
        assertArrayEquals(SOUTH, SliderAI.aim(a, boss, player, boss.getGridPos(), null, 1, 0),
            "in its first phase too: the block is what earns it the second run");
        assertArrayEquals(EAST, SliderAI.aim(a, boss, player, boss.getGridPos(), null, 2, 2),
            "but not on the last run it has: there is no next one to set up");
        assertArrayEquals(EAST, SliderAI.aim(a, boss, player, boss.getGridPos(), null, 1, 1));
    }

    @Test
    @DisplayName("failing both, toward its target on the axis it is further away on, and Z when they are level")
    void aimsTowardItsTarget() {
        GridArena a = arena(new GridPos(0, 0));
        CombatEntity boss = slider(a, 6, 6);
        GridPos from = boss.getGridPos();

        assertArrayEquals(EAST, SliderAI.aim(a, boss, new GridPos(10, 9), from, null, 1, 0), "3 east, 2 south");
        assertArrayEquals(SOUTH, SliderAI.aim(a, boss, new GridPos(9, 10), from, null, 1, 0), "2 east, 3 south");
        assertArrayEquals(SOUTH, SliderAI.aim(a, boss, new GridPos(10, 10), from, null, 1, 0), "the tie goes to Z");
        assertArrayEquals(WEST, SliderAI.aim(a, boss, new GridPos(2, 5), from, null, 1, 0));
        assertArrayEquals(NORTH, SliderAI.aim(a, boss, new GridPos(4, 1), from, null, 1, 0));
    }

    @Test
    @DisplayName("it takes the other axis toward its target when the edge is against it on the first")
    void aimsRoundTheEdge() {
        GridArena a = arena(new GridPos(0, 0));
        CombatEntity boss = slider(a, 12, 6);
        // 2 south and level east to west: south is the only way toward them, and it is open.
        assertArrayEquals(SOUTH, SliderAI.aim(a, boss, new GridPos(13, 9), boss.getGridPos(), null, 1, 0));
        // Hard in the corner with its target somewhere past it: neither way toward them can be
        // taken, and it goes the first way that can.
        GridArena b = arena(new GridPos(0, 0));
        CombatEntity cornered = slider(b, 12, 12);
        assertArrayEquals(WEST, SliderAI.aim(b, cornered, new GridPos(20, 20), cornered.getGridPos(), null, 1, 0));
    }

    @Test
    @DisplayName("it does not go straight back the way it came, unless that is the only way left")
    void doesNotGoStraightBack() {
        GridPos player = new GridPos(2, 10);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 12, 6);
        GridPos from = boss.getGridPos();

        assertArrayEquals(WEST, SliderAI.aim(a, boss, player, from, null, 2, 1), "fixture: west is the way to its target");
        assertArrayEquals(SOUTH, SliderAI.aim(a, boss, player, from, EAST, 2, 1),
            "having just come east it takes the other axis");

        // Boxed in: the room ends to the north and to the south of it, and the edge is to the
        // east. A creature on either side would not do. It would run into one.
        GridArena b = arenaWithout(player,
            new GridPos(12, 5), new GridPos(13, 5), new GridPos(12, 8), new GridPos(13, 8));
        CombatEntity boxed = slider(b, 12, 6);
        assertArrayEquals(WEST, SliderAI.aim(b, boxed, player, from, EAST, 2, 1), "then back is the only way");
    }

    @Test
    @DisplayName("but a crush is a crush: someone standing in its own track behind it is run down")
    void goesBackForACrush() {
        GridPos behind = new GridPos(3, 6);
        GridArena a = arena(behind);
        CombatEntity boss = slider(a, 9, 6);
        assertArrayEquals(WEST, SliderAI.aim(a, boss, behind, boss.getGridPos(), EAST, 2, 1));
    }

    @Test
    @DisplayName("the same position always gives the same answer")
    void aimingIsDeterministic() {
        Random dice = new Random(7);
        for (int i = 0; i < 200; i++) {
            GridPos player = new GridPos(dice.nextInt(SIZE), dice.nextInt(SIZE));
            GridArena a = arena(player);
            for (int walls = dice.nextInt(6); walls > 0; walls--) wall(a, dice.nextInt(SIZE), dice.nextInt(SIZE));
            int x = dice.nextInt(SIZE - 1);
            int z = dice.nextInt(SIZE - 1);
            if (GridArena.getOccupiedTiles(new GridPos(x, z), 2, 2).contains(player)) continue;
            for (GridPos tile : GridArena.getOccupiedTiles(new GridPos(x, z), 2, 2)) paint(a, TileType.NORMAL, tile.x(), tile.z());
            CombatEntity boss = slider(a, x, z);
            int phase = 1 + dice.nextInt(3);

            int[] once = SliderAI.aim(a, boss, player, boss.getGridPos(), null, phase, 0);
            int[] again = SliderAI.aim(a, boss, player, boss.getGridPos(), null, phase, 0);
            assertArrayEquals(once, again, "position " + i);
            assertEquals(SliderAI.route(a, boss, player, phase), SliderAI.route(a, boss, player, phase), "position " + i);
            assertEquals(SliderAI.bricks(a, boss, player, phase), SliderAI.bricks(a, boss, player, phase), "position " + i);
        }
    }

    // ── A whole slide ──

    @Test
    @DisplayName("one run in its first phase, up to three in its second, up to five in its third")
    void runsToASlide() {
        // Somewhere no run along an edge ever comes level with.
        GridPos player = new GridPos(9, 9);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 0, 0);

        List<SliderAI.Leg> five = SliderAI.route(a, boss, player, 3).legs();
        assertEquals(List.of(new GridPos(0, 12), new GridPos(12, 12), new GridPos(12, 0), new GridPos(0, 0), new GridPos(0, 12)),
            five.stream().map(SliderAI.Leg::end).collect(Collectors.toList()),
            "round the arena, each run to the edge, never straight back");
        assertEquals(five.subList(0, 3), SliderAI.route(a, boss, player, 2).legs());
        assertEquals(five.subList(0, 1), SliderAI.route(a, boss, player, 1).legs());
    }

    @Test
    @DisplayName("in its first phase a second run comes only off a block, and never a third")
    void theSecondRunOfTheFirstPhase() {
        GridPos player = new GridPos(9, 9);
        GridArena a = arena(player);
        wall(a, 0, 6);
        wall(a, 9, 4);
        CombatEntity boss = slider(a, 0, 0);

        List<SliderAI.Leg> legs = SliderAI.route(a, boss, player, 1).legs();
        assertEquals(2, legs.size());
        assertEquals(SliderAI.Stop.BLOCK, legs.get(0).stop());
        assertEquals(new GridPos(0, 4), legs.get(0).end(), "stopped by the first block");
        assertEquals(new GridPos(7, 4), legs.get(1).end(), "turned, and stopped by the second");
        assertEquals(SliderAI.Stop.BLOCK, legs.get(1).stop(), "which it breaks, and there it ends all the same");

        assertTrue(SliderAI.follows(1, 0, legs.get(0)));
        assertFalse(SliderAI.follows(1, 1, legs.get(1)));
        assertFalse(SliderAI.follows(1, 0, SliderAI.leg(a, boss, player, new GridPos(4, 10), 1, 0)),
            "a run the edge stopped earns nothing");
    }

    @Test
    @DisplayName("a crush ends the slide, however many runs it had left")
    void aCrushEndsTheSlide() {
        GridPos player = new GridPos(0, 9);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 0, 0);
        for (int phase = 1; phase <= 3; phase++) {
            List<SliderAI.Leg> legs = SliderAI.route(a, boss, player, phase).legs();
            assertEquals(1, legs.size(), "phase " + phase);
            assertEquals(player, legs.get(0).crushed());
            assertFalse(SliderAI.follows(phase, 0, legs.get(0)));
        }
    }

    // ── Bricks ──

    @Test
    @DisplayName("the brick it raises is where its slide turns: stopped by it, the next run crushes")
    void theBrickIsWhereItTurns() {
        GridPos player = new GridPos(10, 9);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);

        assertNull(SliderAI.route(a, boss, player, 1).crushed(), "fixture: without help its slide only passes them");
        List<GridPos> bricks = SliderAI.bricks(a, boss, player, 1);
        assertEquals(new GridPos(11, 6), bricks.get(0), "three tiles east, where stopping leaves them due south of it");

        paint(a, TileType.OBSTACLE, 11, 6);
        List<SliderAI.Leg> legs = SliderAI.route(a, boss, player, 1).legs();
        assertEquals(2, legs.size());
        assertTrue(rowAhead(legs.get(0).end(), 1, 0).contains(bricks.get(0)), "the first run ends against it");
        assertEquals(player, legs.get(1).crushed());
    }

    @Test
    @DisplayName("with a crush already open, the brick goes just past its target, to stop the run there if they step aside")
    void theBrickThatStopsItPastItsTarget() {
        GridPos player = new GridPos(10, 6);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);

        assertEquals(player, SliderAI.route(a, boss, player, 1).crushed(), "fixture: they are standing in its lane");
        assertEquals(List.of(new GridPos(11, 7)), SliderAI.bricks(a, boss, player, 1),
            "the row behind them, and of its two tiles the one that is not against them");

        // With them gone the run now ends on the tile they stood on, and not at the far wall.
        paint(a, TileType.OBSTACLE, 11, 7);
        stand(a, new GridPos(2, 2));
        assertEquals(new GridPos(9, 6), SliderAI.leg(a, boss, new GridPos(2, 2), boss.getGridPos(), 1, 0).end());
    }

    @Test
    @DisplayName("it raises nothing when nothing would be better for it")
    void noBrickWhenNothingHelps() {
        // Crushed where they stand, with their back to the edge: no brick gets it there
        // sooner, and there is no floor behind them to stop it on.
        GridPos player = new GridPos(13, 6);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);
        assertTrue(SliderAI.bricks(a, boss, player, 1).isEmpty());
        assertTrue(SliderAI.bricks(a, boss, player, 3).isEmpty());

        SliderAI ai = woken(boss, a, player, 3);
        assertInstanceOf(EnemyAction.Idle.class, turn(ai, boss, a, player), "nothing to build");
        assertNotNull(ai.getPendingWarning(), "but it has taken aim all the same");
        assertTrue(ai.isSliding());
    }

    @Test
    @DisplayName("never more bricks than its phase allows")
    void bricksByPhase() {
        Random dice = new Random(11);
        int[] most = new int[4];
        for (int i = 0; i < 300; i++) {
            GridPos player = new GridPos(dice.nextInt(SIZE), dice.nextInt(SIZE));
            if (GridArena.getOccupiedTiles(new GridPos(6, 6), 2, 2).contains(player)) continue;
            GridArena a = arena(player);
            CombatEntity boss = slider(a, 6, 6);
            for (int phase = 1; phase <= 3; phase++) {
                int count = SliderAI.bricks(a, boss, player, phase).size();
                assertTrue(count <= SliderAI.bricksFor(phase), "phase " + phase + " raised " + count);
                most[phase] = Math.max(most[phase], count);
            }
        }
        System.out.println("BRICKS the most it raised at once in each phase: " + most[1] + ", " + most[2] + ", " + most[3]);
        assertEquals(2, most[1], "and it does find a use for both");
        assertTrue(most[2] >= 2 && most[3] >= 2);
    }

    @Test
    @DisplayName("a brick only ever goes on plain floor that nobody is standing on, itself included")
    void bricksOnlyOnOpenFloor() {
        Random dice = new Random(23);
        int raised = 0;
        for (int i = 0; i < 150; i++) {
            GridPos player = new GridPos(dice.nextInt(SIZE), dice.nextInt(SIZE));
            if (GridArena.getOccupiedTiles(new GridPos(6, 6), 2, 2).contains(player)) continue;
            GridArena a = arena(player);
            CombatEntity boss = slider(a, 6, 6);
            // Other creatures, holes, puddles, pits and blocks, wherever there is room.
            TileType[] litter = {null, null, null, null, null, null, TileType.VOID, TileType.VOID, TileType.DEEP_WATER,
                TileType.WATER, TileType.WATER, TileType.LAVA, TileType.LOW_GROUND, TileType.LOW_GROUND,
                TileType.OBSTACLE, TileType.OBSTACLE, TileType.RUBBLE};
            for (TileType kind : litter) {
                GridPos tile = new GridPos(dice.nextInt(SIZE), dice.nextInt(SIZE));
                if (!open(a, boss, tile) || tile.equals(player) || a.getOccupant(tile) != null) continue;
                if (kind == null) zombie(a, tile.x(), tile.z());
                else paint(a, kind, tile.x(), tile.z());
            }
            for (int phase = 1; phase <= 3; phase++) {
                List<GridPos> bricks = SliderAI.bricks(a, boss, player, phase);
                assertEquals(bricks.size(), new HashSet<>(bricks).size(), "the same tile twice");
                for (GridPos brick : bricks) {
                    String where = "arena " + i + ", phase " + phase + ", a brick at " + brick;
                    raised++;
                    assertEquals(TileType.NORMAL, typeAt(a, brick.x(), brick.z()), where);
                    assertNull(a.getOccupant(brick), where);
                    assertFalse(brick.equals(player), where);
                    assertTrue(boss.minDistanceTo(brick) > 0, where);
                }
            }
        }
        assertTrue(raised > 150, "fixture: only " + raised + " bricks to look at");
    }

    @Test
    @DisplayName("a brick is raised for what it buys: a crush where there was none, or the same crush in fewer runs, or failing both a stop nearer its target")
    void whatABrickBuys() {
        for (int phase = 1; phase <= 3; phase++) {
            int places = 0;
            int without = 0;
            int with = 0;
            int sooner = 0;
            int nearer = 0;
            int atTheEdge = 0;
            for (int px = 0; px < SIZE; px++) {
                for (int pz = 0; pz < SIZE; pz++) {
                    if (px >= 6 && px <= 7 && pz >= 6 && pz <= 7) continue;
                    GridPos player = new GridPos(px, pz);
                    GridArena a = arena(player);
                    // A little stone of the room, so that not every stop is the edge.
                    for (int[] stone : new int[][]{{3, 3}, {10, 4}, {4, 11}}) {
                        if (!player.equals(new GridPos(stone[0], stone[1]))) wall(a, stone[0], stone[1]);
                    }
                    CombatEntity boss = slider(a, 6, 6);
                    String where = "phase " + phase + ", a player at " + player;

                    SliderAI.Route before = SliderAI.route(a, boss, player, phase);
                    List<GridPos> bricks = SliderAI.bricks(a, boss, player, phase);
                    for (GridPos brick : bricks) paint(a, TileType.OBSTACLE, brick.x(), brick.z());
                    SliderAI.Route after = SliderAI.route(a, boss, player, phase);
                    places++;
                    boolean had = before.crushed() != null;
                    boolean has = after.crushed() != null;
                    if (had) without++;
                    if (has) with++;
                    if (had) {
                        // Never one in the lane between itself and a crush it already has.
                        assertEquals(player, after.crushed(), "its bricks spoiled a crush it already had. " + where);
                        assertTrue(after.legs().size() <= before.legs().size(), "it takes more runs to get there. " + where);
                        if (after.legs().size() < before.legs().size()) sooner++;
                    } else if (!has && !bricks.isEmpty()) {
                        nearer++;
                    }
                    // And where it ends: not against the edge, if there was floor to stop it on.
                    GridPos rests = after.end(boss.getGridPos());
                    if (rests.x() == 0 || rests.z() == 0 || rests.x() == SIZE - 2 || rests.z() == SIZE - 2) atTheEdge++;
                }
            }
            String tally = "phase " + phase + ": of " + places + " places to stand, its next slide crushes in " + without
                + " with no bricks and " + with + " with them, " + sooner + " of them sooner; and in " + nearer
                + " where it could not, it raised some all the same; the slide as planned ends against the edge in "
                + atTheEdge;
            System.out.println("BRICKS " + tally);
            assertTrue(with > without, tally);
            assertTrue(atTheEdge * 4 < places, "more than a quarter of its slides are planned to end on the edge. " + tally);
        }
    }

    @Test
    @DisplayName("with a crush three runs off, its brick is the one that makes it two")
    void theBrickThatGetsThereSooner() {
        // Round two corners to the stone on the far side, which stops it level with them.
        GridPos player = new GridPos(9, 9);
        GridArena a = arena(player);
        wall(a, 11, 12);
        CombatEntity boss = slider(a, 0, 0);
        SliderAI.Route before = SliderAI.route(a, boss, player, 2);
        assertEquals(List.of(new GridPos(0, 12), new GridPos(9, 12), new GridPos(9, 10)),
            before.legs().stream().map(SliderAI.Leg::end).collect(Collectors.toList()), "fixture");
        assertEquals(player, before.crushed(), "fixture");

        GridPos brick = SliderAI.bricks(a, boss, player, 2).get(0);
        paint(a, TileType.OBSTACLE, brick.x(), brick.z());
        SliderAI.Route after = SliderAI.route(a, boss, player, 2);
        assertEquals(2, after.legs().size(), "with a brick at " + brick);
        assertEquals(player, after.crushed());
    }

    @Test
    @DisplayName("a brick never seals a player in: they keep two tiles beside them to step onto")
    void bricksNeverSealAPlayerIn() {
        GridPos corner = new GridPos(0, 0);
        GridArena a = arena(corner);
        CombatEntity boss = slider(a, 6, 6);
        SliderAI.Scene scene = SliderAI.Scene.of(a, boss, corner);
        assertTrue(scene.seals(new GridPos(1, 0)), "two ways out of a corner, and this is one of them");
        assertTrue(scene.seals(new GridPos(0, 1)));
        assertFalse(scene.seals(new GridPos(1, 1)), "not beside them");

        GridPos open = new GridPos(4, 10);
        GridArena b = arena(open);
        CombatEntity other = slider(b, 6, 6);
        assertFalse(SliderAI.Scene.of(b, other, open).seals(new GridPos(4, 9)), "four ways out, three left");
        wall(b, 3, 10);
        assertFalse(SliderAI.Scene.of(b, other, open).seals(new GridPos(4, 9)), "three, two left");
        paint(b, TileType.VOID, 5, 10);
        assertTrue(SliderAI.Scene.of(b, other, open).seals(new GridPos(4, 9)), "two, and a hole is no way out");
    }

    @Test
    @DisplayName("when the brick it wants would seal one of a party in, it takes the next tile that does the same job")
    void theBrickThatWouldSealSomeoneIn() {
        // Its target is to the east. Someone else stands against the north edge with stone on
        // their other side, beside the tile that would stop it level with its target.
        GridPos target = new GridPos(11, 2);
        GridPos cornered = new GridPos(5, 0);
        GridArena a = arena(target);
        wall(a, 4, 0);
        CombatEntity boss = slider(a, 6, 6);
        assertEquals(new GridPos(6, 0), SliderAI.bricks(a, boss, target, 1).get(0),
            "fixture: with nobody beside it, that tile is the one it picks");

        a.setAllPlayerGridPositions(List.of(target, cornered));
        assertTrue(SliderAI.Scene.of(a, boss, target).seals(new GridPos(6, 0)), "fixture: it is one of their two ways out");
        assertEquals(new GridPos(7, 0), SliderAI.bricks(a, boss, target, 1).get(0),
            "the other half of the same row, which stops it just as well");
    }

    @Test
    @DisplayName("a brick is asked for as the engine wants it: an obstacle, for four turns, on floor nobody is standing on")
    void theBrickAsTheEngineSeesIt() {
        GridPos player = new GridPos(10, 9);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);
        SliderAI ai = woken(boss, a, player, 3);

        EnemyAction.CreateTerrain brick = assertInstanceOf(EnemyAction.CreateTerrain.class, decide(ai, boss, a, player),
            "bare: there is no run on a build turn for it to share a bundle with");
        assertEquals(TileType.OBSTACLE, brick.terrainType());
        assertEquals(SliderAI.BRICK_TURNS, brick.duration());
        assertEquals(List.of(new GridPos(11, 6), new GridPos(9, 10)), brick.tiles(),
            "one to turn on, and one behind them to stop on");
        assertEquals(TileType.NORMAL, typeAt(a, 11, 6), "it changes no tile itself");
        assertEquals(new GridPos(6, 6), boss.getGridPos(), "and goes nowhere");
        assertNull(boss.getPendingStrikeTile());
    }

    // ── The loop ──

    @Test
    @DisplayName("awake it builds, slides, builds, slides: never two alike running, never a turn of nothing")
    void buildsAndSlidesTurnAbout() {
        for (int first : EACH_PHASE) {
            GridPos player = new GridPos(10, 9);
            GridArena a = arena(player);
            CombatEntity boss = slider(a, 6, 6, 100 * HP);
            SliderAI ai = woken(boss, a, player, 100 * first);

            for (int t = 0; t < 12; t++) {
                String when = "first strike " + first + ", turn " + t;
                boolean slide = t % 2 == 1;
                assertEquals(slide, ai.isSliding(), when);
                EnemyAction action = turn(ai, boss, a, player);
                if (slide) {
                    assertTrue(runOf(action) instanceof EnemyAction.Move || isCrush(action),
                        "a slide that did not slide. " + when + ": " + action);
                    assertNull(ai.getPendingWarning(), "nothing is painted after a slide. " + when);
                    assertTrue(raisedOf(action).isEmpty(), when);
                } else {
                    assertTrue(action instanceof EnemyAction.Idle || action instanceof EnemyAction.CreateTerrain, when);
                    assertTrue(pathOf(action).isEmpty() && !isCrush(action), "it moved on a build turn. " + when);
                    assertNotNull(ai.getPendingWarning(), "a build turn that took no aim. " + when);
                    assertNotNull(ai.aimedWay(), when);
                }
            }
        }
    }

    @Test
    @DisplayName("the build turn paints where the slide to come is dangerous: every tile it would crush someone on, with the arrow of the run that would do it")
    void theSlideIsShown() {
        GridPos player = new GridPos(10, 9);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);
        SliderAI ai = woken(boss, a, player, 3);
        turn(ai, boss, a, player);
        assertArrayEquals(EAST, ai.aimedWay());
        assertEquals(TileType.OBSTACLE, typeAt(a, 11, 6), "fixture: the brick it turns on");
        assertEquals(TileType.OBSTACLE, typeAt(a, 9, 10), "fixture: and the one behind them, to stop on");

        // Its first run, to the brick: its lane, the open tile beside the brick, and the
        // ground past the brick, which its second run reaches by going straight on.
        Set<GridPos> eastward = new HashSet<>(box(8, 6, 10, 7));
        eastward.add(new GridPos(11, 7));
        eastward.addAll(box(12, 6, 13, 7));
        assertEquals(eastward, arrowed(ai, 1, 0));
        // Its second run is aimed when it happens, so the danger is every way that run could
        // go from the brick. Back down its own track,
        assertEquals(box(0, 6, 5, 7), arrowed(ai, -1, 0));
        // north to the edge,
        assertEquals(box(9, 0, 10, 5), arrowed(ai, 0, -1));
        // and south as far as the second brick, with the open tile beside that.
        Set<GridPos> southward = new HashSet<>(box(9, 8, 10, 9));
        southward.add(new GridPos(10, 10));
        assertEquals(southward, arrowed(ai, 0, 1));

        Set<GridPos> unarrowed = new HashSet<>(painted(ai));
        unarrowed.removeAll(arrowed(ai));
        assertEquals(Set.of(new GridPos(7, 5), new GridPos(8, 5), new GridPos(11, 5), new GridPos(7, 8), new GridPos(8, 8),
            new GridPos(11, 8), new GridPos(11, 6)), unarrowed, "what is left of its first run's wake: shaken, and not crushed");
        assertFalse(painted(ai).contains(new GridPos(9, 11)), "past the second brick nothing reaches");
        assertFalse(painted(ai).contains(new GridPos(3, 3)), "nor anywhere out of line with where it stops");
    }

    @Test
    @DisplayName("the slide as painted, against someone who stood still: into the brick, round the corner, and onto them")
    void theSlideAsShown() {
        GridPos player = new GridPos(10, 9);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);
        SliderAI ai = woken(boss, a, player, 3);
        turn(ai, boss, a, player);

        EnemyAction slid = decide(ai, boss, a, player);
        assertEquals(TileType.OBSTACLE, typeAt(a, 11, 6), "it changes no tile itself");
        assertSlideBundle(slid, "the slide");
        EnemyAction.CompositeAction bundle = assertInstanceOf(EnemyAction.CompositeAction.class, slid);
        EnemyAction.CreateTerrain broken = assertInstanceOf(EnemyAction.CreateTerrain.class, bundle.actions().get(0),
            "the brick is made floor first");
        assertEquals(List.of(new GridPos(11, 6)), broken.tiles());
        EnemyAction.MoveAndAttackWithKnockback crush = assertInstanceOf(
            EnemyAction.MoveAndAttackWithKnockback.class, bundle.actions().get(1),
            "one run for both, with the strike where the second one ends");
        assertEquals(List.of(new GridPos(7, 6), new GridPos(8, 6), new GridPos(9, 6), new GridPos(9, 7)), crush.path());
        assertEquals(ATTACK, crush.damage());
        assertEquals(3, crush.knockbackTiles());
        assertEquals(player, boss.getPendingStrikeTile(), "and the engine is told whose it is");

        Set<GridPos> shaken = new HashSet<>(box(7, 5, 11, 5));
        shaken.addAll(box(7, 8, 11, 8));
        assertEquals(shaken, wakeOf(slid), "the first run's wake, both sides, less the row it ran into");
        assertEquals(new GridPos(9, 6), shakeOf(slid).center(), "drawn from where the first run stopped");

        carryOut(slid, boss, a);
        assertEquals(new GridPos(9, 7), boss.getGridPos());
        assertEquals(1, boss.minDistanceTo(player));
        assertEquals(TileType.NORMAL, typeAt(a, 11, 6));
    }

    @Test
    @DisplayName("the second run is aimed when it happens, and the paint said so: someone who steps behind the brick is run down there")
    void theSecondRunFollowsWhoeverMoved() {
        GridPos player = new GridPos(10, 9);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);
        SliderAI ai = woken(boss, a, player, 3);
        turn(ai, boss, a, player);

        GridPos behind = new GridPos(13, 7);
        assertArrayEquals(EAST, ai.danger().arrows().get(behind), "and the ground past the brick was on its map");
        assertFalse(shown(ai).contains(behind), "though nothing on the floor said so: a later run is not shown");
        stand(a, behind);
        EnemyAction slid = turn(ai, boss, a, behind);
        EnemyAction.MoveAndAttackWithKnockback crush = assertInstanceOf(
            EnemyAction.MoveAndAttackWithKnockback.class, runOf(slid));
        assertEquals(List.of(new GridPos(7, 6), new GridPos(8, 6), new GridPos(9, 6), new GridPos(10, 6), new GridPos(11, 6)),
            crush.path(), "to the brick, and straight on through where it stood");
        assertEquals(behind, boss.getPendingStrikeTile());
    }

    @Test
    @DisplayName("a brick the player mines before the slide is not there to stop it: the run goes on to the next thing that does")
    void aMinedBrickChangesTheSlide() {
        GridPos player = new GridPos(10, 9);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);
        SliderAI ai = woken(boss, a, player, 3);
        turn(ai, boss, a, player);
        assertEquals(TileType.OBSTACLE, typeAt(a, 11, 6), "fixture: the brick is up");

        paint(a, TileType.NORMAL, 11, 6);
        EnemyAction slid = turn(ai, boss, a, player);
        EnemyAction.Move slide = assertInstanceOf(EnemyAction.Move.class, runOf(slid),
            "nothing turned it, so in its first phase one run is all it has");
        assertEquals(new GridPos(12, 6), end(slide.path()), "to the edge");
        assertEquals(6, slide.path().size());
        assertTrue(levelledOf(slid).isEmpty());
        assertNull(boss.getPendingStrikeTile());
    }

    @Test
    @DisplayName("the extra runs come at exactly two thirds of its health and at exactly one third")
    void runsAtTheHealthBoundaries() {
        int[][] runsAt = {{61, 1}, {60, 3}, {31, 3}, {30, 5}};
        for (int[] each : runsAt) {
            // Somewhere no run along an edge comes level with, and every brick mined as soon
            // as it is up: so nothing turns it but the edge, and nothing ends its slide early.
            GridPos player = new GridPos(9, 9);
            GridArena a = arena(player);
            CombatEntity boss = slider(a, 0, 0);
            SliderAI ai = woken(boss, a, player, HP - each[0]);
            for (GridPos brick : raisedOf(turn(ai, boss, a, player))) paint(a, TileType.NORMAL, brick.x(), brick.z());

            GridPos stood = boss.getGridPos();
            EnemyAction slid = turn(ai, boss, a, player);
            String when = "with " + each[0] + " health left: " + slid;
            assertFalse(isCrush(slid), "fixture: " + when);
            assertEquals(each[1], stretches(stood, pathOf(slid), when).size(), when);
        }
    }

    @Test
    @DisplayName("a brick crumbles by itself after the second slide it could have served")
    void aBrickCrumbles() {
        // Far enough off its line that it never comes this way: the brick is theirs to watch.
        GridPos player = new GridPos(2, 2);
        GridArena a = arena(player);
        paint(a, TileType.OBSTACLE, 12, 12);
        standing(a).put(new GridPos(12, 12), SliderAI.BRICK_TURNS);
        crumble(a);   // the turn it was raised on
        CombatEntity boss = slider(a, 6, 6);
        SliderAI ai = new SliderAI();
        for (int t = 0; t < 3; t++) {
            assertEquals(TileType.OBSTACLE, typeAt(a, 12, 12), "turn " + t);
            turn(ai, boss, a, player);
        }
        assertEquals(TileType.NORMAL, typeAt(a, 12, 12), "a slide, a build and a slide later");
    }

    @Test
    @DisplayName("shoved off its mark, it does not slide blind: it builds and takes aim again from where it is")
    void shovedOffItsMark() {
        GridPos player = new GridPos(10, 9);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);
        SliderAI ai = woken(boss, a, player, 3);
        turn(ai, boss, a, player);
        assertTrue(ai.isSliding());

        assertTrue(a.moveEntity(boss, new GridPos(2, 2)));
        EnemyAction action = turn(ai, boss, a, player);
        assertTrue(pathOf(action).isEmpty(), "it stayed where it was put");
        assertNotNull(ai.getPendingWarning());
        assertTrue(ai.isSliding(), "and the slide comes next, down what it has just shown");
        SliderAI.Telegraph worked = SliderAI.telegraph(a, boss, player, 1);
        assertEquals(new HashSet<>(worked.tiles()), painted(ai));
    }

    @Test
    @DisplayName("something it does not crush, come to stand against its face, is run into where it stands")
    void somethingInItsWay() {
        GridPos player = new GridPos(10, 9);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);
        SliderAI ai = woken(boss, a, player, 3);
        turn(ai, boss, a, player);
        assertArrayEquals(EAST, ai.aimedWay(), "fixture");

        zombie(a, 8, 6);
        GridPos stood = boss.getGridPos();
        EnemyAction action = turn(ai, boss, a, player);
        EnemyAction.Ram ram = assertInstanceOf(EnemyAction.Ram.class, action, "it ran into it: " + action);
        assertEquals(List.of(new GridPos(8, 6)), ram.tiles());
        assertEquals(ATTACK, ram.damage(), "what a player in its way would take");
        assertEquals(0, ram.afterSteps(), "at once: it was against it already");
        assertEquals(stood, boss.getGridPos(), "and it went nowhere");
        assertFalse(ai.isSliding(), "the slide is spent");

        turn(ai, boss, a, player);
        assertNotNull(ai.getPendingWarning(), "and it builds and takes aim on the turn after, as ever");
        assertTrue(ai.isSliding());
    }

    @Test
    @DisplayName("a creature further off is run into when the run gets there, not when it sets off")
    void runIntoOnArrival() {
        GridPos player = new GridPos(12, 9);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 4, 6);
        SliderAI ai = woken(boss, a, player, 3);
        turn(ai, boss, a, player);
        assertArrayEquals(EAST, ai.aimedWay(), "fixture");

        zombie(a, 9, 7);
        EnemyAction action = turn(ai, boss, a, player);
        assertSlideBundle(action, "the slide");
        assertEquals(new GridPos(7, 6), boss.getGridPos(), "it stopped in front of it");
        List<EnemyAction.Ram> rams = ramsOf(action);
        assertEquals(1, rams.size(), "one creature, run into once: " + action);
        assertEquals(List.of(new GridPos(9, 7)), rams.get(0).tiles());
        assertEquals(3, pathOf(action).size(), "fixture: three tiles to get there");
        assertEquals(3, rams.get(0).afterSteps(), "as the third step lands");
        List<EnemyAction> parts = EnemyAction.flatten(action);
        assertTrue(parts.indexOf(runOf(action)) < parts.indexOf(rams.get(0)),
            "after the run in the bundle, which is what makes the engine hold it back");
    }

    @Test
    @DisplayName("with runs to spare it runs into a creature once and goes another way: it does not stand there battering it")
    void runIntoOnceASlide() {
        GridPos player = new GridPos(12, 9);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 4, 6);
        zombie(a, 8, 7);

        List<SliderAI.Leg> legs = SliderAI.route(a, boss, player, 2).legs();
        assertEquals(3, legs.size(), "fixture: all three of its runs. " + legs);
        assertEquals(List.of(new GridPos(8, 7)), legs.get(0).rammed(), "the first runs into it");
        assertEquals(2, legs.get(0).steps());
        for (SliderAI.Leg later : legs.subList(1, legs.size())) {
            assertTrue(later.rammed().isEmpty(), "and no run after it does so again: " + later);
            assertTrue(later.steps() > 0, "nor stands against it going nowhere: " + later);
        }
    }

    @Test
    @DisplayName("what a run hits is struck as that run ends, though the slide goes on from there")
    void runIntoMidSlide() {
        GridPos player = new GridPos(12, 9);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 4, 6);
        SliderAI ai = woken(boss, a, player, 30);
        turn(ai, boss, a, player);
        int[] aimed = ai.aimedWay();
        assertNotNull(aimed, "fixture: it took aim");

        // Something walks into its lane after the paint went down, three tiles off.
        GridPos from = boss.getGridPos();
        // Its face is two tiles on from its corner going east or south, and one going back.
        int ahead = aimed[0] + aimed[1] > 0 ? 5 : -4;
        GridPos inTheWay = new GridPos(from.x() + (aimed[0] != 0 ? ahead : 0), from.z() + (aimed[1] != 0 ? ahead : 0));
        assertTrue(open(a, boss, inTheWay) && a.getOccupant(inTheWay) == null && !inTheWay.equals(player),
            "fixture: room for a creature at " + inTheWay);
        zombie(a, inTheWay.x(), inTheWay.z());
        SliderAI.Leg first = SliderAI.leg(a, boss, player, from, aimed[0], aimed[1]);
        assertEquals(List.of(inTheWay), first.rammed(), "fixture: its first run ends against it. " + first);
        assertTrue(first.steps() > 0, "fixture: after going some way. " + first);

        EnemyAction action = turn(ai, boss, a, player);
        assertSlideBundle(action, "the slide");
        List<EnemyAction.Ram> rams = ramsOf(action);
        assertEquals(1, rams.size(), "run into once: " + action);
        assertEquals(List.of(inTheWay), rams.get(0).tiles());
        assertEquals(first.steps(), rams.get(0).afterSteps(), "as its first run ends");
        assertTrue(pathOf(action).size() > first.steps(), "fixture: and the slide went on from there. " + action);
    }

    @Test
    @DisplayName("a sentry goes off when it is run into, and anything else takes the blow")
    void whatGoesOff() {
        GridPos player = new GridPos(2, 2);
        GridArena a = arena(player);
        CombatEntity mine = sentry(a, 9, 9);
        CombatEntity bystander = zombie(a, 10, 3);
        CombatEntity pet = zombie(a, 11, 3);
        pet.setAlly(true);

        assertEquals(SentryAI.BLAST_RADIUS, SliderAI.blastOf(mine));
        assertEquals(-1, SliderAI.blastOf(bystander));
        assertEquals(-1, SliderAI.blastOf(pet), "nobody's pet is a bomb");
        EnemyAction.Explode burst = new SentryAI().whenRammed(mine);
        assertEquals(BLAST, burst.damage(), "its own blast, the one it would have walked over");
        assertEquals(SentryAI.BLAST_RADIUS, burst.radius());
    }

    @Test
    @DisplayName("ground a blast would reach is painted, with no arrow: beside a sentry the slide would run into")
    void blastGroundIsPainted() {
        // Its target stands past the sentry, in its lane. It sets off east, runs into the
        // sentry two tiles on and goes no further.
        GridPos player = new GridPos(12, 6);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);
        sentry(a, 10, 6);

        SliderAI.Telegraph shown = SliderAI.telegraph(a, boss, player, 1);
        GridPos behind = new GridPos(11, 6);
        assertTrue(shown.tiles().contains(behind), "the tile past the sentry, which the blast reaches: " + shown);
        assertNull(shown.arrows().get(behind), "nothing is crushed there");
        assertFalse(shown.tiles().contains(player), "two tiles off, it is out of reach");

        // The same floor with something there that only takes the blow.
        GridArena b = arena(player);
        CombatEntity other = slider(b, 6, 6);
        zombie(b, 10, 6);
        assertFalse(SliderAI.telegraph(b, other, player, 1).tiles().contains(behind), "no blast, no paint");
    }

    @Test
    @DisplayName("a sentry nothing runs into paints nothing")
    void aSentryOutOfTheWayPaintsNothing() {
        GridPos player = new GridPos(12, 6);
        GridArena plain = arena(player);
        CombatEntity alone = slider(plain, 6, 6);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);
        sentry(a, 3, 12);
        assertEquals(new HashSet<>(SliderAI.telegraph(plain, alone, player, 1).tiles()),
            new HashSet<>(SliderAI.telegraph(a, boss, player, 1).tiles()));
    }

    // ── Sentries ──

    /** The sentry a build turn asked for, or null when it asked for none. */
    private static EnemyAction.SummonMinions calledUp(EnemyAction action) {
        for (EnemyAction part : EnemyAction.flatten(action)) {
            if (part instanceof EnemyAction.SummonMinions summon) return summon;
        }
        return null;
    }

    @Test
    @DisplayName("with its bricks it calls up a sentry: beside a brick, on open floor, never against a player")
    void callsUpASentry() {
        int called = 0;
        Random dice = new Random(41);
        for (int i = 0; i < 60; i++) {
            GridPos player = new GridPos(dice.nextInt(SIZE), dice.nextInt(SIZE));
            if (GridArena.getOccupiedTiles(new GridPos(6, 6), 2, 2).contains(player)) continue;
            GridArena a = arena(player);
            CombatEntity boss = slider(a, 6, 6);
            SliderAI ai = woken(boss, a, player, 3);
            ai.withSentries(true);
            EnemyAction action = decide(ai, boss, a, player);
            List<GridPos> bricks = raisedOf(action);
            EnemyAction.SummonMinions summon = calledUp(action);
            String where = "arena " + i + ": " + action;
            if (bricks.isEmpty()) {
                assertNull(summon, "no brick, no sentry. " + where);
                continue;
            }
            if (summon == null) continue;
            called++;
            assertEquals(SliderAI.SENTRY_TYPE, summon.entityTypeId(), where);
            assertEquals(1, summon.count(), where);
            assertEquals(1, summon.positions().size(), where);
            assertEquals(SliderAI.SENTRY_HP, summon.hp(), where);
            assertEquals(BLAST, summon.atk(), "two thirds of a crush. " + where);
            GridPos post = summon.positions().get(0);
            assertEquals(TileType.NORMAL, typeAt(a, post.x(), post.z()), where);
            assertNull(a.getOccupant(post), where);
            assertFalse(bricks.contains(post), "on a brick. " + where);
            assertTrue(boss.minDistanceTo(post) > 0, "under itself. " + where);
            assertTrue(Math.abs(post.x() - player.x()) + Math.abs(post.z() - player.z()) >= SliderAI.SENTRY_CLEARANCE,
                "against a player, where it would go off unannounced. " + where);
            boolean besideABrick = false;
            for (GridPos brick : bricks) {
                besideABrick |= Math.abs(brick.x() - post.x()) + Math.abs(brick.z() - post.z()) == 1;
            }
            assertTrue(besideABrick, "not beside any brick. " + where);
            List<EnemyAction> parts = EnemyAction.flatten(action);
            assertTrue(parts.indexOf(summon) > 0 && parts.get(0) instanceof EnemyAction.CreateTerrain,
                "the bricks go up first. " + where);
        }
        assertTrue(called > 20, "fixture: only " + called + " sentries to look at");
    }

    @Test
    @DisplayName("a sentry is never called into the way of the slide it has just aimed")
    void aSentryDoesNotSpoilTheSlide() {
        Random dice = new Random(43);
        int looked = 0;
        for (int i = 0; i < 60; i++) {
            GridPos player = new GridPos(dice.nextInt(SIZE), dice.nextInt(SIZE));
            if (GridArena.getOccupiedTiles(new GridPos(6, 6), 2, 2).contains(player)) continue;
            GridArena a = arena(player);
            CombatEntity boss = slider(a, 6, 6);
            SliderAI ai = woken(boss, a, player, 3);
            ai.withSentries(true);
            EnemyAction built = decide(ai, boss, a, player);
            EnemyAction.SummonMinions summon = calledUp(built);
            if (summon == null) continue;
            looked++;
            carryOut(built, boss, a);
            SliderAI.Route without = SliderAI.route(a, boss, player, 1);
            sentry(a, summon.positions().get(0).x(), summon.positions().get(0).z());
            assertEquals(without, SliderAI.route(a, boss, player, 1),
                "the slide is not what it was with the sentry standing there. arena " + i);
        }
        assertTrue(looked > 20, "fixture: only " + looked + " to look at");
    }

    @Test
    @DisplayName("it keeps no more sentries standing than its phase allows: one, then two, then three")
    void noMoreSentriesThanItsPhaseAllows() {
        assertEquals(1, SliderAI.sentriesFor(1));
        assertEquals(2, SliderAI.sentriesFor(2));
        assertEquals(3, SliderAI.sentriesFor(3));

        GridPos player = new GridPos(11, 10);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 4, 4);
        SliderAI ai = woken(boss, a, player, 3);
        ai.withSentries(true);
        EnemyAction first = decide(ai, boss, a, player);
        assertNotNull(calledUp(first), "fixture: a build that calls one up. " + first);

        // The same build with one standing already.
        GridArena b = arena(player);
        CombatEntity other = slider(b, 4, 4);
        SliderAI again = woken(other, b, player, 3);
        again.withSentries(true);
        sentry(b, 1, 12);
        assertEquals(1, SliderAI.sentriesStanding(b, other));
        EnemyAction second = decide(again, other, b, player);
        assertFalse(raisedOf(second).isEmpty(), "fixture: it still builds. " + second);
        assertNull(calledUp(second), "one is all it has in its first phase");
    }

    @Test
    @DisplayName("without the Aether there are no sentries to call, and a build is bricks alone")
    void noSentriesWithoutTheAether() {
        GridPos player = new GridPos(11, 10);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 4, 4);
        SliderAI ai = woken(boss, a, player, 3);
        EnemyAction action = decide(ai, boss, a, player);
        assertFalse(raisedOf(action).isEmpty(), "fixture: " + action);
        assertInstanceOf(EnemyAction.CreateTerrain.class, action);
    }

    // ── How it moves ──

    @Test
    @DisplayName("a run starts slow and gathers speed, and every run starts again from slow")
    void gathersSpeed() {
        int before = Integer.MAX_VALUE;
        for (int into = 0; into < 12; into++) {
            int ticks = SliderAI.runTicks(into, false);
            assertTrue(ticks >= 1 && ticks <= before, "it slowed down, " + into + " tiles in");
            before = ticks;
        }
        assertTrue(SliderAI.runTicks(0, false) >= 3 * SliderAI.runTicks(8, false), "off the mark it crawls");
        assertEquals(1, SliderAI.runTicks(8, false), "and at full speed it is a tile a tick");
        assertTrue(SliderAI.runTicks(0, true) < SliderAI.runTicks(0, false), "red, it is quicker off the mark");

        // One path, two runs: three tiles east, then two south.
        GridPos start = new GridPos(6, 6);
        List<GridPos> path = List.of(new GridPos(7, 6), new GridPos(8, 6), new GridPos(9, 6),
            new GridPos(9, 7), new GridPos(9, 8));
        int[] into = {0, 1, 2, 0, 1};
        boolean[] ends = {false, false, true, false, true};
        for (int i = 0; i < path.size(); i++) {
            assertEquals(into[i], SliderAI.stepOfRun(start, path, i), "step " + i);
            assertEquals(ends[i], SliderAI.endsRun(start, path, i), "step " + i);
        }
        CombatEntity boss = slider(arena(new GridPos(1, 1)), 6, 6);
        SliderAI ai = new SliderAI();
        assertEquals(ai.stepTicks(boss, start, path, 0, 4), ai.stepTicks(boss, start, path, 3, 4),
            "the corner is a standing start");
        assertTrue(ai.stepTicks(boss, start, path, 2, 4) < ai.stepTicks(boss, start, path, 0, 4));
    }

    @Test
    @DisplayName("a long run hits harder than a short one, and no run shakes the camera off its mount")
    void hitsHarderTheFurtherItCame() {
        assertTrue(SliderAI.impactShake(1) > 0f);
        assertTrue(SliderAI.impactShake(8) > SliderAI.impactShake(2));
        assertTrue(SliderAI.impactShake(40) <= 1.0f);
    }

    /** A fight brought as far as its first paint, which can be had again as often as wanted. */
    private record Painted(GridArena a, CombatEntity boss, SliderAI ai) {
        static Painted fight(int[] where, int first) {
            GridPos player = new GridPos(where[2], where[3]);
            GridArena a = arena(player);
            wall(a, 3, 3);
            wall(a, 10, 4);
            paint(a, TileType.OBSTACLE, 4, 11);
            CombatEntity boss = slider(a, where[0], where[1], 100 * HP);
            SliderAI ai = woken(boss, a, player, 100 * first);
            turn(ai, boss, a, player);
            return new Painted(a, boss, ai);
        }
    }

    @Test
    @DisplayName("the paint is the danger and nothing but: wherever a player alone ends their turn, they are crushed if that tile carried an arrow and not if it did not, and shaken only on paint")
    void thePaintIsTheDanger() {
        // Where it stands and where they stood when it painted.
        int[][] fights = {{6, 6, 10, 9}, {0, 0, 9, 9}, {4, 6, 9, 8}, {11, 2, 2, 12}};
        int crushed = 0;
        int spared = 0;
        for (int first : EACH_PHASE) {
            for (int[] where : fights) {
                Painted shown = Painted.fight(where, first);
                Set<GridPos> arrows = arrowed(shown.ai());
                Set<GridPos> paint = painted(shown.ai());
                for (int x = 0; x < SIZE; x++) {
                    for (int z = 0; z < SIZE; z++) {
                        // The same fight again, and this time they walk to this tile.
                        GridPos tile = new GridPos(x, z);
                        Painted again = Painted.fight(where, first);
                        if (!open(again.a(), again.boss(), tile)) continue;
                        stand(again.a(), tile);
                        EnemyAction slid = turn(again.ai(), again.boss(), again.a(), tile);
                        String when = "first strike " + first + ", it at " + where[0] + "," + where[1] + ", painted for a player at "
                            + where[2] + "," + where[3] + " who then stood at " + tile + ": " + slid;
                        assertEquals(arrows.contains(tile), isCrush(slid), when);
                        if (wakeOf(slid).contains(tile)) assertTrue(paint.contains(tile), "shaken on ground with no paint. " + when);
                        if (isCrush(slid)) crushed++;
                        else spared++;
                    }
                }
            }
        }
        System.out.println("PAINT tiles tried: crushed on " + crushed + ", spared on " + spared);
        assertTrue(crushed > 300 && spared > 300, "fixture: " + crushed + " crushed, " + spared + " spared");
    }

    @Test
    @DisplayName("with a party the paint allows for the others getting out of its way: someone it only reaches if its target steps aside is on marked ground")
    void thePaintForAParty() {
        // Its target is due east. Behind them is the brick that will stop it, and due south
        // of where that leaves it stands someone else.
        GridPos target = new GridPos(10, 6);
        GridPos other = new GridPos(9, 11);
        GridArena a = arena(target);
        a.setAllPlayerGridPositions(List.of(target, other));
        CombatEntity boss = slider(a, 6, 6);
        SliderAI ai = woken(boss, a, target, 3);
        turn(ai, boss, a, target);

        assertArrayEquals(EAST, ai.aimedWay(), "fixture: it goes for its target");
        assertArrayEquals(EAST, ai.danger().arrows().get(target));
        // With everyone where they are, its first run ends on its target and that is the
        // slide. But they will not be there.
        assertArrayEquals(SOUTH, ai.danger().arrows().get(other), "on its map: what comes if the one in front steps aside");

        GridPos aside = new GridPos(12, 3);
        a.setPlayerGridPos(aside);
        a.setAllPlayerGridPositions(List.of(aside, other));
        assertTrue(isCrush(turn(ai, boss, a, aside)));
        assertEquals(other, boss.getPendingStrikeTile(), "which is what came");
    }

    @Test
    @DisplayName("hit across a third after it has painted, it slides with the runs it painted for, and has its extra ones from the slide after")
    void theRunsAreFixedWhenItPaints() {
        GridPos player = new GridPos(9, 9);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 0, 0);
        SliderAI ai = woken(boss, a, player, HP - 61);
        for (GridPos brick : raisedOf(turn(ai, boss, a, player))) paint(a, TileType.NORMAL, brick.x(), brick.z());

        strike(boss, 1);
        assertEquals(2, SliderAI.phase(boss.getCurrentHp(), boss.getMaxHp()), "fixture: across the line, paint already down");
        GridPos stood = boss.getGridPos();
        EnemyAction slid = turn(ai, boss, a, player);
        assertEquals(1, stretches(stood, pathOf(slid), "").size(), "one run, as painted: " + slid);

        for (GridPos brick : raisedOf(turn(ai, boss, a, player))) paint(a, TileType.NORMAL, brick.x(), brick.z());
        stood = boss.getGridPos();
        slid = turn(ai, boss, a, player);
        assertEquals(3, stretches(stood, pathOf(slid), "").size(), "and three the next time: " + slid);
    }

    @Test
    @DisplayName("a brick on its last turn is not counted on: it will have crumbled before the slide, and the paint is for the floor as it will be")
    void aBrickOnItsLastTurnIsNotCountedOn() {
        // Due east of it with a brick between. While the brick stands it stops the first run
        // and earns a second, which could turn north. Once it is gone, there is one run and
        // it goes straight through to them.
        GridPos player = new GridPos(13, 6);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);
        GridTile brick = new GridTile(TileType.OBSTACLE, null);
        a.setTile(new GridPos(10, 6), brick);
        GridPos northOfTheStop = new GridPos(8, 2);

        brick.setTurnsRemaining(2);
        assertNotNull(SliderAI.telegraph(a, boss, player, 1).arrows().get(northOfTheStop), "standing for the slide");
        brick.setTurnsRemaining(1);
        assertNull(SliderAI.telegraph(a, boss, player, 1).arrows().get(northOfTheStop), "gone before it");
        assertArrayEquals(EAST, SliderAI.telegraph(a, boss, player, 1).arrows().get(new GridPos(10, 6)),
            "and the tile it stands on is ground like any other by then");
    }

    @Test
    @DisplayName("it starts in the middle of its room, and says what it builds and nothing about what it breaks")
    void whereItStartsAndWhatItSays() {
        SliderAI ai = new SliderAI();
        assertTrue(ai.spawnsAtCenter());
        assertEquals("§6  The Slider raises a brick.", ai.describeTerrain(TileType.OBSTACLE, 1));
        assertEquals("§6  The Slider raises 3 bricks.", ai.describeTerrain(TileType.OBSTACLE, 3));
        assertEquals("", ai.describeTerrain(TileType.NORMAL, 4), "nothing at all, which is not the same as the engine's own line");
        assertNull(ai.describeTerrain(TileType.VOID, 1), "anything else is the engine's to describe");
    }

    @Test
    @DisplayName("a box of their own blocks is no shelter: walls somebody put down do not count as hemming them in, and it comes through")
    void aBoxOfTheirOwnBlocksIsNoShelter() {
        for (int first : EACH_PHASE) {
            GridPos player = new GridPos(10, 9);
            GridArena a = arena(player);
            CombatEntity boss = slider(a, 6, 6, 100 * HP);
            SliderAI ai = new SliderAI();
            strike(boss, 100 * first);
            int crushes = 0;
            for (int t = 0; t < 16; t++) {
                // Walled in on all four sides, and whatever it breaks is put back every turn.
                for (int[] way : WAYS) {
                    GridPos side = new GridPos(player.x() + way[0], player.z() + way[1]);
                    if (open(a, boss, side)) paint(a, TileType.OBSTACLE, side.x(), side.z());
                }
                if (isCrush(turn(ai, boss, a, player))) crushes++;
            }
            assertTrue(crushes >= 3, "first strike " + first + ": sixteen turns in their box, and only " + crushes + " crushes");
        }
    }

    @Test
    @DisplayName("what is shown is read off the board as it stands: mine the brick in its lane, and the lane on the floor runs on to the next thing that stops it")
    void thePaintFollowsTheBoard() {
        GridPos player = new GridPos(10, 9);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);
        SliderAI ai = woken(boss, a, player, 3);
        turn(ai, boss, a, player);
        BossWarning warning = ai.getPendingWarning();
        assertEquals(box(8, 6, 10, 7), shownArrows(ai), "fixture: its lane, as far as the brick");
        assertArrayEquals(NORTH, ai.danger().arrows().get(new GridPos(9, 3)), "fixture: with the brick up it can turn north off it");
        assertArrayEquals(SOUTH, ai.danger().arrows().get(player), "fixture: or south, onto them");

        paint(a, TileType.NORMAL, 11, 6);
        assertEquals(box(8, 6, 13, 7), shownArrows(ai), "with the brick gone the lane on the floor runs to the edge");
        assertArrayEquals(EAST, warning.arrowAt(new GridPos(11, 6)), "over the tile the brick stood on");
        assertNull(ai.danger().arrows().get(new GridPos(9, 3)), "and its map follows too: it has one run now, and no turn north");
        assertFalse(painted(ai).contains(player), "so they are out of its way where they stand");

        EnemyAction slid = turn(ai, boss, a, player);
        assertFalse(isCrush(slid), "and it is as shown");
        assertEquals(new GridPos(12, 6), boss.getGridPos());
    }

    @Test
    @DisplayName("what it shows is its first run and nothing after it: arrows down its lane, its wake with none, and not a mark for where it turns or where a later run lands")
    void whatIsShownIsItsFirstRun() {
        GridPos player = new GridPos(10, 9);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);
        SliderAI ai = woken(boss, a, player, 3);
        turn(ai, boss, a, player);
        assertArrayEquals(EAST, ai.aimedWay());
        assertEquals(TileType.OBSTACLE, typeAt(a, 11, 6), "fixture: the brick it means to turn on");
        BossWarning warning = ai.getPendingWarning();

        // The lane: the ground its body crosses from where it sits to the brick.
        assertEquals(box(8, 6, 10, 7), shownArrows(ai));
        for (GridPos tile : shownArrows(ai)) assertArrayEquals(EAST, warning.arrowAt(tile), "every arrow points the way it sets off: " + tile);

        // Its wake: both sides of that run and the row it stops against, with no arrow.
        Set<GridPos> wake = new HashSet<>(box(7, 5, 11, 5));
        wake.addAll(box(7, 8, 11, 8));
        wake.addAll(box(11, 6, 11, 7));
        Set<GridPos> unarrowed = new HashSet<>(shown(ai));
        unarrowed.removeAll(shownArrows(ai));
        assertEquals(wake, unarrowed);

        // And that is all. Where it turns off the brick is on its map, and not on the floor.
        assertArrayEquals(SOUTH, ai.danger().arrows().get(player), "its second run would come down on them");
        assertFalse(shown(ai).contains(player), "and nothing says so");
        assertArrayEquals(NORTH, ai.danger().arrows().get(new GridPos(9, 3)));
        assertFalse(shown(ai).contains(new GridPos(9, 3)));
        assertArrayEquals(EAST, ai.danger().arrows().get(new GridPos(13, 6)), "straight on through the brick");
        assertFalse(shown(ai).contains(new GridPos(13, 6)));
        assertTrue(painted(ai).containsAll(shown(ai)), "what is shown is part of its map");
        assertTrue(painted(ai).size() > 2 * shown(ai).size(), "and the lesser part");

        // They stood still, and the run that was not shown is the one that lands.
        assertTrue(isCrush(turn(ai, boss, a, player)));
        assertEquals(player, boss.getPendingStrikeTile());
        assertNull(ai.getPendingWarning(), "nothing is shown after a slide");
    }

    @Test
    @DisplayName("whoever stands in the way of its first run is shown it: the lane stops at them, and their own tile carries the arrow")
    void whoeverIsInItsWayIsShownIt() {
        GridPos player = new GridPos(10, 7);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 4, 6);
        SliderAI ai = woken(boss, a, player, 3);
        turn(ai, boss, a, player);
        assertArrayEquals(EAST, ai.aimedWay());

        Set<GridPos> lane = new HashSet<>(box(6, 6, 9, 7));
        lane.add(player);
        assertEquals(lane, shownArrows(ai), "as far as them, and them");

        // They step out of it, and the lane runs on over where they stood to whatever stops it next.
        GridPos aside = new GridPos(10, 10);
        stand(a, aside);
        assertTrue(shownArrows(ai).contains(player), "the tile they left is lane now");
        assertFalse(shownArrows(ai).contains(aside));
        assertTrue(shownArrows(ai).size() > lane.size(), "and it goes on past it");
    }

    // ── What a slide does on its way ──

    @Test
    @DisplayName("a hole in its way is made floor ahead of the run, by the engine and not by it, and it does not stop or turn")
    void fillsTheHolesInItsWay() {
        // Due south of it, at the far edge: one run, straight down onto them.
        GridPos player = new GridPos(6, 12);
        for (TileType hole : List.of(TileType.VOID, TileType.DEEP_WATER)) {
            GridArena a = arena(player);
            paint(a, hole, 6, 9);
            paint(a, hole, 8, 9);    // beside its lane
            CombatEntity boss = slider(a, 6, 2);
            SliderAI ai = woken(boss, a, player, 3);
            turn(ai, boss, a, player);
            assertArrayEquals(SOUTH, ai.aimedWay(), "fixture: " + hole);

            EnemyAction slid = decide(ai, boss, a, player);
            assertEquals(hole, typeAt(a, 6, 9), hole + ": it changes no tile itself");
            assertTrue(levelledOf(slid).contains(new GridPos(6, 9)), hole.toString());
            assertFalse(levelledOf(slid).contains(new GridPos(8, 9)), hole + ": its passing fills nothing beside it");
            carryOut(slid, boss, a);
            assertEquals(TileType.NORMAL, typeAt(a, 6, 9));
            assertEquals(hole, typeAt(a, 8, 9));
        }
    }

    @Test
    @DisplayName("with a party, the tile named is the one it ran into, and the one beside its lane is only shaken")
    void theStrikeIsForWhoeverStoppedIt() {
        GridPos inLane = new GridPos(11, 6);
        GridPos beside = new GridPos(9, 5);
        GridArena a = arena(inLane);
        a.setAllPlayerGridPositions(List.of(inLane, beside));
        CombatEntity boss = slider(a, 6, 6);
        SliderAI ai = woken(boss, a, inLane, 3);
        turn(ai, boss, a, inLane);

        EnemyAction slid = turn(ai, boss, a, inLane);
        assertTrue(isCrush(slid));
        assertEquals(inLane, boss.getPendingStrikeTile());
        assertTrue(wakeOf(slid).contains(beside));
        assertFalse(wakeOf(slid).contains(inLane), "crushed, and so not shaken as well");
    }

    @Test
    @DisplayName("nobody is shaken and crushed in one turn: passed by the first run, then run down by a later one")
    void neverShakenAndCrushed() {
        // Against the side of the lane its first run takes to the brick, and crushed from
        // there by the second, which does not have to move to do it.
        GridPos player = new GridPos(9, 8);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 4, 6);
        SliderAI ai = woken(boss, a, player, 3);
        turn(ai, boss, a, player);
        assertArrayEquals(EAST, ai.aimedWay(), "fixture");

        GridPos stood = boss.getGridPos();
        EnemyAction slid = turn(ai, boss, a, player);
        assertTrue(isCrush(slid), "fixture: " + slid);
        int ran = pathOf(slid).size();
        assertTrue(ran > 0, "fixture: its first run has to go somewhere to shake anything");
        List<GridPos> raw = SliderAI.wake(a, boss, new SliderAI.Lane(stood, 1, 0, ran), ran);
        assertTrue(raw.contains(player), "fixture: they stood where its passing shakes");
        assertFalse(wakeOf(slid).isEmpty(), "and it did shake the rest");
        assertFalse(wakeOf(slid).contains(player), "but not them: " + slid);
    }

    // ── The wake ──

    @Test
    @DisplayName("its passing shakes every tile against the ground it crosses: both sides, the corners, and the row past where it stops")
    void wakeGeometry() {
        GridArena a = arena(new GridPos(12, 12));
        CombatEntity boss = slider(a, 4, 4);
        SliderAI.Lane east = new SliderAI.Lane(new GridPos(4, 4), 1, 0, 3);

        Set<GridPos> expected = new HashSet<>();
        expected.addAll(box(6, 3, 8, 3));     // one side
        expected.addAll(box(6, 6, 8, 6));     // the other
        expected.addAll(box(9, 4, 9, 5));     // the row past where it stops
        expected.add(new GridPos(9, 3));      // the corners beyond that
        expected.add(new GridPos(9, 6));
        expected.add(new GridPos(5, 3));      // and the ones it leaves behind, beside where it stood
        expected.add(new GridPos(5, 6));
        List<GridPos> wake = SliderAI.wake(a, boss, east, 3);
        assertEquals(expected, new HashSet<>(wake));
        assertEquals(12, wake.size(), "each tile once");
        for (GridPos tile : east.tiles(2, 2)) {
            assertFalse(wake.contains(tile), "the lane is the crush's ground, not the wake's: " + tile);
        }
        for (GridPos tile : box(4, 4, 5, 5)) {
            assertFalse(wake.contains(tile), "nobody can be standing under it: " + tile);
        }
        assertEquals(wake, SliderAI.wake(a, boss, east, 9), "a lane has no more tiles than its length");
        assertTrue(SliderAI.wake(a, boss, east, 0).isEmpty(), "no ground crossed, nothing shaken");

        // The same shape whichever way it faces.
        GridArena b = arena(new GridPos(12, 12));
        CombatEntity other = slider(b, 4, 8);
        SliderAI.Lane north = new SliderAI.Lane(new GridPos(4, 8), 0, -1, 3);
        Set<GridPos> turned = new HashSet<>();
        turned.addAll(box(3, 4, 3, 8));
        turned.addAll(box(6, 4, 6, 8));
        turned.addAll(box(4, 4, 5, 4));
        assertEquals(turned, new HashSet<>(SliderAI.wake(b, other, north, 3)));
    }

    @Test
    @DisplayName("stopped short of where it could have gone, it shakes only beside the ground it did cross, and never the lane ahead of it")
    void wakeOfAShortRun() {
        GridArena a = arena(new GridPos(12, 12));
        CombatEntity boss = slider(a, 4, 4);
        SliderAI.Lane lane = new SliderAI.Lane(new GridPos(4, 4), 1, 0, 3);

        Set<GridPos> oneTile = new HashSet<>();
        oneTile.addAll(box(5, 3, 7, 3));
        oneTile.addAll(box(5, 6, 7, 6));
        assertEquals(oneTile, new HashSet<>(SliderAI.wake(a, boss, lane, 1)),
            "the tiles ahead of it are lane still: whoever stopped it there is crushed, and hit once");

        Set<GridPos> twoTiles = new HashSet<>();
        twoTiles.addAll(box(5, 3, 8, 3));
        twoTiles.addAll(box(5, 6, 8, 6));
        assertEquals(twoTiles, new HashSet<>(SliderAI.wake(a, boss, lane, 2)));
    }

    @Test
    @DisplayName("the wake is clipped at the edge of the arena")
    void wakeAtTheEdge() {
        GridArena a = arena(new GridPos(12, 12));
        CombatEntity inCorner = slider(a, 0, 0);
        Set<GridPos> expected = new HashSet<>();
        expected.addAll(box(2, 1, 2, 5));     // the one side there is
        expected.addAll(box(0, 5, 1, 5));     // and the row past the end
        List<GridPos> wake = SliderAI.wake(a, inCorner, new SliderAI.Lane(new GridPos(0, 0), 0, 1, 3), 3);
        assertEquals(expected, new HashSet<>(wake));
        for (GridPos tile : wake) assertNotNull(a.getTile(tile), "off the arena: " + tile);

        // Run out to the far edge, and there is no row past the end to shake.
        GridArena b = arena(new GridPos(0, 0));
        CombatEntity atEdge = slider(b, 10, 6);
        Set<GridPos> sides = new HashSet<>();
        sides.addAll(box(11, 5, 13, 5));
        sides.addAll(box(11, 8, 13, 8));
        assertEquals(sides, new HashSet<>(
            SliderAI.wake(b, atEdge, new SliderAI.Lane(new GridPos(10, 6), 1, 0, 2), 2)));
    }

    // ── The rules, held to every turn ──

    /** What every tile of an arena is and whether the arena holds it as permanent, to look back at. */
    private record Ground(TileType[][] type, boolean[][] permanent) {
        static Ground of(GridArena a) {
            int size = a.getWidth();
            Ground was = new Ground(new TileType[size][size], new boolean[size][size]);
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    was.type[x][z] = a.getTile(x, z).getType();
                    was.permanent[x][z] = a.getTile(x, z).isPermanent();
                }
            }
            return was;
        }

        boolean inside(GridPos tile) {
            return tile.x() >= 0 && tile.x() < type.length && tile.z() >= 0 && tile.z() < type.length;
        }

        /** Whether the floor is tile for tile what it was in {@code other}. */
        boolean sameAs(Ground other) {
            return java.util.Arrays.deepEquals(type, other.type);
        }

        /** Something standing on the floor there: what stops a run and is broken by it. */
        boolean block(GridPos tile) {
            return inside(tile) && isBlock(type[tile.x()][tile.z()]);
        }

        /** Any other ground there it could not walk on: what a run fills as it crosses. */
        boolean hole(GridPos tile) {
            return inside(tile) && !type[tile.x()][tile.z()].walkable && !isBlock(type[tile.x()][tile.z()]);
        }

        /** Ground there someone could step onto. */
        boolean walkable(GridPos tile) {
            return inside(tile) && type[tile.x()][tile.z()].walkable;
        }
    }

    /** What a warning showed at the moment it was read: its tiles, and the arrow on each that had one. */
    private record Paint(Set<GridPos> tiles, Map<GridPos, int[]> arrows) {
        static Paint of(BossWarning warning) {
            if (warning == null) return null;
            Set<GridPos> tiles = new HashSet<>(warning.getAffectedTiles());
            Map<GridPos, int[]> arrows = new HashMap<>();
            for (GridPos tile : tiles) {
                if (warning.arrowAt(tile) != null) arrows.put(tile, warning.arrowAt(tile));
            }
            return new Paint(tiles, arrows);
        }

        int[] arrowAt(GridPos tile) {
            return arrows.get(tile);
        }

        Set<GridPos> getAffectedTiles() {
            return tiles;
        }
    }

    /** One straight stretch of a path: which way, and where it starts and ends. */
    private record Stretch(int dx, int dz, GridPos from, GridPos to) {}

    /** A path from {@code start} cut into its straight stretches. More than one step at a time is not a path. */
    private static List<Stretch> stretches(GridPos start, List<GridPos> path, String where) {
        List<Stretch> all = new ArrayList<>();
        GridPos at = start;
        GridPos from = start;
        int[] way = null;
        for (GridPos step : path) {
            int dx = step.x() - at.x();
            int dz = step.z() - at.z();
            assertEquals(1, Math.abs(dx) + Math.abs(dz), "a step that is not one tile along one axis. " + where);
            if (way != null && (way[0] != dx || way[1] != dz)) {
                all.add(new Stretch(way[0], way[1], from, at));
                from = at;
            }
            way = new int[]{dx, dz};
            at = step;
        }
        if (way != null) all.add(new Stretch(way[0], way[1], from, at));
        return all;
    }

    /**
     * One of its turns, held to the rules. Nothing here asks the AI what it did: what kind of
     * turn it was due, where a run had to stop and what it was entitled to break are worked
     * out from the grid as it stood, and the action is held to that.
     *
     * <p>Asleep it does nothing. Awake it builds and slides turn about. A build raises no more
     * bricks than its phase allows, on plain floor nobody stands on, sealing nobody in, moves
     * nothing, hurts nobody, and leaves the slide to come on show. A slide is one path of
     * straight runs, no more than its phase allows, the first of them the way it was shown and
     * over ground that was shown, each ending against something that stops it; the only ground
     * it changes is a hole it crossed or a block it ran into; its wake is its first run's
     * alone; the tile its strike is for is named exactly when it crushes; and it leaves
     * nothing on show.
     */
    private static EnemyAction watchedTurn(SliderAI ai, CombatEntity boss, GridArena a, GridPos player, String when) {
        boolean awake = ai.isAwake();
        boolean slide = awake && ai.isSliding();
        int[] aimed = ai.aimedWay();
        // The paint as it reads at this moment. It follows the board, and once the turn has
        // been carried out it would be read off a different one.
        Paint shown = Paint.of(ai.getPendingWarning());
        Set<GridPos> danger = painted(ai);
        Ground was = Ground.of(a);
        Set<GridPos> boiled = new HashSet<>(SliderAI.boiledAway(boss, a));
        GridPos stood = boss.getGridPos();
        int phase = SliderAI.phase(boss.getCurrentHp(), boss.getMaxHp());
        // A slide has the runs it settled on when it painted, whatever it has been hit for
        // since. That it settled on no more than its phase gives is held to on the build.
        int runs = slide ? ai.aimedRuns() : phase;

        EnemyAction action = decide(ai, boss, a, player);
        carryOut(action, boss, a);
        String where = when + ": " + action;

        // Never a plain attack, and never a bundle of anything but ground, one run and a wake.
        assertSlideBundle(action, where);
        EnemyAction run = runOf(action);
        boolean crush = isCrush(action);
        assertTrue(run instanceof EnemyAction.Idle || run instanceof EnemyAction.Move
            || run instanceof EnemyAction.CustomAction || run instanceof EnemyAction.CreateTerrain || crush,
            "not something it may ask for. " + where);
        List<GridPos> path = pathOf(action);
        Set<GridPos> wake = wakeOf(action);
        Set<GridPos> floored = new HashSet<>(levelledOf(action));
        Set<GridPos> raised = new HashSet<>(raisedOf(action));
        for (GridPos step : path) {
            assertTrue(CombatEntity.minDistanceFromSizedEntity(step, 2, 2, player) > 0,
                "it slid over the player. " + where);
        }
        assertEquals(crush ? player : null, boss.getPendingStrikeTile(),
            "the tile its strike is for is named when it crushes, and only then. " + where);
        if (crush) assertEquals(1, boss.minDistanceTo(player), "the strike would miss. " + where);

        // A turn with nothing else to do may write back liquid the entity boiled away.
        boolean mend = action instanceof EnemyAction.CreateTerrain && !boiled.isEmpty() && floored.equals(boiled);
        Set<GridPos> levelled = mend ? Set.of() : floored;

        if (!awake) {
            assertTrue(action instanceof EnemyAction.Idle || action instanceof EnemyAction.CustomAction || mend, where);
        } else if (!slide) {
            watchBuild(ai, was, action, levelled, raised, stood, phase, boss, a, player, where);
        } else {
            assertTrue(raised.isEmpty(), "a brick raised on a slide. " + where);
            watchSlide(aimed, shown, was, action, levelled, stood, runs, boss, a, player, where);
            // What the paint is for. Alone, on ground that carried none as their turn ended,
            // whatever was done to the floor after it first went down: not crushed, not shaken.
            if (!danger.contains(player)) {
                SEEN[OFF_THE_PAINT]++;
                assertFalse(crush, "crushed on ground that carried no paint. " + where);
                assertFalse(wake.contains(player), "shaken on ground that carried no paint. " + where);
            }
            assertFalse(ai.isSliding(), "two slides running. " + where);
            assertNull(ai.getPendingWarning(), "paint left down after a slide. " + where);
            assertNull(ai.aimedWay(), where);
        }

        // Not one other tile on the arena is any different, and none has lost or gained its
        // standing as part of the room but the stone it broke.
        Ground now = Ground.of(a);
        for (int x = 0; x < was.type.length; x++) {
            for (int z = 0; z < was.type.length; z++) {
                GridPos tile = new GridPos(x, z);
                assertEquals(was.permanent[x][z] && !floored.contains(tile), now.permanent[x][z], where);
                TileType due = floored.contains(tile) ? TileType.NORMAL
                    : raised.contains(tile) ? TileType.OBSTACLE : was.type[x][z];
                assertEquals(due, now.type[x][z], "ground changed that was not its to change, at " + tile + ". " + where);
            }
        }
        crumble(a);
        return action;
    }

    /** The part of {@link #watchedTurn} for a turn it was due to build on. */
    private static void watchBuild(SliderAI ai, Ground was, EnemyAction action, Set<GridPos> levelled,
                                   Set<GridPos> raised, GridPos stood, int phase, CombatEntity boss, GridArena a,
                                   GridPos player, String where) {
        assertTrue(action instanceof EnemyAction.Idle || action instanceof EnemyAction.CreateTerrain,
            "not a build. " + where);
        assertTrue(levelled.isEmpty(), "ground made floor on a build. " + where);
        assertTrue(wakeOf(action).isEmpty() && !isCrush(action), "it hurt someone on a build. " + where);
        assertEquals(stood, boss.getGridPos(), "it moved on a build. " + where);

        if (action instanceof EnemyAction.CreateTerrain bricks && !raised.isEmpty()) {
            assertEquals(SliderAI.BRICK_TURNS, bricks.duration(), where);
            assertEquals(bricks.tiles().size(), raised.size(), "the same brick twice. " + where);
        }
        assertTrue(raised.size() <= SliderAI.bricksFor(phase), "more bricks than its phase allows. " + where);
        Set<GridPos> under = new HashSet<>(GridArena.getOccupiedTiles(stood, 2, 2));
        for (GridPos brick : raised) {
            assertEquals(TileType.NORMAL, was.type[brick.x()][brick.z()], "a brick on ground that was not plain floor, at " + brick + ". " + where);
            assertFalse(under.contains(brick), "a brick under itself. " + where);
            assertFalse(brick.equals(player), "a brick on the player. " + where);
            assertNull(a.getOccupant(brick), "a brick on somebody. " + where);
            if (brick.manhattanDistance(player) != 1) continue;
            int ways = 0;
            for (int[] way : WAYS) {
                GridPos beside = new GridPos(player.x() + way[0], player.z() + way[1]);
                if (was.walkable(beside) && !raised.contains(beside)) ways++;
            }
            assertTrue(ways >= 2, "a brick that seals the player in, at " + brick + ". " + where);
        }

        // What is on show afterwards is the slide it would make from here with the bricks up.
        assertTrue(ai.isSliding(), "two builds running. " + where);
        BossWarning warning = ai.getPendingWarning();
        int[] aimed = ai.aimedWay();
        assertNotNull(aimed, "a build turn that took no aim. " + where);
        assertNotNull(warning, "it took aim and showed nothing. " + where);
        int runs = ai.aimedRuns();
        assertTrue(runs == phase || (runs < 0 && SliderAI.legs(runs) <= SliderAI.legs(phase)),
            "it settled on more runs than its phase gives it. " + where);
        SliderAI.Telegraph due = SliderAI.telegraph(a, boss, player, runs);
        assertEquals(new HashSet<>(due.tiles()), painted(ai), "what is shown is not the slide it would make. " + where);
        // It always leaves a way out: clear ground within three steps or a brick there to
        // mine, or it has given up every brick and every run but one and can do no less.
        if (ai.bricksHeldBack() + ai.runsHeldBack() > 0) SEEN[HELD_BACK]++;
        if (!wayOut(a, boss, player, painted(ai))) {
            assertTrue(raised.isEmpty() && runs == SliderAI.exactly(1),
                "it left nothing to be done, with " + raised.size() + " bricks and runs " + runs + ". " + where);
        }
        for (GridPos tile : due.tiles()) {
            assertArrayEquals(due.arrows().get(tile), ai.danger().arrows().get(tile), "the arrow at " + tile + ". " + where);
        }
        // And, worked out here: its first run's ground points the way it is aimed, as far as
        // the first block or the edge, through whoever is standing in it.
        GridPos at = stood;
        while (true) {
            List<GridPos> row = rowAhead(at, aimed[0], aimed[1]);
            if (row.stream().anyMatch(tile -> !was.inside(tile))) break;
            boolean stone = row.stream().anyMatch(tile -> was.block(tile) || raised.contains(tile));
            for (GridPos tile : row) {
                // A hole is not somewhere anyone can be standing, so it is not painted.
                if (was.block(tile) || raised.contains(tile) || was.hole(tile)) continue;
                assertArrayEquals(aimed, ai.danger().arrows().get(tile), "its first run is not on its map at " + tile + ". " + where);
            }
            if (stone) break;
            at = new GridPos(at.x() + aimed[0], at.z() + aimed[1]);
        }

        // What is shown is its first run and nothing after it. Arrows on its lane as the
        // floor stands, from where it sits to the first row with a block, a body or the edge
        // in it, and on whoever stands in that row to be crushed. Every one of them pointing
        // the way it is aimed, and none anywhere else. Nothing shown that is not on its map.
        Set<GridPos> lane = new HashSet<>();
        at = stood;
        while (true) {
            List<GridPos> row = rowAhead(at, aimed[0], aimed[1]);
            boolean stops = row.stream().anyMatch(tile -> !was.inside(tile) || was.block(tile) || raised.contains(tile)
                || tile.equals(player) || a.getOccupant(tile) != null);
            if (stops) {
                if (row.contains(player)) lane.add(player);
                break;
            }
            for (GridPos tile : row) {
                if (!was.hole(tile)) lane.add(tile);
            }
            at = new GridPos(at.x() + aimed[0], at.z() + aimed[1]);
        }
        Set<GridPos> arrows = new HashSet<>();
        for (GridPos tile : warning.getAffectedTiles()) {
            if (warning.arrowAt(tile) == null) continue;
            arrows.add(tile);
            assertArrayEquals(aimed, warning.arrowAt(tile), "an arrow that does not point the way of its first run, at " + tile + ". " + where);
        }
        assertEquals(lane, arrows, "the arrows shown are not its first run's lane. " + where);
        assertTrue(painted(ai).containsAll(warning.getAffectedTiles()), "something is shown that is not on its map. " + where);
    }

    /** Things {@link #watchSlide} has seen happen, for a test that has to know its fights were fights. */
    private static final int[] SEEN = new int[6];
    /** A build on which it gave up a brick or a run to leave the player a way out. */
    private static final int HELD_BACK = 5;

    /**
     * Whether there is something the player can do about {@code paint}: within three steps
     * of them, ground with none of it on, or ground beside one of its bricks, which they can
     * then mine. Four-way steps over ground they can stand on, and not through it or a block.
     * A wall somebody put down, which is one that is neither stone of the room nor a brick
     * with turns left, is stepped through as if it were not there: nobody is hemmed in by
     * their own walls.
     */
    /** One of its own bricks: a wall that is not stone of the room and has turns left to stand. */
    private static boolean isBrick(GridArena a, GridPos tile) {
        GridTile ground = a.getTile(tile);
        return ground != null && ground.getType() == TileType.OBSTACLE && !ground.isPermanent()
            && ground.getTurnsRemaining() > 0;
    }

    private static boolean besideABrick(GridArena a, GridPos at) {
        for (int[] way : WAYS) {
            if (isBrick(a, new GridPos(at.x() + way[0], at.z() + way[1]))) return true;
        }
        return false;
    }

    private static boolean wayOut(GridArena a, CombatEntity boss, GridPos player, Set<GridPos> paint) {
        Map<GridPos, Integer> steps = new HashMap<>();
        steps.put(player, 0);
        ArrayDeque<GridPos> queue = new ArrayDeque<>(List.of(player));
        while (!queue.isEmpty()) {
            GridPos at = queue.poll();
            if (a.getTile(at).isWalkable() && (!paint.contains(at) || besideABrick(a, at))) return true;
            if (steps.get(at) == 3) continue;
            for (int[] way : WAYS) {
                GridPos next = new GridPos(at.x() + way[0], at.z() + way[1]);
                GridTile ground = a.getTile(next);
                if (steps.containsKey(next) || ground == null || boss.minDistanceTo(next) == 0) continue;
                boolean putDown = ground.getType() == TileType.OBSTACLE && !ground.isPermanent() && ground.getTurnsRemaining() <= 0;
                if (!ground.isWalkable() && !putDown) continue;
                steps.put(next, steps.get(at) + 1);
                queue.add(next);
            }
        }
        return false;
    }
    /** A slide that came with the player on ground that carried no paint as their turn ended. */
    private static final int OFF_THE_PAINT = 4;

    /** A run after the first of a slide. */
    private static final int LATER_RUNS = 0;
    /** One of those that went straight on, through the block that had stopped the run before it. */
    private static final int STRAIGHT_ON = 1;
    /** A crush by a run that did not move it. */
    private static final int STANDING_CRUSHES = 2;
    /** Someone its first run passed, inside its wake, crushed by a later one. */
    private static final int PASSED_THEN_CRUSHED = 3;

    /** Whether a run can go on into {@code row}: all of it on the arena, no block in it, and nobody. */
    private static boolean clear(Ground was, List<GridPos> row, Set<GridPos> standing, GridPos player) {
        return row.stream().allMatch(was::inside) && row.stream().noneMatch(standing::contains)
            && !row.contains(player);
    }

    /**
     * The part of {@link #watchedTurn} for a turn it was due to slide on.
     *
     * <p>The path is walked as runs. A run goes on while the row ahead of it is clear and ends
     * in front of the first that is not, so where each one stops is decided here and not read
     * off the path: a path that stops short of that, or goes on past it, fails. The first run
     * goes the way it was aimed. Each one after goes whichever way the path goes next.
     *
     * <p>A run that does not move it leaves nothing in the path. It shows only in what was
     * broken: a block against the body somewhere a run ended. Those are counted as one run
     * more, which is the least they can have been.
     */
    private static void watchSlide(int[] aimed, Paint shown, Ground was, EnemyAction action,
                                   Set<GridPos> levelled, GridPos stood, int phase, CombatEntity boss,
                                   GridArena a, GridPos player, String where) {
        List<GridPos> path = pathOf(action);
        boolean crush = isCrush(action);
        assertNotNull(aimed, "a slide with no aim taken. " + where);
        assertNotNull(shown, "a slide nobody was shown. " + where);
        assertFalse(action instanceof EnemyAction.Idle, "a slide turn it did nothing with. " + where);

        // What is standing, which changes as it goes: it breaks what it runs into.
        Set<GridPos> standing = new HashSet<>();
        for (int x = 0; x < was.type.length; x++) {
            for (int z = 0; z < was.type.length; z++) {
                if (isBlock(was.type[x][z])) standing.add(new GridPos(x, z));
            }
        }
        Set<GridPos> broken = new HashSet<>();      // the blocks a run was seen to end against
        Set<GridPos> faced = new HashSet<>();       // every row against it, anywhere a run ended
        Set<GridPos> crossed = new HashSet<>();     // every tile it went over

        int runs = 0;
        int walked = 0;
        GridPos at = stood;
        int[] way = aimed;
        int firstSteps = 0;
        boolean firstStone = false;
        List<GridPos> firstRow = List.of();
        boolean caught = false;
        boolean wentBack = false;
        boolean wentOn = false;
        while (true) {
            int steps = 0;
            List<GridPos> ahead = rowAhead(at, way[0], way[1]);
            while (clear(was, ahead, standing, player)) {
                GridPos next = new GridPos(at.x() + way[0], at.z() + way[1]);
                assertTrue(walked < path.size() && path.get(walked).equals(next),
                    "a run that stopped with nothing to stop it, at " + at + ". " + where);
                if (runs == 0) {
                    for (GridPos tile : ahead) {
                        if (was.hole(tile)) continue;
                        assertArrayEquals(aimed, shown.arrowAt(tile),
                            "its first run crossed ground it never showed, at " + tile + ". " + where);
                    }
                }
                crossed.addAll(ahead);
                at = next;
                walked++;
                steps++;
                ahead = rowAhead(at, way[0], way[1]);
            }
            runs++;
            boolean stone = ahead.stream().anyMatch(standing::contains);
            caught = ahead.contains(player);
            for (GridPos tile : ahead) {
                if (!standing.contains(tile)) continue;
                assertTrue(levelled.contains(tile), "it ran into a block and left it standing, at " + tile + ". " + where);
                broken.add(tile);
            }
            standing.removeAll(ahead);
            faced.addAll(rowsAround(at));
            if (runs == 1) {
                firstSteps = steps;
                firstStone = stone;
                firstRow = ahead;
                assertTrue(steps > 0 || stone || caught, "its first run was not one worth making. " + where);
                if (caught) {
                    assertArrayEquals(aimed, shown.arrowAt(player),
                        "crushed by its first run on ground that carried no arrow. " + where);
                }
            } else {
                SEEN[LATER_RUNS]++;
                if (wentOn) SEEN[STRAIGHT_ON]++;
            }
            assertTrue(!wentBack || caught,
                "it went straight back the way it came, with other ways open and nobody there to crush. " + where);
            if (caught) {
                assertTrue(crush, "it had someone in the row that stopped it and let them be. " + where);
                assertEquals(path.size(), walked, "it went on after crushing someone. " + where);
                break;
            }
            if (walked == path.size()) break;

            GridPos next = path.get(walked);
            int[] turn = {next.x() - at.x(), next.z() - at.z()};
            assertEquals(1, Math.abs(turn[0]) + Math.abs(turn[1]), "a step that is not one tile along one axis. " + where);
            // Straight back is for a crush, or for when nothing else moves it, which on a
            // floor this wide is never. The one other way it comes about is a run in between
            // that did not move it: something broken beside it, on the spot.
            GridPos here = at;
            boolean brokeBeside = rowsAround(here).stream()
                .anyMatch(tile -> standing.contains(tile) && levelled.contains(tile));
            wentBack = turn[0] == -way[0] && turn[1] == -way[1] && !brokeBeside;
            wentOn = turn[0] == way[0] && turn[1] == way[1];
            assertTrue(!wentOn || stone, "two runs the same way with nothing between them. " + where);
            way = turn;
        }

        // The ground. A hole made floor was crossed, and every hole crossed was made floor. A
        // block made floor stood in a row against it somewhere a run ended.
        Set<GridPos> unseen = new HashSet<>();
        for (GridPos tile : levelled) {
            if (was.block(tile)) {
                if (broken.contains(tile)) continue;
                assertTrue(faced.contains(tile), "it broke a block it never ran into, at " + tile + ". " + where);
                unseen.add(tile);
            } else {
                assertTrue(was.hole(tile), "it made floor of ground it could have crossed as it was, at " + tile + ". " + where);
                assertTrue(crossed.contains(tile), "it filled a hole it did not cross, at " + tile + ". " + where);
            }
        }
        for (GridPos tile : crossed) {
            if (was.hole(tile)) assertTrue(levelled.contains(tile), "it crossed a hole it never had made floor, at " + tile + ". " + where);
        }

        // How many runs that was, at the least, and whether its phase gives it so many.
        int counted = runs;
        if (crush && !caught) {
            // Crushed by a run that did not move it: straight along a row against it, from
            // where the last one left it. A block in that row was that same run's to break.
            List<GridPos> pressed = null;
            for (int[] each : WAYS) {
                List<GridPos> row = rowAhead(at, each[0], each[1]);
                if (row.contains(player)) pressed = row;
            }
            assertNotNull(pressed, "a crush with nobody in a row against it. " + where);
            unseen.removeAll(pressed);
            counted++;
            SEEN[STANDING_CRUSHES]++;
        }
        boolean hidden = !unseen.isEmpty();
        if (hidden) counted++;
        int most = phase == 1 ? (firstStone ? 2 : 1) : SliderAI.legs(phase);
        assertTrue(counted <= most, "at least " + counted + " runs, and its phase gives it " + most + ". " + where);
        // And it does not stop early: short of a crush it takes every run it has.
        if (!crush && !hidden) assertEquals(most, runs, "it had runs left and did not take them. " + where);

        // The wake is the first run's alone, less the row it ran into and whoever was crushed.
        int reach = 0;
        while (rowAhead(new GridPos(stood.x() + aimed[0] * reach, stood.z() + aimed[1] * reach), aimed[0], aimed[1])
            .stream().allMatch(tile -> was.inside(tile) && !was.block(tile))) reach++;
        Set<GridPos> due = new HashSet<>(SliderAI.wake(a, boss, new SliderAI.Lane(stood, aimed[0], aimed[1], reach), firstSteps));
        if (firstStone) due.removeAll(firstRow);
        if (crush && due.remove(player)) SEEN[PASSED_THEN_CRUSHED]++;
        assertEquals(due, wakeOf(action), "its wake is not its first run's, less what it crushed. " + where);
        Set<GridPos> marked = new HashSet<>(shown.getAffectedTiles());
        for (GridPos tile : wakeOf(action)) {
            assertTrue(marked.contains(tile), "it shook ground it never marked, at " + tile + ". " + where);
        }
    }

    /** The four rows against a 2x2 body cornered at {@code at}: everything it faces from there. */
    private static List<GridPos> rowsAround(GridPos at) {
        List<GridPos> rows = new ArrayList<>();
        for (int[] way : WAYS) rows.addAll(rowAhead(at, way[0], way[1]));
        return rows;
    }

    @Test
    @DisplayName("wherever you stand and in every phase: it builds and slides turn about, and neither does anything the rules do not give it")
    void everyTurnKeepsTheRules() {
        for (int first : EACH_PHASE) {
            for (int px = 0; px < SIZE; px++) {
                for (int pz = 0; pz < SIZE; pz++) {
                    if (px >= 6 && px <= 7 && pz >= 6 && pz <= 7) continue;
                    GridPos player = new GridPos(px, pz);
                    GridArena a = arena(player);
                    // A little of everything. Blocks of all three kinds for it to run into and
                    // break. Holes and deep water to fill on its way. And a puddle and a
                    // shallow pit, which it only crosses.
                    wall(a, 3, 6);
                    wall(a, 6, 2);
                    paint(a, TileType.OBSTACLE, 9, 7);
                    paint(a, TileType.RUBBLE, 2, 3);
                    paint(a, TileType.VOID, 7, 10);
                    paint(a, TileType.VOID, 4, 7);
                    paint(a, TileType.DEEP_WATER, 6, 4);
                    paint(a, TileType.WATER, 10, 6);
                    paint(a, TileType.LOW_GROUND, 6, 11);
                    if (typeAt(a, px, pz) != TileType.NORMAL) continue;
                    CombatEntity boss = slider(a, 6, 6, 100 * HP);
                    SliderAI ai = new SliderAI();
                    strike(boss, 100 * first);

                    for (int t = 0; t < 9; t++) {
                        watchedTurn(ai, boss, a, player, "first strike " + first + ", player at " + player + ", turn " + t);
                    }
                }
            }
        }
    }

    /** What a player can do to the tile beside them, weighted roughly by how cheap it is. */
    private static final TileType[] WORK = {
        TileType.OBSTACLE, TileType.OBSTACLE, TileType.OBSTACLE,   // a block put down
        TileType.VOID, TileType.VOID,                              // a hole dug through
        TileType.LOW_GROUND,                                       // a pit only started
        TileType.WATER,                                            // a bucket emptied
    };

    /** Plain floor that neither it nor anything else is standing on. */
    private static boolean open(GridArena a, CombatEntity boss, GridPos tile) {
        GridTile ground = a.getTile(tile);
        return ground != null && ground.getType() == TileType.NORMAL && boss.minDistanceTo(tile) > 0;
    }

    private static GridPos beside(GridPos at, Random dice) {
        int[] way = WAYS[dice.nextInt(WAYS.length)];
        return new GridPos(at.x() + way[0], at.z() + way[1]);
    }

    /** Where someone crushed from where they stand lands: thrown straight away from it, as far as there is floor. */
    private static GridPos thrown(GridArena a, CombatEntity boss, GridPos player) {
        GridPos at = boss.getGridPos();
        int awayX = Integer.signum(player.x() - Math.max(at.x(), Math.min(player.x(), at.x() + 1)));
        int awayZ = Integer.signum(player.z() - Math.max(at.z(), Math.min(player.z(), at.z() + 1)));
        for (int i = 0; i < SliderAI.KNOCKBACK_TILES; i++) {
            GridPos next = new GridPos(player.x() + awayX, player.z() + awayZ);
            if (!open(a, boss, next)) break;
            player = next;
        }
        return player;
    }

    @Test
    @DisplayName("the same holds through whole fights, against someone who moves, builds, digs, pours water and hits back")
    void holdsThroughWholeFights() {
        int crushes = 0;
        int shakes = 0;
        int blocks = 0;
        int holes = 0;
        int bricks = 0;
        int[] seen = SEEN.clone();
        int[] turnsIn = new int[4];
        for (int seed = 0; seed < 120; seed++) {
            Random dice = new Random(seed);
            GridPos player = new GridPos(dice.nextInt(SIZE), dice.nextInt(4));
            GridArena a = arena(player);
            CombatEntity boss = slider(a, 6, 6);
            SliderAI ai = new SliderAI();
            // Three stones of the room itself, somewhere on the floor.
            for (int i = 0; i < 3; i++) {
                GridPos stone = new GridPos(dice.nextInt(SIZE), dice.nextInt(SIZE));
                if (open(a, boss, stone) && !stone.equals(player)) wall(a, stone.x(), stone.z());
            }
            strike(boss, 1 + dice.nextInt(8));

            for (int t = 0; t < 50; t++) {
                // The player's turn: up to three steps, then perhaps some work on the tile
                // beside them, then a swing if that left them against it.
                for (int i = dice.nextInt(4); i > 0; i--) {
                    GridPos step = beside(player, dice);
                    if (open(a, boss, step)) player = step;
                }
                stand(a, player);
                GridPos work = beside(player, dice);
                int job = dice.nextInt(12);
                if (job < WORK.length && open(a, boss, work)) paint(a, WORK[job], work.x(), work.z());
                int swing = 1 + dice.nextInt(9);
                if (boss.minDistanceTo(player) == 1 && dice.nextInt(3) > 0 && boss.getCurrentHp() > swing) {
                    strike(boss, swing);
                    ai.notifyStruck();
                }

                Ground was = Ground.of(a);
                turnsIn[SliderAI.phase(boss.getCurrentHp(), boss.getMaxHp())]++;
                EnemyAction action = watchedTurn(ai, boss, a, player, "fight " + seed + ", turn " + t);

                bricks += raisedOf(action).size();
                if (wakeOf(action).contains(player)) shakes++;
                for (GridPos tile : levelledOf(action)) {
                    if (was.block(tile)) blocks++;
                    else if (was.hole(tile)) holes++;
                }
                if (isCrush(action)) {
                    crushes++;
                    player = thrown(a, boss, player);
                    stand(a, player);
                }
            }
        }
        // The fights were fights: every rule above was given plenty to refuse, in every phase.
        // The counts are the same every run, so a floor well under each is safe.
        int later = SEEN[LATER_RUNS] - seen[LATER_RUNS];
        int straightOn = SEEN[STRAIGHT_ON] - seen[STRAIGHT_ON];
        int standing = SEEN[STANDING_CRUSHES] - seen[STANDING_CRUSHES];
        int passed = SEEN[PASSED_THEN_CRUSHED] - seen[PASSED_THEN_CRUSHED];
        int offThePaint = SEEN[OFF_THE_PAINT] - seen[OFF_THE_PAINT];
        String tally = offThePaint + " slides met off the paint, " + crushes + " crushes, " + standing + " of them by a run that did not move it and " + passed
            + " of someone its first run had passed, " + shakes + " shaken, " + blocks + " blocks broken, " + holes
            + " holes filled, " + bricks + " bricks raised, " + later + " runs after a first, " + straightOn
            + " of them straight on through a block, and turns in each phase "
            + turnsIn[1] + ", " + turnsIn[2] + ", " + turnsIn[3];
        System.out.println("TALLY " + tally);
        assertTrue(SEEN[HELD_BACK] > seen[HELD_BACK], "fixture: it never had to hold back");
        assertTrue(offThePaint > 0 && crushes > 0 && standing > 0 && passed > 0 && shakes > 0 && blocks > 0 && holes > 0 && bricks > 0
            && later > 0 && straightOn > 0 && turnsIn[1] > 0 && turnsIn[2] > 0 && turnsIn[3] > 0, tally);
    }

    // ── Nowhere to stand ──

    @Test
    @DisplayName("someone who never moves is crushed, wherever they stand and in every phase")
    void standingStillIsNeverSafe() {
        for (int first : EACH_PHASE) {
            int slowest = 0;
            for (int px = 0; px < SIZE; px++) {
                for (int pz = 0; pz < SIZE; pz++) {
                    if (px >= 6 && px <= 7 && pz >= 6 && pz <= 7) continue;
                    GridPos player = new GridPos(px, pz);
                    GridArena a = arena(player);
                    CombatEntity boss = slider(a, 6, 6, 100 * HP);
                    SliderAI ai = new SliderAI();
                    strike(boss, 100 * first);

                    String who = "a player standing at " + player + ", first strike " + first;
                    int caught = -1;
                    // The turn it wakes on, then two builds and two slides at the most.
                    for (int t = 0; t < 5 && caught < 0; t++) {
                        if (isCrush(watchedTurn(ai, boss, a, player, who + ", turn " + t))) caught = t;
                    }
                    assertTrue(caught >= 0, "its second slide, and it has not reached " + who);
                    slowest = Math.max(slowest, caught);
                }
            }
            System.out.println("STILL first " + first + ": slowest first crush on turn " + slowest);
        }
    }

    /** Where a player with a block to spend every turn puts it. */
    private enum Waller { NONE, IN_FRONT_OF_THEM, AGAINST_ITS_FACE, BESIDE_THEM, OUT_TOWARD_IT, BEHIND_IT }

    /** Signed distance from a 2-wide body starting at {@code at} to coordinate {@code to}. */
    private static int gap(int at, int to) {
        return to < at ? to - at : to > at + 1 ? to - (at + 1) : 0;
    }

    /**
     * The tile such a player blocks this turn, or null when it is not theirs to block. Worked
     * out from the way it is aimed when it has taken aim, and otherwise from the way it would
     * come at them, which is the axis it is further off on.
     */
    private static GridPos wallTile(Waller where, SliderAI ai, CombatEntity boss, GridArena a, GridPos player) {
        if (where == Waller.NONE) return null;
        GridPos at = boss.getGridPos();
        int gapX = gap(at.x(), player.x());
        int gapZ = gap(at.z(), player.z());
        int[] aimed = ai.aimedWay();
        boolean alongX = aimed != null ? aimed[0] != 0 : Math.abs(gapX) > Math.abs(gapZ);
        int dx = aimed != null ? aimed[0] : alongX ? Integer.signum(gapX) : 0;
        int dz = aimed != null ? aimed[1] : alongX ? 0 : Integer.signum(gapZ);

        GridPos tile;
        if (where == Waller.IN_FRONT_OF_THEM) {
            tile = new GridPos(player.x() - dx, player.z() - dz);
        } else if (where == Waller.OUT_TOWARD_IT) {
            // The first tile toward it that does not hold a block already.
            tile = new GridPos(player.x() - dx, player.z() - dz);
            while (a.getTile(tile) != null && typeAt(a, tile.x(), tile.z()) == TileType.OBSTACLE) {
                tile = new GridPos(tile.x() - dx, tile.z() - dz);
            }
        } else if (where == Waller.AGAINST_ITS_FACE || where == Waller.BEHIND_IT) {
            int side = where == Waller.BEHIND_IT ? -1 : 1;
            List<GridPos> row = rowAhead(at, side * dx, side * dz);
            tile = open(a, boss, row.get(0)) && !row.get(0).equals(player) ? row.get(0) : row.get(1);
        } else if (alongX) {
            // The other half of the width it comes down, level with them.
            tile = new GridPos(player.x(), player.z() == at.z() ? at.z() + 1 : player.z() == at.z() + 1 ? at.z()
                : player.z() + Integer.signum(at.z() - player.z()));
        } else {
            tile = new GridPos(player.x() == at.x() ? at.x() + 1 : player.x() == at.x() + 1 ? at.x()
                : player.x() + Integer.signum(at.x() - player.x()), player.z());
        }
        return open(a, boss, tile) && !tile.equals(player) ? tile : null;
    }

    @Test
    @DisplayName("nor does a block for every turn save them: wherever they put it, someone who stands still is crushed again and again")
    void aBlockEveryTurnIsNoSafer() {
        for (Waller where : Waller.values()) {
            for (int first : EACH_PHASE) {
                int fewest = Integer.MAX_VALUE;
                int quietest = 0;
                for (int px = 1; px < SIZE; px += 3) {
                    for (int pz = 1; pz < SIZE; pz += 3) {
                        if (px >= 6 && px <= 7 && pz >= 6 && pz <= 7) continue;
                        GridPos player = new GridPos(px, pz);
                        GridArena a = arena(player);
                        CombatEntity boss = slider(a, 6, 6, 100 * HP);
                        SliderAI ai = new SliderAI();
                        strike(boss, 100 * first);

                        String who = "a player at " + player + " with a block every turn " + where + ", first strike " + first;
                        int crushes = 0;
                        int quiet = 0;
                        int longest = 0;
                        for (int t = 0; t < 40; t++) {
                            GridPos tile = wallTile(where, ai, boss, a, player);
                            if (tile != null) paint(a, TileType.OBSTACLE, tile.x(), tile.z());
                            if (isCrush(watchedTurn(ai, boss, a, player, who + ", turn " + t))) {
                                crushes++;
                                quiet = 0;
                            } else if (crushes > 0) {
                                quiet++;
                                longest = Math.max(longest, quiet);
                            }
                            if (boss.minDistanceTo(player) == 1) {
                                strike(boss, 1);
                                strike(boss, 1);
                            }
                        }
                        // It slides on twenty of those turns, less the one it woke on. With no
                        // block at all the fewest is eighteen, and the blocks cost it three.
                        assertTrue(crushes >= 12, "forty turns, and only " + crushes + " crushes on " + who);
                        fewest = Math.min(fewest, crushes);
                        quietest = Math.max(quietest, longest);
                    }
                }
                System.out.println("WALLER " + where + " first " + first + ": fewest crushes in 40 turns " + fewest
                    + ", longest run of turns without one " + quietest);
            }
        }
    }

    /** How a simulated player answers the paint. */
    private enum Dodger {
        /**
         * Trusts the paint: walks to the tile furthest from all the ground it has painted,
         * failing any to the nearest tile whose paint carries no arrow, and failing that
         * stays where they are.
         */
        BY_THE_PAINT,
        /**
         * Knows its rules as well as it does: walks to a tile where the slide it has aimed
         * would not crush them, off the paint if there is one, and failing any does the other.
         */
        BY_THE_RULES,
        /**
         * Trusts the paint and carries a pickaxe: with no clear ground in reach they walk to
         * the nearest brick they can get beside, break it, read the paint again, and spend
         * what steps they have left on that.
         */
        BY_THE_PICKAXE,
        /**
         * Goes by what is shown and nothing else: ends every turn as near it as three steps
         * allow without standing on its paint.
         */
        BY_WHAT_IS_SHOWN,
        /**
         * The same, and reads the bricks: works out from the floor where its first run will
         * stop, and stays out of both rows and both columns it will be standing in there.
         */
        BY_THE_BRICKS
    }

    /**
     * Where a player who goes by what is shown ends their turn: as near it as they can get in
     * three steps without standing on its paint, and for the one who reads the bricks without
     * standing in line with where its first run will stop either. With nowhere like that, the
     * nearest ground off the paint, then the nearest whose paint has no arrow, and failing
     * that where they are.
     */
    private static GridPos nearest(Dodger how, SliderAI ai, CombatEntity boss, GridArena a, GridPos player, int[] trapped) {
        BossWarning warning = ai.getPendingWarning();
        Set<GridPos> paint = shown(ai);
        Set<GridPos> inLine = new HashSet<>();
        int[] aimed = ai.aimedWay();
        if (how == Dodger.BY_THE_BRICKS && warning != null && aimed != null) {
            // Where its first run leaves it, read off the floor: in front of the first block
            // or the edge. From there it can come down either row or either column it is in.
            GridPos stop = boss.getGridPos();
            while (true) {
                List<GridPos> row = rowAhead(stop, aimed[0], aimed[1]);
                if (row.stream().anyMatch(tile -> a.getTile(tile) == null || isBlock(a.getTile(tile).getType()))) break;
                stop = new GridPos(stop.x() + aimed[0], stop.z() + aimed[1]);
            }
            for (int i = 0; i < a.getWidth(); i++) {
                for (int wide = 0; wide < 2; wide++) {
                    inLine.add(new GridPos(stop.x() + wide, i));
                    inLine.add(new GridPos(i, stop.z() + wide));
                }
            }
        }
        List<GridPos> reach = reach(a, boss, player);
        GridPos best = null;
        for (GridPos tile : reach) {
            if (paint.contains(tile) || inLine.contains(tile)) continue;
            if (best == null || boss.minDistanceTo(tile) < boss.minDistanceTo(best)) best = tile;
        }
        if (best != null) return best;
        for (GridPos tile : reach) {
            if (paint.contains(tile)) continue;
            if (best == null || boss.minDistanceTo(tile) < boss.minDistanceTo(best)) best = tile;
        }
        if (best != null) return best;
        for (GridPos tile : reach) {
            if (warning.arrowAt(tile) == null) return tile;
        }
        trapped[0]++;
        return player;
    }

    /** Bricks mined by {@link Dodger#BY_THE_PICKAXE}, all told. */
    private static final int[] MINED = {0};

    /** The tiles a player can walk to in three steps, nearest first, where they stand included. */
    private static List<GridPos> reach(GridArena a, CombatEntity boss, GridPos from) {
        return new ArrayList<>(stepsFrom(a, boss, from).keySet());
    }

    /** The same tiles, each with how many steps it is from where they stand. */
    private static Map<GridPos, Integer> stepsFrom(GridArena a, CombatEntity boss, GridPos from) {
        Map<GridPos, Integer> steps = new LinkedHashMap<>();
        steps.put(from, 0);
        ArrayDeque<GridPos> queue = new ArrayDeque<>(List.of(from));
        while (!queue.isEmpty()) {
            GridPos at = queue.poll();
            if (steps.get(at) == 3) continue;
            for (int[] way : WAYS) {
                GridPos next = new GridPos(at.x() + way[0], at.z() + way[1]);
                if (!steps.containsKey(next) && open(a, boss, next)) {
                    steps.put(next, steps.get(at) + 1);
                    queue.add(next);
                }
            }
        }
        return steps;
    }

    /**
     * Where such a player goes once it has painted its slide. With nothing painted they stay.
     *
     * @param trapped counted up when there is no tile in reach that the slide would spare:
     *                none without an arrow for the one, none it works out as safe for the other
     */
    private static GridPos dodge(Dodger how, SliderAI ai, CombatEntity boss, GridArena a, GridPos player, int[] trapped) {
        if (how == Dodger.BY_WHAT_IS_SHOWN || how == Dodger.BY_THE_BRICKS) return nearest(how, ai, boss, a, player, trapped);
        Set<GridPos> hot = painted(ai);
        if (hot.isEmpty()) return player;
        List<GridPos> reach = reach(a, boss, player);

        if (how == Dodger.BY_THE_RULES) {
            int phase = ai.aimedRuns();
            GridPos spared = null;
            for (GridPos tile : reach) {
                stand(a, tile);
                SliderAI.Route slide = SliderAI.route(SliderAI.Scene.of(a, boss, tile), boss.getGridPos(), phase, ai.aimedWay());
                if (slide.crushed() != null) continue;
                if (spared == null) spared = tile;
                if (!hot.contains(tile)) {
                    spared = tile;
                    break;
                }
            }
            stand(a, player);
            if (spared == null) trapped[0]++;
            return spared == null ? player : spared;
        }

        if (how == Dodger.BY_THE_PICKAXE && hot.containsAll(reach)) {
            // Nowhere clear to walk to. The nearest brick they can get beside comes down, and
            // what is left of their turn is spent on the paint as it reads after that.
            int left = 3;
            search:
            for (Map.Entry<GridPos, Integer> at : stepsFrom(a, boss, player).entrySet()) {
                for (int[] way : WAYS) {
                    GridPos brick = new GridPos(at.getKey().x() + way[0], at.getKey().z() + way[1]);
                    if (!isBrick(a, brick)) continue;
                    paint(a, TileType.NORMAL, brick.x(), brick.z());
                    standing(a).remove(brick);
                    MINED[0]++;
                    player = at.getKey();
                    left = 3 - at.getValue();
                    stand(a, player);
                    break search;
                }
            }
            hot = painted(ai);
            reach = new ArrayList<>();
            for (Map.Entry<GridPos, Integer> at : stepsFrom(a, boss, player).entrySet()) {
                if (at.getValue() <= left) reach.add(at.getKey());
            }
        }

        GridPos best = null;
        int bestClear = 0;
        for (GridPos tile : reach) {
            if (hot.contains(tile)) continue;
            int clear = Integer.MAX_VALUE;
            for (GridPos marked : hot) clear = Math.min(clear, tile.manhattanDistance(marked));
            if (best == null || clear > bestClear) {
                bestClear = clear;
                best = tile;
            }
        }
        if (best != null) return best;
        // No ground in reach without paint on it. Paint with no arrow is only shaken, so
        // that is the next best place to be, and failing that there is nowhere to go.
        Set<GridPos> arrows = arrowed(ai);
        for (GridPos tile : reach) {
            if (!arrows.contains(tile)) return tile;
        }
        trapped[0]++;
        return player;
    }

    /** Up to three steps to stand against it, or as near to that as three steps get. */
    private static GridPos approach(GridArena a, CombatEntity boss, GridPos player) {
        GridPos best = player;
        for (GridPos tile : reach(a, boss, player)) {
            if (boss.minDistanceTo(tile) < boss.minDistanceTo(best)) best = tile;
        }
        return best;
    }

    /**
     * Not a test of what the numbers are. They are printed, for whoever is tuning the fight,
     * and every turn of it is held to the rules like any other.
     *
     * <p>Six players, each for 200 turns in each phase from each of four places, in a room
     * the size of its own with it starting in the middle. All of them swing twice whenever
     * they are beside it. Two trust the paint, two work the slide out for themselves, and two
     * trust the paint and mine its bricks when they are hemmed in; one of each stays where
     * the slide leaves them, and the other walks back up to it after every slide to swing
     * again.
     */
    @Test
    @DisplayName("and against players who get out of its way every time, it still keeps every rule")
    void againstSomeoneWhoDodges() {
        int[][] starts = {{1, 1}, {12, 12}, {6, 1}, {12, 7}};
        for (Dodger how : Dodger.values()) {
            for (boolean closes : new boolean[]{false, true}) {
                // The two who go by what is shown walk up to it of themselves, every turn.
                if (closes && (how == Dodger.BY_WHAT_IS_SHOWN || how == Dodger.BY_THE_BRICKS)) continue;
                for (int first : EACH_PHASE) {
                    int crushes = 0;
                    int shakes = 0;
                    int rounds = 0;
                    int slides = 0;
                    int atTheEdge = 0;
                    int builds = 0;
                    int heldBuilds = 0;
                    int heldBricks = 0;
                    int heldRuns = 0;
                    int paintedTiles = 0;
                    int openTiles = 0;
                    int[] trapped = {0};
                    int minedBefore = MINED[0];
                    for (int[] start : starts) {
                        GridPos player = new GridPos(start[0], start[1]);
                        GridArena a = arena(ROOM, player);
                        CombatEntity boss = slider(a, ROOM / 2 - 1, ROOM / 2 - 1, 1000 * HP);
                        SliderAI ai = new SliderAI();
                        strike(boss, 1000 * first);
                        for (int t = 0; t < 200; t++) {
                            boolean marked = ai.getPendingWarning() != null;
                            if (marked) {
                                builds++;
                                if (ai.bricksHeldBack() + ai.runsHeldBack() > 0) heldBuilds++;
                                heldBricks += ai.bricksHeldBack();
                                heldRuns += ai.runsHeldBack();
                                for (int x = 0; x < ROOM; x++) {
                                    for (int z = 0; z < ROOM; z++) {
                                        GridPos tile = new GridPos(x, z);
                                        if (!open(a, boss, tile)) continue;
                                        openTiles++;
                                        if (ai.getPendingWarning().getAffectedTiles().contains(tile)) paintedTiles++;
                                    }
                                }
                            }
                            if (!marked && closes) player = approach(a, boss, player);
                            if (boss.minDistanceTo(player) == 1) {
                                strike(boss, 1);
                                strike(boss, 1);
                                rounds++;
                            }
                            player = dodge(how, ai, boss, a, player, trapped);
                            stand(a, player);

                            boolean slide = ai.isAwake() && ai.isSliding();
                            EnemyAction action = watchedTurn(ai, boss, a, player, how + (closes ? ", closing" : "")
                                + ", from " + start[0] + "," + start[1] + ", first strike " + first + ", turn " + t);
                            if (slide) {
                                slides++;
                                GridPos at = boss.getGridPos();
                                if (at.x() == 0 || at.z() == 0 || at.x() == ROOM - 2 || at.z() == ROOM - 2) atTheEdge++;
                            }
                            if (wakeOf(action).contains(player)) shakes++;
                            if (isCrush(action)) {
                                crushes++;
                                player = thrown(a, boss, player);
                                stand(a, player);
                            }
                        }
                    }
                    double fights = starts.length;
                    System.out.println("DODGER " + how + (closes ? " closing" : " staying") + ", first strike " + first
                        + ": in 200 turns, crushed " + crushes / fights + ", shaken " + shakes / fights
                        + ", rounds of swings " + rounds / fights + ", no tile in reach to be spared on " + trapped[0] / fights
                        + ", slides " + slides / fights + ", of them ending against the edge " + atTheEdge / fights
                        + ", open tiles painted on an average build " + paintedTiles / Math.max(1, builds) + " of "
                        + openTiles / Math.max(1, builds) + ", builds it held back on " + heldBuilds / fights
                        + ", giving up bricks " + heldBricks / fights + " and runs " + heldRuns / fights
                        + ", bricks mined " + (MINED[0] - minedBefore) / fights);
                }
            }
        }
    }

    // ── What the real entity does to the floor ──

    @Test
    @DisplayName("liquid within a tile of it is written back to floor, on a turn it has nothing else to do")
    void boiledAway() {
        GridPos player = new GridPos(10, 10);
        GridArena a = arena(player);
        paint(a, TileType.WATER, 6, 5);     // beside it
        paint(a, TileType.LAVA, 3, 3);      // off its corner
        paint(a, TileType.WATER, 7, 4);     // two tiles out
        CombatEntity boss = slider(a, 4, 4);
        SliderAI ai = new SliderAI();

        EnemyAction.CreateTerrain mend = assertInstanceOf(EnemyAction.CreateTerrain.class,
            turn(ai, boss, a, player), "asleep or not: the entity evaporates either way");
        assertEquals(TileType.NORMAL, mend.terrainType());
        assertEquals(0, mend.duration());
        assertEquals(Set.of(new GridPos(6, 5), new GridPos(3, 3)), new HashSet<>(mend.tiles()));
        assertFalse(ai.isAwake(), "and it is not woken by it");
        assertInstanceOf(EnemyAction.Idle.class, turn(ai, boss, a, player), "written back, there is nothing left to mend");
    }

    @Test
    @DisplayName("it does not fill a marking turn by walking up and swinging")
    void noAdvanceWhileMarking() {
        GridPos player = new GridPos(8, 6);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 6, 6);
        assertInstanceOf(EnemyAction.Idle.class, new SliderAI().getChargingAdvanceAction(boss, a, player));
    }

    // ── Danger paint ──

    @Test
    @DisplayName("awake, the danger is the four lanes it could take, each to the edge, not a diamond round it")
    void threatTiles() {
        GridPos player = new GridPos(12, 12);
        GridArena a = arena(player);
        CombatEntity boss = slider(a, 5, 5);
        SliderAI ai = woken(boss, a, player, 3);

        Set<GridPos> danger = ai.computeThreatTiles(boss, a);
        Set<GridPos> lanes = new HashSet<>();
        lanes.addAll(box(7, 5, 13, 6));
        lanes.addAll(box(0, 5, 4, 6));
        lanes.addAll(box(5, 7, 6, 13));
        lanes.addAll(box(5, 0, 6, 4));
        assertEquals(lanes, danger);
        assertFalse(danger.contains(new GridPos(7, 7)), "the corners are the safe ground");
        assertFalse(danger.contains(new GridPos(5, 5)), "not its own tiles");
    }

    @Test
    @DisplayName("that danger ends at a block, takes in the open tile beside it, and runs straight through a hole")
    void threatTilesAndStone() {
        GridPos player = new GridPos(12, 12);
        GridArena a = arena(player);
        paint(a, TileType.OBSTACLE, 8, 5);    // in the lane east, one tile out
        wall(a, 5, 3);                        // in the lane north, one tile out
        paint(a, TileType.VOID, 5, 8);        // in the lane south
        CombatEntity boss = slider(a, 5, 5);
        SliderAI ai = woken(boss, a, player, 3);

        Set<GridPos> danger = ai.computeThreatTiles(boss, a);
        Set<GridPos> east = new HashSet<>(box(7, 5, 7, 6));
        east.add(new GridPos(8, 6));
        assertEquals(east, danger.stream().filter(t -> t.x() > 6).collect(Collectors.toSet()),
            "east: one tile, and where it would crush someone against the block");
        Set<GridPos> north = new HashSet<>(box(5, 4, 6, 4));
        north.add(new GridPos(6, 3));
        assertEquals(north, danger.stream().filter(t -> t.z() < 5).collect(Collectors.toSet()),
            "north: the same, against the stone of the room");
        assertEquals(box(5, 7, 6, 13), danger.stream().filter(t -> t.z() > 6).collect(Collectors.toSet()),
            "south: all the way, the hole included");
    }
}
