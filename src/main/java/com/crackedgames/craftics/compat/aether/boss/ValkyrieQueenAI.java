package com.crackedgames.craftics.compat.aether.boss;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.CrafticsAPI;
import com.crackedgames.craftics.api.ProjectileImpactHandler;
import com.crackedgames.craftics.api.registry.ProjectileImpactRegistry;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.CombatManager;
import com.crackedgames.craftics.combat.MobTraits;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ProjectileSpawner;
import com.crackedgames.craftics.combat.ai.AIRegistry;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.combat.ai.boss.BossAI;
import com.crackedgames.craftics.combat.ai.boss.BossWarning;
import com.crackedgames.craftics.combat.animation.AnimState;
import com.crackedgames.craftics.combat.animation.MobAnimations;
import com.crackedgames.craftics.combat.animation.MobAttackAnimations;
import com.crackedgames.craftics.compat.aether.AetherCompat;
import com.crackedgames.craftics.compat.aether.AetherMobs;
import com.crackedgames.craftics.compat.aether.ai.ValkyrieAI;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Silver Dungeon boss - "The Valkyrie Queen" ({@code aether:valkyrie_queen}, 1x1).
 *
 * <p>She plays the way she does in the Aether: a gate, and then a duel. The duel is in three
 * parts, by thirds of her health.
 *
 * <h2>The medal gate</h2>
 * She will not fight anyone who has not proved themselves, so the fight opens with her guard
 * up. The proof is ten Victory Medals, which is what her valkyries drop on the way here. She
 * takes every medal the party is carrying, and for whatever is still missing she sends her
 * valkyries: at most {@link #MAX_ESCORT} at a time, and each one beaten counts as
 * {@link #VALKYRIE_WORTH} medals. A valkyrie this deep is a fight in itself, and ten of them
 * before the boss could be touched was two whole levels of fighting.
 *
 * <p>While the guard is up she keeps her distance, but she does not only watch: she keeps one
 * thunder crystal in the air. It is the same crystal the duel is built on, so the gate is
 * where a party learns to strike one, and one struck at a valkyrie of hers does twice its
 * own blow or a third of the valkyrie's health, whichever is more.
 *
 * <h2>The duel on foot</h2>
 * Once the tribute is in she drops the guard and fights, by priority:
 * <ul>
 *   <li><b>Shove</b>: every third swing at someone standing against her throws them two
 *       tiles clear, every second once she is hurt. Beside her she can neither throw nor
 *       lunge, so without it hugging her would be the safest place in the room.</li>
 *   <li><b>Thunder crystal</b>: a homing crystal that strikes like lightning and throws its
 *       target back a tile. She throws without breaking stride, the same turn she walks,
 *       and only ever from {@link #CRYSTAL_HEAD_START} steps off or more: a crystal flies
 *       the turn it is thrown, and from any closer it would land before it could be
 *       answered. Striking it is a shot, settled as it is struck: it flies straight away
 *       from the striker and bursts on the first of hers its line comes within a tile of,
 *       at any distance. On her that is twice its blow or a twentieth of her health. One
 *       left unanswered burns out after {@link #CRYSTAL_TURNS} turns of hunting.</li>
 *   <li><b>Lunge</b>: with her target {@link #LUNGE_MIN} to {@link #LUNGE_MAX} tiles down a
 *       clear lane she marks the lane, and on her next turn crosses it and strikes whoever
 *       is still standing in it. Step out of the lane and she hits nothing.</li>
 *   <li><b>Blink</b>: when she cannot reach her target on foot, or has not moved that way in
 *       a while, she teleports to its far side. It is only ever how she gets there.</li>
 *   <li>Otherwise she swings at whoever is beside her, or walks up to them. She does not
 *       swing at the end of a walk: the blade waits for someone who chose to stay.</li>
 * </ul>
 *
 * <h2>On the wing</h2>
 * At two thirds of her health the cooldowns shorten, a third crystal joins the two, and she
 * has the dive, every {@link #DIVE_EVERY} turns. On one turn she takes to the air, where
 * nothing reaches her, and marks a square of three by three on her target. The mark does not
 * follow them. On the next she comes down on the middle of it, or on the open tile nearest
 * that: everyone in the square takes her attack and is thrown {@link #DIVE_THROW} tiles
 * outward. Her crystals keep hunting while she is up.
 *
 * <p>Grounding her is the answer to it, and it has to be made. A crystal lying in the
 * square does nothing. A crystal of hers that a player strikes while she is up, on a line
 * that crosses the square, brings her down hard: on the ground where she meant to land,
 * hurt for a tenth of her health, and a turn getting up.
 *
 * <p>From here on the lane of her lunge is three wide. Her blade is still for the first
 * player in the middle of it, and anyone on the outer tiles she passes takes half.
 *
 * <p>And from here on she is not alone. Whenever fewer than {@link #MAX_GUARD} valkyries
 * of hers are on the floor she calls one of her honour guard, every {@link #GUARD_EVERY}
 * turns at most and every {@link #GUARD_EVERY_STORM} in the storm: an escort in every
 * number, so it outlasts the turn it lands. The call is the whole of her move. A valkyrie
 * lands calm and comes for the party on her next turn, so nothing that arrives strikes unseen.
 *
 * <h2>The storm</h2>
 * At a third she calls the storm down. From then on every turn she ends on the ground
 * marks three by three on her target and on each other player, and on her next turn
 * lightning strikes all of it at once for her attack. It is one strike over the whole of
 * the marked ground, so nobody takes two bolts, and whoever her blade finds that turn is
 * left out of it. The dive stays. The blink goes: the strikes ride with whatever else she
 * does, and a blink has to be the whole of its turn.
 *
 * <h2>What she shows, and what she holds herself to</h2>
 * Everything of hers that can hurt is on the floor a turn before it lands, with three
 * exceptions: her swing at someone standing beside her, the shove, which is that same swing,
 * and a crystal, which is a thing in plain sight that somebody chose not to answer. So a
 * player who ends their turn off every mark and not beside her is not hurt by her turn.
 * The marks are held as one warning, which is repainted every turn and never timed: the
 * base class resolves nothing from it, and each mark is made good by hand on the turn it
 * falls due. The lunge strikes only someone on its lane, the dive only the square, the
 * lightning only its squares, and nothing she does at the end of a walk strikes at all.
 * Nobody takes two hits from one turn of hers.
 *
 * <p>Nothing here links against the Aether. The medal is found by registry id, and the few
 * things that must be said to the live entity are said by reflection on its own names.
 */
public class ValkyrieQueenAI extends BossAI {

    /** The AI key the Silver Dungeon's boss spawns with. */
    public static final String BOSS_KEY = "boss:" + AetherCompat.SILVER_DUNGEON;
    public static final String QUEEN = AetherCompat.MOD_ID + ":valkyrie_queen";
    /** Registry path of the medal she asks for. Her valkyries are the only source. */
    public static final String MEDAL = "victory_medal";
    /** Projectile type of her crystal: the key its impact handler is registered under. */
    public static final String THUNDER_CRYSTAL = "aether_thunder_crystal";

    /** Medals she asks for, as in the Aether. */
    public static final int MEDALS_REQUIRED = 10;
    /** What beating one of her valkyries is worth toward that. */
    public static final int VALKYRIE_WORTH = 2;
    /** Valkyries she keeps on the floor at once while the gate is shut. */
    public static final int MAX_ESCORT = 2;
    /** She steps away from anyone who comes this close while her guard is up. */
    public static final int KEEP_AWAY = 2;
    /** Crystals she will have chasing the party at once in the duel. */
    public static final int MAX_CRYSTALS = 2;
    /** And from two thirds of her health down. */
    public static final int MAX_CRYSTALS_ENRAGED = 3;
    /** Crystals she keeps in the air while her guard is still up. */
    public static final int GATE_CRYSTALS = 1;
    /**
     * Steps a crystal has to start from anyone it could go for. It flies two the turn it is
     * thrown, so one thrown from closer than this lands before it can be answered.
     */
    public static final int CRYSTAL_HEAD_START = 3;
    /**
     * Turns a crystal hunts before it burns out. One that is led about and never answered
     * would otherwise fill her hand for good, and she would stop throwing.
     */
    public static final int CRYSTAL_TURNS = 5;
    /** Tiles her shove throws whoever is standing against her. */
    public static final int REPULSE_TILES = 2;
    /** Turns without a blink after which she repositions even if she could have walked. */
    public static final int REPOSITION_EVERY = 5;
    /**
     * How near her target a lunge starts, and how far down a clear lane it reaches. Her own:
     * an ordinary valkyrie stops at four, and her room is bigger than a corridor.
     */
    public static final int LUNGE_MIN = 2;
    public static final int LUNGE_MAX = 6;
    /** Turns from one takeoff to the next, once she has the dive. */
    public static final int DIVE_EVERY = 5;
    /** Tiles the dive throws whoever she came down at. */
    public static final int DIVE_THROW = 2;
    /** Valkyries of hers on the floor at which she stops calling for another. */
    public static final int MAX_GUARD = 2;
    /** Turns from one call to her honour guard to the next: on the wing, and in the storm. */
    public static final int GUARD_EVERY = 6;
    public static final int GUARD_EVERY_STORM = 4;
    /** Nine tenths of a tile on a body 0.8 blocks wide, so she stays inside her own square. */
    public static final double QUEEN_SCALE = 1.1;

    /**
     * The body a crystal flies in. A projectile type the engine does not know gets no visual
     * of its own, so the creature that tracks it on the grid is what the player sees - and
     * an allay is the one small, blue, glowing thing that hovers.
     */
    static final String CRYSTAL_BODY = "minecraft:allay";
    /**
     * Enough that a stray sweep does not pop it. A crystal is answered by knocking it away,
     * and a direct hit on it never deals damage at all (the same reason ghast fireballs carry 99).
     */
    static final int CRYSTAL_HP = 99;
    static final double CRYSTAL_SCALE = 1.5;

    private static final String SEEKING = "seeking_projectile";
    /** Set on a crystal that has burnt out, for its last turn. */
    private static final String BURNT = "valkyrie_crystal_burnt";

    private static final String CD_ESCORT = "escort";
    private static final String CD_GUARD = "guard";
    private static final String CD_CRYSTAL = "thunder_crystal";
    private static final String CD_LUNGE = "lunge";
    private static final String CD_BLINK = "blink";
    private static final String CD_REPULSE = "repulse";
    private static final String CD_DIVE = "dive";

    /**
     * Turns a mark is given before the base class would resolve it by itself. Never reached:
     * her marks are one warning, repainted every turn and made good by hand.
     */
    private static final int HELD = 1_000_000;
    /** What anything aimed at her says while she is in the air. */
    static final String ALOFT_HINT =
        "§eShe is out of reach. Strike a thunder crystal across the marked ground to bring her down.";

    // Her own lines, word for word from the Aether.
    static final String LINE_CHALLENGE =
        "Very well then. Bring me ten medals from my subordinates to prove your worth, then we'll see.";
    static final String LINE_WAITING = "Take your time.";
    static final String LINE_BEGIN = "Now then, let's begin!";
    static final String LINE_FIGHT = "This will be your final battle!";
    static final String LINE_DEFEATED = "You are truly... a mighty warrior...";

    private static final String FAREWELL_SAID = "valkyrie_queen_farewell";
    /** Her gold, for everything she marks. */
    private static final int MARK_COLOR = 0xFFF2D86A;

    private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    // ================================================================
    // Registration
    // ================================================================

    private static boolean registered = false;

    /**
     * Register the fight. The brain, the traits and the crystal go in whether or not the
     * Aether is installed. The hooks that only mean anything beside a live Aether entity wait
     * for the mod, so this has to run after {@code AetherCompat.init} has had its look.
     */
    public static void register() {
        if (registered) return;
        registered = true;

        AIRegistry.registerBoss(BOSS_KEY, ValkyrieQueenAI::new);
        // Each is a claim about what the AI does: the blink, the lunge down a lane, the
        // shove and the dive's throw, and leaving the floor altogether.
        MobTraits.declare(BOSS_KEY, MobTraits.ETHEREAL, MobTraits.BERZERKER, MobTraits.FORCEFUL,
            MobTraits.ACROBATIC);
        // She swings the way her valkyries do.
        MobAttackAnimations.register(QUEEN, MobAttackAnimations.Style.DASH);
        ProjectileImpactRegistry.register(THUNDER_CRYSTAL, new ThunderCrystal(), true);

        if (!AetherCompat.isLoaded()) return;
        // Keyed on the entity, not on the fight: what her own tick leaves running is true of
        // her wherever she turns up.
        CrafticsAPI.registerSpawnCustomizer(QUEEN, ValkyrieQueenAI::prepare);
    }

    // ================================================================
    // Pure rules (unit-tested)
    // ================================================================

    /**
     * How much of the tribute is in: medals handed over, plus {@link #VALKYRIE_WORTH} for each
     * valkyrie beaten, never past the target.
     */
    public static int tribute(int medals, int valkyriesBeaten, int target) {
        int paid = Math.max(0, medals) + Math.max(0, valkyriesBeaten) * VALKYRIE_WORTH;
        return Math.max(0, Math.min(target, paid));
    }

    /** Whether the gate opens. */
    public static boolean gateOpen(int medals, int valkyriesBeaten, int target) {
        return tribute(medals, valkyriesBeaten, target) >= target;
    }

    /** How many of the medals a party holds she takes: all of them, up to what she is still owed. */
    public static int medalsAccepted(int medalsHeld, int tributeSoFar, int target) {
        return Math.max(0, Math.min(medalsHeld, target - tributeSoFar));
    }

    /**
     * How many valkyries to send for a tribute still {@code owed}. Never more than the escort
     * allows, and never more than it takes: one already on the floor is tribute waiting to be
     * collected.
     */
    /** How many of her valkyries would pay off a tribute still {@code owed}. */
    public static int valkyriesToBeat(int owed) {
        return (Math.max(0, owed) + VALKYRIE_WORTH - 1) / VALKYRIE_WORTH;
    }

    /** What is left to do before she will fight, said as a number of valkyries. */
    static String stillToDo(int tribute) {
        int left = valkyriesToBeat(MEDALS_REQUIRED - tribute);
        return left <= 0 ? "Paid in full." : "Beat " + left + " more of her valkyries and she will fight.";
    }

    /** The count the party is given whenever it moves. */
    static String tributeLine(int tribute) {
        return "§7✦ Tribute: " + tribute + " of " + MEDALS_REQUIRED + ". §e" + stillToDo(tribute)
            + " §7A Victory Medal counts for 1, and every valkyrie of hers you beat for " + VALKYRIE_WORTH + ".";
    }

    public static int escortToSend(int alive, int owed) {
        int needed = (Math.max(0, owed) + VALKYRIE_WORTH - 1) / VALKYRIE_WORTH;
        return Math.max(0, Math.min(MAX_ESCORT - alive, needed - alive));
    }

    // Her escort is the dungeon's own valkyrie, read off her so it scales with her: at her
    // base 100 / 11 / 3 these come to the 20 / 8 / 2 the biome gives its valkyries.

    public static int escortHp(int queenMaxHp) {
        return Math.max(4, queenMaxHp / 5);
    }

    public static int escortAttack(int queenAttack) {
        return Math.max(1, queenAttack * 3 / 4);
    }

    public static int escortDefense(int queenDefense) {
        return Math.max(0, queenDefense - 1);
    }

    // Her honour guard, called in the duel: an escort's blade and an escort's health. On
    // half of it a strong party cut each one down in the turn it stood calm, and the guard
    // never drew a blade.

    public static int guardHp(int queenMaxHp) {
        return escortHp(queenMaxHp);
    }

    public static int guardAttack(int queenAttack) {
        return escortAttack(queenAttack);
    }

    /** A crystal hits for half of what her blade does, close to the Aether's 5 against 13.5. */
    public static int crystalDamage(int queenAttack) {
        return Math.max(2, queenAttack / 2);
    }

    /** What the outer tiles of a lane three wide take as she passes: half her blade. */
    public static int sweepDamage(int queenAttack) {
        return Math.max(1, queenAttack / 2);
    }

    /**
     * What a crystal does to an enemy it is knocked into. Twice what it would have done to
     * the player, and never less than a share of the target's health: a twentieth of a boss,
     * a third of anything else. A flat number is nothing against the health anything carries
     * this deep, and sending one back has to be worth the swing.
     */
    public static int reflectedDamage(int crystalDamage, int targetMaxHp, boolean boss) {
        int base = crystalDamage * 2;
        return Math.max(base, targetMaxHp / (boss ? 20 : 3));
    }

    /** What bringing her down out of the air does to her: twice the crystal, or a tenth of her. */
    public static int groundingDamage(int crystalDamage, int queenMaxHp) {
        return Math.max(crystalDamage * 2, queenMaxHp / 10);
    }

    /** Which third of her health she is in: 1 above two thirds, 2 from there down, 3 at a third or less. */
    public static int phase(int hp, int maxHp) {
        if (hp * 3 <= maxHp) return 3;
        if (hp * 3 <= maxHp * 2) return 2;
        return 1;
    }

    /**
     * The way a struck crystal is sent: straight away from whoever struck it, on whichever
     * of the eight ways points most nearly from them to it. Struck from beside it, that is
     * simply the way they were facing it, corner to corner included.
     */
    static int[] knockedAway(GridPos crystal, GridPos striker) {
        int dx = crystal.x() - striker.x();
        int dz = crystal.z() - striker.z();
        int[] best = {0, -1};
        double bestDot = Double.NEGATIVE_INFINITY;
        if (dx == 0 && dz == 0) return best;
        for (int wx = -1; wx <= 1; wx++) {
            for (int wz = -1; wz <= 1; wz++) {
                if (wx == 0 && wz == 0) continue;
                double dot = (wx * dx + wz * dz) / Math.hypot(wx, wz);
                if (dot > bestDot + 1e-9) {
                    bestDot = dot;
                    best = new int[]{wx, wz};
                }
            }
        }
        return best;
    }

    /**
     * The flight of a struck crystal: the way it went, the tiles it crossed, and what it
     * found. That is one of hers it burst on, or the square she was diving at, or nothing.
     */
    record Shot(int dx, int dz, List<GridPos> path, CombatEntity hit, boolean grounds) {
        GridPos end(GridPos struck) {
            return path.isEmpty() ? struck : path.get(path.size() - 1);
        }
    }

    /**
     * Where a crystal struck at {@code struck} by someone standing at {@code striker} goes.
     * It is settled the moment it is struck: straight away from them, over the floor and
     * past anyone standing on it, until a wall or the edge of the room stops it. It bursts
     * on the first of {@code hers} that its line comes within a tile of, however far off.
     *
     * <p>{@code square} is the ground she is diving at, when she is in the air. A line that
     * crosses it before it finds anyone brings her down. The tile the crystal was struck
     * on does not count for that: one lying in the square still has to be sent across it.
     */
    static Shot shot(GridArena arena, GridPos struck, GridPos striker, List<CombatEntity> hers,
                     List<GridPos> square) {
        List<GridPos> path = new ArrayList<>();
        if (striker == null) return new Shot(0, 0, path, null, false);
        int[] way = knockedAway(struck, striker);
        GridPos at = new GridPos(struck.x() + way[0], struck.z() + way[1]);
        while (arena.isInBounds(at)) {
            GridTile ground = arena.getTile(at);
            if (ground == null || !ground.isWalkable()) break;
            path.add(at);
            if (square.contains(at)) return new Shot(way[0], way[1], path, null, true);
            CombatEntity nearest = null;
            for (CombatEntity one : hers) {
                int gap = gap(one, at);
                if (gap > 1) continue;
                if (nearest == null || gap < gap(nearest, at)) nearest = one;
            }
            if (nearest != null) return new Shot(way[0], way[1], path, nearest, false);
            at = new GridPos(at.x() + way[0], at.z() + way[1]);
        }
        return new Shot(way[0], way[1], path, null, false);
    }

    /** How far {@code tile} is from the body of {@code one}, a corner counted as one. Nothing, inside it. */
    static int gap(CombatEntity one, GridPos tile) {
        GridPos anchor = one.getGridPos();
        int dx = Math.max(0, Math.max(anchor.x() - tile.x(), tile.x() - (anchor.x() + one.getSizeX() - 1)));
        int dz = Math.max(0, Math.max(anchor.z() - tile.z(), tile.z() - (anchor.z() + one.getSizeZ() - 1)));
        return Math.max(dx, dz);
    }

    /** The square of three by three around {@code centre}, as much of it as is in the room. */
    static List<GridPos> square(GridArena arena, GridPos centre) {
        List<GridPos> tiles = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                GridPos tile = new GridPos(centre.x() + dx, centre.z() + dz);
                if (arena.isInBounds(tile)) tiles.add(tile);
            }
        }
        return tiles;
    }

    /** "[The Valkyrie Queen]: ..." with the name in yellow, the way the Aether prints her. */
    static String voice(String speaker, String line) {
        return "§e[" + speaker + "]§f: " + line;
    }

    /** Whether this is a dead Queen who has not yet had her last word. */
    static boolean owesFarewell(CombatEntity entity) {
        return entity != null && BOSS_KEY.equals(entity.getAiKey()) && !entity.isAlive()
            && entity.getAiMemory(FAREWELL_SAID, 0) == 0;
    }

    // ================================================================
    // State
    // ================================================================

    private boolean challenged = false;
    private boolean dueling = false;
    private boolean fightDeclared = false;
    private int medalsCounted = 0;
    private int valkyriesBeaten = 0;
    /** The tribute last told to the party, so it is only said again when it moves. */
    private int tributeAnnounced = -1;
    /** Valkyries she has sent that have not been counted as beaten yet. */
    private final List<Integer> escort = new ArrayList<>();
    private int turnsSinceBlink = 0;
    // The spawn callbacks below are handed an entity id and nothing else, so the fight is
    // remembered from the last time it was seen.
    private GridArena arena;
    private CombatEntity queen;

    /** The furthest third of her health she has been in. It does not go back. */
    private int phaseReached = 1;
    /** A lunge she has marked and not yet run. */
    private Lunge lane = null;
    /** The middle of the square she is coming down on, while she is in the air. */
    private GridPos diveAt = null;
    private List<GridPos> diveMark = List.of();
    /** The ground her lightning strikes on her next turn, and the points it was called on. */
    private List<GridPos> storm = List.of();
    private List<GridPos> stormPoints = List.of();
    /** Whether she has called the storm down. */
    private boolean stormCalled = false;
    /** The valkyries of her honour guard that have landed and not yet been set on the party. */
    private final List<Integer> guardLanded = new ArrayList<>();
    /** Her crystals still hunting, each with how many of her turns it has been at it. */
    private final Map<Integer, Integer> hunting = new LinkedHashMap<>();
    /** The outer tiles of the lane her run passed this turn, each good for half her blade. */
    private List<GridPos> sweep = List.of();
    /** Brought down out of the air: her next turn is spent getting up. */
    private boolean down = false;
    /** Whether she could have walked up to her target this turn. */
    private boolean reaches = false;
    /** The tile her blade is for this turn, which her lightning then leaves alone. */
    private GridPos struck = null;
    /** Everyone her dive landed on after the first, each still owed a throw. See {@link #wantsAnotherAction}. */
    private final List<Thrown> throwsOwed = new ArrayList<>();
    private Thrown throwNow = null;
    /** Her cooldowns as the landing left them, put back for each throw that follows it. */
    private Map<String, Integer> cooldownsAtLanding = Map.of();

    /** Somebody the dive throws: the tile they stood on when it landed, and which way they go. */
    record Thrown(GridPos tile, int dx, int dz) {}

    /**
     * A lunge as it was marked: where she stood, the tiles she crosses, the one it is aimed
     * at, and the tiles either side of all of those once the lane is three wide.
     */
    record Lunge(GridPos from, List<GridPos> run, GridPos end, int dx, int dz, List<GridPos> sides) {
        /** The lane she runs: where her blade is. */
        List<GridPos> middle() {
            List<GridPos> tiles = new ArrayList<>(run);
            tiles.add(end);
            return tiles;
        }

        List<GridPos> tiles() {
            List<GridPos> tiles = middle();
            tiles.addAll(sides);
            return tiles;
        }

        /** The tiles either side of {@code tile}, across the lane. */
        List<GridPos> beside(GridPos tile) {
            return List.of(new GridPos(tile.x() + dz, tile.z() + dx), new GridPos(tile.x() - dz, tile.z() - dx));
        }
    }

    public boolean isGuardUp() {
        return !dueling;
    }

    /** Medals and beaten valkyries so far, out of {@link #MEDALS_REQUIRED}. */
    public int tributeSoFar() {
        return tribute(medalsCounted, valkyriesBeaten, MEDALS_REQUIRED);
    }

    /** The furthest third of her health she has fought in: 1, 2 or 3. */
    public int phaseReached() {
        return phaseReached;
    }

    /** In the air, between taking off and coming down. */
    public boolean isAloft() {
        return diveAt != null;
    }

    /** Brought down, with the turn she loses for it still ahead of her. */
    public boolean isDown() {
        return down;
    }

    /** The lane she has marked, the square she is diving on and the ground her lightning will strike. */
    List<GridPos> laneMarked() {
        return lane == null ? List.of() : lane.tiles();
    }

    List<GridPos> diveMarked() {
        return diveMark;
    }

    List<GridPos> stormMarked() {
        return storm;
    }

    private boolean hurt() {
        return phaseReached >= 2;
    }

    // ================================================================
    // The turn
    // ================================================================

    /** Two thirds of her health or less: where she takes to the air. */
    @Override
    protected boolean reachedPhaseTwo(CombatEntity self) {
        return phase(self.getCurrentHp(), self.getMaxHp()) >= 2;
    }

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
    }

    /**
     * She never fills a turn on which she has marked something by walking up and swinging,
     * which is what the default does for a boss this deep. The marks are where she said she
     * would be, and an unmarked swing at the end of a walk is the one thing she does not do.
     */
    @Override
    public EnemyAction getChargingAdvanceAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        return new EnemyAction.Idle();
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        this.arena = arena;
        this.queen = self;
        EnemyAction thrown = dueling ? throwTheNext(self, arena) : null;
        if (thrown != null) return thrown;
        ageHerCrystals();
        return dueling ? duel(self, arena, playerPos) : holdTheGate(self, arena, playerPos);
    }

    /**
     * A turn older, every crystal of hers still hunting, and the ones that have had
     * {@link #CRYSTAL_TURNS} are spent. A spent one stops counting against her at once, so
     * she may throw again this turn, and goes out on its own turn, which comes after hers.
     */
    private void ageHerCrystals() {
        for (Iterator<Map.Entry<Integer, Integer>> it = hunting.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Integer> held = it.next();
            CombatEntity crystal = occupant(held.getKey());
            if (crystal == null || !crystal.isAlive()) {
                it.remove();
                continue;
            }
            held.setValue(held.getValue() + 1);
            if (held.getValue() < CRYSTAL_TURNS) continue;
            crystal.setAiMemory(BURNT, 1);
            crystal.setAiInstance(BURN_OUT);
            it.remove();
        }
    }

    /**
     * The brain of a crystal that has burnt out. Its one turn is to end where it hangs,
     * which the engine takes as landing there, and {@link ThunderCrystal#onImpact} knows a
     * spent crystal when it is handed one. It keeps no count of its own: anything may ask a
     * creature what it would do without its doing it.
     */
    static final EnemyAI BURN_OUT = new EnemyAI() {
        @Override
        public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
            return new EnemyAction.ProjectileMove(List.of(), true, self.getGridPos());
        }

        @Override
        public Set<GridPos> computeThreatTiles(CombatEntity self, GridArena arena) {
            return Set.of();
        }
    };

    /** Whether this crystal has burnt out and is only waiting for its turn to go. */
    static boolean burntOut(CombatEntity crystal) {
        return crystal.getAiMemory(BURNT, 0) == 1;
    }

    /**
     * The dive throws everyone it lands on, and the engine throws one player to an action:
     * whoever the action names. So the first goes with the landing, and each of the others
     * is an action more, in the same turn, until there is nobody left to throw.
     *
     * <p>Handed over and forgotten each time it is asked, as the engine expects. If it comes
     * back round and never reaches her, the next answer is for the next of them, or no.
     */
    @Override
    public boolean wantsAnotherAction(CombatEntity self) {
        throwNow = throwsOwed.isEmpty() ? null : throwsOwed.remove(0);
        return throwNow != null;
    }

    /** The throw she was asked back for, or null when this is a turn of the ordinary kind. */
    private EnemyAction throwTheNext(CombatEntity self, GridArena arena) {
        Thrown owed = throwNow;
        throwNow = null;
        // Somebody the landing put down is no longer on the grid to be thrown.
        if (owed == null || !playerOn(arena, owed.tile())) return null;
        // The base class counted this as a turn of hers. It is the rest of the last one.
        cooldowns.clear();
        cooldowns.putAll(cooldownsAtLanding);
        self.setPendingStrikeTile(owed.tile());
        return new EnemyAction.ForcedMovement(-1, owed.dx(), owed.dz(), DIVE_THROW);
    }

    // ================================================================
    // The medal gate
    // ================================================================

    private EnemyAction holdTheGate(CombatEntity self, GridArena arena, GridPos target) {
        boolean firstTurn = !challenged;
        if (firstTurn) {
            challenged = true;
            // Already up when the spawn hook ran. Raised here too for a fight built without it.
            self.setDamageImmune(true, guardHint(self, tributeSoFar()));
        }

        countBeatenValkyries();
        // Asked again every turn, not once: a medal is worth the same whenever it turns up,
        // and a party is not always all here the first time anyone asks.
        int accepted = medalsAccepted(medalsHeld(self), tributeSoFar(), MEDALS_REQUIRED);
        if (accepted > 0) {
            takeMedals(self, accepted);
            medalsCounted += accepted;
            announce(self, "§6✦ Your party hands the Queen " + accepted + " Victory Medal"
                + (accepted == 1 ? "" : "s") + ".");
        }

        int tribute = tributeSoFar();
        if (gateOpen(medalsCounted, valkyriesBeaten, MEDALS_REQUIRED)) {
            dueling = true;
            self.setDamageImmune(false, null);
            announce(self, voice(self.getDisplayName(), LINE_BEGIN));
            announce(self, "§a✦ The tribute is paid. Her guard is down.");
            // She gives the party the turn she promised before she comes for them.
            return new EnemyAction.Idle();
        }

        if (tribute != tributeAnnounced) {
            tributeAnnounced = tribute;
            if (firstTurn) announce(self, voice(self.getDisplayName(), LINE_WAITING));
            announce(self, tributeLine(tribute));
            // And for anyone who swings at her to find out: the same count on her guard.
            self.setDamageImmune(true, guardHint(self, tribute));
        }

        EnemyAction send = sendEscort(self, arena, MEDALS_REQUIRED - tribute);
        if (send != null) return send;
        EnemyAction away = keepDistance(self, arena, target);
        // Cornered, she leaves first. A blink has to be the whole of its turn.
        if (away instanceof EnemyAction.Teleport) return away;
        EnemyAction crystal = throwCrystal(self, arena, target, away, GATE_CRYSTALS);
        return crystal != null ? crystal : away;
    }

    /**
     * Count the valkyries that have fallen since last turn. The base class has already
     * dropped the dead from its own list by now, so anything she sent that is no longer on
     * it has been beaten.
     */
    private void countBeatenValkyries() {
        for (Iterator<Integer> it = escort.iterator(); it.hasNext(); ) {
            if (summonedMinionIds.contains(it.next())) continue;
            it.remove();
            valkyriesBeaten++;
        }
    }

    /**
     * Where the valkyries she sends come down: on the floor of the hall, never on a stair or
     * a raised floor. Beside her when that is where she stands. When she is up on a raised
     * floor, the nearest of the hall floor to her takes the place of each tile that was not.
     */
    private List<GridPos> landingTiles(CombatEntity self, GridArena arena, int count) {
        List<GridPos> tiles = new ArrayList<>(findSummonPositionsNear(arena, self.getGridPos(), 2, count));
        if (tiles.isEmpty()) tiles = new ArrayList<>(findSummonPositions(arena, count));
        int wanted = tiles.size();
        tiles.removeIf(tile -> !onTheFloor(arena, tile));
        // Nothing of hers was headed for a raised floor: the room is as flat as it was.
        if (tiles.size() == wanted) return tiles;

        GridPos from = self.getGridPos();
        List<GridPos> ground = new ArrayList<>();
        for (int x = 0; x < arena.getWidth(); x++) {
            for (int z = 0; z < arena.getHeight(); z++) {
                GridPos tile = new GridPos(x, z);
                if (open(arena, tile) && onTheFloor(arena, tile) && !tiles.contains(tile)) ground.add(tile);
            }
        }
        ground.sort(java.util.Comparator.comparingInt(tile -> tile.manhattanDistance(from)));
        for (GridPos tile : ground) {
            if (tiles.size() >= wanted) break;
            tiles.add(tile);
        }
        return tiles;
    }

    /** On the arena's own floor: not part way up a staircase, and not on a raised floor. */
    private static boolean onTheFloor(GridArena arena, GridPos tile) {
        GridTile at = arena.getTile(tile);
        return at != null && at.isOnArenaFloor();
    }

    /**
     * She starts on the floor above the hall, at the middle of the room: the first raised
     * floor, between the stairs up from the hall and the steps of her dais. A room with no
     * raised floor leaves her where the level put her.
     */
    @Override
    public GridPos spawnTile(GridArena arena, int sizeX, int sizeZ) {
        GridPos middle = new GridPos(arena.getWidth() / 2, arena.getHeight() / 2);
        GridPos best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int x = 0; x < arena.getWidth(); x++) {
            for (int z = 0; z < arena.getHeight(); z++) {
                GridPos tile = new GridPos(x, z);
                GridTile at = arena.getTile(tile);
                if (at == null || at.getRise() != 0
                        || at.getType() != com.crackedgames.craftics.core.TileType.ELEVATED) {
                    continue;
                }
                if (arena.isOccupied(tile)) continue;
                int distance = tile.manhattanDistance(middle);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = tile;
                }
            }
        }
        return best;
    }

    /**
     * One of the valkyries she sent has just fallen. The engine says so as it happens, so
     * the tribute goes up at the blow that earned it and not when her turn next comes
     * round. Anything else dying in her room, and anything once the gate is open, is not
     * hers to count. The gate itself still opens on her turn, with her own words.
     *
     * @param self   the Queen, whose guard carries the count for anyone who strikes it
     * @param fallen what has just died in her room
     * @return what to tell the party, or null when this death pays nothing
     */
    public String onValkyrieBeaten(CombatEntity self, CombatEntity fallen) {
        if (dueling || fallen == null || !escort.remove(Integer.valueOf(fallen.getEntityId()))) return null;
        valkyriesBeaten++;
        int tribute = tributeSoFar();
        // Said here, so her turn does not say the same number again.
        tributeAnnounced = tribute;
        boolean paid = tribute >= MEDALS_REQUIRED;
        if (self != null && self.isDamageImmune()) self.setDamageImmune(true, guardHint(self, tribute));
        return "§6✦ A valkyrie of hers is beaten. That is worth " + VALKYRIE_WORTH
            + " medals to the Queen. §7Tribute: " + tribute + " of " + MEDALS_REQUIRED + ". "
            + (paid ? "§aPaid in full. §7She answers on her turn." : "§e" + stillToDo(tribute));
    }

    private EnemyAction sendEscort(CombatEntity self, GridArena arena, int owed) {
        if (isOnCooldown(CD_ESCORT)) return null;
        int send = escortToSend(getAliveMinionCount(), owed);
        if (send <= 0) return null;
        List<GridPos> tiles = landingTiles(self, arena, send);
        if (tiles.isEmpty()) return null;
        // A turn's grace before the next one arrives, so beating a valkyrie buys something.
        setCooldown(CD_ESCORT, 2);
        return new EnemyAction.SummonMinions(AetherMobs.VALKYRIE, tiles.size(), new ArrayList<>(tiles),
            escortHp(self.getMaxHp()), escortAttack(self.getAttackPower()), escortDefense(self.getDefense()));
    }

    /**
     * A valkyrie minds its own business until it is hit, which is right for one met in a
     * corridor and wrong for one sent to test somebody. These arrive already provoked.
     *
     * <p>Kept on a list of her own as well, because the base class forgets a minion the
     * moment it dies: one summoned and beaten inside the same round would never be seen.
     *
     * <p>Her honour guard, called in the duel, is the exception for one turn. Whatever is
     * set down takes its turn the round it arrives, and a valkyrie that arrived provoked
     * could lunge at somebody four tiles off with nothing on the floor to say so. These
     * land calm, and she sets them on the party at the start of her next turn.
     */
    @Override
    public void registerSpawnedMinion(int entityId) {
        super.registerSpawnedMinion(entityId);
        if (dueling) {
            guardLanded.add(entityId);
            return;
        }
        escort.add(entityId);
        CombatEntity valkyrie = occupant(entityId);
        if (valkyrie != null) valkyrie.setEnraged(true);
    }

    /** Set the guard that landed last turn on the party. */
    private void rouseTheGuard() {
        for (int id : guardLanded) {
            CombatEntity valkyrie = occupant(id);
            if (valkyrie != null) valkyrie.setEnraged(true);
        }
        guardLanded.clear();
    }

    /** Guard up: step away from anyone close, blink away when cornered, otherwise stand and watch. */
    private EnemyAction keepDistance(CombatEntity self, GridArena arena, GridPos target) {
        GridPos me = self.getGridPos();
        List<GridPos> threats = AIUtils.threatPositions(arena, target);
        int gap = AIUtils.minThreatDistance(me, threats);
        if (gap > KEEP_AWAY) return new EnemyAction.Idle();

        GridPos retreat = AIUtils.bestRetreatTile(self, arena, threats);
        if (retreat != null) {
            List<GridPos> path = Pathfinding.findPathSized(arena, me, retreat, self.getMoveSpeed(), self);
            if (!path.isEmpty()) return new EnemyAction.Move(path);
        }
        if (!isOnCooldown(CD_BLINK)) {
            GridPos far = farthestOpenTile(arena, threats, gap);
            if (far != null) {
                setCooldown(CD_BLINK, blinkCooldown());
                return new EnemyAction.Teleport(far);
            }
        }
        return new EnemyAction.Idle();
    }

    /** The open tile furthest from every threat, or null when none is further than she already is. */
    private static GridPos farthestOpenTile(GridArena arena, List<GridPos> threats, int currentGap) {
        GridPos best = null;
        int bestGap = currentGap;
        for (int x = 0; x < arena.getWidth(); x++) {
            for (int z = 0; z < arena.getHeight(); z++) {
                GridPos tile = new GridPos(x, z);
                if (!open(arena, tile)) continue;
                int gap = AIUtils.minThreatDistance(tile, threats);
                if (gap > bestGap) {
                    bestGap = gap;
                    best = tile;
                }
            }
        }
        return best;
    }

    // ================================================================
    // The duel
    // ================================================================

    /**
     * One turn of the duel, and it is always the same three steps. What she marked on her
     * last turn falls due. Then she makes her own move for this one, which may mark
     * something for the next. Then, in the storm and on the ground, she calls the next
     * strikes. The floor is repainted from whatever is marked at the end of it.
     */
    private EnemyAction duel(CombatEntity self, GridArena arena, GridPos target) {
        if (!fightDeclared) {
            fightDeclared = true;
            announce(self, voice(self.getDisplayName(), LINE_FIGHT));
        }
        phaseReached = Math.max(phaseReached, phase(self.getCurrentHp(), self.getMaxHp()));
        rouseTheGuard();

        // Brought down out of the air, she loses this turn getting up. Nothing was left
        // marked when she fell, so nothing is owed, and she marks nothing from the floor.
        if (down) {
            down = false;
            paint(self);
            announce(self, "§7✦ " + self.getDisplayName() + " gets back to her feet.");
            return new EnemyAction.Idle();
        }

        List<GridPos> bolts = storm;
        List<GridPos> calledOn = stormPoints;
        storm = List.of();
        stormPoints = List.of();
        struck = null;
        sweep = List.of();
        turnsSinceBlink++;
        EnemyAction move = herMove(self, arena, target, !bolts.isEmpty());

        if (stormCalled && diveAt == null) callLightning(arena, target);
        paint(self);
        return withWhatFallsDue(self, arena, bolts, calledOn, move);
    }

    /** Her own move for the turn. What is already marked comes first: it was promised. */
    private EnemyAction herMove(CombatEntity self, GridArena arena, GridPos target, boolean stormy) {
        if (diveAt != null) return land(self, arena, target);
        if (lane != null) {
            Lunge marked = lane;
            lane = null;
            EnemyAction run = runTheLane(self, arena, marked);
            if (run != null) return run;
        }
        boolean stormBreaks = phaseReached >= 3 && !stormCalled;
        if (stormBreaks) {
            // Said once. It costs her nothing: the first strikes are marked this same turn.
            stormCalled = true;
            announce(self, "§e✦ " + self.getDisplayName() + " calls the storm down. Lightning strikes the"
                + " marked ground on her next turn, every turn she is on her feet.");
        }
        if (hurt() && !isOnCooldown(CD_DIVE)) return takeWing(self, arena, target);
        // The storm breaks with nobody called to her side for it: that turn is the storm's.
        EnemyAction guard = stormBreaks ? null : callHerGuard(self, arena);
        if (guard != null) return guard;

        EnemyAction onFoot = step(self, arena, target);
        EnemyAction shove = repulse(self, target, onFoot);
        if (shove != null) return shove;
        EnemyAction crystal = throwCrystal(self, arena, target, onFoot,
            hurt() ? MAX_CRYSTALS_ENRAGED : MAX_CRYSTALS);
        if (crystal != null) return crystal;
        EnemyAction lunge = windUpLunge(self, arena, target);
        if (lunge != null) return lunge;
        // The storm's strikes ride with whatever else she does, and a blink has to be the
        // whole of its turn. So with strikes to deliver she does not blink.
        EnemyAction blink = stormy ? null : blink(self, arena, target);
        if (blink != null) return blink;
        if (onFoot instanceof EnemyAction.Attack) aimAt(self, target);
        return onFoot;
    }

    /**
     * What she does on foot: a swing at her target if it is standing beside her, and
     * otherwise a walk toward it.
     *
     * <p>Never a walk that ends in a swing. Whoever that swing found was not beside her when
     * their turn ended, and nothing on the floor said it was coming. She walks up, and the
     * blade is for whoever is still there on her next turn.
     */
    private EnemyAction step(CombatEntity self, GridArena arena, GridPos target) {
        EnemyAction onFoot = meleeOrApproach(self, arena, target, 0);
        reaches = onFoot instanceof EnemyAction.MoveAndAttack;
        return onFoot instanceof EnemyAction.MoveAndAttack walk ? new EnemyAction.Move(walk.path()) : onFoot;
    }

    /** Name the tile her blade is for: the engine gives the strike to whoever stands there. */
    private void aimAt(CombatEntity self, GridPos tile) {
        self.setPendingStrikeTile(tile);
        struck = tile;
    }

    /**
     * Her swing at someone standing against her, when it is time for it to be a shove.
     *
     * <p>Beside her target she has room for little else she does: no tile gives a crystal its
     * head start, and a lunge needs a lane. Left at that, standing against her would be the
     * safest place in the room, one swing a turn. So every third swing there, every second
     * once she is hurt, throws them clear. That is the room she needs: the turn after, the
     * crystal and the lunge are both back in reach.
     *
     * <p>It is a strike that takes time, and it goes through the ordinary melee path like
     * any other swing of hers.
     */
    private EnemyAction repulse(CombatEntity self, GridPos target, EnemyAction onFoot) {
        if (!(onFoot instanceof EnemyAction.Attack swing) || isOnCooldown(CD_REPULSE)) return null;
        setCooldown(CD_REPULSE, hurt() ? 2 : 3);
        aimAt(self, target);
        return new EnemyAction.AttackWithKnockback(swing.damage(), REPULSE_TILES);
    }

    // ================================================================
    // The marks
    // ================================================================

    /**
     * Paint the floor with everything she has marked: the lane, with arrows down it, the
     * square she is diving on, with arrows out of it, and the ground her lightning will
     * strike, with none. One warning for all of it, since the base class holds one.
     */
    private void paint(CombatEntity self) {
        List<GridPos> tiles = new ArrayList<>();
        Map<GridPos, int[]> arrows = new HashMap<>();
        if (lane != null) {
            for (GridPos tile : lane.tiles()) {
                tiles.add(tile);
                arrows.put(tile, new int[]{lane.dx(), lane.dz()});
            }
        }
        for (GridPos tile : diveMark) {
            if (!tiles.contains(tile)) tiles.add(tile);
            if (!tile.equals(diveAt)) {
                arrows.put(tile, new int[]{
                    Integer.signum(tile.x() - diveAt.x()), Integer.signum(tile.z() - diveAt.z())});
            }
        }
        for (GridPos tile : storm) {
            if (!tiles.contains(tile)) tiles.add(tile);
        }
        pendingWarning = tiles.isEmpty() ? null : new BossWarning(self.getEntityId(),
            arrows.isEmpty() ? BossWarning.WarningType.TILE_HIGHLIGHT : BossWarning.WarningType.DIRECTIONAL,
            tiles, HELD, new EnemyAction.Idle(), MARK_COLOR).withArrows(arrows);
    }

    /** Whether a party member is standing on {@code tile}. */
    private static boolean playerOn(GridArena arena, GridPos tile) {
        return tile.equals(arena.getPlayerGridPos()) || arena.getAllPlayerGridPositions().contains(tile);
    }

    // ================================================================
    // The storm
    // ================================================================

    /**
     * Call one valkyrie of her honour guard, from two thirds of her health down, whenever
     * fewer than {@link #MAX_GUARD} of hers are on the floor and the call is ready. Null
     * when it is not time, or there is nowhere to put one. The call is the whole of her move.
     */
    private EnemyAction callHerGuard(CombatEntity self, GridArena arena) {
        if (!hurt() || isOnCooldown(CD_GUARD) || getAliveMinionCount() >= MAX_GUARD) return null;
        List<GridPos> tiles = landingTiles(self, arena, 1);
        // The search for anywhere at all does not know the room's shape. This does.
        tiles.removeIf(tile -> !open(arena, tile));
        if (tiles.isEmpty()) return null;
        setCooldown(CD_GUARD, phaseReached >= 3 ? GUARD_EVERY_STORM : GUARD_EVERY);
        announce(self, "§e✦ " + self.getDisplayName() + " calls a valkyrie of her honour guard to her side.");
        return new EnemyAction.SummonMinions(AetherMobs.VALKYRIE, 1, List.of(tiles.get(0)),
            guardHp(self.getMaxHp()), guardAttack(self.getAttackPower()), escortDefense(self.getDefense()));
    }

    /**
     * Mark where the lightning will fall on her next turn: three by three on her target, and
     * on every other player. Where they are standing now, and it stays there.
     */
    private void callLightning(GridArena arena, GridPos target) {
        List<GridPos> points = new ArrayList<>();
        points.add(target);
        for (GridPos player : arena.getAllPlayerGridPositions()) {
            if (!points.contains(player)) points.add(player);
        }
        Set<GridPos> ground = new LinkedHashSet<>();
        for (GridPos point : points) ground.addAll(square(arena, point));
        stormPoints = points;
        storm = new ArrayList<>(ground);
    }

    /**
     * Her move with what falls due this turn in front of it: last turn's lightning, and the
     * sweep of a run down a lane three wide.
     *
     * <p>Nobody takes two hits from one turn of hers. The lightning is one area hit over
     * everything that was marked, so someone standing where two squares cross takes one
     * bolt. The tile her blade is for is left out of it. And the sweep leaves out whatever
     * the lightning covers, since the bolt is the harder of the two.
     *
     * <p>A swing that rides with either is asked for as one with no throw to it. That is
     * the shape the engine walks through the ordinary melee path from inside a bundle. A
     * plain attack in one is resolved on the spot, with none of what a real swing goes through.
     */
    private EnemyAction withWhatFallsDue(CombatEntity self, GridArena arena, List<GridPos> bolts,
                                         List<GridPos> calledOn, EnemyAction move) {
        MobEntity mob = self.getMobEntity();
        if (!bolts.isEmpty() && mob != null && mob.getWorld() instanceof ServerWorld world) {
            for (GridPos point : calledOn) ThunderCrystal.bolt(world, arena, point);
        }
        List<GridPos> ground = new ArrayList<>(bolts);
        if (struck != null) ground.remove(struck);
        List<GridPos> swept = new ArrayList<>(sweep);
        swept.removeAll(ground);
        // Asked for only with someone standing in it: an area hit on empty floor is noise.
        if (swept.stream().noneMatch(tile -> playerOn(arena, tile))) swept.clear();
        if (ground.isEmpty() && swept.isEmpty()) return move;

        List<EnemyAction> turn = new ArrayList<>();
        if (!ground.isEmpty()) {
            turn.add(new EnemyAction.TileAreaAttack(ground, ground.get(0), self.getAttackPower(), null));
        }
        if (!swept.isEmpty()) {
            turn.add(new EnemyAction.TileAreaAttack(swept, swept.get(0), sweepDamage(self.getAttackPower()), null));
        }
        if (move instanceof EnemyAction.CompositeAction bundle) {
            turn.addAll(bundle.actions());
        } else if (move instanceof EnemyAction.Attack swing) {
            turn.add(new EnemyAction.AttackWithKnockback(swing.damage(), 0));
        } else if (!(move instanceof EnemyAction.Idle)) {
            turn.add(move);
        }
        return turn.size() == 1 ? turn.get(0) : new EnemyAction.CompositeAction(List.copyOf(turn));
    }

    // ================================================================
    // The dive
    // ================================================================

    /**
     * Take to the air and mark the square she will come down on: three by three, on where
     * her target is standing now. It does not follow them.
     *
     * <p>She leaves the grid herself, here, and asks the engine for nothing. Its own action
     * for leaving the floor cannot share a turn with anything, and in the storm this turn
     * still has last turn's strikes to deliver. Off the grid she is in nobody's way and
     * nothing aimed at a tile finds her, and the guard is for whatever is aimed at her
     * some other way.
     */
    private EnemyAction takeWing(CombatEntity self, GridArena arena, GridPos target) {
        setCooldown(CD_DIVE, DIVE_EVERY);
        diveAt = target;
        diveMark = square(arena, target);

        if (arena.getOccupant(self.getGridPos()) == self) arena.removeEntity(self);
        self.setOnCeiling(true);
        self.setDamageImmune(true, ALOFT_HINT);
        AIRBORNE.values().removeIf(held -> held.get() == null);
        AIRBORNE.put(self.getEntityId(), new WeakReference<>(this));
        MobEntity mob = self.getMobEntity();
        if (mob != null) {
            // Out of sight overhead, the way the engine lifts anything it takes off the floor.
            mob.setInvisible(true);
            mob.requestTeleport(mob.getX(), mob.getY() + 100, mob.getZ());
        }
        announce(self, "§e✦ " + self.getDisplayName() + " takes to the air! She comes down on the marked"
            + " ground. Strike a thunder crystal across it to bring her down hard.");
        return new EnemyAction.Idle();
    }

    /**
     * Come down on the square she marked. Everyone standing in it takes her attack, as one
     * area hit, and is thrown clear of it.
     *
     * <p>In the order it has to happen: she lands, then the hit, then the throw. Thrown
     * first, they would be out of the square before the hit was counted. The throw that
     * goes with the landing is her target's, or failing them the first player in the
     * square. Anyone else in it is thrown by {@link #wantsAnotherAction}, one to an action.
     */
    private EnemyAction land(CombatEntity self, GridArena arena, GridPos target) {
        GridPos centre = diveAt;
        List<GridPos> mark = diveMark;
        GridPos landing = touchDown(self, arena);
        turnsSinceBlink = 0;
        announce(self, "§c✦ " + self.getDisplayName() + " dives!");

        List<EnemyAction> turn = new ArrayList<>();
        turn.add(new EnemyAction.CeilingDrop(landing, 0));
        turn.add(new EnemyAction.TileAreaAttack(mark, centre, self.getAttackPower(), null));

        List<GridPos> caught = new ArrayList<>();
        if (mark.contains(target) && playerOn(arena, target)) caught.add(target);
        for (GridPos tile : mark) {
            if (playerOn(arena, tile) && !caught.contains(tile)) caught.add(tile);
        }
        throwsOwed.clear();
        for (GridPos tile : caught) {
            // Out of the square, or away from her when they stood on the middle of it.
            GridPos from = tile.equals(centre) ? landing : centre;
            int dx = Integer.signum(tile.x() - from.x());
            int dz = Integer.signum(tile.z() - from.z());
            if (dx != 0 || dz != 0) throwsOwed.add(new Thrown(tile, dx, dz));
        }
        if (!throwsOwed.isEmpty()) {
            Thrown first = throwsOwed.remove(0);
            self.setPendingStrikeTile(first.tile());
            turn.add(new EnemyAction.ForcedMovement(-1, first.dx(), first.dz(), DIVE_THROW));
        }
        cooldownsAtLanding = new HashMap<>(cooldowns);
        return new EnemyAction.CompositeAction(List.copyOf(turn));
    }

    /**
     * The end of a flight, however it ends: the mark is spent, she can be hurt again, and
     * the tile she comes down on is settled. The middle of the square, or the open tile
     * nearest it when somebody is standing there.
     */
    private GridPos touchDown(CombatEntity self, GridArena arena) {
        // Had the engine walked her while she was up, after a target it could not see, she
        // would be standing on a tile already. Off it first, or she ends up on two.
        if (arena.getOccupant(self.getGridPos()) == self) arena.removeEntity(self);
        GridPos landing = landingTile(arena, diveAt);
        diveAt = null;
        diveMark = List.of();
        AIRBORNE.remove(self.getEntityId());
        self.setDamageImmune(false, null);
        return landing;
    }

    /** The middle of the square if she can stand on it, and otherwise the open tile nearest it. */
    static GridPos landingTile(GridArena arena, GridPos centre) {
        if (open(arena, centre)) return centre;
        GridPos best = centre;
        int bestSteps = Integer.MAX_VALUE;
        for (int x = 0; x < arena.getWidth(); x++) {
            for (int z = 0; z < arena.getHeight(); z++) {
                GridPos tile = new GridPos(x, z);
                if (!open(arena, tile)) continue;
                int steps = centre.manhattanDistance(tile);
                if (steps < bestSteps) {
                    bestSteps = steps;
                    best = tile;
                }
            }
        }
        return best;
    }

    /**
     * Brought down by a crystal of her own, struck across the ground she had marked: she
     * lands where she meant to, now, takes a tenth of her health for it, and loses her
     * next turn getting up.
     *
     * <p>Done here and not asked of the engine, because it happens on a player's turn, in
     * the middle of their strike, and there is no action of hers for the engine to run.
     */
    void broughtDown(ProjectileImpactHandler.Context ctx, CombatEntity crystal) {
        CombatEntity self = queen;
        GridArena arena = ctx.arena();
        GridPos landing = touchDown(self, arena);
        self.setOnCeiling(false);
        self.setGridPos(landing);
        arena.placeEntity(self);
        MobEntity mob = self.getMobEntity();
        if (mob != null) {
            mob.setInvisible(false);
            BlockPos block = arena.gridToBlockPos(landing);
            mob.requestTeleport(block.getX() + 0.5, arena.getEntityY(landing), block.getZ() + 0.5);
            // The engine's own daze, for the look of it. The turn she loses is kept here:
            // a stun asked of the engine is one a boss shrugs off half the time.
            MobAnimations.set(mob, AnimState.STUNNED);
        }
        throwsOwed.clear();
        lane = null;
        storm = List.of();
        stormPoints = List.of();
        down = true;
        paint(self);

        int raw = groundingDamage(crystal.getAttackPower(), self.getMaxHp());
        if (self.isSoaked()) raw *= 2;
        int dealt = ctx.damage(self, raw);
        ctx.message("§e✦ The crystal bursts across the ground she marked! " + self.getDisplayName()
            + " comes down hard for " + dealt + ", and is a turn getting up.");
    }

    /**
     * Queens in the air, by entity id. A crystal knows who threw it and nothing else, and a
     * Queen in the air is on no grid to be looked up on, so this is how one struck while she
     * is up finds its way back to her fight. Held weakly: a fight abandoned in the middle of
     * a dive is not kept alive by it.
     */
    private static final Map<Integer, WeakReference<ValkyrieQueenAI>> AIRBORNE = new ConcurrentHashMap<>();

    /** The Queen who threw a crystal, if she is in the air. */
    static ValkyrieQueenAI airborne(CombatEntity crystal) {
        WeakReference<ValkyrieQueenAI> held = AIRBORNE.get(crystal.getProjectileOwnerId());
        ValkyrieQueenAI ai = held != null ? held.get() : null;
        return ai != null && ai.diveAt != null ? ai : null;
    }

    /**
     * Throw a crystal, if one is ready, fewer than {@code limit} are already in the air and
     * there is somewhere to throw it from. If she is walking {@code onFoot}, the walk goes
     * with it.
     */
    private EnemyAction throwCrystal(CombatEntity self, GridArena arena, GridPos target,
                                     EnemyAction onFoot, int limit) {
        if (isOnCooldown(CD_CRYSTAL) || crystalsInFlight(self, arena) >= limit) return null;
        // She throws without breaking stride, and a walk is all that goes with a throw: the
        // turn machine runs one action that takes time per bundle. A swing never does.
        // Beside her target there is nowhere to throw from in any case.
        boolean onTheMove = EnemyAction.drivesTurn(onFoot);
        GridPos launch = launchTile(self, arena, target, onTheMove ? pathOf(onFoot) : List.of());
        // Nowhere to throw from: the crystal stays in her hand, and its cooldown is not spent.
        if (launch == null) return null;

        setCooldown(CD_CRYSTAL, hurt() ? 2 : 3);
        int[] dir = getDirectionToward(launch, target);
        EnemyAction crystal = new EnemyAction.SpawnProjectile(CRYSTAL_BODY, List.of(launch),
            List.<int[]>of(new int[]{dir[0], dir[1]}), CRYSTAL_HP, crystalDamage(self.getAttackPower()), 0,
            THUNDER_CRYSTAL);
        return onTheMove ? new EnemyAction.CompositeAction(List.of(crystal, onFoot)) : crystal;
    }

    /**
     * Where a crystal leaves her hand: the tile toward her target if it is free, then the two
     * beside it, then anywhere next to her. Never a tile she is about to walk through, since
     * the crystal would be standing in it by the time she got there.
     *
     * <p>And never one without a head start. A crystal takes its first two steps the turn it
     * is thrown, before anyone can strike it, so it starts at least
     * {@link #CRYSTAL_HEAD_START} steps from everyone it could go for: her target, the rest
     * of the party, and whatever fights beside them. Null when no tile next to her is that
     * far off, which beside her target none is.
     */
    private GridPos launchTile(CombatEntity self, GridArena arena, GridPos target, List<GridPos> keepClear) {
        GridPos me = self.getGridPos();
        int[] dir = getDirectionToward(me, target);
        GridPos ahead = new GridPos(me.x() + dir[0], me.z() + dir[1]);
        List<GridPos> order = new ArrayList<>();
        order.add(ahead);
        // Swapping the axes of a cardinal step gives the step across it.
        order.add(new GridPos(ahead.x() + dir[1], ahead.z() + dir[0]));
        order.add(new GridPos(ahead.x() - dir[1], ahead.z() - dir[0]));
        order.addAll(AIUtils.getAdjacentTiles(arena, me));
        // Everyone a crystal could go for: the party, and her target when that is something
        // else (an ally that has drawn her). Not every pet. A crystal never turns aside for
        // one, and a wolf parked beside her would otherwise stop her throwing at all.
        List<GridPos> inReach = new ArrayList<>(arena.getAllPlayerGridPositions());
        if (!inReach.contains(target)) inReach.add(target);
        for (GridPos tile : order) {
            if (tile.equals(me) || !open(arena, tile) || keepClear.contains(tile)) continue;
            if (hasHeadStart(tile, inReach)) return tile;
        }
        return null;
    }

    /**
     * Whether a crystal thrown from {@code tile} cannot arrive on its first move. It homes
     * one square at a time, never across a corner, so the steps between two tiles are what
     * it has to cover, and a wall in the way only adds to them.
     */
    static boolean hasHeadStart(GridPos tile, List<GridPos> anyoneItCouldGoFor) {
        return AIUtils.minThreatDistance(tile, anyoneItCouldGoFor) >= CRYSTAL_HEAD_START;
    }

    private static List<GridPos> pathOf(EnemyAction action) {
        if (action instanceof EnemyAction.Move move) return move.path();
        if (action instanceof EnemyAction.MoveAndAttack attack) return attack.path();
        return List.of();
    }

    /** Her crystals still homing on the party. One that has burnt out is no longer counted. */
    private static int crystalsInFlight(CombatEntity self, GridArena arena) {
        int count = 0;
        for (CombatEntity e : arena.getOccupants().values()) {
            if (e.isAlive() && e.isProjectile() && THUNDER_CRYSTAL.equals(e.getProjectileType())
                    && e.getProjectileOwnerId() == self.getEntityId() && !e.isProjectileRedirected()
                    && !burntOut(e)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Give a newly thrown crystal its aim and its look.
     *
     * <p>The engine decides which projectiles home from a fixed list of its own types, and
     * anything else flies straight. This is the one moment the crystal can be handed the
     * homing brain instead: it has just been placed, and nothing has moved it yet.
     */
    @Override
    public void registerSpawnedProjectile(int entityId) {
        super.registerSpawnedProjectile(entityId);
        CombatEntity crystal = occupant(entityId);
        if (crystal == null || !THUNDER_CRYSTAL.equals(crystal.getProjectileType())) return;
        hunting.put(entityId, 0);
        crystal.setAiOverrideKey(SEEKING);
        crystal.setBossDisplayName("Thunder Crystal");
        MobEntity body = crystal.getMobEntity();
        if (body != null) {
            body.setSilent(true);
            // Seen as a crystal, not as the creature that carries it across the grid. If the
            // crystal cannot be shown the carrier stays in plain sight: an odd thunder
            // crystal is better than one nobody can see.
            if (CrystalLook.dress(crystal)) {
                body.setInvisible(true);
                scale(body, 0.01);
            } else {
                body.setGlowing(true);
                scale(body, CRYSTAL_SCALE);
            }
        }
        if (queen != null) {
            announce(queen, "§e✦ A thunder crystal hunts you. Strike it and it flies straight away from you:"
                + " line her up behind it, and it bursts on her.");
        }
    }

    /**
     * Mark the lane and take aim. Returns null when her target is not {@link #LUNGE_MIN} to
     * {@link #LUNGE_MAX} tiles down a clear straight line. She holds still for it: the run
     * is her next turn.
     */
    private EnemyAction windUpLunge(CombatEntity self, GridArena arena, GridPos target) {
        if (isOnCooldown(CD_LUNGE)) return null;
        List<GridPos> run = lungeRun(self, arena, target);
        if (run == null) return null;

        GridPos me = self.getGridPos();
        setCooldown(CD_LUNGE, hurt() ? 2 : 3);
        int dx = Integer.signum(target.x() - me.x());
        int dz = Integer.signum(target.z() - me.z());
        Lunge aimed = new Lunge(me, run, target, dx, dz, List.of());
        if (hurt()) {
            // Three wide from two thirds down: one step sideways no longer clears it.
            List<GridPos> sides = new ArrayList<>();
            for (GridPos tile : aimed.middle()) {
                for (GridPos beside : aimed.beside(tile)) {
                    if (arena.isInBounds(beside)) sides.add(beside);
                }
            }
            aimed = new Lunge(me, run, target, dx, dz, sides);
        }
        lane = aimed;
        announce(self, "§e✦ " + self.getDisplayName() + " levels her blade down the lane!");
        return new EnemyAction.Idle();
    }

    /**
     * Run the lane she marked, as far as the first player standing in the middle of it, and
     * strike them. With nobody there she crosses it and strikes nobody: someone who stepped
     * clear and is standing beside where she stops was shown nothing, so nothing lands on them.
     *
     * <p>Where the lane is three wide, the tiles either side of what her blade passed are
     * left in {@link #sweep} for the turn: half her blade to anyone standing on one.
     *
     * <p>Null when she is no longer standing where she took aim, shoved or pulled since.
     * The lane on the floor is then not the one she would run, so she does not run it, and
     * the turn is still hers to use.
     */
    private EnemyAction runTheLane(CombatEntity self, GridArena arena, Lunge marked) {
        if (!self.getGridPos().equals(marked.from())) return null;
        List<GridPos> ran = new ArrayList<>();
        List<GridPos> passed = new ArrayList<>();
        EnemyAction run = null;
        for (GridPos tile : marked.middle()) {
            passed.add(tile);
            if (playerOn(arena, tile)) {
                aimAt(self, tile);
                int damage = ValkyrieAI.lungeDamage(self.getAttackPower(), ran.size());
                run = ran.isEmpty() ? new EnemyAction.Attack(damage) : new EnemyAction.MoveAndAttack(ran, damage);
                break;
            }
            // The last tile is the one she aimed at. She stops short of it, as she would
            // have with someone on it, and short of anything else that has come to stand here.
            if (tile.equals(marked.end()) || !open(arena, tile)) break;
            ran.add(tile);
        }
        List<GridPos> swept = new ArrayList<>();
        for (GridPos tile : passed) {
            for (GridPos beside : marked.beside(tile)) {
                if (marked.sides().contains(beside)) swept.add(beside);
            }
        }
        sweep = swept;
        if (run != null) return run;
        return ran.isEmpty() ? new EnemyAction.Idle() : new EnemyAction.Move(ran);
    }

    /** The tiles she crosses to reach her target, or null when there is no lunge to make. */
    static List<GridPos> lungeRun(CombatEntity self, GridArena arena, GridPos target) {
        GridPos me = self.getGridPos();
        int dist = me.manhattanDistance(target);
        if (dist < LUNGE_MIN || !AIUtils.hasCardinalLOS(arena, me, target, LUNGE_MAX)) {
            return null;
        }
        int dx = Integer.signum(target.x() - me.x());
        int dz = Integer.signum(target.z() - me.z());
        List<GridPos> run = new ArrayList<>();
        for (int i = 1; i < dist; i++) {
            GridPos step = new GridPos(me.x() + dx * i, me.z() + dz * i);
            // The line check only knows about enemies. A second player or a patch of fire in
            // the lane stops the lunge just the same.
            if (arena.isOccupied(step) || AIUtils.isHazardTile(arena, step)) return null;
            run.add(step);
        }
        return run;
    }

    /**
     * Blink to the far side of her target, when she could not have walked up to it this turn
     * or has not blinked in a while. It is only how she gets there: a strike on arrival
     * would land on someone who was shown nothing.
     */
    private EnemyAction blink(CombatEntity self, GridArena arena, GridPos target) {
        if (isOnCooldown(CD_BLINK) || self.minDistanceTo(target) <= 1) return null;
        if (reaches && turnsSinceBlink < REPOSITION_EVERY) return null;
        GridPos landing = flankTile(self, arena, target);
        if (landing == null) return null;
        setCooldown(CD_BLINK, blinkCooldown());
        turnsSinceBlink = 0;
        return new EnemyAction.Teleport(landing);
    }

    private int blinkCooldown() {
        return hurt() ? 2 : 3;
    }

    /**
     * The open tile beside the target that is furthest from where she stands: the far side.
     * Arriving behind someone is the point of not walking there.
     */
    static GridPos flankTile(CombatEntity self, GridArena arena, GridPos target) {
        GridPos me = self.getGridPos();
        GridPos best = null;
        int bestDist = -1;
        for (int[] dir : CARDINALS) {
            GridPos tile = new GridPos(target.x() + dir[0], target.z() + dir[1]);
            if (!open(arena, tile)) continue;
            int dist = me.manhattanDistance(tile);
            if (dist > bestDist) {
                bestDist = dist;
                best = tile;
            }
        }
        return best;
    }

    /** A tile something can be put on: inside the grid, empty, solid underfoot and harmless. */
    static boolean open(GridArena arena, GridPos tile) {
        if (!arena.isInBounds(tile) || arena.isOccupied(tile)) return false;
        GridTile ground = arena.getTile(tile);
        return ground != null && ground.isWalkable() && !ground.isWater()
            && !AIUtils.isHazardTile(arena, tile);
    }

    private CombatEntity occupant(int entityId) {
        if (arena == null) return null;
        for (CombatEntity e : arena.getOccupants().values()) {
            if (e.getEntityId() == entityId) return e;
        }
        return null;
    }

    // ================================================================
    // The live world: the party, its medals, and what it is told
    // ================================================================

    /** What striking her guard says: her challenge, and how much of it is still to do. */
    private static String guardHint(CombatEntity self, int tribute) {
        return voice(self.getDisplayName(), LINE_CHALLENGE)
            + " §eTribute " + tribute + " of " + MEDALS_REQUIRED + ". " + stillToDo(tribute);
    }

    /** Victory Medals in the inventories of everyone in this fight. */
    protected int medalsHeld(CombatEntity self) {
        Item medal = AetherCompat.lookupItem(MEDAL);
        if (medal == null) return 0;
        int held = 0;
        for (ServerPlayerEntity member : party(self)) {
            var inventory = member.getInventory();
            for (int i = 0; i < inventory.size(); i++) {
                ItemStack stack = inventory.getStack(i);
                if (stack.isOf(medal)) held += stack.getCount();
            }
        }
        return held;
    }

    /** Take {@code count} medals from the party, from whoever has them. */
    protected void takeMedals(CombatEntity self, int count) {
        Item medal = AetherCompat.lookupItem(MEDAL);
        if (medal == null) return;
        int owed = count;
        for (ServerPlayerEntity member : party(self)) {
            var inventory = member.getInventory();
            for (int i = 0; i < inventory.size() && owed > 0; i++) {
                ItemStack stack = inventory.getStack(i);
                if (!stack.isOf(medal)) continue;
                int taken = Math.min(owed, stack.getCount());
                stack.decrement(taken);
                owed -= taken;
            }
        }
    }

    /** Say something to everyone in this fight. */
    protected void announce(CombatEntity self, String message) {
        List<ServerPlayerEntity> members = party(self);
        if (members.isEmpty()) return;
        // The fight's own channel, which reaches the whole party: asking through one member is all of them.
        CombatManager.getActiveCombat(members.get(0).getUuid()).sendMessage(message);
    }

    /**
     * The players in this fight: everyone in her world whose fight has her in it. A world
     * can hold more than one fight, so being nearby is not enough.
     */
    private static List<ServerPlayerEntity> party(CombatEntity self) {
        MobEntity mob = self != null ? self.getMobEntity() : null;
        if (mob == null || !(mob.getWorld() instanceof ServerWorld world)) return List.of();
        List<ServerPlayerEntity> members = new ArrayList<>();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()) continue;
            // Asked first because getActiveCombat never answers null: it makes a manager for
            // whoever asks, and a bystander should not be handed one.
            if (!CombatManager.isEngaged(player.getUuid())) continue;
            CombatManager fight = CombatManager.getActiveCombat(player.getUuid());
            if (fight.isActive() && fight.getEnemies() != null && fight.getEnemies().contains(self)) {
                members.add(player);
            }
        }
        return members;
    }

    // ================================================================
    // The thunder crystal
    // ================================================================

    /** What her crystal does when it lands, and when it is struck. */
    static final class ThunderCrystal implements ProjectileImpactHandler {

        /**
         * Struck by a player, and settled here, the moment it is struck. It flies straight
         * away from whoever struck it, and what that line finds is worked out now, against
         * where everything stands now. Left to fly as a thing on the board it would arrive
         * a turn late, at a Queen who had moved, and a shot that was lined up would miss.
         *
         * <p>It bursts on the first of hers its line comes within a tile of. With her in
         * the air, a line that crosses the square she has marked brings her down instead.
         * With nothing on the line it flies off and is gone.
         */
        @Override
        public boolean onDeflect(Context ctx) {
            CombatEntity crystal = ctx.projectile();
            GridArena arena = ctx.arena();
            GridPos struckAt = crystal.getGridPos();
            ValkyrieQueenAI diving = airborne(crystal);
            // Everyone of hers in the fight who is on the floor to be hit. A Queen in the
            // air still has a tile to her name, and is not standing on it.
            List<CombatEntity> hers = new ArrayList<>();
            for (CombatEntity one : ctx.enemiesNear(4096)) {
                if (!one.isOnCeiling()) hers.add(one);
            }
            // Whoever is acting is whoever struck it.
            Shot shot = shot(arena, struckAt, arena.getPlayerGridPos(), hers,
                diving == null ? List.of() : diving.diveMark);
            // Seen to go: a crystal flies the line and bursts where the line ends. Without it
            // a struck crystal simply vanished, and read as one killed with a single blow.
            // What it did at the far end is settled below, now, as everything stands now.
            GridPos end = shot.end(struckAt);
            CrystalLook.fly(ctx, arena, struckAt, end, () -> bolt(ctx.world(), arena, end));

            if (shot.grounds()) {
                diving.broughtDown(ctx, crystal);
            } else if (shot.hit() != null) {
                CombatEntity enemy = shot.hit();
                int raw = reflectedDamage(crystal.getAttackPower(), enemy.getMaxHp(), enemy.isBoss());
                // The doubling every lightning source honours on a Soaked target.
                if (enemy.isSoaked()) raw *= 2;
                String name = enemy.getDisplayName();
                // Through the ordinary door, so a Queen still holding her guard shrugs it off.
                int dealt = ctx.damage(enemy, raw);
                ctx.message(dealt > 0
                    ? "§e✦ The crystal bursts on " + name + " for " + dealt + "!"
                    : "§7The crystal bursts against " + name + " and does nothing.");
            } else {
                ctx.message("§7The thunder crystal flies off and is gone."
                    + " §8Stand so she is in line behind it before you strike.");
            }
            return true;
        }

        @Override
        public void onImpact(Context ctx) {
            CombatEntity crystal = ctx.projectile();
            GridPos at = ctx.impactPos();
            if (burntOut(crystal)) {
                ctx.message("§7A thunder crystal burns out.");
                return;
            }
            bolt(ctx.world(), ctx.arena(), at);
            // A struck crystal is settled as it is struck, so none flies on. Were one ever
            // sent astray some other way, it would be nobody's to be hurt by.
            if (ctx.redirected()) return;
            // Found before the hit, to know afterwards whether they are still on their feet.
            ServerPlayerEntity victim = AetherMobs.playerAt(crystal, ctx.arena(), at);
            if (ctx.hitPlayers(0, crystal.getAttackPower(), null, 0, 0,
                    "§e✦ The thunder crystal strikes %s for %d!")) {
                return;
            }
            throwBack(ctx, victim);
        }

        /**
         * Throw whoever the crystal hit one tile further along its flight.
         *
         * <p>Only onto plain ground. The shove the engine offers moves a player and applies
         * nothing of what they land in, so a tile that would hurt them is left alone and
         * they stay where they are.
         */
        private static void throwBack(Context ctx, ServerPlayerEntity victim) {
            // A player this hit put down is held at one health, and is not to be moved.
            if (victim == null || victim.getHealth() <= 1.0f || ctx.combat().wasLastHitAvoided()) return;
            GridPos from = ctx.projectile().getGridPos();
            GridPos at = ctx.impactPos();
            int dx = Integer.signum(at.x() - from.x());
            int dz = Integer.signum(at.z() - from.z());
            if ((dx == 0) == (dz == 0)) return;
            if (!open(ctx.arena(), new GridPos(at.x() + dx, at.z() + dz))) return;
            // The shove reads its direction off the player the arena is tracking, who in a
            // party need not be the one who was hit. Naming a point one step behind that
            // player, along the flight, gives the flight's direction whoever it is.
            GridPos tracked = ctx.arena().getPlayerGridPos();
            if (tracked == null) return;
            ctx.combat().shovePlayerFrom(new GridPos(tracked.x() - dx, tracked.z() - dz), 1);
        }

        private static void bolt(ServerWorld world, GridArena arena, GridPos at) {
            if (world == null || arena == null || at == null) return;
            BlockPos pos = arena.gridToBlockPos(at);
            ProjectileSpawner.spawnImpact(world, pos, "lightning");
            world.playSound(null, pos, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                SoundCategory.HOSTILE, 0.8f, 1.4f);
        }
    }

    /**
     * What a thunder crystal looks like: a lit cube stood on one corner, in place of the
     * creature that carries it on the grid. The engine keeps a projectile's visual in step
     * with its carrier and clears the two away together, but picks that visual from a fixed
     * list of its own types. This hangs one on the same link.
     */
    private static final class CrystalLook {

        private static final float SIZE = 0.5f;
        /** The least a struck crystal's flight lasts, in ticks, so a short one is still seen. */
        private static final int SHORTEST_FLIGHT = 4;

        /** A crystal at a point in the world, or null if the world would not take it. */
        private static net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity body(
                ServerWorld world, double x, double y, double z) {
            var shard = new net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity(
                net.minecraft.entity.EntityType.ITEM_DISPLAY, world);
            shard.refreshPositionAndAngles(x, y, z, 0f, 0f);
            ((com.crackedgames.craftics.mixin.ItemDisplayInvoker) shard).craftics$setItemStack(
                new ItemStack(net.minecraft.item.Items.SEA_LANTERN));
            var look = (com.crackedgames.craftics.mixin.DisplayEntityInvoker) shard;
            // Stood on one corner, a cube reads as a crystal rather than as a block.
            look.craftics$setTransformation(new net.minecraft.util.math.AffineTransformation(
                new org.joml.Vector3f(0f, 0f, 0f),
                new org.joml.Quaternionf().rotateXYZ(0.7854f, 0f, 0.6155f),
                new org.joml.Vector3f(SIZE, SIZE, SIZE),
                new org.joml.Quaternionf()));
            look.craftics$setTeleportDuration(2);
            look.craftics$setBrightness(new net.minecraft.entity.decoration.Brightness(15, 15));
            shard.setGlowing(true);
            shard.addCommandTag("craftics_arena");
            return world.spawnEntity(shard) ? shard : null;
        }

        /** Hang a crystal on the carrier of {@code crystal}. False if it could not be shown. */
        static boolean dress(CombatEntity crystal) {
            MobEntity carrier = crystal.getMobEntity();
            if (carrier == null || !(carrier.getWorld() instanceof ServerWorld world)) return false;
            var shard = body(world, carrier.getX(), carrier.getY() + 0.5, carrier.getZ());
            if (shard == null) return false;
            shard.addCommandTag("craftics_visual_projectile");
            crystal.setVisualProjectileEntityId(shard.getId());
            return true;
        }

        /**
         * The flight of a struck crystal, from the tile it was struck on to the end of its
         * line: sparks down the line at once, and a crystal that crosses it and is gone.
         * {@code onArrival} runs as it gets there, or at once when there is no world to
         * show it in.
         */
        static void fly(ProjectileImpactHandler.Context ctx, GridArena arena, GridPos from, GridPos to,
                        Runnable onArrival) {
            ServerWorld world = ctx.world();
            CombatManager combat = ctx.combat();
            if (world == null || combat == null || arena == null || from == null || to == null
                    || from.equals(to)) {
                onArrival.run();
                return;
            }
            BlockPos a = arena.gridToBlockPos(from);
            BlockPos b = arena.gridToBlockPos(to);
            double sx = a.getX() + 0.5, sy = arena.getEntityY(from) + 0.5, sz = a.getZ() + 0.5;
            double ex = b.getX() + 0.5, ey = arena.getEntityY(to) + 0.5, ez = b.getZ() + 0.5;
            int tiles = Math.max(Math.abs(to.x() - from.x()), Math.abs(to.z() - from.z()));
            for (int i = 0; i <= tiles * 2; i++) {
                double t = i / (tiles * 2.0);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK,
                    sx + (ex - sx) * t, sy + (ey - sy) * t, sz + (ez - sz) * t, 2, 0.05, 0.05, 0.05, 0.0);
            }
            var shard = body(world, sx, sy, sz);
            if (shard == null) {
                onArrival.run();
                return;
            }
            combat.glideThenRun(shard, sx, sy, sz, ex, ey, ez, Math.max(SHORTEST_FLIGHT, tiles), () -> {
                shard.discard();
                onArrival.run();
            });
        }
    }

    // ================================================================
    // Her last words
    // ================================================================

    /** Her defeat line, once. The engine asks for it as it announces her fall. */
    @Override
    public String getLastWords(CombatEntity self) {
        AIRBORNE.remove(self.getEntityId());
        if (!owesFarewell(self)) return null;
        self.setAiMemory(FAREWELL_SAID, 1);
        return voice(self.getDisplayName(), LINE_DEFEATED);
    }

    // ================================================================
    // Spawn hook: what NoAI leaves running
    // ================================================================

    /**
     * Make the live Queen safe to stand in an arena.
     *
     * <p>Her own tick still runs there, and most of it is harmless by construction: she only
     * breaks blocks toward a target, and nothing in an arena ever gives her one. What is
     * left is her two states. "Ready" is what lets a hit start the Aether's own fight, with
     * its own boss bar and music, and "boss fight" is what puts that bar on screen. Both
     * off is the one combination in which her entity does nothing at all, so both are put
     * off here whatever she was saved or spawned with.
     */
    private static void prepare(ServerWorld world, MobEntity mob, CombatEntity entity) {
        mob.setTarget(null);
        setFlag(mob, "setReady", false);
        setFlag(mob, "setBossFight", false);

        // Past here is her own fight. In another one that merely borrows her body, the
        // name, the size and the guard are that fight's to decide.
        if (!BOSS_KEY.equals(entity.getAiKey())) return;
        // Up from the moment she stands there, not from her first turn, or the party's
        // opening move would land on a Queen who has not been asked for anything yet.
        // Not once the tribute is paid: this runs again when the engine replaces an entity
        // it found dead mid-fight, and a guard raised then is one nothing would ever lower.
        if (!(entity.getAiInstance() instanceof ValkyrieQueenAI ai) || ai.isGuardUp()) {
            int paid = entity.getAiInstance() instanceof ValkyrieQueenAI queen ? queen.tributeSoFar() : 0;
            entity.setDamageImmune(true, guardHint(entity, paid));
        }
        // The nameplate also feeds the name she signs her own chat lines with.
        mob.setCustomName(Text.literal("§b§l" + entity.getDisplayName()));
        mob.setCustomNameVisible(true);
        scale(mob, QUEEN_SCALE);
    }

    /** Call one of her own boolean setters by name. */
    private static void setFlag(MobEntity mob, String setter, boolean value) {
        try {
            mob.getClass().getMethod(setter, boolean.class).invoke(mob, value);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (REPORTED.add(setter)) {
                CrafticsMod.LOGGER.warn("[Craftics × Aether] could not call {} on the Valkyrie Queen. "
                    + "The Aether may have renamed it.", setter);
            }
        }
    }

    private static void scale(MobEntity mob, double value) {
        //? if <=1.21.1 {
        var scale = mob.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_SCALE);
        //?} else {
        /*var scale = mob.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.SCALE);
        *///?}
        if (scale != null) scale.setBaseValue(value);
    }

    /** Things already said once. A spawn hook runs per boss, and none of these is worth repeating. */
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
}
