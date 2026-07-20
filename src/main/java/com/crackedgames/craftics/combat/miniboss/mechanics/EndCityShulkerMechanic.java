package com.crackedgames.craftics.combat.miniboss.mechanics;

import com.crackedgames.craftics.combat.miniboss.MinibossContext;
import com.crackedgames.craftics.combat.miniboss.MinibossMechanic;
import com.crackedgames.craftics.combat.miniboss.MinibossSpawns;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.level.LevelDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The end city biome's level-4 miniboss: the Shulker Sentinel encounter. Opens with one beefy
 * "elite" shulker (roughly double a normal add's HP, plus the elite's own range-4 shots so it
 * reads as the miniboss) placed near arena center, plus one normal shulker add for company.
 * Every 3rd round the sentinel "deploys" two more shulker adds near the elite, unless the arena
 * is already crowded.
 *
 * <p>Design note: the brief's original vision calls for the sentinel firing levitation shots at
 * the player every round. {@link MinibossContext} only exposes {@code enemies()} - there is no
 * handle back to the player entity or any player-targeted status API, and
 * {@code CombatEntity#applyLevitationState(int, int)} is an enemy-side primitive (it exists so
 * *enemies* can be levitated, e.g. by player abilities) - reusing it here would do nothing
 * useful and inventing a new player-targeting primitive is out of scope for this task. This
 * mechanic drops the scripted levitation-shot flavor and leans on what is already true of a
 * shulker: its normal AI already fires real levitation-inducing shulker bullets at range against
 * the player, and the elite's doubled HP plus the "deploy more shulkers" wave keep the ranged
 * pressure escalating over the course of the fight. Between the elite's own attacks and the
 * periodic reinforcement waves this reads as a complete ranged miniboss encounter without any
 * invented primitives.
 *
 * <p>No extra win objective - the elite is a normal enemy (no scenery/inert flags), so clearing
 * every shulker (elite included) completes the fight via the base "all enemies cleared" check.
 * {@link #isComplete} uses the interface default (always true).
 */
public final class EndCityShulkerMechanic implements MinibossMechanic {

    private static final int ADD_CADENCE = 3; // reinforcement wave every 3rd round
    private static final int ADD_WAVE_SIZE = 2;
    private static final int CROWD_CAP = 8; // skip reinforcements if this many enemies are already alive

    @Override
    public String biomeId() {
        return "end_city";
    }

    @Override
    public String introTitle() {
        return "§d§l☠ Shulker Sentinel";
    }

    @Override
    public List<LevelDefinition.EnemySpawn> initialSpawns(int width, int height, int ordinal, Random rng) {
        int hpBonus = ordinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        int atkBonus = ordinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());

        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(width / 2, 0)); // player start - never spawn here

        // Elite sentinel, placed near arena center - roughly double a normal add's HP, ranged.
        GridPos elitePos = MinibossSpawns.findOpenBiased(width, height, used, rng, width / 2, height / 2);
        if (elitePos == null) elitePos = new GridPos(width / 2, height / 2);
        used.add(elitePos);
        spawns.add(new LevelDefinition.EnemySpawn("minecraft:shulker", elitePos,
            40 + hpBonus * 2, 6 + atkBonus, 4, 4));

        // One normal shulker for company at the opening.
        GridPos addPos = MinibossSpawns.findOpenBiased(width, height, used, rng, width / 2, height / 2);
        if (addPos != null) {
            used.add(addPos);
            spawns.add(new LevelDefinition.EnemySpawn("minecraft:shulker", addPos,
                14 + hpBonus, 4, 2, 4));
        }

        return spawns;
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        ctx.banner(introTitle());
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        if (ctx.round() % ADD_CADENCE != 0) return;

        int liveCount = 0;
        for (var e : ctx.enemies()) {
            if (e.isAlive()) liveCount++;
        }
        if (liveCount >= CROWD_CAP) return; // arena too crowded, skip this wave

        var arena = ctx.arena();
        List<GridPos> used = new ArrayList<>();
        for (var e : ctx.enemies()) {
            if (e.isAlive() && e.getGridPos() != null) used.add(e.getGridPos());
        }

        boolean spawnedAny = false;
        for (int i = 0; i < ADD_WAVE_SIZE; i++) {
            GridPos pos = MinibossSpawns.findOpenBiased(arena.getWidth(), arena.getHeight(), used, ctx.rng(),
                arena.getWidth() / 2, arena.getHeight() / 2);
            if (pos == null) continue;
            used.add(pos);
            ctx.spawnMob("minecraft:shulker", pos, 14, 4, 2, 4);
            spawnedAny = true;
        }
        if (spawnedAny) ctx.message("§dThe Sentinel deploys more shulkers!");
    }
}
