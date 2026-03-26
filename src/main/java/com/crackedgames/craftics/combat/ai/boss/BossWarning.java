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

    public BossWarning(int bossEntityId, WarningType type, List<GridPos> affectedTiles,
                       int turnsUntilResolve, EnemyAction resolveAction, int color) {
        this.bossEntityId = bossEntityId;
        this.type = type;
        this.affectedTiles = List.copyOf(affectedTiles);
        this.turnsUntilResolve = turnsUntilResolve;
        this.resolveAction = resolveAction;
        this.color = color;
    }

    public int getBossEntityId() { return bossEntityId; }
    public WarningType getType() { return type; }
    public List<GridPos> getAffectedTiles() { return affectedTiles; }
    public int getTurnsUntilResolve() { return turnsUntilResolve; }
    public EnemyAction getResolveAction() { return resolveAction; }
    public int getColor() { return color; }

    /** Tick down the warning. Returns true if it should resolve now. */
    public boolean tick() {
        turnsUntilResolve--;
        return turnsUntilResolve <= 0;
    }

    public boolean isReady() {
        return turnsUntilResolve <= 0;
    }
}
