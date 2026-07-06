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
    }

    public int getBossEntityId() { return bossEntityId; }
    public WarningType getType() { return type; }
    public List<GridPos> getAffectedTiles() { return affectedTiles; }
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
