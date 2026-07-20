package com.crackedgames.craftics.combat.miniboss.mechanics;

import com.crackedgames.craftics.combat.miniboss.MinibossContext;
import com.crackedgames.craftics.combat.miniboss.MinibossMechanic;
import com.crackedgames.craftics.combat.miniboss.MinibossSpawns;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;
import com.crackedgames.craftics.level.LevelDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The desert biome's level-4 miniboss: the Sandstorm encounter. Opens with 3 husks, ordinal-scaled
 * exactly like {@link PlainsGraveyardMechanic}'s zombies. The signature hazard is a telegraphed
 * sand gust: one round it announces a lane (a full row or column), the next round it buries that
 * lane in temporary {@link TileType#RUBBLE} the player has to path around, then the cycle re-picks
 * a new lane.
 *
 * <p>Design note: the "real" sandstorm would hit the player/party directly (damage + Slowed), but
 * {@link MinibossContext} only exposes {@link MinibossContext#enemies()} - there is no primitive to
 * reach the player/party from a mechanic. Rather than invent one, this implements the achievable
 * equivalent: the telegraphed lane becomes an unwalkable RUBBLE hazard for a couple of rounds
 * (sand piling up), forcing a reroute instead of applying direct damage/status. A future
 * "hazard hits party" context primitive (e.g. a {@code damagePartyOnTile} hook) would let this
 * mechanic apply real damage/Slowed to whoever is caught standing in the lane when it resolves,
 * which would read closer to the intended design.
 *
 * <p>No extra win objective - clearing the husks (and any future adds) completes the fight, so
 * {@link #isComplete} uses the interface default (always true).
 */
public final class DesertSandstormMechanic implements MinibossMechanic {

    private static final int HUSK_COUNT = 3;
    private static final int RUBBLE_DURATION = 2; // rounds the buried lane stays impassable

    /** -1 when no lane is currently telegraphed. */
    private int pendingRow = -1;
    private int pendingCol = -1;

    @Override
    public String biomeId() {
        return "desert";
    }

    @Override
    public String introTitle() {
        return "§6§l☠ Sandstorm";
    }

    @Override
    public List<LevelDefinition.EnemySpawn> initialSpawns(int width, int height, int ordinal, Random rng) {
        int hpBonus = ordinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        int atkBonus = ordinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());

        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(width / 2, 0)); // player start - never spawn here

        for (int i = 0; i < HUSK_COUNT; i++) {
            GridPos pos = MinibossSpawns.findOpen(width, height, used, rng);
            if (pos == null) continue;
            used.add(pos);
            spawns.add(new LevelDefinition.EnemySpawn("minecraft:husk", pos,
                14 + hpBonus, 4 + atkBonus, 1, 1));
        }

        return spawns;
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        pendingRow = -1;
        pendingCol = -1;
        ctx.banner(introTitle());
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        GridArena arena = ctx.arena();

        // Resolve a lane telegraphed last round by burying it in temporary rubble.
        if (pendingRow >= 0) {
            for (int x = 0; x < arena.getWidth(); x++) {
                ctx.placeTemporaryTile(new GridPos(x, pendingRow), TileType.RUBBLE, RUBBLE_DURATION);
            }
            ctx.message("§6Sand buries the lane!");
            pendingRow = -1;
        } else if (pendingCol >= 0) {
            for (int z = 0; z < arena.getHeight(); z++) {
                ctx.placeTemporaryTile(new GridPos(pendingCol, z), TileType.RUBBLE, RUBBLE_DURATION);
            }
            ctx.message("§6Sand buries the lane!");
            pendingCol = -1;
        }

        // Telegraph the next lane (alternates row/column via coin flip each cycle).
        if (ctx.rng().nextBoolean()) {
            pendingRow = ctx.rng().nextInt(Math.max(1, arena.getHeight()));
        } else {
            pendingCol = ctx.rng().nextInt(Math.max(1, arena.getWidth()));
        }
        ctx.message("§6Sandstorm gathers along a lane...");
    }
}
