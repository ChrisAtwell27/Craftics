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
 * The desert biome's level-4 miniboss: the Sandstorm encounter. Opens with 3 husks, ordinal-scaled
 * exactly like {@link PlainsGraveyardMechanic}'s zombies. On top of the base husk trio,
 * raid-style reinforcement waves add two more husks every {@link #ADD_CADENCE} rounds while the
 * arena is under the {@link #CROWD_CAP}, mirroring {@link SnowyBlizzardMechanic}'s add-wave loop
 * and live-tile dedup.
 *
 * <p>The blinding sandstorm weather is no longer this mechanic's concern - it comes from the
 * desert biome's persistent {@link com.crackedgames.craftics.combat.biomeeffect.effects.SandstormEffect}
 * weather layer, which blinds independently of this fight's special-level spawns.
 *
 * <p>No extra win objective - clearing the husks (and any reinforcement waves) completes the
 * fight, so {@link #isComplete} uses the interface default (always true).
 */
public final class DesertSandstormMechanic implements MinibossMechanic {

    private static final int HUSK_COUNT = 3;
    private static final int ADD_CADENCE = 3; // reinforcement wave every 3rd round
    private static final int ADD_WAVE_SIZE = 2;
    private static final int CROWD_CAP = 8; // skip reinforcements if this many enemies are already alive

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
        ctx.banner(introTitle());
        ctx.playSound(SoundEvents.BLOCK_SAND_BREAK, 0.6f, 0.8f);
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        spawnReinforcements(ctx);
    }

    /** Every 3rd round, add 2 more husks while the arena isn't already crowded. */
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
            ctx.spawnMob("minecraft:husk", pos, 14, 4, 1, 1);
            // Sandy puff as the husk staggers out of the sandstorm.
            ctx.spawnHazardBurst(ParticleTypes.POOF, pos);
            spawnedAny = true;
        }
        if (spawnedAny) {
            ctx.message("§6More husks stagger out of the sandstorm!");
            ctx.playSound(SoundEvents.ENTITY_HUSK_AMBIENT, 0.5f, 0.9f);
        }
    }
}
