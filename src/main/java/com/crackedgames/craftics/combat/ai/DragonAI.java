package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.boss.BossAI;
import com.crackedgames.craftics.combat.ai.boss.BossWarning;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Dragon's Nest Boss — "The Ender Dragon" (backgroundBoss approach).
 *
 * ─── Attack State (default) ────────────────────────────────────────────
 *  Dragon is off-stage. Each turn it telegraphs one attack:
 *   • Breath Wave — spawns a 3-wide wave of fire at one arena edge that
 *     advances 3 tiles per turn autonomously. Damages anyone it touches.
 *     Runs independently: the boss can keep attacking while waves move.
 *   • Breath Cross — highlights player's row AND column, resolves as fire.
 *   • Swoop — highlights a 3-wide corridor, resolves as area damage + fire.
 *  After a cycle of attacks, dragon perches.
 *
 * ─── Perch State (2 turns P1, 3 turns P2) ─────────────────────────────
 *  Dragon visible + targetable on a cluster of centre tiles:
 *   • Wing Buffet (odd turns) — pushes player 3 tiles away.
 *   • Tail Slam (even turns) — telegraphed radius-3 AoE.
 */
public class DragonAI extends BossAI {
    @Override public int getGridSize() { return 1; } // backgroundBoss, size managed manually

    @Override
    protected boolean shouldQueueAbilityAfterWarningResolve() { return false; }

    @Override
    public EnemyAction getChargingAdvanceAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        return new EnemyAction.Idle();
    }

    public enum State { ATTACKING, PERCHING }
    private State state = State.ATTACKING;
    private int attackTurns = 0;
    private int perchTurnsUsed = 0;
    private boolean lastSwoopHorizontal = false;

    public State getState() { return state; }
    public boolean isDragonPhaseTwo() { return isPhaseTwo(); }
    /** Consumed by CombatManager to detect state changes and update occupancy/messages. */
    private State lastReportedState = State.ATTACKING;
    public boolean hasStateChanged() { return state != lastReportedState; }
    public void acknowledgeStateChange() { lastReportedState = state; }

    // ─── Breath Wave system ──────────────────────────────────────────────
    // Waves are persistent hazards that advance every turn regardless of
    // the boss's own action. CombatManager calls tickWaves() each turn.

    /** A single advancing fire wave. */
    public static class BreathWave {
        public final boolean horizontal; // true = moves along Z, false = moves along X
        public final int baseOffset;     // fixed axis offset (start of the 3-wide strip)
        public final int direction;      // +1 or -1
        public int frontPos;             // current leading-edge position on the moving axis
        public final int damage;
        public final int fireDuration;

        public BreathWave(boolean horizontal, int baseOffset, int direction,
                          int startPos, int damage, int fireDuration) {
            this.horizontal = horizontal;
            this.baseOffset = baseOffset;
            this.direction = direction;
            this.frontPos = startPos;
            this.damage = damage;
            this.fireDuration = fireDuration;
        }
    }

    private final List<BreathWave> activeWaves = new ArrayList<>();

    /** Get the active waves for CombatManager to tick. */
    public List<BreathWave> getActiveWaves() { return activeWaves; }

    /**
     * Advance all active waves by 3 tiles, place fire, damage the player if
     * they stand in the wave's path. Called by CombatManager each turn start.
     * Returns the set of tiles that were set on fire this tick (for particles).
     */
    public List<GridPos> tickWaves(GridArena arena) {
        List<GridPos> burnedTiles = new ArrayList<>();
        Iterator<BreathWave> it = activeWaves.iterator();
        while (it.hasNext()) {
            BreathWave wave = it.next();
            boolean anyInBounds = false;
            for (int step = 0; step < 3; step++) {
                int pos = wave.frontPos + wave.direction * step;
                for (int d = 0; d < 3; d++) {
                    GridPos tile;
                    if (wave.horizontal) {
                        tile = new GridPos(wave.baseOffset + d, pos);
                    } else {
                        tile = new GridPos(pos, wave.baseOffset + d);
                    }
                    if (!arena.isInBounds(tile)) continue;
                    anyInBounds = true;
                    burnedTiles.add(tile);
                    GridTile gt = arena.getTile(tile);
                    if (gt != null) {
                        gt.setTemporaryType(TileType.FIRE, wave.fireDuration);
                    }
                }
            }
            wave.frontPos += wave.direction * 3;
            if (!anyInBounds) {
                it.remove(); // wave has left the arena
            }
        }
        return burnedTiles;
    }

    // ─── Phase transitions ────────────────────────────────────────────────

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        state = State.PERCHING;
        perchTurnsUsed = 0;
        attackTurns = 0;
    }

    // ─── Main decision loop ──────────────────────────────────────────────

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {

        // ── PERCHING ──
        if (state == State.PERCHING) {
            int maxPerch = isPhaseTwo() ? 3 : 2;
            perchTurnsUsed++;

            EnemyAction action;
            if (perchTurnsUsed % 2 == 1) {
                // Wing Buffet — push player away
                int[] dir = getDirectionToward(self.getGridPos(), playerPos);
                action = new EnemyAction.ForcedMovement(-1, -dir[0], -dir[1], 3);
            } else {
                // Tail Slam — telegraphed radius-3 AoE
                List<GridPos> slamTiles = getAreaTiles(arena, self.getGridPos(), 3);
                EnemyAction slamResolve = new EnemyAction.AreaAttack(
                    self.getGridPos(), 3, self.getAttackPower() + 3, "tail_slam");
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                    slamTiles, 1, slamResolve, 0xFFFF4400);
                action = new EnemyAction.Idle();
            }

            if (perchTurnsUsed >= maxPerch) {
                state = State.ATTACKING;
                attackTurns = 0;
            }
            return action;
        }

        // ── ATTACKING ──
        attackTurns++;

        int cycleLength = isPhaseTwo() ? 3 : 4;
        if (attackTurns > cycleLength) {
            state = State.PERCHING;
            perchTurnsUsed = 0;
            int[] dir = getDirectionToward(self.getGridPos(), playerPos);
            return new EnemyAction.ForcedMovement(-1, -dir[0], -dir[1], 3);
        }

        // P1: Wave, Swoop, Wave, BreathCross  (cycle 4)
        // P2: Wave, Swoop, BreathCross         (cycle 3)
        int pick = attackTurns;
        if (pick == 1 || (pick == 3 && !isPhaseTwo())) {
            return spawnBreathWave(self, arena, playerPos);
        } else if (pick == 2) {
            return telegraphSwoop(self, arena, playerPos);
        } else {
            return telegraphBreathCross(self, arena, playerPos);
        }
    }

    // ─── Attack builders ──────────────────────────────────────────────

    /**
     * Spawn a breath wave — a 3-wide wall of fire that starts at the arena
     * edge nearest the player and advances 3 tiles per turn toward the opposite
     * edge. No telegraph: the wave IS the warning (the player sees fire tiles
     * marching toward them). The wave ticks independently via tickWaves().
     */
    private EnemyAction spawnBreathWave(CombatEntity self, GridArena arena, GridPos playerPos) {
        int w = arena.getWidth();
        int h = arena.getHeight();
        int dmg = self.getAttackPower() + (isPhaseTwo() ? 3 : 0);
        int fireDuration = isPhaseTwo() ? 5 : 3;

        // Alternate horizontal and vertical waves
        boolean horizontal = !lastSwoopHorizontal;
        lastSwoopHorizontal = horizontal;

        int baseOffset, startPos, direction;
        if (horizontal) {
            // Wave moves along Z axis, 3-wide along X centred on player
            baseOffset = Math.max(0, Math.min(w - 3, playerPos.x() - 1));
            // Start from the edge closest to the player
            if (playerPos.z() < h / 2) {
                startPos = 0;
                direction = 1;
            } else {
                startPos = h - 1;
                direction = -1;
            }
        } else {
            // Wave moves along X axis, 3-wide along Z centred on player
            baseOffset = Math.max(0, Math.min(h - 3, playerPos.z() - 1));
            if (playerPos.x() < w / 2) {
                startPos = 0;
                direction = 1;
            } else {
                startPos = w - 1;
                direction = -1;
            }
        }

        activeWaves.add(new BreathWave(horizontal, baseOffset, direction, startPos, dmg, fireDuration));

        // No Idle — the wave spawns and the boss still gets to act next turn.
        // Return a message-only action: the wave will be ticked by CombatManager.
        // We use an Idle here but the attack cycle counter already incremented,
        // so the next chooseAbility call picks the next attack in rotation.
        return new EnemyAction.Idle();
    }

    private EnemyAction telegraphSwoop(CombatEntity self, GridArena arena, GridPos playerPos) {
        int w = arena.getWidth();
        int h = arena.getHeight();

        boolean horizontal = (attackTurns % 2 == 0);

        // Swoop covers 3 rows/columns wide
        List<GridPos> warningTiles = new ArrayList<>();
        if (horizontal) {
            int baseZ = Math.max(0, Math.min(h - 3, playerPos.z() - 1));
            for (int x = 0; x < w; x++) {
                for (int dz = 0; dz < 3; dz++) {
                    GridPos t = new GridPos(x, baseZ + dz);
                    if (arena.isInBounds(t)) warningTiles.add(t);
                }
            }
        } else {
            int baseX = Math.max(0, Math.min(w - 3, playerPos.x() - 1));
            for (int z = 0; z < h; z++) {
                for (int dx = 0; dx < 3; dx++) {
                    GridPos t = new GridPos(baseX + dx, z);
                    if (arena.isInBounds(t)) warningTiles.add(t);
                }
            }
        }

        int dmg = self.getAttackPower() + (isPhaseTwo() ? 3 : 0);
        int fireDuration = isPhaseTwo() ? 5 : 3;

        EnemyAction resolve = new EnemyAction.CompositeAction(List.of(
            new EnemyAction.AreaAttack(playerPos, 0, dmg, "dragon_swoop"),
            new EnemyAction.CreateTerrain(warningTiles, TileType.FIRE, fireDuration)
        ));

        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
            warningTiles, 1, resolve, 0xFFCC33FF);
        return new EnemyAction.Idle();
    }

    private EnemyAction telegraphBreathCross(CombatEntity self, GridArena arena, GridPos playerPos) {
        List<GridPos> cross = new ArrayList<>();
        cross.addAll(getRowTiles(arena, playerPos.z()));
        cross.addAll(getColumnTiles(arena, playerPos.x()));

        int fireDuration = isPhaseTwo() ? 5 : 3;
        int dmg = self.getAttackPower() + (isPhaseTwo() ? 3 : 0);

        EnemyAction resolve = new EnemyAction.CompositeAction(List.of(
            new EnemyAction.LineAttack(new GridPos(0, playerPos.z()), 1, 0, arena.getWidth(), dmg),
            new EnemyAction.LineAttack(new GridPos(playerPos.x(), 0), 0, 1, arena.getHeight(), dmg),
            new EnemyAction.CreateTerrain(cross, TileType.FIRE, fireDuration)
        ));

        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.GATHERING_PARTICLES,
            cross, 1, resolve, 0xFFFF00FF);
        return new EnemyAction.Idle();
    }
}
