package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.TrimEffects;
import com.crackedgames.craftics.core.GridArena;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Context object passed to all {@link CombatEffectHandler} callbacks.
 *
 * <p>Provides read access to the player, the arena, current status effects, and the
 * trim scan for the fight. Created once per encounter and refreshed at the start of
 * each turn via {@link #update}.
 *
 * @since 0.2.0
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

    /** The player taking part in the fight. */
    public ServerPlayerEntity getPlayer() { return player; }

    /** The arena the fight is taking place in. */
    public GridArena getArena() { return arena; }

    /** The player's current active status effects. */
    public CombatEffects getPlayerEffects() { return playerEffects; }

    /** The player's trim scan results for the current fight. */
    public TrimEffects.TrimScan getTrimScan() { return trimScan; }

    /** All alive, non-ally combatants currently in the arena. */
    public List<CombatEntity> getAllEnemies() {
        List<CombatEntity> enemies = new ArrayList<>();
        for (CombatEntity e : arena.getOccupants().values()) {
            if (e.isAlive() && !e.isAlly()) enemies.add(e);
        }
        return enemies;
    }

    /** All alive allied combatants currently in the arena. */
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
