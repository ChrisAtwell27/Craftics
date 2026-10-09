package com.crackedgames.craftics.compat.aether.boss;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.CrafticsAPI;
import com.crackedgames.craftics.api.CustomActionHandler;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.MobTraits;
import com.crackedgames.craftics.combat.ai.AIRegistry;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.combat.ai.PacedMover;
import com.crackedgames.craftics.combat.ai.boss.BossAI;
import com.crackedgames.craftics.combat.ai.boss.BossWarning;
import com.crackedgames.craftics.compat.aether.AetherCompat;
import com.crackedgames.craftics.compat.aether.ai.SentryAI;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import com.crackedgames.craftics.vfx.GhostBlocks;
import com.crackedgames.craftics.vfx.Vfx;
import com.crackedgames.craftics.vfx.VfxContext;
import com.crackedgames.craftics.vfx.VfxDescriptor;
import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bronze Dungeon boss - the Slider, a 2x2 block of living stone.
 *
 * <p>It is the Aether's fight moved onto a grid, and made to press:
 * <ul>
 *   <li><b>Only a pickaxe hurts it.</b> It holds a guard for the whole fight, raised when it
 *       spawns, and a pickaxe swung from a neighbouring tile is the one thing that goes
 *       through. Anything else is refused before it costs AP, with the Aether's own line.</li>
 *   <li><b>It starts asleep</b> and does nothing until it has been hurt once, so the player
 *       chooses when the fight begins.</li>
 *   <li><b>Awake, it never idles.</b> Its turns alternate, one to build and take aim, one to
 *       slide, and then round again.</li>
 *   <li><b>A slide runs until something stops it.</b> There is no length to it: it crosses
 *       the arena to the edge, or to the first block in its way, or into whoever is standing
 *       there. A player in its way is crushed: its attack, and thrown three tiles.</li>
 *   <li><b>A block stops it and is broken.</b> Anything standing on the floor, an obstacle, a
 *       wall somebody put down, the stone of the room or a brick of its own, ends that run in
 *       front of it. Every block in the row it ran into is destroyed, and anyone standing in
 *       that row beside one is crushed against it.</li>
 *   <li><b>It gathers speed, and it hits.</b> Each run starts slow and ends fast, and what it
 *       runs into is broken or struck as it gets there, not as it sets off. See
 *       {@link #stepTicks} and {@link #stepLanded}.</li>
 *   <li><b>Anything else in its way is run into.</b> A creature that is not a player stops a
 *       run as it always did, and now takes a crush for it where it stands. A sentry goes off
 *       instead, and the blast is the sentry's own: it reaches whoever is beside it, and not
 *       the Slider. Once in a slide for each of them: with runs to spare it does not stand
 *       there battering the same creature, it goes another way.</li>
 *   <li><b>It calls up sentries with its bricks.</b> On a build turn that raises a brick, one
 *       sentry comes up beside one, while fewer are standing than its phase allows. Never
 *       against a player, and never in the way of the slide it has just aimed.</li>
 *   <li><b>A hole does not stop it.</b> A pit dug through the floor, or water too deep to
 *       stand in, is made floor again as it crosses.</li>
 *   <li><b>It gets more runs as it is broken.</b> One to a slide while it has more than two
 *       thirds of its health, and a second only when the first was stopped by a block. Up to
 *       three from there down, when it turns red, and up to five in its last third. The first
 *       goes the way it painted. Each one after is aimed from where the last one stopped, at
 *       wherever everyone is standing by then. A crush ends the turn.</li>
 *   <li><b>It builds the stops it needs.</b> On a build turn it raises bricks of the
 *       dungeon's own stone, two, then three, then four as it gets its extra runs. All but
 *       one go where its next slide can use them, to be brought up level with its target.
 *       The last goes where that slide ends, to stop it out on the floor beside them and not
 *       against the far wall. The bricks crumble by themselves a few turns on, and it breaks
 *       its own like any other.</li>
 *   <li><b>It always leaves a way out.</b> Before it paints, it looks at where each player
 *       could walk to in three steps. If the slide it means to make leaves any of them no
 *       ground in reach without paint on it and no brick of its own in reach to mine, it
 *       holds back until they have one or the other: it raises a brick fewer, and with none
 *       left to give up it makes a run fewer, down to one. Mining its bricks is the answer
 *       to it, and the paint is read off the board as it stands, so a brick mined changes
 *       what is shown at once.</li>
 *   <li><b>Its passing shakes the ground.</b> The tiles against its first run, either side
 *       of it and just past where it stops, take half a crush as it goes by. Only the first
 *       run of a turn shakes anything, and nobody is both shaken and crushed in one turn.</li>
 * </ul>
 *
 * <h2>What it shows, and what it holds itself to</h2>
 * The slide is worked out on the turn it happens, from where everyone is standing then: its
 * first run sets off the way it took aim and goes as far as things let it, and the rest are
 * aimed afresh, so they follow whoever moved. What it shows of that is its first run and no
 * more: the ground that run will cross, with arrows pointing the way it goes, and the ground
 * it will shake in passing with none. Where it turns off a brick, and everything a later
 * run could reach, is not painted. That is for the player to read off the bricks. So ground
 * with no paint is safe from its first run only. See {@link #shown}.
 *
 * <p>What it reasons with is still the whole of the danger, every tile on which the slide
 * would crush someone standing there, which {@link #telegraph} works out. It is against
 * that, and not against the little it paints, that it checks it has left a way out.
 *
 * <p>Two things are fixed when the paint goes down so that it stays true: the way its first
 * run goes, and how many runs it has. A blow that takes it across a third of its health
 * after that gives it its extra runs from the next slide, not this one. And a brick on its
 * last turn is not counted on, since it will have crumbled before the slide.
 *
 * <p>The one thing that breaks the alternation is a slide it cannot make as shown. Shoved off
 * the tile it took aim from, or with something it does not crush come to stand against its
 * face that way, it does not slide down ground nobody was shown and it does not wait: it
 * builds and takes aim again, and slides on the turn after.
 *
 * <p>The warning is held rather than timed, so that the base class never resolves anything
 * from it, and it is cleared by hand when the slide is made. What a slide comes to is asked
 * for as one bundle, in the order it has to happen: the ground to make floor, then the run,
 * then the wake. The run is the only part that takes time. The engine gives a turn to the
 * first such action in a bundle and drops any other, so however many runs there are, they are
 * one path with corners in it, and a crush at the end is that same path with the strike on
 * arrival.
 *
 * <h2>What the real entity still does</h2>
 * See {@link #dress}. The short version: under NoAI the Aether's Slider cannot move, hit or
 * break anything, but it still boils away liquid within a block of itself every tick, and
 * {@link #boiledAway} keeps the grid honest about that.
 */
public class SliderAI extends BossAI implements PacedMover {

    public static final String BIOME_ID = AetherCompat.BRONZE_DUNGEON;
    public static final String AI_KEY = "boss:" + BIOME_ID;
    public static final String ENTITY_TYPE = AetherCompat.MOD_ID + ":slider";

    /** The custom action that plays the awakening. Top level only: a bundle would drop it. */
    public static final String AWAKEN = "craftics:aether_slider_awaken";

    /** The Aether's own line for the wrong tool, in the red every other refusal uses. */
    public static final String PICKAXE_HINT = "§cHmm. Perhaps I need to attack it with a Pickaxe?";

    /** Tiles a crushed player is thrown. */
    public static final int KNOCKBACK_TILES = 3;
    /** Turns a brick it raises stands before it crumbles by itself. */
    public static final int BRICK_TURNS = 4;
    /** What its bricks are made of: the stone the Bronze Dungeon is built from. */
    public static final String BRICK_BLOCK = AetherCompat.MOD_ID + ":carved_stone";

    /** What comes up beside its bricks. */
    public static final String SENTRY_TYPE = AetherCompat.MOD_ID + ":sentry";
    /** A sentry it calls up is slighter than one met in the halls. */
    public static final int SENTRY_HP = 8;
    /** How near a player a sentry may come up. Never against one, where it would go off unannounced. */
    static final int SENTRY_CLEARANCE = 2;

    /**
     * Ticks each tile of a run takes, from a standing start. Past the end of this it is a
     * tile a tick. Red, it skips the first: it is already angry.
     */
    private static final int[] RUN_TICKS = {7, 5, 4, 3, 2, 2};

    /**
     * How long a block it has broken goes on being shown, in ticks, if nothing says sooner.
     * Something always does: the run reaching it, or the move ending.
     */
    private static final int SHOWN_UNTIL_HIT = 200;

    /** How far up a block it has broken is cleared, in blocks, so a pillar leaves no stump. */
    private static final int TALLEST_BLOCK = 8;

    /** The Aether's hitbox for it, in blocks: exactly its footprint. */
    private static final double NATIVE_WIDTH = 2.0;
    private static final String NAMEPLATE = "§6§lThe Slider";
    /** Bronze, for the lane. */
    private static final int LANE_COLOR = 0xFFCC8833;
    /**
     * Turns a painted route is given before the base class would resolve it itself. Never
     * reached: the paint is cleared by hand on the turn the slide happens.
     */
    private static final int HELD = 1_000_000;

    /** The four ways it can go, in the order every tie is broken. */
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    // ── Per-fight state ──

    private boolean awake = false;
    /**
     * Whether its next turn is a slide. False, and it is a build. They alternate, unless a
     * build found no way to go at all: then it builds again.
     */
    private boolean sliding = false;
    /** The way its next slide sets off, as painted, or null when it had no way to go. */
    private int[] aimed = null;
    /** Where it stood when it took that aim. */
    private GridPos aimedFrom = null;
    /**
     * The runs it settled on when it took that aim: the phase it was in, or fewer if it held
     * back, as {@link #exactly}. The slide that follows has those and no others, whatever it
     * is hit for in between, or the paint would be wrong for a blow landed after it went down.
     */
    private int aimedRuns = 1;
    /** How many bricks, and how many runs, it gave up on its last build to leave a way out. */
    private int heldBricks = 0;
    private int heldRuns = 0;
    /** Who it was sent after when it took aim. */
    private GridPos aimedTarget = null;
    /** Bricks that were on their last turn when it took aim, and so are gone before the slide. */
    private List<GridPos> aboutToCrumble = List.of();
    /** The bricks it asked for on that turn, until the engine has been seen to raise them. */
    private List<GridPos> notYetRaised = null;
    /** What is shown of that slide as last worked out, and the board it was worked out for. See {@link #livePaint}. */
    private BossWarning.Live paintNow = null;
    private Scene paintFor = null;
    /** The whole danger of that slide on the same board, worked out when it is asked for. See {@link #danger}. */
    private Telegraph dangerNow = null;
    /** Who it was painted for and where, kept for whoever asks for the danger afterwards. */
    private CombatEntity paintSelf = null;
    private GridArena paintArena = null;
    /** Whether there are sentries to call up: the Aether's own, so only with the Aether. */
    private boolean sentries = AetherCompat.isLoaded();
    /** Blocks this slide has broken that are still to be seen to break, each when its run arrives. */
    private final List<Break> breaksToCome = new ArrayList<>();

    @Override
    public int getGridSize() { return 2; }

    /** Its bricks are the dungeon's own stone. The engine falls back to its default without the Aether. */
    @Override
    public String getWallBlockId() { return BRICK_BLOCK; }

    /** It sleeps in the middle of its room, which is built around it. */
    @Override
    public boolean spawnsAtCenter() { return true; }

    /**
     * It changes the floor every turn, and the engine's line for that would be said every
     * turn. Bricks get a line of their own. Ground it breaks or fills on its way gets none:
     * the slide that did it is there to be seen.
     */
    @Override
    public String describeTerrain(TileType type, int changed) {
        if (type == TileType.NORMAL) return "";
        if (type != TileType.OBSTACLE) return null;
        return changed == 1 ? "§6  The Slider raises a brick." : "§6  The Slider raises " + changed + " bricks.";
    }

    // ================================================================
    // Registration
    // ================================================================

    private static boolean registered = false;

    /**
     * Register the fight. The brain, its traits and the awakening go in whether or not the
     * Aether is installed. The spawn hook waits for the mod, for the reason every Aether hook
     * does: the hook registry reports keyed combatants it has no hook for once it holds any.
     * Call it after {@code AetherCompat.init()}, which is what decides whether the mod is here.
     */
    public static void register() {
        if (registered) return;
        registered = true;

        AIRegistry.registerBoss(AI_KEY, SliderAI::new);
        // Both are claims about slide(): a straight lane, and a throw when it lands.
        MobTraits.declare(AI_KEY, MobTraits.BERZERKER, MobTraits.FORCEFUL);
        CrafticsAPI.registerCustomAction(AWAKEN, SliderAI::awaken);

        if (!AetherCompat.isLoaded()) return;
        CrafticsAPI.registerSpawnCustomizer(AI_KEY, SliderAI::dress);
    }

    // ================================================================
    // The turn
    // ================================================================

    /** Two thirds of its health gone or less left: where it turns red and gets its extra runs. */
    @Override
    protected boolean reachedPhaseTwo(CombatEntity self) {
        return phase(self.getCurrentHp(), self.getMaxHp()) >= 2;
    }

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        // The Aether draws its cracked, red-eyed face off the entity's own health, which an
        // arena never touches. A quarter is the highest value that still reads as critical.
        MobEntity mob = self.getMobEntity();
        if (mob != null && isAetherSlider(self)) mob.setHealth(mob.getMaxHealth() / 4.0f);
    }

    /**
     * It never fills a telegraph turn by walking up and swinging, which is what the default
     * does for a late-biome boss.
     */
    @Override
    public EnemyAction getChargingAdvanceAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        return new EnemyAction.Idle();
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        holdGuard(self);

        if (!awake) {
            if (!disturbed(self)) return still(self, arena);
            awake = true;
            return new EnemyAction.CustomAction(AWAKEN);
        }

        // Something shoved it since it took aim, so the paint is no longer where it would go.
        // It never slides down a lane nobody was shown: it builds and takes aim again.
        if (sliding && !self.getGridPos().equals(aimedFrom)) sliding = false;

        if (sliding) {
            sliding = false;
            EnemyAction slid = slide(self, arena, playerPos);
            if (slid != null) return slid;
            // Something it does not crush has come to stand against its face, the way it was
            // aimed. It does not wait for it to leave: it takes aim again, some other way.
        }
        EnemyAction built = build(self, arena, playerPos);
        sliding = aimed != null;
        return built;
    }

    /**
     * Raised at spawn by {@link #dress}, and again here every turn: a fight started from a
     * datapack on a server without the Aether has no spawn hook, and nothing else may drop it.
     */
    private static void holdGuard(CombatEntity self) {
        self.setPickaxeVulnerable(true);
        self.setDamageImmune(true, PICKAXE_HINT);
    }

    /** Lost health counts as well as the flag, which a turn it was stunned through never sees. */
    private static boolean disturbed(CombatEntity self) {
        return self.wasDamagedSinceLastTurn() || self.getCurrentHp() < self.getMaxHp();
    }

    /**
     * A strike, reported by whoever landed it. It no longer counts them: nothing a strike does
     * shortens a slide now. Kept so that the item path that calls it still has it to call.
     */
    public void notifyStruck() {
    }

    /** Which third of its health it is in: 1 above two thirds, 2 from there down, 3 at a third or less. */
    static int phase(int hp, int maxHp) {
        if (hp * 3 <= maxHp) return 3;
        if (hp * 3 <= maxHp * 2) return 2;
        return 1;
    }

    private static int phase(CombatEntity self) {
        return phase(self.getCurrentHp(), self.getMaxHp());
    }

    /**
     * Runs to a slide in each phase. In the first that is one, and {@link #follows} gives it a
     * second when a block stopped it.
     *
     * <p>Wherever a phase is taken to say how many runs a slide has, a slide it is holding
     * back can be said instead, as {@link #exactly}: that many runs and none to be earned.
     */
    static int legs(int phase) {
        if (phase < 0) return -phase;
        return phase >= 3 ? 5 : phase == 2 ? 3 : 1;
    }

    /** A slide of just {@code runs} runs, to give wherever a phase is asked for. See {@link #legs}. */
    static int exactly(int runs) {
        return -Math.max(1, runs);
    }

    /**
     * Bricks it may raise on a build turn in each phase. One of them is kept for where the
     * slide ends, see {@link #stopBrick}, and the rest are for getting there.
     */
    static int bricksFor(int phase) {
        return Math.max(1, Math.min(3, phase)) + 1;
    }

    /** What its passing does to the tiles beside its lane: half a crush, and never nothing. */
    static int wakeDamage(int attackPower) {
        return Math.max(1, attackPower / 2);
    }

    /** Sentries it may have standing at once in each phase. */
    static int sentriesFor(int phase) {
        return Math.max(1, Math.min(3, phase));
    }

    /** What a sentry it called up does when it goes off: two thirds of a crush, and never nothing. */
    static int sentryBlast(int attackPower) {
        return Math.max(1, attackPower * 2 / 3);
    }

    // ================================================================
    // The build turn
    // ================================================================

    /**
     * Raise what bricks are worth raising, then paint the slide to come with them counted.
     * With nothing worth building it still takes aim.
     */
    private EnemyAction build(CombatEntity self, GridArena arena, GridPos target) {
        GridPos from = self.getGridPos();
        int phase = phase(self);
        Scene floor = Scene.atBuild(arena, self, target);
        List<GridPos> bricks = bricks(floor, from, phase);
        int wanted = bricks.size();
        int runs = phase;
        // A wall that is neither the stone of the room nor a brick on a timer is one
        // somebody put down, and a player behind one is there by choice.
        // One with turns left is a brick of its own: up for the slide if it has more than
        // one, and gone before it if this is its last.
        boolean[] putDown = new boolean[floor.width * floor.height];
        boolean[] standing = new boolean[putDown.length];
        List<GridPos> crumbling = new ArrayList<>();
        for (int x = 0; x < floor.width; x++) {
            for (int z = 0; z < floor.height; z++) {
                GridTile tile = arena.getTile(x, z);
                if (tile == null) continue;
                boolean wall = tile.getType() == TileType.OBSTACLE && !tile.isPermanent();
                putDown[x + z * floor.width] = wall && tile.getTurnsRemaining() <= 0;
                standing[x + z * floor.width] = wall && tile.getTurnsRemaining() > 1;
                if (standsOnTheFloor(tile.getType()) && tile.getTurnsRemaining() == 1) crumbling.add(new GridPos(x, z));
            }
        }

        // It always leaves a way out. What it would like to do is tried first, and while
        // that leaves anyone with no ground in reach to stand clear on, it holds back: a
        // brick fewer, the last chosen first, and with none left a run fewer, down to one.
        // What it settles on is what it paints, and what the slide then does.
        Telegraph shown;
        Scene scene;
        boolean[] bricksUp;
        while (true) {
            // The bricks are not in the grid until the engine has raised them.
            scene = floor.copy();
            for (GridPos brick : bricks) scene.raise(brick);
            Route route = route(scene, from, runs, null);
            aimed = route.legs().isEmpty() ? null : route.legs().get(0).way();
            shown = aimed == null ? null : telegraph(arena, self, scene, from, runs, aimed);
            bricksUp = standing.clone();
            for (GridPos brick : bricks) bricksUp[scene.index(brick)] = true;
            if (shown == null || scene.leavesAWayOut(from, shown.tiles(), putDown, bricksUp)) break;
            if (!bricks.isEmpty()) bricks = bricks.subList(0, bricks.size() - 1);
            else if (runs == 1) runs = exactly(1);
            else if (legs(runs) > 1) runs = exactly(legs(runs) - 1);
            else break;
        }

        // With its bricks, a sentry. It comes up beside one, out of the way of the slide just
        // aimed, and the paint is worked out again with it standing there: it is one more
        // thing a run can hit, and one that goes off. If that leaves anyone no way out, the
        // sentry is what it gives up.
        List<GridPos> posts = List.of();
        if (sentries && aimed != null && !bricks.isEmpty()
                && sentriesStanding(arena, self) < sentriesFor(phase)) {
            GridPos post = sentryPost(scene, from, runs, aimed, bricks);
            if (post != null) {
                Scene guarded = scene.withBombAt(post, SentryAI.BLAST_RADIUS);
                Telegraph withIt = telegraph(arena, self, guarded, from, runs, aimed);
                if (guarded.leavesAWayOut(from, withIt.tiles(), putDown, bricksUp)) {
                    posts = List.of(post);
                    shown = withIt;
                }
            }
        }
        aimedFrom = from;
        aimedRuns = runs;
        aimedTarget = target;
        aboutToCrumble = crumbling;
        notYetRaised = new ArrayList<>(bricks);
        paintNow = null;
        paintFor = null;
        dangerNow = null;
        paintSelf = self;
        paintArena = arena;
        heldBricks = wanted - bricks.size();
        heldRuns = legs(phase) - legs(runs) + (phase == 1 && runs != 1 ? 1 : 0);
        pendingWarning = null;
        if (aimed != null) {
            // All of that was reasoned on the whole of the danger. What goes on the floor is
            // its first run and no more, and it is read off the board from here on.
            Scene asRaised = posts.isEmpty() ? scene : scene.withBombAt(posts.get(0), SentryAI.BLAST_RADIUS);
            Telegraph seen = shown(arena, self, asRaised, from, aimed);
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.DIRECTIONAL,
                seen.tiles(), HELD,
                new EnemyAction.Idle(), LANE_COLOR, aimed[0], aimed[1]).withArrows(seen.arrows())
                .withLive(() -> livePaint(self, arena));
        }
        if (bricks.isEmpty()) return still(self, arena);
        EnemyAction raise = new EnemyAction.CreateTerrain(bricks, TileType.OBSTACLE, BRICK_TURNS);
        if (posts.isEmpty()) return raise;
        // The bricks first, so a sentry is never called onto a tile a brick is about to take.
        return new EnemyAction.CompositeAction(List.of(raise, new EnemyAction.SummonMinions(
            SENTRY_TYPE, posts.size(), posts, SENTRY_HP, sentryBlast(self.getAttackPower()), 0)));
    }

    /**
     * Where a sentry comes up: on plain floor against one of the bricks just chosen, on a tile
     * no run of the slide just aimed so much as looks at, and not within
     * {@link #SENTRY_CLEARANCE} tiles of a player. Of those, the one furthest from every
     * player, and the first found if they tie. Null when there is nowhere.
     *
     * <p>Out of the slide's way, because a sentry is a body and a body stops a run: one put
     * down in a lane would undo the slide the bricks were chosen for. Away from the players,
     * because a sentry beside one goes off on its first turn, and nobody was warned.
     */
    static GridPos sentryPost(Scene scene, GridPos from, int phase, int[] first, List<GridPos> bricks) {
        boolean[] looked = new boolean[scene.width * scene.height];
        scene.looked = looked;
        route(scene, from, phase, first);
        scene.looked = null;

        GridPos best = null;
        int bestClear = -1;
        for (GridPos brick : bricks) {
            for (int[] way : DIRECTIONS) {
                GridPos tile = new GridPos(brick.x() + way[0], brick.z() + way[1]);
                if (!scene.inside(tile)) continue;
                int at = scene.index(tile);
                if (!scene.plain[at] || looked[at]) continue;
                int clear = scene.nearestPlayer(tile);
                if (clear < SENTRY_CLEARANCE || clear <= bestClear) continue;
                bestClear = clear;
                best = tile;
            }
        }
        return best;
    }

    /** How many sentries are standing in the arena: anything that goes off when it is run into. */
    static int sentriesStanding(GridArena arena, CombatEntity self) {
        Set<CombatEntity> counted = new HashSet<>();
        for (CombatEntity other : arena.getOccupants().values()) {
            if (other != null && other != self && other.isAlive() && blastOf(other) >= 0) counted.add(other);
        }
        return counted.size();
    }

    /**
     * How far the blast reaches when {@code other} is run into, or -1 when it only takes the
     * blow. Asked of its brain, so anything that says it bursts counts, not only a sentry.
     */
    static int blastOf(CombatEntity other) {
        if (other.isAlly() || other.isBackgroundBoss()) return -1;
        EnemyAI brain = other.getAiInstance();
        if (brain == null && other.getAiKey() != null) brain = AIRegistry.get(other.getAiKey());
        EnemyAction.Explode burst = brain == null ? null : brain.whenRammed(other);
        return burst == null ? -1 : Math.max(0, burst.radius());
    }

    /**
     * What is shown of the slide it has aimed, against the board as it stands now: its
     * first run, see {@link #shown}.
     *
     * <p>The paint is read through this every time, so it follows the board: a brick mined,
     * a block set down, someone moved, and the next look at it shows the first run that would
     * now be made. The way its first run goes and the runs it has stay as they were fixed
     * when it took aim. It is asked for once for the tiles and once more for every arrow,
     * so the answer is kept, and worked out again only when the board is not the one it was
     * worked out for.
     */
    private BossWarning.Live livePaint(CombatEntity self, GridArena arena) {
        GridPos from = self.getGridPos();
        if (aimed == null || !from.equals(aimedFrom)) return new BossWarning.Live(List.of(), Map.of());
        Scene scene = Scene.of(arena, self, aimedTarget);
        if (!scene.preyAt(aimedTarget)) scene = Scene.of(arena, self, arena.getPlayerGridPos());
        for (GridPos tile : aboutToCrumble) {
            GridTile brick = arena.getTile(tile);
            if (brick != null && standsOnTheFloor(brick.getType()) && brick.getTurnsRemaining() == 1) scene.lower(tile);
        }
        // Between asking for its bricks and the engine raising them they are not in the grid.
        // Once they have been seen there, the grid is the truth: one that is gone was mined.
        if (notYetRaised != null) {
            boolean raised = true;
            for (GridPos brick : notYetRaised) raised &= scene.isBlock(brick);
            if (raised) notYetRaised = null;
            else for (GridPos brick : notYetRaised) scene.raise(brick);
        }
        if (paintNow == null || !scene.sameAs(paintFor)) {
            Telegraph seen = shown(arena, self, scene, from, aimed);
            paintNow = new BossWarning.Live(seen.tiles(), seen.arrows());
            paintFor = scene;
            dangerNow = null;
        }
        return paintNow;
    }

    /**
     * The whole danger of the slide it has aimed, against the board as it stands now: every
     * tile it would crush, shake or catch in a blast someone standing there, by any of its
     * runs. Far more than it shows, and what it reasoned with when it built. Empty when it
     * has no slide aimed.
     */
    Telegraph danger() {
        if (aimed == null || paintSelf == null || !paintSelf.getGridPos().equals(aimedFrom)) {
            return new Telegraph(List.of(), Map.of());
        }
        livePaint(paintSelf, paintArena);
        if (dangerNow == null) dangerNow = telegraph(paintArena, paintSelf, paintFor, aimedFrom, aimedRuns, aimed);
        return dangerNow;
    }

    /**
     * The bricks worth raising for the slide that follows, best first, at most as many as its
     * phase allows and often fewer.
     *
     * <p>One at a time, each chosen with the ones before it counted, by trying the slide it
     * would then make. All but one are for the slide itself, and such a brick is worth raising
     * when the slide comes out better for it: it ends in a crush where it did not, or gets
     * there in fewer runs, or failing a crush leaves it nearer its target. When nothing is
     * better for a brick, none is raised. What is left, the one kept back and any of the
     * others it found no use for, goes on where the slide ends: see {@link #stopBrick}.
     */
    static List<GridPos> bricks(Scene scene, GridPos from, int phase) {
        Scene trial = scene.copy();
        List<GridPos> raised = new ArrayList<>();
        for (int i = 0; i < bricksFor(phase) - 1; i++) {
            GridPos brick = brick(trial, from, phase);
            if (brick == null) break;
            trial.raise(brick);
            raised.add(brick);
        }
        while (raised.size() < bricksFor(phase)) {
            GridPos brick = stopBrick(trial, from, phase);
            if (brick == null) break;
            trial.raise(brick);
            raised.add(brick);
        }
        return raised;
    }

    /** The one brick that most improves the slide from here, or null when none does. */
    private static GridPos brick(Scene scene, GridPos from, int phase) {
        // A brick changes nothing unless it stands in a row some run looked into, taken or
        // only weighed up. So those are the only tiles worth trying.
        boolean[] looked = new boolean[scene.width * scene.height];
        scene.looked = looked;
        Route best = route(scene, from, phase, null);
        scene.looked = null;

        GridPos choice = null;
        for (int at = 0; at < looked.length; at++) {
            if (!looked[at] || !scene.plain[at]) continue;
            GridPos tile = scene.tile(at);
            if (scene.seals(tile)) continue;
            scene.raise(tile);
            Route tried = route(scene, from, phase, null);
            scene.unraise(tile);
            if (better(scene, from, tried, best)) {
                best = tried;
                choice = tile;
            }
        }
        return choice;
    }

    /**
     * Whether slide {@code a} is the better one to make: it ends in a crush and the other does
     * not; or both do and it takes fewer runs; or neither does and it stops nearer its target.
     * With nothing in any of that, the one that leaves it out on the floor is better than the
     * one that leaves it against the edge, where half the ways it could go next are shut.
     */
    private static boolean better(Scene scene, GridPos from, Route a, Route b) {
        boolean crushes = a.crushed() != null;
        if (crushes != (b.crushed() != null)) return crushes;
        int worth = crushes ? a.legs().size() : scene.distance(a.end(from));
        int other = crushes ? b.legs().size() : scene.distance(b.end(from));
        if (worth != other) return worth < other;
        return !scene.atEdge(a.end(from)) && scene.atEdge(b.end(from));
    }

    /**
     * A brick to end the slide out on the floor, or null when it needs none or there is
     * nowhere for one.
     *
     * <p>The last run of a slide is the one that leaves it where it will stand. With nothing
     * in its way that is against the edge of the arena, which is the worst place for it: half
     * the ways it could go next are shut. So when its last run would reach the edge, with
     * nobody standing in its way, a brick is put in that run's path to stop it as near its
     * target as a stop can be. Where the run ends in a crush that is the row behind whoever
     * it crushes, or the first one after that with room: never short of them, which would
     * save them. Where it does not, it is wherever along the run it comes nearest.
     *
     * <p>The brick must leave every run before the last as it was, and a crush still a crush.
     * In its first phase a brick that stops its only run earns it a second, and that one may
     * want stopping in its turn, which is why this is asked for more than once.
     */
    private static GridPos stopBrick(Scene scene, GridPos from, int phase) {
        Route route = route(scene, from, phase, null);
        List<Leg> legs = route.legs();
        if (legs.isEmpty()) return null;
        Scene then = scene.copy();
        List<Leg> before = legs.subList(0, legs.size() - 1);
        for (Leg leg : before) {
            for (GridPos tile : leg.smashed()) then.lower(tile);
        }
        Leg last = legs.get(legs.size() - 1);
        Leg unhindered = then.run(last.from(), last.dx(), last.dz(), true);
        if (unhindered.stop() != Stop.EDGE) return null;

        // Stopped after each number of tiles it could be, nearest its target first, and at
        // the same distance the sooner. Never further from them than the edge would leave
        // it: someone with their back to the wall is best met at the wall.
        List<Integer> stops = new ArrayList<>();
        int atTheEdge = scene.distance(unhindered.end());
        for (int after = last.crushed() != null ? last.steps() + 1 : 1; after < unhindered.steps(); after++) {
            if (last.crushed() != null || scene.distance(last.at(after)) <= atTheEdge) stops.add(after);
        }
        stops.sort((a, b) -> Integer.compare(scene.distance(last.at(a)), scene.distance(last.at(b))));
        for (int after : stops) {
            List<GridPos> row = new ArrayList<>(then.row(last.at(after), last.dx(), last.dz()));
            row.sort((a, b) -> Boolean.compare(scene.besidePlayer(a), scene.besidePlayer(b)));
            for (GridPos tile : row) {
                if (!scene.inside(tile) || !scene.plain[scene.index(tile)] || scene.seals(tile)) continue;
                scene.raise(tile);
                List<Leg> tried = route(scene, from, phase, null).legs();
                scene.unraise(tile);
                boolean kept = last.crushed() != null
                    ? tried.equals(legs)
                    : tried.size() >= legs.size() && tried.subList(0, before.size()).equals(before)
                        && tried.get(before.size()).stop() == Stop.BLOCK && tried.get(before.size()).steps() == after;
                if (kept) return tile;
            }
        }
        return null;
    }

    // ================================================================
    // The slide turn
    // ================================================================

    /**
     * The slide it took aim for, or null when its first run cannot be made the way it was
     * painted: then nothing has been done, and the turn is still to be used.
     */
    private EnemyAction slide(CombatEntity self, GridArena arena, GridPos target) {
        int[] first = aimed;
        aimed = null;
        aimedFrom = null;
        pendingWarning = null;
        paintNow = null;
        paintFor = null;
        dangerNow = null;
        notYetRaised = null;

        // Worked out now, from where everyone is standing now. The first run goes the way it
        // was painted, as far as things let it. Each run after that is aimed afresh from where
        // the one before it stopped. It has the runs it had when it painted, and no more for
        // having been hit since.
        GridPos from = self.getGridPos();
        Scene scene = Scene.of(arena, self, target);
        Route route = route(scene, from, aimedRuns, first);
        List<Leg> legs = route.legs();
        if (legs.isEmpty()) return null;

        // The ground that has to be floor before the path is walked: the holes each run
        // crosses, and every block it broke. The engine makes the tile floor. What stands on
        // it is broken here.
        //
        // What a run hits, it hits when it gets there. Each run ends so many steps into the
        // one path, and that is when its blocks are seen to break and whoever was in its way
        // is struck: a run that goes nowhere hits at once, and the rest wait for it to arrive.
        List<GridPos> path = new ArrayList<>();
        Set<GridPos> rough = new LinkedHashSet<>();
        List<EnemyAction> rams = new ArrayList<>();
        releaseBreaks(self, false);
        for (Leg leg : legs) {
            path.addAll(leg.path());
            for (GridPos tile : leg.lane().tiles(scene.sizeX, scene.sizeZ)) {
                if (scene.isHole(tile)) rough.add(tile);
            }
            rough.addAll(leg.smashed());
            smash(self, arena, leg.smashed(), path.size());
            if (!leg.rammed().isEmpty()) {
                rams.add(new EnemyAction.Ram(leg.rammed(), self.getAttackPower(), path.size()));
            }
        }
        if (!path.isEmpty()) setOff(self);

        GridPos victim = route.crushed();
        EnemyAction run = null;
        if (victim != null) {
            // The engine strikes on arrival, at the end of the whole path, and throws the
            // victim clear. It is told which tile the strike is for, or with a party it would
            // pick whoever is nearest. With nothing walked the same strike is asked for bare.
            self.setPendingStrikeTile(victim);
            int damage = self.getAttackPower();
            run = path.isEmpty()
                ? new EnemyAction.AttackWithKnockback(damage, KNOCKBACK_TILES)
                : new EnemyAction.MoveAndAttackWithKnockback(path, damage, KNOCKBACK_TILES);
        } else if (!path.isEmpty()) {
            // All the runs are one move, with a corner in it wherever it turned.
            run = new EnemyAction.Move(path);
        }

        // In the order it has to happen. The ground first, which costs no time, so that it is
        // floor before the path is checked. Then the run, the one part that takes time: the
        // engine gives the turn to the first such action in a bundle and drops any other. Then
        // whoever it runs into, each held by the engine until the run has got that far. Then
        // what its passing shakes, which is the first run's doing alone: the rest crush or do
        // nothing. Nobody is hit twice. The row it ran into is the crush's ground, and whoever
        // this turn crushes, by any run, is left out of the wake.
        List<EnemyAction> turn = new ArrayList<>();
        if (!rough.isEmpty()) {
            turn.add(new EnemyAction.CreateTerrain(new ArrayList<>(rough), TileType.NORMAL, 0));
        }
        if (run != null) turn.add(run);
        turn.addAll(rams);
        Leg one = legs.get(0);
        List<GridPos> shaken = wake(arena, self, scene.run(from, one.dx(), one.dz(), true).lane(), one.steps());
        if (one.stop() == Stop.BLOCK) shaken.removeAll(scene.row(one.end(), one.dx(), one.dz()));
        shaken.remove(victim);
        if (!shaken.isEmpty()) {
            turn.add(new EnemyAction.TileAreaAttack(shaken, one.end(), wakeDamage(self.getAttackPower()), null));
        }
        // A run that was worth making moved it, broke something, crushed someone or ran into
        // someone, so there is always at least the one part.
        return turn.size() == 1 ? turn.get(0) : new EnemyAction.CompositeAction(List.copyOf(turn));
    }

    /** One block of something it broke: where it was, and what it was. */
    private record Shard(BlockPos at, BlockState was) {}

    /** A broken block still being shown, and how many steps into the slide it is seen to break. */
    private record Break(int afterSteps, List<Shard> shards) {}

    /**
     * The part of breaking {@code blocks} that the engine's terrain change leaves undone, as
     * the Aether's Slider breaks whatever it runs into.
     *
     * <p>No tile is retyped here. That is the engine's, asked for at the head of the slide:
     * it makes the tile floor and lays the floor block again. What it does not touch is
     * anything standing on that floor, so a block's own blocks are cleared here, and a wall
     * somebody put down is handed back to the arena, which keeps its own record of those. With
     * no world behind the entity there is nothing standing anywhere, and nothing to do.
     *
     * <p>The blocks are gone from the world at once, every one, because the path is checked
     * against the grid before a step of it is taken, and because a block left standing for
     * the run to reach is a block left behind for good if the run never gets there. What
     * waits is the sight of it. A block the run has yet to reach goes on being shown to
     * everyone watching, and is seen to break when the run arrives: see {@link #stepLanded}.
     *
     * @param afterSteps how many steps into the slide the run that broke them ends. At 0 it
     *                   is against them already, and they break where they stand
     */
    private void smash(CombatEntity self, GridArena arena, Collection<GridPos> blocks, int afterSteps) {
        MobEntity mob = self.getMobEntity();
        if (mob == null || !(mob.getWorld() instanceof ServerWorld world)) return;
        for (GridPos tile : blocks) {
            List<Shard> shards = shatter(arena, world, tile);
            if (shards.isEmpty()) continue;
            if (afterSteps <= 0) {
                crumble(world, shards);
                continue;
            }
            GhostBlocks ghosts = GhostBlocks.of(world);
            for (Shard shard : shards) ghosts.showAfterChange(world, shard.at(), shard.was(), SHOWN_UNTIL_HIT);
            breaksToCome.add(new Break(afterSteps, shards));
        }
    }

    /** The two kinds of ground that are a block in the space a body stands in, and not the floor. */
    private static boolean standsOnTheFloor(TileType type) {
        return type == TileType.OBSTACLE || type == TileType.RUBBLE;
    }

    /**
     * Clear what stands on one tile, in the world, and say what was there, bottom first. What
     * the tile is, is the engine's to change.
     */
    private static List<Shard> shatter(GridArena arena, ServerWorld world, GridPos tile) {
        BlockPos block = arena.gridToBlockPos(tile);
        List<Shard> shards = new ArrayList<>();
        if (arena.isVfxObstacle(tile)) {
            // A wall somebody put down. The arena remembers what was under it and puts that
            // back, block and all.
            BlockState was = world.getBlockState(block);
            arena.clearVfxObstacle(world, tile);
            if (!was.isAir()) shards.add(new Shard(block, was));
            return shards;
        }
        // It stands on the floor and may stand tall. A pillar comes out to its top, or the
        // stump left hanging would be read back as stone the next time the arena is scanned.
        for (int dy = 0; dy < TALLEST_BLOCK; dy++) {
            BlockPos part = block.up(dy);
            BlockState was = world.getBlockState(part);
            if (dy > 0 && was.isAir()) break;
            world.setBlockState(part, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
            if (!was.isAir()) shards.add(new Shard(part, was));
        }
        return shards;
    }

    /** The sight and sound of one block breaking: its own dust, from where its foot was. */
    private static void crumble(ServerWorld world, List<Shard> shards) {
        Shard foot = shards.get(0);
        BlockPos block = foot.at();
        world.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, foot.was()),
            block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5, 20, 0.3, 0.4, 0.3, 0.2);
        world.playSound(null, block, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.HOSTILE, 1.0f, 0.7f);
    }

    /**
     * Stop showing every broken block the slide has now reached, or all of them when the
     * move is over.
     *
     * @param reached how many steps of the slide have landed, or a negative number for all
     * @param seen    whether they are seen to break. Not when the run never got to them: then
     *                they are only gone, which they have been all along
     */
    private void releaseBreaks(CombatEntity self, int reached, boolean seen) {
        if (breaksToCome.isEmpty()) return;
        MobEntity mob = self.getMobEntity();
        ServerWorld world = mob != null && mob.getWorld() instanceof ServerWorld server ? server : null;
        var waiting = breaksToCome.iterator();
        while (waiting.hasNext()) {
            Break broken = waiting.next();
            if (reached >= 0 && broken.afterSteps() > reached) continue;
            waiting.remove();
            if (world == null) continue;
            GhostBlocks ghosts = GhostBlocks.of(world);
            for (Shard shard : broken.shards()) ghosts.release(world, shard.at());
            if (seen) crumble(world, broken.shards());
        }
    }

    private void releaseBreaks(CombatEntity self, boolean seen) {
        releaseBreaks(self, -1, seen);
    }

    // ================================================================
    // How it moves
    // ================================================================

    /**
     * How far into its own straight run step {@code index} of {@code path} is, counting from
     * 0. A slide is one path with a corner wherever a run ended and the next began, so a run
     * is every step that goes the same way as the one before it.
     *
     * @param start where it stood before the first step
     */
    static int stepOfRun(GridPos start, List<GridPos> path, int index) {
        int into = 0;
        while (index - into > 0 && sameWay(start, path, index - into, index - into - 1)) into++;
        return into;
    }

    /** Whether step {@code index} of {@code path} is the last of its run: the next turns, or there is none. */
    static boolean endsRun(GridPos start, List<GridPos> path, int index) {
        return index + 1 >= path.size() || !sameWay(start, path, index + 1, index);
    }

    private static boolean sameWay(GridPos start, List<GridPos> path, int a, int b) {
        return wayOf(start, path, a)[0] == wayOf(start, path, b)[0]
            && wayOf(start, path, a)[1] == wayOf(start, path, b)[1];
    }

    private static int[] wayOf(GridPos start, List<GridPos> path, int index) {
        GridPos to = path.get(index);
        GridPos from = index == 0 ? start : path.get(index - 1);
        return new int[]{Integer.signum(to.x() - from.x()), Integer.signum(to.z() - from.z())};
    }

    /**
     * Ticks for one step of a run: slow off the mark, then faster with every tile, down to a
     * tile a tick. Every run starts again from slow, which is what makes a corner read as a
     * stop and a fresh shove.
     *
     * @param into   how far into its run the step is, from 0
     * @param enraged red, it is a notch quicker off the mark
     */
    static int runTicks(int into, boolean enraged) {
        int at = Math.max(0, into) + (enraged ? 1 : 0);
        return at < RUN_TICKS.length ? RUN_TICKS[at] : 1;
    }

    /** How hard the camera is shaken when a run of {@code length} tiles ends. A long run hits harder. */
    static float impactShake(int length) {
        return Math.min(1.0f, 0.25f + 0.1f * Math.max(0, length));
    }

    @Override
    public int stepTicks(CombatEntity self, GridPos start, List<GridPos> path, int index, int usual) {
        return runTicks(stepOfRun(start, path, index), self.isEnraged());
    }

    /**
     * A step of the slide has landed. Whatever this many steps brought it up against is seen
     * to break now, and where a run ends here it hits: dust off its leading face, the Aether's
     * own sound for it, and the camera shaken harder the further it came.
     */
    @Override
    public void stepLanded(CombatEntity self, GridArena arena, GridPos start, List<GridPos> path, int index) {
        releaseBreaks(self, index + 1, true);
        if (!endsRun(start, path, index)) return;
        MobEntity mob = self.getMobEntity();
        if (mob == null || !(mob.getWorld() instanceof ServerWorld world)) return;

        int[] way = wayOf(start, path, index);
        int length = stepOfRun(start, path, index) + 1;
        BlockPos corner = arena.gridToBlockPos(self.getGridPos());
        double x = corner.getX() + self.getSizeX() / 2.0 + way[0] * self.getSizeX() / 2.0;
        double z = corner.getZ() + self.getSizeZ() / 2.0 + way[1] * self.getSizeZ() / 2.0;
        double y = corner.getY() + 0.6;
        // Spread along the face it hit with, not out in front of it.
        world.spawnParticles(ParticleTypes.POOF, x, y, z, 8 + 3 * length,
            way[0] != 0 ? 0.15 : 0.9, 0.5, way[1] != 0 ? 0.15 : 0.9, 0.05);
        world.spawnParticles(ParticleTypes.CRIT, x, y + 0.3, z, 6 + 2 * length, 0.4, 0.5, 0.4, 0.3);
        world.playSound(null, corner, sound("entity.slider.collide", SoundEvents.BLOCK_ANVIL_LAND),
            SoundCategory.HOSTILE, Math.min(2.0f, 0.8f + 0.15f * length), 0.75f);
        Vfx.play(world, VfxDescriptor.builder().phase(0).shake(impactShake(length), 6).build(),
            VfxContext.ofBlocks(corner, corner, 0f, arena));
        // A corner: it is off again at once.
        if (index + 1 < path.size()) setOff(self);
    }

    /** However the move ended, nothing it broke goes on being shown. */
    @Override
    public void moveEnded(CombatEntity self, GridArena arena, int stepsLanded) {
        releaseBreaks(self, false);
    }

    /** The sound of it starting to move. */
    private static void setOff(CombatEntity self) {
        MobEntity mob = self.getMobEntity();
        if (mob == null || !(mob.getWorld() instanceof ServerWorld world)) return;
        world.playSound(null, mob.getBlockPos(), sound("entity.slider.move", SoundEvents.BLOCK_GRINDSTONE_USE),
            SoundCategory.HOSTILE, 1.4f, 0.8f);
    }

    /**
     * What a turn with nothing to do returns: nothing, unless there is liquid to account for.
     */
    private static EnemyAction still(CombatEntity self, GridArena arena) {
        List<GridPos> gone = boiledAway(self, arena);
        if (gone.isEmpty()) return new EnemyAction.Idle();
        return new EnemyAction.CreateTerrain(gone, TileType.NORMAL, 0);
    }

    /**
     * Water and lava within a tile of it, which the real entity has already deleted.
     *
     * <p>Every Aether boss evaporates liquid around itself on every tick, asleep or awake, and
     * nothing an arena sets stops that (only the mob griefing rule does). A bucket poured here
     * replaces the floor block with the liquid, so what is left is a hole under a tile the
     * grid still calls water. Turning those tiles back into floor puts the block back and
     * makes the grid agree, on the first turn it has nothing else to do.
     */
    static List<GridPos> boiledAway(CombatEntity self, GridArena arena) {
        GridPos at = self.getGridPos();
        List<GridPos> gone = new ArrayList<>();
        for (int x = at.x() - 1; x <= at.x() + self.getSizeX(); x++) {
            for (int z = at.z() - 1; z <= at.z() + self.getSizeZ(); z++) {
                GridTile tile = arena.getTile(x, z);
                if (tile == null) continue;
                if (tile.getType() == TileType.WATER || tile.getType() == TileType.LAVA) {
                    gone.add(new GridPos(x, z));
                }
            }
        }
        return gone;
    }

    // ================================================================
    // A slide, worked out
    // ================================================================

    /** What ends a run: a block it ran into, somebody standing there, or the arena. */
    enum Stop { BLOCK, BODY, EDGE }

    /**
     * One straight run of a slide: where from, which way, how far it got, what stopped it, the
     * blocks it ran into and broke, the tile of whoever it crushed, or null, and the tiles of
     * anyone else it ran into, who is struck where they stand.
     */
    record Leg(GridPos from, int dx, int dz, int steps, Stop stop, List<GridPos> smashed, GridPos crushed,
               List<GridPos> rammed) {

        /** A run that ran into nobody but whoever it crushed. */
        Leg(GridPos from, int dx, int dz, int steps, Stop stop, List<GridPos> smashed, GridPos crushed) {
            this(from, dx, dz, steps, stop, smashed, crushed, List.of());
        }

        int[] way() {
            return new int[]{dx, dz};
        }

        /** Where the body's corner stands after {@code count} tiles of it. */
        GridPos at(int count) {
            return new GridPos(from.x() + dx * count, from.z() + dz * count);
        }

        GridPos end() {
            return at(steps);
        }

        Lane lane() {
            return new Lane(from, dx, dz, steps);
        }

        List<GridPos> path() {
            return lane().path(steps);
        }

        /**
         * Whether it is a run worth making. One of no length is, when it broke something,
         * crushed someone or ran into someone where it stood. One that goes nowhere and does
         * nothing is not.
         */
        boolean taken() {
            return steps > 0 || !smashed.isEmpty() || crushed != null || !rammed.isEmpty();
        }
    }

    /** A whole slide: its runs, in the order it makes them. */
    record Route(List<Leg> legs) {

        /** Its last run, or null when it has none. */
        Leg last() {
            return legs.isEmpty() ? null : legs.get(legs.size() - 1);
        }

        /** The tile of whoever it crushes, which only its last run can, or null. */
        GridPos crushed() {
            return legs.isEmpty() ? null : legs.get(legs.size() - 1).crushed();
        }

        /** Where it leaves the body's corner, having started it at {@code from}. */
        GridPos end(GridPos from) {
            return legs.isEmpty() ? from : legs.get(legs.size() - 1).end();
        }
    }

    /**
     * Whether another run may follow run number {@code index} of a slide, counting from 0.
     * Never after a crush. In the first phase, only after a first run that a block stopped.
     * After that, as long as there are runs left.
     */
    static boolean follows(int phase, int index, Leg leg) {
        if (leg.crushed() != null) return false;
        if (phase == 1) return index == 0 && leg.stop() == Stop.BLOCK;
        return index + 1 < legs(phase);
    }

    /**
     * The slide it would make from {@code from} with things as {@code scene} has them.
     *
     * @param first the way its first run must go, because that is what was painted, or null
     *              to aim that one like the rest
     */
    static Route route(Scene scene, GridPos from, int phase, int[] first) {
        Scene live = scene.copy();
        live.looked = scene.looked;
        List<Leg> legs = new ArrayList<>();
        GridPos at = from;
        int[] cameBy = null;
        boolean moved = false;
        while (legs.isEmpty() || follows(phase, legs.size() - 1, legs.get(legs.size() - 1))) {
            int[] way = legs.isEmpty() && first != null
                ? first : aim(live, at, cameBy, phase, legs.size(), !legs.isEmpty() && !moved);
            if (way == null) break;
            Leg leg = live.run(at, way[0], way[1], false);
            if (!leg.taken()) break;
            legs.add(leg);
            for (GridPos tile : leg.smashed()) live.lower(tile);
            for (GridPos tile : leg.rammed()) live.spend(tile);
            at = leg.end();
            cameBy = way;
            moved |= leg.steps() > 0;
        }
        return new Route(legs);
    }

    /**
     * Which way run number {@code index} of a slide goes from {@code from}, or null when
     * nothing moves it.
     *
     * <p>All four ways are tried, and the first of these that any of them meets decides:
     * <ol>
     *   <li>It crushes someone.</li>
     *   <li>There is a run to come after it, and where this one stops leaves that one a
     *       crush.</li>
     *   <li>It is toward its target, on the axis it is further away on. That is the Aether's
     *       rule, and X only when it is strictly the longer.</li>
     * </ol>
     * A run of no length counts only when it breaks or crushes something. It does not go
     * straight back the way it came unless that is the only way left. Ties go to the way
     * nearest its target, and then in the order of {@link #DIRECTIONS}, so the same position
     * always gives the same answer.
     *
     * @param stuck the slide has had runs already and none of them has moved it. Then under
     *              the third rule a way that moves it comes before one that only breaks what
     *              is against its face, wherever it leads: or a block set against it every
     *              turn, on the side it means to go and the side it would go next, would
     *              hold it where it stands for as long as the blocks lasted
     */
    static int[] aim(Scene scene, GridPos from, int[] cameBy, int phase, int index, boolean stuck) {
        List<int[]> ways = scene.towardTarget(from);
        Leg[] tried = new Leg[ways.size()];
        for (int i = 0; i < tried.length; i++) {
            tried[i] = scene.run(from, ways.get(i)[0], ways.get(i)[1], false);
        }
        for (int i = 0; i < tried.length; i++) {
            if (tried[i].crushed() != null) return ways.get(i);
        }
        for (int i = 0; i < tried.length; i++) {
            if (!tried[i].taken() || back(ways.get(i), cameBy)) continue;
            if (follows(phase, index, tried[i]) && scene.crushOpenAfter(tried[i])) return ways.get(i);
        }
        for (int i = 0; stuck && i < tried.length; i++) {
            if (tried[i].steps() > 0 && !back(ways.get(i), cameBy)) return ways.get(i);
        }
        for (int i = 0; i < tried.length; i++) {
            if (tried[i].taken() && !back(ways.get(i), cameBy)) return ways.get(i);
        }
        for (int i = 0; i < tried.length; i++) {
            if (tried[i].taken()) return ways.get(i);
        }
        return null;
    }

    private static boolean back(int[] way, int[] cameBy) {
        return cameBy != null && way[0] == -cameBy[0] && way[1] == -cameBy[1];
    }

    /** Signed distance from a body covering {@code [at, at + size)} to coordinate {@code to}. */
    private static int gap(int at, int size, int to) {
        if (to < at) return to - at;
        if (to > at + size - 1) return to - (at + size - 1);
        return 0;
    }

    /**
     * The arena as a slide meets it, read once for a decision and cheap to try things on.
     *
     * <p>Every tile is one of four things to a slide: floor it crosses, a hole it crosses and
     * fills, a block that stops it and is broken, or not there at all. Who stands where is
     * read once too: those it crushes, a player or the pet it was sent after, and anyone else,
     * who only stops it. Planning a build tries a great many slides, a brick here and a brick
     * there, so this is flat arrays and not the arena itself.
     */
    static final class Scene {
        private static final byte FLOOR = 0;
        private static final byte HOLE = 1;
        private static final byte BLOCK = 2;
        private static final byte OFF = 3;

        final int width;
        final int height;
        final int sizeX;
        final int sizeZ;
        private final GridPos target;
        private final byte[] ground;
        /** Those it crushes: every player, and the pet it was sent after. */
        private final boolean[] prey;
        /** Anyone else, who stops it and is struck where they stand. */
        private final boolean[] body;
        /** For each of those, how far the blast reaches if they go off when run into, or -1. */
        private final int[] blast;
        /** Those of them this slide has run into already. They still stop it. They are not struck again. */
        private boolean[] spent;
        /** Where a player stands, which is what a brick must not seal in. */
        private final boolean[] player;
        /** Plain floor nobody is standing on and it is not standing on: where a brick can go. */
        private final boolean[] plain;
        /** When set, every tile a run looks into is marked in it. */
        private boolean[] looked;

        private Scene(int width, int height, int sizeX, int sizeZ, GridPos target, byte[] ground,
                      boolean[] prey, boolean[] body, int[] blast, boolean[] player, boolean[] plain) {
            this.width = width;
            this.height = height;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            this.target = target;
            this.ground = ground;
            this.prey = prey;
            this.body = body;
            this.blast = blast;
            this.player = player;
            this.plain = plain;
            this.spent = new boolean[body.length];
        }

        /** Read {@code arena} as {@code self} meets it. {@code target} may be null: then nobody's pet is prey. */
        static Scene of(GridArena arena, CombatEntity self, GridPos target) {
            int width = arena.getWidth();
            int height = arena.getHeight();
            int count = width * height;
            int[] quiet = new int[count];
            java.util.Arrays.fill(quiet, -1);
            Scene scene = new Scene(width, height, self.getSizeX(), self.getSizeZ(), target, new byte[count],
                new boolean[count], new boolean[count], quiet, new boolean[count], new boolean[count]);
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < height; z++) {
                    GridTile tile = arena.getTile(x, z);
                    int at = x + z * width;
                    if (tile == null) scene.ground[at] = OFF;
                    else if (standsOnTheFloor(tile.getType())) scene.ground[at] = BLOCK;
                    else if (!tile.isWalkable()) scene.ground[at] = HOLE;
                    scene.plain[at] = tile != null && tile.getType() == TileType.NORMAL;
                }
            }
            CombatEntity pet = target == null ? null : arena.getOccupant(target);
            if (pet != null && !pet.isAlly()) pet = null;
            for (Map.Entry<GridPos, CombatEntity> standing : arena.getOccupants().entrySet()) {
                int at = scene.index(standing.getKey());
                CombatEntity other = standing.getValue();
                if (at < 0 || other == null) continue;
                scene.plain[at] = false;
                if (other == self || other.isBackgroundBoss()) continue;
                if (other == pet) {
                    scene.prey[at] = true;
                } else {
                    scene.body[at] = true;
                    scene.blast[at] = blastOf(other);
                }
            }
            for (GridPos tile : GridArena.getOccupiedTiles(self)) {
                if (scene.inside(tile)) scene.plain[scene.index(tile)] = false;
            }
            for (GridPos tile : players(arena)) {
                int at = scene.index(tile);
                if (at < 0) continue;
                scene.prey[at] = true;
                scene.player[at] = true;
                scene.plain[at] = false;
            }
            return scene;
        }

        /**
         * {@link #of}, as it will be when the slide comes and not as it is on the turn it
         * builds. The round turns over in between, and a brick on its last turn is gone by
         * then, so it is not counted on.
         */
        static Scene atBuild(GridArena arena, CombatEntity self, GridPos target) {
            Scene scene = of(arena, self, target);
            for (int x = 0; x < scene.width; x++) {
                for (int z = 0; z < scene.height; z++) {
                    GridTile tile = arena.getTile(x, z);
                    if (tile != null && standsOnTheFloor(tile.getType()) && tile.getTurnsRemaining() == 1) {
                        scene.ground[x + z * scene.width] = FLOOR;
                    }
                }
            }
            return scene;
        }

        Scene copy() {
            Scene copy = new Scene(width, height, sizeX, sizeZ, target, ground.clone(),
                prey, body, blast, player, plain.clone());
            copy.spent = spent.clone();
            return copy;
        }

        /** Count whoever stands on {@code tile} as run into already in this slide. */
        void spend(GridPos tile) {
            spent[index(tile)] = true;
        }

        /**
         * The same floor with one more body on {@code tile}, which goes off when it is run
         * into and reaches {@code radius} tiles. For a sentry about to be called up.
         */
        Scene withBombAt(GridPos tile, int radius) {
            boolean[] bodies = body.clone();
            int[] blasts = blast.clone();
            boolean[] open = plain.clone();
            int at = index(tile);
            bodies[at] = true;
            blasts[at] = radius;
            open[at] = false;
            return new Scene(width, height, sizeX, sizeZ, target, ground.clone(), prey, bodies, blasts, player, open);
        }

        /** How far {@code tile} is from the nearest player, in tiles walked. Far, with nobody there. */
        int nearestPlayer(GridPos tile) {
            int nearest = Integer.MAX_VALUE;
            for (int at = 0; at < player.length; at++) {
                if (!player[at]) continue;
                nearest = Math.min(nearest, Math.abs(at % width - tile.x()) + Math.abs(at / width - tile.z()));
            }
            return nearest;
        }

        /**
         * Whether a blast that {@code route} sets off reaches {@code tile}: some run of it
         * runs into something that goes off, near enough.
         */
        boolean blastReaches(Route route, GridPos tile) {
            for (Leg leg : route.legs()) {
                for (GridPos hit : leg.rammed()) {
                    int reach = blast[index(hit)];
                    if (reach >= 0 && Math.abs(hit.x() - tile.x()) + Math.abs(hit.z() - tile.z()) <= reach) return true;
                }
            }
            return false;
        }

        /**
         * The same floor with its target standing on {@code tile} instead. Where somebody is
         * standing there already, it is they who are taken for its target and nobody has
         * moved. The ground is shared and not copied: this is for trying a slide, which
         * copies what it changes.
         */
        Scene withTargetAt(GridPos tile) {
            boolean[] moved = prey.clone();
            int to = index(tile);
            int was = target == null ? -1 : index(target);
            if (was >= 0 && !moved[to]) moved[was] = false;
            moved[to] = true;
            return new Scene(width, height, sizeX, sizeZ, tile, ground, moved, body, blast, player, plain);
        }

        /**
         * Whether every player has something they can do about {@code painted} on their
         * turn: for each of them, a tile within three steps of where they stand, their own
         * included, that either has no paint on it or is beside one of its {@code bricks},
         * which they can then mine. Steps are the four ways, over ground they can stand on,
         * and not through it, a block, or anyone else.
         *
         * @param putDown walls somebody put down, which are stepped through here as if they
         *                were not there, though nobody ends a turn in one. Without that, a
         *                player who walled themselves in would have one tile in reach, it
         *                could never paint that tile, and a box of their own blocks would be
         *                a place nothing could reach
         */
        boolean leavesAWayOut(GridPos from, Collection<GridPos> painted, boolean[] putDown, boolean[] bricks) {
            Set<GridPos> hot = new HashSet<>(painted);
            for (int start = 0; start < player.length; start++) {
                if (!player[start]) continue;
                int[] steps = new int[player.length];
                java.util.Arrays.fill(steps, -1);
                steps[start] = 0;
                List<Integer> reach = new ArrayList<>(List.of(start));
                boolean out = false;
                for (int i = 0; i < reach.size() && !out; i++) {
                    int at = reach.get(i);
                    out = !putDown[at] && (!hot.contains(tile(at)) || beside(at, bricks));
                    if (steps[at] == 3) continue;
                    for (int[] way : DIRECTIONS) {
                        int next = index(at % width + way[0], at / width + way[1]);
                        if (next < 0 || steps[next] >= 0 || player[next]) continue;
                        if (!putDown[next] && !standable(next, from)) continue;
                        steps[next] = steps[at] + 1;
                        reach.add(next);
                    }
                }
                if (!out) return false;
            }
            return true;
        }

        /** Whether any of the four tiles against tile {@code at} is marked. */
        private boolean beside(int at, boolean[] marks) {
            for (int[] way : DIRECTIONS) {
                int next = index(at % width + way[0], at / width + way[1]);
                if (next >= 0 && marks[next]) return true;
            }
            return false;
        }

        /** Whether it would crush whoever stands on {@code tile}. */
        boolean preyAt(GridPos tile) {
            int at = tile == null ? -1 : index(tile);
            return at >= 0 && prey[at];
        }

        /** Whether this is the same floor with the same people standing in the same places. */
        boolean sameAs(Scene other) {
            return other != null && java.util.Arrays.equals(ground, other.ground)
                && java.util.Arrays.equals(prey, other.prey) && java.util.Arrays.equals(body, other.body)
                && java.util.Arrays.equals(blast, other.blast);
        }

        /** Whether a body cornered at {@code at} is against the edge of the arena. */
        boolean atEdge(GridPos at) {
            return at.x() <= 0 || at.z() <= 0 || at.x() + sizeX >= width || at.z() + sizeZ >= height;
        }

        /** The same floor with nobody on it to crush but a target standing on {@code tile}. */
        Scene aloneAt(GridPos tile) {
            boolean[] only = new boolean[prey.length];
            only[index(tile)] = true;
            return new Scene(width, height, sizeX, sizeZ, tile, ground, only, body, blast, player, plain);
        }

        /** Whether there is anyone for it to crush besides its target. */
        boolean others() {
            int mark = target == null ? -1 : index(target);
            for (int at = 0; at < prey.length; at++) {
                if (prey[at] && at != mark) return true;
            }
            return false;
        }

        /** Whether a player could be standing on tile {@code at} when a slide from {@code from} comes. */
        boolean standable(int at, GridPos from) {
            if (ground[at] != FLOOR || body[at]) return false;
            int x = at % width;
            int z = at / width;
            return x < from.x() || x >= from.x() + sizeX || z < from.z() || z >= from.z() + sizeZ;
        }

        int index(GridPos tile) {
            return index(tile.x(), tile.z());
        }

        private int index(int x, int z) {
            return x < 0 || z < 0 || x >= width || z >= height ? -1 : x + z * width;
        }

        GridPos tile(int at) {
            return new GridPos(at % width, at / width);
        }

        /** On the arena, and part of it. */
        boolean inside(GridPos tile) {
            int at = index(tile);
            return at >= 0 && ground[at] != OFF;
        }

        boolean isBlock(GridPos tile) {
            int at = index(tile);
            return at >= 0 && ground[at] == BLOCK;
        }

        boolean isHole(GridPos tile) {
            int at = index(tile);
            return at >= 0 && ground[at] == HOLE;
        }

        /** Count a brick as standing on {@code tile}. */
        void raise(GridPos tile) {
            int at = index(tile);
            ground[at] = BLOCK;
            plain[at] = false;
        }

        /** Take back a brick counted with {@link #raise}. */
        void unraise(GridPos tile) {
            int at = index(tile);
            ground[at] = FLOOR;
            plain[at] = true;
        }

        /** Count the block on {@code tile} as broken, and the tile as floor. */
        void lower(GridPos tile) {
            ground[index(tile)] = FLOOR;
        }

        /** The row a body cornered at {@code at} would step into next along (dx, dz). */
        List<GridPos> row(GridPos at, int dx, int dz) {
            return leadingEdge(at, sizeX, sizeZ, dx, dz);
        }

        /**
         * Run from {@code from} along (dx, dz) until something stops it.
         *
         * <p>A hole is open ground. A block ends the run in front of the row it stands in, and
         * is looked for before anything else in that row: a row with a block in it is one it
         * ran into, and every block in it is broken, whoever is standing beside them. They are
         * crushed, if they are prey, and run into if they are not. Then the edge of the arena.
         * Then whoever is standing there: prey is crushed, and anyone else stops it and is
         * struck where they stand.
         *
         * @param throughBodies take nobody as being there. For what a run could reach, as
         *                      against what it would do with everyone where they are
         */
        Leg run(GridPos from, int dx, int dz, boolean throughBodies) {
            int x = from.x();
            int z = from.z();
            int across = dx != 0 ? sizeZ : sizeX;
            int steps = 0;
            while (true) {
                int rowX = dx > 0 ? x + sizeX : dx < 0 ? x - 1 : x;
                int rowZ = dz > 0 ? z + sizeZ : dz < 0 ? z - 1 : z;
                boolean off = false;
                boolean stone = false;
                boolean someone = false;
                int crushed = -1;
                for (int i = 0; i < across; i++) {
                    int at = dx != 0 ? index(rowX, rowZ + i) : index(rowX + i, rowZ);
                    if (at < 0 || ground[at] == OFF) {
                        off = true;
                        continue;
                    }
                    if (looked != null) looked[at] = true;
                    if (ground[at] == BLOCK) stone = true;
                    if (throughBodies) continue;
                    if (body[at]) someone = true;
                    if (prey[at] && (crushed < 0 || tile(at).equals(target))) crushed = at;
                }
                GridPos victim = crushed < 0 ? null : tile(crushed);
                List<GridPos> rammed = List.of();
                if (someone) {
                    rammed = new ArrayList<>(across);
                    for (int i = 0; i < across; i++) {
                        int at = dx != 0 ? index(rowX, rowZ + i) : index(rowX + i, rowZ);
                        if (at >= 0 && body[at] && !spent[at]) rammed.add(tile(at));
                    }
                }
                if (stone) {
                    List<GridPos> smashed = new ArrayList<>(across);
                    for (int i = 0; i < across; i++) {
                        int at = dx != 0 ? index(rowX, rowZ + i) : index(rowX + i, rowZ);
                        if (at >= 0 && ground[at] == BLOCK) smashed.add(tile(at));
                    }
                    return new Leg(from, dx, dz, steps, Stop.BLOCK, smashed, victim, rammed);
                }
                if (off) return new Leg(from, dx, dz, steps, Stop.EDGE, List.of(), null);
                if (victim != null || someone) {
                    return new Leg(from, dx, dz, steps, Stop.BODY, List.of(), victim, rammed);
                }
                x += dx;
                z += dz;
                steps++;
            }
        }

        /** Whether, once {@code leg} is made, any run from where it stops would crush someone. */
        boolean crushOpenAfter(Leg leg) {
            for (GridPos tile : leg.smashed()) ground[index(tile)] = FLOOR;
            boolean open = false;
            GridPos from = leg.end();
            for (int[] way : DIRECTIONS) {
                if (run(from, way[0], way[1], false).crushed() != null) {
                    open = true;
                    break;
                }
            }
            for (GridPos tile : leg.smashed()) ground[index(tile)] = BLOCK;
            return open;
        }

        /**
         * The four ways from {@code from}, nearest its target first: along the axis it is
         * further away on, then along the other, then the two that lead away, in the order
         * every tie is broken in. With no target it is that order alone.
         */
        List<int[]> towardTarget(GridPos from) {
            List<int[]> ways = new ArrayList<>(4);
            if (target != null) {
                int gapX = gap(from.x(), sizeX, target.x());
                int gapZ = gap(from.z(), sizeZ, target.z());
                int[] alongX = {Integer.signum(gapX), 0};
                int[] alongZ = {0, Integer.signum(gapZ)};
                for (int[] way : Math.abs(gapX) > Math.abs(gapZ)
                        ? new int[][]{alongX, alongZ} : new int[][]{alongZ, alongX}) {
                    if (way[0] != 0 || way[1] != 0) ways.add(way);
                }
            }
            for (int[] way : DIRECTIONS) {
                boolean listed = false;
                for (int[] have : ways) listed |= have[0] == way[0] && have[1] == way[1];
                if (!listed) ways.add(way);
            }
            return ways;
        }

        /** How far a body cornered at {@code at} is from its target, in tiles walked. */
        int distance(GridPos at) {
            if (target == null) return 0;
            return Math.abs(gap(at.x(), sizeX, target.x())) + Math.abs(gap(at.z(), sizeZ, target.z()));
        }

        /** Whether a player stands against {@code tile}, on one of its four sides. */
        boolean besidePlayer(GridPos tile) {
            for (int[] way : DIRECTIONS) {
                int at = index(tile.x() + way[0], tile.z() + way[1]);
                if (at >= 0 && player[at]) return true;
            }
            return false;
        }

        /**
         * Whether a brick on {@code tile} would seal a player in. Every player keeps at least
         * two tiles beside them that they could step onto, or the brick is not raised.
         */
        boolean seals(GridPos tile) {
            for (int[] way : DIRECTIONS) {
                int x = tile.x() + way[0];
                int z = tile.z() + way[1];
                int at = index(x, z);
                if (at < 0 || !player[at]) continue;
                int ways = 0;
                for (int[] out : DIRECTIONS) {
                    int beside = index(x + out[0], z + out[1]);
                    if (beside >= 0 && beside != index(tile) && ground[beside] == FLOOR) ways++;
                }
                if (ways < 2) return true;
            }
            return false;
        }
    }

    // ── For anything that wants a slide worked out against the arena itself ──

    /** {@link Scene#run} against {@code arena} as it stands. */
    static Leg leg(GridArena arena, CombatEntity self, GridPos target, GridPos from, int dx, int dz) {
        return Scene.of(arena, self, target).run(from, dx, dz, false);
    }

    /** {@link #aim(Scene, GridPos, int[], int, int, boolean)} against {@code arena} as it stands. */
    static int[] aim(GridArena arena, CombatEntity self, GridPos target, GridPos from, int[] cameBy,
                     int phase, int index) {
        return aim(Scene.of(arena, self, target), from, cameBy, phase, index, false);
    }

    /** The slide it would make from where it stands, in {@code phase}, its first run aimed like the rest. */
    static Route route(GridArena arena, CombatEntity self, GridPos target, int phase) {
        return route(Scene.of(arena, self, target), self.getGridPos(), phase, null);
    }

    /** The bricks it would raise from where it stands, in {@code phase}. */
    static List<GridPos> bricks(GridArena arena, CombatEntity self, GridPos target, int phase) {
        return bricks(Scene.atBuild(arena, self, target), self.getGridPos(), phase);
    }

    /** What it would paint from where it stands, in {@code phase}, with nothing more raised. */
    static Telegraph telegraph(GridArena arena, CombatEntity self, GridPos target, int phase) {
        Scene scene = Scene.atBuild(arena, self, target);
        List<Leg> legs = route(scene, self.getGridPos(), phase, null).legs();
        if (legs.isEmpty()) return new Telegraph(List.of(), Map.of());
        return telegraph(arena, self, scene, self.getGridPos(), phase, legs.get(0).way());
    }

    // ================================================================
    // Telegraph
    // ================================================================

    /** Tiles, and the arrow on each one that carries one: the danger of a slide, or the part of it that is shown. */
    record Telegraph(List<GridPos> tiles, Map<GridPos, int[]> arrows) {}

    /**
     * What the player is shown of a slide from {@code from} that sets off along {@code first}:
     * its first run, and nothing of what comes after.
     *
     * <p>An arrow marks the ground its body will cross on that run, from where it sits to
     * where the run stops with the floor and everyone on it as they are: the edge, a block,
     * or whoever is standing in its way, whose own tile carries the arrow too. Every arrow
     * points the way of that run. No arrow marks the ground that run shakes as it passes, or
     * ground caught in a blast that the run itself sets off.
     *
     * <p>Nothing is shown for a later run: not where it turns off a brick, and not where it
     * could land. That is left to be read off the bricks. So ground with no paint is safe
     * from its first run and from nothing else, and {@link #telegraph} is the whole of the
     * danger, which is what it goes on reasoning with.
     */
    static Telegraph shown(GridArena arena, CombatEntity self, Scene scene, GridPos from, int[] first) {
        Map<GridPos, int[]> arrows = new LinkedHashMap<>();
        Leg one = scene.run(from, first[0], first[1], false);
        for (GridPos tile : one.lane().tiles(scene.sizeX, scene.sizeZ)) {
            // A hole it crosses is not ground anyone can be standing on.
            if (!scene.isHole(tile)) arrows.put(tile, first);
        }
        if (one.crushed() != null) arrows.put(one.crushed(), first);
        Set<GridPos> tiles = new LinkedHashSet<>(arrows.keySet());
        Leg unhindered = scene.run(from, first[0], first[1], true);
        tiles.addAll(wake(arena, self, unhindered.lane(), one.steps()));
        Route alone = new Route(List.of(one));
        for (int at = 0; at < scene.width * scene.height; at++) {
            if (scene.standable(at, from) && scene.blastReaches(alone, scene.tile(at))) tiles.add(scene.tile(at));
        }
        return new Telegraph(new ArrayList<>(tiles), arrows);
    }

    /**
     * The danger of a slide from {@code from} that sets off along {@code first}: a map of
     * where it is dangerous to be standing when it comes. It is what it reasons with, and
     * not what it paints, which is only its first run: see {@link #shown}.
     *
     * <p>An arrow marks a tile on which that slide would crush whoever stood there. It is
     * found by trying it: for every tile a player could be standing on, the slide is worked
     * out as it will be on the turn it happens, with its target on that tile and its first
     * run going the way it is now aimed. The arrow points the way of the run that would do
     * it. No arrow marks ground that would only be shaken, which is what lies beside its
     * first run to where that run stops with nobody in its way.
     *
     * <p>Ground a blast would reach is on the map too, with no arrow: a tile on which that slide,
     * made with its target standing there, runs into something that goes off within reach of
     * it. A sentry nothing runs into marks nothing here. It has its own ring.
     *
     * <p>So a player alone, who ends their turn on ground that is not on this map, is neither crushed
     * nor shaken nor caught in a blast by the slide that follows, so long as the floor is as
     * it was painted and nothing else has moved: a block set down or dug out after the paint
     * changes where a run stops, and that is theirs to answer for.
     *
     * <p>With a party one map cannot be exact, since each of them can go anywhere and who it
     * is after is decided when it slides. Each tile is tried as if whoever stood there were
     * its target, twice: with everyone else where they are now, and with everyone else out
     * of its way altogether. Either is enough to mark it. What that misses is a slide bent
     * by where the others end up, which neither of those is.
     */
    static Telegraph telegraph(GridArena arena, CombatEntity self, Scene scene, GridPos from, int phase,
                               int[] first) {
        Map<GridPos, int[]> arrows = new LinkedHashMap<>();
        Set<GridPos> blasted = new LinkedHashSet<>();
        boolean party = scene.others();
        for (int at = 0; at < scene.width * scene.height; at++) {
            if (!scene.standable(at, from)) continue;
            GridPos tile = scene.tile(at);
            Route tried = route(scene.withTargetAt(tile), from, phase, first);
            Leg last = tried.last();
            boolean caught = scene.blastReaches(tried, tile);
            if (party && (last == null || !tile.equals(last.crushed()))) {
                tried = route(scene.aloneAt(tile), from, phase, first);
                last = tried.last();
                caught |= scene.blastReaches(tried, tile);
            }
            if (last != null && tile.equals(last.crushed())) arrows.put(tile, last.way());
            else if (caught) blasted.add(tile);
        }
        Set<GridPos> tiles = new LinkedHashSet<>(arrows.keySet());
        Leg one = scene.run(from, first[0], first[1], true);
        tiles.addAll(wake(arena, self, one.lane(), one.steps()));
        tiles.addAll(blasted);
        return new Telegraph(new ArrayList<>(tiles), arrows);
    }

    /**
     * The tiles shaken by a run of {@code steps} tiles down {@code lane}: every tile touching
     * the ground it crossed, sideways and at the corners, and the row past where it stopped.
     *
     * <p>Never a tile of the lane itself, even the part it did not reach. That ground is the
     * crush's, and whoever stopped the slide by standing in it is hit once, not twice. Never
     * the ground it started on either, which nobody can be standing in.
     */
    static List<GridPos> wake(GridArena arena, CombatEntity self, Lane lane, int steps) {
        int sizeX = self.getSizeX();
        int sizeZ = self.getSizeZ();
        Set<GridPos> crush = new HashSet<>(lane.tiles(sizeX, sizeZ));
        Set<GridPos> swept = new LinkedHashSet<>();
        GridPos at = lane.from();
        for (int i = 0; i < Math.min(steps, lane.length()); i++) {
            swept.addAll(leadingEdge(at, sizeX, sizeZ, lane.dx(), lane.dz()));
            at = new GridPos(at.x() + lane.dx(), at.z() + lane.dz());
        }
        GridPos start = lane.from();
        Set<GridPos> shaken = new LinkedHashSet<>();
        for (GridPos tile : swept) {
            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) {
                    GridPos near = new GridPos(tile.x() + ox, tile.z() + oz);
                    if (crush.contains(near) || arena.getTile(near) == null) continue;
                    boolean underIt = near.x() >= start.x() && near.x() < start.x() + sizeX
                        && near.z() >= start.z() && near.z() < start.z() + sizeZ;
                    if (!underIt) shaken.add(near);
                }
            }
        }
        return new ArrayList<>(shaken);
    }

    // ================================================================
    // Geometry
    // ================================================================

    /** A straight run of the whole body: where from, which way, and how many tiles. */
    record Lane(GridPos from, int dx, int dz, int length) {

        /** Where the body's corner stands after {@code steps} tiles. */
        GridPos at(int steps) {
            return new GridPos(from.x() + dx * steps, from.z() + dz * steps);
        }

        /** Where the body's corner stands at the end of it. */
        GridPos end() {
            return at(length);
        }

        /** Where the body's corner stands after each of the first {@code steps} tiles. */
        List<GridPos> path(int steps) {
            List<GridPos> path = new ArrayList<>();
            for (int i = 1; i <= Math.min(steps, length); i++) {
                path.add(new GridPos(from.x() + dx * i, from.z() + dz * i));
            }
            return path;
        }

        /** Every tile the body sweeps over: all of the lane, and none of where it starts. */
        List<GridPos> tiles(int sizeX, int sizeZ) {
            List<GridPos> tiles = new ArrayList<>();
            GridPos at = from;
            for (int i = 0; i < length; i++) {
                tiles.addAll(leadingEdge(at, sizeX, sizeZ, dx, dz));
                at = new GridPos(at.x() + dx, at.z() + dz);
            }
            return tiles;
        }
    }

    /** The tiles a body cornered at {@code at} newly covers by moving one tile along (dx, dz). */
    static List<GridPos> leadingEdge(GridPos at, int sizeX, int sizeZ, int dx, int dz) {
        List<GridPos> edge = new ArrayList<>();
        if (dx != 0) {
            int x = dx > 0 ? at.x() + sizeX : at.x() - 1;
            for (int i = 0; i < sizeZ; i++) edge.add(new GridPos(x, at.z() + i));
        } else {
            int z = dz > 0 ? at.z() + sizeZ : at.z() - 1;
            for (int i = 0; i < sizeX; i++) edge.add(new GridPos(at.x() + i, z));
        }
        return edge;
    }

    /** Everyone in the party, wherever the engine happens to be keeping them. */
    private static List<GridPos> players(GridArena arena) {
        List<GridPos> all = new ArrayList<>(arena.getAllPlayerGridPositions());
        GridPos lead = arena.getPlayerGridPos();
        if (lead != null && !all.contains(lead)) all.add(lead);
        return all;
    }

    /**
     * Asleep it threatens nothing, and says so. Awake, the ground to stay off is the four
     * lanes it could take from where it stands, not a diamond of walking distance it will
     * never walk. Each runs to the first block or the edge, and takes in the open tiles beside
     * that block, where it would crush someone against it. Where it would turn next is not
     * guessed at here.
     */
    @Override
    public Set<GridPos> computeThreatTiles(CombatEntity self, GridArena arena) {
        Set<GridPos> tiles = new HashSet<>();
        if (!awake) return tiles;
        Scene scene = Scene.of(arena, self, null);
        for (int[] way : DIRECTIONS) {
            Leg unhindered = scene.run(self.getGridPos(), way[0], way[1], true);
            tiles.addAll(unhindered.lane().tiles(scene.sizeX, scene.sizeZ));
            if (unhindered.stop() != Stop.BLOCK) continue;
            for (GridPos tile : scene.row(unhindered.end(), way[0], way[1])) {
                if (scene.inside(tile) && !scene.isBlock(tile)) tiles.add(tile);
            }
        }
        return tiles;
    }

    // ── For the tests, which sit beside this class ──

    boolean isAwake() { return awake; }

    /** Whether its next turn is a slide. */
    boolean isSliding() { return sliding; }

    /** The way its next slide sets off, as painted, or null. */
    int[] aimedWay() { return aimed; }

    /** The runs its next slide has, as painted: a phase, or {@link #exactly} when it held back. */
    int aimedRuns() { return aimedRuns; }

    /** Bricks it gave up on its last build so as to leave a way out. */
    int bricksHeldBack() { return heldBricks; }

    /** Runs it gave up on its last build so as to leave a way out. */
    int runsHeldBack() { return heldRuns; }

    /** Say whether there are sentries to call up, where no mod is there to say so. */
    void withSentries(boolean there) { sentries = there; }

    // ================================================================
    // The real entity
    // ================================================================

    private static boolean isAetherSlider(CombatEntity entity) {
        return ENTITY_TYPE.equals(entity.getEntityTypeId());
    }

    /**
     * Spawn hook: the guard, the nameplate, the size, and the entity's own idea of whether it
     * is awake.
     *
     * <p>The guard goes up here and not on its first turn because the player moves first, and
     * a sword swung in that opening would otherwise land.
     *
     * <p>What the Aether's Slider does in an arena, where it is frozen with NoAI:
     * <ul>
     *   <li>Nothing that moves, hits or breaks. All of that lives in goals, NoAI stops goals,
     *       and every one of them also refuses to run while the entity thinks it is asleep.
     *       So it is put to sleep here, which is where a fresh one starts anyway.</li>
     *   <li>Asleep, it is solid to walk into, like a block. That is right for a thing that
     *       fills its tiles, and it stops the moment {@link #awaken} wakes it, before it
     *       first moves.</li>
     *   <li>It evaporates liquid around itself every tick whatever state it is in. Nothing
     *       here can switch that off. See {@link #boiledAway}.</li>
     * </ul>
     *
     * <p>Its hitbox is two blocks square, so at its natural size it fills a 2x2 footprint to
     * the edge and no further. The scale every unlisted boss is given would hang it half a
     * tile over each neighbour.
     */
    private static void dress(ServerWorld world, MobEntity mob, CombatEntity entity) {
        holdGuard(entity);
        mob.setCustomName(Text.literal(NAMEPLATE));
        mob.setCustomNameVisible(true);

        if (!isAetherSlider(entity)) return;
        //? if <=1.21.1 {
        var scale = mob.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_SCALE);
        //?} else {
        /*var scale = mob.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.SCALE);
        *///?}
        if (scale != null) {
            scale.setBaseValue(fitScale(Math.min(entity.getSizeX(), entity.getSizeZ())));
        }
        // Asleep to begin with. A replacement for an entity the engine found dead mid-fight
        // is put back in whichever state the fight has reached.
        setEntityAwake(mob, entity.getAiInstance() instanceof SliderAI ai && ai.isAwake());
    }

    /** The scale at which the Slider's own hitbox exactly fills {@code tiles} tiles. Never above 1. */
    static double fitScale(int tiles) {
        if (tiles <= 0) return 1.0;
        return Math.min(1.0, tiles / NATIVE_WIDTH);
    }

    /**
     * The awakening: said out loud, and shown on the entity, whose sleeping and waking faces
     * are different textures chosen by its own flag.
     */
    private static void awaken(CustomActionHandler.Context ctx) {
        CombatEntity slider = ctx.self();
        MobEntity mob = slider.getMobEntity();
        if (mob != null && isAetherSlider(slider)) setEntityAwake(mob, true);

        BlockPos corner = ctx.arena().gridToBlockPos(slider.getGridPos());
        double x = corner.getX() + slider.getSizeX() / 2.0;
        double z = corner.getZ() + slider.getSizeZ() / 2.0;
        ctx.world().spawnParticles(ParticleTypes.POOF, x, corner.getY() + 1.0, z, 30, 0.9, 0.9, 0.9, 0.02);
        ctx.world().playSound(null, corner, sound("entity.slider.awaken", SoundEvents.BLOCK_DEEPSLATE_BREAK),
            SoundCategory.HOSTILE, 2.0f, 1.0f);
        ctx.message("§6§l" + slider.getDisplayName() + " awakens! §r§7Stay out of the lane it marks.");
    }

    /** One of the Aether's sounds by path, or {@code fallback} when it is not registered. */
    private static SoundEvent sound(String path, SoundEvent fallback) {
        Identifier id = Identifier.of(AetherCompat.MOD_ID, path);
        SoundEvent event = Registries.SOUND_EVENT.containsId(id) ? Registries.SOUND_EVENT.get(id) : null;
        return event != null ? event : fallback;
    }

    /** Set the entity's own awake flag, by the Aether's name for it. */
    private static void setEntityAwake(MobEntity mob, boolean awake) {
        try {
            mob.getClass().getMethod("setAwake", boolean.class).invoke(mob, awake);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (REPORTED.add("setAwake")) {
                CrafticsMod.LOGGER.warn("[Craftics × Aether] could not set the Slider's awake flag "
                    + "(no setAwake). The Aether may have renamed it.");
            }
        }
    }

    /** Things already said once. The hooks run per fight, and none of this is worth repeating. */
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
}
