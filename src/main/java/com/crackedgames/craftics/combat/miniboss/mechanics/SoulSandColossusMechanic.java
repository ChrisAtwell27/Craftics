package com.crackedgames.craftics.combat.miniboss.mechanics;

import com.crackedgames.craftics.combat.miniboss.MinibossContext;
import com.crackedgames.craftics.combat.miniboss.MinibossMechanic;
import com.crackedgames.craftics.combat.miniboss.MinibossSpawns;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.level.LevelDefinition;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The soul sand valley biome's level-4 miniboss: the Bone Colossus. Opens with one beefy "elite"
 * wither skeleton (roughly double a normal add's HP, so it reads as the miniboss) plus one normal
 * skeleton for company. Every 3rd round two more skeleton archers rise from the sand near the
 * colossus, unless the arena is already crowded.
 *
 * <p>Design note: the brief floats a "soul sand slows tiles" flavor for the arena floor. There is
 * no per-tile slow/status primitive exposed on {@link MinibossContext} (no analogous mechanism to
 * {@code placeTemporaryTile} that applies a movement debuff to whoever stands on it), and inventing
 * one is out of scope for this task. This mechanic implements the achievable core of the encounter
 * (elite + ranged reinforcement waves) and drops the slow-tile flavor entirely; the elite's doubled
 * HP plus the steady archer pressure carry the "miniboss lair" feel on their own.
 *
 * <p>No extra win objective - the elite is a normal enemy (no scenery/inert flags), so clearing
 * every skeleton (elite included) completes the fight via the base "all enemies cleared" check.
 * {@link #isComplete} uses the interface default (always true).
 */
public final class SoulSandColossusMechanic implements MinibossMechanic {

    private static final int ADD_CADENCE = 3; // reinforcement wave every 3rd round
    private static final int ADD_WAVE_SIZE = 2;
    private static final int CROWD_CAP = 8; // skip reinforcements if this many enemies are already alive

    @Override
    public String biomeId() {
        return "soul_sand_valley";
    }

    @Override
    public String introTitle() {
        return "§f§l☠ Bone Colossus";
    }

    @Override
    public List<LevelDefinition.EnemySpawn> initialSpawns(int width, int height, int ordinal, Random rng) {
        int hpBonus = ordinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        int atkBonus = ordinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());

        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(width / 2, 0)); // player start - never spawn here

        // Elite colossus, placed near arena center - roughly double a normal add's HP.
        GridPos elitePos = MinibossSpawns.findOpenBiased(width, height, used, rng, width / 2, height / 2);
        if (elitePos == null) elitePos = new GridPos(width / 2, height / 2);
        used.add(elitePos);
        spawns.add(new LevelDefinition.EnemySpawn("minecraft:wither_skeleton", elitePos,
            45 + hpBonus * 2, 7 + atkBonus, 2, 1));

        // One normal skeleton for company at the opening.
        GridPos addPos = MinibossSpawns.findOpenBiased(width, height, used, rng, width / 2, height / 2);
        if (addPos != null) {
            used.add(addPos);
            spawns.add(new LevelDefinition.EnemySpawn("minecraft:skeleton", addPos,
                10 + hpBonus, 4 + atkBonus, 0, 3));
        }

        return spawns;
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        ctx.banner(introTitle());
        ctx.playSound(SoundEvents.ENTITY_WITHER_SKELETON_AMBIENT, 0.6f, 0.7f);
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
            ctx.spawnMob("minecraft:skeleton", pos, 10, 4, 0, 3);
            spawnedAny = true;
        }
        if (spawnedAny) {
            ctx.message("§f☠ The Colossus raises the fallen!");
            ctx.playSound(SoundEvents.ENTITY_SKELETON_AMBIENT, 0.5f, 0.9f);
        }
    }
}
