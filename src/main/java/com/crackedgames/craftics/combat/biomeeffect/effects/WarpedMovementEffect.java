package com.crackedgames.craftics.combat.biomeeffect.effects;

import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.biomeeffect.BiomeEffect;
import com.crackedgames.craftics.combat.miniboss.MinibossContext;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;

/**
 * The warped forest biome's persistent weather layer: the warped air periodically bends the
 * party's sense of direction, mirroring their movement (not attacks) about their own tile for
 * their next turn. The level also opens with a scatter of warped-fungus hazard tiles - a
 * cobweb-like obstacle that inflicts Warped on anyone who walks through it (see the fungus
 * scan in CombatManager.handleMove).
 */
public final class WarpedMovementEffect implements BiomeEffect {

    @Override
    public String id() {
        return "warped";
    }

    // Continuous warped-forest ambience: a light drift of warped-spore particles across the arena
    // and a soft, low portal-ambient hum, cadenced so the air feels alive without a fog.
    private static final int PARTICLE_CADENCE = 3;   // spores every 3 ticks (~7/sec)
    private static final int SPORES_MIN = 4;
    private static final int SPORES_MAX = 7;         // inclusive
    private static final int SOUND_CADENCE = 70;     // soft hum every ~3.5s

    @Override
    public void onFightStart(MinibossContext ctx) {
        ctx.message("§5The warped air bends your sense of direction...");
        ctx.playSound(SoundEvents.AMBIENT_WARPED_FOREST_LOOP.value(), 0.4f, 1.0f);
        FungusScatter.scatter(ctx, TileType.WARPED_FUNGUS);
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        // Every other round (round 2, 4, 6...), warp the party's movement for their next turn.
        // applyPartyEffect adds the enemy-phase-tick compensation, so 1 = active on the player's
        // next turn exactly. Starting turn 2, then every other turn.
        if (ctx.round() % 2 != 0) return;
        ctx.applyPartyEffect(CombatEffects.EffectType.WARPED, 1);
        ctx.playSound(SoundEvents.BLOCK_RESPAWN_ANCHOR_AMBIENT, 0.7f, 0.6f);
        ctx.message("§5The warp twists your bearings - your movement is mirrored this turn!");
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
                ctx.spawnTileParticle(ParticleTypes.WARPED_SPORE, pos, 1, 0.4, 0.005);
            }
        }
        if (tick % SOUND_CADENCE == 0) {
            ctx.playSound(SoundEvents.AMBIENT_WARPED_FOREST_LOOP.value(), 0.35f, 1.0f);
        }
    }
}
