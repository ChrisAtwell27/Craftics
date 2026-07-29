package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ai.boss.BossAI;
import com.crackedgames.craftics.combat.ai.boss.BossWarning;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.List;

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
 * - (Existing) Melee: devastating close-range attack
 * - (Existing) Sonic Boom (Phase 2): range 4, ignores LOS
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
    private List<GridPos> planFissure(GridArena arena, GridPos playerPos) {
        int w = arena.getWidth();
        int h = arena.getHeight();
        int width = isPhaseTwo() ? FISSURE_WIDTH_P2 : FISSURE_WIDTH;

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

        // Determine effective target (vibration or player)
        GridPos effectiveTarget = (vibrationTarget != null) ? vibrationTarget : playerPos;

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

        int distToTarget = self.minDistanceTo(effectiveTarget);
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
            List<GridPos> crack = planFissure(arena, playerPos);
            if (!crack.isEmpty()) {
                setCooldown(CD_FISSURE, fissureCooldown);
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
            int pulseRadius = isPhaseTwo() ? 3 : 2;
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

        // Sonic Boom (Phase 2) - target highest-priority vibration source
        if (isPhaseTwo() && distToTarget <= 4) {
            return new EnemyAction.RangedAttack(self.getAttackPower() + bonusDamage, "sonic_boom");
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
            return new EnemyAction.MoveAndAttack(path, self.getAttackPower() + bonusDamage + sculkBonus);
        }
        return new EnemyAction.Move(path);
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
