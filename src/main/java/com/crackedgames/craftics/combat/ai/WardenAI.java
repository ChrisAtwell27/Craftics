package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ai.boss.BossAI;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deep Dark Boss - "The Warden" (Enhanced with Vibration Sense)
 * Entity: Warden | 50HP / 8ATK / 4DEF / Speed 3 | Size 2×2
 *
 * Core Mechanic - Vibration Sense:
 * - Blind - hunts by vibration, not sight.
 * - Projectile distraction: player throws at empty tile → Warden chases that tile.
 * - Movement 3+ tiles in a turn causes lock-on (overrides distraction).
 * - Phase 2: distraction lasts 1 turn only, movement threshold drops to 2+.
 *
 * Abilities:
 * - Sculk Spread: tiles attacked become sculk (+1 dmg per adjacent sculk)
 * - Sculk Shrieker: trap tile, triggers on proximity, 3 damage + vibration override
 * - Darkness Pulse: all tiles go dark for 1 turn, Warden gets +2 speed
 * - Tremor Stomp: when confused, stomp sends tremors in 4 cardinal directions
 * - Fissure: tears a band of floor out of the arena for good. Never under itself, and it
 *   will not walk into one it has telegraphed. The hole fills itself back in over the
 *   following rounds as the ceiling collapses into it (CombatManager#tickFissureDebris).
 * - (Existing) Melee: devastating close-range attack
 * - Sonic Boom (Phase 2): a 3-wide lane to EVERY player on the field, any range, no line of
 *   sight needed, telegraphed a turn ahead. Whoever it catches is Marked, blinded and left
 *   in the dark for 4 turns; while a mark is live the Warden hunts that player specifically
 *   and gains speed, and no thrown distraction will pull it off them.
 *
 * Phase 2 - "The Ancient Awakens":
 * - +3 bonus damage, Sonic Boom unlocked
 * - Sculk regen: 1 HP/turn per sculk tile on field
 * - Darkness Pulse every 2 turns instead of 4
 * - 2 Sculk Shriekers placed automatically
 * - Distraction only lasts 1 turn
 * - Movement threshold drops to 2+ tiles
 * - Tremor Stomp always fires (no 50% chance), range 4
 */
public class WardenAI extends BossAI {
    private static final String CD_DARKNESS = "darkness_pulse";
    private static final String CD_SHRIEKER = "sculk_shrieker";
    private static final String CD_STOMP = "tremor_stomp";
    private static final String CD_FISSURE = "fissure";
    private static final String CD_SONIC = "sonic_boom";

    /**
     * Rounds between booms. The boom now covers a lane to every player at once instead of
     * poking one of them, so it needs a gap: uncapped, phase two was a beam every single
     * turn and the Marked it leaves behind would never lapse.
     */
    private static final int SONIC_COOLDOWN = 3;
    /** Tiles to each side of the beam's centre line. 1 makes the lane 3 wide. */
    private static final int SONIC_SPREAD = 1;

    /** Turn the first fissure opens - late enough that the fight has a shape to ruin. */
    private static final int FISSURE_FIRST_TURN = 4;
    /** Rounds between fissures. Phase 2 halves it. */
    private static final int FISSURE_COOLDOWN = 6;
    /** How wide the crack is, and how much wider it gets in phase two. */
    private static final int FISSURE_WIDTH = 2;
    private static final int FISSURE_WIDTH_P2 = 3;
    /** Damage to anything standing on the crack when it opens. */
    private static final int FISSURE_DAMAGE = 6;

    /** Rows/columns already cracked, so a second fissure doesn't reopen the same ground. */
    private final List<Integer> fissureLines = new ArrayList<>();

    /**
     * The crack that is telegraphed right now, from the turn it is announced to the turn it
     * opens. Ground the Warden refuses to walk onto, so it can never drop itself into its
     * own hole.
     *
     * <p>The plain campaign flow ends the Warden's turn the moment it telegraphs, and the
     * crack opens before it decides again, so today this mainly guards the charging-advance
     * path (a boss deep enough in the campaign acts during its telegraph turn) and any future
     * flow that lets it move with a fissure outstanding. It is the invariant that matters,
     * not the one route that currently reaches it.
     */
    private final Set<GridPos> pendingFissureTiles = new HashSet<>();

    /**
     * The tile of a player its sonic boom has Marked, refreshed every round by
     * {@code CombatManager.tickWardenMarkHunt}, or null when nobody is marked.
     *
     * <p>A live mark outranks every other lead: a marked player is one the Warden can
     * genuinely track, so a thrown snowball no longer pulls it away from them.
     */
    private GridPos huntedTile = null;

    // Vibration tracking
    private GridPos vibrationTarget = null;
    private boolean isConfused = false;
    private boolean distractionActive = false;
    private int distractionTurnsLeft = 0;
    private final List<GridPos> sculkTiles = new ArrayList<>();
    private final List<GridPos> shriekerTiles = new ArrayList<>();

    /**
     * Called by CombatManager when the player throws a projectile at an empty tile.
     * Sets the vibration target for the Warden to chase.
     */
    public void onProjectileDistraction(GridPos target) {
        vibrationTarget = target;
        distractionActive = true;
        isConfused = false;
        distractionTurnsLeft = isPhaseTwo() ? 1 : 99; // P2: only 1 turn
    }

    /**
     * Called by CombatManager when the player moves.
     * If movement exceeds threshold, Warden locks on to player.
     */
    public void onPlayerMove(GridPos playerPos, int tilesMoved) {
        int threshold = isPhaseTwo() ? 2 : 3;
        if (tilesMoved >= threshold) {
            vibrationTarget = playerPos;
            distractionActive = false;
            isConfused = false;
        }
    }

    /**
     * Set (or clear, with null) the tile of the player its sonic boom has Marked. Pushed once
     * per round by CombatManager, which owns the mark's lifetime.
     */
    public void setHuntedTile(GridPos target) {
        this.huntedTile = target;
    }

    /**
     * Called by CombatManager when a shrieker is triggered.
     * Overrides projectile distraction.
     */
    public void onShriekerTriggered(GridPos shriekerPos) {
        vibrationTarget = shriekerPos;
        distractionActive = false;
        isConfused = false;
    }

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        // Place 2 sculk shriekers automatically
        List<GridPos> positions = findSummonPositions(arena, 2);
        for (GridPos pos : positions) {
            shriekerTiles.add(pos);
        }
    }

    /**
     * Pick where the arena tears.
     *
     * <p>The crack runs the full span of the board on the axis the player is FURTHEST along, so
     * it cuts between them and the space they have most of - the retreat, not the wall they're
     * already backed against. It's offset a couple of tiles to the player's side of centre for
     * the same reason, and never laid on a line that's already cracked or on the Warden's own
     * footprint (a boss that drops itself into the void is a comedy, not a threat).
     *
     * <p>Returns an empty list when there's nowhere sensible left to split, which retires the
     * ability naturally on a board that's already in pieces.
     */
    private List<GridPos> planFissure(CombatEntity self, GridArena arena, GridPos playerPos) {
        int w = arena.getWidth();
        int h = arena.getHeight();
        int width = isPhaseTwo() ? FISSURE_WIDTH_P2 : FISSURE_WIDTH;

        // Every tile the Warden itself covers. The crack is never laid under its own feet:
        // the javadoc above has always promised that and the code never did it, so a fissure
        // that happened to line up with the boss opened the floor beneath it.
        Set<GridPos> ownFootprint = new HashSet<>();
        GridPos base = self.getGridPos();
        if (base != null) {
            for (int dx = 0; dx < self.getSizeX(); dx++) {
                for (int dz = 0; dz < self.getSizeZ(); dz++) {
                    ownFootprint.add(new GridPos(base.x() + dx, base.z() + dz));
                }
            }
        }

        // Split across the LONGER axis so the crack actually reaches both edges.
        boolean splitOnZ = h >= w;
        int span = splitOnZ ? h : w;
        int playerLine = splitOnZ ? playerPos.z() : playerPos.x();
        if (span < width + 4) return List.of();   // too cramped to lose a band of floor

        // Two tiles off the player, toward the middle: close enough to matter this turn, far
        // enough that they aren't simply standing on the whole thing when it opens.
        int centre = playerLine + (playerLine > span / 2 ? -2 : 2);
        int start = Math.max(1, Math.min(span - width - 1, centre - width / 2));

        List<GridPos> crack = new ArrayList<>();
        for (int line = start; line < start + width; line++) {
            if (fissureLines.contains(line)) return List.of();   // don't re-crack the same ground
            for (int i = 0; i < (splitOnZ ? w : h); i++) {
                GridPos pos = splitOnZ ? new GridPos(i, line) : new GridPos(line, i);
                var tile = arena.getTile(pos);
                if (tile == null) continue;
                if (tile.getType() == TileType.VOID) continue;    // already open
                if (ownFootprint.contains(pos)) continue;         // never under the Warden
                crack.add(pos);
            }
        }
        if (crack.isEmpty()) return List.of();
        for (int line = start; line < start + width; line++) fissureLines.add(line);
        return crack;
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int bonusDamage = isPhaseTwo() ? 3 : 0;

        // chooseAbility only runs once a telegraph has resolved (BossAI holds the turn while
        // one is pending), so reaching here means last turn's crack has already opened and
        // the no-go zone is spent.
        pendingFissureTiles.clear();

        // Tick distraction timer
        if (distractionActive) {
            distractionTurnsLeft--;
            if (distractionTurnsLeft <= 0) {
                distractionActive = false;
                vibrationTarget = null;
            }
        }

        // Phase 2: sculk regen
        if (isPhaseTwo() && !sculkTiles.isEmpty()) {
            self.heal(sculkTiles.size());
        }

        // Determine effective target. A live sonic-boom mark wins outright; failing that,
        // the last vibration it heard; failing that, the player.
        GridPos effectiveTarget = huntedTile != null ? huntedTile
            : (vibrationTarget != null) ? vibrationTarget : playerPos;
        if (huntedTile != null) {
            // A mark is a lock, so a projectile thrown while it is live is not a way out.
            distractionActive = false;
            isConfused = false;
        }

        // If confused (reached distraction tile, found nothing) - Tremor Stomp or idle
        if (isConfused) {
            isConfused = false;
            boolean doStomp = isPhaseTwo() || Math.random() < 0.5;
            if (doStomp) {
                int range = isPhaseTwo() ? 4 : 3;
                List<GridPos> stompTiles = getCrossTiles(arena, myPos, range);
                return new EnemyAction.BossAbility("tremor_stomp",
                    new EnemyAction.AreaAttack(myPos, range, 3, "tremor_stomp"),
                    stompTiles);
            }
            return new EnemyAction.Idle();
        }

        // If chasing distraction target and we've arrived - become confused
        if (distractionActive && vibrationTarget != null) {
            int distToTarget = myPos.manhattanDistance(vibrationTarget);
            if (distToTarget <= 1) {
                isConfused = true;
                distractionActive = false;
                vibrationTarget = null;
                return new EnemyAction.Idle();
            }
        }

        int distToPlayer = self.minDistanceTo(playerPos);

        // FISSURE - the Warden splits the arena.
        //
        // A thin ground attack asks "are you standing here?", and one step answers it. This asks
        // something the player can't step out of: a band of floor across the whole board drops
        // out permanently, and the arena they've been fighting in is now two arenas. Cover on the
        // wrong side is gone, the party can be cut from each other, and the ground they retreat
        // over stops existing. Bosses vault gaps (see Pathfinding#canVaultGaps), so this traps
        // the player, never the Warden.
        //
        // Telegraphed a turn ahead like every other boss ability - standing on the crack when it
        // opens costs damage, but the tile is lost either way.
        int fissureCooldown = isPhaseTwo() ? FISSURE_COOLDOWN / 2 : FISSURE_COOLDOWN;
        if (getTurnCounter() >= FISSURE_FIRST_TURN && !isOnCooldown(CD_FISSURE)) {
            List<GridPos> crack = planFissure(self, arena, playerPos);
            if (!crack.isEmpty()) {
                setCooldown(CD_FISSURE, fissureCooldown);
                // Remembered for the telegraph turn: the Warden will not walk into the
                // ground it is about to drop out from under itself.
                pendingFissureTiles.clear();
                pendingFissureTiles.addAll(crack);
                // The "fissure" effect name is what turns these tiles to VOID in the resolver -
                // the damage is incidental, the hole is the attack.
                return new EnemyAction.BossAbility("fissure",
                    new EnemyAction.TileAreaAttack(crack, myPos, FISSURE_DAMAGE, "fissure"),
                    crack);
            }
        }

        // Darkness Pulse - AoE around the player that blinds them. Telegraphed so the
        // player sees it coming and can reposition out of the warning tiles.
        int darknessCooldown = isPhaseTwo() ? 2 : 4;
        if (!isOnCooldown(CD_DARKNESS)) {
            setCooldown(CD_DARKNESS, darknessCooldown);
            int pulseRadius = isPhaseTwo() ? 4 : 3;
            List<GridPos> pulseTiles = getAreaTiles(arena, playerPos, pulseRadius);
            return new EnemyAction.BossAbility("darkness_pulse",
                new EnemyAction.AreaAttack(playerPos, pulseRadius, 2, "darkness_pulse"),
                pulseTiles);
        }

        // Place Sculk Shrieker
        if (!isOnCooldown(CD_SHRIEKER) && shriekerTiles.size() < (isPhaseTwo() ? 4 : 2)) {
            setCooldown(CD_SHRIEKER, 3);
            GridPos shriekerPos = findSummonPositions(arena, 1).isEmpty() ? null :
                findSummonPositions(arena, 1).get(0);
            if (shriekerPos != null) {
                shriekerTiles.add(shriekerPos);
                return new EnemyAction.CreateTerrain(List.of(shriekerPos), TileType.OBSTACLE, 0);
            }
        }

        // Adjacent to effective target - melee attack
        if (distToPlayer <= 1) {
            // Mark attacked tile as sculk
            sculkTiles.add(myPos);
            int sculkBonus = countAdjacentSculk(myPos);
            return new EnemyAction.Attack(self.getAttackPower() + bonusDamage + sculkBonus);
        }

        // Sonic Boom (Phase 2): a beam down every line it can hear, at any range, whether or
        // not it is currently hunting anyone. Telegraphed like everything else, so the shape
        // is on the floor for a turn before it fires and the play is to leave the lane.
        if (!isOnCooldown(CD_SONIC)) {
            List<GridPos> beam = sonicBeamTiles(self, arena, playerPos);
            if (!beam.isEmpty()) {
                setCooldown(CD_SONIC, SONIC_COOLDOWN);
                return new EnemyAction.BossAbility("sonic_boom",
                    new EnemyAction.TileAreaAttack(beam, myPos,
                        self.getAttackPower() + bonusDamage, "sonic_boom"),
                    beam);
            }
        }

        // Rush toward effective target
        GridPos moveTarget = AIUtils.findBestAdjacentTarget(arena, myPos, effectiveTarget, self.getMoveSpeed());
        if (moveTarget == null) moveTarget = effectiveTarget;

        List<GridPos> path = Pathfinding.findPath(arena, myPos, moveTarget, self.getMoveSpeed(), self);
        if (path.isEmpty()) return AIUtils.seekOrWander(self, arena, playerPos);

        GridPos endPos = path.get(path.size() - 1);
        if (endPos.manhattanDistance(playerPos) <= 1) {
            sculkTiles.add(endPos);
            int sculkBonus = countAdjacentSculk(endPos);
            return keepOffPendingFissure(
                new EnemyAction.MoveAndAttack(path, self.getAttackPower() + bonusDamage + sculkBonus));
        }
        return keepOffPendingFissure(new EnemyAction.Move(path));
    }

    /**
     * The shape of a sonic boom: one lane per player on the field, each a straight line from
     * the Warden to them, widened by {@link #SONIC_SPREAD} tiles on either side.
     *
     * <p>Lanes overlap into one set, so a player standing behind another is in the same beam
     * rather than granted a second one, and the Warden's own footprint is left out: the blast
     * leaves it, it does not wash over it.
     */
    private List<GridPos> sonicBeamTiles(CombatEntity self, GridArena arena, GridPos playerPos) {
        Set<GridPos> beam = new LinkedHashSet<>();
        Set<GridPos> ownFootprint = new HashSet<>();
        GridPos base = self.getGridPos();
        if (base != null) {
            for (int dx = 0; dx < self.getSizeX(); dx++) {
                for (int dz = 0; dz < self.getSizeZ(); dz++) {
                    ownFootprint.add(new GridPos(base.x() + dx, base.z() + dz));
                }
            }
        }

        List<GridPos> targets = new ArrayList<>(arena.getAllPlayerGridPositions());
        if (targets.isEmpty() && playerPos != null) targets.add(playerPos);

        for (GridPos target : targets) {
            if (target == null) continue;
            GridPos from = self.nearestTileTo(target);
            // The beam does not stop where the player is standing - it carries on to the far
            // wall. Stepping one tile back along the lane was never an escape from a sound
            // wave, and a beam that halts exactly at whoever it is aimed at reads as a dart
            // rather than a blast. The line is extended past the target far enough to leave
            // the board, and addBeamTile discards whatever falls outside it.
            GridPos beyond = extendPastTarget(arena, from, target);
            List<GridPos> centre = Pathfinding.traceLine(from, beyond);
            GridPos prev = from;
            for (GridPos on : centre) {
                addBeamTile(arena, beam, ownFootprint, on);
                // Widen across the direction of travel. Using the per-step direction keeps
                // the lane square to the beam even where a diagonal line staircases.
                int sdx = on.x() - prev.x();
                int sdz = on.z() - prev.z();
                for (int off = 1; off <= SONIC_SPREAD; off++) {
                    addBeamTile(arena, beam, ownFootprint,
                        new GridPos(on.x() - sdz * off, on.z() + sdx * off));
                    addBeamTile(arena, beam, ownFootprint,
                        new GridPos(on.x() + sdz * off, on.z() - sdx * off));
                }
                prev = on;
            }
        }
        return new ArrayList<>(beam);
    }

    /**
     * The point the beam is traced to: the same heading as {@code from -> target}, pushed out
     * far enough that the line certainly leaves the arena. Scaled by the board's own span, so
     * it reaches the wall on any arena size without guessing at a fixed length.
     */
    private static GridPos extendPastTarget(GridArena arena, GridPos from, GridPos target) {
        int dx = target.x() - from.x();
        int dz = target.z() - from.z();
        if (dx == 0 && dz == 0) return target;
        int span = arena.getWidth() + arena.getHeight();
        // Normalised by the longer leg so the heading is preserved exactly; a diagonal beam
        // keeps its diagonal rather than drifting toward a cardinal.
        int longest = Math.max(Math.abs(dx), Math.abs(dz));
        return new GridPos(
            from.x() + (dx * span) / longest,
            from.z() + (dz * span) / longest);
    }

    /** Add one in-bounds tile to the beam, skipping the Warden's own ground. */
    private static void addBeamTile(GridArena arena, Set<GridPos> beam,
                                    Set<GridPos> ownFootprint, GridPos pos) {
        if (!arena.isInBounds(pos) || ownFootprint.contains(pos)) return;
        beam.add(pos);
    }

    /**
     * The Warden advances during its telegraph turn like every other late-game boss, so the
     * one turn it is most likely to walk into a fissure is the turn the fissure is pending.
     * Route that advance through the same guard the normal chase uses.
     */
    @Override
    public EnemyAction getChargingAdvanceAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        return keepOffPendingFissure(super.getChargingAdvanceAction(self, arena, playerPos));
    }

    /**
     * Trim a movement action so it stops short of ground that is about to fall away.
     *
     * <p>A truncated {@code MoveAndAttack} becomes a plain {@code Move}: the attack half was
     * only valid because the path ended next to the target, and it no longer does. An action
     * that never touches the crack is returned untouched, which is the usual case.
     */
    private EnemyAction keepOffPendingFissure(EnemyAction action) {
        if (pendingFissureTiles.isEmpty()) return action;
        List<GridPos> path = action instanceof EnemyAction.Move mv ? mv.path()
            : action instanceof EnemyAction.MoveAndAttack maa ? maa.path()
            : null;
        if (path == null || path.isEmpty()) return action;

        int stopAt = path.size();
        for (int i = 0; i < path.size(); i++) {
            if (pendingFissureTiles.contains(path.get(i))) { stopAt = i; break; }
        }
        if (stopAt == path.size()) return action;                 // never crosses the crack
        if (stopAt == 0) return new EnemyAction.Idle();           // the first step is the crack
        return new EnemyAction.Move(new ArrayList<>(path.subList(0, stopAt)));
    }

    private int countAdjacentSculk(GridPos pos) {
        int count = 0;
        for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            GridPos adj = new GridPos(pos.x() + d[0], pos.z() + d[1]);
            if (sculkTiles.contains(adj)) count++;
        }
        return count;
    }

    public List<GridPos> getSculkTiles() { return sculkTiles; }
    public List<GridPos> getShriekerTiles() { return shriekerTiles; }
    public boolean isWardenConfused() { return isConfused; }
    public GridPos getVibrationTarget() { return vibrationTarget; }
}
