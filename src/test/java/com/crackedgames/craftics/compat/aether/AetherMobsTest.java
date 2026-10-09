package com.crackedgames.craftics.compat.aether;

import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.combat.ai.PassiveAI;
import com.crackedgames.craftics.combat.ai.ZombieAI;
import com.crackedgames.craftics.compat.aether.ai.AechorPlantAI;
import com.crackedgames.craftics.compat.aether.ai.CockatriceAI;
import com.crackedgames.craftics.compat.aether.ai.MoaAI;
import com.crackedgames.craftics.compat.aether.ai.SentryAI;
import com.crackedgames.craftics.compat.aether.ai.SwetAI;
import com.crackedgames.craftics.compat.aether.ai.ValkyrieAI;
import com.crackedgames.craftics.compat.aether.ai.ZephyrAI;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the Aether's creatures decide to do, and the tables that describe them.
 *
 * <p>Nothing here can spawn an Aether mob or resolve an action: that needs the game and the
 * mod, and belongs to the in-game checklist. What can be pinned without either is what each
 * AI <em>asks for</em> on a bare grid, which is where the rules that matter live - a turret
 * that must never ask to move, a cloud that must never ask to hurt, a mine that must stay
 * asleep until someone comes close.
 */
class AetherMobsTest {

    // ── Fixture ──
    // A 12x12 floor of plain tiles. Mobs get an explicit move speed: the default comes from
    // a switch on vanilla ids that knows none of these.

    private static final int SIZE = 12;

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

    private static void paint(GridArena a, TileType type, int x, int z) {
        a.setTile(new GridPos(x, z), new GridTile(type, null));
    }

    private static int nextId = 100;

    private static CombatEntity mob(GridArena a, String type, int x, int z, int attack, int range, int speed) {
        CombatEntity e = new CombatEntity(nextId++, type, new GridPos(x, z), 20, attack, 0, range, 1, speed);
        assertTrue(a.placeEntity(e), "fixture: " + type + " did not fit at " + x + "," + z);
        return e;
    }

    private static GridPos end(List<GridPos> path) {
        return path.get(path.size() - 1);
    }

    // ── Roster ──

    @Test
    @DisplayName("seventeen creatures, all the Aether's, none of them a boss")
    void roster() {
        assertEquals(17, AetherMobs.ALL.size());
        assertEquals(17, new HashSet<>(AetherMobs.ALL).size(), "an id is listed twice");
        for (String id : AetherMobs.ALL) {
            assertTrue(id.startsWith("aether:"), id);
        }
        assertTrue(AetherMobs.ALL.contains(AetherMobs.WHIRLWIND));
        assertTrue(AetherMobs.ALL.contains(AetherMobs.EVIL_WHIRLWIND));
        for (String absent : new String[]{
                "aether:slider", "aether:valkyrie_queen", "aether:sun_spirit"}) {
            assertFalse(AetherMobs.ALL.contains(absent), absent + " does not belong in an arena roster");
        }
    }

    @Test
    @DisplayName("every creature has a brain, and nothing else is given one")
    void everyCreatureHasABrain() {
        Map<String, EnemyAI> brains = AetherMobs.brains();
        assertEquals(new HashSet<>(AetherMobs.ALL), brains.keySet());
        for (String id : AetherMobs.ALL) {
            assertNotNull(brains.get(id), id);
        }
    }

    @Test
    @DisplayName("the right kind of brain for each")
    void brainsMatchBehaviour() {
        Map<String, EnemyAI> brains = AetherMobs.brains();
        assertInstanceOf(ZephyrAI.class, brains.get(AetherMobs.ZEPHYR));
        assertInstanceOf(CockatriceAI.class, brains.get(AetherMobs.COCKATRICE));
        assertInstanceOf(AechorPlantAI.class, brains.get(AetherMobs.AECHOR_PLANT));
        assertInstanceOf(SwetAI.class, brains.get(AetherMobs.BLUE_SWET));
        assertSame(brains.get(AetherMobs.BLUE_SWET), brains.get(AetherMobs.GOLDEN_SWET));
        assertInstanceOf(SentryAI.class, brains.get(AetherMobs.SENTRY));
        assertInstanceOf(ValkyrieAI.class, brains.get(AetherMobs.VALKYRIE));
        // A valkyrie IS a MoaAI by inheritance, so the moa is checked by exact class.
        assertSame(MoaAI.class, brains.get(AetherMobs.MOA).getClass());
        assertInstanceOf(ZombieAI.class, brains.get(AetherMobs.MIMIC));
        assertInstanceOf(ZombieAI.class, brains.get(AetherMobs.FIRE_MINION));
        for (String id : new String[]{AetherMobs.PHYG, AetherMobs.FLYING_COW, AetherMobs.SHEEPUFF,
                AetherMobs.AERBUNNY, AetherMobs.AERWHALE}) {
            assertInstanceOf(PassiveAI.class, brains.get(id), id);
        }
    }

    @Test
    @DisplayName("only the two swets are swets")
    void swets() {
        assertTrue(AetherMobs.isSwet("aether:blue_swet"));
        assertTrue(AetherMobs.isSwet("aether:golden_swet"));
        assertFalse(AetherMobs.isSwet("aether:sentry"), "a sentry is a slime underneath, but not a swet");
        assertFalse(AetherMobs.isSwet("minecraft:slime"));
        assertFalse(AetherMobs.isSwet(null));
    }

    // ── Drops ──

    private static Set<String> dropIds(String mob) {
        Set<String> ids = new HashSet<>();
        for (AetherMobs.Drop drop : AetherMobs.dropTable(mob)) ids.add(drop.itemId());
        return ids;
    }

    @Test
    @DisplayName("every creature has a drop table, well formed")
    void dropTablesAreWellFormed() {
        for (String mob : AetherMobs.ALL) {
            List<AetherMobs.Drop> table = AetherMobs.dropTable(mob);
            assertNotNull(table, mob + " is ours and must not fall through to another module");
            for (AetherMobs.Drop drop : table) {
                assertTrue(drop.itemId().matches("[a-z_]+:[a-z_]+"), mob + " drops a malformed id: " + drop.itemId());
                assertTrue(drop.weight() > 0, mob + " has a weightless drop: " + drop.itemId());
            }
        }
    }

    @Test
    @DisplayName("the drops are the Aether's own")
    void dropsFollowTheAether() {
        assertEquals(Set.of("aether:cold_aercloud"), dropIds(AetherMobs.ZEPHYR));
        assertEquals(Set.of("minecraft:feather"), dropIds(AetherMobs.COCKATRICE));
        assertEquals(Set.of("aether:aechor_petal"), dropIds(AetherMobs.AECHOR_PLANT));
        assertEquals(Set.of("aether:swet_ball", "aether:blue_aercloud"), dropIds(AetherMobs.BLUE_SWET));
        assertEquals(Set.of("minecraft:glowstone"), dropIds(AetherMobs.GOLDEN_SWET));
        assertTrue(dropIds(AetherMobs.SENTRY).contains("aether:carved_stone"));
        assertEquals(Set.of("minecraft:chest", "aether:zanite_gemstone"), dropIds(AetherMobs.MIMIC));
        assertEquals(Set.of("aether:victory_medal"), dropIds(AetherMobs.VALKYRIE),
            "the medal is the point of fighting one, so nothing may dilute it");
        assertEquals(Set.of("minecraft:feather"), dropIds(AetherMobs.MOA));
        assertEquals(Set.of("minecraft:porkchop", "minecraft:feather"), dropIds(AetherMobs.PHYG));
        assertEquals(Set.of("minecraft:beef", "minecraft:leather"), dropIds(AetherMobs.FLYING_COW));
        assertEquals(Set.of("minecraft:white_wool", "minecraft:mutton"), dropIds(AetherMobs.SHEEPUFF));
        assertEquals(Set.of("minecraft:string"), dropIds(AetherMobs.AERBUNNY));
    }

    @Test
    @DisplayName("ours but empty-handed is an empty table; not ours is no table at all")
    void emptyIsNotTheSameAsUnowned() {
        assertTrue(AetherMobs.dropTable(AetherMobs.FIRE_MINION).isEmpty());
        assertTrue(AetherMobs.dropTable(AetherMobs.AERWHALE).isEmpty());
        assertNull(AetherMobs.dropTable("minecraft:zombie"));
        assertFalse(AetherMobs.dropTable(AetherMobs.WHIRLWIND).isEmpty());
        assertFalse(AetherMobs.dropTable(AetherMobs.EVIL_WHIRLWIND).isEmpty());
        assertNull(AetherMobs.dropTable("aether:slider"), "the bosses are another module's");
        assertNull(AetherMobs.dropTable(null));
    }

    // ── What a hit leaves behind ──

    @Test
    @DisplayName("a swet lifts you for a turn, a fire minion burns you for two, nothing else leaves anything")
    void riders() {
        for (String swet : new String[]{AetherMobs.BLUE_SWET, AetherMobs.GOLDEN_SWET}) {
            AetherMobs.Rider rider = AetherMobs.riderFor(swet);
            assertEquals(CombatEffects.EffectType.LEVITATION, rider.effect());
            assertEquals(1, rider.turns());
        }
        AetherMobs.Rider burn = AetherMobs.riderFor(AetherMobs.FIRE_MINION);
        assertEquals(CombatEffects.EffectType.BURNING, burn.effect());
        assertEquals(2, burn.turns());

        for (String mob : AetherMobs.ALL) {
            if (AetherMobs.isSwet(mob) || mob.equals(AetherMobs.FIRE_MINION)) continue;
            assertNull(AetherMobs.riderFor(mob), mob);
        }
        // Poison is the theme tag's job. A second copy here would stack with it.
        assertNull(AetherMobs.riderFor(AetherMobs.COCKATRICE));
        assertNull(AetherMobs.riderFor("minecraft:blaze"));
        assertNull(AetherMobs.riderFor(null));
    }

    // ── Fitting a cloud to a tile ──

    @Test
    @DisplayName("a cloud is shrunk to nine tenths of its footprint and never enlarged")
    void fitScale() {
        assertEquals(0.2, AetherMobs.fitScale(4.5, 1), 1e-9, "a zephyr on one tile");
        assertEquals(0.4, AetherMobs.fitScale(4.5, 2), 1e-9, "a zephyr on a 2x2");
        assertEquals(0.3, AetherMobs.fitScale(3.0, 1), 1e-9, "an aerwhale on one tile");
        assertEquals(1.0, AetherMobs.fitScale(0.9, 1), 1e-9, "already fits");
        assertEquals(1.0, AetherMobs.fitScale(0.5, 3), 1e-9, "never made bigger than it is");
        assertEquals(1.0, AetherMobs.fitScale(0, 1), 1e-9);
        assertEquals(1.0, AetherMobs.fitScale(4.5, 0), 1e-9);
    }

    // ── Whirlwinds ──

    @Test
    @DisplayName("a whirlwind tosses whoever is beside it, straight away, and does no damage")
    void whirlwindTosses() {
        GridPos player = new GridPos(5, 5);
        GridArena a = arena(player);
        CombatEntity wind = mob(a, AetherMobs.WHIRLWIND, 4, 5, 3, 1, 2);
        EnemyAction action = new com.crackedgames.craftics.compat.aether.ai.WhirlwindAI(false)
            .decideAction(wind, a, player);
        EnemyAction.ForcedMovement toss = assertInstanceOf(EnemyAction.ForcedMovement.class, action);
        assertEquals(-1, toss.targetEntityId(), "the player, not a pet");
        assertEquals(1, toss.dx(), "away from the whirlwind");
        assertEquals(0, toss.dz());
        assertEquals(com.crackedgames.craftics.compat.aether.ai.WhirlwindAI.TOSS_TILES, toss.tiles());
    }

    @Test
    @DisplayName("a whirlwind with nobody beside it only drifts, and never into water")
    void whirlwindDrifts() {
        GridPos player = new GridPos(10, 10);
        GridArena a = arena(player);
        // Ring the whirlwind with water: wherever it would wander, it would get wet.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx != 0 || dz != 0) paint(a, TileType.WATER, 3 + dx, 3 + dz);
            }
        }
        CombatEntity wind = mob(a, AetherMobs.WHIRLWIND, 3, 3, 3, 1, 2);
        var ai = new com.crackedgames.craftics.compat.aether.ai.WhirlwindAI(false);
        for (int i = 0; i < 40; i++) {
            EnemyAction action = ai.decideAction(wind, a, player);
            if (action instanceof EnemyAction.Move move) {
                for (GridPos step : move.path()) {
                    assertFalse(a.getTile(step).isWater(), "it walked into water at " + step);
                }
            } else {
                assertInstanceOf(EnemyAction.Idle.class, action);
            }
        }
    }

    @Test
    @DisplayName("a whirlwind hunts nobody; an evil one does")
    void onlyTheEvilOneIsAThreat() {
        GridPos player = new GridPos(8, 8);
        GridArena a = arena(player);
        CombatEntity wind = mob(a, AetherMobs.WHIRLWIND, 2, 2, 3, 1, 2);
        assertFalse(new com.crackedgames.craftics.compat.aether.ai.WhirlwindAI(false)
            .isHostileThreat(wind, a, player));
        assertTrue(new com.crackedgames.craftics.compat.aether.ai.WhirlwindAI(true)
            .isHostileThreat(wind, a, player));
    }

    @Test
    @DisplayName("an evil whirlwind closes in, and beside its target its toss is a real hit")
    void evilWhirlwindHits() {
        GridPos player = new GridPos(8, 5);
        GridArena a = arena(player);
        var ai = new com.crackedgames.craftics.compat.aether.ai.WhirlwindAI(true);

        CombatEntity far = mob(a, AetherMobs.EVIL_WHIRLWIND, 2, 5, 6, 1, 2);
        EnemyAction.Move chase = assertInstanceOf(EnemyAction.Move.class, ai.decideAction(far, a, player));
        assertTrue(end(chase.path()).manhattanDistance(player) < far.getGridPos().manhattanDistance(player));

        CombatEntity near = mob(a, AetherMobs.EVIL_WHIRLWIND, 7, 5, 6, 1, 2);
        EnemyAction.AttackWithKnockback hit = assertInstanceOf(
            EnemyAction.AttackWithKnockback.class, ai.decideAction(near, a, player));
        assertEquals(6, hit.damage());
        assertEquals(com.crackedgames.craftics.compat.aether.ai.WhirlwindAI.EVIL_TOSS_TILES,
            hit.knockbackTiles());
    }

    @Test
    @DisplayName("a throw is shortened by a wall rather than refused, and refused only with no room at all")
    void tossRespectsWalls() {
        GridPos player = new GridPos(5, 5);
        GridArena a = arena(player);
        paint(a, TileType.OBSTACLE, 7, 5);
        CombatEntity wind = mob(a, AetherMobs.WHIRLWIND, 4, 5, 3, 1, 2);
        var ai = new com.crackedgames.craftics.compat.aether.ai.WhirlwindAI(false);
        EnemyAction.ForcedMovement toss = assertInstanceOf(
            EnemyAction.ForcedMovement.class, ai.decideAction(wind, a, player));
        assertEquals(1, toss.tiles(), "one clear tile before the wall");

        paint(a, TileType.OBSTACLE, 6, 5);
        assertFalse(ai.decideAction(wind, a, player) instanceof EnemyAction.ForcedMovement,
            "no room behind the victim: nothing to throw them into");
    }

    // ── Aechor plant ──

    @Test
    @DisplayName("an aechor plant spits at what it can reach and otherwise waits")
    void aechorPlantShootsInRange() {
        GridPos player = new GridPos(5, 8);
        GridArena a = arena(player);
        CombatEntity plant = mob(a, AetherMobs.AECHOR_PLANT, 5, 5, 3, 3, 2);
        AechorPlantAI ai = new AechorPlantAI();

        EnemyAction shot = ai.decideAction(plant, a, player);
        assertEquals(3, assertInstanceOf(EnemyAction.RangedAttack.class, shot).damage());

        GridPos far = new GridPos(5, 9);
        standPlayer(a, far);
        assertInstanceOf(EnemyAction.Idle.class, ai.decideAction(plant, a, far), "one tile out of range");
    }

    @Test
    @DisplayName("an aechor plant will not shoot through a wall")
    void aechorPlantNeedsALine() {
        GridPos player = new GridPos(5, 8);
        GridArena a = arena(player);
        paint(a, TileType.OBSTACLE, 5, 6);
        CombatEntity plant = mob(a, AetherMobs.AECHOR_PLANT, 5, 5, 3, 3, 2);
        assertInstanceOf(EnemyAction.Idle.class, new AechorPlantAI().decideAction(plant, a, player));
    }

    @Test
    @DisplayName("an aechor plant never asks to move, wherever the player stands")
    void aechorPlantNeverMoves() {
        AechorPlantAI ai = new AechorPlantAI();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                if (x == 5 && z == 5) continue;
                GridPos player = new GridPos(x, z);
                GridArena a = arena(player);
                CombatEntity plant = mob(a, AetherMobs.AECHOR_PLANT, 5, 5, 3, 3, 2);
                // Hurt, too: being hit is what sends a passive mob running.
                plant.takeDamage(1);
                EnemyAction action = ai.decideAction(plant, a, player);
                assertTrue(action instanceof EnemyAction.RangedAttack || action instanceof EnemyAction.Idle,
                    "player at " + player + " made the plant ask for " + action);
            }
        }
    }

    @Test
    @DisplayName("an aechor plant's danger zone is its range, not its range plus a walk")
    void aechorPlantThreatIsItsRange() {
        GridArena a = arena(new GridPos(0, 0));
        CombatEntity plant = mob(a, AetherMobs.AECHOR_PLANT, 5, 5, 3, 3, 2);
        Set<GridPos> tiles = new AechorPlantAI().computeThreatTiles(plant, a);
        assertEquals(25, tiles.size(), "a radius-3 diamond");
        assertTrue(tiles.contains(new GridPos(5, 8)));
        assertTrue(tiles.contains(new GridPos(7, 6)));
        assertFalse(tiles.contains(new GridPos(5, 9)));
        assertFalse(tiles.contains(new GridPos(7, 7)));
    }

    // ── Sentry ──

    @Test
    @DisplayName("a sentry sleeps until someone comes within three tiles")
    void sentrySleeps() {
        GridPos player = new GridPos(9, 9);
        GridArena a = arena(player);
        CombatEntity sentry = mob(a, AetherMobs.SENTRY, 0, 0, 5, 1, 3);
        SentryAI ai = new SentryAI();

        for (int turn = 0; turn < 3; turn++) {
            assertInstanceOf(EnemyAction.Idle.class, ai.decideAction(sentry, a, player));
        }
        assertTrue(ai.isHostileThreat(sentry, a, player), "asleep is not the same as harmless");
        assertEquals(10, ai.computeThreatTiles(sentry, a).size(), "the wake ring, clipped by the corner");

        GridPos four = new GridPos(4, 0);
        standPlayer(a, four);
        assertInstanceOf(EnemyAction.Idle.class, ai.decideAction(sentry, a, four), "four tiles is still outside");
    }

    @Test
    @DisplayName("a woken sentry hops next to its target, then detonates on its next turn")
    void sentryHopsThenDetonates() {
        GridPos player = new GridPos(3, 0);
        GridArena a = arena(player);
        CombatEntity sentry = mob(a, AetherMobs.SENTRY, 0, 0, 5, 1, 3);
        SentryAI ai = new SentryAI();

        EnemyAction.Move hop = assertInstanceOf(EnemyAction.Move.class, ai.decideAction(sentry, a, player),
            "arriving is a move and nothing more: the player gets a turn to react");
        assertEquals(new GridPos(2, 0), end(hop.path()));
        assertNull(ai.computeThreatTiles(sentry, a), "awake, it is drawn like any other mob");

        assertTrue(a.moveEntity(sentry, end(hop.path())));
        EnemyAction.Explode blast = assertInstanceOf(EnemyAction.Explode.class, ai.decideAction(sentry, a, player));
        assertEquals(5, blast.damage());
        assertEquals(SentryAI.BLAST_RADIUS, blast.radius());
    }

    @Test
    @DisplayName("once awake a sentry stays awake, and being hit wakes it from anywhere")
    void sentryStaysAwake() {
        GridPos near = new GridPos(3, 0);
        GridArena a = arena(near);
        CombatEntity sentry = mob(a, AetherMobs.SENTRY, 0, 0, 5, 1, 3);
        SentryAI ai = new SentryAI();
        ai.decideAction(sentry, a, near);

        GridPos gone = new GridPos(10, 10);
        standPlayer(a, gone);
        assertInstanceOf(EnemyAction.Move.class, ai.decideAction(sentry, a, gone), "it keeps coming");

        GridArena b = arena(gone);
        CombatEntity sniped = mob(b, AetherMobs.SENTRY, 0, 0, 5, 1, 3);
        sniped.takeDamage(2);
        assertInstanceOf(EnemyAction.Move.class, ai.decideAction(sniped, b, gone));
    }

    @Test
    @DisplayName("a sentry never swings: it only ever waits, hops or explodes")
    void sentryNeverMelees() {
        SentryAI ai = new SentryAI();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                if (x == 5 && z == 5) continue;
                GridPos player = new GridPos(x, z);
                GridArena a = arena(player);
                CombatEntity sentry = mob(a, AetherMobs.SENTRY, 5, 5, 5, 1, 3);
                sentry.takeDamage(1);
                EnemyAction action = ai.decideAction(sentry, a, player);
                assertTrue(action instanceof EnemyAction.Move || action instanceof EnemyAction.Explode
                    || action instanceof EnemyAction.Idle, "player at " + player + ": " + action);
                boolean adjacent = sentry.minDistanceTo(player) <= 1;
                assertEquals(adjacent, action instanceof EnemyAction.Explode, "player at " + player);
            }
        }
    }

    // ── Zephyr ──

    @Test
    @DisplayName("a zephyr's gust throws its target two tiles straight away from it")
    void zephyrGust() {
        GridPos player = new GridPos(6, 5);
        GridArena a = arena(player);
        CombatEntity zephyr = mob(a, AetherMobs.ZEPHYR, 2, 5, 1, 5, 1);

        EnemyAction.ForcedMovement gust = assertInstanceOf(EnemyAction.ForcedMovement.class,
            new ZephyrAI().decideAction(zephyr, a, player));
        assertEquals(-1, gust.targetEntityId(), "-1 is the player");
        assertEquals(1, gust.dx());
        assertEquals(0, gust.dz());
        assertEquals(ZephyrAI.GUST_TILES, gust.tiles());
    }

    @Test
    @DisplayName("the gust is directly away, diagonals included")
    void gustDirection() {
        assertArrayEquals(new int[]{1, 0}, ZephyrAI.gustDirection(new GridPos(2, 5), new GridPos(6, 5)));
        assertArrayEquals(new int[]{-1, 0}, ZephyrAI.gustDirection(new GridPos(6, 5), new GridPos(2, 5)));
        assertArrayEquals(new int[]{0, -1}, ZephyrAI.gustDirection(new GridPos(4, 9), new GridPos(4, 1)));
        assertArrayEquals(new int[]{1, 1}, ZephyrAI.gustDirection(new GridPos(2, 2), new GridPos(5, 4)));
        assertArrayEquals(new int[]{-1, 1}, ZephyrAI.gustDirection(new GridPos(5, 2), new GridPos(4, 6)));
    }

    @Test
    @DisplayName("a gust is only thrown across clear, harmless ground")
    void gustStopsShortOfTrouble() {
        GridPos player = new GridPos(6, 5);
        ZephyrAI ai = new ZephyrAI();

        GridArena wall = arena(player);
        paint(wall, TileType.OBSTACLE, 8, 5);
        CombatEntity z1 = mob(wall, AetherMobs.ZEPHYR, 2, 5, 1, 5, 1);
        assertEquals(1, assertInstanceOf(EnemyAction.ForcedMovement.class,
            ai.decideAction(z1, wall, player)).tiles(), "one tile of room is a one tile throw");

        // A pit two tiles back would cost the player health; the throw stops before it.
        GridArena pit = arena(player);
        paint(pit, TileType.VOID, 8, 5);
        CombatEntity z2 = mob(pit, AetherMobs.ZEPHYR, 2, 5, 1, 5, 1);
        assertEquals(1, assertInstanceOf(EnemyAction.ForcedMovement.class,
            ai.decideAction(z2, pit, player)).tiles());

        GridArena lava = arena(player);
        paint(lava, TileType.LAVA, 7, 5);
        CombatEntity z3 = mob(lava, AetherMobs.ZEPHYR, 2, 5, 1, 5, 1);
        assertFalse(ai.decideAction(z3, lava, player) instanceof EnemyAction.ForcedMovement,
            "no room at all: it does something else with the turn");
    }

    @Test
    @DisplayName("a zephyr that has been reached runs instead of blowing")
    void zephyrPanics() {
        GridPos player = new GridPos(3, 5);
        GridArena a = arena(player);
        CombatEntity zephyr = mob(a, AetherMobs.ZEPHYR, 4, 5, 1, 5, 1);

        EnemyAction.Move drift = assertInstanceOf(EnemyAction.Move.class,
            new ZephyrAI().decideAction(zephyr, a, player));
        assertTrue(end(drift.path()).manhattanDistance(player) > 1, "it must end further off than it started");
    }

    @Test
    @DisplayName("a zephyr out of range floats closer")
    void zephyrCloses() {
        GridPos player = new GridPos(11, 5);
        GridArena a = arena(player);
        CombatEntity zephyr = mob(a, AetherMobs.ZEPHYR, 0, 5, 1, 4, 1);

        EnemyAction.Move drift = assertInstanceOf(EnemyAction.Move.class,
            new ZephyrAI().decideAction(zephyr, a, player));
        assertEquals(new GridPos(1, 5), end(drift.path()));
    }

    @Test
    @DisplayName("a zephyr aimed at a pet throws the pet")
    void zephyrThrowsPets() {
        GridPos player = new GridPos(11, 11);
        GridArena a = arena(player);
        CombatEntity zephyr = mob(a, AetherMobs.ZEPHYR, 2, 5, 1, 5, 1);
        CombatEntity wolf = mob(a, "minecraft:wolf", 6, 5, 3, 1, 3);
        wolf.setAlly(true);

        EnemyAction.ForcedMovement gust = assertInstanceOf(EnemyAction.ForcedMovement.class,
            new ZephyrAI().decideAction(zephyr, a, wolf.getGridPos()));
        assertEquals(wolf.getEntityId(), gust.targetEntityId());
    }

    @Test
    @DisplayName("a zephyr never asks for anything that deals damage")
    void zephyrNeverHurts() {
        ZephyrAI ai = new ZephyrAI();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                if (x == 5 && z == 5) continue;
                GridPos player = new GridPos(x, z);
                GridArena a = arena(player);
                CombatEntity zephyr = mob(a, AetherMobs.ZEPHYR, 5, 5, 4, 5, 1);
                EnemyAction action = ai.decideAction(zephyr, a, player);
                assertTrue(action instanceof EnemyAction.ForcedMovement || action instanceof EnemyAction.Move
                    || action instanceof EnemyAction.Idle, "player at " + player + ": " + action);
            }
        }
    }

    // ── Cockatrice ──

    @Test
    @DisplayName("a cockatrice with a clear shot and room to breathe just shoots")
    void cockatriceShoots() {
        GridPos player = new GridPos(4, 7);
        GridArena a = arena(player);
        CombatEntity bird = mob(a, AetherMobs.COCKATRICE, 4, 4, 2, 3, 2);
        assertEquals(2, assertInstanceOf(EnemyAction.RangedAttack.class,
            new CockatriceAI().decideAction(bird, a, player)).damage());
    }

    @Test
    @DisplayName("a crowded cockatrice shoots first and then backs off, in that order")
    void cockatriceShootsAndScoots() {
        GridPos player = new GridPos(4, 5);
        GridArena a = arena(player);
        CombatEntity bird = mob(a, AetherMobs.COCKATRICE, 4, 4, 2, 3, 2);

        EnemyAction.CompositeAction turn = assertInstanceOf(EnemyAction.CompositeAction.class,
            new CockatriceAI().decideAction(bird, a, player));
        assertEquals(2, turn.actions().size());
        // Order matters: the engine resolves a bundled shot the moment it is dispatched, so
        // it has to come before the walk for the shot to leave from where the line was good.
        assertInstanceOf(EnemyAction.RangedAttack.class, turn.actions().get(0));
        EnemyAction.Move retreat = assertInstanceOf(EnemyAction.Move.class, turn.actions().get(1));
        assertTrue(end(retreat.path()).manhattanDistance(player) > 1);
    }

    @Test
    @DisplayName("a cockatrice with no shot walks to one, and never moves and shoots in one action")
    void cockatriceRepositions() {
        GridPos player = new GridPos(4, 4);
        GridArena a = arena(player);
        paint(a, TileType.OBSTACLE, 2, 4);
        CombatEntity bird = mob(a, AetherMobs.COCKATRICE, 0, 4, 2, 5, 2);
        assertFalse(Pathfinding.hasLineOfSight(a, bird.getGridPos(), player), "fixture: the wall must block");

        EnemyAction.Move walk = assertInstanceOf(EnemyAction.Move.class,
            new CockatriceAI().decideAction(bird, a, player));
        assertTrue(Pathfinding.hasLineOfSight(a, end(walk.path()), player), "it walked somewhere it can shoot from");
    }

    @Test
    @DisplayName("a cockatrice crowded by a pet runs; the bundled shot would have hit a player instead")
    void cockatriceRunsFromPets() {
        GridPos player = new GridPos(11, 11);
        GridArena a = arena(player);
        CombatEntity bird = mob(a, AetherMobs.COCKATRICE, 4, 4, 2, 3, 2);
        CombatEntity wolf = mob(a, "minecraft:wolf", 4, 5, 3, 1, 3);
        wolf.setAlly(true);
        assertInstanceOf(EnemyAction.Move.class, new CockatriceAI().decideAction(bird, a, wolf.getGridPos()));
    }

    // ── Moa ──

    @Test
    @DisplayName("a moa is no threat until it is hit, and then it fights")
    void moaIsNeutral() {
        GridPos player = new GridPos(5, 6);
        GridArena a = arena(player);
        CombatEntity moa = mob(a, AetherMobs.MOA, 5, 5, 4, 1, 3);
        MoaAI ai = new MoaAI();

        assertFalse(ai.isHostileThreat(moa, a, player));
        assertTrue(ai.computeThreatTiles(moa, a).isEmpty(), "a calm moa paints no danger");
        for (int turn = 0; turn < 20; turn++) {
            EnemyAction calm = ai.decideAction(moa, a, player);
            assertTrue(calm instanceof EnemyAction.Idle || calm instanceof EnemyAction.Move,
                "a calm moa standing next to the player asked for " + calm);
        }

        moa.takeDamage(1);
        assertTrue(ai.isHostileThreat(moa, a, player));
        assertNull(ai.computeThreatTiles(moa, a));
        assertEquals(4, assertInstanceOf(EnemyAction.Attack.class, ai.decideAction(moa, a, player)).damage());
    }

    // ── Valkyrie ──

    private static CombatEntity provokedValkyrie(GridArena a, int x, int z, int speed) {
        CombatEntity valkyrie = mob(a, AetherMobs.VALKYRIE, x, z, 6, 1, speed);
        valkyrie.setEnraged(true);
        return valkyrie;
    }

    @Test
    @DisplayName("a valkyrie leaves you alone until you hit it")
    void valkyrieIsNeutral() {
        GridPos player = new GridPos(5, 6);
        GridArena a = arena(player);
        CombatEntity valkyrie = mob(a, AetherMobs.VALKYRIE, 5, 5, 6, 1, 3);
        ValkyrieAI ai = new ValkyrieAI();

        assertFalse(ai.isHostileThreat(valkyrie, a, player));
        for (int turn = 0; turn < 20; turn++) {
            EnemyAction calm = ai.decideAction(valkyrie, a, player);
            assertTrue(calm instanceof EnemyAction.Idle || calm instanceof EnemyAction.Move, "asked for " + calm);
        }

        valkyrie.takeDamage(1);
        assertTrue(ai.isHostileThreat(valkyrie, a, player));
        assertInstanceOf(EnemyAction.Attack.class, ai.decideAction(valkyrie, a, player));
    }

    @Test
    @DisplayName("a valkyrie lunges down a clear line, harder the longer the run")
    void valkyrieLunges() {
        ValkyrieAI ai = new ValkyrieAI();
        for (int gap = ValkyrieAI.LUNGE_MIN; gap <= ValkyrieAI.LUNGE_MAX; gap++) {
            GridPos player = new GridPos(2 + gap, 5);
            GridArena a = arena(player);
            // Speed 1: every tile past the first is the lunge, not its walk.
            CombatEntity valkyrie = provokedValkyrie(a, 2, 5, 1);

            EnemyAction.MoveAndAttack lunge = assertInstanceOf(EnemyAction.MoveAndAttack.class,
                ai.decideAction(valkyrie, a, player), "gap " + gap);
            assertEquals(gap - 1, lunge.path().size(), "it stops on the tile before the target");
            assertEquals(new GridPos(1 + gap, 5), end(lunge.path()));
            assertEquals(6 + gap - 2, lunge.damage(), "gap " + gap);
        }
    }

    @Test
    @DisplayName("run-up past the first tile is what a lunge adds")
    void lungeDamage() {
        assertEquals(6, ValkyrieAI.lungeDamage(6, 1));
        assertEquals(7, ValkyrieAI.lungeDamage(6, 2));
        assertEquals(8, ValkyrieAI.lungeDamage(6, 3));
        assertEquals(6, ValkyrieAI.lungeDamage(6, 0), "never a penalty");
    }

    @Test
    @DisplayName("a blocked lane is not a lunge")
    void valkyrieNeedsAClearLane() {
        GridPos player = new GridPos(5, 5);
        GridArena a = arena(player);
        paint(a, TileType.OBSTACLE, 4, 5);
        CombatEntity valkyrie = provokedValkyrie(a, 2, 5, 1);
        assertFalse(new ValkyrieAI().decideAction(valkyrie, a, player) instanceof EnemyAction.MoveAndAttack);
    }

    @Test
    @DisplayName("out of reach, a valkyrie blinks beside its target, and then not again for three turns")
    void valkyrieBlinksOnACooldown() {
        GridPos player = new GridPos(5, 3);
        GridArena a = arena(player);
        CombatEntity valkyrie = provokedValkyrie(a, 0, 0, 1);
        ValkyrieAI ai = new ValkyrieAI();

        EnemyAction.Teleport blink = assertInstanceOf(EnemyAction.Teleport.class,
            ai.decideAction(valkyrie, a, player));
        assertEquals(1, blink.target().manhattanDistance(player), "beside the target, not on it");

        // Asked again before it has gone anywhere, it gives the same answer: nothing was
        // spent by the asking, which is what lets a preview look without touching.
        assertEquals(blink, ai.decideAction(valkyrie, a, player));

        assertTrue(a.moveEntity(valkyrie, blink.target()));
        // Far enough to be out of reach on foot, off any straight line, still in blink range.
        GridPos fled = new GridPos(9, 6);
        standPlayer(a, fled);
        for (int turn = 1; turn <= ValkyrieAI.BLINK_COOLDOWN; turn++) {
            assertFalse(ai.decideAction(valkyrie, a, fled) instanceof EnemyAction.Teleport,
                "blinked again on turn " + turn + " of the cooldown");
        }
        assertInstanceOf(EnemyAction.Teleport.class, ai.decideAction(valkyrie, a, fled));
    }

    @Test
    @DisplayName("a valkyrie that can walk into reach does that rather than blink")
    void valkyrieWalksWhenItCan() {
        GridPos player = new GridPos(5, 3);
        GridArena a = arena(player);
        CombatEntity valkyrie = provokedValkyrie(a, 3, 2, 3);
        assertInstanceOf(EnemyAction.MoveAndAttack.class, new ValkyrieAI().decideAction(valkyrie, a, player));
    }

    // ── Swet ──

    @Test
    @DisplayName("a swet hops at its prey and slams it on arrival")
    void swetHops() {
        GridPos player = new GridPos(5, 5);
        GridArena a = arena(player);
        SwetAI ai = new SwetAI();

        CombatEntity far = mob(a, AetherMobs.BLUE_SWET, 0, 5, 2, 1, 2);
        assertTrue(ai.isHostileThreat(far, a, player));
        assertInstanceOf(EnemyAction.Move.class, ai.decideAction(far, a, player));

        CombatEntity near = mob(a, AetherMobs.GOLDEN_SWET, 5, 3, 2, 1, 2);
        EnemyAction.MoveAndAttack slam = assertInstanceOf(EnemyAction.MoveAndAttack.class,
            ai.decideAction(near, a, player));
        assertEquals(2, slam.damage());

        CombatEntity beside = mob(a, AetherMobs.BLUE_SWET, 6, 5, 2, 1, 2);
        assertInstanceOf(EnemyAction.Attack.class, ai.decideAction(beside, a, player));
    }

    @Test
    @DisplayName("a swet standing in water dissolves")
    void swetDissolves() {
        GridPos player = new GridPos(5, 5);
        GridArena a = arena(player);
        paint(a, TileType.WATER, 5, 6);
        CombatEntity swet = mob(a, AetherMobs.BLUE_SWET, 5, 6, 2, 1, 2);

        EnemyAction.CustomAction dissolve = assertInstanceOf(EnemyAction.CustomAction.class,
            new SwetAI().decideAction(swet, a, player), "even with its prey right beside it");
        assertEquals(AetherMobs.SWET_DISSOLVE, dissolve.id());
    }

    @Test
    @DisplayName("a swet goes round water rather than through it")
    void swetGoesRound() {
        GridPos player = new GridPos(5, 5);
        GridArena a = arena(player);
        // The straight hop from (3,5) lands on (4,5). Flood it, and the only way to the
        // player is three steps round the pool.
        paint(a, TileType.WATER, 4, 5);
        CombatEntity swet = mob(a, AetherMobs.BLUE_SWET, 3, 5, 2, 1, 3);

        EnemyAction.MoveAndAttack hop = assertInstanceOf(EnemyAction.MoveAndAttack.class,
            new SwetAI().decideAction(swet, a, player));
        assertEquals(3, hop.path().size());
        for (GridPos step : hop.path()) {
            assertFalse(a.getTile(step).isWater(), "it stepped in the water at " + step);
        }
        assertEquals(1, end(hop.path()).manhattanDistance(player), "and still arrived");
    }

    @Test
    @DisplayName("a swet that cannot quite get round still closes the gap, dry")
    void swetGetsAsCloseAsItCanDry() {
        GridPos player = new GridPos(7, 5);
        GridArena a = arena(player);
        paint(a, TileType.WATER, 4, 5);
        CombatEntity swet = mob(a, AetherMobs.BLUE_SWET, 3, 5, 2, 1, 3);

        // Three steps round the pool end beside it, at (5,4) or (5,6).
        EnemyAction.Move hop = assertInstanceOf(EnemyAction.Move.class,
            new SwetAI().decideAction(swet, a, player));
        for (GridPos step : hop.path()) {
            assertFalse(a.getTile(step).isWater(), "it stepped in the water at " + step);
        }
        assertEquals(3, end(hop.path()).manhattanDistance(player),
            "one tile nearer than the four it started at");
    }

    @Test
    @DisplayName("a swet walled off by water waits on the bank")
    void swetWaitsAtTheBank() {
        GridPos player = new GridPos(5, 5);
        GridArena a = arena(player);
        for (int z = 0; z < SIZE; z++) paint(a, TileType.WATER, 3, z);
        CombatEntity swet = mob(a, AetherMobs.BLUE_SWET, 2, 5, 2, 1, 2);
        assertInstanceOf(EnemyAction.Idle.class, new SwetAI().decideAction(swet, a, player));
    }

    @Test
    void aValkyrieGivesExactlyOneMedalAndNothingElseIsCounted() {
        assertEquals(1, AetherMobs.fixedDropCount(AetherMobs.VALKYRIE));
        assertEquals(0, AetherMobs.fixedDropCount(AetherMobs.ZEPHYR), "everything else rolls as it always did");
        assertEquals(0, AetherMobs.fixedDropCount("minecraft:zombie"));
        assertEquals(0, AetherMobs.fixedDropCount(null));
    }
}
