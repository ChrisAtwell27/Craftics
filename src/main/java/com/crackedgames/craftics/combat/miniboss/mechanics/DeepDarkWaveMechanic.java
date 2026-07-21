package com.crackedgames.craftics.combat.miniboss.mechanics;

import com.crackedgames.craftics.combat.miniboss.MinibossContext;
import com.crackedgames.craftics.combat.miniboss.MinibossMechanic;
import com.crackedgames.craftics.combat.miniboss.MinibossSpawns;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.level.LevelDefinition;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The deep dark biome's level-4 miniboss: The Swarm. Replaces the old Warden Echo elite-miniboss
 * with a plain wave encounter (the deep dark's sculk-sensor hazard layer - see
 * {@link com.crackedgames.craftics.combat.biomeeffect.effects.SculkSensorEffect} - now carries the
 * biome's "dangerous dark" identity, so the special level itself is just a swarm fight). Opens
 * with 2 silverfish + 1 skeleton, ordinal-scaled exactly like {@link SnowyBlizzardMechanic}'s
 * creeper trio. On top of the base trio, reinforcement waves add two more silverfish every
 * {@link #ADD_CADENCE} rounds while the arena is under the {@link #CROWD_CAP}, mirroring
 * {@link SnowyBlizzardMechanic}'s add-wave loop and live-tile dedup exactly.
 *
 * <p>No extra win objective - clearing all enemies (and any reinforcement waves) completes the
 * fight, so {@link #isComplete} uses the interface default (always true).
 */
public final class DeepDarkWaveMechanic implements MinibossMechanic {

    private static final int ADD_CADENCE = 3; // reinforcement wave every 3rd round
    private static final int ADD_WAVE_SIZE = 2;
    private static final int CROWD_CAP = 8; // skip reinforcements if this many enemies are already alive

    @Override
    public String biomeId() {
        return "deep_dark";
    }

    @Override
    public String introTitle() {
        return "§3§l☠ The Swarm";
    }

    @Override
    public List<LevelDefinition.EnemySpawn> initialSpawns(int width, int height, int ordinal, Random rng) {
        int hpBonus = ordinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        int atkBonus = ordinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());

        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(width / 2, 0)); // player start - never spawn here

        for (int i = 0; i < 2; i++) {
            GridPos pos = MinibossSpawns.findOpen(width, height, used, rng);
            if (pos == null) continue;
            used.add(pos);
            spawns.add(new LevelDefinition.EnemySpawn("minecraft:silverfish", pos,
                8 + hpBonus, 3 + atkBonus, 0, 1));
        }

        GridPos skeletonPos = MinibossSpawns.findOpen(width, height, used, rng);
        if (skeletonPos != null) {
            used.add(skeletonPos);
            spawns.add(new LevelDefinition.EnemySpawn("minecraft:skeleton", skeletonPos,
                10 + hpBonus, 4 + atkBonus, 0, 3));
        }

        return spawns;
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        ctx.banner(introTitle());
        ctx.playSound(SoundEvents.ENTITY_SILVERFISH_AMBIENT, 0.6f, 0.7f);
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        spawnReinforcements(ctx);
    }

    /** Every 3rd round, add 2 more silverfish while the arena isn't already crowded. */
    private void spawnReinforcements(MinibossContext ctx) {
        if (ctx.round() % ADD_CADENCE != 0) return;

        int liveCount = 0;
        for (var e : ctx.enemies()) {
            if (e.isAlive()) liveCount++;
        }
        if (liveCount >= CROWD_CAP) return; // arena too crowded, skip this wave

        GridArena arena = ctx.arena();
        List<GridPos> used = new ArrayList<>();
        for (var e : ctx.enemies()) {
            if (e.isAlive() && e.getGridPos() != null) used.add(e.getGridPos());
        }

        boolean spawnedAny = false;
        for (int i = 0; i < ADD_WAVE_SIZE; i++) {
            GridPos pos = MinibossSpawns.findOpen(arena.getWidth(), arena.getHeight(), used, ctx.rng());
            if (pos == null) continue;
            used.add(pos);
            ctx.spawnMob("minecraft:silverfish", pos, 8, 3, 0, 1);
            // Sculk-soul wisp on the tile the swarm silverfish crawls out of.
            ctx.spawnHazardBurst(ParticleTypes.SCULK_SOUL, pos);
            spawnedAny = true;
        }
        if (spawnedAny) {
            ctx.message("§3The swarm swells from the dark!");
            ctx.playSound(SoundEvents.ENTITY_SILVERFISH_AMBIENT, 0.5f, 1.0f);
        }
    }
}
