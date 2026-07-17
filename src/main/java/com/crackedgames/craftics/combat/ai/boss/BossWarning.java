package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * A telegraphed boss ability warning. The warning is displayed during the player's turn,
 * and the ability resolves at the start of the boss's next turn.
 */
public class BossWarning {
    public enum WarningType {
        TILE_HIGHLIGHT,       // Target tiles glow red
        GROUND_CRACK,         // Fissure particles on tiles (summons, seismic)
        GATHERING_PARTICLES,  // Particles converge on target (channeled abilities)
        DIRECTIONAL,          // Arrow markers (pushes, line attacks)
        ENTITY_GLOW,          // Boss flashes a color (self-buffs, phase transitions)
        SOUND_ONLY            // Just a sound cue
    }

    /** Sentinel {@code trackedId} meaning "this is a fixed-tile warning, not a tracking one". */
    public static final int MARK_NONE = Integer.MIN_VALUE;

    /** Tracked id meaning "the player", who has no {@code CombatEntity} id. Mob entity ids
     *  are positive network ids, so this can never collide with a real combatant. */
    public static final int MARK_PLAYER_ID = -1;

    private final int bossEntityId;
    private final WarningType type;
    private final List<GridPos> affectedTiles;
    private int turnsUntilResolve;
    private final EnemyAction resolveAction;
    private final int color; // ARGB for rendering
    /** Cardinal direction of travel/push for DIRECTIONAL-style telegraphs
     *  (charge path, pull, knockback). (0,0) = no direction; the client then
     *  renders plain warning tiles without arrow glyphs. */
    private final int dirX;
    private final int dirZ;
    /** Entity id of the combatant this warning tracks, or {@link #MARK_NONE} for a
     *  fixed-tile warning. */
    private final int trackedId;
    /** Resolves a tracked id to a live grid position. Null for fixed-tile warnings. */
    private final java.util.function.IntFunction<GridPos> tracker;
    /** For a tracking warning, the Manhattan radius painted around the live mark.
     *  0 paints only the mark tile; a positive value paints the filled Manhattan
     *  diamond (see {@code AoeShapes.filledDiamond}) so the telegraph shows the
     *  whole area the attack reaches from the mark, not just the mark itself. */
    private final int trackRadius;

    public BossWarning(int bossEntityId, WarningType type, List<GridPos> affectedTiles,
                       int turnsUntilResolve, EnemyAction resolveAction, int color) {
        this(bossEntityId, type, affectedTiles, turnsUntilResolve, resolveAction, color, 0, 0);
    }

    /** Directional variant: {@code dirX/dirZ} is the (signum) direction the attack
     *  travels or shoves the player - the client draws marching arrow glyphs on the
     *  affected tiles pointing that way. */
    public BossWarning(int bossEntityId, WarningType type, List<GridPos> affectedTiles,
                       int turnsUntilResolve, EnemyAction resolveAction, int color,
                       int dirX, int dirZ) {
        this.bossEntityId = bossEntityId;
        this.type = type;
        this.affectedTiles = List.copyOf(affectedTiles);
        this.turnsUntilResolve = turnsUntilResolve;
        this.resolveAction = resolveAction;
        this.color = color;
        this.dirX = dirX;
        this.dirZ = dirZ;
        this.trackedId = MARK_NONE;
        this.tracker = null;
        this.trackRadius = 0;
    }

    private BossWarning(int bossEntityId, WarningType type, int trackedId,
                        java.util.function.IntFunction<GridPos> tracker,
                        int turnsUntilResolve, EnemyAction resolveAction, int color,
                        int trackRadius) {
        this.bossEntityId = bossEntityId;
        this.type = type;
        this.affectedTiles = List.of();
        this.turnsUntilResolve = turnsUntilResolve;
        this.resolveAction = resolveAction;
        this.color = color;
        this.dirX = 0;
        this.dirZ = 0;
        this.trackedId = trackedId;
        this.tracker = tracker;
        this.trackRadius = Math.max(0, trackRadius);
    }

    /**
     * A warning that follows a combatant instead of a fixed tile. Its tiles are recomputed
     * from the tracked entity's LIVE position every time {@link #getAffectedTiles()} is read,
     * so the telegraph follows the mark as it moves. A mark frozen to the tile where it was
     * cast would make the counterplay "step one tile aside", which is precisely what a tracking
     * ability must not be.
     *
     * @param trackedId the marked combatant's id (the caller decides its meaning; see the
     *                  resolver it supplies)
     * @param tracker   resolves {@code trackedId} to a live {@link GridPos}, or {@code null}
     *                  if the mark is dead/absent - a null result paints nothing and fizzles
     */
    public static BossWarning tracking(int bossEntityId, WarningType type, int trackedId,
                                       java.util.function.IntFunction<GridPos> tracker,
                                       int turnsUntilResolve, EnemyAction resolveAction, int color) {
        return tracking(bossEntityId, type, trackedId, tracker,
            turnsUntilResolve, resolveAction, color, 0);
    }

    /**
     * Tracking-warning variant that paints a Manhattan {@code trackRadius} area
     * around the live mark, not just the mark tile. Use this when the attack
     * reaches beyond the mark (e.g. a chain that jumps to combatants within N
     * tiles) so the telegraph shows the true danger area and follows the mark as
     * it moves. {@code trackRadius} MUST equal the mechanic's own reach so the
     * paint cannot lie about range. A radius of 0 is identical to the single-tile
     * {@link #tracking(int, WarningType, int, java.util.function.IntFunction, int, EnemyAction, int)}.
     */
    public static BossWarning tracking(int bossEntityId, WarningType type, int trackedId,
                                       java.util.function.IntFunction<GridPos> tracker,
                                       int turnsUntilResolve, EnemyAction resolveAction, int color,
                                       int trackRadius) {
        return new BossWarning(bossEntityId, type, trackedId, tracker,
            turnsUntilResolve, resolveAction, color, trackRadius);
    }

    public int getBossEntityId() { return bossEntityId; }
    public WarningType getType() { return type; }
    /** For a tracking warning, the tiles are recomputed from the mark's LIVE position on every
     *  read, so the sync/highlight path repaints the telegraph as the mark walks. A dead or
     *  absent mark resolves to an empty list and the warning paints nothing. */
    public List<GridPos> getAffectedTiles() {
        if (tracker != null) {
            GridPos live = tracker.apply(trackedId);
            if (live == null) return List.of();
            // trackRadius 0 paints just the mark; a positive radius paints the
            // filled Manhattan diamond that reach covers, recomputed here so it
            // still follows the mark as it walks. The chain uses Manhattan
            // distance, so a diamond (not a Chebyshev square) is what the mechanic
            // actually reaches - the paint and the jump agree by construction.
            if (trackRadius <= 0) return List.of(live);
            return com.crackedgames.craftics.combat.AoeShapes.filledDiamond(live, trackRadius);
        }
        return affectedTiles;
    }
    public int getTurnsUntilResolve() { return turnsUntilResolve; }
    public EnemyAction getResolveAction() { return resolveAction; }
    public int getColor() { return color; }
    public int getDirX() { return dirX; }
    public int getDirZ() { return dirZ; }
    public boolean hasDirection() { return dirX != 0 || dirZ != 0; }

    /** Tick down the warning. Returns true if it should resolve now. */
    public boolean tick() {
        turnsUntilResolve--;
        return turnsUntilResolve <= 0;
    }

    public boolean isReady() {
        return turnsUntilResolve <= 0;
    }
}
