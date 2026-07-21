package com.crackedgames.craftics.combat.biomeeffect.effects;

import com.crackedgames.craftics.combat.biomeeffect.BiomeEffect;
import com.crackedgames.craftics.combat.miniboss.MinibossContext;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;

/**
 * The crimson forest biome's persistent layer: the level opens with a scatter of crimson-fungus
 * hazard tiles - a cobweb-like obstacle that makes anyone who walks through it bleed (Bleeding,
 * 1 turn; see the fungus scan in CombatManager.handleMove). Unlike the warped forest's warp
 * effect there is no periodic per-round component - the crimson fungus is a static field hazard,
 * so this only adds the biome's drifting-spore ambience on top of the opening scatter.
 */
public final class CrimsonBloomEffect implements BiomeEffect {

    // Continuous crimson-forest ambience: a light drift of crimson-spore particles and a soft
    // forest hum, cadenced so it stays atmospheric rather than a fog.
    private static final int PARTICLE_CADENCE = 3;   // spores every 3 ticks (~7/sec)
    private static final int SPORES_MIN = 4;
    private static final int SPORES_MAX = 7;         // inclusive
    private static final int SOUND_CADENCE = 70;     // soft hum every ~3.5s

    @Override
    public String id() {
        return "crimson_bloom";
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        ctx.message("§cCrimson fungus bristles across the ground - mind where you step.");
        ctx.playSound(SoundEvents.AMBIENT_CRIMSON_FOREST_LOOP.value(), 0.4f, 1.0f);
        FungusScatter.scatter(ctx, TileType.CRIMSON_FUNGUS);
    }

    @Override
    public void onCombatTick(MinibossContext ctx, int tick) {
        if (tick % PARTICLE_CADENCE == 0) {
            GridArena arena = ctx.arena();
            int width = arena.getWidth();
            int height = arena.getHeight();
            int spores = SPORES_MIN + ctx.rng().nextInt(SPORES_MAX - SPORES_MIN + 1);
            for (int i = 0; i < spores; i++) {
                GridPos pos = new GridPos(ctx.rng().nextInt(Math.max(1, width)),
                                          ctx.rng().nextInt(Math.max(1, height)));
                if (!arena.isInBounds(pos)) continue;
                ctx.spawnTileParticle(ParticleTypes.CRIMSON_SPORE, pos, 1, 0.4, 0.005);
            }
        }
        if (tick % SOUND_CADENCE == 0) {
            ctx.playSound(SoundEvents.AMBIENT_CRIMSON_FOREST_LOOP.value(), 0.35f, 1.0f);
        }
    }
}
