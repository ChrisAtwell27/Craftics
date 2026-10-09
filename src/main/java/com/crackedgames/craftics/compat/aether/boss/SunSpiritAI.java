package com.crackedgames.craftics.compat.aether.boss;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.CrafticsAPI;
import com.crackedgames.craftics.api.ProjectileImpactHandler;
import com.crackedgames.craftics.api.registry.ProjectileImpactRegistry;
import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.CombatManager;
import com.crackedgames.craftics.combat.ProjectileSpawner;
import com.crackedgames.craftics.combat.ai.AIRegistry;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.combat.ai.boss.BossAI;
import com.crackedgames.craftics.compat.aether.AetherCompat;
import com.crackedgames.craftics.compat.aether.AetherMobs;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import com.crackedgames.craftics.mixin.DisplayEntityInvoker;
import com.crackedgames.craftics.mixin.ItemDisplayInvoker;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gold Dungeon Boss - "The Sun Spirit"
 * Entity: aether:sun_spirit | stats from the biome JSON | Size 2x2
 *
 * <p>Nothing the party carries hurts it. It holds a guard from the moment it spawns, and
 * the only cold in the room is its own: the fight is turning that cold back on it, and
 * turning it back takes aim.
 * <ul>
 *   <li><b>It drifts.</b> It never walks at anyone. It crosses the room on a straight
 *       heading, its move speed in tiles a turn, and bounces off walls, braziers, frozen
 *       floor and anyone standing in its way like a billiard ball. One turn in three it
 *       veers an eighth of a turn, toward whoever it is fighting. A turn it cannot move at
 *       all, it turns to the nearest way that is open, so it is never held for two.</li>
 *   <li><b>It burns what it passes.</b> The floor its body leaves is alight for two turns,
 *       and whoever is beside it where it stops takes its attack and Burning.</li>
 *   <li><b>It throws a crystal every turn:</b> two Fire Crystals, then an Ice Crystal, and
 *       round again. A Fire Crystal is thrown at its target and flies in a straight line,
 *       two tiles a turn. Reaching a player, a wall or a brazier it bursts and burns
 *       everyone within a tile. Anything else in its way only breaks it, and after four
 *       turns in the air it burns out. One strike destroys it. An Ice Crystal is thrown
 *       the other way, over its shoulder, and then follows a player until it lands, for a
 *       lighter hit and Weakness.</li>
 *   <li><b>An Ice Crystal that is struck flies straight away from whoever struck it,</b>
 *       along the nearest of the eight ways. If that line crosses the Sun Spirit or passes
 *       within a tile of it, at any distance, the crystal goes back into it: a tenth of
 *       its health through its guard, and frozen for two of its turns, guard down, held
 *       still, no fire, no crystals of its own. If the line misses, the crystal shatters
 *       where the line ends and that is all. So it has to be struck from the side away
 *       from the Sun Spirit, and it is thrown so that it does not arrive already lined
 *       up. Each freeze brings out a Fire Minion, two at most, which does nothing on the
 *       turn it appears. The turn it thaws it flares, burning the ring it was frozen in as
 *       well as the ring it carries off.</li>
 *   <li><b>Where the crystal was struck, the floor freezes,</b> hit or miss: three tiles
 *       by three, for four turns, less whatever is under the Sun Spirit or touching it,
 *       which is too hot to freeze. Frozen floor harms nobody and anyone may walk on it.
 *       Fire on it goes out and none can be laid there. To the Sun Spirit it is a wall,
 *       which it bounces off like stone. A brazier in the patch or touching it goes dark
 *       until the frost is gone. So frost is how it is turned into the line of a shot,
 *       and how a brazier is silenced.</li>
 * </ul>
 *
 * <h2>Three phases, by thirds of its health</h2>
 * <ul>
 *   <li><b>Above two thirds:</b> everything above.</li>
 *   <li><b>From two thirds down, the braziers cut the room.</b> As each of its turns ends
 *       one brazier, taken in turn round the room, marks a lane: a straight line of floor
 *       from the brazier to the wall, along whichever row, column or diagonal comes
 *       closest to the player nearest it. Its next turn lights the lane, for two turns.
 *       Frost stops a lane where the lane meets it. A dark brazier marks nothing, and its
 *       turn is lost, not passed on to the next: silencing one thins the lanes. A lane
 *       marked by a brazier that has gone dark since is not lit either. A brazier is any
 *       obstacle of netherrack: they are found by what they are, not by where the room
 *       was built. They do not stop while the Sun Spirit is frozen: the room is not.</li>
 *   <li><b>From one third down, the room burns inward.</b> Two braziers take their turn
 *       each turn. Every second turn it is free, the outermost ring of floor not yet burning
 *       catches and stays lit, four rings deep at most. Frozen floor is never taken, and
 *       every freeze puts the innermost burning ring out again. It stops wandering: each
 *       drift is aimed afresh at the nearest player (and still bounces), a tile further
 *       than before, and with the straight way at them shut it takes the nearest way that
 *       is open, so frost and a wall cannot hold it between them.</li>
 * </ul>
 *
 * <h2>What it shows, and what it holds itself to</h2>
 * {@link #computeThreatTiles} is its whole next turn: the ground it will cross, the ring
 * its aura will burn, the ring a thaw will flare, the floor about to catch and the lanes
 * about to be lit. All of it is worked out by {@link #plan}, the same call the turn itself
 * is made from, out of nothing but the board and what the boss already knew when its last
 * turn ended. So a player who ends their turn off those tiles, and off fire that is already
 * burning, is not hurt by its turn. A crystal is the exception, and a minion: each is a
 * thing on the board that can be seen coming, stepped out of the way of, or struck. Nobody
 * takes two of its area hits in one turn either. The aura and the flare are one hit over
 * both rings.
 *
 * <p>A crystal is never launched closer than three tiles to anyone. It flies two tiles the
 * turn it appears, so one born any nearer could land before the player it was thrown at had
 * a turn to answer it. With no tile that far away to put one on, none is thrown.
 *
 * <h2>Two things about the floor</h2>
 * Its fire is never laid on a tile squarely beside a brazier. The arena spreads fire into
 * anything that burns, netherrack burns, and a brazier it reached would be burnt down to a
 * floor tile.
 *
 * <p>Frozen floor is not the engine's frost. That tile slows whoever lands on it, and ice
 * sends them sliding, and this floor was promised to do neither. It is the one walkable
 * tile type the arena attaches nothing to, painted as packed ice: see {@link #FROZEN_FLOOR}.
 */
public class SunSpiritAI extends BossAI {

    public static final String BOSS_KEY = "boss:" + AetherCompat.GOLD_DUNGEON;
    public static final String ENTITY_ID = AetherCompat.MOD_ID + ":sun_spirit";

    /** Projectile types. The entity ids only name them: neither is a mob, so the engine flies a stand-in. */
    public static final String FIRE_CRYSTAL = "aether_fire_crystal";
    public static final String ICE_CRYSTAL = "aether_ice_crystal";
    private static final String FIRE_CRYSTAL_ENTITY = AetherCompat.MOD_ID + ":fire_crystal";
    private static final String ICE_CRYSTAL_ENTITY = AetherCompat.MOD_ID + ":ice_crystal";

    /**
     * Every third crystal is ice. The Aether's own pattern is every fifth, at a rate of fire
     * no turn-based fight can match.
     */
    static final int CRYSTALS_PER_CYCLE = 3;
    /** Boss turns a freeze costs it, in every phase. */
    static final int FREEZE_TURNS = 2;
    /** Fire Minions it will have out at once. A freeze that finds this many brings no more. */
    static final int MAX_MINIONS = 2;
    /** Turns the fire it leaves behind keeps burning. The same two a Bastion Brute charge leaves. */
    static final int TRAIL_TURNS = 2;
    /** One turn in three it veers, the Aether's own odds for a change of course. */
    static final int VEER_ODDS = 3;
    /** Tiles a Fire Crystal's burst reaches around where it lands. */
    static final int FIRE_BURST_RADIUS = 1;
    /** A deflected crystal takes at least this share of the boss's health, so it matters at any scaling. */
    static final int DEFLECT_HP_SHARE = 10;

    /**
     * What frozen floor is made of. Not {@code FROST}, which slows whoever lands on it, and
     * not {@code ICE}, which makes players and mobs alike slide across it: this floor does
     * nothing to anyone but the Sun Spirit. {@code SCULK} is the engine's paint-only tile,
     * walkable, with no rule read off it anywhere, and what it looks like is whatever block
     * it is given. Every rule about frost in this file is read off the board through this
     * one type, so nothing about it has to be remembered between turns.
     */
    static final TileType FROZEN_FLOOR = TileType.SCULK;
    /** Rounds a frozen patch lasts. Struck on the player's turn, that is four of the boss's. */
    static final int FROST_TURNS = 4;
    /** How far the patch reaches from the tile the crystal was struck on. One: three by three. */
    static final int FROST_REACH = 1;

    /**
     * How near to anyone a crystal may appear, in tiles, diagonals counted as one. The rule
     * the Valkyrie Queen's crystal keeps. A crystal flies two the turn it is launched, so
     * from three off it cannot reach anybody's tile before they have had a turn. Nor can a
     * Fire Crystal's burst: one that meets a wall that soon bursts on its first tile or
     * the one it was born on, which is still two from everyone, and it bursts one wide.
     */
    static final int HEAD_START = 3;
    /** Turns a Fire Crystal flies before it burns out: eight tiles. */
    static final int FIRE_CRYSTAL_TURNS = 4;
    /** Turns a brazier's lane of fire burns once it is lit. */
    static final int LANE_TURNS = 2;

    /** Rings of floor the room may burn in from its walls. */
    static final int MAX_RINGS = 4;
    /** However small the room, the fire leaves a floor this wide in the middle of it. */
    static final int FLOOR_KEPT = 11;
    /** Its own turns from one ring catching to the next. */
    static final int RING_EVERY = 2;
    /**
     * Turns the burning rings are lit for. The engine cuts short any boss fire asked for
     * without an end, so "for good" has to be asked for as a number no fight outlasts.
     */
    static final int LIT_TURNS = 99;

    /** The area effect tag the engine already answers with Burning for two turns. */
    private static final String AURA_EFFECT = "burning";
    /** Tiles out from its body a crystal may appear. */
    private static final int CRYSTAL_REACH = 2;
    /** What a spot that is not dead in line with its target costs, in tiles of distance. */
    private static final int OFF_LINE = 4;

    // The fight's state that the crystal handler has to reach lives on the boss itself. The
    // handler is handed the combat entity, never this AI.
    private static final String FROZEN = "sun_spirit_frozen";
    private static final String FROZEN_TURNS = "sun_spirit_frozen_turns";
    private static final String MINIONS_OWED = "sun_spirit_minions_owed";
    private static final String RINGS_OWED = "sun_spirit_rings_owed";
    private static final String CLUE_GIVEN = "sun_spirit_clue_given";
    // And these two on a Fire Crystal: how long it has flown, and whether it ended without a burst.
    private static final String TURNS_FLOWN = "sun_spirit_crystal_turns";
    private static final String SPENT = "sun_spirit_crystal_spent";
    private static final String SPOKE = "sun_spirit_last_words";

    static final String GUARD_HINT = "§6§l\"No man, hero, or villain can harm me.\" "
        + "§r§7Only its own cold can. Knock an §bIce Crystal§7 back at it.";
    /** The Aether's own clue, word for word, the first time it freezes. */
    static final String FROZEN_CLUE = "§eI should try attacking the Sun Spirit while it's frozen!";
    static final String LAST_WORDS = "§b\"Such bitter cold... is this the feeling... of pain?\"";
    private static final String THAW_LINE = "§6✦ The Sun Spirit burns free of the ice. Its guard is back up!";
    private static final String BRAZIERS_LINE = "§6✦ The braziers answer it. Each turn one will mark a line of fire across the room.";
    private static final String WIDE_LINE = "§7✦ The Ice Crystal flies wide of %s and shatters. §8Strike it from the side away from the Sun Spirit.";
    private static final String LAST_THIRD_LINE = "§c✦ The Sun Spirit burns white and turns on you. The room itself begins to catch.";
    private static final String CATCH_LINE = "§6✦ The floor along the walls catches. The fire closes in.";
    private static final String PUSHED_LINE = "§b✦ The cold drives the fire back from the middle of the room.";
    private static final String MINION_LINE = "§6✦ A Fire Minion tears loose from the ice. §7It needs a turn to find its feet.";
    private static final String NAMEPLATE = "§6§lThe Sun Spirit";

    /** The eight ways it can travel, in turning order. */
    private static final int[][] COMPASS = {
        {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
    };

    private static boolean registered = false;

    /**
     * Register the fight. No traits are declared: everything the panel should say about this
     * boss (Invulnerable while it holds its guard, Flameborne, Large, Infallible) the engine
     * already reads off the live entity.
     */
    public static void register() {
        if (registered) return;
        registered = true;

        AIRegistry.registerBoss(BOSS_KEY, SunSpiritAI::new);
        ProjectileImpactRegistry.register(FIRE_CRYSTAL, SunSpiritAI::fireCrystalBursts, false);
        ProjectileImpactRegistry.register(ICE_CRYSTAL, new IceCrystal(), true);

        // Asked of the loader rather than of AetherCompat, whose flag is only set once its own
        // init has run. Nothing promises that happens before this.
        if (FabricLoader.getInstance().isModLoaded(AetherCompat.MOD_ID)) {
            // Keyed by entity type, not by boss key: what the real entity does every tick is
            // a problem wherever one stands in an arena, whatever is thinking for it.
            CrafticsAPI.registerSpawnCustomizer(ENTITY_ID, SunSpiritAI::onSpawn);
        }
    }

    // ================================================================
    // Per-fight state
    // ================================================================

    private final Random rng;
    /** Where it is headed on its next move, until its last third aims every move afresh. One of {@link #COMPASS}. */
    private int headingX;
    private int headingZ;
    /** Its own crystals that actually flew. Counted when the engine confirms the spawn, never before. */
    private int crystalsLaunched = 0;
    /** Its speed the last time it moved, for planning the turn it thaws while it is still frozen. */
    private int lastSpeed = 2;
    /** Rings of floor alight, counted in from the walls. */
    private int rings = 0;
    /** Its own free turns still to pass before the next ring catches. One to begin with: a turn of warning. */
    private int burnWait = RING_EVERY - 1;
    /** Whose turn it is among the braziers to mark a lane. */
    private int nextBrazier = 0;
    /** The lanes marked as its last turn ended, which its next turn lights. */
    private List<Lane> lanes = new ArrayList<>();
    private boolean saidLastThird = false;
    /** The arena of the turn in progress, so a crystal the engine just spawned can be found. */
    private GridArena lastArena;

    public SunSpiritAI() {
        this(new Random());
    }

    /** Seeded, for tests. */
    SunSpiritAI(Random rng) {
        this.rng = rng;
        // Diagonal to begin with, as a ball struck into a room would be.
        int[] start = COMPASS[1 + 2 * rng.nextInt(4)];
        this.headingX = start[0];
        this.headingZ = start[1];
    }

    @Override
    public int getGridSize() {
        return 2;
    }

    /** It hangs in the middle of its chamber, as it does in the Gold Dungeon, and drifts out from there. */
    @Override
    public boolean spawnsAtCenter() {
        return true;
    }

    /**
     * It lays fire every turn it moves and the engine's line for that would be said every
     * turn. The fire that matters, a ring catching or being driven back, gets a line of its
     * own from here.
     */
    @Override
    public String describeTerrain(TileType type, int changed) {
        return type.isFlames() ? "" : null;
    }

    int headingX() {
        return headingX;
    }

    int headingZ() {
        return headingZ;
    }

    void setHeading(int x, int z) {
        this.headingX = x;
        this.headingZ = z;
    }

    int crystalsLaunched() {
        return crystalsLaunched;
    }

    int rings() {
        return rings;
    }

    List<Lane> lanes() {
        return lanes;
    }

    /** Two thirds of its health or less: where the braziers wake. */
    @Override
    protected boolean reachedPhaseTwo(CombatEntity self) {
        return phase(self) >= 2;
    }

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        say(self, arena, BRAZIERS_LINE);
    }

    /** It does not chase, so there is no advance to make while anything is pending. */
    @Override
    public EnemyAction getChargingAdvanceAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        return new EnemyAction.Idle();
    }

    /** Its dying line, for the engine to say as it announces the death. */
    @Override
    public String getLastWords(CombatEntity self) {
        return lastWords(self);
    }

    /**
     * The braziers of the room it is fighting in: every obstacle made of netherrack. Found
     * by what they are, so a room built differently, or a floor with none, needs nothing
     * changed here.
     */
    protected List<GridPos> braziers(GridArena arena) {
        List<GridPos> stones = new ArrayList<>();
        for (int x = 0; x < arena.getWidth(); x++) {
            for (int z = 0; z < arena.getHeight(); z++) {
                GridTile tile = arena.getTile(x, z);
                if (tile != null && tile.getType() == TileType.OBSTACLE && tile.getBlockType() != null) {
                    stones.add(new GridPos(x, z));
                }
            }
        }
        // Only now is the question put to the game's own block list, and only for a floor
        // that has blocks on it at all.
        return stones.isEmpty() ? stones : Room.netherrackOnly(arena, stones);
    }

    // ================================================================
    // The turn
    // ================================================================

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        lastArena = arena;
        List<GridPos> braziers = braziers(arena);
        int phase = phase(self);
        if (phase == 3 && !saidLastThird) {
            saidLastThird = true;
            say(self, arena, LAST_THIRD_LINE);
        }

        // Worked out before anything is changed, from exactly what the player was shown.
        Plan plan = plan(self, arena, braziers);
        List<EnemyAction> turn = new ArrayList<>();
        EnemyAction pushed = pushFireBack(self, arena);

        if (plan.sitsOut()) {
            // A turn spent in the ice. It does nothing of its own. What it owes for being
            // frozen is paid here, and the room goes on without it.
            self.setAiMemory(FROZEN_TURNS, self.getAiMemory(FROZEN_TURNS, 0) - 1);
            int owed = self.getAiMemory(MINIONS_OWED, 0);
            EnemyAction.SummonMinions minion = owed > 0 && getAliveMinionCount() < MAX_MINIONS
                ? summonMinion(self, arena, playerPos) : null;
            if (minion != null) {
                self.setAiMemory(MINIONS_OWED, owed - 1);
                turn.add(minion);
            }
            if (pushed != null) turn.add(pushed);
            if (!plan.lanes().isEmpty()) {
                turn.add(new EnemyAction.CreateTerrain(plan.lanes(), TileType.FIRE, LANE_TURNS));
            }
            markLanes(arena, self, braziers);
            showBraziers(self, arena, braziers);
            return bundle(turn);
        }

        if (isFrozen(self)) {
            thaw(self);
            say(self, arena, THAW_LINE);
        } else if (!self.isDamageImmune()) {
            // First turn of a fight the spawn hook never dressed, or a guard something dropped.
            self.setDamageImmune(true, GUARD_HINT);
        }

        Drift drift = plan.drift();
        GridPos end = drift.end(self.getGridPos());
        if (!drift.path().isEmpty()) {
            // First in the bundle on purpose. Only one sub-action may own the turn while it
            // plays out, and the walk is the one that has to.
            turn.add(new EnemyAction.Move(drift.path()));
            if (!plan.trail().isEmpty()) {
                turn.add(new EnemyAction.CreateTerrain(plan.trail(), TileType.FIRE, TRAIL_TURNS));
            }
        }
        if (pushed != null) turn.add(pushed);
        if (!plan.catching().isEmpty()) {
            turn.add(new EnemyAction.CreateTerrain(plan.catching(), TileType.FIRE, LIT_TURNS));
            say(self, arena, CATCH_LINE);
        }
        if (!plan.lanes().isEmpty()) {
            turn.add(new EnemyAction.CreateTerrain(plan.lanes(), TileType.FIRE, LANE_TURNS));
        }
        if (!plan.burn().isEmpty()) {
            // One hit over everything it burns this turn, so that nobody standing where two
            // of its rings overlap is hit for both.
            turn.add(new EnemyAction.TileAreaAttack(plan.burn(), end, self.getAttackPower(), AURA_EFFECT));
        }

        GridPos prey = prey(arena, self);
        EnemyAction crystal = launchCrystal(arena, self, drift, prey != null ? prey : playerPos);
        if (crystal != null) turn.add(crystal);

        // What it will do next turn is settled now, as this one ends, so that the tiles
        // painted for the player in between are the tiles it will really burn.
        lastSpeed = Math.max(1, self.getMoveSpeed());
        headingX = drift.headingX();
        headingZ = drift.headingZ();
        // In its last third it has no heading to keep: see heading().
        if (phase < 3) {
            GridPos target = prey != null ? prey : playerPos;
            int[] out = drift.path().isEmpty() && target != null ? openWayAt(arena, self, target) : null;
            if (out != null) {
                // Held where it stood, by frost against a wall as likely as not. It does not
                // go on trying the same shut way, or the one beside it: it turns to the
                // nearest way that is open, so nothing holds it for more than the one turn.
                headingX = out[0];
                headingZ = out[1];
            } else if (drift.path().isEmpty() || rng.nextInt(VEER_ODDS) == 0) {
                veer(self, end, target);
            }
        }
        if (phase == 3) {
            if (burnWait <= 0) {
                burnWait = RING_EVERY - 1;
                if (rings < maxRings(arena)) rings++;
            } else {
                burnWait--;
            }
        }
        markLanes(arena, self, braziers);
        showBraziers(self, arena, braziers);
        return bundle(turn);
    }

    private static EnemyAction bundle(List<EnemyAction> turn) {
        if (turn.isEmpty()) return new EnemyAction.Idle();
        return turn.size() == 1 ? turn.get(0) : new EnemyAction.CompositeAction(turn);
    }

    /**
     * Everything its next turn will burn: the ground it crosses, the ring its aura takes,
     * the ring a thaw flares, the floor about to catch and the lanes about to be lit. With
     * a frozen turn still to sit out, the lanes are all of it.
     */
    @Override
    public Set<GridPos> computeThreatTiles(CombatEntity self, GridArena arena) {
        return plan(self, arena, braziers(arena)).threat(self);
    }

    /** One turn, worked out and not yet done. */
    record Plan(Drift drift, List<GridPos> trail, List<GridPos> burn, List<GridPos> catching,
                List<GridPos> lanes, boolean sitsOut) {
        /** Every tile this turn puts fire on or burns someone standing in. */
        Set<GridPos> threat(CombatEntity self) {
            Set<GridPos> tiles = new LinkedHashSet<>(lanes);
            if (sitsOut) return tiles;
            tiles.addAll(swept(self, drift));
            tiles.addAll(burn);
            tiles.addAll(catching);
            return tiles;
        }
    }

    /**
     * Its next turn, from the board as it stands and what it settled when its last turn
     * ended. Reads and changes nothing, which is what lets the same call paint the warning
     * between turns and make the turn itself.
     */
    Plan plan(CombatEntity self, GridArena arena, List<GridPos> braziers) {
        List<GridPos> lit = laneFire(arena, braziers, lanes);
        if (sitsOut(self)) {
            return new Plan(new Drift(List.of(), headingX, headingZ), List.of(), List.of(), List.of(), lit, true);
        }
        int[] way = heading(self, arena);
        Drift drift = planDrift(arena, self, way[0], way[1], steps(self));
        GridPos end = drift.end(self.getGridPos());
        List<GridPos> burn = aura(arena, self, end);
        if (isFrozen(self)) {
            // It burns free. Whoever is still pressed against it when the ice goes is caught
            // in the same fire as the ring it will carry off with it.
            for (GridPos tile : aura(arena, self, self.getGridPos())) {
                if (!burn.contains(tile)) burn.add(tile);
            }
        }
        List<GridPos> catching = phase(self) == 3 && burnWait <= 0
            ? catching(arena, braziers, rings) : List.of();
        // A tile two of its fires would take on the same turn is lit once: for good if a
        // ring takes it, and otherwise by the trail.
        List<GridPos> trail = drift.path().isEmpty() ? new ArrayList<>() : trail(arena, self, drift, braziers);
        trail.removeAll(catching);
        lit.removeAll(catching);
        lit.removeAll(trail);
        return new Plan(drift, trail, burn, catching, lit, false);
    }

    /**
     * The way it sets off on its next move. For two thirds of the fight that is the heading
     * it was left on. In its last third it turns to face the nearest player every time.
     */
    private int[] heading(CombatEntity self, GridArena arena) {
        if (phase(self) == 3) {
            GridPos prey = prey(arena, self);
            int[] aimed = prey == null ? null : openWayAt(arena, self, prey);
            if (aimed != null) return aimed;
        }
        return new int[]{headingX, headingZ};
    }

    private int steps(CombatEntity self) {
        // The engine reports no speed at all for something frozen, which is right for the
        // freeze and wrong for planning the turn that follows it.
        int base = self.isFrozen() ? lastSpeed : Math.max(1, self.getMoveSpeed());
        // Read off its health, not off a flag that only turns on its own turn: the tiles
        // the player is shown between turns have to be the ones it will really cross.
        return base + (phase(self) == 3 ? 1 : 0);
    }

    /**
     * Called by the engine once a crystal is really on the board, which makes it the one honest
     * place to count it. A launch that was only planned, or that found no room, never arrives
     * here and so never uses up a place in the pattern.
     */
    @Override
    public void registerSpawnedProjectile(int entityId) {
        super.registerSpawnedProjectile(entityId);
        CombatEntity crystal = lastArena == null ? null : occupant(lastArena, entityId);
        boolean ice = crystal == null ? isIce(crystalsLaunched) : ICE_CRYSTAL.equals(crystal.getProjectileType());
        crystalsLaunched++;
        if (crystal == null) return;

        if (ice) {
            // An Ice Crystal is the key to the fight, so it must not be lost to a wall the
            // way a straight shot would be: it goes after a player until it is struck or lands.
            crystal.setAiOverrideKey("seeking_projectile");
        } else {
            // The engine aims a projectile down a row or a column. A crystal thrown from a
            // corner of the room would cross it along the wall and meet nobody, so it is
            // aimed again here, on the nearest of all eight ways to whoever it is meant for.
            GridPos mark = nearest(players(lastArena), crystal.getGridPos());
            int[] way = mark == null ? null : aimAt(crystal.getGridPos(), 1, 1, mark);
            if (way != null) {
                crystal.setProjectileDirX(way[0]);
                crystal.setProjectileDirZ(way[1]);
            }
            crystal.setAiInstance(CrystalFlight.INSTANCE);
        }
        // Named for what it is. Its type id is the Aether's entity, which reads well enough,
        // but a name it is given is what a hover shows whatever carries it across the grid.
        crystal.setBossDisplayName(ice ? "Ice Crystal" : "Fire Crystal");
        if (crystal.getMobEntity() != null) CrystalLook.dress(crystal, ice);
        say(crystal, lastArena, ice
            ? "§b✦ The Sun Spirit looses an Ice Crystal! §7Strike it from the far side to send it back."
            : "§6✦ The Sun Spirit looses a Fire Crystal!");
    }

    /**
     * A Fire Minion does nothing on the turn it appears. The engine gives whatever joins a
     * fight its turn at once, and a minion born beside a frozen Sun Spirit would be on
     * whoever had just walked up to it before they had so much as seen it. Its first turn
     * goes on finding its feet instead, and the players' next turn has it in plain sight.
     */
    @Override
    public void registerSpawnedMinion(int entityId) {
        super.registerSpawnedMinion(entityId);
        CombatEntity minion = lastArena == null ? null : occupant(lastArena, entityId);
        if (minion == null) return;
        minion.setStunned(true);
        say(minion, lastArena, MINION_LINE);
    }

    // Asks for a live entity before going near the fight manager, so a boss with nothing
    // behind it (a turn worked out on a bare grid) never loads it.
    private static void say(CombatEntity anyone, GridArena arena, String line) {
        if (anyone.getMobEntity() != null) Stage.say(anyone, arena, line);
    }

    /**
     * Turn an eighth of a circle. It still does not chase: it only ever bounces and veers.
     * But of the two ways it could veer it takes the one that points more nearly at its
     * target, so over a few turns it comes round to whoever it is fighting instead of
     * wandering the far half of the room. A coin decides when neither is nearer.
     */
    private void veer(CombatEntity self, GridPos from, GridPos target) {
        int at = 0;
        for (int i = 0; i < COMPASS.length; i++) {
            if (COMPASS[i][0] == headingX && COMPASS[i][1] == headingZ) at = i;
        }
        int[] next = COMPASS[Math.floorMod(at + veerSide(COMPASS[Math.floorMod(at - 1, COMPASS.length)],
            COMPASS[Math.floorMod(at + 1, COMPASS.length)], self, from, target), COMPASS.length)];
        headingX = next[0];
        headingZ = next[1];
    }

    /** -1 to take {@code left}, +1 to take {@code right}: whichever leans more toward the target. */
    private int veerSide(int[] left, int[] right, CombatEntity self, GridPos from, GridPos target) {
        boolean coin = rng.nextBoolean();
        if (target == null || from == null) return coin ? 1 : -1;
        int lean = leaning(left, right, from, self.getSizeX(), self.getSizeZ(), target);
        return lean != 0 ? lean : (coin ? 1 : -1);
    }

    private EnemyAction launchCrystal(GridArena arena, CombatEntity self, Drift drift, GridPos target) {
        boolean ice = isIce(crystalsLaunched);
        GridPos spot = crystalSpot(arena, self, drift, target, !ice);
        if (spot == null) return null;
        int power = self.getAttackPower();
        return new EnemyAction.SpawnProjectile(
            ice ? ICE_CRYSTAL_ENTITY : FIRE_CRYSTAL_ENTITY,
            List.of(spot), List.<int[]>of(toward(spot, target)),
            ice ? iceCrystalHp(self.getMaxHp()) : 1,
            ice ? iceCrystalDamage(power) : fireCrystalDamage(power),
            0,
            ice ? ICE_CRYSTAL : FIRE_CRYSTAL);
    }

    /**
     * Mark the lanes its next turn will light: one brazier's turn from the second third of
     * its health down, two in the last. The braziers take it in turn round the room, and a
     * dark one loses its turn: the turn is not handed on to the next, so a brazier that
     * has been silenced is a lane the room does not get. Marked now, as its turn ends, so
     * that they are on the board for the whole of the players' turn.
     */
    private void markLanes(GridArena arena, CombatEntity self, List<GridPos> braziers) {
        List<Lane> marked = new ArrayList<>();
        int turns = Math.min(phase(self) - 1, braziers.size());
        for (int i = 0; i < turns; i++) {
            GridPos brazier = braziers.get(Math.floorMod(nextBrazier, braziers.size()));
            nextBrazier = Math.floorMod(nextBrazier + 1, braziers.size());
            if (!lit(arena, brazier)) continue;
            GridPos mark = nearest(players(arena), brazier);
            if (mark == null) continue;
            int[] way = laneWay(arena, brazier, mark);
            marked.add(new Lane(brazier, way[0], way[1]));
        }
        lanes = marked;
    }

    /**
     * The fire the freezes since its last turn have driven back: the innermost burning ring
     * for each, asked to burn for one turn more, which is how a lit tile is put out with the
     * floor it was lit on given back. Null when there was nothing to drive back.
     */
    private EnemyAction pushFireBack(CombatEntity self, GridArena arena) {
        int owed = self.getAiMemory(RINGS_OWED, 0);
        if (owed <= 0) return null;
        self.setAiMemory(RINGS_OWED, 0);
        List<GridPos> out = new ArrayList<>();
        while (owed-- > 0 && rings > 0) {
            rings--;
            for (GridPos tile : ring(arena, rings)) {
                GridTile ground = arena.getTile(tile);
                if (ground != null && ground.getType().isFlames()) out.add(tile);
            }
        }
        if (out.isEmpty()) return null;
        // The fire takes a breath before it comes on again.
        burnWait = RING_EVERY - 1;
        say(self, arena, PUSHED_LINE);
        return new EnemyAction.CreateTerrain(out, TileType.FIRE, 1);
    }

    /** One Fire Minion beside it, on the side nearest the player, or null when it is hemmed in. */
    private static EnemyAction.SummonMinions summonMinion(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (GridPos tile : ring(arena, self.getGridPos(), self.getSizeX(), self.getSizeZ())) {
            GridTile ground = arena.getTile(tile);
            if (ground == null || !ground.isWalkable() || arena.isOccupied(tile)) continue;
            int dist = playerPos == null ? 0 : tile.manhattanDistance(playerPos);
            if (dist < bestDist) {
                bestDist = dist;
                best = tile;
            }
        }
        if (best == null) return null;
        return new EnemyAction.SummonMinions(AetherMobs.FIRE_MINION, 1, List.of(best),
            minionHp(self.getMaxHp()),
            Math.max(2, self.getAttackPower() * 2 / 3),
            Math.max(0, self.getDefense() / 2));
    }

    /** Keep the flame on each brazier in step with whether it will be lit when the players next look. */
    private void showBraziers(CombatEntity self, GridArena arena, List<GridPos> braziers) {
        if (self.getMobEntity() == null || braziers.isEmpty()) return;
        Room.showBraziers(self.getMobEntity(), arena, braziers);
    }

    // ================================================================
    // Pure rules (unit-tested)
    // ================================================================

    /** One turn of travel: the tiles its anchor steps through, and the heading it is left on. */
    record Drift(List<GridPos> path, int headingX, int headingZ) {
        GridPos end(GridPos start) {
            return path.isEmpty() ? start : path.get(path.size() - 1);
        }
    }

    /** Which third of its health it is in: 1 above two thirds, 2 from there down, 3 at a third or less. */
    static int phase(int hp, int maxHp) {
        if (hp * 3 <= maxHp) return 3;
        if (hp * 3 <= maxHp * 2) return 2;
        return 1;
    }

    static int phase(CombatEntity self) {
        return phase(self.getCurrentHp(), self.getMaxHp());
    }

    static boolean isIce(int crystalsLaunchedSoFar) {
        return crystalsLaunchedSoFar % CRYSTALS_PER_CYCLE == CRYSTALS_PER_CYCLE - 1;
    }

    // In the Aether a Fire Crystal hits half again as hard as the contact burn. Not here: a
    // crystal is not the boss, so the ceiling the engine puts on a single boss hit never
    // sees it, and at half again it would take most of a player's health in one burst. It
    // hits as hard as the burn does. The Ice Crystal keeps its proportion, seven tenths.

    static int fireCrystalDamage(int attackPower) {
        return Math.max(1, attackPower);
    }

    static int iceCrystalDamage(int attackPower) {
        return Math.max(1, attackPower * 7 / 10);
    }

    /** Sturdy enough that a stray splash does not shatter the one crystal the party is waiting for. */
    static int iceCrystalHp(int bossMaxHp) {
        return Math.max(6, bossMaxHp / 12);
    }

    static int deflectDamage(int crystalPower, int bossMaxHp) {
        return Math.max(crystalPower, bossMaxHp / DEFLECT_HP_SHARE);
    }

    /**
     * A Fire Minion's health: a fortieth of its master's, which at the health the Sun Spirit
     * has in its own dungeon is what the Fire Minions of the floors below it have.
     */
    static int minionHp(int bossMaxHp) {
        return Math.max(6, bossMaxHp / 40);
    }

    /**
     * The scale that sits a Sun Spirit inside {@code tiles} tiles. Its hitbox is two and a half
     * blocks across, and this is also what keeps its contact burn off its neighbours: that
     * burn reaches a block and a half less than its hitbox, so at this size it is a column
     * well inside its own footprint.
     */
    static double fitScale(int tiles) {
        return Math.min(1.0, 0.9 * Math.max(1, tiles) / 2.5);
    }

    /**
     * Ground it will cross. It keeps out of liquid and off anything that would sink it into
     * the floor, and frozen floor turns it like a wall.
     */
    static boolean crossable(TileType type) {
        if (type == null || !type.walkable || type == FROZEN_FLOOR) return false;
        return switch (type) {
            case WATER, LAVA, LOW_GROUND, POWDER_SNOW -> false;
            default -> true;
        };
    }

    /** Whether its whole body fits with its anchor at {@code anchor}: in bounds, open ground, nobody there. */
    static boolean fits(GridArena arena, CombatEntity self, GridPos anchor) {
        for (GridPos tile : GridArena.getOccupiedTiles(anchor, self.getSizeX(), self.getSizeZ())) {
            if (!arena.isInBounds(tile)) return false;
            GridTile ground = arena.getTile(tile);
            if (ground == null || !crossable(ground.getType())) return false;
            if (players(arena).contains(tile)) return false;
            CombatEntity other = arena.getOccupant(tile);
            if (other != null && other != self && !other.isBackgroundBoss()) return false;
        }
        return true;
    }

    /** A step is clear when the tile ahead is, and for a diagonal, when it does not cut a corner to get there. */
    static boolean canStep(GridArena arena, CombatEntity self, GridPos at, int hx, int hz) {
        if (hx != 0 && hz != 0) {
            if (!fits(arena, self, new GridPos(at.x() + hx, at.z()))) return false;
            if (!fits(arena, self, new GridPos(at.x(), at.z() + hz))) return false;
        }
        return fits(arena, self, new GridPos(at.x() + hx, at.z() + hz));
    }

    /**
     * The heading it leaves {@code at} on: the one it has if the way is clear, otherwise that
     * heading reflected off whatever it met. A wall across one axis flips that axis, a corner
     * flips both. Null when it is boxed in on every side it could turn to.
     */
    static int[] bounce(GridArena arena, CombatEntity self, GridPos at, int hx, int hz) {
        if (canStep(arena, self, at, hx, hz)) return new int[]{hx, hz};
        int[][] tries;
        if (hx == 0 || hz == 0) {
            tries = new int[][]{{-hx, -hz}};
        } else {
            boolean wallX = !fits(arena, self, new GridPos(at.x() + hx, at.z()));
            boolean wallZ = !fits(arena, self, new GridPos(at.x(), at.z() + hz));
            if (wallX && !wallZ) {
                tries = new int[][]{{-hx, hz}, {-hx, -hz}, {hx, -hz}};
            } else if (wallZ && !wallX) {
                tries = new int[][]{{hx, -hz}, {-hx, -hz}, {-hx, hz}};
            } else {
                tries = new int[][]{{-hx, -hz}, {-hx, hz}, {hx, -hz}};
            }
        }
        for (int[] way : tries) {
            if (canStep(arena, self, at, way[0], way[1])) return way;
        }
        return null;
    }

    /** Plan {@code steps} tiles of travel from where it stands. Reads the board and changes nothing. */
    static Drift planDrift(GridArena arena, CombatEntity self, int hx, int hz, int steps) {
        List<GridPos> path = new ArrayList<>();
        GridPos at = self.getGridPos();
        if (hx == 0 && hz == 0) return new Drift(path, hx, hz);
        for (int i = 0; i < steps; i++) {
            int[] way = bounce(arena, self, at, hx, hz);
            if (way == null) break;
            hx = way[0];
            hz = way[1];
            at = new GridPos(at.x() + hx, at.z() + hz);
            path.add(at);
        }
        return new Drift(path, hx, hz);
    }

    /**
     * Which of the eight ways points most nearly from a body anchored at {@code from} at
     * {@code target}. Null when the target is the middle of the body itself.
     */
    static int[] aimAt(GridPos from, int sizeX, int sizeZ, GridPos target) {
        // Doubled, so the middle of an even-sized body is still a whole number.
        int tx = target.x() * 2 - (from.x() * 2 + sizeX - 1);
        int tz = target.z() * 2 - (from.z() * 2 + sizeZ - 1);
        if (tx == 0 && tz == 0) return null;
        int[] best = null;
        double bestDot = Double.NEGATIVE_INFINITY;
        for (int[] way : COMPASS) {
            double dot = (way[0] * tx + way[1] * tz) / Math.hypot(way[0], way[1]);
            if (dot > bestDot + 1e-9) {
                bestDot = dot;
                best = way;
            }
        }
        return new int[]{best[0], best[1]};
    }

    /**
     * The way it sets off at {@code target} in its last third: of the eight, the one that
     * points most nearly at them and that it can move on at all, a bounce counting as a
     * move. So frost laid between it and a wall turns it aside, and does not hold it there
     * for as long as the player stands still behind the frost. Null with the target in the
     * middle of its own body.
     */
    static int[] openWayAt(GridArena arena, CombatEntity self, GridPos target) {
        GridPos from = self.getGridPos();
        int tx = target.x() * 2 - (from.x() * 2 + self.getSizeX() - 1);
        int tz = target.z() * 2 - (from.z() * 2 + self.getSizeZ() - 1);
        if (tx == 0 && tz == 0) return null;
        int[] straight = null;
        int[] open = null;
        double straightDot = Double.NEGATIVE_INFINITY;
        double openDot = Double.NEGATIVE_INFINITY;
        for (int[] way : COMPASS) {
            double dot = (way[0] * tx + way[1] * tz) / Math.hypot(way[0], way[1]);
            if (dot > straightDot + 1e-9) {
                straightDot = dot;
                straight = way;
            }
            if (dot > openDot + 1e-9 && bounce(arena, self, from, way[0], way[1]) != null) {
                openDot = dot;
                open = way;
            }
        }
        // Boxed in on every side, it keeps facing them, and moves the turn something gives.
        int[] chosen = open != null ? open : straight;
        return new int[]{chosen[0], chosen[1]};
    }

    /**
     * Which of two headings points more nearly from a body anchored at {@code from} toward
     * {@code target}: -1 for {@code left}, +1 for {@code right}, 0 when it makes no difference.
     */
    static int leaning(int[] left, int[] right, GridPos from, int sizeX, int sizeZ, GridPos target) {
        int tx = target.x() * 2 - (from.x() * 2 + sizeX - 1);
        int tz = target.z() * 2 - (from.z() * 2 + sizeZ - 1);
        double l = (left[0] * tx + left[1] * tz) / Math.hypot(left[0], left[1]);
        double r = (right[0] * tx + right[1] * tz) / Math.hypot(right[0], right[1]);
        if (Math.abs(l - r) < 1e-9) return 0;
        return r > l ? 1 : -1;
    }

    /** Every tile its body covers over the turn, from where it starts to where it stops. */
    static Set<GridPos> swept(CombatEntity self, Drift drift) {
        Set<GridPos> tiles = new LinkedHashSet<>(GridArena.getOccupiedTiles(self));
        for (GridPos step : drift.path()) {
            tiles.addAll(GridArena.getOccupiedTiles(step, self.getSizeX(), self.getSizeZ()));
        }
        return tiles;
    }

    /**
     * Floor its fire can be laid on: plain floor, and not squarely beside a brazier. Not floor
     * already burning either, since lighting a tile again sets how long it burns again, and
     * a ring lit for good would be put out by the trail crossing it.
     */
    static boolean takesFire(GridArena arena, List<GridPos> braziers, GridPos tile) {
        GridTile ground = arena.getTile(tile);
        if (ground == null || ground.getType() != TileType.NORMAL) return false;
        for (GridPos brazier : braziers) {
            if (brazier.manhattanDistance(tile) == 1) return false;
        }
        return true;
    }

    /** The tiles it leaves burning: everything it covered that takes fire, less where it comes to rest. */
    static List<GridPos> trail(GridArena arena, CombatEntity self, Drift drift, List<GridPos> braziers) {
        Set<GridPos> tiles = swept(self, drift);
        tiles.removeAll(GridArena.getOccupiedTiles(
            drift.end(self.getGridPos()), self.getSizeX(), self.getSizeZ()));
        List<GridPos> lit = new ArrayList<>();
        for (GridPos tile : tiles) {
            if (takesFire(arena, braziers, tile)) lit.add(tile);
        }
        return lit;
    }

    /** The in-bounds tiles one step out from a footprint, corners included. */
    static List<GridPos> ring(GridArena arena, GridPos anchor, int sizeX, int sizeZ) {
        List<GridPos> tiles = new ArrayList<>();
        for (int dx = -1; dx <= sizeX; dx++) {
            for (int dz = -1; dz <= sizeZ; dz++) {
                if (dx >= 0 && dx < sizeX && dz >= 0 && dz < sizeZ) continue;
                GridPos tile = new GridPos(anchor.x() + dx, anchor.z() + dz);
                if (arena.isInBounds(tile)) tiles.add(tile);
            }
        }
        return tiles;
    }

    /**
     * The ring its aura burns once it has stopped at {@code anchor}. Tiles holding its own
     * crystals and minions are left out: an area hit also lands on every enemy standing in
     * it, and the aura would otherwise shatter the crystal it had just let go of.
     */
    static List<GridPos> aura(GridArena arena, CombatEntity self, GridPos anchor) {
        List<GridPos> tiles = new ArrayList<>();
        for (GridPos tile : ring(arena, anchor, self.getSizeX(), self.getSizeZ())) {
            CombatEntity there = arena.getOccupant(tile);
            if (there != null && there != self && !there.isAlly()) continue;
            tiles.add(tile);
        }
        return tiles;
    }

    // ── The room ──

    /** Everyone in the fight, the one whose turn it was first. */
    static List<GridPos> players(GridArena arena) {
        List<GridPos> all = new ArrayList<>();
        if (arena.getPlayerGridPos() != null) all.add(arena.getPlayerGridPos());
        for (GridPos member : arena.getAllPlayerGridPositions()) {
            if (member != null && !all.contains(member)) all.add(member);
        }
        return all;
    }

    /** Whichever of {@code tiles} is fewest steps from {@code from}, diagonals counted as one. Null for none. */
    static GridPos nearest(List<GridPos> tiles, GridPos from) {
        GridPos best = null;
        for (GridPos tile : tiles) {
            if (best == null || from.chebyshevDistanceTo(tile) < from.chebyshevDistanceTo(best)) best = tile;
        }
        return best;
    }

    /** The player nearest its body, or null with nobody on the floor. */
    static GridPos prey(GridArena arena, CombatEntity self) {
        GridPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (GridPos player : players(arena)) {
            int dist = self.minChebyshevDistanceTo(player);
            if (dist < bestDist) {
                bestDist = dist;
                best = player;
            }
        }
        return best;
    }

    /** How far the nearest player is from {@code tile}, diagonals counted as one. */
    private static int clearance(GridArena arena, GridPos tile) {
        int least = Integer.MAX_VALUE;
        for (GridPos player : players(arena)) least = Math.min(least, tile.chebyshevDistanceTo(player));
        return least;
    }

    /** Whether {@code target} is dead ahead of {@code from} on one of the eight ways a crystal can fly. */
    static boolean inLine(GridPos from, GridPos target) {
        int dx = Math.abs(target.x() - from.x());
        int dz = Math.abs(target.z() - from.z());
        return dx == 0 || dz == 0 || dx == dz;
    }

    /** A tile a crystal can be put on: floor nothing is standing on, far enough from everyone, not spoken for. */
    private static boolean fairSpot(GridArena arena, GridPos tile, Set<GridPos> taken) {
        if (!arena.isInBounds(tile) || taken.contains(tile) || arena.isOccupied(tile)) return false;
        GridTile ground = arena.getTile(tile);
        if (ground == null || !ground.isWalkable()) return false;
        return clearance(arena, tile) >= HEAD_START;
    }

    /**
     * Lower is better. A Fire Crystal is put near its target and dead in line with them.
     * An Ice Crystal is put as far from them as it can be: thrown from beside them it would
     * arrive with the Sun Spirit straight behind it, and any strike at all would send it
     * home. Thrown over its shoulder it has to come round, by which time the Sun Spirit
     * has moved on, and the shot is one that has to be made.
     */
    private static int spotScore(GridPos tile, GridPos target, boolean flies) {
        if (target == null) return 0;
        if (!flies) return -tile.manhattanDistance(target);
        return tile.manhattanDistance(target) + (inLine(tile, target) ? 0 : OFF_LINE);
    }

    /**
     * Where one of its own crystals appears: a free tile within two of where it will stop,
     * never on ground it is about to cross and never within {@link #HEAD_START} of anyone.
     * A crystal that flies straight is put dead in line with its target where it can be.
     * Null when there is no such tile, and then no crystal is thrown.
     */
    static GridPos crystalSpot(GridArena arena, CombatEntity self, Drift drift, GridPos target, boolean flies) {
        GridPos end = drift.end(self.getGridPos());
        Set<GridPos> body = swept(self, drift);
        GridPos best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int dx = -CRYSTAL_REACH; dx < self.getSizeX() + CRYSTAL_REACH; dx++) {
            for (int dz = -CRYSTAL_REACH; dz < self.getSizeZ() + CRYSTAL_REACH; dz++) {
                GridPos tile = new GridPos(end.x() + dx, end.z() + dz);
                if (!fairSpot(arena, tile, body)) continue;
                int score = spotScore(tile, target, flies);
                if (score < bestScore) {
                    bestScore = score;
                    best = tile;
                }
            }
        }
        return best;
    }

    // ── Lanes ──

    /** A line of fire a brazier has marked: where it starts, and the way it runs. */
    record Lane(GridPos brazier, int dx, int dz) {}

    /**
     * The way a brazier's lane runs: of the eight, the one whose line to the wall comes
     * closest to {@code target}, and of two that come as close, the one that points more
     * nearly at them.
     */
    static int[] laneWay(GridArena arena, GridPos brazier, GridPos target) {
        int[] best = COMPASS[0];
        int bestGap = Integer.MAX_VALUE;
        double bestDot = Double.NEGATIVE_INFINITY;
        for (int[] way : COMPASS) {
            int gap = Integer.MAX_VALUE;
            GridPos at = new GridPos(brazier.x() + way[0], brazier.z() + way[1]);
            while (arena.isInBounds(at)) {
                gap = Math.min(gap, at.chebyshevDistanceTo(target));
                at = new GridPos(at.x() + way[0], at.z() + way[1]);
            }
            if (gap == Integer.MAX_VALUE) continue;
            double dot = (way[0] * (target.x() - brazier.x()) + way[1] * (target.z() - brazier.z()))
                / Math.hypot(way[0], way[1]);
            if (gap < bestGap || (gap == bestGap && dot > bestDot + 1e-9)) {
                bestGap = gap;
                bestDot = dot;
                best = way;
            }
        }
        return new int[]{best[0], best[1]};
    }

    /** The floor a lane runs over, from its brazier until it meets a wall, a block or frost. */
    static List<GridPos> laneTiles(GridArena arena, Lane lane) {
        List<GridPos> tiles = new ArrayList<>();
        if (lane.dx() == 0 && lane.dz() == 0) return tiles;
        GridPos at = new GridPos(lane.brazier().x() + lane.dx(), lane.brazier().z() + lane.dz());
        while (arena.isInBounds(at)) {
            GridTile ground = arena.getTile(at);
            if (ground == null || !ground.isWalkable() || ground.getType() == FROZEN_FLOOR) break;
            tiles.add(at);
            at = new GridPos(at.x() + lane.dx(), at.z() + lane.dz());
        }
        return tiles;
    }

    /**
     * The tiles these lanes set alight, on the board as it stands. A lane whose brazier is
     * dark, or is no longer there, lights nothing. Floor already burning is left burning
     * as it is, and the floor squarely beside a brazier is never lit.
     */
    static List<GridPos> laneFire(GridArena arena, List<GridPos> braziers, List<Lane> lanes) {
        List<GridPos> fire = new ArrayList<>();
        for (Lane lane : lanes) {
            if (!braziers.contains(lane.brazier()) || !lit(arena, lane.brazier())) continue;
            for (GridPos tile : laneTiles(arena, lane)) {
                if (takesFire(arena, braziers, tile) && !fire.contains(tile)) fire.add(tile);
            }
        }
        return fire;
    }

    static boolean isFrost(GridArena arena, GridPos tile) {
        GridTile ground = arena.isInBounds(tile) ? arena.getTile(tile) : null;
        return ground != null && ground.getType() == FROZEN_FLOOR;
    }

    /** A brazier burns unless frost is on a tile touching it. */
    static boolean lit(GridArena arena, GridPos brazier) {
        return !frostBeside(arena, brazier, 0);
    }

    /** Whether frozen floor with more than {@code turns} left to it touches {@code brazier}. */
    static boolean frostBeside(GridArena arena, GridPos brazier, int turns) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                GridPos tile = new GridPos(brazier.x() + dx, brazier.z() + dz);
                if (isFrost(arena, tile) && arena.getTile(tile).getTurnsRemaining() > turns) return true;
            }
        }
        return false;
    }

    /**
     * The floor a struck Ice Crystal freezes: every tile within one of where it was struck
     * that is floor, burning or not. Never the tiles under the Sun Spirit or touching it,
     * which are too hot to freeze. Frost under it would hold it where it stands, and frost
     * against it is how it could be walled into a corner and kept there: a crystal struck
     * on the one tile diagonal to it would shut both ways out at once, and the next crystal
     * struck there would shut them again as the first patch melted. With the floor round
     * it always clear, frost turns it and can never box it in.
     */
    static List<GridPos> frostPatch(GridArena arena, CombatEntity boss, GridPos struck) {
        List<GridPos> patch = new ArrayList<>();
        Set<GridPos> under = new HashSet<>();
        if (boss != null) {
            under.addAll(GridArena.getOccupiedTiles(boss));
            under.addAll(ring(arena, boss.getGridPos(), boss.getSizeX(), boss.getSizeZ()));
        }
        for (int dx = -FROST_REACH; dx <= FROST_REACH; dx++) {
            for (int dz = -FROST_REACH; dz <= FROST_REACH; dz++) {
                GridPos tile = new GridPos(struck.x() + dx, struck.z() + dz);
                if (!arena.isInBounds(tile) || under.contains(tile)) continue;
                GridTile ground = arena.getTile(tile);
                if (ground == null) continue;
                TileType type = ground.getType();
                if (type == TileType.NORMAL || type.isFlames() || type == TileType.EMBER
                        || type == FROZEN_FLOOR) {
                    patch.add(tile);
                }
            }
        }
        return patch;
    }

    /** Rings the fire may take in this room: four, or fewer where that would leave less than {@link #FLOOR_KEPT}. */
    static int maxRings(GridArena arena) {
        int spare = (Math.min(arena.getWidth(), arena.getHeight()) - FLOOR_KEPT) / 2;
        return Math.max(0, Math.min(MAX_RINGS, spare));
    }

    /** The tiles of one ring of the room, counted in from its walls: 0 is the floor against them. */
    static List<GridPos> ring(GridArena arena, int depth) {
        List<GridPos> tiles = new ArrayList<>();
        int w = arena.getWidth();
        int h = arena.getHeight();
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                if (Math.min(Math.min(x, z), Math.min(w - 1 - x, h - 1 - z)) != depth) continue;
                if (arena.isInBounds(x, z)) tiles.add(new GridPos(x, z));
            }
        }
        return tiles;
    }

    /**
     * The floor that catches when the fire next comes on, with {@code rings} rings alight
     * already: the next ring in, and any floor in the rings behind it that is not burning
     * any more, which is what a patch of frost leaves when it melts.
     */
    static List<GridPos> catching(GridArena arena, List<GridPos> braziers, int rings) {
        List<GridPos> tiles = new ArrayList<>();
        int deepest = Math.min(rings, maxRings(arena) - 1);
        for (int depth = 0; depth <= deepest; depth++) {
            for (GridPos tile : ring(arena, depth)) {
                if (takesFire(arena, braziers, tile)) tiles.add(tile);
            }
        }
        return tiles;
    }

    private static int[] toward(GridPos from, GridPos to) {
        int[] way = to == null ? null : aimAt(from, 1, 1, to);
        return way != null ? way : new int[]{0, 1};
    }

    private static CombatEntity occupant(GridArena arena, int entityId) {
        for (CombatEntity e : arena.getOccupants().values()) {
            if (e.getEntityId() == entityId) return e;
        }
        return null;
    }

    // ================================================================
    // Frozen
    // ================================================================

    static boolean isFrozen(CombatEntity boss) {
        return boss.getAiMemory(FROZEN, 0) == 1;
    }

    /** Frozen, with a turn of it still to sit out. Frozen with none left, its next turn is the thaw. */
    static boolean sitsOut(CombatEntity boss) {
        return isFrozen(boss) && boss.getAiMemory(FROZEN_TURNS, 0) > 0;
    }

    /**
     * Freeze the boss for {@code turns} of its own turns: guard down, held still, one Fire
     * Minion owed and one ring of fire owed back, both paid on its next turn. Freezing it
     * again while it is frozen never shortens what is left.
     */
    static void freeze(CombatEntity boss, int turns) {
        boss.setAiMemory(FROZEN, 1);
        boss.setAiMemory(FROZEN_TURNS, Math.max(turns, boss.getAiMemory(FROZEN_TURNS, 0)));
        boss.setAiMemory(MINIONS_OWED, boss.getAiMemory(MINIONS_OWED, 0) + 1);
        boss.setAiMemory(RINGS_OWED, boss.getAiMemory(RINGS_OWED, 0) + 1);
        // The engine's own flag as well: it is what shows "Frozen" on the panel, and what
        // holds the boss still on the turns the engine moves a mob without asking its AI.
        boss.setFrozen(true);
        boss.setDamageImmune(false, null);
        if (boss.getMobEntity() != null) RealEntity.showFrozen(boss, true);
    }

    static void thaw(CombatEntity boss) {
        boss.setAiMemory(FROZEN, 0);
        boss.setAiMemory(FROZEN_TURNS, 0);
        // A minion it had no room for is not carried over to the next freeze.
        boss.setAiMemory(MINIONS_OWED, 0);
        boss.setFrozen(false);
        boss.setDamageImmune(true, GUARD_HINT);
        if (boss.getMobEntity() != null) RealEntity.showFrozen(boss, false);
    }

    /** The flight of a struck Ice Crystal: the way it went, the tiles it crossed, and whether it found the Sun Spirit. */
    record Shot(int dx, int dz, List<GridPos> path, boolean lands) {
        GridPos end(GridPos struck) {
            return path.isEmpty() ? struck : path.get(path.size() - 1);
        }
    }

    static Shot shot(GridArena arena, CombatEntity boss, GridPos struck, GridPos striker) {
        return shot(arena, boss.getGridPos(), boss.getSizeX(), boss.getSizeZ(), struck, striker);
    }

    /**
     * Where an Ice Crystal struck at {@code struck} by someone standing at {@code striker}
     * goes: straight away from them, on the nearest of the eight ways, over frost and past
     * anything standing on the floor, until a wall or a block stops it. It lands on the
     * first tile of that line that is part of the body anchored at {@code anchor} or within
     * one tile of it, however far off that is. The tile it was struck on does not count:
     * a crystal lying against the Sun Spirit still has to be sent the right way.
     */
    static Shot shot(GridArena arena, GridPos anchor, int sizeX, int sizeZ, GridPos struck, GridPos striker) {
        int[] way = striker == null ? null : aimAt(striker, 1, 1, struck);
        List<GridPos> path = new ArrayList<>();
        if (way == null) return new Shot(0, 0, path, false);
        GridPos at = new GridPos(struck.x() + way[0], struck.z() + way[1]);
        while (arena.isInBounds(at)) {
            GridTile ground = arena.getTile(at);
            if (ground == null || !ground.isWalkable()) break;
            path.add(at);
            if (gap(anchor, sizeX, sizeZ, at) <= 1) return new Shot(way[0], way[1], path, true);
            at = new GridPos(at.x() + way[0], at.z() + way[1]);
        }
        return new Shot(way[0], way[1], path, false);
    }

    /** How far {@code tile} is from a body anchored at {@code anchor}, diagonals counted as one. Nothing, inside it. */
    static int gap(GridPos anchor, int sizeX, int sizeZ, GridPos tile) {
        int dx = Math.max(0, Math.max(anchor.x() - tile.x(), tile.x() - (anchor.x() + sizeX - 1)));
        int dz = Math.max(0, Math.max(anchor.z() - tile.z(), tile.z() - (anchor.z() + sizeZ - 1)));
        return Math.max(dx, dz);
    }

    /**
     * What an Ice Crystal knocked back into the boss does, once its damage has landed: the
     * freeze, and the lines the party is told.
     */
    static List<String> struck(CombatEntity boss, int dealt) {
        List<String> lines = new ArrayList<>();
        String name = boss.getDisplayName();
        if (!boss.isAlive()) {
            lines.add("§b✦ The Ice Crystal slams back into " + name + " for " + dealt + "!");
            String words = lastWords(boss);
            if (words != null) lines.add(words);
            return lines;
        }
        int turns = FREEZE_TURNS;
        freeze(boss, turns);
        lines.add("§b✦ The Ice Crystal slams back into " + name + " for " + dealt
            + "! §f§lFROZEN§r§b for " + turns + (turns == 1 ? " turn" : " turns")
            + ". Its guard is down!");
        if (boss.getAiMemory(CLUE_GIVEN, 0) == 0) {
            boss.setAiMemory(CLUE_GIVEN, 1);
            lines.add(FROZEN_CLUE);
        }
        return lines;
    }

    /**
     * The Sun Spirit's dying line, once, or null for anything else and for every call after
     * the first. The engine asks through {@link #getLastWords} as it announces the death, and
     * the Ice Crystal asks when its own hit is the one that ends the fight. Whichever asks
     * first is given it.
     */
    public static String lastWords(CombatEntity dying) {
        if (dying == null || !BOSS_KEY.equals(dying.getAiKey())) return null;
        if (dying.getAiMemory(SPOKE, 0) == 1) return null;
        dying.setAiMemory(SPOKE, 1);
        return LAST_WORDS;
    }

    // ================================================================
    // Crystals
    // ================================================================

    static boolean isSpent(CombatEntity crystal) {
        return crystal.getAiMemory(SPENT, 0) == 1;
    }

    /**
     * How a Fire Crystal flies, its own or a brazier's.
     *
     * <p>The engine's straight projectile stops and waits behind anything standing in its way,
     * for as long as that thing stands there. One crystal a fight never showed it. Three or
     * four a turn did: they queued up behind the Sun Spirit, behind its minions and behind
     * each other until the floor was full of crystals going nowhere and it could not move for
     * them. So this one never waits. It bursts on a player, on a wall or on a brazier. On
     * anything else in its way it breaks, harmlessly, and after {@link #FIRE_CRYSTAL_TURNS}
     * turns in the air it burns out the same way, so that what is on the board is always
     * something still coming.
     */
    static final class CrystalFlight implements EnemyAI {
        static final CrystalFlight INSTANCE = new CrystalFlight();
        /** Tiles a turn, the pace every projectile in a fight keeps. */
        static final int SPEED = 2;

        /** Where one turn takes it: the tiles it crosses, whether it ends there, where, and whether without a burst. */
        record Flown(List<GridPos> path, boolean ends, GridPos at, boolean spent) {}

        @Override
        public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
            Flown flown = fly(self, arena, playerPos);
            self.setAiMemory(TURNS_FLOWN, self.getAiMemory(TURNS_FLOWN, 0) + 1);
            if (flown.spent()) self.setAiMemory(SPENT, 1);
            return new EnemyAction.ProjectileMove(flown.path(), flown.ends(), flown.ends() ? flown.at() : null);
        }

        /** Its next turn of flight. Reads the board and changes nothing. */
        static Flown fly(CombatEntity self, GridArena arena, GridPos target) {
            int dx = self.getProjectileDirX();
            int dz = self.getProjectileDirZ();
            List<GridPos> everyone = players(arena);
            List<GridPos> path = new ArrayList<>();
            GridPos at = self.getGridPos();
            for (int i = 0; i < SPEED; i++) {
                GridPos next = new GridPos(at.x() + dx, at.z() + dz);
                GridTile ground = arena.isInBounds(next) ? arena.getTile(next) : null;
                if ((dx == 0 && dz == 0) || ground == null || !ground.isWalkable()) {
                    return new Flown(path, true, at, false);
                }
                if (next.equals(target) || everyone.contains(next)) {
                    path.add(next);
                    return new Flown(path, true, next, false);
                }
                if (arena.isOccupied(next)) return new Flown(path, true, at, true);
                path.add(next);
                at = next;
            }
            boolean burntOut = self.getAiMemory(TURNS_FLOWN, 0) + 1 >= FIRE_CRYSTAL_TURNS;
            return new Flown(path, burntOut, at, burntOut);
        }
    }

    private static void fireCrystalBursts(ProjectileImpactHandler.Context ctx) {
        BlockPos at = ctx.arena().gridToBlockPos(ctx.impactPos());
        if (isSpent(ctx.projectile())) {
            // Broken against something that was not a wall, or burnt out. Smoke, and nothing else.
            ctx.world().spawnParticles(ParticleTypes.LARGE_SMOKE,
                at.getX() + 0.5, at.getY() + 0.6, at.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.01);
            return;
        }
        ctx.world().spawnParticles(ParticleTypes.FLAME,
            at.getX() + 0.5, at.getY() + 0.6, at.getZ() + 0.5, 30, 0.7, 0.5, 0.7, 0.05);
        ctx.world().spawnParticles(ParticleTypes.LAVA,
            at.getX() + 0.5, at.getY() + 0.4, at.getZ() + 0.5, 8, 0.5, 0.2, 0.5, 0.0);
        ctx.world().playSound(null, at, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.0f, 0.7f);
        ctx.message("§6✦ The Fire Crystal bursts!");
        ctx.hitPlayers(FIRE_BURST_RADIUS, ctx.projectile().getAttackPower(),
            CombatEffects.EffectType.BURNING, 2, 0,
            "§c  The blast scorches %s for %d! §6(Burning, 2 turns)");
    }

    /** The one crystal a player wants to see coming. */
    private static final class IceCrystal implements ProjectileImpactHandler {

        @Override
        public void onImpact(Context ctx) {
            BlockPos at = ctx.arena().gridToBlockPos(ctx.impactPos());
            ctx.world().spawnParticles(ParticleTypes.SNOWFLAKE,
                at.getX() + 0.5, at.getY() + 0.8, at.getZ() + 0.5, 25, 0.4, 0.5, 0.4, 0.04);
            ctx.world().playSound(null, at, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 0.9f, 1.3f);
            // Knocked away with nothing to fly back into: it only breaks.
            if (ctx.redirected()) return;
            ctx.hitPlayers(0, ctx.projectile().getAttackPower(),
                CombatEffects.EffectType.WEAKNESS, 2, 0,
                "§b  The Ice Crystal bites %s for %d! §7(Weakness, 2 turns)");
        }

        /**
         * Settled here, the moment it is struck. The crystal flies straight away from
         * whoever struck it, and whether that line finds the Sun Spirit is worked out now,
         * against where it stands now. Left to fly as a thing on the board it would arrive
         * a turn late, at a boss that had moved, and a shot that was lined up would miss.
         */
        @Override
        public boolean onDeflect(Context ctx) {
            CombatEntity boss = thrower(ctx);
            if (boss == null) return false;

            ServerWorld world = ctx.world();
            GridArena arena = ctx.arena();
            GridPos struckAt = ctx.impactPos();
            // Whoever is acting is whoever struck it. The engine reads the same tile to send
            // a fireball that has been knocked back on its way.
            Shot shot = shot(arena, boss, struckAt, arena.getPlayerGridPos());
            GridPos ends = shot.end(struckAt);
            BlockPos from = arena.gridToBlockPos(struckAt);
            BlockPos to = arena.gridToBlockPos(shot.lands() ? boss.nearestTileTo(ends) : ends);
            ProjectileSpawner.spawnSpellTrail(world, from, to,
                ParticleTypes.SNOWFLAKE, ParticleTypes.END_ROD, 24, 0.6);
            world.spawnParticles(ParticleTypes.SNOWFLAKE,
                to.getX() + 0.5, to.getY() + 1.2, to.getZ() + 0.5, shot.lands() ? 45 : 15, 0.8, 0.9, 0.8, 0.06);
            world.playSound(null, to, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE,
                shot.lands() ? 1.2f : 0.7f, shot.lands() ? 0.6f : 1.5f);

            if (shot.lands()) {
                int dealt = ctx.pierce(boss, deflectDamage(ctx.projectile().getAttackPower(), boss.getMaxHp()));
                for (String line : struck(boss, dealt)) ctx.message(line);
                if (!boss.isAlive()) return true;
            } else {
                ctx.message(String.format(WIDE_LINE, boss.getDisplayName()));
            }

            // The floor freezes where the crystal was struck, hit or miss, and at once, so
            // that what the player sees for the rest of their turn is the board its next
            // turn is made on.
            List<GridPos> patch = frostPatch(arena, boss, struckAt);
            Room.freeze(world, arena, patch);
            List<GridPos> braziers = boss.getAiInstance() instanceof SunSpiritAI ai
                ? ai.braziers(arena) : List.of();
            int dark = 0;
            for (GridPos brazier : braziers) {
                if (lit(arena, brazier)) continue;
                dark++;
                Room.showBrazier(world, arena, brazier, false);
            }
            if (!patch.isEmpty()) {
                ctx.message("§b  The floor freezes where the crystal was struck."
                    + (dark == 0 ? "" : dark == 1 ? " §7A brazier goes dark." : " §7" + dark + " braziers go dark."));
            }
            return true;
        }

        /** The Sun Spirit that threw this crystal, or failing that, any Sun Spirit still in the fight. */
        private static CombatEntity thrower(Context ctx) {
            // Far wider than any arena: this is a search of the fight, not of a neighbourhood.
            List<CombatEntity> everyone = ctx.enemiesNear(4096);
            int owner = ctx.projectile().getProjectileOwnerId();
            for (CombatEntity e : everyone) {
                if (e.getEntityId() == owner) return e;
            }
            for (CombatEntity e : everyone) {
                if (BOSS_KEY.equals(e.getAiKey())) return e;
            }
            return null;
        }
    }

    // ================================================================
    // Spawn hook: what NoAI leaves running
    // ================================================================

    private static void onSpawn(ServerWorld world, MobEntity mob, CombatEntity entity) {
        RealEntity.fitToFootprint(mob, entity);
        RealEntity.cutLooseFromItsRoom(mob);
        if (!BOSS_KEY.equals(entity.getAiKey())) return;

        mob.setCustomName(Text.literal(NAMEPLATE));
        mob.setCustomNameVisible(true);
        // This also runs when the engine replaces an entity it found dead mid-fight, so it
        // has to put back whichever state the fight is in rather than always the first one.
        if (isFrozen(entity)) {
            RealEntity.showFrozen(entity, true);
        } else {
            entity.setDamageImmune(true, GUARD_HINT);
        }
    }

    /**
     * Everything that reaches into the live Aether entity, by name. Kept in a class of its
     * own so that none of it is loaded until there is a real entity to reach into.
     *
     * <p>Arena mobs are frozen with NoAI, which silences goals and nothing else. A Sun Spirit
     * does three things in its own {@code tick} that an arena cannot have: it destroys every
     * breakable block within a block of its hitbox, it boils away every liquid within nine
     * blocks, and it burns whatever is inside a column through its middle.
     */
    private static final class RealEntity {

        private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
        /** Below any world, so nothing that is ever really there falls inside it. */
        private static final double NOWHERE_Y = -4096.0;
        /** The Aether takes half a room's width less five as the reach of its evaporation: ten gives none. */
        private static final double ROOM_WIDTH = 10.0;

        static void fitToFootprint(MobEntity mob, CombatEntity entity) {
            setScale(mob, fitScale(Math.min(entity.getSizeX(), entity.getSizeZ())));
        }

        static void setScale(MobEntity mob, double value) {
            //? if <=1.21.1 {
            var scale = mob.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_SCALE);
            //?} else {
            /*var scale = mob.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.SCALE);
            *///?}
            if (scale != null) scale.setBaseValue(value);
        }

        /**
         * Give the entity a boss room that is nowhere.
         *
         * <p>Its block breaking and its right-click dialogue both ask the same question first:
         * is this inside my room? With no room at all the answer is taken as yes, which is
         * how a Sun Spirit in an arena comes to eat the obstacles beside it, the border posts
         * and their lanterns, and to offer its ten lines of chat and its own boss fight to
         * anyone who clicks it. With a room that contains nothing the answer is always no.
         * The same room, being ten wide, shrinks its evaporation from nine blocks around it
         * to the tiles it is standing on. Only this one entity is touched: the block tag that
         * could spare the arena's blocks would spare them in every real Gold Dungeon too.
         */
        static void cutLooseFromItsRoom(MobEntity mob) {
            try {
                Class<?> roomType = Class.forName("com.aetherteam.nitrogen.entity.BossRoomTracker");
                Constructor<?> build = null;
                for (Constructor<?> candidate : roomType.getConstructors()) {
                    if (candidate.getParameterCount() == 4) build = candidate;
                }
                if (build == null) throw new NoSuchMethodException("BossRoomTracker(boss, origin, bounds, players)");
                double half = ROOM_WIDTH / 2.0;
                Box nowhere = new Box(mob.getX() - half, NOWHERE_Y, mob.getZ() - half,
                    mob.getX() + half, NOWHERE_Y + ROOM_WIDTH, mob.getZ() + half);
                Object room = build.newInstance(mob, new Vec3d(mob.getX(), mob.getY(), mob.getZ()),
                    nowhere, new ArrayList<UUID>());
                mob.getClass().getMethod("setDungeon", roomType).invoke(mob, room);
            } catch (ReflectiveOperationException | RuntimeException e) {
                reportOnce("could not give a Sun Spirit an empty boss room (" + e + "). It will break "
                    + "the blocks beside it and evaporate liquid across the arena");
            }
        }

        /**
         * Show the freeze on the entity itself, which the Aether draws differently and which
         * stops its flames. Only for looks: at the scale it is given here its contact burn
         * cannot reach a neighbouring tile whether it is frozen or not.
         */
        static void showFrozen(CombatEntity boss, boolean frozen) {
            MobEntity mob = boss.getMobEntity();
            if (mob == null || !ENTITY_ID.equals(boss.getEntityTypeId())) return;
            try {
                mob.getClass().getMethod("setFrozen", boolean.class).invoke(mob, frozen);
            } catch (ReflectiveOperationException | RuntimeException e) {
                reportOnce("could not show a Sun Spirit frozen (no setFrozen)");
            }
        }

        private static void reportOnce(String what) {
            if (REPORTED.add(what)) {
                CrafticsMod.LOGGER.warn("[Craftics × Aether] {}. The Aether may have renamed it.", what);
            }
        }
    }

    /**
     * The room's own blocks: which obstacles are braziers, the flame on top of each, and the
     * ice a struck crystal leaves on the floor. Apart from the rest because every line of it
     * needs the game's block list, which a turn worked out on a bare grid never loads.
     */
    private static final class Room {

        /** What frozen floor looks like. The block the engine's own frost is drawn with. */
        private static final Block ICE = Blocks.PACKED_ICE;

        static List<GridPos> netherrackOnly(GridArena arena, List<GridPos> stones) {
            List<GridPos> braziers = new ArrayList<>();
            for (GridPos stone : stones) {
                if (arena.getTile(stone).getBlockType() == Blocks.NETHERRACK) braziers.add(stone);
            }
            return braziers;
        }

        /**
         * Freeze the floor on {@code patch}. Done the way the arena lays any passing terrain:
         * the tile is given its new type for a number of rounds and painted now, and the
         * engine's own count gives the floor back when the rounds run out, or when the fight
         * ends first. A flame standing on the tile is taken off with it.
         */
        static void freeze(ServerWorld world, GridArena arena, List<GridPos> patch) {
            for (GridPos tile : patch) {
                GridTile ground = arena.getTile(tile);
                if (ground == null) continue;
                ground.setTemporaryType(FROZEN_FLOOR, FROST_TURNS);
                ground.setBlockType(ICE);
                BlockPos floor = arena.gridToBlockPos(tile).down();
                BlockState above = world.getBlockState(floor.up());
                if (above.isOf(Blocks.FIRE) || above.isOf(Blocks.SOUL_FIRE)) {
                    world.setBlockState(floor.up(), Blocks.AIR.getDefaultState(),
                        Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                }
                world.setBlockState(floor, ICE.getDefaultState(), Block.NOTIFY_ALL);
                world.spawnParticles(ParticleTypes.SNOWFLAKE,
                    floor.getX() + 0.5, floor.getY() + 1.1, floor.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.02);
            }
        }

        /**
         * Each brazier is shown as it will be when the players next look at the room. Frost
         * on its last round is gone by then, so a brazier it was keeping dark is lit again a
         * turn early here, though it stays out of this turn's throwing.
         */
        static void showBraziers(MobEntity anyone, GridArena arena, List<GridPos> braziers) {
            if (!(anyone.getWorld() instanceof ServerWorld world)) return;
            for (GridPos brazier : braziers) {
                showBrazier(world, arena, brazier, !frostBeside(arena, brazier, 1));
            }
        }

        /**
         * Put the flame on top of a brazier, or take it off. Only ever swaps flame for air or
         * air for flame, so a brazier built taller, or with something else on it, is left as
         * it was built. The flame is the room's own and belongs to no tile, which is why it
         * is set by hand here: nothing in the arena's count of the floor knows it is there.
         */
        static void showBrazier(ServerWorld world, GridArena arena, GridPos brazier, boolean alight) {
            BlockPos top = arena.gridToBlockPos(brazier).up();
            BlockState now = world.getBlockState(top);
            if (alight && now.isAir()) {
                world.setBlockState(top, Blocks.FIRE.getDefaultState(),
                    Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            } else if (!alight && now.isOf(Blocks.FIRE)) {
                world.setBlockState(top, Blocks.AIR.getDefaultState(),
                    Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                    top.getX() + 0.5, top.getY() + 0.3, top.getZ() + 0.5, 10, 0.2, 0.3, 0.2, 0.01);
            }
        }
    }

    /**
     * What a crystal looks like. Neither Aether crystal is a mob, so the engine flies an
     * invisible stand-in and lets a visual ride along with it. For its own projectiles it
     * picks that visual from a fixed list; this hangs one on a crystal the same way, using
     * the link the engine already keeps in step and clears away.
     */
    private static final class CrystalLook {

        private static final float SIZE = 0.55f;

        static void dress(CombatEntity crystal, boolean ice) {
            MobEntity carrier = crystal.getMobEntity();
            if (carrier == null || !(carrier.getWorld() instanceof ServerWorld world)) return;

            DisplayEntity.ItemDisplayEntity shard =
                new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
            shard.refreshPositionAndAngles(carrier.getX(), carrier.getY() + 0.5, carrier.getZ(), 0f, 0f);
            ((ItemDisplayInvoker) shard).craftics$setItemStack(
                new ItemStack(ice ? Items.BLUE_ICE : Items.MAGMA_BLOCK));
            DisplayEntityInvoker look = (DisplayEntityInvoker) shard;
            // Stood on one corner, a cube reads as a crystal rather than as a block.
            look.craftics$setTransformation(new AffineTransformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf().rotateXYZ(0.7854f, 0f, 0.6155f),
                new Vector3f(SIZE, SIZE, SIZE),
                new Quaternionf()));
            look.craftics$setTeleportDuration(2);
            look.craftics$setBrightness(new Brightness(15, 15));
            shard.addCommandTag("craftics_arena");
            shard.addCommandTag("craftics_visual_projectile");
            if (!world.spawnEntity(shard)) return;

            crystal.setVisualProjectileEntityId(shard.getId());
            carrier.setInvisible(true);
            carrier.setSilent(true);
            RealEntity.setScale(carrier, 0.01);

            BlockPos at = carrier.getBlockPos();
            world.playSound(null, at, ice ? SoundEvents.BLOCK_GLASS_BREAK : SoundEvents.ENTITY_BLAZE_SHOOT,
                SoundCategory.HOSTILE, 1.0f, ice ? 1.6f : 1.0f);
        }
    }

    /**
     * The fight this boss is in, found from the outside, to speak to the party. Kept apart so
     * that an AI with no live entity behind it never touches the fight manager at all.
     */
    private static final class Stage {

        static void say(CombatEntity anyone, GridArena arena, String line) {
            CombatManager fight = fightOf(anyone, arena);
            if (fight != null) fight.sendMessage(line);
        }

        private static CombatManager fightOf(CombatEntity anyone, GridArena arena) {
            MobEntity mob = anyone.getMobEntity();
            if (mob == null || !(mob.getWorld() instanceof ServerWorld world)) return null;
            for (ServerPlayerEntity player : world.getPlayers()) {
                // Asked first, because looking a fight up for someone who is not in one makes one.
                if (!CombatManager.isEngaged(player.getUuid())) continue;
                CombatManager fight = CombatManager.getActiveCombat(player.getUuid());
                if (fight != null && fight.isActive() && fight.getArena() == arena) return fight;
            }
            return null;
        }
    }
}
