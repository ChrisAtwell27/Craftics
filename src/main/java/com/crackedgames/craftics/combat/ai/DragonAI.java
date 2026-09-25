package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.boss.BossAI;
import com.crackedgames.craftics.combat.ai.boss.BossWarning;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Dragon's Nest Boss - "The Ender Dragon" (backgroundBoss approach).
 *
 * ─── Tiers ────────────────────────────────────────────────────────────
 *  The dragon's health splits into three tiers, and each one throws more at once:
 *   • Above 2/3 HP - one attack per volley, a volley every other turn, perches after 4.
 *   • 2/3 and below - enraged: two attacks per volley, +3 damage, perches after 3.
 *   • 1/3 and below - three attacks per volley, and the next volley is telegraphed on the
 *     same turn the last one lands, so there is no free turn between them.
 *  Crossing either line forces a perch.
 *
 * ─── Volleys (off-stage) ───────────────────────────────────────────────
 *  The dragon is parked off-stage and each volley takes the next attacks from a rotation:
 *   • Breath Wave - a 3-wide band that rolls in from the nearest edge to the player's tile.
 *   • Swoop - a 3-wide corridor the full length of the arena; its middle lane is left
 *     clouded with harming breath for 2 turns.
 *   • Fireball - a plus on the player's tile, left clouded for 3 turns.
 *   • Breath Cross - the player's full row AND column.
 *  A volley is ONE telegraph and ONE hit: every tile it warns is a tile it damages, anyone
 *  inside is hit once however many attacks overlap there, and each attack's fire or cloud
 *  lands only on its own tiles, all inside the warning.
 *
 * ─── Fire and breath ──────────────────────────────────────────────────
 *  Fire goes in through the arena's burn cycle ({@link EnemyAction.IgniteTiles}): soul fire
 *  that spreads a ring per turn, needs no fuel, and restores end stone behind it. The breath
 *  clouds ({@link EnemyAction.BreathCloud}) are the vanilla dragon's harming breath: magic that
 *  no armor, dodge, Fire Resistance or Piglin Head stops, so the fire counters don't answer it.
 *
 * ─── Perch State (2 turns above 2/3, 3 below) ─────────────────────────
 *  Dragon visible + targetable on a 3x7 cluster of centre tiles. As it lands it calls its
 *  brood - Endermen, Shulkers and Phantoms - topping the adds it has summoned back up to
 *  3 / 5 / 7 by tier.
 *   • Wing Buffet (odd turns) - pushes player 3 tiles away.
 *   • Tail Slam (even turns) - telegraphed radius-3 AoE.
 */
public class DragonAI extends BossAI {
    @Override public int getGridSize() { return 1; } // backgroundBoss, size managed manually

    /** Width and length of the perch landing zone, centred on the arena. */
    public static final int PERCH_W = 3;
    public static final int PERCH_H = 7;

    /** The attacks a volley draws from, in order. */
    public enum Move {
        BREATH_WAVE("Breath Wave"), SWOOP("Swoop"), FIREBALL("Fireball"), BREATH_CROSS("Breath Cross");

        public final String label;

        Move(String label) { this.label = label; }
    }

    private static final Move[] ROTATION = { Move.BREATH_WAVE, Move.SWOOP, Move.FIREBALL, Move.BREATH_CROSS };

    /** Turns each breath cloud hangs for. */
    private static final int FIREBALL_CLOUD_TURNS = 3;
    private static final int SWOOP_CLOUD_TURNS = 2;

    /** The brood: Dragon's Nest's own hostile pool, at its biome weights and stats (hp, atk, def). */
    private static final String[] BROOD_TYPES = { "minecraft:enderman", "minecraft:shulker", "minecraft:phantom" };
    private static final int[] BROOD_WEIGHTS = { 5, 4, 3 };
    private static final int[][] BROOD_STATS = { { 22, 9, 2 }, { 18, 8, 2 }, { 14, 7, 1 } };
    /** No add is called in closer than this to a player, so a perch never opens with a free hit. */
    private static final int BROOD_MIN_DISTANCE = 3;

    public enum State { ATTACKING, PERCHING }

    private final Random rng;
    private State state = State.ATTACKING;
    /** 1 above 2/3 HP, 2 at 2/3 and below, 3 at 1/3 and below. Never goes back down. */
    private int tier = 1;
    private int volleysThisCycle = 0;
    private int perchTurnsUsed = 0;
    private int rotationIndex = 0;
    /** Alternate each attack's axis so consecutive ones don't keep one half of the arena safe. */
    private boolean lastWaveHorizontal = false;
    private boolean lastSwoopHorizontal = false;
    /** Set when a perch begins; the first action of the perch carries the summons. */
    private boolean broodDue = false;
    private boolean furyPeaked = false;
    private List<Move> announcedVolley = null;

    public DragonAI() {
        this(new Random());
    }

    public DragonAI(Random rng) {
        this.rng = rng;
    }

    public State getState() { return state; }
    public boolean isDragonPhaseTwo() { return isPhaseTwo(); }
    /** Consumed by CombatManager to detect state changes and update occupancy/messages. */
    private State lastReportedState = State.ATTACKING;
    public boolean hasStateChanged() { return state != lastReportedState; }
    public void acknowledgeStateChange() { lastReportedState = state; }

    /** The attacks in the volley telegraphed since the last call, or null. Read once, by chat. */
    public List<Move> consumeAnnouncedVolley() {
        List<Move> v = announcedVolley;
        announcedVolley = null;
        return v;
    }

    /** True once, the turn the dragon drops to its last tier. */
    public boolean consumeFuryPeaked() {
        boolean f = furyPeaked;
        furyPeaked = false;
        return f;
    }

    /** Whether {@code pos} is under the perched dragon's body. */
    public static boolean inPerchZone(GridArena arena, GridPos pos) {
        int ox = (arena.getWidth() - PERCH_W) / 2;
        int oz = (arena.getHeight() - PERCH_H) / 2;
        return pos.x() >= ox && pos.x() < ox + PERCH_W && pos.z() >= oz && pos.z() < oz + PERCH_H;
    }

    // ─── BossAI hooks ─────────────────────────────────────────────────────

    @Override
    protected boolean shouldQueueAbilityAfterWarningResolve() {
        // The last tier telegraphs its next volley as the previous one lands.
        return tier >= 3;
    }

    @Override
    public EnemyAction getChargingAdvanceAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        return new EnemyAction.Idle();
    }

    @Override
    protected boolean reachedPhaseTwo(CombatEntity self) {
        return self.getCurrentHp() * 3 <= self.getMaxHp() * 2;
    }

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        tier = Math.max(tier, 2);
        startPerch();
    }

    private void startPerch() {
        state = State.PERCHING;
        perchTurnsUsed = 0;
        volleysThisCycle = 0;
        broodDue = true;
    }

    private int volleysPerCycle() { return tier == 1 ? 4 : 3; }
    private int perchTurns() { return tier == 1 ? 2 : 3; }
    private int broodTarget() { return tier == 1 ? 3 : tier == 2 ? 5 : 7; }
    private int volleyDamage(CombatEntity self) { return self.getAttackPower() + (tier >= 2 ? 3 : 0); }

    // ─── Main decision loop ──────────────────────────────────────────────

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        if (tier < 3 && self.getCurrentHp() * 3 <= self.getMaxHp()) {
            tier = 3;
            furyPeaked = true;
            if (state != State.PERCHING) startPerch();
        }

        if (state == State.PERCHING) {
            return withBrood(perchAction(self, arena, playerPos), arena);
        }

        volleysThisCycle++;
        if (volleysThisCycle > volleysPerCycle()) {
            startPerch();
            int[] dir = getDirectionToward(self.getGridPos(), playerPos);
            return withBrood(new EnemyAction.ForcedMovement(-1, -dir[0], -dir[1], 3), arena);
        }
        return telegraphVolley(self, arena, playerPos);
    }

    private EnemyAction perchAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        perchTurnsUsed++;
        EnemyAction action;
        if (perchTurnsUsed % 2 == 1) {
            // Wing Buffet - push player away
            int[] dir = getDirectionToward(self.getGridPos(), playerPos);
            action = new EnemyAction.ForcedMovement(-1, -dir[0], -dir[1], 3);
        } else {
            // Tail Slam - telegraphed radius-3 AoE
            List<GridPos> slamTiles = getAreaTiles(arena, self.getGridPos(), 3);
            EnemyAction slamResolve = new EnemyAction.AreaAttack(
                self.getGridPos(), 3, self.getAttackPower() + 3, "tail_slam");
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                slamTiles, 1, slamResolve, 0xFFFF4400);
            action = new EnemyAction.Idle();
        }
        if (perchTurnsUsed >= perchTurns()) {
            state = State.ATTACKING;
            volleysThisCycle = 0;
        }
        return action;
    }

    // ─── The brood ───────────────────────────────────────────────────────

    /** {@code action}, plus the perch's summons if this is the first action of a perch. */
    private EnemyAction withBrood(EnemyAction action, GridArena arena) {
        if (!broodDue) return action;
        broodDue = false;
        List<EnemyAction> summons = callBrood(arena);
        if (summons.isEmpty()) return action;
        List<EnemyAction> all = new ArrayList<>();
        if (!(action instanceof EnemyAction.Idle)) all.add(action);
        all.addAll(summons);
        return all.size() == 1 ? all.get(0) : new EnemyAction.CompositeAction(all);
    }

    /**
     * Top the dragon's adds back up to its tier's count. Tops up rather than stacks, so a long
     * fight can't bury the arena under every perch's brood at once.
     */
    private List<EnemyAction> callBrood(GridArena arena) {
        int wanted = broodTarget() - getAliveMinionCount();
        if (wanted <= 0) return List.of();
        List<EnemyAction> out = new ArrayList<>();
        for (GridPos spot : broodSpots(arena, wanted)) {
            int k = pickBroodType();
            out.add(new EnemyAction.SummonMinions(BROOD_TYPES[k], 1, List.of(spot),
                BROOD_STATS[k][0], BROOD_STATS[k][1], BROOD_STATS[k][2]));
        }
        return out;
    }

    private int pickBroodType() {
        int total = 0;
        for (int w : BROOD_WEIGHTS) total += w;
        int roll = rng.nextInt(total);
        for (int i = 0; i < BROOD_WEIGHTS.length; i++) {
            roll -= BROOD_WEIGHTS[i];
            if (roll < 0) return i;
        }
        return 0;
    }

    /** Free, harmless tiles off the perch zone, out of any cloud, and away from every player. */
    private List<GridPos> broodSpots(GridArena arena, int count) {
        List<GridPos> players = arena.getAllPlayerGridPositions();
        if (players == null || players.isEmpty()) players = List.of(arena.getPlayerGridPos());
        List<GridPos> candidates = new ArrayList<>();
        for (int x = 0; x < arena.getWidth(); x++) {
            for (int z = 0; z < arena.getHeight(); z++) {
                GridPos p = new GridPos(x, z);
                if (!arena.isInBounds(p) || arena.isOccupied(p)) continue;
                GridTile t = arena.getTile(p);
                if (t == null || !t.isSafeForSpawn()) continue;
                if (arena.hasBreathCloud(p) || inPerchZone(arena, p)) continue;
                boolean clear = true;
                for (GridPos pl : players) {
                    if (pl != null && p.manhattanDistance(pl) < BROOD_MIN_DISTANCE) { clear = false; break; }
                }
                if (clear) candidates.add(p);
            }
        }
        Collections.shuffle(candidates, rng);
        return candidates.subList(0, Math.min(count, candidates.size()));
    }

    // ─── Volleys ─────────────────────────────────────────────────────────

    /**
     * Telegraph the next volley: the next {@code tier} attacks from the rotation, bundled into one
     * warning and one hit. The TileAreaAttack over the union is what makes it one hit - it strikes
     * each person inside once, so attacks that overlap never stack on the same tile.
     */
    private EnemyAction telegraphVolley(CombatEntity self, GridArena arena, GridPos playerPos) {
        int dmg = volleyDamage(self);
        int cloudBite = Math.max(2, Math.round(dmg / 3f));

        List<Move> moves = new ArrayList<>();
        Set<GridPos> hit = new LinkedHashSet<>();
        Set<GridPos> fire = new LinkedHashSet<>();
        List<EnemyAction> clouds = new ArrayList<>();
        for (int i = 0; i < tier; i++) {
            Move move = ROTATION[rotationIndex++ % ROTATION.length];
            moves.add(move);
            switch (move) {
                case BREATH_WAVE -> {
                    List<GridPos> band = breathWave(arena, playerPos);
                    hit.addAll(band);
                    fire.addAll(band);
                }
                case SWOOP -> {
                    boolean horizontal = !lastSwoopHorizontal;
                    lastSwoopHorizontal = horizontal;
                    hit.addAll(swoopCorridor(arena, playerPos, horizontal));
                    fire.addAll(swoopCorridor(arena, playerPos, horizontal));
                    clouds.add(new EnemyAction.BreathCloud(
                        swoopLane(arena, playerPos, horizontal), SWOOP_CLOUD_TURNS, cloudBite));
                }
                case FIREBALL -> {
                    List<GridPos> plus = fireballBurst(arena, playerPos);
                    hit.addAll(plus);
                    clouds.add(new EnemyAction.BreathCloud(plus, FIREBALL_CLOUD_TURNS, cloudBite));
                }
                case BREATH_CROSS -> {
                    List<GridPos> cross = breathCross(arena, playerPos);
                    hit.addAll(cross);
                    fire.addAll(cross);
                }
            }
        }
        if (hit.isEmpty()) return new EnemyAction.Idle();

        List<GridPos> warned = new ArrayList<>(hit);
        List<EnemyAction> resolve = new ArrayList<>();
        resolve.add(new EnemyAction.TileAreaAttack(warned, playerPos, dmg,
            moves.size() == 1 && moves.get(0) == Move.SWOOP ? "dragon_swoop" : "dragon_breath"));
        if (!fire.isEmpty()) resolve.add(new EnemyAction.IgniteTiles(new ArrayList<>(fire), true));
        resolve.addAll(clouds);

        pendingWarning = new BossWarning(
            self.getEntityId(), warningTypeFor(moves), warned, 1,
            new EnemyAction.CompositeAction(resolve), colorFor(moves));
        announcedVolley = List.copyOf(moves);
        return new EnemyAction.Idle();
    }

    private static BossWarning.WarningType warningTypeFor(List<Move> moves) {
        return moves.size() == 1 && moves.get(0) == Move.BREATH_CROSS
            ? BossWarning.WarningType.GATHERING_PARTICLES
            : BossWarning.WarningType.TILE_HIGHLIGHT;
    }

    private static int colorFor(List<Move> moves) {
        if (moves.size() > 1) return 0xFFD02AFF;
        return switch (moves.get(0)) {
            case BREATH_WAVE -> 0xFF3AB0FF;
            case SWOOP -> 0xFFCC33FF;
            case FIREBALL -> 0xFFB040FF;
            case BREATH_CROSS -> 0xFFFF00FF;
        };
    }

    // ─── Attack shapes ────────────────────────────────────────────────────

    /**
     * A 3-wide band of soul fire that rolls in from the arena edge nearest the player and runs
     * all the way to their tile. It used to stop at the wall, which made the middle of the arena
     * a spot the wave could never reach.
     */
    private List<GridPos> breathWave(GridArena arena, GridPos playerPos) {
        int w = arena.getWidth();
        int h = arena.getHeight();
        boolean horizontal = !lastWaveHorizontal;
        lastWaveHorizontal = horizontal;

        List<GridPos> band = new ArrayList<>();
        if (horizontal) {
            int baseX = Math.max(0, Math.min(w - 3, playerPos.x() - 1));
            int edgeZ = playerPos.z() < h / 2 ? 0 : h - 1;
            int z0 = Math.min(edgeZ, playerPos.z()), z1 = Math.max(edgeZ, playerPos.z());
            for (int x = baseX; x < baseX + 3; x++) {
                for (int z = z0; z <= z1; z++) addIfInBounds(arena, band, new GridPos(x, z));
            }
        } else {
            int baseZ = Math.max(0, Math.min(h - 3, playerPos.z() - 1));
            int edgeX = playerPos.x() < w / 2 ? 0 : w - 1;
            int x0 = Math.min(edgeX, playerPos.x()), x1 = Math.max(edgeX, playerPos.x());
            for (int z = baseZ; z < baseZ + 3; z++) {
                for (int x = x0; x <= x1; x++) addIfInBounds(arena, band, new GridPos(x, z));
            }
        }
        return band;
    }

    /** The swoop's full-length 3-wide corridor over the player. */
    private static List<GridPos> swoopCorridor(GridArena arena, GridPos playerPos, boolean horizontal) {
        List<GridPos> out = new ArrayList<>();
        if (horizontal) {
            int baseZ = Math.max(0, Math.min(arena.getHeight() - 3, playerPos.z() - 1));
            for (int x = 0; x < arena.getWidth(); x++) {
                for (int dz = 0; dz < 3; dz++) addIfInBounds(arena, out, new GridPos(x, baseZ + dz));
            }
        } else {
            int baseX = Math.max(0, Math.min(arena.getWidth() - 3, playerPos.x() - 1));
            for (int z = 0; z < arena.getHeight(); z++) {
                for (int dx = 0; dx < 3; dx++) addIfInBounds(arena, out, new GridPos(baseX + dx, z));
            }
        }
        return out;
    }

    /** The middle lane of the swoop corridor, where the dragon's body passed: left clouded. */
    private static List<GridPos> swoopLane(GridArena arena, GridPos playerPos, boolean horizontal) {
        List<GridPos> out = new ArrayList<>();
        if (horizontal) {
            int laneZ = Math.max(0, Math.min(arena.getHeight() - 3, playerPos.z() - 1)) + 1;
            for (int x = 0; x < arena.getWidth(); x++) addIfInBounds(arena, out, new GridPos(x, laneZ));
        } else {
            int laneX = Math.max(0, Math.min(arena.getWidth() - 3, playerPos.x() - 1)) + 1;
            for (int z = 0; z < arena.getHeight(); z++) addIfInBounds(arena, out, new GridPos(laneX, z));
        }
        return out;
    }

    /** The fireball's burst: the player's tile and the four beside it. */
    private static List<GridPos> fireballBurst(GridArena arena, GridPos playerPos) {
        List<GridPos> out = new ArrayList<>();
        addIfInBounds(arena, out, playerPos);
        addIfInBounds(arena, out, new GridPos(playerPos.x() + 1, playerPos.z()));
        addIfInBounds(arena, out, new GridPos(playerPos.x() - 1, playerPos.z()));
        addIfInBounds(arena, out, new GridPos(playerPos.x(), playerPos.z() + 1));
        addIfInBounds(arena, out, new GridPos(playerPos.x(), playerPos.z() - 1));
        return out;
    }

    /** The player's full row and column. */
    private List<GridPos> breathCross(GridArena arena, GridPos playerPos) {
        Set<GridPos> cross = new LinkedHashSet<>();
        cross.addAll(getRowTiles(arena, playerPos.z()));
        cross.addAll(getColumnTiles(arena, playerPos.x()));
        return new ArrayList<>(cross);
    }

    /** Add {@code t} to {@code out} when the arena actually has that tile. */
    private static void addIfInBounds(GridArena arena, List<GridPos> out, GridPos t) {
        if (arena.isInBounds(t)) out.add(t);
    }
}
