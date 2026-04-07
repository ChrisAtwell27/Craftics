package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.TrimEffects;
import com.crackedgames.craftics.core.GridArena;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Context object passed to all combat effect callbacks.
 * Provides access to the player, arena, and combat state.
 * Created once per combat encounter and updated each turn.
 */
public class CombatEffectContext {
    private ServerPlayerEntity player;
    private GridArena arena;
    private CombatEffects playerEffects;
    private TrimEffects.TrimScan trimScan;

    public CombatEffectContext(ServerPlayerEntity player, GridArena arena,
                                CombatEffects playerEffects, TrimEffects.TrimScan trimScan) {
        this.player = player;
        this.arena = arena;
        this.playerEffects = playerEffects;
        this.trimScan = trimScan;
    }

    public ServerPlayerEntity getPlayer() { return player; }
    public GridArena getArena() { return arena; }
    public CombatEffects getPlayerEffects() { return playerEffects; }
    public TrimEffects.TrimScan getTrimScan() { return trimScan; }

    public List<CombatEntity> getAllEnemies() {
        List<CombatEntity> enemies = new ArrayList<>();
        for (CombatEntity e : arena.getOccupants().values()) {
            if (e.isAlive() && !e.isAlly()) enemies.add(e);
        }
        return enemies;
    }

    public List<CombatEntity> getAllAllies() {
        List<CombatEntity> allies = new ArrayList<>();
        for (CombatEntity e : arena.getOccupants().values()) {
            if (e.isAlive() && e.isAlly()) allies.add(e);
        }
        return allies;
    }

    /** Update context at the start of each turn (arena state may have changed). */
    public void update(ServerPlayerEntity player, GridArena arena,
                       CombatEffects playerEffects, TrimEffects.TrimScan trimScan) {
        this.player = player;
        this.arena = arena;
        this.playerEffects = playerEffects;
        this.trimScan = trimScan;
    }
}
