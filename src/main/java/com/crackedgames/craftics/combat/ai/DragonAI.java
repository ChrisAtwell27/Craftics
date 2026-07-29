package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.boss.BossAI;
import com.crackedgames.craftics.combat.ai.boss.BossWarning;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Dragon's Nest Boss - "The Ender Dragon" (backgroundBoss approach).
 *
 * ─── Attack State (default) ────────────────────────────────────────────
 *  Dragon is off-stage. Each turn it telegraphs one attack:
 *   • Breath Wave - lights a 3-wide line at the arena edge nearest the player.
 *   • Breath Cross - the player's full row AND column.
 *   • Swoop - a 3-wide corridor the full length of the arena.
 *  All three are telegraphed a turn ahead, and the tiles they warn are exactly
 *  the tiles they damage and exactly the tiles they set alight. After a cycle
 *  of attacks, the dragon perches.
 *
 * ─── Fire model ───────────────────────────────────────────────────────
 *  The dragon does NOT paint a shape of flame terrain and let it time out. It
 *  lights its footprint through the arena's burn cycle
 *  ({@link EnemyAction.IgniteTiles}), which spreads a ring per turn, collapses
 *  each tile to magma behind the front, and burns it out. So the shape the
 *  player dodges is only where the fire STARTS - it keeps growing after the
 *  turn it landed on.
 *
 *  <p>It lights SOUL fire, which needs no fuel: it crosses bare stone and eats
 *  through walls, where ordinary fire would stop at the first tile with nothing
 *  to burn. It also sets anyone standing in it to Burning III rather than II.
 *  Nothing caps the spread; it is bounded by the arena edge and by its own wake,
 *  since a tile that has just burned refuses to catch again while it cools.
 *
 *  <p>Note this arena does NOT scar. End stone and obsidian are not fuel, and
 *  {@code igniteTile} reads non-fuel ground as restoring - it comes back as
 *  itself with a short cooldown rather than burning to permanent ash the way a
 *  grass arena does. So the front can only advance into ground it has not
 *  reached yet, but ground behind it does eventually become flammable again.
 *
 * ─── Perch State (2 turns P1, 3 turns P2) ─────────────────────────────
 *  Dragon visible + targetable on a cluster of centre tiles:
 *   • Wing Buffet (odd turns) - pushes player 3 tiles away.
 *   • Tail Slam (even turns) - telegraphed radius-3 AoE.
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
    /** Alternates breath-WAVE orientation (independent of the swoop's turn-parity axis). */
    private boolean lastWaveHorizontal = false;

    public State getState() { return state; }
    public boolean isDragonPhaseTwo() { return isPhaseTwo(); }
    /** Consumed by CombatManager to detect state changes and update occupancy/messages. */
    private State lastReportedState = State.ATTACKING;
    public boolean hasStateChanged() { return state != lastReportedState; }
    public void acknowledgeStateChange() { lastReportedState = state; }

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
     * Breathe a line of soul fire along the arena edge nearest the player, and leave it
     * there. The line does not march - the burn cycle spreads it, a ring per turn, so what
     * started as a 3-wide strip at the far wall arrives as an advancing field.
     *
     * <p>Telegraphed like every other dragon attack. The old marching wave went unwarned on
     * the argument that the fire WAS the warning, but that only held while it was a wall of
     * flame crossing the arena in plain sight. A single line that lands and then creeps has
     * nothing to see on the turn it lands, so it gets a highlight like the rest.
     */
    private EnemyAction spawnBreathWave(CombatEntity self, GridArena arena, GridPos playerPos) {
        int w = arena.getWidth();
        int h = arena.getHeight();
        int dmg = self.getAttackPower() + (isPhaseTwo() ? 3 : 0);

        // Alternate the axis the breath sweeps along, so consecutive waves don't stack the
        // same edge and leave one half of the arena permanently safe.
        boolean horizontal = !lastWaveHorizontal;
        lastWaveHorizontal = horizontal;

        // 3 wide, centred on the player, laid flat against whichever edge they're nearest -
        // the fire has the least ground to cross to reach them.
        List<GridPos> line = new ArrayList<>();
        if (horizontal) {
            int baseX = Math.max(0, Math.min(w - 3, playerPos.x() - 1));
            int edgeZ = playerPos.z() < h / 2 ? 0 : h - 1;
            for (int d = 0; d < 3; d++) addIfInBounds(arena, line, new GridPos(baseX + d, edgeZ));
        } else {
            int baseZ = Math.max(0, Math.min(h - 3, playerPos.z() - 1));
            int edgeX = playerPos.x() < w / 2 ? 0 : w - 1;
            for (int d = 0; d < 3; d++) addIfInBounds(arena, line, new GridPos(edgeX, baseZ + d));
        }
        if (line.isEmpty()) return new EnemyAction.Idle();

        EnemyAction resolve = new EnemyAction.CompositeAction(List.of(
            new EnemyAction.TileAreaAttack(line, line.get(line.size() / 2), dmg, "dragon_breath"),
            new EnemyAction.IgniteTiles(line, true)
        ));

        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
            line, 1, resolve, 0xFF3AB0FF);
        return new EnemyAction.Idle();
    }

    /** Add {@code t} to {@code out} when the arena actually has that tile. */
    private static void addIfInBounds(GridArena arena, List<GridPos> out, GridPos t) {
        if (arena.isInBounds(t)) out.add(t);
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

        // Warned, damaged, lit - one and the same list. The dragon's body passes down the
        // entire corridor, so the entire corridor hits and the entire corridor catches; the
        // player is owed a telegraph that means exactly what it shows. TileAreaAttack also
        // guarantees one hit per victim, so the wide footprint can't stack on anyone.
        EnemyAction resolve = new EnemyAction.CompositeAction(List.of(
            new EnemyAction.TileAreaAttack(warningTiles, playerPos, dmg, "dragon_swoop"),
            new EnemyAction.IgniteTiles(warningTiles, true)
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

        int dmg = self.getAttackPower() + (isPhaseTwo() ? 3 : 0);

        // The breath sweeps the full row and column, so the full row and column catch. The
        // telegraph is the footprint - every tile it paints burns.
        EnemyAction resolve = new EnemyAction.CompositeAction(List.of(
            new EnemyAction.LineAttack(new GridPos(0, playerPos.z()), 1, 0, arena.getWidth(), dmg),
            new EnemyAction.LineAttack(new GridPos(playerPos.x(), 0), 0, 1, arena.getHeight(), dmg),
            new EnemyAction.IgniteTiles(cross, true)
        ));

        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.GATHERING_PARTICLES,
            cross, 1, resolve, 0xFFFF00FF);
        return new EnemyAction.Idle();
    }
}
