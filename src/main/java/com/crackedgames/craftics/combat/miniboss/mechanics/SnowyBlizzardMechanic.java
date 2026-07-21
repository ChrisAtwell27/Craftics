package com.crackedgames.craftics.combat.miniboss.mechanics;

import com.crackedgames.craftics.combat.miniboss.MinibossContext;
import com.crackedgames.craftics.combat.miniboss.MinibossMechanic;
import com.crackedgames.craftics.combat.miniboss.MinibossSpawns;
import com.crackedgames.craftics.compat.creeperoverhaul.CreeperOverhaulCompat;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.level.LevelDefinition;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The snowy biome's level-4 miniboss: the Blizzard encounter. Opens with 3 creepers - real
 * {@code creeperoverhaul:snowy_creeper}s when Creeper Overhaul is loaded, plain
 * {@code minecraft:creeper} otherwise - ordinal-scaled exactly like
 * {@link DesertSandstormMechanic}'s husks. On top of the base creeper trio, raid-style
 * reinforcement waves add two more (snowy) creepers every {@link #ADD_CADENCE} rounds while the
 * arena is under the {@link #CROWD_CAP}, mirroring {@link JungleBroodmotherMechanic}'s add-wave
 * loop and live-tile dedup.
 *
 * <p>The blizzard's wind gusts are no longer this mechanic's concern - they come from the snowy
 * biome's persistent {@link com.crackedgames.craftics.combat.biomeeffect.effects.BlizzardWindsEffect}
 * weather layer, which telegraphs and drags independently of this fight's special-level spawns.
 *
 * <p>No extra win objective - clearing all creepers (and any reinforcement waves) completes the
 * fight, so {@link #isComplete} uses the interface default (always true).
 */
public final class SnowyBlizzardMechanic implements MinibossMechanic {

    private static final int CREEPER_COUNT = 3;
    private static final int ADD_CADENCE = 3; // reinforcement wave every 3rd round
    private static final int ADD_WAVE_SIZE = 2;
    private static final int CROWD_CAP = 8; // skip reinforcements if this many enemies are already alive

    /** Resolves the themed creeper type for this fight: the Creeper Overhaul snowy variant when
     *  the mod is loaded, else the vanilla creeper. Applied to both the initial trio and every
     *  reinforcement wave so the whole fight reads as "snowy creepers", not just the opener. */
    private static String creeperTypeId() {
        return CreeperOverhaulCompat.isLoaded() ? "creeperoverhaul:snowy_creeper" : "minecraft:creeper";
    }

    @Override
    public String biomeId() {
        return "snowy";
    }

    @Override
    public String introTitle() {
        return "§b§l☠ Blizzard";
    }

    @Override
    public List<LevelDefinition.EnemySpawn> initialSpawns(int width, int height, int ordinal, Random rng) {
        int hpBonus = ordinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        int atkBonus = ordinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());

        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(width / 2, 0)); // player start - never spawn here

        String creeperType = creeperTypeId();
        for (int i = 0; i < CREEPER_COUNT; i++) {
            GridPos pos = MinibossSpawns.findOpen(width, height, used, rng);
            if (pos == null) continue;
            used.add(pos);
            spawns.add(new LevelDefinition.EnemySpawn(creeperType, pos,
                12 + hpBonus, 5 + atkBonus, 0, 1));
        }

        return spawns;
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        ctx.banner(introTitle());
        ctx.playSound(SoundEvents.ENTITY_CREEPER_PRIMED, 0.6f, 0.7f);
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        spawnReinforcements(ctx);
    }

    /** Every 3rd round, add 2 more (snowy) creepers while the arena isn't already crowded. */
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
            ctx.spawnMob(creeperTypeId(), pos, 12, 5, 0, 1);
            // Whiteout flurry on the tile the reinforcement creeper emerges from.
            ctx.spawnTileParticle(ParticleTypes.SNOWFLAKE, pos, 8, 0.4, 0.05);
            spawnedAny = true;
        }
        if (spawnedAny) {
            ctx.message("§bMore creepers stagger out of the whiteout!");
            ctx.playSound(SoundEvents.ENTITY_CREEPER_PRIMED, 0.5f, 1.0f);
        }
    }
}
