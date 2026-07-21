package com.crackedgames.craftics.combat.biomeeffect.effects;

import com.crackedgames.craftics.combat.biomeeffect.BiomeEffect;
import com.crackedgames.craftics.combat.miniboss.MinibossContext;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

/**
 * The snowy biome's persistent weather layer: a two-phase wind gust that telegraphs a random
 * cardinal direction one round, then drags every player and enemy on the grid that direction
 * the next. This is a verbatim move of the gust logic that used to live in
 * {@code SnowyBlizzardMechanic} (the level-4 miniboss) - that mechanic drops its own copy once
 * this effect takes over as the single source of the winds, so the behavior (cadence, messages,
 * frost accent) must stay identical.
 */
public final class BlizzardWindsEffect implements BiomeEffect {

    private static final int GUST_CADENCE = 2; // gust cycle: telegraph one round, drag the next
    private static final int FROST_TILES_MIN = 2;
    private static final int FROST_TILES_MAX = 3; // inclusive
    private static final int FROST_DURATION = 2; // rounds the gust's frost accent lingers

    /** Wind direction rolled each gust, as a (dx,dz) cardinal pair. */
    private static final int DIR_NORTH = 0; // toward z=0
    private static final int DIR_SOUTH = 1; // toward z=height-1
    private static final int DIR_EAST = 2;  // toward x=width-1
    private static final int DIR_WEST = 3;  // toward x=0

    /** The gust telegraphed last round, waiting to resolve this round. null = nothing pending;
     *  a 2-int {dirX, dirZ}. Instance state on the shared singleton, reset in onFightStart. */
    private int[] pendingGust = null;

    // --- Continuous ambience (onCombatTick) ---
    private static final int PARTICLE_CADENCE = 2; // scatter snow every 2 ticks (~10/sec)
    private static final int FLAKES_MIN = 1;
    private static final int FLAKES_MAX = 2; // inclusive
    private static final int WIND_SOUND_CADENCE = 50; // low howl loop every 2.5 seconds

    @Override
    public String id() {
        return "blizzard_winds";
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        pendingGust = null;
        ctx.message("§b❄ A blizzard howls across the arena.");
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        blowWindGust(ctx);
    }

    /**
     * Every {@link #GUST_CADENCE} rounds, rolls a random cardinal wind direction and calls
     * {@link MinibossContext#windGust} - the real Frostbound-style gust: sweeping arrow glyphs
     * across the whole arena telegraph the drag direction, then every player and enemy on the
     * grid is shoved that way. A small scatter of temporary FROST tiles rides along on the
     * downwind side as a light accent, reusing the tile type's existing "Frozen risk on step"
     * contract from {@link TileType}.
     */
    private void blowWindGust(MinibossContext ctx) {
        // Resolve a gust telegraphed last round: NOW drag everything the warned direction.
        if (pendingGust != null) {
            int dirX = pendingGust[0];
            int dirZ = pendingGust[1];
            pendingGust = null;
            ctx.message("§b❄ The gust hits - everything is dragged " + dirNameOf(dirX, dirZ) + "!");
            // One-shot louder whoosh on the resolve round - same wind sound as the ambient
            // howl loop, just louder/higher-pitched to read as a sudden gust rather than the
            // steady background wind.
            ctx.playSound(SoundEvents.ENTITY_PHANTOM_FLAP, 0.8f, 0.6f);
            ctx.windGust(dirX, dirZ);
            windGustFrostAccent(ctx, dirX, dirZ);
            return; // don't also telegraph the same round - one action per round reads clearly
        }

        // Otherwise, on the cadence, telegraph the NEXT gust: paint the arrows, no drag yet.
        if (ctx.round() % GUST_CADENCE != 0) return;

        Random rng = ctx.rng();
        int dir = rng.nextInt(4);
        int dirX;
        int dirZ;
        switch (dir) {
            case DIR_NORTH -> { dirX = 0; dirZ = -1; }
            case DIR_SOUTH -> { dirX = 0; dirZ = 1; }
            case DIR_EAST -> { dirX = 1; dirZ = 0; }
            default -> { dirX = -1; dirZ = 0; }
        }
        pendingGust = new int[]{dirX, dirZ};
        ctx.message("§b❄ The wind rises to the " + dirNameOf(dirX, dirZ)
            + " - the arrows show which way it will drag. Brace!");
        ctx.windTelegraph(dirX, dirZ);
    }

    private static String dirNameOf(int dirX, int dirZ) {
        if (dirZ < 0) return "north";
        if (dirZ > 0) return "south";
        if (dirX > 0) return "east";
        return "west";
    }

    /** Scatter a few temporary FROST tiles on the downwind side of a resolved gust (flavor). */
    private void windGustFrostAccent(MinibossContext ctx, int dirX, int dirZ) {
        GridArena arena = ctx.arena();
        int width = arena.getWidth();
        int height = arena.getHeight();
        Random rng = ctx.rng();
        int dir = dirZ < 0 ? DIR_NORTH : dirZ > 0 ? DIR_SOUTH : dirX > 0 ? DIR_EAST : DIR_WEST;
        // Light frost accent on the downwind side, purely cosmetic follow-through.
        int tileCount = FROST_TILES_MIN + rng.nextInt(FROST_TILES_MAX - FROST_TILES_MIN + 1);
        for (int i = 0; i < tileCount; i++) {
            int x;
            int z;
            switch (dir) {
                case DIR_NORTH -> { // downwind = low z half
                    x = rng.nextInt(Math.max(1, width));
                    z = rng.nextInt(Math.max(1, height / 2));
                }
                case DIR_SOUTH -> { // downwind = high z half
                    x = rng.nextInt(Math.max(1, width));
                    int half = Math.max(1, height / 2);
                    z = half + rng.nextInt(Math.max(1, height - half));
                }
                case DIR_EAST -> { // downwind = high x half
                    int half = Math.max(1, width / 2);
                    x = half + rng.nextInt(Math.max(1, width - half));
                    z = rng.nextInt(Math.max(1, height));
                }
                default -> { // DIR_WEST - downwind = low x half
                    x = rng.nextInt(Math.max(1, width / 2));
                    z = rng.nextInt(Math.max(1, height));
                }
            }
            ctx.placeTemporaryTile(new GridPos(x, z), TileType.FROST, FROST_DURATION);
        }
    }

    /**
     * Continuous blizzard ambience: light snowflakes drifting sideways across the arena every
     * couple of ticks, plus a low wind howl looping every couple seconds. There's no vanilla
     * "wind gust" sound, so this reuses {@code ENTITY_PHANTOM_FLAP} pitched down - a soft,
     * airy flapping sound that reads as a low howl at 0.5 pitch without being identifiable as
     * a phantom. It's already a known-good sound (used elsewhere in combat VFX) so it's safe
     * on every shard.
     */
    @Override
    public void onCombatTick(MinibossContext ctx, int tick) {
        if (tick % PARTICLE_CADENCE == 0) {
            GridArena arena = ctx.arena();
            int width = arena.getWidth();
            int height = arena.getHeight();
            Random rng = ctx.rng();
            BlockPos origin = arena.getOrigin();
            int flakes = FLAKES_MIN + rng.nextInt(FLAKES_MAX - FLAKES_MIN + 1);
            for (int i = 0; i < flakes; i++) {
                int x = rng.nextInt(Math.max(1, width));
                int z = rng.nextInt(Math.max(1, height));
                GridPos pos = new GridPos(x, z);
                if (!arena.isInBounds(pos)) continue;
                BlockPos ground = arena.gridToBlockPos(pos);
                double wx = ground.getX() + 0.5;
                double wz = ground.getZ() + 0.5;
                double midY = origin.getY() + 2.5; // mid height above the floor
                // Light sideways drift - a small random horizontal speed component sells the
                // "blowing" feel without a persistent wind-direction field.
                double driftX = (rng.nextDouble() - 0.5) * 0.6;
                double driftZ = (rng.nextDouble() - 0.5) * 0.6;
                ctx.spawnAmbientParticle(ParticleTypes.SNOWFLAKE, wx, midY, wz,
                    1, driftX, 0.15, driftZ, 0.03);
            }
        }

        if (tick % WIND_SOUND_CADENCE == 0) {
            ctx.playSound(SoundEvents.ENTITY_PHANTOM_FLAP, 0.3f, 0.5f);
        }
    }
}
