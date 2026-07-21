package com.crackedgames.craftics.combat.biomeeffect.effects;

import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.biomeeffect.BiomeEffect;
import com.crackedgames.craftics.combat.miniboss.MinibossContext;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

/**
 * The desert biome's persistent weather layer: a driving sandstorm that blinds the whole party
 * every 3rd round.
 */
public final class SandstormEffect implements BiomeEffect {

    private static final int CADENCE = 3;
    private static final int BLIND_TURNS = 1;

    // --- Continuous ambience (onCombatTick) ---
    private static final int PARTICLE_CADENCE = 2; // scatter sand every 2 ticks (~10/sec)
    private static final int GRAINS_MIN = 2;
    private static final int GRAINS_MAX = 3; // inclusive
    private static final int HISS_CADENCE = 50; // soft sand hiss loop every 2.5 seconds
    private static final int BLIND_BURST_GRAINS = 20; // one-shot burst on the blind round

    @Override
    public String id() {
        return "sandstorm";
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        ctx.message("§eA sandstorm rolls in - watch for the blinding gusts.");
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        if (ctx.round() % CADENCE != 0) return;
        ctx.applyPartyEffect(CombatEffects.EffectType.BLINDNESS, BLIND_TURNS);
        ctx.message("§e☀ Sand whips into your eyes - blinded for a turn!");
        // One-shot louder gust + a burst of sand on the blind round, layered on top of the
        // steady ambient hiss so the blinding moment reads as a sudden intensification.
        ctx.playSound(SoundEvents.BLOCK_SAND_BREAK, 0.7f, 0.7f);
        gustBurst(ctx);
    }

    /** A dense one-shot burst of ASH scattered low across the arena - the visual gust that
     *  accompanies the periodic blind. */
    private void gustBurst(MinibossContext ctx) {
        GridArena arena = ctx.arena();
        int width = arena.getWidth();
        int height = arena.getHeight();
        Random rng = ctx.rng();
        BlockPos origin = arena.getOrigin();
        for (int i = 0; i < BLIND_BURST_GRAINS; i++) {
            int x = rng.nextInt(Math.max(1, width));
            int z = rng.nextInt(Math.max(1, height));
            GridPos pos = new GridPos(x, z);
            if (!arena.isInBounds(pos)) continue;
            BlockPos ground = arena.gridToBlockPos(pos);
            double wx = ground.getX() + 0.5;
            double wz = ground.getZ() + 0.5;
            double lowY = origin.getY() + 1.2;
            double driftX = (rng.nextDouble() - 0.5) * 0.8;
            double driftZ = (rng.nextDouble() - 0.5) * 0.8;
            ctx.spawnAmbientParticle(ParticleTypes.ASH, wx, lowY, wz,
                1, driftX, 0.1, driftZ, 0.04);
        }
    }

    /**
     * Continuous sandstorm ambience: ASH grains drifting sideways low across the arena every
     * couple of ticks (the same "desert dust" particle already used elsewhere for desert
     * ambience, e.g. CombatManager's per-biome mob VFX), plus a soft periodic sand hiss.
     */
    @Override
    public void onCombatTick(MinibossContext ctx, int tick) {
        if (tick % PARTICLE_CADENCE == 0) {
            GridArena arena = ctx.arena();
            int width = arena.getWidth();
            int height = arena.getHeight();
            Random rng = ctx.rng();
            BlockPos origin = arena.getOrigin();
            int grains = GRAINS_MIN + rng.nextInt(GRAINS_MAX - GRAINS_MIN + 1);
            for (int i = 0; i < grains; i++) {
                int x = rng.nextInt(Math.max(1, width));
                int z = rng.nextInt(Math.max(1, height));
                GridPos pos = new GridPos(x, z);
                if (!arena.isInBounds(pos)) continue;
                BlockPos ground = arena.gridToBlockPos(pos);
                double wx = ground.getX() + 0.5;
                double wz = ground.getZ() + 0.5;
                double lowY = origin.getY() + 0.8; // low across the arena
                double driftX = (rng.nextDouble() - 0.5) * 0.6;
                double driftZ = (rng.nextDouble() - 0.5) * 0.6;
                ctx.spawnAmbientParticle(ParticleTypes.ASH, wx, lowY, wz,
                    1, driftX, 0.05, driftZ, 0.03);
            }
        }

        if (tick % HISS_CADENCE == 0) {
            ctx.playSound(SoundEvents.BLOCK_SAND_BREAK, 0.3f, 0.8f);
        }
    }
}
